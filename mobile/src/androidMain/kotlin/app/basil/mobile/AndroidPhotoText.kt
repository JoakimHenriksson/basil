package app.basil.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

@Composable actual fun rememberPhotoTextReader(onText: (String) -> Unit, onError: (String) -> Unit): PhotoTextReader {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onText)
    val error by rememberUpdatedState(onError)
    var cameraFile by remember { mutableStateOf<File?>(null) }
    fun recognize(uri: Uri, temporary: File? = null) {
        try {
            val reader = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            reader.process(InputImage.fromFilePath(context, uri)).addOnSuccessListener { callback(it.text) }
                .addOnFailureListener { error("Couldn't read the photo. Try a sharper image or paste the text.") }
                .addOnCompleteListener { reader.close(); temporary?.delete() }
        } catch (_: Exception) { temporary?.delete(); error("Couldn't open that photo.") }
    }
    val choose = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { recognize(it) } }
    val capture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val file = cameraFile; cameraFile = null
        if (taken && file != null) recognize(FileProvider.getUriForFile(context, "${context.packageName}.exports", file), file) else file?.delete()
    }
    fun launchCamera() {
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File.createTempFile("basil-scan-", ".jpg", directory); cameraFile = file
        try { capture.launch(FileProvider.getUriForFile(context, "${context.packageName}.exports", file)) }
        catch (_: Exception) { file.delete(); cameraFile = null; error("Camera unavailable. Choose a photo instead.") }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else error("Camera permission wasn't granted. Choose a photo instead.")
    }
    return PhotoTextReader(capture = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera() else permission.launch(Manifest.permission.CAMERA)
    }, choose = { choose.launch("image/*") })
}
