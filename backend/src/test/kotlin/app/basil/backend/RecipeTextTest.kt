package app.basil.backend
import kotlin.test.*

class RecipeTextTest {
    @Test fun `scanned text can be normalized reviewed and stored without AI`() {
        val draft = RecipeText.draft("Dinner\n4 portioner\nIngredienser\n2 eggs\n300 g crushed tomatoes\nGör så här\nCook everything.", null)
        assertEquals("Dinner", draft.title); assertEquals(4, draft.baseServings)
        assertEquals(listOf("2 eggs", "300 g crushed tomatoes"), draft.ingredientLines)
        assertEquals(listOf("Cook everything."), draft.instructions)
        assertFailsWith<IllegalArgumentException> { RecipeText.draft("Unreadable recipe", null) }
    }
}
