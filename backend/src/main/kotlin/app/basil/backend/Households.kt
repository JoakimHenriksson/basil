package app.basil.backend

import app.basil.contracts.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.*

class AccessDenied(val status: Int) : RuntimeException(if (status == 401) "Your session is unavailable. Connect again." else "You no longer have access to this household.")
@Serializable private data class MemberRecord(val householdId: String, val member: HouseholdMember)
@Serializable private data class SessionRecord(val hash: String, val memberId: String)
@Serializable private data class InviteRecord(val id: String, val hash: String, val householdId: String, val expiresAt: String)
@Serializable private data class HouseholdState(val members: List<MemberRecord> = emptyList(), val sessions: List<SessionRecord> = emptyList(), val invites: List<InviteRecord> = emptyList())

/** Device sessions for local prototype sharing; not production account identity. */
class Households(private val directory: Path, private val initialVault: Vault? = null) {
    private val file = directory.resolve("households.json")
    private val json = Json { prettyPrint = true }
    private var state = if (Files.exists(file)) json.decodeFromString<HouseholdState>(Files.readString(file)) else HouseholdState()
    private val vaults = mutableMapOf<String, Vault>()
    private fun secret(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
    private fun hash(token: String) = MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun save(next: HouseholdState) {
        Files.createDirectories(directory)
        val temporary = Files.createTempFile(directory, "households-", ".json")
        try { Files.writeString(temporary, json.encodeToString(next)); Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); state = next }
        finally { Files.deleteIfExists(temporary) }
    }
    @Synchronized fun register(request: SessionRequest): DeviceSession {
        require(request.name.isNotBlank() && request.name.length <= 80) { "Enter a device name of up to 80 characters." }
        val household = UUID.randomUUID().toString(); val member = UUID.randomUUID().toString(); val token = secret()
        // Copy the existing development vault into the first private household.
        if (state.members.isEmpty() && initialVault != null) {
            vaults[household] = initialVault.fork(directory.resolve(household).resolve("vault.json"))
        }
        save(state.copy(members = state.members + MemberRecord(household, HouseholdMember(member, request.name.trim(), "owner")), sessions = state.sessions + SessionRecord(hash(token), member)))
        return DeviceSession(token, household, member)
    }
    @Synchronized fun memberId(token: String): String = state.sessions.find { it.hash == hash(token) }?.memberId ?: throw AccessDenied(401)
    @Synchronized fun info(token: String, household: String): HouseholdInfo {
        val member = memberId(token)
        if (state.members.none { it.householdId == household && it.member.id == member }) throw AccessDenied(403)
        return HouseholdInfo(household, state.members.filter { it.householdId == household }.map { it.member }, member)
    }
    private fun owner(token: String, household: String) { val info = info(token, household); if (info.members.none { it.id == info.memberId && it.role == "owner" }) throw AccessDenied(403) }
    @Synchronized fun authorizedVault(token: String, household: String): Vault { info(token, household); return vault(household) }
    private fun vault(id: String): Vault = vaults.getOrPut(id) { Vault(directory.resolve(id).resolve("vault.json")) }
    @Synchronized fun invite(token: String, household: String): HouseholdInvite {
        owner(token, household)
        val code = secret(); val id = UUID.randomUUID().toString(); val expires = Instant.now().plusSeconds(86400).toString()
        save(state.copy(invites = state.invites.filter { Instant.parse(it.expiresAt).isAfter(Instant.now()) } + InviteRecord(id, hash(code), household, expires)))
        return HouseholdInvite(id, code, expires)
    }
    @Synchronized fun revokeInvite(token: String, household: String, id: String) { owner(token, household); save(state.copy(invites = state.invites.filterNot { it.householdId == household && it.id == id })) }
    @Synchronized fun join(token: String, code: String): JoinedHousehold {
        val memberId = memberId(token)
        val invite = requireNotNull(state.invites.find { it.hash == hash(code.trim()) && Instant.parse(it.expiresAt).isAfter(Instant.now()) }) { "That invitation is expired or unavailable." }
        val member = state.members.first { it.member.id == memberId }.member.copy(role = "member")
        val members = if (state.members.any { it.householdId == invite.householdId && it.member.id == memberId }) state.members else state.members + MemberRecord(invite.householdId, member)
        save(state.copy(members = members, invites = state.invites.filterNot { it.id == invite.id }))
        return JoinedHousehold(invite.householdId)
    }
    @Synchronized fun revokeMember(token: String, household: String, member: String) {
        owner(token, household)
        require(state.members.none { it.householdId == household && it.member.id == member && it.member.role == "owner" }) { "The household owner cannot be removed." }
        save(state.copy(members = state.members.filterNot { it.householdId == household && it.member.id == member }))
    }
}
