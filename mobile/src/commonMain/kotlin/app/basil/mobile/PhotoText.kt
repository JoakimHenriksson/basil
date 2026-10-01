package app.basil.mobile
import androidx.compose.runtime.Composable

class PhotoTextReader(val capture: () -> Unit, val choose: () -> Unit)
@Composable expect fun rememberPhotoTextReader(onText: (String) -> Unit, onError: (String) -> Unit): PhotoTextReader
