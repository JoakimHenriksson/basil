package app.basil.mobile

import app.basil.contracts.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@Composable fun HouseholdScreen(api: BasilApi, enabled: Boolean, onAction: (suspend () -> Unit) -> Unit) {
    var info by remember { mutableStateOf<HouseholdInfo?>(null) }
    var invite by remember { mutableStateOf<HouseholdInvite?>(null) }
    var code by remember { mutableStateOf("") }
    Text("Household", style = MaterialTheme.typography.headlineSmall)
    TextButton(enabled = enabled, onClick = { onAction { info = api.household() } }) { Text("Show household members") }
    info?.let { household ->
        household.members.forEach { member ->
            Text("${member.name} · ${member.role}" + if (member.id == household.memberId) " (this device)" else "")
            if (household.members.any { it.id == household.memberId && it.role == "owner" } && member.role != "owner") {
                TextButton(enabled = enabled, onClick = { onAction { api.revokeMember(member.id); info = api.household() } }) { Text("Remove ${member.name}") }
            }
        }
        if (household.members.any { it.id == household.memberId && it.role == "owner" }) TextButton(enabled = enabled, onClick = { onAction { invite = api.invite() } }) { Text("Invite a household member") }
    }
    invite?.let { current ->
        Text("Share this invitation code. It works once and expires after 24 hours.")
        OutlinedTextField(current.code, {}, readOnly = true, label = { Text("Invitation code") }, modifier = Modifier.fillMaxWidth())
        TextButton(enabled = enabled, onClick = { onAction { api.revokeInvite(current.id); invite = null } }) { Text("Revoke invitation") }
    }
    OutlinedTextField(code, { code = it }, enabled = enabled, label = { Text("Join with invitation code") }, modifier = Modifier.fillMaxWidth())
    Button(enabled = enabled && code.isNotBlank(), onClick = { onAction { api.join(code); code = ""; info = api.household(); invite = null } }) { Text("Join household") }
}
