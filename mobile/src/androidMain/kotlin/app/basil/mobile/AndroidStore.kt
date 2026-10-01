package app.basil.mobile

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Composable actual fun rememberDeviceStore(): DeviceStore {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidDeviceStore(context) }
}

private class AndroidDeviceStore(context: Context) : DeviceStore {
    private val preferences = context.getSharedPreferences("basil-device", Context.MODE_PRIVATE)
    private val key: SecretKey
    init {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "basil-device-store"
        key = (store.getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }
    @Synchronized override fun read(key: String): String? {
        val value = preferences.getString(key, null) ?: return null
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, this.key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD(key.toByteArray())
        return cipher.doFinal(bytes.copyOfRange(12, bytes.size)).decodeToString()
    }
    @Synchronized override fun write(key: String, value: String?) {
        val editor = preferences.edit()
        if (value == null) editor.remove(key) else {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, this@AndroidDeviceStore.key); updateAAD(key.toByteArray()) }
            editor.putString(key, Base64.encodeToString(cipher.iv + cipher.doFinal(value.encodeToByteArray()), Base64.NO_WRAP))
        }
        check(editor.commit()) { "Couldn't save pending changes on this device." }
    }
}
