package app.basil.mobile

import app.basil.contracts.CaptureRequest
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.Json

class BrowserState(initialUrl: String) {
    var url by mutableStateOf(initialUrl)
    var loading by mutableStateOf(true)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var capturing by mutableStateOf(false)
    var load: (String) -> Unit = {}
    var back: () -> Unit = {}
    var forward: () -> Unit = {}
    var capture: () -> Unit = {}
}

@Composable
fun RecipeBrowser(
    initialUrl: String,
    enabled: Boolean,
    notice: String?,
    onCapture: (CaptureRequest) -> Unit,
    onError: (String) -> Unit,
    onClose: () -> Unit,
    onViewSaved: () -> Unit,
) {
    val state = remember { BrowserState(initialUrl) }
    var address by remember { mutableStateOf(initialUrl) }
    LaunchedEffect(state.url) { address = state.url }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onClose, enabled = enabled) { Text("Recipes") }
            TextButton(onClick = { state.back() }, enabled = enabled && state.canGoBack) { Text("Back") }
            TextButton(onClick = { state.forward() }, enabled = enabled && state.canGoForward) { Text("Forward") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(address, { address = it }, singleLine = true, enabled = enabled,
                label = { Text("Search or website") }, modifier = Modifier.weight(1f))
            Button(enabled = enabled && address.isNotBlank(), onClick = {
                val input = address.trim()
                val destination = when {
                    input.startsWith("https://") -> input
                    !input.contains(' ') && input.contains('.') && !input.contains(":") -> "https://$input"
                    else -> "https://www.google.com/search?q=${input.encodeURLParameter()}"
                }
                state.load(destination)
            }) { Text("Go") }
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        notice?.let {
            Row(Modifier.fillMaxWidth()) {
                Text(it, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onViewSaved, enabled = enabled) { Text("View saved") }
            }
        }
        Button(modifier = Modifier.fillMaxWidth(), enabled = enabled && !state.loading && !state.capturing,
            onClick = { state.capture() }) { Text(if (!enabled || state.capturing) "Saving recipe…" else "Save recipe") }
        BrowserSurface(state, Modifier.fillMaxWidth().weight(1f), onCapture, onError)
    }
}

@Composable
expect fun BrowserSurface(state: BrowserState, modifier: Modifier, onCapture: (CaptureRequest) -> Unit, onError: (String) -> Unit)

fun decodeCapture(json: String): CaptureRequest {
    val capture = Json.decodeFromString<CaptureRequest>(json)
    require(capture.url.startsWith("https://") && capture.html.isNotBlank()) { "Open an HTTPS recipe page first." }
    require(capture.html.encodeToByteArray().size <= 2_000_000) { "This page is too large to capture." }
    return capture
}

// Runs only in response to Save recipe. No native Javascript bridge is exposed.
val captureJavascript = """
(function() {
    const copy = document.documentElement.cloneNode(true);
    const loadedImages = document.querySelectorAll('img');
    copy.querySelectorAll('img').forEach(function(node, index) {
        const src = loadedImages[index].currentSrc || loadedImages[index].src;
        if (src) node.setAttribute('src', src);
    });
    copy.querySelectorAll('script').forEach(function(node) {
        if ((node.getAttribute('type') || '').trim().toLowerCase() !== 'application/ld+json') node.remove();
    });
    copy.querySelectorAll('style, nav, footer, aside, form, input, textarea, button, iframe, noscript').forEach(function(node) { node.remove(); });
    copy.querySelectorAll('*').forEach(function(node) {
        Array.from(node.attributes).forEach(function(attr) {
            if (attr.name.startsWith('on') || attr.name === 'style') node.removeAttribute(attr.name);
        });
    });
    const html = '<!doctype html>' + copy.outerHTML;
    if (html.length > 2000000) throw new Error('This page is too large to capture.');
    return JSON.stringify({url: location.href, html: html});
})()
""".trimIndent()
