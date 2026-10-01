package app.basil.mobile

import app.basil.contracts.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class OfflineTest {
    private class Store : DeviceStore {
        val values = mutableMapOf<String, String>()
        override fun read(key: String) = values[key]
        override fun write(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
    }
    @Test fun persistedScanQueueReplaysOnceAfterRestart() = runBlocking {
        val store = Store(); var disconnected = false; var additions = 0
        val snapshot = HouseholdSnapshot("v1", emptyList(), emptyList(), ShoppingList("shopping", emptyList(), emptyList()), emptyList(), emptyList())
        fun engine() = MockEngine { request ->
            if (disconnected) throw IllegalStateException("Network unavailable")
            val body = when (request.url.encodedPath) {
                "/session" -> Json.encodeToString(DeviceSession("test-token", "household", "device"))
                "/v1/sync" -> Json.encodeToString(snapshot)
                "/v1/stock" -> { additions++; Json.encodeToString(StockMovement("scan", "add", "date", emptyList())) }
                else -> error("Unexpected path")
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val api = BasilApi("https://example.test", store, engine())
        api.refresh(); disconnected = true
        api.stock(StockRequest("scan", "4006381333931", "add"))
        assertTrue(api.offline); assertEquals(1, api.pending.size)
        api.close()
        val restarted = BasilApi("https://example.test", store, engine())
        assertEquals(1, restarted.pending.size)
        assertTrue(restarted.recipes().isEmpty())
        disconnected = false; restarted.refresh(); restarted.refresh()
        assertTrue(restarted.pending.isEmpty()); assertEquals(1, additions)
        restarted.close()
    }
    @Test fun staleShoppingStaysVisibleAndRevocationClearsCacheAndQueue() = runBlocking {
        val store = Store(); var response = 200; var disconnected = false
        val snapshot = HouseholdSnapshot("v1", emptyList(), emptyList(), ShoppingList("shopping", emptyList(), emptyList()), emptyList(), emptyList())
        val engine = MockEngine { request ->
            if (disconnected) throw IllegalStateException("Network unavailable")
            val status = if (request.url.encodedPath == "/session") 200 else response
            val body = when {
                status != 200 -> Json.encodeToString(ApiError("conflict", "List changed"))
                request.url.encodedPath == "/session" -> Json.encodeToString(DeviceSession("token", "household", "device"))
                else -> Json.encodeToString(snapshot)
            }
            respond(body, HttpStatusCode.fromValue(status), headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val api = BasilApi("https://example.test", store, engine)
        api.refresh(); disconnected = true
        api.manualShopping(ManualShoppingRequest("extra", "Soap", "shopping"))
        assertEquals("Soap", api.shopping().items.single().name)
        disconnected = false; response = 409
        assertFailsWith<ApiFailure> { api.refresh() }
        assertNotNull(api.pending.single().error)
        response = 403
        assertFailsWith<ApiFailure> { api.refresh() }
        assertTrue(api.pending.isEmpty())
        assertFalse(store.values.keys.any { it.endsWith(".snapshot") })
        api.close()
    }
    @Test fun independentOfflineExtrasAndCheckOffReplayInOrder() = runBlocking {
        val store = Store(); var disconnected = false
        var shopping = ShoppingList("v1", emptyList(), emptyList())
        val engine = MockEngine { request ->
            if (disconnected) throw IllegalStateException("Disconnected")
            val body = when {
                request.url.encodedPath == "/session" -> Json.encodeToString(DeviceSession("token", "home", "device"))
                request.url.encodedPath == "/v1/sync" -> Json.encodeToString(HouseholdSnapshot(shopping.version, emptyList(), emptyList(), shopping, emptyList(), emptyList()))
                request.url.encodedPath == "/v1/shopping" -> Json.encodeToString(shopping)
                request.url.encodedPath == "/v1/shopping/items" -> {
                    val item = Json.decodeFromString<ManualShoppingRequest>((request.body as io.ktor.http.content.TextContent).text)
                    assertEquals(shopping.version, item.expectedVersion)
                    shopping = shopping.copy(version = shopping.version + "x", items = shopping.items + ShoppingItem("manual-${item.id}", item.name, null, "", manual = true))
                    Json.encodeToString(shopping)
                }
                request.url.encodedPath.endsWith("/manual-a") -> {
                    val check = Json.decodeFromString<CheckShoppingRequest>((request.body as io.ktor.http.content.TextContent).text)
                    assertEquals(shopping.version, check.expectedVersion)
                    shopping = shopping.copy(version = shopping.version + "x", items = shopping.items.map { if (it.id == "manual-a") it.copy(checked = check.checked) else it })
                    Json.encodeToString(shopping)
                }
                else -> error("Unexpected path")
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val api = BasilApi("https://example.test", store, engine)
        api.refresh(); disconnected = true
        api.manualShopping(ManualShoppingRequest("a", "Soap", "v1"))
        api.manualShopping(ManualShoppingRequest("b", "Sponges", "v1"))
        api.checkShopping("manual-a", CheckShoppingRequest(true, "v1"))
        assertTrue(api.shopping().items.first().checked)
        disconnected = false; api.refresh()
        assertTrue(api.pending.isEmpty()); assertEquals(2, shopping.items.size); assertTrue(shopping.items.first().checked)
        api.close()
    }

}
