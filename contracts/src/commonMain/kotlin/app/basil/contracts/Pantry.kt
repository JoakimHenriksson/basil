package app.basil.contracts

import kotlinx.serialization.Serializable

@Serializable data class Product(val barcode: String, val name: String, val ingredient: String, val packageAmount: String, val unit: String)
@Serializable data class ProductDraft(val barcode: String, val name: String = "", val packageAmount: String? = null, val unit: String = "g", val source: String = "", val warning: String = "")
@Serializable data class StockDelta(val lot: PantryItem, val amount: String)
@Serializable data class StockMovement(
    val id: String, val kind: String, val at: String, val deltas: List<StockDelta>,
    val barcode: String? = null, val undoOf: String? = null,
    val recipeId: String? = null, val servings: Int? = null, val reconciledUseIds: List<String> = emptyList(),
)
@Serializable data class StockRequest(val id: String, val barcode: String, val kind: String, val amount: String? = null)
@Serializable data class UndoStockRequest(val id: String)
@Serializable data class CookRequest(val id: String, val servings: Int, val recipeRevision: Int, val reconciledUseIds: List<String> = emptyList())
