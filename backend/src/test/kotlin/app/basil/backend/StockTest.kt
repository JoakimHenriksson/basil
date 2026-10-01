package app.basil.backend

import app.basil.contracts.*
import java.nio.file.Files
import kotlin.test.*

class StockTest {
    private val barcode = "4006381333931"
    private fun fixture(block: (Vault) -> Unit) {
        val dir = Files.createTempDirectory("basil-stock")
        try { block(Vault(dir.resolve("vault.json"))) }
        finally { Files.walk(dir).use { it.sorted(Comparator.reverseOrder()).forEach(Files::delete) } }
    }
    private fun product(vault: Vault) = vault.rememberProduct(Product(barcode, "Tomato tin", "krossade tomater", "425", "g"))
    @Test fun `GTIN aliases share identity and invalid check digits fail`() {
        assertEquals("04006381333931", Stock.barcode(barcode))
        assertEquals(Stock.barcode(barcode), Stock.barcode("04006381333931"))
        assertFailsWith<IllegalArgumentException> { Stock.barcode("4006381333932") }
        assertFailsWith<IllegalArgumentException> { Stock.barcode("https://example.com") }
    }
    @Test fun `425 gram tin is an ingredient and repeated effects are idempotent`() = fixture { vault ->
        val saved = product(vault)
        assertEquals("crushed tomatoes", saved.ingredient)
        val add = StockRequest("add", barcode, "add")
        val added = vault.stock(add)
        assertEquals(added, vault.stock(add))
        assertEquals("425", vault.pantry().single().amount)
        val use = StockRequest("use", barcode, "use")
        val used = vault.stock(use)
        assertTrue(vault.pantry().isEmpty())
        assertEquals(used, vault.stock(use))
        assertEquals(2, vault.movements().size)
        assertFailsWith<IllegalArgumentException> { vault.stock(add.copy(kind = "use")) }
    }
    @Test fun `partial use defaults to remainder and can be undone once`() = fixture { vault ->
        product(vault)
        vault.stock(StockRequest("add", barcode, "add"))
        val partial = vault.stock(StockRequest("partial", barcode, "use", "125"))
        assertEquals("300", vault.pantry().single().amount)
        assertFailsWith<IllegalArgumentException> { vault.undoStock("add", UndoStockRequest("invalid-undo")) }
        vault.stock(StockRequest("rest", barcode, "use"))
        assertTrue(vault.pantry().isEmpty())
        val undo = vault.undoStock("rest", UndoStockRequest("undo-rest"))
        assertEquals(undo, vault.undoStock("rest", UndoStockRequest("undo-rest")))
        assertEquals("300", vault.pantry().single().amount)
        assertFailsWith<IllegalArgumentException> { vault.undoStock("rest", UndoStockRequest("duplicate")) }
        vault.undoStock(partial.id, UndoStockRequest("undo-partial"))
        assertEquals("425", vault.pantry().single().amount)
    }
    @Test fun `use spans packages and insufficient stock has no effects`() = fixture { vault ->
        product(vault)
        vault.stock(StockRequest("a", barcode, "add"))
        vault.stock(StockRequest("b", barcode, "add"))
        val before = vault.pantry()
        assertFailsWith<IllegalArgumentException> { vault.stock(StockRequest("too-much", barcode, "use", "900")) }
        assertEquals(before, vault.pantry())
        vault.stock(StockRequest("partial", barcode, "use", "500"))
        assertEquals("350", vault.pantry().single().amount)
        assertEquals(3, vault.movements().size)
        val reopened = Vault(vault.mediaDirectory.parent.resolve("vault.json"))
        assertEquals(vault.pantry(), reopened.pantry())
        assertEquals(vault.movements(), reopened.movements())
        assertEquals(vault.products(), reopened.products())
    }
    @Test fun `cooking explicitly reconciles scanned uses without double subtraction`() = fixture { vault ->
        product(vault)
        vault.stock(StockRequest("add", barcode, "add"))
        vault.stock(StockRequest("use", barcode, "use", "100"))
        val recipe = vault.create(RecipeEditRequest("Tomato dinner", 2, listOf("300 g crushed tomatoes"), listOf("Cook.")))
        val request = CookRequest("cook", 2, 1, listOf("use"))
        val cooked = vault.cook(recipe.id, request)
        assertEquals("125", vault.pantry().single().amount)
        assertEquals("-200", cooked.deltas.single().amount)
        assertEquals(cooked, vault.cook(recipe.id, request))
        assertFailsWith<IllegalArgumentException> { vault.undoStock("use", UndoStockRequest("undo-use")) }
        assertFailsWith<IllegalArgumentException> { vault.cook(recipe.id, request.copy(id = "again")) }
        vault.undoStock("cook", UndoStockRequest("undo-cook"))
        assertEquals("325", vault.pantry().single().amount)
        vault.undoStock("use", UndoStockRequest("undo-use"))
        assertEquals("425", vault.pantry().single().amount)
    }
    @Test fun `unlinked uses are never guessed to belong to a meal`() = fixture { vault ->
        product(vault); vault.stock(StockRequest("add", barcode, "add"))
        vault.stock(StockRequest("use", barcode, "use", "100"))
        val recipe = vault.create(RecipeEditRequest("Meal", 1, listOf("300 g crushed tomatoes"), listOf("Cook.")))
        vault.cook(recipe.id, CookRequest("cook", 1, 1))
        assertEquals("25", vault.pantry().single().amount)
    }
    @Test fun `uncertain recipes cannot consume stock`() = fixture { vault ->
        val recipe = vault.create(RecipeEditRequest("Meal", 1, listOf("salt to taste"), listOf("Cook.")))
        assertFailsWith<IllegalArgumentException> { vault.cook(recipe.id, CookRequest("cook", 1, 1)) }
        assertTrue(vault.movements().isEmpty())
    }
    @Test fun `product lookup quantities are drafts and not inventory`() {
        val draft = OpenFoodFactsLookup.parse(barcode, """{"product":{"product_name":"Tomatoes","product_quantity":0.425,"product_quantity_unit":"kg"}}""")
        assertEquals("425", draft.packageAmount)
        assertEquals("g", draft.unit)
        assertTrue(draft.warning.isNotBlank())
        assertNull(OpenFoodFactsLookup.parse(barcode, """{"product":{"quantity":"2 cans"}}""").packageAmount)
    }
}
