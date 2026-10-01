package app.basil.backend

import app.basil.contracts.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

@Serializable private data class VaultState(val recipes: List<Recipe> = emptyList(), val pantry: List<PantryItem> = emptyList())

/** Local prototype storage. Production household persistence is a later slice. */
class Vault(private val file: Path) {
    private val json = Json { prettyPrint = true }
    private var state = if (Files.exists(file)) json.decodeFromString<VaultState>(Files.readString(file)) else VaultState()
    @Synchronized fun recipes(): List<Recipe> = state.recipes
    @Synchronized fun pantry(): List<PantryItem> = state.pantry
    @Synchronized fun recipe(id: String): Recipe? = state.recipes.find { it.id == id }
    @Synchronized fun setYield(id: String, servings: Int): Recipe? {
        require(servings in 1..1000) { "Choose between 1 and 1000 base servings." }
        val old = recipe(id) ?: return null
        val next = old.copy(baseServings = servings)
        persist(state.copy(recipes = state.recipes.map { if (it.id == id) next else it }))
        return next
    }
    @Synchronized fun save(recipe: Recipe): Recipe {
        val existing = state.recipes.find { it.sourceUrl == recipe.sourceUrl }
        if (existing != null) return existing
        persist(state.copy(recipes = state.recipes + recipe))
        return recipe
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
