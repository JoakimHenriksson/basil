package app.basil.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.basil.mobile.BasilApp

class MainActivity : ComponentActivity() {
    private var sharedUrl by mutableStateOf("")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readShare(intent)
        setContent { BasilApp(BuildConfig.API_URL, sharedUrl) }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); readShare(intent) }
    private fun readShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            sharedUrl = Regex("https://[^\\s]+").find(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())?.value.orEmpty()
        }
    }
}
