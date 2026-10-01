package app.basil.backend

import app.basil.contracts.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import java.net.InetAddress
import java.nio.file.Files
import kotlin.test.*

class VaultAndApiTest {
    @Test fun `saved recipes and pantry survive reopening the vault`() {
        val file = Files.createTempDirectory("basil-test").resolve("vault.json")
        try {
            val first = Vault(file)
            val recipe = Recipe("id", "Dinner", "https://example.com/dinner", 2, "2", listOf(Ingredients.parse("3 eggs")), listOf("Cook."), "2026-10-01T00:00:00Z")
            first.save(recipe)
            assertEquals("id", first.save(recipe.copy(id = "duplicate")).id)
            first.add(PantryRequest("eggs", "1", "piece"))
            val reopened = Vault(file)
            assertEquals(listOf(recipe), reopened.recipes())
            assertEquals("1", reopened.pantry().single().amount)
            assertEquals(6, reopened.setYield("id", 6)?.baseServings)
            assertEquals(6, Vault(file).recipe("id")?.baseServings)
        } finally { Files.deleteIfExists(file); Files.deleteIfExists(file.parent) }
    }
    @Test fun `invalid pantry input is rejected`() {
        val file = Files.createTempDirectory("basil-test").resolve("vault.json")
        try {
            assertFailsWith<IllegalArgumentException> { Vault(file).add(PantryRequest("rice", "-1", "g")) }
            assertFailsWith<IllegalArgumentException> { Vault(file).add(PantryRequest("rice", "1", "kg")) }
        } finally { Files.deleteIfExists(file); Files.deleteIfExists(file.parent) }
    }
    @Test fun `private destinations and non HTTPS links are rejected`() {
        listOf("127.0.0.1", "10.0.0.1", "169.254.169.254", "100.64.0.1", "::1", "fc00::1").forEach { assertFalse(PublicPageFetcher.isPublicAddress(InetAddress.getByName(it))) }
        assertTrue(PublicPageFetcher.isPublicAddress(InetAddress.getByName("8.8.8.8")))
        assertFailsWith<IllegalArgumentException> { PublicPageFetcher.validateUrl("https://example.com/bad link") }
        assertFailsWith<IllegalArgumentException> { PublicPageFetcher.validateUrl("http://example.com/recipe") }
        assertFailsWith<IllegalArgumentException> { PublicPageFetcher.validateUrl("https://user:password@example.com/recipe") }
    }
    @Test fun `HTTP slice imports persists scales and returns missing ingredients`() = testApplication {
        val file = Files.createTempDirectory("basil-api-test").resolve("vault.json")
        application {
            basil(Vault(file), PageFetcher { url -> url to """<script type="application/ld+json">{"@type":"Recipe","name":"Dinner","recipeYield":"4","recipeIngredient":["3 eggs"],"recipeInstructions":["Cook."]}</script>""" })
        }
        try {
            val imported = client.post("/v1/recipes/import") { contentType(ContentType.Application.Json); setBody("""{"url":"https://example.com/dinner"}""") }
            assertEquals(HttpStatusCode.Created, imported.status)
            val recipe = Json.decodeFromString<Recipe>(imported.bodyAsText())
            val pantry = client.post("/v1/pantry") { contentType(ContentType.Application.Json); setBody("""{"name":"eggs","amount":"1","unit":"piece"}""") }
            assertEquals(HttpStatusCode.Created, pantry.status)
            val invalidAmount = client.post("/v1/pantry") { contentType(ContentType.Application.Json); setBody("""{"name":"eggs","amount":"1/0","unit":"piece"}""") }
            assertEquals(HttpStatusCode.BadRequest, invalidAmount.status)
            assertTrue(invalidAmount.bodyAsText().contains("denominator"))
            val preview = client.post("/v1/recipes/${recipe.id}/preview") { contentType(ContentType.Application.Json); setBody("""{"servings":2}""") }
            assertEquals(HttpStatusCode.OK, preview.status)
            assertEquals("0.5", Json.decodeFromString<RecipePreview>(preview.bodyAsText()).missing.single().amount)
            assertTrue(client.get("/v1/recipes").bodyAsText().contains("Dinner"))
        } finally { Files.deleteIfExists(file); Files.deleteIfExists(file.parent) }
    }
}
