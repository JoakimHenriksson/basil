package app.basil.backend

import app.basil.contracts.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

@Serializable data class ShoppingState(
    val selections: List<MealSelection> = emptyList(),
    val manual: List<ShoppingItem> = emptyList(),
    val checks: Map<String, String> = emptyMap(),
    val revision: Long = 0,
)

class ShoppingConflict : RuntimeException("Your shopping list changed. Refresh it before trying again.")

object Shopping {
    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    fun version(state: ShoppingState, pantry: List<PantryItem>): String = hash(Json.encodeToString(state) + Json.encodeToString(pantry))
    fun requireVersion(state: ShoppingState, pantry: List<PantryItem>, expected: String) {
        if (version(state, pantry) != expected) throw ShoppingConflict()
    }
    fun signature(item: ShoppingItem, selections: List<MealSelection>): String = hash(Json.encodeToString(item.copy(checked = false)) +
        Json.encodeToString(selections.filter { it.id in item.selectionIds }))
    fun list(state: ShoppingState, pantry: List<PantryItem>): ShoppingList {
        val scaled = state.selections.associate { it.id to Ingredients.preview(it.recipe, it.servings, emptyList()) }
        val all = scaled.values.flatMap { it.ingredients }
        val combined = Recipe("shopping", "Shopping", "", 1, "1", all, emptyList(), "")
        val result = Ingredients.preview(combined, 1, pantry)
        val generated = result.missing.map { missing ->
            val ids = state.selections.filter { selection -> scaled.getValue(selection.id).ingredients.any {
                !it.optional && it.name == missing.name && it.unit == missing.unit
            } }.map { it.id }
            val item = ShoppingItem("need-" + hash("${missing.name}|${missing.unit}|${missing.needsReview}"),
                missing.name, missing.amount, missing.unit, missing.needsReview, ids)
            item.copy(checked = state.checks[item.id] == signature(item, state.selections))
        }
        return ShoppingList(version(state, pantry), state.selections, generated + state.manual,
            scaled.values.flatMap { it.warnings }.distinct())
    }
}
