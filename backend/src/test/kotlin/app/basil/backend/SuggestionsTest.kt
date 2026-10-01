package app.basil.backend

import app.basil.contracts.*
import kotlin.test.*
import java.nio.file.Files
import io.ktor.server.testing.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType

class SuggestionsTest {
    private fun recipe(id: String, ingredient: String, base: Int? = 2) = Recipe(id, id, "", base, "", listOf(Ingredients.parse(ingredient)), listOf("Cook."), "date")
    @Test fun `saved recipes rank ready close and uncertain with deterministic shortages`() {
        val recipes = listOf(recipe("check", "salt to taste"), recipe("close", "300 g rice"), recipe("ready", "2 eggs"), recipe("yield", "2 eggs", null))
        val pantry = listOf(PantryItem("eggs", "eggs", "2", "piece"), PantryItem("rice", "rice", "80", "g"))
        val matches = Suggestions.saved(recipes, pantry, 2)
        assertEquals(listOf("ready", "close", "check", "yield"), matches.map { it.recipeId })
        assertEquals("Ready to cook", matches.first().status)
        assertEquals("220", matches[1].preview!!.missing.single().amount)
        assertEquals("Check amounts", matches.last().status)
    }
    @Test fun `Apple and Gemini drafts use the same shared constraints`() {
        val draft = GeneratedRecipeDraft("Dinner", 2, listOf("2 eggs"), listOf("Cook."))
        assertEquals(draft.reviewed("Apple Intelligence").ingredientLines, draft.reviewed("Gemini").ingredientLines)
        assertFailsWith<IllegalArgumentException> { draft.copy(baseServings = 0).reviewed("Apple Intelligence") }
        assertFailsWith<IllegalArgumentException> { draft.copy(ingredients = emptyList()).reviewed("Gemini") }
        assertFailsWith<IllegalArgumentException> { draft.copy(instructions = listOf("x".repeat(10001))).reviewed("Gemini") }
    }
    @Test fun `Gemini uses header credentials bounded schema and validates its response`() {
        var calls = 0
        val draft = GeneratedRecipeDraft("Egg supper", 2, listOf("2 eggs"), listOf("Cook."))
        val envelope = buildJsonObject { put("candidates", buildJsonArray { add(buildJsonObject {
            put("finishReason", "STOP"); put("content", buildJsonObject { put("parts", buildJsonArray { add(buildJsonObject { put("text", Json.encodeToString(draft)) }) }) })
        }) }) }.toString()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            calls++
            assertEquals("fake-key", chain.request().header("x-goog-api-key"))
            assertFalse(chain.request().url.toString().contains("fake-key"))
            Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("OK").body(envelope.toResponseBody("application/json".toMediaType())).build()
        }.build()
        assertEquals(draft, GeminiMealGenerator("fake-key", client = client).generate(emptyList(), MealIdeaRequest()))
        assertEquals(1, calls)
        assertFailsWith<IllegalArgumentException> { GeminiMealGenerator.parse(envelope.replace("STOP", "MAX_TOKENS")) }
    }
    @Test fun `AI suggestions are drafts with no stock spending or recipe side effects and have a cooldown`() = testApplication {
        val dir = Files.createTempDirectory("basil-ideas")
        val vault = Vault(dir.resolve("vault.json"))
        var calls = 0
        application { basil(vault, extractor = null, mealGenerator = MealGenerator { _, _ -> calls++; GeneratedRecipeDraft("Dinner", 2, listOf("2 eggs"), listOf("Cook.")) }) }
        try {
            suspend fun idea() = client.post("/v1/suggestions/gemini") { contentType(ContentType.Application.Json); setBody(Json.encodeToString(MealIdeaRequest())) }
            assertEquals(HttpStatusCode.OK, idea().status)
            assertEquals(HttpStatusCode.TooManyRequests, idea().status)
            assertEquals(1, calls)
            assertTrue(vault.recipes().isEmpty()); assertTrue(vault.pantry().isEmpty()); assertTrue(vault.purchases().isEmpty())
        } finally { Files.walk(dir).use { it.sorted(Comparator.reverseOrder()).forEach(Files::delete) } }
    }
    @Test fun `saved matches remain available without a Gemini key`() = testApplication {
        val dir = Files.createTempDirectory("basil-ideas-off")
        application { basil(Vault(dir.resolve("vault.json")), extractor = null, mealGenerator = null) }
        try {
            assertEquals(HttpStatusCode.OK, client.get("/v1/suggestions/saved").status)
            assertEquals(HttpStatusCode.ServiceUnavailable, client.post("/v1/suggestions/gemini") { contentType(ContentType.Application.Json); setBody(Json.encodeToString(MealIdeaRequest())) }.status)
        } finally { Files.walk(dir).use { it.sorted(Comparator.reverseOrder()).forEach(Files::delete) } }
    }
}
