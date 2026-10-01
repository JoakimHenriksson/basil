package app.basil.backend

import kotlin.test.*

class ImporterTest {
    private val importer = RecipeImporter()
    @Test fun `graph recipe with mixed fractions and nested steps imports`() {
        val recipe = importer.parse("""
            <script type="application/ld+json">
            {"@graph":[{"@type":"WebPage"},{"@type":["Recipe"],"name":"Pancakes","recipeYield":"4 portions",
            "recipeIngredient":["2 1/2 dl vetemjöl","3 ägg"],"recipeInstructions":[{"@type":"HowToSection","itemListElement":[{"text":"Mix."},{"text":"Cook."}]}]}]}
            </script>
        """.trimIndent(), "https://example.com/pancakes")
        assertEquals("Pancakes", recipe.title)
        assertEquals(4, recipe.baseServings)
        assertEquals("250", recipe.ingredients.first().amount)
        assertEquals(listOf("Mix.", "Cook."), recipe.instructions)
    }
    @Test fun `Arla entity-encoded script type is recognized`() {
        val recipe = importer.parse("""<script type="application/ld&#x2B;json">[{"@type":"Recipe","name":"Pannkakor","recipeYield":"4 port","recipeIngredient":["½ tsk salt"],"recipeInstructions":[{"text":"Vispa."}]}]</script>""", "https://www.arla.se/recept/example/")
        assertEquals(4, recipe.baseServings)
        assertEquals("2.5", recipe.ingredients.single().amount)
    }
    @Test fun `blog recipe text is preserved with a review warning`() {
        val recipe = importer.parse("""<h1>Pepparbiff</h1><article><p>Skriv ut</p><div class="entry-content"><p><strong>4–6 portioner</strong><br>1,2 kg biff<br>2 röda paprikor<br>Olja till stekning</p><p>Gör så här: Stek och servera.</p><div class="sharedaddy"><p>Tweet</p></div></div></article>""", "https://example.com/pepparbiff")
        assertNull(recipe.baseServings)
        assertEquals(3, recipe.ingredients.size)
        assertTrue(recipe.ingredients.last().needsReview)
        assertEquals(listOf("Stek och servera."), recipe.instructions)
        assertTrue(recipe.warnings.isNotEmpty())
    }
    @Test fun `non recipe pages fail with useful feedback`() {
        assertFailsWith<IllegalArgumentException> { importer.parse("<h1>Search</h1>", "https://example.com/search") }
    }
}
