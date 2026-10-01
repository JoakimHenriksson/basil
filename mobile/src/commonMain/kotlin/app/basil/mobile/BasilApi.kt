package app.basil.mobile

import app.basil.contracts.*
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

expect fun platformEngine(): HttpClientEngine

class BasilApi(private val base: String) {
    private val client = HttpClient(platformEngine()) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) { requestTimeoutMillis = 120_000 }
        expectSuccess = true
        HttpResponseValidator {
            handleResponseExceptionWithRequest { exception, _ ->
                if (exception is ResponseException) {
                    val message = runCatching { exception.response.body<ApiError>().message }.getOrNull()
                    throw IllegalStateException(message ?: "Basil couldn't finish that request. Please try again.")
                }
            }
        }
    }
    suspend fun recipes(): List<Recipe> = client.get("$base/v1/recipes").body()
    suspend fun pantry(): List<PantryItem> = client.get("$base/v1/pantry").body()
    suspend fun import(url: String): Recipe = client.post("$base/v1/recipes/import") { contentType(ContentType.Application.Json); setBody(ImportRequest(url)) }.body()
    suspend fun capture(page: CaptureRequest): Recipe = client.post("$base/v1/recipes/capture") { contentType(ContentType.Application.Json); setBody(page) }.body()
    suspend fun preview(id: String, servings: Int): RecipePreview = client.post("$base/v1/recipes/$id/preview") { contentType(ContentType.Application.Json); setBody(PreviewRequest(servings)) }.body()
    suspend fun setYield(id: String, servings: Int): Recipe = client.put("$base/v1/recipes/$id/yield") { contentType(ContentType.Application.Json); setBody(YieldRequest(servings)) }.body()
    suspend fun add(item: PantryRequest): PantryItem = client.post("$base/v1/pantry") { contentType(ContentType.Application.Json); setBody(item) }.body()
    suspend fun remove(id: String) { client.delete("$base/v1/pantry/$id") }
    suspend fun createRecipe(request: RecipeEditRequest): Recipe = client.post("$base/v1/recipes") { contentType(ContentType.Application.Json); setBody(request) }.body()
    suspend fun editRecipe(id: String, request: RecipeEditRequest): Recipe = client.put("$base/v1/recipes/$id") { contentType(ContentType.Application.Json); setBody(request) }.body()
    suspend fun favourite(id: String, value: Boolean): Recipe = client.put("$base/v1/recipes/$id/favourite") { contentType(ContentType.Application.Json); setBody(FavouriteRequest(value)) }.body()
    suspend fun deleteRecipe(id: String) { client.delete("$base/v1/recipes/$id") }
    suspend fun exportRecipes(): ByteArray = client.get("$base/v1/recipes/export").body()
    suspend fun shopping(): ShoppingList = client.get("$base/v1/shopping").body()
    suspend fun selectMeal(request: SelectMealRequest): ShoppingList = client.post("$base/v1/shopping/selections") { contentType(ContentType.Application.Json); setBody(request) }.body()
    suspend fun changeSelection(id: String, request: SelectionServingsRequest): ShoppingList = client.put("$base/v1/shopping/selections/$id") { contentType(ContentType.Application.Json); setBody(request) }.body()
    suspend fun removeSelection(id: String, version: String): ShoppingList = client.delete("$base/v1/shopping/selections/$id") { contentType(ContentType.Application.Json); setBody(ShoppingVersionRequest(version)) }.body()
    suspend fun manualShopping(request: ManualShoppingRequest): ShoppingList = client.post("$base/v1/shopping/items") { contentType(ContentType.Application.Json); setBody(request) }.body()
    suspend fun checkShopping(id: String, request: CheckShoppingRequest): ShoppingList = client.put("$base/v1/shopping/items/$id") { contentType(ContentType.Application.Json); setBody(request) }.body()
    suspend fun removeShopping(id: String, version: String): ShoppingList = client.delete("$base/v1/shopping/items/$id") { contentType(ContentType.Application.Json); setBody(ShoppingVersionRequest(version)) }.body()
    fun close() = client.close()
}
