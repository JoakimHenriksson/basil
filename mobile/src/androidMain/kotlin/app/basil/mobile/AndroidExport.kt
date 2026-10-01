package app.basil.mobile

import android.content.Intent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

@Composable
actual fun rememberExportSharer(): (ByteArray) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { bytes ->
            val directory = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(directory, "basil-recipes.zip").apply { writeBytes(bytes) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.exports", file)
            val intent = Intent(Intent.ACTION_SEND).setType("application/zip")
                .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(intent, "Export recipes"))
        }
    }
}
