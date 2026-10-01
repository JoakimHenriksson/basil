@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package app.basil.mobile

import androidx.compose.runtime.*
import platform.Foundation.*
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned

@Composable actual fun rememberDeviceStore(): DeviceStore = remember { IosDeviceStore() }

/** App-private, file-protected storage. Account credential migration to Keychain is release work. */
private class IosDeviceStore : DeviceStore {
    private val directory = NSHomeDirectory() + "/Library/Application Support/Basil"
    init {
        NSFileManager.defaultManager.createDirectoryAtPath(directory, true, mapOf(NSFileProtectionKey to NSFileProtectionComplete), null)
    }
    private fun file(key: String): String = directory + "/" + key.encodeToByteArray().joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
    override fun read(key: String): String? = NSString.stringWithContentsOfFile(file(key), NSUTF8StringEncoding, null)
    override fun write(key: String, value: String?) {
        val path = file(key)
        if (value == null) { NSFileManager.defaultManager.removeItemAtPath(path, null); return }
        val bytes = value.encodeToByteArray()
        val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        check(data.writeToFile(path, true)) { "Couldn't save changes on this device." }
        check(NSFileManager.defaultManager.setAttributes(mapOf(NSFileProtectionKey to NSFileProtectionComplete), path, null)) { "Couldn't protect device storage." }
    }
}
