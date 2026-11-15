package com.greasepencil.pairshot.ui.preview

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Palette
import com.greasepencil.pairshot.data.db.PairRow
import com.greasepencil.pairshot.domain.gallery.ClientTemplate
import com.greasepencil.pairshot.ui.components.ps.PsButton
import com.greasepencil.pairshot.ui.components.ps.PsButtonVariant
import com.greasepencil.pairshot.ui.components.ps.PsEmptyState
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import androidx.webkit.WebViewAssetLoader
import com.greasepencil.pairshot.data.prefs.SettingsStore
import com.greasepencil.pairshot.data.repo.JobRepository
import com.greasepencil.pairshot.domain.gallery.BusinessInfo
import com.greasepencil.pairshot.domain.gallery.GalleryBuilder
import com.greasepencil.pairshot.ui.components.PhotoPrep
import com.greasepencil.pairshot.ui.components.ps.PsScaffold
import com.greasepencil.pairshot.ui.components.ps.PsTopBar
import com.greasepencil.pairshot.ui.nav.PreviewRoute
import com.greasepencil.pairshot.ui.theme.Ps
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private const val HOST = "https://appassets.androidplatform.net"

/** What the preview shows: the page (null when there's nothing finished yet) and what the Style sheet needs. */
data class PreviewState(
    val html: String?,
    /** This job's own style; null follows the business default. */
    val override: ClientTemplate?,
    val default: ClientTemplate,
    val accent: String?,
    val businessName: String,
    val cover: PairRow?,
    val linkLive: Boolean,
)

@HiltViewModel
class PreviewViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repo: JobRepository,
    settings: SettingsStore,
    cloud: com.greasepencil.pairshot.data.cloud.CloudSession,
    sender: com.greasepencil.pairshot.data.cloud.GallerySender,
    @ApplicationContext context: Context,
) : ViewModel() {
    private val jobId = handle.toRoute<PreviewRoute>().jobId
    private val template = context.assets.open("gallery.html").bufferedReader().use { it.readText() }

    /** The finished page, rebuilt whenever the job, its pairs, its style or the business details change. */
    val state: StateFlow<PreviewState?> = combine(
        repo.observeJob(jobId), repo.observePairs(jobId), settings.settings, cloud.state, sender.observe(jobId),
    ) { job, pairs, s, c, link ->
        job ?: return@combine null
        val style = GalleryBuilder.templateFor(job, s.clientTemplate)
        val manifest = GalleryBuilder.build(
            job, pairs,
            BusinessInfo(s.businessName, s.businessPhone, s.reviewUrl, s.bookingUrl, c.isPro, accent = s.accent),
            imageUrl = { file, rot -> "$HOST/photo/$rot/$file" },
            template = style,
        )
        val shown = pairs.filter { !it.needsAfter && !it.pair.hidden }
        PreviewState(
            html = manifest.cover?.let { GalleryBuilder.render(template, manifest) },
            override = ClientTemplate.fromId(job.template),
            default = s.clientTemplate,
            accent = s.accent,
            businessName = s.businessName,
            cover = shown.firstOrNull { it.pair.isCover } ?: shown.minByOrNull { it.pair.createdAt },
            linkLive = link?.isLive == true,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** null goes back to the business default. */
    fun setStyle(t: ClientTemplate?) = viewModelScope.launch { repo.setTemplate(jobId, t?.id) }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PreviewScreen(onBack: () -> Unit, vm: PreviewViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var styling by rememberSaveable { mutableStateOf(false) }
    val c = Ps.colors
    PsScaffold(
        topBar = {
            PsTopBar("Client preview", onBack = onBack) {
                if (state != null) {
                    PsButton("Style", { styling = true }, variant = PsButtonVariant.Quiet, icon = Icons.Outlined.Palette)
                }
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            Text(
                "This is the page your client gets. Only finished pairs that aren't hidden are on it.",
                style = Ps.type.caption,
                color = c.inkSoft,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Ps.space.gutter).padding(bottom = 12.dp),
            )
            HorizontalDivider(thickness = 1.dp, color = c.hairline)
            val st = state
            if (st == null) {
                // Loading: a sunken block where the page will be.
                Box(Modifier.fillMaxSize().background(c.sunken))
                return@Column
            }
            val page = st.html
            if (page == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    PsEmptyState("Nothing to show yet", "Finish a before and after and it appears here.")
                }
                return@Column
            }
            AndroidView(
                factory = { context ->
                    // Photos are served from app storage through this loader: upright, resized, EXIF stripped,
                    // exactly as they'll be uploaded.
                    val loader = WebViewAssetLoader.Builder()
                        .addPathHandler("/photo/") { path ->
                            val rot = path.substringBefore('/').toIntOrNull() ?: 0
                            val file = path.substringAfter('/')
                            if (file.contains("..") || file.contains('/')) return@addPathHandler null
                            PhotoPrep.display(context, file, rot)?.let {
                                WebResourceResponse("image/jpeg", null, it.inputStream())
                            }
                        }
                        .build()
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                                loader.shouldInterceptRequest(request.url)

                            // Review / booking / call buttons open outside, like they will for the client.
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                if (request.url.toString().startsWith(HOST)) return false
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                                return true
                            }
                        }
                    }
                },
                update = { web ->
                    if (web.tag != page) {
                        web.tag = page
                        web.loadDataWithBaseURL("$HOST/", page, "text/html", "utf-8", null)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
    val st = state
    if (styling && st != null) {
        StyleSheet(st, onPick = vm::setStyle, onDismiss = { styling = false })
    }
}
