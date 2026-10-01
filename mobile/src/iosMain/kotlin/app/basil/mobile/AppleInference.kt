package app.basil.mobile

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private var appleProvider: AppleDraftProvider? = null
fun installAppleDraftProvider(provider: AppleDraftProvider) { appleProvider = provider }
actual fun appleRecipeAvailability(): String = appleProvider?.availability() ?: "Apple Intelligence isn't available in this build."
actual suspend fun generateAppleRecipe(prompt: String): String = withTimeout(45_000) {
    val provider = requireNotNull(appleProvider) { "Apple Intelligence isn't available in this build." }
    check(provider.availability() == "available") { provider.availability() }
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { provider.cancel() }
        provider.generate(prompt) { text, error ->
            if (continuation.isActive) {
                if (text != null) continuation.resume(text) else continuation.resumeWithException(IllegalStateException(error ?: "Apple Intelligence couldn't suggest a meal."))
            }
        }
    }
}
