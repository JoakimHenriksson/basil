package app.basil.backend

import app.basil.contracts.*
import java.nio.file.Files
import kotlin.test.*
import io.ktor.server.testing.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class HouseholdsTest {
    @Test fun `invites join once and owner revocation is durable`() {
        val directory = Files.createTempDirectory("basil-households")
        try {
            val homes = Households(directory)
            val owner = homes.register(SessionRequest("Owner phone"))
            val member = homes.register(SessionRequest("Other phone"))
            val recipe = homes.authorizedVault(owner.token, owner.householdId).create(RecipeEditRequest("Dinner", 2, listOf("2 eggs"), listOf("Cook.")))
            assertTrue(homes.authorizedVault(member.token, member.householdId).recipes().isEmpty())
            assertFailsWith<AccessDenied> { homes.authorizedVault(member.token, owner.householdId) }
            val invite = homes.invite(owner.token, owner.householdId)
            assertEquals(owner.householdId, homes.join(member.token, invite.code).householdId)
            assertFailsWith<IllegalArgumentException> { homes.join(member.token, invite.code) }
            assertEquals(recipe, homes.authorizedVault(member.token, owner.householdId).recipes().single())
            assertFailsWith<AccessDenied> { homes.invite(member.token, owner.householdId) }
            homes.revokeMember(owner.token, owner.householdId, member.memberId)
            assertFailsWith<AccessDenied> { Households(directory).authorizedVault(member.token, owner.householdId) }
            assertFalse(Files.readString(directory.resolve("households.json")).contains(owner.token))
            val revoked = homes.invite(owner.token, owner.householdId)
            homes.revokeInvite(owner.token, owner.householdId, revoked.id)
            assertFailsWith<IllegalArgumentException> { homes.join(member.token, revoked.code) }
        } finally { Files.walk(directory).use { it.sorted(Comparator.reverseOrder()).forEach(Files::delete) } }
    }
    @Test fun `first household preserves the complete legacy vault and media`() {
        val directory = Files.createTempDirectory("basil-migrate")
        try {
            val old = Vault(directory.resolve("legacy/vault.json"))
            val recipe = old.create(RecipeEditRequest("Dinner", 2, listOf("2 eggs"), listOf("Cook.")))
            old.favourite(recipe.id, true)
            old.rememberProduct(Product("4006381333931", "Tin", "crushed tomatoes", "425", "g"))
            old.stock(StockRequest("add", "4006381333931", "add"))
            old.selectMeal(SelectMealRequest("meal", recipe.id, 1, 2, old.shopping().version))
            Files.createDirectories(old.mediaDirectory); Files.writeString(old.mediaDirectory.resolve("photo.png"), "fixture")
            val before = old.snapshot()
            val homes = Households(directory.resolve("homes"), old)
            val session = homes.register(SessionRequest("Owner"))
            val adopted = homes.authorizedVault(session.token, session.householdId)
            assertEquals(before, adopted.snapshot())
            assertEquals("fixture", Files.readString(adopted.mediaDirectory.resolve("photo.png")))
            assertEquals(before, old.snapshot())
        } finally { Files.walk(directory).use { it.sorted(Comparator.reverseOrder()).forEach(Files::delete) } }
    }
    @Test fun `every food endpoint and media checks household access`() = testApplication {
        val directory = Files.createTempDirectory("basil-auth-api")
        val homes = Households(directory.resolve("homes"))
        val owner = homes.register(SessionRequest("Owner")); val stranger = homes.register(SessionRequest("Stranger"))
        application { basil(Vault(directory.resolve("local.json")), households = homes, extractor = null) }
        try {
            listOf("/v1/recipes", "/v1/pantry", "/v1/shopping", "/v1/products", "/v1/stock/movements", "/v1/sync", "/v1/media/photo.png", "/v1/recipes/export").forEach { path ->
                assertEquals(HttpStatusCode.Unauthorized, client.get(path).status, path)
                assertEquals(HttpStatusCode.Forbidden, client.get(path) { bearerAuth(stranger.token); header("X-Basil-Household", owner.householdId) }.status, path)
            }
            val before = client.get("/v1/sync") { bearerAuth(owner.token); header("X-Basil-Household", owner.householdId) }
            assertEquals(HttpStatusCode.OK, before.status)
            val snapshot = Json.decodeFromString<HouseholdSnapshot>(before.bodyAsText())
            assertEquals(HttpStatusCode.NoContent, client.get("/v1/sync?since=${snapshot.version}") { bearerAuth(owner.token); header("X-Basil-Household", owner.householdId) }.status)
            val rejected = client.post("/v1/pantry") { bearerAuth(stranger.token); header("X-Basil-Household", owner.householdId); contentType(ContentType.Application.Json); setBody(Json.encodeToString(PantryRequest("rice", "100", "g"))) }
            assertEquals(HttpStatusCode.Forbidden, rejected.status)
            assertTrue(homes.authorizedVault(owner.token, owner.householdId).pantry().isEmpty())
        } finally { Files.walk(directory).use { it.sorted(Comparator.reverseOrder()).forEach(Files::delete) } }
    }
}
