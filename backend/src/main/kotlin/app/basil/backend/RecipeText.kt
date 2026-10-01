package app.basil.backend

import app.basil.contracts.*

object RecipeText {
    fun draft(text: String, extractor: RecipeExtractor?): RecipeEditRequest {
        require(text.isNotBlank() && text.length <= 60_000) { "Scan or paste a readable recipe." }
        if (extractor != null) {
            val recipe = extractor.extract("", text)
            return RecipeEditRequest(recipe.title, recipe.baseServings, recipe.ingredients.map { it.original }, recipe.instructions, "Scanned recipe. Review the extracted ingredients and method.")
        }
        val lines = text.lines().map(String::trim).filter(String::isNotBlank)
        val ingredients = lines.indexOfFirst { it.lowercase() in setOf("ingredients", "ingredients:", "ingredienser", "ingredienser:") }
        val method = lines.indexOfFirst { it.lowercase() in setOf("method", "method:", "instructions", "instructions:", "gör så här", "gör så här:", "tillagning", "tillagning:") }
        require(ingredients > 0 && method > ingredients + 1 && method < lines.lastIndex) { "Add Ingredients and Method headings to the scanned text, or configure Gemini for general recipe extraction." }
        val yield = Regex("(?:serves|servings|portioner|portions)\\s*:?\\s*([0-9]+)|([0-9]+)\\s*(?:servings|portioner|portions)", RegexOption.IGNORE_CASE).find(text)
        val base = yield?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }?.toIntOrNull()?.takeIf { it in 1..1000 }
        return RecipeEditRequest(lines.first().take(250), base, lines.subList(ingredients + 1, method), lines.drop(method + 1), "Scanned recipe. Review ingredients, yield and method.")
    }
}
