package app.basil.contracts

import kotlinx.serialization.Serializable

@Serializable data class MealSelection(val id: String, val recipe: Recipe, val servings: Int)
@Serializable data class ShoppingItem(
    val id: String, val name: String, val amount: String?, val unit: String,
    val needsReview: Boolean = false, val selectionIds: List<String> = emptyList(),
    val checked: Boolean = false, val manual: Boolean = false,
)
@Serializable data class ShoppingList(
    val version: String, val selections: List<MealSelection>, val items: List<ShoppingItem>,
    val warnings: List<String> = emptyList(),
)
@Serializable data class SelectMealRequest(val id: String, val recipeId: String, val recipeRevision: Int, val servings: Int, val expectedVersion: String)
@Serializable data class SelectionServingsRequest(val servings: Int, val expectedVersion: String)
@Serializable data class ShoppingVersionRequest(val expectedVersion: String)
@Serializable data class ManualShoppingRequest(val id: String, val name: String, val expectedVersion: String)
@Serializable data class CheckShoppingRequest(val checked: Boolean, val expectedVersion: String)
