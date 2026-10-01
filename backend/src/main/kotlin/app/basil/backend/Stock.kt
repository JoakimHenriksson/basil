package app.basil.backend

import app.basil.contracts.*
import java.math.BigDecimal

object Stock {
    fun barcode(value: String): String {
        val code = value.trim()
        require(code.matches(Regex("(?:[0-9]{8}|[0-9]{12,14})"))) { "Enter an EAN or UPC product barcode." }
        val sum = code.dropLast(1).reversed().mapIndexed { i, c -> c.digitToInt() * if (i % 2 == 0) 3 else 1 }.sum()
        require((10 - sum % 10) % 10 == code.last().digitToInt()) { "That barcode's check digit is invalid." }
        return code.padStart(14, '0')
    }
    fun positive(value: String): BigDecimal {
        require(value.length <= 30) { "The amount is too long." }
        val number = Ingredients.number(value)
        require(number.signum() > 0) { "Enter a positive amount." }
        return number
    }
    fun consume(pantry: List<PantryItem>, name: String, unit: String, amount: BigDecimal, barcode: String? = null): List<StockDelta> {
        var remaining = amount
        val deltas = mutableListOf<StockDelta>()
        pantry.filter { it.unit == unit && Ingredients.canonical(it.name, it.unit) == name && (barcode == null || it.barcode == barcode) }.forEach { lot ->
            val used = Ingredients.number(lot.amount).min(remaining)
            if (used.signum() > 0) { deltas += StockDelta(lot, Ingredients.display(-used)); remaining -= used }
        }
        require(remaining.signum() == 0) { "There isn't enough tracked stock. Correct the amount or add the missing stock first." }
        return deltas
    }
    fun apply(pantry: List<PantryItem>, deltas: List<StockDelta>): List<PantryItem> {
        val lots = pantry.associateBy { it.id }.toMutableMap()
        deltas.forEach { delta ->
            val current = lots[delta.lot.id]
            val amount = (current?.amount?.let(Ingredients::number) ?: BigDecimal.ZERO) + Ingredients.number(delta.amount)
            require(amount.signum() >= 0) { "This stock has been used since then. Undo the later use first." }
            if (amount.signum() == 0) lots.remove(delta.lot.id)
            else lots[delta.lot.id] = (current ?: delta.lot).copy(amount = Ingredients.display(amount))
        }
        return lots.values.toList()
    }
}
