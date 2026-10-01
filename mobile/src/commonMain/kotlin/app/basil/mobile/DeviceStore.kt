package app.basil.mobile
import androidx.compose.runtime.Composable

interface DeviceStore { fun read(key: String): String?; fun write(key: String, value: String?); }
@Composable expect fun rememberDeviceStore(): DeviceStore
