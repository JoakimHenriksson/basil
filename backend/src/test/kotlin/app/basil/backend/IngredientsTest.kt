package app.basil.backend

import app.basil.contracts.*
import kotlin.test.*

class IngredientsTest {
    @Test fun `mixed fractions convert to base units`() {
        val parsed = Ingredients.parse("2 1/2 dl vetemjöl")
        assertEquals("250", parsed.amount)
        assertEquals("ml", parsed.unit)
        assertEquals("flour", parsed.name)
    }
    @Test fun `unicode fractions and decimal commas work`() {
        assertEquals("2.5", Ingredients.parse("½ tsk salt").amount)
        assertEquals("1500", Ingredients.parse("1,5 kg ris").amount)
        assertEquals("150", Ingredients.parse("1½ dl mjölk").amount)
    }
    @Test fun `a count pepper is distinct from paprika spice`() {
        assertEquals("bell pepper", Ingredients.parse("3 paprika").name)
        assertEquals("paprika", Ingredients.parse("3 g paprika").name)
    }
    @Test fun `unknown quantities and ranges remain visible`() {
        assertTrue(Ingredients.parse("salt efter smak").needsReview)
        assertNull(Ingredients.parse("2–3 ägg").amount)
        assertTrue(Ingredients.parse("2 burkar krossade tomater").needsReview)
    }
    @Test fun `scaling preserves instructions and subtracts stock once`() {
        val recipe = recipe(listOf(Ingredients.parse("200 g rice"), Ingredients.parse("150 g rice")))
        val result = Ingredients.preview(recipe, 4, listOf(PantryItem("lot", "rice", "80", "g")))
        assertEquals("270", result.missing.single().amount)
        assertEquals("Keep oven at 180°C.", recipe.instructions.single())
        assertEquals("100", Ingredients.preview(recipe, 2, emptyList()).ingredients.first().amount)
    }
    @Test fun `partial pieces scale without rounding shopping stock`() {
        assertEquals("1.5", Ingredients.preview(recipe(listOf(Ingredients.parse("3 paprika"))), 2, emptyList()).ingredients.single().amount)
    }
    @Test fun `incompatible pantry dimensions require review`() {
        val result = Ingredients.preview(recipe(listOf(Ingredients.parse("2 dl vetemjöl"))), 4, listOf(PantryItem("lot", "flour", "500", "g")))
        assertTrue(result.missing.single().needsReview)
    }
    @Test fun `optional ingredients do not generate a shortage`() {
        val result = Ingredients.preview(recipe(listOf(Ingredients.parse("1 egg (optional)"))), 4, emptyList())
        assertTrue(result.missing.isEmpty())
    }
    @Test fun `invalid servings are rejected`() {
        assertFailsWith<IllegalArgumentException> { Ingredients.preview(recipe(emptyList()), 0, emptyList()) }
    }
    private fun recipe(lines: List<Ingredient>) = Recipe("recipe", "Dinner", "https://example.com/recipe", 4, "4 portions", lines, listOf("Keep oven at 180°C."), "2026-10-01T00:00:00Z")
}
