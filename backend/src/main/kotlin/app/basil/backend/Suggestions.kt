package app.basil.backend

import app.basil.contracts.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit

fun interface MealGenerator { fun generate(pantry: List<PantryItem>, request: MealIdeaRequest): GeneratedRecipeDraft }
class ProviderUnavailable : RuntimeException("Gemini isn't configured. Saved recipe matches remain available.")
class SuggestionCooldown : RuntimeException("Please wait a little before requesting another Gemini idea.")

object Suggestions {
    fun saved(recipes: List<Recipe>, pantry: List<PantryItem>, servings: Int): List<MealMatch> {
        require(servings in 1..1000) { "Choose between 1 and 1000 servings." }
        return recipes.map { recipe ->
            val preview = if (recipe.baseServings == null) null else Ingredients.preview(recipe, servings, pantry)
            val status = when {
                preview == null || preview.missing.any { it.needsReview } -> "Check amounts"
                preview.missing.isEmpty() -> "Ready to cook"
                preview.missing.size <= 3 -> "Almost ready"
                else -> "Needs ingredients"
            }
            MealMatch(recipe.id, recipe.title, status, preview, recipe.favourite)
        }.sortedWith(compareBy<MealMatch> { when(it.status) { "Ready to cook" -> 0; "Almost ready" -> 1; "Needs ingredients" -> 2; else -> 3 } }
            .thenBy { it.preview?.missing?.size ?: Int.MAX_VALUE }.thenByDescending { it.favourite }.thenBy { it.title.lowercase() })
    }
}

class GeminiMealGenerator(private val key: String, private val model: String = "gemini-2.5-flash",
    private val client: OkHttpClient = OkHttpClient.Builder().followRedirects(false).callTimeout(45, TimeUnit.SECONDS).build()) : MealGenerator {
    init { require(model.matches(Regex("[a-zA-Z0-9._-]+"))) { "Invalid Gemini model name." } }
    override fun generate(pantry: List<PantryItem>, request: MealIdeaRequest): GeneratedRecipeDraft {
        val body = buildJsonObject {
            put("contents", buildJsonArray { add(buildJsonObject { put("role", "user"); put("parts", buildJsonArray { add(buildJsonObject { put("text", mealIdeaPrompt(pantry, request)) }) }) }) })
            put("generationConfig", buildJsonObject {
                put("temperature", 0.4); put("maxOutputTokens", 4096); put("responseMimeType", "application/json")
                put("responseSchema", Json.parseToJsonElement("""{"type":"OBJECT","properties":{"title":{"type":"STRING"},"baseServings":{"type":"INTEGER"},"ingredients":{"type":"ARRAY","items":{"type":"STRING"}},"instructions":{"type":"ARRAY","items":{"type":"STRING"}}},"required":["title","baseServings","ingredients","instructions"]}"""))
            })
        }.toString()
        val http = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent").header("x-goog-api-key", key)
            .post(body.toRequestBody("application/json".toMediaType())).build()
        val raw = client.newCall(http).execute().use { response ->
            require(response.isSuccessful) { "Gemini couldn't suggest a meal. Try a saved recipe or request again later." }
            val bytes = requireNotNull(response.body).byteStream().readNBytes(200_001)
            require(bytes.size <= 200_000) { "The meal response was too large." }
            bytes.decodeToString()
        }
        return parse(raw)
    }
    companion object {
        fun configured(): MealGenerator? = System.getenv("GEMINI_API_KEY")?.takeIf(String::isNotBlank)?.let { GeminiMealGenerator(it, System.getenv("BASIL_GEMINI_MODEL") ?: "gemini-2.5-flash") }
        fun parse(raw: String): GeneratedRecipeDraft {
            val candidate = Json.parseToJsonElement(raw).jsonObject["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            require(candidate?.get("finishReason")?.jsonPrimitive?.content == "STOP") { "Gemini did not finish the recipe draft." }
            val text = candidate["content"]?.jsonObject?.get("parts")?.jsonArray?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }?.joinToString("").orEmpty()
            return Json.decodeFromString<GeneratedRecipeDraft>(text).also { it.reviewed("Gemini") }
        }
    }
}
