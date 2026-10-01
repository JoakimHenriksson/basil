package app.basil.mobile
import kotlinx.serialization.Serializable
import app.basil.contracts.ShoppingItem

@Serializable data class PendingAction(val id: String, val method: String, val path: String, val body: String, val label: String, val error: String? = null, val baselineItem: ShoppingItem? = null)
class ApiFailure(val status: Int, message: String) : IllegalStateException(message)
