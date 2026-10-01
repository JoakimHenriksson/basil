package app.basil.backend

import app.basil.contracts.*
import java.nio.file.Files
import kotlin.test.*

class CostsTest {
    private fun fixture(block: (Vault) -> Unit) {
        val dir = Files.createTempDirectory("basil-costs")
        try { block(Vault(dir.resolve("vault.json"))) }
        finally { Files.walk(dir).use { it.sorted(Comparator.reverseOrder()).forEach(Files::delete) } }
    }
    private fun receipt(id: String = "receipt", action: String = "none", linked: String? = null) = ReceiptRequest(id, "ICA", "2026-10-02", "SEK", 2000,
        listOf(ReceiptLine("tomatoes", "Tomato tin", 2000, "crushed tomatoes", "425", "g", stockAction = action, linkedAddId = linked)))
    @Test fun `one barcode and its receipt record one stock addition and one expense`() = fixture { vault ->
        vault.rememberProduct(Product("4006381333931", "Tomato tin", "crushed tomatoes", "425", "g"))
        vault.stock(StockRequest("scan", "4006381333931", "add"))
        val request = receipt(action = "link", linked = "scan")
        val accepted = vault.acceptReceipt(request)
        assertEquals(accepted, vault.acceptReceipt(request))
        assertEquals("425", vault.pantry().single().amount)
        assertEquals(1, vault.movements().size)
        assertEquals(2000, vault.budget("2026-10").spentMinor)
        assertFailsWith<IllegalArgumentException> { vault.acceptReceipt(request.copy(id = "duplicate")) }
        assertFailsWith<IllegalArgumentException> { vault.acceptReceipt(request.copy(id = "other", reference = "another-real-receipt")) }
        assertEquals(1, vault.purchases().size)
    }
    @Test fun `one receipt line can reconcile multiple separately scanned packages`() = fixture { vault ->
        vault.rememberProduct(Product("4006381333931", "Tin", "crushed tomatoes", "425", "g"))
        vault.stock(StockRequest("first", "4006381333931", "add")); vault.stock(StockRequest("second", "4006381333931", "add"))
        val request = receipt().copy(totalMinor = 4000, lines = listOf(receipt().lines.single().copy(totalMinor = 4000, quantity = "850", packages = 2, stockAction = "link", linkedAddIds = listOf("first", "second"))))
        vault.acceptReceipt(request)
        assertEquals("850", Ingredients.display(vault.pantry().sumOf { Ingredients.number(it.amount) }))
        assertEquals(2, vault.movements().size)
        assertEquals(4000, vault.budget("2026-10").spentMinor)
    }
    @Test fun `explicit receipt additions and price corrections never repeat stock`() = fixture { vault ->
        val purchase = vault.acceptReceipt(receipt(action = "add"))
        assertEquals("425", vault.pantry().single().amount)
        val corrected = vault.correctReceipt(purchase.receipt.id, ReceiptCorrection(1, "ICA", "2026-10-02", 1500, mapOf("tomatoes" to 1500)))
        assertEquals(2, corrected.revision)
        assertEquals(1500, vault.budget("2026-10").spentMinor)
        assertEquals("425", vault.pantry().single().amount)
        assertEquals(2, vault.purchaseHistory(purchase.receipt.id).size)
        assertFailsWith<IllegalArgumentException> { vault.correctReceipt(purchase.receipt.id, ReceiptCorrection(1, "ICA", "2026-10-02", 1000, mapOf("tomatoes" to 1000))) }
        assertEquals(corrected, Vault(vault.mediaDirectory.parent.resolve("vault.json")).purchases().single())
    }
    @Test fun `meal quantities and bought packages are distinct with explicit rounding`() = fixture { vault ->
        vault.acceptReceipt(receipt())
        val recipe = vault.create(RecipeEditRequest("Dinner", 2, listOf("300 g crushed tomatoes"), listOf("Cook.")))
        val cost = vault.recipeCost(recipe.id, 2)
        assertEquals(1412, cost.knownMinor); assertEquals(706, cost.perServingMinor)
        vault.selectMeal(SelectMealRequest("meal", recipe.id, 1, 2, vault.shopping().version))
        assertEquals(2000, vault.shoppingCost().knownMinor)
        vault.add(PantryRequest("crushed tomatoes", "250", "g"))
        assertEquals(2000, vault.shoppingCost().knownMinor)
        assertEquals(1412, vault.recipeCost(recipe.id, 2).knownMinor)
        assertEquals(2824, vault.recipeCost(recipe.id, 4).knownMinor)
    }
    @Test fun `missing prices and discounts do not become invented verified costs`() = fixture { vault ->
        val discounted = receipt().copy(totalMinor = 1500, lines = receipt().lines + ReceiptLine("discount", "Coupon", -500))
        vault.acceptReceipt(discounted)
        assertTrue(Costs.prices(vault.purchases()).isEmpty())
        val recipe = vault.create(RecipeEditRequest("Dinner", 2, listOf("300 g crushed tomatoes", "2 eggs"), listOf("Cook.")))
        assertEquals(listOf("crushed tomatoes", "eggs"), vault.recipeCost(recipe.id, 2).missingPrices)
        assertEquals(1500, vault.budget("2026-10").spentMinor)
    }
    @Test fun `unbalanced receipts have no inventory or spending effects and refunds reduce budgets`() = fixture { vault ->
        assertFailsWith<IllegalArgumentException> { vault.acceptReceipt(receipt(action = "add").copy(totalMinor = 1999)) }
        assertTrue(vault.pantry().isEmpty()); assertTrue(vault.purchases().isEmpty())
        vault.setBudget(BudgetSettings("SEK", 10000))
        vault.acceptReceipt(receipt())
        vault.acceptReceipt(ReceiptRequest("refund", "ICA", "2026-10-02", "SEK", -500, listOf(ReceiptLine("refund", "Refund", -500))))
        assertEquals(8500, vault.budget("2026-10").remainingMinor)
        assertEquals(0, vault.budget("2026-09").spentMinor)
        assertFailsWith<IllegalArgumentException> { vault.setBudget(BudgetSettings("EUR", 10000)) }
    }
    @Test fun `OCR receipt draft extracts printed prices total date and discounts`() {
        val draft = Costs.draft("ICA\n2026-10-02\nTomato tin 20,00\nCoupon -5,00\nTOTALT 15,00\nMoms 3,00")
        assertEquals("2026-10-02", draft.purchasedOn)
        assertEquals(1500, draft.totalMinor)
        assertEquals(listOf(2000L, -500L), draft.lines.map { it.totalMinor })
        assertTrue(draft.lines.all { it.stockAction == "none" })
        assertNull(Costs.draft("ICA\nTomato tin 20,00").totalMinor)
        assertEquals(1995, parseMinor("19,95")); assertEquals(-500, parseMinor("-5"))
        assertFails { parseMinor("19.999") }
    }
}
