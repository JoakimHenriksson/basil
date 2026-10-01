@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package app.basil.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.*
import kotlinx.cinterop.readValue
import platform.AVFoundation.*
import platform.CoreGraphics.CGRectZero
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

@Composable
actual fun rememberBarcodeScanner(onResult: (String) -> Unit, onError: (String) -> Unit): () -> Unit {
    var open by remember { mutableStateOf(false) }
    val result by rememberUpdatedState(onResult)
    val error by rememberUpdatedState(onError)
    if (open) Dialog(onDismissRequest = { open = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Text("Scan a product barcode")
                TextButton(onClick = { open = false }) { Text("Close scanner") }
                BarcodeCamera(Modifier.fillMaxWidth().weight(1f),
                    onResult = { code -> open = false; result(code) },
                    onError = { message -> open = false; error(message) })
            }
        }
    }
    return { open = true }
}

@Composable
private fun BarcodeCamera(modifier: Modifier, onResult: (String) -> Unit, onError: (String) -> Unit) {
    val session = remember { AVCaptureSession() }
    val output = remember { AVCaptureMetadataOutput() }
    val result by rememberUpdatedState(onResult)
    val error by rememberUpdatedState(onError)
    var delivered by remember { mutableStateOf(false) }
    val delegate = remember {
        object : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
            override fun captureOutput(output: AVCaptureOutput, didOutputMetadataObjects: List<*>, fromConnection: AVCaptureConnection) {
                if (!delivered) {
                    val code = didOutputMetadataObjects.filterIsInstance<AVMetadataMachineReadableCodeObject>().firstOrNull()?.stringValue
                    if (code != null) { delivered = true; result(code) }
                }
            }
        }
    }
    val preview = remember { AVCaptureVideoPreviewLayer.layerWithSession(session).apply { videoGravity = AVLayerVideoGravityResizeAspectFill } }
    LaunchedEffect(session) {
        val allowed = suspendCancellableCoroutine<Boolean> { continuation ->
            AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted -> if (continuation.isActive) continuation.resume(granted) }
        }
        if (!allowed) { error("Camera access is unavailable. Allow it in Settings or type the barcode."); return@LaunchedEffect }
        val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
        val input = device?.let { AVCaptureDeviceInput.deviceInputWithDevice(it, error = null) }
        if (input == null || !session.canAddInput(input) || !session.canAddOutput(output)) {
            error("This device can't scan with its camera. Type the barcode instead."); return@LaunchedEffect
        }
        session.addInput(input); session.addOutput(output)
        output.setMetadataObjectsDelegate(delegate, dispatch_get_main_queue())
        output.metadataObjectTypes = listOf(AVMetadataObjectTypeEAN13Code, AVMetadataObjectTypeEAN8Code)
            .filter { it in output.availableMetadataObjectTypes }
        withContext(Dispatchers.Default) { session.startRunning() }
    }
    DisposableEffect(session) {
        onDispose { delivered = true; output.setMetadataObjectsDelegate(null, null); session.stopRunning() }
    }
    UIKitView(factory = { BarcodePreviewView(preview) }, modifier = modifier)
}

private class BarcodePreviewView(private val preview: AVCaptureVideoPreviewLayer) : UIView(frame = CGRectZero.readValue()) {
    init { layer.addSublayer(preview) }
    override fun layoutSubviews() { super.layoutSubviews(); preview.frame = bounds }
}
