@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package app.basil.mobile

import androidx.compose.runtime.*
import kotlinx.coroutines.*
import platform.Foundation.*
import platform.UIKit.*
import platform.Vision.*
import platform.darwin.NSObject

@Composable actual fun rememberPhotoTextReader(onText: (String) -> Unit, onError: (String) -> Unit): PhotoTextReader {
    val scope = rememberCoroutineScope()
    val text by rememberUpdatedState(onText)
    val error by rememberUpdatedState(onError)
    val delegate = remember {
        object : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
            override fun imagePickerControllerDidCancel(picker: UIImagePickerController) { picker.dismissViewControllerAnimated(true, null) }
            override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
                val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
                picker.dismissViewControllerAnimated(true, null)
                scope.launch {
                    try {
                        val cgImage = requireNotNull(image?.CGImage)
                        val request = VNRecognizeTextRequest()
                        request.recognitionLevel = VNRequestTextRecognitionLevelAccurate
                        request.automaticallyDetectsLanguage = true
                        val value = withContext(Dispatchers.Default) {
                            val handler = VNImageRequestHandler(cgImage = cgImage, options = emptyMap<Any?, Any>())
                            check(handler.performRequests(listOf(request), error = null))
                            request.results?.filterIsInstance<VNRecognizedTextObservation>()?.mapNotNull { it.topCandidates(1uL).firstOrNull()?.string }?.joinToString("\n").orEmpty()
                        }
                        text(value)
                    } catch (_: Exception) { error("Couldn't read the photo. Try a sharper image or paste the text.") }
                }
            }
        }
    }
    fun open(camera: Boolean) {
        val source = if (camera) UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera else UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
        if (!UIImagePickerController.isSourceTypeAvailable(source)) { error("Camera unavailable. Choose a photo instead."); return }
        val window = UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>().flatMap { it.windows.filterIsInstance<UIWindow>() }.firstOrNull { it.isKeyWindow() }
        var presenter = window?.rootViewController
        while (presenter?.presentedViewController != null) presenter = presenter?.presentedViewController
        if (presenter == null) { error("Couldn't open the photo reader."); return }
        val picker = UIImagePickerController(); picker.sourceType = source; picker.delegate = delegate
        presenter.presentViewController(picker, true, null)
    }
    return PhotoTextReader(capture = { open(true) }, choose = { open(false) })
}
