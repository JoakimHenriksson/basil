package app.basil.backend

import app.basil.contracts.*
import java.math.BigDecimal
import java.math.MathContext
import java.util.Locale

object Ingredients {
    private val fractions = mapOf('½' to "1/2", '¼' to "1/4", '¾' to "3/4", '⅓' to "1/3", '⅔' to "2/3", '⅛' to "1/8")
    private val amountPattern = Regex("^(\\d+\\s+\\d+/\\d+|\\d+/\\d+|\\d+(?:[.,]\\d+)?)\\s*(.*)$")
    private val units = mapOf(
        "g" to ("g" to "1"), "gram" to ("g" to "1"), "kg" to ("g" to "1000"),
        "ml" to ("ml" to "1"), "cl" to ("ml" to "10"), "dl" to ("ml" to "100"),
        "l" to ("ml" to "1000"), "liter" to ("ml" to "1000"),
        "tsk" to ("ml" to "5"), "msk" to ("ml" to "15"),
        "st" to ("piece" to "1"), "st." to ("piece" to "1"), "stycken" to ("piece" to "1"),
    )
    private val aliases = mapOf(
        "ägg" to "eggs", "egg" to "eggs", "eggs" to "eggs", "large eggs" to "eggs",
        "mjölk" to "milk", "milk" to "milk", "vetemjöl" to "flour", "plain flour" to "flour",
        "arla ko® standardmjölk" to "milk",
        "krossade tomater" to "crushed tomatoes", "crushed tomatoes" to "crushed tomatoes",
        "paprikor" to "bell pepper", "bell peppers" to "bell pepper",
    )
    fun canonical(name: String, unit: String? = null): String {
        val normalized = name.lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")
        if (normalized == "paprika" && unit == "piece") return "bell pepper"
        return aliases[normalized] ?: normalized
    }
    fun number(text: String): BigDecimal {
        val cleaned = text.trim().replace(',', '.')
        val parts = cleaned.split(Regex("\\s+"))
        if (parts.size == 2) return number(parts[0]) + number(parts[1])
        if ('/' in cleaned) {
            val values = cleaned.split('/')
            require(values.size == 2) { "Enter a number or fraction such as 1/2." }
            val denominator = values[1].toBigDecimal()
            require(denominator.signum() != 0) { "A fraction's denominator cannot be zero." }
            return values[0].toBigDecimal().divide(denominator, MathContext.DECIMAL128)
        }
        return cleaned.toBigDecimal()
    }
    fun display(number: BigDecimal): String = number.stripTrailingZeros().toPlainString()
    fun parse(original: String): Ingredient {
        var text = original.trim().replace('\u00a0', ' ')
        fractions.forEach { (symbol, fraction) -> text = text.replace(symbol.toString(), " $fraction ") }
        text = text.replace(Regex("\\s+"), " ").trim()
        val optional = Regex("optional|valfri|valfritt", RegexOption.IGNORE_CASE).containsMatchIn(text)
        val matched = amountPattern.find(text) ?: return Ingredient(original, canonical(text), optional = optional, needsReview = true)
        var remainder = matched.groupValues[2].trim()
        if (remainder.startsWith('-') || remainder.startsWith('–') || remainder.startsWith('.')) {
            return Ingredient(original, canonical(text), optional = optional, needsReview = true)
        }
        val first = remainder.substringBefore(' ').lowercase(Locale.ROOT)
        val conversion = units[first]
        val unsupportedUnit = first in setOf("tsp", "tbsp", "cup", "cups", "oz", "lb", "burk", "burkar", "förpackning", "paket", "cm", "krm")
        if (conversion != null) remainder = remainder.substringAfter(' ', "").trim()
        if (remainder.isBlank() || unsupportedUnit) return Ingredient(original, canonical(remainder.ifBlank { text }), optional = optional, needsReview = true)
        val amount = runCatching { number(matched.groupValues[1]) * (conversion?.second ?: "1").toBigDecimal() }.getOrNull()
        if (amount == null || amount.signum() <= 0) return Ingredient(original, canonical(remainder), optional = optional, needsReview = true)
        val unit = conversion?.first ?: "piece"
        return Ingredient(original, canonical(remainder, unit), display(amount), unit, optional)
    }
    fun preview(recipe: Recipe, servings: Int, pantry: List<PantryItem>): RecipePreview {
        require(servings in 1..1000) { "Choose between 1 and 1000 servings." }
        val base = requireNotNull(recipe.baseServings) { "This recipe's base yield needs review before scaling." }
        val factor = servings.toBigDecimal().divide(base.toBigDecimal(), MathContext.DECIMAL128)
        val scaled = recipe.ingredients.map { item -> item.copy(amount = item.amount?.let { display(number(it) * factor) }) }
        val available = pantry.groupBy { canonical(it.name, it.unit) to it.unit }.mapValues { (_, items) -> items.fold(BigDecimal.ZERO) { sum, item -> sum + number(item.amount) } }.toMutableMap()
        val missing = mutableListOf<MissingItem>()
        scaled.filterNot { it.optional }.forEach { item ->
            if (item.amount == null || item.needsReview) missing += MissingItem(item.name, null, item.unit, true)
            else {
                val key = canonical(item.name, item.unit) to item.unit
                if (key !in available && available.keys.any { it.first == key.first }) {
                    missing += MissingItem(item.name, item.amount, item.unit, true)
                    return@forEach
                }
                val stock = available[key] ?: BigDecimal.ZERO
                val needed = number(requireNotNull(item.amount))
                val used = stock.min(needed)
                available[key] = stock - used
                if (needed > used) missing += MissingItem(item.name, display(needed - used), item.unit)
            }
        }
        val combined = missing.groupBy { Triple(it.name, it.unit, it.needsReview) }.map { (key, items) ->
            MissingItem(key.first, if (items.any { it.amount == null }) null else display(items.fold(BigDecimal.ZERO) { sum, item -> sum + number(item.amount!!) }), key.second, key.third)
        }
        return RecipePreview(servings, scaled, combined, recipe.warnings)
    }
}
