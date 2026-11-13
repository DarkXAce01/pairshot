package com.greasepencil.pairshot.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.greasepencil.pairshot.data.cloud.CloudAccess
import com.greasepencil.pairshot.data.cloud.CloudException
import com.greasepencil.pairshot.data.cloud.CloudSession
import com.greasepencil.pairshot.data.cloud.OfflineException
import com.greasepencil.pairshot.data.cloud.call
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** What happened with a purchase or restore, in words the screen can show. */
sealed interface ProResult {
    data object Pro : ProResult
    /** Paid by a slow method (cash, bank): Pro switches on when Google says the money arrived. */
    data object Pending : ProResult
    /** Play has no Pro purchase for this Google account. */
    data object NothingToRestore : ProResult
    data object Cancelled : ProResult
    data class Failed(val message: String) : ProResult
}

/** A purchase as Play reports it, without Play's classes (so the claim logic can be tested). */
data class OwnedPurchase(val token: String, val products: List<String>, val purchased: Boolean, val pending: Boolean)

/** Hands Play purchases to the server, which checks them with Google and switches the account to Pro. */
object ProClaims {
    suspend fun claim(cloud: CloudAccess, owned: List<OwnedPurchase>, productId: String = ProBilling.PRODUCT_ID): ProResult {
        val pro = owned.filter { productId in it.products }
        if (pro.isEmpty()) return ProResult.NothingToRestore
        val paid = pro.firstOrNull { it.purchased } ?: return ProResult.Pending
        return try {
            val r = cloud.call { it.claimPro(paid.token, productId) }
            cloud.saveAccount(r.account)
            if (r.status == "pro") ProResult.Pro else ProResult.Pending
        } catch (e: OfflineException) {
            ProResult.Failed("You're offline. Your purchase is safe; open this screen again with signal to switch Pro on.")
        } catch (e: CloudException) {
            ProResult.Failed(e.message ?: "Couldn't switch Pro on. Try again.")
        }
    }
}

/**
 * Google Play Billing for the one-time Pro purchase (product id [PRODUCT_ID], a one-time product in
 * Play Console). Only works in a build installed from Play (internal testing counts); a debug build
 * from Android Studio is told the product doesn't exist.
 */
@Singleton
class ProBilling @Inject constructor(
    @ApplicationContext context: Context,
    private val cloud: CloudSession,
) : PurchasesUpdatedListener {
    companion object { const val PRODUCT_ID = "pro" }

    private val scope = CoroutineScope(SupervisorJob())
    private val connectLock = Mutex()
    private val _results = MutableSharedFlow<ProResult>(extraBufferCapacity = 4)
    /** Results of purchases made with [buy] (they finish after Play's own screen closes). */
    val results: SharedFlow<ProResult> = _results

    private val client = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private suspend fun connect(): BillingResult = connectLock.withLock {
        if (client.isReady) return BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.OK).build()
        suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) { if (cont.isActive) cont.resume(result) }
                override fun onBillingServiceDisconnected() {}
            })
        }
    }

    private suspend fun details(): ProductDetails? {
        if (connect().responseCode != BillingClient.BillingResponseCode.OK) return null
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_ID)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build(),
            ),
        ).build()
        return suspendCancellableCoroutine { cont ->
            client.queryProductDetailsAsync(params) { _, result -> if (cont.isActive) cont.resume(result.productDetailsList.firstOrNull()) }
        }
    }

    /** "$9.99" in the buyer's own currency, or null when Play can't sell it here (e.g. a debug build). */
    suspend fun price(): String? = runCatching { details()?.oneTimePurchaseOfferDetails?.formattedPrice }.getOrNull()

    /** Opens Play's purchase screen. Returns a problem to show, or null when the screen opened. */
    suspend fun buy(activity: Activity): String? {
        val d = details() ?: return "Pro can only be bought in Pairshot from Google Play. This copy wasn't installed from Play."
        val account = cloud.state.first().accountId
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()))
            .apply { account?.let { setObfuscatedAccountId(it) } }
            .build()
        val r = client.launchBillingFlow(activity, params)
        return when (r.responseCode) {
            BillingClient.BillingResponseCode.OK -> null
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> { scope.launch { _results.emit(restore()) }; null }
            else -> "Google Play couldn't start the purchase. Try again."
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        scope.launch {
            _results.emit(
                when (result.responseCode) {
                    BillingClient.BillingResponseCode.OK -> ProClaims.claim(cloud, purchases.orEmpty().map(::owned))
                    BillingClient.BillingResponseCode.USER_CANCELED -> ProResult.Cancelled
                    BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> restore()
                    else -> ProResult.Failed("The purchase didn't go through. You weren't charged.")
                },
            )
        }
    }

    /** Finds a Pro purchase this Google Play account already owns and switches Pro on for it. */
    suspend fun restore(): ProResult {
        if (connect().responseCode != BillingClient.BillingResponseCode.OK) {
            return ProResult.Failed("Couldn't reach Google Play. Check that you're signed in to the Play Store.")
        }
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        val owned = suspendCancellableCoroutine { cont ->
            client.queryPurchasesAsync(params) { _, list -> if (cont.isActive) cont.resume(list.map(::owned)) }
        }
        return ProClaims.claim(cloud, owned)
    }

    private fun owned(p: Purchase) = OwnedPurchase(
        token = p.purchaseToken,
        products = p.products,
        purchased = p.purchaseState == Purchase.PurchaseState.PURCHASED,
        pending = p.purchaseState == Purchase.PurchaseState.PENDING,
    )
}
