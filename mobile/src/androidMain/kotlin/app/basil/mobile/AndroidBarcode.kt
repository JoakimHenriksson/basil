package app.basil.mobile

import androidx.compose.runtime.*
import androidx.activity.compose.rememberLauncherForActivityResult
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@Composable
actual fun rememberBarcodeScanner(onResult: (String) -> Unit, onError: (String) -> Unit): () -> Unit {
    val result by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ScanContract()) { scan -> scan.contents?.let(result) }
    return { launcher.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.EAN_13, ScanOptions.EAN_8, ScanOptions.UPC_A)
        .setPrompt("Scan a product barcode").setBeepEnabled(false).setOrientationLocked(false)) }
}
