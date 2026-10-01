package app.basil.backend

import app.basil.contracts.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.util.zip.ZipInputStream
import kotlin.test.*

class RecipeLibraryTest {
    private val draft = RecipeEditRequest("Dinner", 2, listOf("2 eggs", "1 tbsp butter"), listOf("Cook."), "Quick dinner")
    private fun fixture(block: (Vault) -> Unit) {
        val directory = Files.createTempDirectory("basil-library")
        try { block(Vault(directory.resolve("vault.json"))) }
        finally { Files.walk(directory).use { it.sorted(Comparator.reverseOrder()).forEach(Files::delete) } }
    }
    @Test fun `edits retain source photos and original revisions`() = fixture { vault ->
        val original = Recipe("recipe", "Original", "https://example.com/recipe", 2, "2", listOf(Ingredients.parse("2 eggs")), listOf("Cook."), "date", originalImageUrl = "https://example.com/photo.png", imagePath = "/v1/media/photo.png")
        vault.save(original)
        val edited = vault.edit(original.id, draft.copy(ingredientLines = listOf("3 eggs")))!!
        assertEquals(original.sourceUrl, edited.sourceUrl)
        assertEquals(original.imagePath, edited.imagePath)
        assertEquals(2, edited.revision)
        assertEquals("3", edited.ingredients.first().amount)
        assertEquals(listOf(original, edited), vault.history(original.id))
        assertFailsWith<RecipeConflict> { vault.edit(original.id, draft) }
        assertEquals(edited, vault.recipe(original.id))
    }
    @Test fun `manual recipes favourites and revisions survive storage reopening`() = fixture { vault ->
        val first = vault.create(draft)
        val second = vault.create(draft.copy(title = "Lunch"))
        assertNotEquals(first.id, second.id)
        val favourite = vault.favourite(first.id, true)!!
        assertTrue(favourite.favourite)
        assertEquals(1, favourite.revision)
        val file = vault.mediaDirectory.parent.resolve("vault.json")
        assertTrue(Vault(file).recipe(first.id)!!.favourite)
        assertTrue(vault.deleteRecipe(first.id))
        assertTrue(vault.history(first.id).isEmpty())
        assertEquals(second, vault.recipes().single())
    }
    @Test fun `range scaling keeps endpoints and allocates the conservative amount`() {
        val recipe = Recipe("id", "Dinner", "", 4, "4", listOf(Ingredients.parse("2–3 eggs")), listOf("Cook."), "date")
        val result = Ingredients.preview(recipe, 2, listOf(PantryItem("lot", "eggs", "1", "piece")))
        assertEquals("1", result.ingredients.single().amount)
        assertEquals("1.5", result.ingredients.single().maxAmount)
        assertEquals("0.5", result.missing.single().amount)
        assertTrue(result.warnings.any { it.contains("upper end") })
        assertTrue(Ingredients.parse("3–2 eggs").needsReview)
    }
    @Test fun `metric spoon units mass units and preparation names normalize`() {
        assertEquals("30", Ingredients.parse("2 tbsp butter").amount)
        assertEquals("453.59237", Ingredients.parse("1 lb flour").amount)
        assertEquals("eggs", Ingredients.parse("2 eggs, beaten").name)
        assertTrue(Ingredients.parse("1 cup flour").needsReview)
        assertTrue(Ingredients.parse("2 cans crushed tomatoes").needsReview)
    }
    @Test fun `export includes recipe JSON source attribution and retained photos`() = fixture { vault ->
        val bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 13, 10, 26, 10, 0, 0, 0, 0)
        val images = RecipeImages(vault.mediaDirectory, ImageFetcher { Download(bytes, "image/png") })
        val path = images.preserve("https://example.com/photo.png")
        val recipe = Recipe("id", "Dinner", "https://example.com/dinner", 2, "2", listOf(Ingredients.parse("2 eggs")), listOf("Cook."), "date", imagePath = path)
        vault.save(recipe)
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(RecipeArchive.export(vault, images).inputStream()).use { zip ->
            while (true) { val entry = zip.nextEntry ?: break; entries[entry.name] = zip.readBytes() }
        }
        assertEquals(recipe.sourceUrl, Json.decodeFromString<RecipeExport>(entries.getValue("recipes.json").decodeToString()).recipes.single().sourceUrl)
        assertContentEquals(bytes, entries.getValue("media/${path.substringAfterLast('/')}"))
    }
    @Test fun `HTTP edits reject stale clients and export produces zip`() = testApplication {
        val directory = Files.createTempDirectory("basil-library-api")
        application { basil(Vault(directory.resolve("vault.json")), extractor = null) }
        try {
            val response = client.post("/v1/recipes") { contentType(ContentType.Application.Json); setBody(Json.encodeToString(RecipeEditRequest.serializer(), draft)) }
            assertEquals(HttpStatusCode.Created, response.status)
            val saved = Json.decodeFromString<Recipe>(response.bodyAsText())
            suspend fun update() = client.put("/v1/recipes/${saved.id}") { contentType(ContentType.Application.Json); setBody(Json.encodeToString(RecipeEditRequest.serializer(), draft)) }
            assertEquals(HttpStatusCode.OK, update().status)
            assertEquals(HttpStatusCode.Conflict, update().status)
            val export = client.get("/v1/recipes/export")
            assertEquals(HttpStatusCode.OK, export.status)
            assertEquals("application/zip", export.headers[HttpHeaders.ContentType]?.substringBefore(';'))
        } finally { Files.walk(directory).use { it.sorted(Comparator.reverseOrder()).forEach(Files::delete) } }
    }
}
