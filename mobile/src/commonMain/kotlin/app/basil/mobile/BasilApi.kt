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
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.CancellationException

expect fun platformEngine(): HttpClientEngine

class BasilApi(private val base: String, private val store: DeviceStore? = null, engine: HttpClientEngine = platformEngine()) {
    private val json = Json { ignoreUnknownKeys = true }
    private val sessionKey = "$base.session"
    private var session = store?.read(sessionKey)?.let { json.decodeFromString<DeviceSession>(it) }
    private val dataKey get() = "$base.${session?.householdId}.snapshot"
    private val pendingKey get() = "$base.${session?.householdId}.pending"
    private var cached = store?.read(dataKey)?.let { json.decodeFromString<HouseholdSnapshot>(it) }
    var pending = store?.read(pendingKey)?.let { json.decodeFromString<List<PendingAction>>(it) } ?: emptyList()
        private set
    var offline = false
        private set
    private fun saveCache() { store?.write(dataKey, cached?.let { json.encodeToString(it) }) }
    private fun savePending() { store?.write(pendingKey, json.encodeToString(pending)) }
    private fun denied() { cached = null; pending = emptyList(); saveCache(); savePending() }
    suspend fun connect() {
        if (session != null) return
        session = client.post("$base/session") { contentType(ContentType.Application.Json); setBody(SessionRequest("My device")) }.body()
        store?.write(sessionKey, json.encodeToString(session!!))
    }
    suspend fun refresh(): HouseholdSnapshot? {
        offline = false
        try {
            connect()
            for (action in pending.toList()) {
                try {
                    var body = action.body
                    if (action.path.startsWith("/v1/shopping/items")) {
                        val current = client.get("$base/v1/shopping").body<ShoppingList>()
                        if (action.method == "POST") body = json.encodeToString(json.decodeFromString<ManualShoppingRequest>(body).copy(expectedVersion = current.version))
                        else if (action.method == "PUT") {
                            val item = current.items.find { it.id == action.path.substringAfterLast('/') }
                            val desired = json.decodeFromString<CheckShoppingRequest>(body)
                            if (item != null && action.baselineItem != null && item.copy(checked = action.baselineItem.checked) == action.baselineItem && item.checked == desired.checked) {
                                pending = pending.filterNot { it.id == action.id }; savePending(); continue
                            }
                            if (item != action.baselineItem) throw ApiFailure(409, "This shopping item changed. Review it before applying your pending check-off.")
                            body = json.encodeToString(desired.copy(expectedVersion = current.version))
                        }
                    }
                    client.request("$base${action.path}") { method = HttpMethod(action.method); contentType(ContentType.Application.Json); setBody(body) }
                    pending = pending.filterNot { it.id == action.id }; savePending()
                } catch (failure: ApiFailure) {
                    if (failure.status == 401 || failure.status == 403) { denied(); throw failure }
                    pending = pending.map { if (it.id == action.id) it.copy(error = failure.message) else it }; savePending(); break
                }
            }
            val response = client.get("$base/v1/sync") { cached?.let { parameter("since", it.version) } }
            if (response.status != HttpStatusCode.NoContent) { cached = response.body(); saveCache() }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: ApiFailure) { throw failure }
        catch (failure: Exception) { offline = true; if (cached == null) throw failure }
        return cached
    }
    fun cachedSnapshot(): HouseholdSnapshot? = cached?.let { it.copy(shopping = if (offline) localShopping() else it.shopping) }
    fun discardPending(id: String) { pending = pending.filterNot { it.id == id }; savePending() }
    private suspend fun <T> queued(method: String, path: String, body: String, label: String, fallback: () -> T, send: suspend () -> T): T {
        if (!offline) try { return send() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: ApiFailure) { throw failure }
        catch (_: Exception) { offline = true }
        check(cached != null && session != null) { "Connect to Basil before making offline changes." }
        val baseline = if (method == "PUT" && path.startsWith("/v1/shopping/items/")) localShopping().items.find { it.id == path.substringAfterLast('/') } else null
        pending = pending + PendingAction(shoppingId(), method, path, body, label, baselineItem = baseline); savePending()
        return fallback()
    }
    suspend fun household(): HouseholdInfo = client.get("$base/household").body()
    suspend fun invite(): HouseholdInvite = client.post("$base/household/invites").body()
    suspend fun revokeMember(id: String) { client.delete("$base/household/members/$id") }
    suspend fun revokeInvite(id: String) { client.delete("$base/household/invites/$id") }
    suspend fun join(code: String) {
        check(pending.isEmpty()) { "Sync or discard pending changes before joining another household." }
        val joined = client.post("$base/households/join") { contentType(ContentType.Application.Json); setBody(JoinHouseholdRequest(code)) }.body<JoinedHousehold>()
        session = session!!.copy(householdId = joined.householdId); store?.write(sessionKey, json.encodeToString(session!!))
        cached = null; pending = emptyList(); refresh()
    }
    private val client = HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) { requestTimeoutMillis = 10_000 }
        defaultRequest {
            session?.let { header(HttpHeaders.Authorization, "Bearer ${it.token}"); header("X-Basil-Household", it.householdId) }
        }
        expectSuccess = true
        HttpResponseValidator {
            handleResponseExceptionWithRequest { exception, _ ->
                if (exception is ResponseException) {
                    val message = runCatching { exception.response.body<ApiError>().message }.getOrNull()
                    if (exception.response.status.value in listOf(401, 403)) denied()
                    throw ApiFailure(exception.response.status.value, message ?: "Basil couldn't finish that request. Please try again.")
                }
            }
        }
    }
    suspend fun recipes(): List<Recipe> = cached?.recipes ?: client.get("$base/v1/recipes").body()
    suspend fun pantry(): List<PantryItem> = cached?.pantry ?: client.get("$base/v1/pantry").body()
    suspend fun import(url: String): Recipe = client.post("$base/v1/recipes/import") { contentType(ContentType.Application.Json); timeout { requestTimeoutMillis = 120_000 }; setBody(ImportRequest(url)) }.body()
    suspend fun capture(page: CaptureRequest): Recipe = client.post("$base/v1/recipes/capture") { contentType(ContentType.Application.Json); timeout { requestTimeoutMillis = 120_000 }; setBody(page) }.body()
    suspend fun preview(id: String, servings: Int): RecipePreview = client.post("$base/v1/recipes/$id/preview") { contentType(ContentType.Application.Json); setBody(PreviewRequest(servings)) }.body()
    suspend fun setYield(id: String, servings: Int): Recipe = client.put("$base/v1/recipes/$id/yield") { contentType(ContentType.Application.Json); setBody(YieldRequest(servings)) }.body()
    suspend fun add(item: PantryRequest): PantryItem = client.post("$base/v1/pantry") { contentType(ContentType.Application.Json); setBody(item) }.body()
    suspend fun remove(id: String) { client.delete("$base/v1/pantry/$id") }
    suspend fun createRecipe(request: RecipeEditRequest): Recipe = client.post("$base/v1/recipes") { contentType(ContentType.Application.Json); setBody(request) }.body()
    suspend fun editRecipe(id: String, request: RecipeEditRequest): Recipe = client.put("$base/v1/recipes/$id") { contentType(ContentType.Application.Json); setBody(request) }.body()
    suspend fun favourite(id: String, value: Boolean): Recipe = client.put("$base/v1/recipes/$id/favourite") { contentType(ContentType.Application.Json); setBody(FavouriteRequest(value)) }.body()
    suspend fun deleteRecipe(id: String) { client.delete("$base/v1/recipes/$id") }
    suspend fun exportRecipes(): ByteArray = client.get("$base/v1/recipes/export").body()
    private fun localShopping(): ShoppingList {
        val list = cached!!.shopping
        var items = list.items
        for (action in pending) when {
            action.path == "/v1/shopping/items" && action.method == "POST" -> {
                val request = json.decodeFromString<ManualShoppingRequest>(action.body)
                if (items.none { it.id == "manual-${request.id}" }) items = items + ShoppingItem("manual-${request.id}", request.name, null, "", manual = true)
            }
            action.path.startsWith("/v1/shopping/items/") && action.method == "PUT" -> {
                val request = json.decodeFromString<CheckShoppingRequest>(action.body)
                items = items.map { if (it.id == action.path.substringAfterLast('/')) it.copy(checked = request.checked) else it }
            }
        }
        return list.copy(items = items)
    }
    suspend fun shopping(): ShoppingList = cached?.let { if (offline) localShopping() else it.shopping } ?: client.get("$base/v1/shopping").body()
    suspend fun selectMeal(request: SelectMealRequest): ShoppingList = client.post("$base/v1/shopping/selections") { contentType(ContentType.Application.Json); setBody(request) }.body()
    suspend fun changeSelection(id: String, request: SelectionServingsRequest): ShoppingList = client.put("$base/v1/shopping/selections/$id") { contentType(ContentType.Application.Json); setBody(request) }.body()
    suspend fun removeSelection(id: String, version: String): ShoppingList = client.delete("$base/v1/shopping/selections/$id") { contentType(ContentType.Application.Json); setBody(ShoppingVersionRequest(version)) }.body()
    suspend fun manualShopping(request: ManualShoppingRequest): ShoppingList = queued("POST", "/v1/shopping/items", json.encodeToString(request), "Add shopping extra", { localShopping() }) {
        client.request("$base/v1/shopping/items") { this.method = HttpMethod("POST"); contentType(ContentType.Application.Json); setBody(request) }.body()
    }
    suspend fun checkShopping(id: String, request: CheckShoppingRequest): ShoppingList = queued("PUT", "/v1/shopping/items/$id", json.encodeToString(request), "Update shopping check-off", { localShopping() }) {
        client.request("$base/v1/shopping/items/$id") { this.method = HttpMethod("PUT"); contentType(ContentType.Application.Json); setBody(request) }.body()
    }
    suspend fun removeShopping(id: String, version: String): ShoppingList = client.delete("$base/v1/shopping/items/$id") { contentType(ContentType.Application.Json); setBody(ShoppingVersionRequest(version)) }.body()
    suspend fun products(): List<Product> = cached?.products ?: client.get("$base/v1/products").body()
    suspend fun productDraft(barcode: String): ProductDraft = client.get("$base/v1/products/$barcode").body()
    suspend fun rememberProduct(product: Product): Product = client.put("$base/v1/products/${product.barcode}") { contentType(ContentType.Application.Json); setBody(product) }.body()
    suspend fun movements(): List<StockMovement> = cached?.movements ?: client.get("$base/v1/stock/movements").body()
    suspend fun stock(request: StockRequest): StockMovement = queued("POST", "/v1/stock", json.encodeToString(request), "${request.kind} ${request.barcode}", { StockMovement(request.id, "pending", "", emptyList()) }) {
        client.post("$base/v1/stock") { contentType(ContentType.Application.Json); setBody(request) }.body()
    }
    suspend fun undoStock(id: String, operationId: String): StockMovement = client.post("$base/v1/stock/$id/undo") { contentType(ContentType.Application.Json); setBody(UndoStockRequest(operationId)) }.body()
    suspend fun cook(id: String, request: CookRequest): StockMovement = client.post("$base/v1/recipes/$id/cook") { contentType(ContentType.Application.Json); setBody(request) }.body()
    fun imageHeaders(): coil3.network.NetworkHeaders = coil3.network.NetworkHeaders.Builder().apply {
        session?.let { set("Authorization", "Bearer ${it.token}"); set("X-Basil-Household", it.householdId) }
    }.build()
    fun close() = client.close()
}
