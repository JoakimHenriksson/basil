package app.basil.mobile

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.*
import app.basil.contracts.CaptureRequest
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.serialization.json.Json

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun BrowserSurface(state: BrowserState, modifier: Modifier, onCapture: (CaptureRequest) -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val captureCallback by rememberUpdatedState(onCapture)
    val errorCallback by rememberUpdatedState(onError)
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = request.url.scheme != "https"
                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) { state.loading = true; state.url = url.orEmpty() }
                override fun onPageFinished(view: WebView, url: String?) {
                    state.loading = false; state.url = url.orEmpty(); state.canGoBack = view.canGoBack(); state.canGoForward = view.canGoForward()
                }
                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
                    handler.cancel()
                    state.loading = false
                    errorCallback("This website's secure connection could not be verified. Try another site.")
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) { state.loading = false; errorCallback("Couldn't load this page. Try another link.") }
                }
            }
            loadUrl(state.url)
        }
    }
    SideEffect {
        state.load = { if (it.startsWith("https://")) webView.loadUrl(it) else errorCallback("Use an HTTPS recipe page.") }
        state.back = { webView.goBack() }
        state.forward = { webView.goForward() }
        state.capture = {
            state.capturing = true
            webView.evaluateJavascript(captureJavascript) { result ->
                state.capturing = false
                try {
                    val page = decodeCapture(Json.decodeFromString<String>(result))
                    require(page.url == webView.url) { "The page changed during capture. Please save again." }
                    captureCallback(page)
                } catch (failure: Exception) { errorCallback(failure.message ?: "Couldn't capture this recipe page.") }
            }
        }
    }
    DisposableEffect(webView) {
        onDispose { webView.stopLoading(); webView.destroy() }
    }
    AndroidView(factory = { webView }, modifier = modifier)
}
