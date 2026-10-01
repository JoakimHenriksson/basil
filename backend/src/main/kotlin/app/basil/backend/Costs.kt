package app.basil.backend

import app.basil.contracts.*
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate

object Costs {
    fun validate(request: ReceiptRequest, currency: String) {
        require(request.id.matches(Regex("[a-zA-Z0-9-]{1,80}"))) { "Invalid receipt ID." }
        require(request.store.isNotBlank() && request.store.length <= 250 && request.reference.length <= 150) { "Enter a store name." }
        require(runCatching { LocalDate.parse(request.purchasedOn) }.isSuccess) { "Enter the purchase date as YYYY-MM-DD." }
        require(request.currency == currency) { "Use the household currency $currency." }
        require(request.lines.size in 1..500 && request.lines.map { it.id }.distinct().size == request.lines.size) { "Enter distinct receipt lines." }
        request.lines.forEach {
            require(it.id.matches(Regex("[a-zA-Z0-9-]{1,80}")) && it.name.isNotBlank() && it.name.length <= 250 && it.ingredient.length <= 150) { "Check the receipt item name." }
            require(it.totalMinor in -100_000_000..100_000_000) { "Check the receipt price." }
            require(it.stockAction in setOf("none", "add", "link") && it.packages in 1..10000) { "Check the stock action and package count." }
            if (it.ingredient.isNotBlank() || it.stockAction != "none") {
                require(it.ingredient.isNotBlank() && it.unit in setOf("g", "ml", "piece")) { "Choose an ingredient and unit." }
                Stock.positive(requireNotNull(it.quantity) { "Enter the purchased quantity." })
            }
            if (it.totalMinor <= 0) require(it.stockAction == "none") { "Discounts and refunds cannot add stock." }
        }
        val sum = request.lines.fold(0L) { sum, line -> Math.addExact(sum, line.totalMinor) }
        require(sum == request.totalMinor) { "Receipt items add up to ${moneyLabel(sum, currency)}. Correct the items or total before saving." }
    }
    fun prices(purchases: List<Purchase>): List<PriceObservation> = purchases.flatMap { purchase ->
        // Unallocated discounts make per-product paid prices uncertain; don't learn those as verified prices.
        if (purchase.receipt.lines.any { it.totalMinor < 0 }) emptyList() else purchase.receipt.lines.filter { it.ingredient.isNotBlank() && it.quantity != null && it.totalMinor > 0 }.map {
            PriceObservation(Ingredients.canonical(it.ingredient, it.unit), it.quantity!!, it.unit, it.totalMinor, it.packages, purchase.receipt.purchasedOn, purchase.receipt.id)
        }
    }
    fun estimate(ingredients: List<Ingredient>, servings: Int?, currency: String, prices: List<PriceObservation>, packages: Boolean = false): CostEstimate {
        val used = mutableListOf<PriceObservation>(); val missing = mutableListOf<String>(); var cost = BigDecimal.ZERO
        ingredients.filterNot { it.optional }.forEach { ingredient ->
            val observation = prices.asReversed().filter { it.name == ingredient.name && it.unit == ingredient.unit }.maxByOrNull { it.purchasedOn }
            if (observation == null || ingredient.amount == null || ingredient.needsReview) missing += ingredient.name
            else {
                val needed = Ingredients.number(ingredient.maxAmount ?: ingredient.amount!!)
                val bought = Ingredients.number(observation.quantity)
                val contribution = if (packages) {
                    val size = bought.divide(observation.packages.toBigDecimal(), MathContext.DECIMAL128)
                    val count = needed.divide(size, 0, RoundingMode.CEILING)
                    count * observation.totalMinor.toBigDecimal().divide(observation.packages.toBigDecimal(), MathContext.DECIMAL128)
                } else needed.divide(bought, MathContext.DECIMAL128) * observation.totalMinor.toBigDecimal()
                cost += contribution; used += observation
            }
        }
        fun minor(value: BigDecimal) = requireNotNull(runCatching { value.setScale(0, RoundingMode.HALF_UP).longValueExact() }.getOrNull()) { "This estimated cost is too large. Check the purchase quantities and prices." }
        return CostEstimate(currency, minor(cost), servings?.let { minor(cost.divide(it.toBigDecimal(), MathContext.DECIMAL128)) }, missing.distinct(), used.distinct(), packages)
    }
    fun draft(text: String): ReceiptDraft {
        require(text.isNotBlank() && text.length <= 60_000) { "Scan or paste a readable receipt of up to 60,000 characters." }
        val lines = text.lines().map(String::trim).filter(String::isNotBlank)
        val date = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").find(text)?.value?.takeIf { runCatching { LocalDate.parse(it) }.isSuccess }
        var total: Long? = null
        val items = lines.mapNotNull { line ->
            val match = Regex("^(.+?)\\s+(-?[0-9]+[.,][0-9]{2})(?:\\s*(?:kr|SEK))?$", RegexOption.IGNORE_CASE).matchEntire(line) ?: return@mapNotNull null
            val name = match.groupValues[1]
            val amount = runCatching { parseMinor(match.groupValues[2]) }.getOrNull() ?: return@mapNotNull null
            if (Regex("^(total|totalt|summa|att betala|amount due)\\b", RegexOption.IGNORE_CASE).containsMatchIn(name)) { total = amount; null }
            else if (Regex("moms|vat|tax|kontant|kort|cash|change|växel", RegexOption.IGNORE_CASE).containsMatchIn(name)) null
            else ReceiptLine(java.util.UUID.randomUUID().toString(), name, amount)
        }
        return ReceiptDraft(lines.first().take(250), date, total, items, listOf("Review scanned items, purchase date and total. Receipt tax is included in item prices; omitted or misread lines need correction."))
    }
}
