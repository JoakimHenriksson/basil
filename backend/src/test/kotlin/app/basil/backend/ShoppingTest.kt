package app.basil.backend

import app.basil.contracts.*
import java.nio.file.Files
import kotlin.test.*
import io.ktor.server.testing.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ShoppingTest {
    private fun fixture(block: (Vault) -> Unit) {
        val dir = Files.createTempDirectory("basil-shopping")
        try { block(Vault(dir.resolve("vault.json"))) }
        finally { Files.walk(dir).use { it.sorted(Comparator.reverseOrder()).forEach(Files::delete) } }
    }
    private fun recipe(vault: Vault, name: String, amount: Int) = vault.create(RecipeEditRequest(name, 2, listOf("$amount g rice"), listOf("Cook.")))
    private fun select(vault: Vault, recipe: Recipe, id: String) = vault.selectMeal(SelectMealRequest(id, recipe.id, recipe.revision, 2, vault.shopping().version))
    @Test fun `two meals allocate stock once and persist selected revisions`() = fixture { vault ->
        val a = recipe(vault, "Lunch", 200)
        val b = recipe(vault, "Dinner", 150)
        vault.add(PantryRequest("rice", "80", "g"))
        select(vault, a, "a")
        val list = select(vault, b, "b")
        assertEquals("270", list.items.single().amount)
        assertEquals(listOf("a", "b"), list.items.single().selectionIds)
        vault.edit(a.id, RecipeEditRequest("Changed", 2, listOf("500 g rice"), listOf("Cook.")))
        assertEquals(list, vault.shopping())
        assertEquals(list, Vault(vault.mediaDirectory.parent.resolve("vault.json")).shopping())
        vault.deleteRecipe(a.id)
        assertEquals("Lunch", vault.shopping().selections.first().recipe.title)
        assertEquals("270", vault.shopping().items.single().amount)
    }
    @Test fun `serving changes and pantry changes reset checked requirements without reviving old checks`() = fixture { vault ->
        select(vault, recipe(vault, "Meal", 200), "a")
        val list = vault.shopping()
        vault.checkShoppingItem(list.items.single().id, CheckShoppingRequest(true, list.version))
        assertTrue(vault.shopping().items.single().checked)
        vault.changeSelection("a", SelectionServingsRequest(4, vault.shopping().version))
        assertEquals("400", vault.shopping().items.single().amount)
        assertFalse(vault.shopping().items.single().checked)
        vault.changeSelection("a", SelectionServingsRequest(2, vault.shopping().version))
        assertFalse(vault.shopping().items.single().checked)
        vault.checkShoppingItem(list.items.single().id, CheckShoppingRequest(true, vault.shopping().version))
        val stock = vault.add(PantryRequest("rice", "200", "g"))
        assertTrue(vault.shopping().items.isEmpty())
        vault.remove(stock.id)
        assertFalse(vault.shopping().items.single().checked)
    }
    @Test fun `manual items survive recalculation and add retries do not duplicate`() = fixture { vault ->
        val request = ManualShoppingRequest("extra", "Dish soap", vault.shopping().version)
        val added = vault.addShoppingItem(request)
        assertEquals(added, vault.addShoppingItem(request))
        val item = added.items.single()
        vault.checkShoppingItem(item.id, CheckShoppingRequest(true, added.version))
        val meal = recipe(vault, "Dinner", 150)
        val selection = SelectMealRequest("meal", meal.id, meal.revision, 2, vault.shopping().version)
        val list = vault.selectMeal(selection)
        assertEquals(list, vault.selectMeal(selection))
        assertTrue(list.items.first { it.manual }.checked)
        vault.removeSelection("meal", list.version)
        assertEquals("Dish soap", vault.shopping().items.single().name)
        vault.removeShoppingItem(item.id, vault.shopping().version)
        assertTrue(vault.shopping().items.isEmpty())
    }
    @Test fun `stale list and stale recipe writes are rejected without effects`() = fixture { vault ->
        val meal = recipe(vault, "Dinner", 150)
        val version = vault.shopping().version
        vault.add(PantryRequest("rice", "10", "g"))
        assertFailsWith<ShoppingConflict> { vault.selectMeal(SelectMealRequest("meal", meal.id, 1, 2, version)) }
        vault.edit(meal.id, RecipeEditRequest("Updated", 2, listOf("200 g rice"), listOf("Cook.")))
        assertFailsWith<RecipeConflict> { vault.selectMeal(SelectMealRequest("meal", meal.id, 1, 2, vault.shopping().version)) }
        assertTrue(vault.shopping().selections.isEmpty())
    }
    @Test fun `optional and ambiguous requirements stay honest`() = fixture { vault ->
        val meal = vault.create(RecipeEditRequest("Meal", 2, listOf("1 cup flour", "2 eggs (optional)", "2–3 paprika"), listOf("Cook.")))
        select(vault, meal, "meal")
        val list = vault.shopping()
        assertEquals(2, list.items.size)
        assertTrue(list.items.any { it.needsReview && it.amount == null })
        assertEquals("3", list.items.first { it.name == "bell pepper" }.amount)
        assertTrue(list.warnings.any { it.contains("upper end") })
        assertTrue(vault.pantry().isEmpty())
    }
    @Test fun `shopping HTTP returns a conflict for stale check-off`() = testApplication {
        val dir = Files.createTempDirectory("basil-shopping-api")
        val vault = Vault(dir.resolve("vault.json"))
        application { basil(vault, extractor = null) }
        try {
            val before = Json.decodeFromString<ShoppingList>(client.get("/v1/shopping").bodyAsText())
            val added = client.post("/v1/shopping/items") { contentType(ContentType.Application.Json); setBody(Json.encodeToString(ManualShoppingRequest("extra", "Soap", before.version))) }
            assertEquals(HttpStatusCode.OK, added.status)
            val list = Json.decodeFromString<ShoppingList>(added.bodyAsText())
            val rejected = client.put("/v1/shopping/items/${list.items.single().id}") { contentType(ContentType.Application.Json); setBody(Json.encodeToString(CheckShoppingRequest(true, before.version))) }
            assertEquals(HttpStatusCode.Conflict, rejected.status)
            assertFalse(vault.shopping().items.single().checked)
        } finally { Files.walk(dir).use { it.sorted(Comparator.reverseOrder()).forEach(Files::delete) } }
    }
}
