package app.basil.contracts
import kotlinx.serialization.Serializable

@Serializable data class SessionRequest(val name: String)
@Serializable data class DeviceSession(val token: String, val householdId: String, val memberId: String)
@Serializable data class HouseholdMember(val id: String, val name: String, val role: String)
@Serializable data class HouseholdInfo(val id: String, val members: List<HouseholdMember>, val memberId: String)
@Serializable data class HouseholdInvite(val id: String, val code: String, val expiresAt: String)
@Serializable data class JoinHouseholdRequest(val code: String)
@Serializable data class JoinedHousehold(val householdId: String)
@Serializable data class HouseholdSnapshot(val version: String, val recipes: List<Recipe>, val pantry: List<PantryItem>, val shopping: ShoppingList, val products: List<Product>, val movements: List<StockMovement>)
