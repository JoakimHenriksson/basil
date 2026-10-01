package app.basil.backend

import app.basil.contracts.Recipe
import kotlinx.serialization.json.*
import org.jsoup.Jsoup
import java.time.Instant
import java.util.UUID

class RecipeImporter {
    private val json = Json { isLenient = true; ignoreUnknownKeys = true }
    fun parse(html: String, source: String): Recipe {
        val document = Jsoup.parse(html, source)
        val candidates = mutableListOf<JsonObject>()
        fun walk(element: JsonElement) {
            when (element) {
                is JsonObject -> {
                    if (element["@type"]?.toString()?.contains("Recipe") == true && element["recipeIngredient"] != null) candidates += element
                    element.values.forEach { walk(it) }
                }
                is JsonArray -> element.forEach { walk(it) }
                else -> Unit
            }
        }
        document.select("script[type=application/ld+json]").forEach { script -> runCatching { walk(json.parseToJsonElement(script.data())) } }
        // Some sites expose their schema in a JSON-valued HTML data attribute.
        document.select("[data-model]").forEach { element -> runCatching { walk(json.parseToJsonElement(element.attr("data-model"))) } }
        val schema = candidates.firstOrNull()
        if (schema != null) {
            fun text(key: String) = when (val value = schema[key]) { is JsonPrimitive -> value.content; is JsonArray -> (value.firstOrNull() as? JsonPrimitive)?.content.orEmpty(); else -> "" }
            val lines = (schema["recipeIngredient"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()
            val instructions = mutableListOf<String>()
            fun steps(value: JsonElement?) {
                when (value) {
                    is JsonPrimitive -> instructions += Jsoup.parse(value.content).text()
                    is JsonArray -> value.forEach { steps(it) }
                    is JsonObject -> { (value["text"] as? JsonPrimitive)?.let { instructions += Jsoup.parse(it.content).text() }; steps(value["itemListElement"]) }
                    else -> Unit
                }
            }
            steps(schema["recipeInstructions"])
            val yieldText = text("recipeYield")
            val servings = yieldCount(yieldText)
            require(text("name").isNotBlank() && lines.isNotEmpty()) { "No complete recipe data was found on this page." }
            return Recipe(UUID.randomUUID().toString(), Jsoup.parse(text("name")).text(), source, servings, yieldText,
                lines.map { Ingredients.parse(Jsoup.parse(it).text()) }, instructions.filter { it.isNotBlank() }, Instant.now().toString(),
                if (servings == null) listOf("The recipe yield needs review before scaling.") else emptyList())
        }
        val content = document.selectFirst(".entry-content") ?: document.selectFirst("[itemprop=recipeInstructions]") ?: document.selectFirst("article")
            ?: throw IllegalArgumentException("This page has no readable recipe. Try sharing the recipe page rather than a search page.")
        content.select(".sharedaddy, .sd-sharing, .panorama-sticky, .modul, .ad-panorama-wrapper, aside").remove()
        val paragraphs = content.select("p, li").map { it.wholeText().trim() }.filter { it.isNotBlank() }
        val lines = paragraphs.flatMap { it.lines() }.map { it.trim() }.filter { it.length in 2..180 }
        val yieldPattern = Regex("\\d.*(?:portion|person|port\\b|serving|people)", RegexOption.IGNORE_CASE)
        val numericStart = Regex("^(?:\\d|[½¼¾⅓⅔⅛])")
        val methodPattern = Regex("^gör\\s*så\\s*här\\s*:?|^method\\s*:?|^instructions\\s*:?", RegexOption.IGNORE_CASE)
        val methodStart = paragraphs.indexOfFirst { methodPattern.containsMatchIn(it) }
        val beforeMethod = paragraphs.take(if (methodStart >= 0) methodStart else paragraphs.size)
        val ingredientBlock = beforeMethod.firstOrNull { paragraph -> paragraph.lines().count { numericStart.containsMatchIn(it.trim()) } >= 2 }
        val ingredientLines = if (ingredientBlock != null) ingredientBlock.lines().map { it.trim() }.filter { it.isNotBlank() && !it.endsWith(':') && !yieldPattern.containsMatchIn(it) }
            else lines.filter { numericStart.containsMatchIn(it) && !Regex("^\\d+[.)]").containsMatchIn(it) && !yieldPattern.containsMatchIn(it) }
        require(ingredientLines.isNotEmpty()) { "No ingredient list was found. This page needs a different import method." }
        val yieldText = lines.firstOrNull { yieldPattern.containsMatchIn(it) }.orEmpty()
        val methods = if (methodStart >= 0) paragraphs.drop(methodStart).map { methodPattern.replaceFirst(it, "").trim() }.filter { it.isNotBlank() } else paragraphs
        return Recipe(UUID.randomUUID().toString(), document.selectFirst("h1")?.text() ?: document.title(), source,
            yieldCount(yieldText), yieldText, ingredientLines.map(Ingredients::parse), methods, Instant.now().toString(),
            listOf("Imported from page text. Check the ingredient list, instructions, and yield."))
    }
    private fun yieldCount(text: String): Int? {
        if (Regex("\\d\\s*[-–]\\s*\\d").containsMatchIn(text)) return null
        return Regex("\\d+").find(text)?.value?.toIntOrNull()?.takeIf { it in 1..1000 }
    }
}
