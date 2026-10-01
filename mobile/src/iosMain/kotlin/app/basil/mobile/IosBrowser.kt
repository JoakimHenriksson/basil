@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package app.basil.mobile

import app.basil.contracts.CaptureRequest
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.Foundation.*
import platform.WebKit.*
import platform.darwin.NSObject

@Composable
actual fun BrowserSurface(state: BrowserState, modifier: Modifier, onCapture: (CaptureRequest) -> Unit, onError: (String) -> Unit) {
    val captureCallback by rememberUpdatedState(onCapture)
    val errorCallback by rememberUpdatedState(onError)
    val webView = remember { WKWebView(frame = CGRectZero.readValue(), configuration = WKWebViewConfiguration()) }
    val delegate = remember {
        object : NSObject(), WKNavigationDelegateProtocol {
            @ObjCSignatureOverride
            override fun webView(webView: WKWebView, didStartProvisionalNavigation: WKNavigation?) { state.loading = true }
            @ObjCSignatureOverride
            override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
                state.loading = false; state.url = webView.URL?.absoluteString.orEmpty()
                state.canGoBack = webView.canGoBack; state.canGoForward = webView.canGoForward
            }
            @ObjCSignatureOverride
            override fun webView(webView: WKWebView, didFailNavigation: WKNavigation?, withError: NSError) {
                state.loading = false; errorCallback("Couldn't load this page. Try another link.")
            }
            @ObjCSignatureOverride
            override fun webView(webView: WKWebView, didFailProvisionalNavigation: WKNavigation?, withError: NSError) {
                state.loading = false; errorCallback("Couldn't load this page. Try another link.")
            }
            override fun webView(webView: WKWebView, decidePolicyForNavigationAction: WKNavigationAction, decisionHandler: (WKNavigationActionPolicy) -> Unit) {
                val scheme = decidePolicyForNavigationAction.request.URL?.scheme
                decisionHandler(if (scheme == "https" || scheme == "about") WKNavigationActionPolicyAllow else WKNavigationActionPolicyCancel)
            }
        }
    }
    SideEffect {
        state.load = { url -> NSURL.URLWithString(url)?.let { webView.loadRequest(NSURLRequest.requestWithURL(it)) } }
        state.back = { webView.goBack() }
        state.forward = { webView.goForward() }
        state.capture = {
            state.capturing = true
            webView.evaluateJavaScript(captureJavascript) { result, error ->
                state.capturing = false
                try {
                    require(error == null) { "Couldn't capture this recipe page." }
                    val page = decodeCapture(result as? String ?: throw IllegalArgumentException("No readable page was captured."))
                    require(page.url == webView.URL?.absoluteString) { "The page changed during capture. Please save again." }
                    captureCallback(page)
                } catch (failure: Exception) { errorCallback(failure.message ?: "Couldn't capture this recipe page.") }
            }
        }
    }
    DisposableEffect(webView) {
        webView.navigationDelegate = delegate
        NSURL.URLWithString(state.url)?.let { webView.loadRequest(NSURLRequest.requestWithURL(it)) }
        onDispose { webView.stopLoading(); webView.navigationDelegate = null }
    }
    UIKitView(factory = { webView }, modifier = modifier)
}
