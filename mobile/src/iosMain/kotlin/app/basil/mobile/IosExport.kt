@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package app.basil.mobile

import androidx.compose.runtime.*
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.*
import platform.UIKit.*

@Composable
actual fun rememberExportSharer(): (ByteArray) -> Unit = remember {
    { bytes ->
        val file = NSTemporaryDirectory() + "basil-recipes.zip"
        val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        check(data.writeToFile(file, atomically = true)) { "Couldn't write the recipe export." }
        val window = UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>()
            .flatMap { it.windows.filterIsInstance<UIWindow>() }.firstOrNull { it.isKeyWindow() }
        var presenter = window?.rootViewController
        while (presenter?.presentedViewController != null) presenter = presenter?.presentedViewController
        checkNotNull(presenter) { "Couldn't open the export sheet." }
        val activity = UIActivityViewController(listOf(NSURL.fileURLWithPath(file)), null)
        activity.popoverPresentationController?.sourceView = presenter.view
        activity.popoverPresentationController?.sourceRect = presenter.view.bounds
        presenter.presentViewController(activity, animated = true, completion = null)
    }
}
