package app.basil.contracts

import kotlinx.serialization.Serializable

@Serializable
data class Ingredient(
    val original: String,
    val name: String,
    val amount: String? = null,
    val unit: String = "unknown",
    val optional: Boolean = false,
    val needsReview: Boolean = false,
)

@Serializable
data class Recipe(
    val id: String,
    val title: String,
    val sourceUrl: String,
    val baseServings: Int?,
    val yieldText: String,
    val ingredients: List<Ingredient>,
    val instructions: List<String>,
    val savedAt: String,
    val warnings: List<String> = emptyList(),
)

@Serializable data class ImportRequest(val url: String)
@Serializable data class PreviewRequest(val servings: Int)
@Serializable data class YieldRequest(val servings: Int)
@Serializable data class PantryRequest(val name: String, val amount: String, val unit: String)
@Serializable data class PantryItem(val id: String, val name: String, val amount: String, val unit: String)
@Serializable data class MissingItem(val name: String, val amount: String?, val unit: String, val needsReview: Boolean = false)
@Serializable data class RecipePreview(
    val servings: Int,
    val ingredients: List<Ingredient>,
    val missing: List<MissingItem>,
    val warnings: List<String>,
)
@Serializable data class ApiError(val code: String, val message: String)
