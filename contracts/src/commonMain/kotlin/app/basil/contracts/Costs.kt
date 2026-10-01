package app.basil.contracts
import kotlinx.serialization.Serializable

@Serializable data class BudgetSettings(val currency: String = "SEK", val monthlyLimitMinor: Long? = null)
@Serializable data class ReceiptLine(val id: String, val name: String, val totalMinor: Long, val ingredient: String = "", val quantity: String? = null, val unit: String = "g", val packages: Int = 1, val stockAction: String = "none", val linkedAddId: String? = null, val linkedAddIds: List<String> = emptyList())
@Serializable data class ReceiptRequest(val id: String, val store: String, val purchasedOn: String, val currency: String, val totalMinor: Long, val lines: List<ReceiptLine>, val reference: String = "")
@Serializable data class Purchase(val receipt: ReceiptRequest, val fingerprint: String, val revision: Int = 1)
@Serializable data class ReceiptCorrection(val expectedRevision: Int, val store: String, val purchasedOn: String, val totalMinor: Long, val lineTotals: Map<String, Long>)
@Serializable data class PriceObservation(val name: String, val quantity: String, val unit: String, val totalMinor: Long, val packages: Int, val purchasedOn: String, val purchaseId: String)
@Serializable data class CostEstimate(val currency: String, val knownMinor: Long, val perServingMinor: Long?, val missingPrices: List<String>, val sources: List<PriceObservation>, val packageShopping: Boolean = false)
@Serializable data class BudgetSummary(val month: String, val settings: BudgetSettings, val spentMinor: Long, val remainingMinor: Long?)
@Serializable data class ReceiptDraft(val store: String, val purchasedOn: String?, val totalMinor: Long?, val lines: List<ReceiptLine>, val warnings: List<String>)
@Serializable data class TextScanRequest(val text: String)

fun parseMinor(value: String): Long {
    val match = Regex("^(-?)([0-9]{1,10})(?:[.,]([0-9]{1,2}))?$").matchEntire(value.trim()) ?: error("Enter a price with up to two decimal places.")
    val amount = match.groupValues[2].toLong() * 100 + match.groupValues[3].padEnd(2, '0').toLong()
    return if (match.groupValues[1] == "-") -amount else amount
}
fun moneyLabel(minor: Long, currency: String): String {
    val sign = if (minor < 0) "-" else ""
    val amount = if (minor < 0) -minor else minor
    return "$sign${amount / 100}.${(amount % 100).toString().padStart(2, '0')} $currency"
}
