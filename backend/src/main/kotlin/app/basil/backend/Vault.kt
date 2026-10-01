package app.basil.backend

import app.basil.contracts.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

@Serializable private data class VaultState(val recipes: List<Recipe> = emptyList(), val pantry: List<PantryItem> = emptyList(), val recipeHistory: List<Recipe> = emptyList())

/** Local prototype storage. Production household persistence is a later slice. */
class Vault(private val file: Path) {
    val mediaDirectory: Path = file.toAbsolutePath().parent.resolve("media")
    private val json = Json { prettyPrint = true }
    private var state = if (Files.exists(file)) json.decodeFromString<VaultState>(Files.readString(file)) else VaultState()
    @Synchronized fun recipes(): List<Recipe> = state.recipes
    @Synchronized fun pantry(): List<PantryItem> = state.pantry
    @Synchronized fun recipe(id: String): Recipe? = state.recipes.find { it.id == id }
    @Synchronized fun setYield(id: String, servings: Int): Recipe? {
        require(servings in 1..1000) { "Choose between 1 and 1000 base servings." }
        val old = recipe(id) ?: return null
        val next = old.copy(baseServings = servings, revision = old.revision + 1)
        persist(state.copy(recipes = state.recipes.map { if (it.id == id) next else it }, recipeHistory = state.recipeHistory + old))
        return next
    }
    @Synchronized fun save(recipe: Recipe): Recipe {
        val existing = state.recipes.find { recipe.sourceUrl.isNotBlank() && it.sourceUrl == recipe.sourceUrl }
        if (existing != null) return existing
        persist(state.copy(recipes = state.recipes + recipe))
        return recipe
    }
    @Synchronized fun history(id: String): List<Recipe> = state.recipeHistory.filter { it.id == id } + listOfNotNull(recipe(id))
    @Synchronized fun edit(id: String, request: RecipeEditRequest): Recipe? {
        val old = recipe(id) ?: return null
        if (request.expectedRevision != old.revision) throw RecipeConflict()
        val next = validateEdit(request, old).copy(revision = old.revision + 1)
        persist(state.copy(recipes = state.recipes.map { if (it.id == id) next else it }, recipeHistory = state.recipeHistory + old))
        return next
    }
    @Synchronized fun create(request: RecipeEditRequest): Recipe {
        val blank = Recipe(UUID.randomUUID().toString(), "", "", null, "", emptyList(), emptyList(), java.time.Instant.now().toString())
        return save(validateEdit(request, blank))
    }
    private fun validateEdit(request: RecipeEditRequest, old: Recipe): Recipe {
        require(request.title.isNotBlank() && request.title.length <= 250) { "Enter a recipe title of up to 250 characters." }
        require(request.baseServings == null || request.baseServings in 1..1000) { "Choose between 1 and 1000 base servings." }
        require(request.ingredientLines.size in 1..200 && request.ingredientLines.all { it.isNotBlank() && it.length <= 1000 }) { "Enter at least one ingredient, with one ingredient per line." }
        require(request.instructions.size in 1..200 && request.instructions.all { it.isNotBlank() && it.length <= 10_000 }) { "Enter at least one cooking step." }
        require(request.notes.length <= 20_000) { "Recipe notes are too long." }
        val ingredients = request.ingredientLines.map(Ingredients::parse)
        val warnings = buildList {
            if (request.baseServings == null) add("The recipe yield needs review before scaling.")
            if (ingredients.any { it.needsReview }) add("Some ingredient amounts need review.")
        }
        return old.copy(title = request.title.trim(), baseServings = request.baseServings,
            yieldText = request.baseServings?.let { "$it servings" }.orEmpty(), ingredients = ingredients,
            instructions = request.instructions.map(String::trim), notes = request.notes.trim(), warnings = warnings)
    }
    @Synchronized fun favourite(id: String, value: Boolean): Recipe? {
        val updated = recipe(id)?.copy(favourite = value) ?: return null
        persist(state.copy(recipes = state.recipes.map { if (it.id == id) updated else it }))
        return updated
    }
    @Synchronized fun deleteRecipe(id: String): Boolean {
        if (recipe(id) == null) return false
        persist(state.copy(recipes = state.recipes.filterNot { it.id == id }, recipeHistory = state.recipeHistory.filterNot { it.id == id }))
        return true
    }
    @Synchronized fun add(request: PantryRequest): PantryItem {
        require(request.name.isNotBlank() && request.name.length <= 150) { "Enter an ingredient name." }
        require(request.unit in setOf("g", "ml", "piece")) { "Choose grams, millilitres, or pieces." }
        require(request.amount.length <= 30 && Ingredients.number(request.amount).signum() > 0) { "Enter a positive amount." }
        val item = PantryItem(UUID.randomUUID().toString(), Ingredients.canonical(request.name, request.unit), Ingredients.display(Ingredients.number(request.amount)), request.unit)
        persist(state.copy(pantry = state.pantry + item))
        return item
    }
    @Synchronized fun remove(id: String): Boolean {
        if (state.pantry.none { it.id == id }) return false
        persist(state.copy(pantry = state.pantry.filterNot { it.id == id }))
        return true
    }
    private fun persist(next: VaultState) {
        Files.createDirectories(file.toAbsolutePath().parent)
        val temporary = Files.createTempFile(file.toAbsolutePath().parent, "basil-", ".json")
        try {
            Files.writeString(temporary, json.encodeToString(next))
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            state = next
        } finally { Files.deleteIfExists(temporary) }
    }
}

class RecipeConflict : RuntimeException("This recipe changed. Reopen it before saving your edit.")
