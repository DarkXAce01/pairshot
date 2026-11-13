package com.greasepencil.pairshot.ui.preview

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.greasepencil.pairshot.ui.nav.PreviewRoute
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

@HiltViewModel
class PreviewViewModel @Inject constructor(
    handle: SavedStateHandle,
    repo: JobRepository,
    settings: SettingsStore,
    cloud: com.greasepencil.pairshot.data.cloud.CloudSession,
    @ApplicationContext context: Context,
) : ViewModel() {
    private val jobId = handle.toRoute<PreviewRoute>().jobId
    private val template = context.assets.open("gallery.html").bufferedReader().use { it.readText() }

    /** The finished page, rebuilt whenever the job, its pairs or the business details change. */
    val html: StateFlow<String?> = combine(repo.observeJob(jobId), repo.observePairs(jobId), settings.settings, cloud.state) { job, pairs, s, c ->
        job ?: return@combine null
        val manifest = GalleryBuilder.build(
            job, pairs,
            BusinessInfo(s.businessName, s.businessPhone, s.reviewUrl, s.bookingUrl, c.isPro),
            imageUrl = { file, rot -> "$HOST/photo/$rot/$file" },
        )
        GalleryBuilder.render(template, manifest)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(onBack: () -> Unit, vm: PreviewViewModel = hiltViewModel()) {
    val html by vm.html.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Client preview", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "This is the page your client gets. Only finished pairs that aren't hidden are on it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            val page = html ?: return@Column
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
}
