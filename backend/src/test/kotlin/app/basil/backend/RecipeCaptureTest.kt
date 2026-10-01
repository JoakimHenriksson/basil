package app.basil.backend

import app.basil.contracts.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import java.nio.file.Files
import kotlin.test.*

class RecipeCaptureTest {
    private val source = "https://example.com/dinner"
    private val html = """<script type="application/ld+json">{"@type":"Recipe","name":"Dinner","recipeYield":"2","image":{"url":"/photo.png"},"recipeIngredient":["2 eggs"],"recipeInstructions":["Cook."]}</script>"""
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 13, 10, 26, 10, 0, 0, 0, 0)
    private fun fixture(test: (Vault, RecipeImages) -> Unit) {
        val directory = Files.createTempDirectory("basil-capture")
        try { test(Vault(directory.resolve("vault.json")), RecipeImages(directory.resolve("media"), ImageFetcher { Download(png, "image/png") })) }
        finally { Files.walk(directory).use { files -> files.sorted(Comparator.reverseOrder()).forEach(Files::delete) } }
    }
    @Test fun `capture saves rendered recipe and photo without fetching the page`() = fixture { vault, images ->
        val service = RecipeCapture(vault, images, RecipeExtractor { _, _ -> error("Structured recipes must skip Gemini") })
        val saved = service.save(CaptureRequest(source, html))
        assertEquals(source, saved.sourceUrl)
        assertEquals("https://example.com/photo.png", saved.originalImageUrl)
        val photo = images.file(saved.imagePath!!.substringAfterLast('/'))!!
        assertContentEquals(png, Files.readAllBytes(photo))
        assertEquals(saved, service.save(CaptureRequest(source, html)))
        val reopened = Vault(photo.parent.parent.resolve("vault.json"))
        assertEquals(saved, reopened.recipes().single())
        assertEquals(1L, Files.list(photo.parent).use { it.count() })
    }
    @Test fun `missing photo preserves recipe and reports the limitation`() = fixture { vault, _ ->
        val images = RecipeImages(vault.mediaDirectory, ImageFetcher { throw IllegalArgumentException("Unavailable") })
        val saved = RecipeCapture(vault, images).save(CaptureRequest(source, html))
        assertNull(saved.imagePath)
        assertTrue(saved.warnings.any { it.contains("photo") })
        assertEquals(source, saved.sourceUrl)
    }
    @Test fun `non structured page uses extractor and strips forms and scripts`() = fixture { vault, images ->
        val extractor = RecipeExtractor { url, text ->
            assertEquals(source, url)
            assertTrue(text.contains("2 eggs"))
            assertFalse(text.contains("PRIVATE_FORM"))
            assertFalse(text.contains("SCRIPT_SECRET"))
            GeminiRecipeExtractor.validate(url, """{"title":"Dinner","yieldText":"2","baseServings":2,"ingredients":["2 eggs"],"instructions":["Cook."]}""")
        }
        val saved = RecipeCapture(vault, images, extractor).save(CaptureRequest(source, "<article>2 eggs. Cook.</article><form>PRIVATE_FORM</form><script>SCRIPT_SECRET</script>"))
        assertEquals("Dinner", saved.title)
        assertTrue(saved.warnings.any { it.contains("Gemini") })
    }
    @Test fun `bad extracted yields and non recipes are rejected`() {
        assertFailsWith<IllegalArgumentException> { GeminiRecipeExtractor.validate(source, """{"title":"Dinner","baseServings":0,"ingredients":["2 eggs"],"instructions":["Cook."]}""") }
        assertFailsWith<IllegalArgumentException> { GeminiRecipeExtractor.validate(source, """{"title":"Search","ingredients":[],"instructions":[]}""") }
    }
    @Test fun `Gemini unavailable falls back to readable recipe text`() = fixture { vault, images ->
        val saved = RecipeCapture(vault, images, RecipeExtractor { _, _ -> error("offline") }).save(CaptureRequest(source,
            "<h1>Dinner</h1><article><p>2 servings<br>2 eggs<br>1 dl milk</p><p>Method: Cook.</p></article>"))
        assertEquals(2, saved.ingredients.size)
        assertTrue(saved.warnings.any { it.contains("unavailable") })
    }
    @Test fun `captures and media reject oversized and unsafe inputs`() = fixture { vault, images ->
        assertFailsWith<IllegalArgumentException> { RecipeCapture(vault, images).save(CaptureRequest("file:///secret", html)) }
        assertFailsWith<IllegalArgumentException> { RecipeCapture(vault, images).save(CaptureRequest(source, "x".repeat(2_000_001))) }
        assertNull(images.file("../vault.json"))
        val invalid = RecipeImages(vault.mediaDirectory, ImageFetcher { Download("<svg>unsafe</svg>".toByteArray(), "image/png") })
        assertFailsWith<IllegalArgumentException> { invalid.preserve("https://example.com/photo.png") }
    }
    @Test fun `Gemini uses structured output and keeps credentials out of payload`() {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("test-secret", request.header("x-goog-api-key"))
            assertFalse(request.url.toString().contains("test-secret"))
            val buffer = okio.Buffer(); request.body!!.writeTo(buffer)
            val payload = Json.parseToJsonElement(buffer.readUtf8()).jsonObject
            assertEquals("application/json", payload["generationConfig"]!!.jsonObject["responseMimeType"]!!.jsonPrimitive.content)
            assertNotNull(payload["systemInstruction"])
            val recipeJson = """{"title":"Dinner","yieldText":"2","baseServings":2,"ingredients":["2 eggs"],"instructions":["Cook."]}"""
            val response = buildJsonObject { put("candidates", buildJsonArray { add(buildJsonObject {
                put("finishReason", "STOP"); put("content", buildJsonObject { put("parts", buildJsonArray { add(buildJsonObject { put("text", recipeJson) }) }) })
            }) }) }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(response.toString().toResponseBody()).build()
        }.build()
        val recipe = GeminiRecipeExtractor("test-secret", client = client).extract(source, "2 eggs. Cook.")
        assertEquals(source, recipe.sourceUrl)
        assertEquals("eggs", recipe.ingredients.single().name)
    }
    @Test fun `HTTP capture and retained media work after reopening storage`() = testApplication {
        val directory = Files.createTempDirectory("basil-capture-api")
        application { basil(Vault(directory.resolve("vault.json")), PageFetcher { error("Do not fetch browser pages") },
            RecipeImages(directory.resolve("media"), ImageFetcher { Download(png, "image/png") }), extractor = null) }
        try {
            val response = client.post("/v1/recipes/capture") { contentType(ContentType.Application.Json); setBody(Json.encodeToString(CaptureRequest.serializer(), CaptureRequest(source, html))) }
            assertEquals(HttpStatusCode.Created, response.status)
            val saved = Json.decodeFromString<Recipe>(response.bodyAsText())
            assertEquals(HttpStatusCode.OK, client.get(saved.imagePath!!).status)
            assertEquals(HttpStatusCode.NotFound, client.get("/v1/media/not-a-photo.png").status)
        } finally { Files.walk(directory).use { files -> files.sorted(Comparator.reverseOrder()).forEach(Files::delete) } }
    }
}
