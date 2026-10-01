package app.basil.mobile

import app.basil.contracts.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json

@Composable fun SuggestionsScreen(api: BasilApi, pantry: List<PantryItem>, enabled: Boolean,
    onAction: (suspend () -> Unit) -> Unit, onChoose: (String, Int) -> Unit, onDraft: (RecipeEditRequest) -> Unit) {
    var servings by remember { mutableStateOf("2") }
    var preferences by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf<List<MealMatch>>(emptyList()) }
    var appleStatus by remember { mutableStateOf(appleRecipeAvailability()) }
    Text("What can we eat?", style = MaterialTheme.typography.headlineSmall)
    OutlinedTextField(servings, { servings = it }, label = { Text("People to feed") }, enabled = enabled, modifier = Modifier.fillMaxWidth())
    val count = servings.toIntOrNull()
    Button(enabled = enabled && count in 1..1000, onClick = { onAction { matches = api.savedSuggestions(count!!) } }) { Text("Find ready and close recipes") }
    matches.forEach { match ->
        Card(onClick = { onChoose(match.recipeId, match.preview?.servings ?: count ?: 2) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(match.title, style = MaterialTheme.typography.titleMedium)
                Text(match.status)
                match.preview?.missing?.forEach { item -> Text(if (item.needsReview) "Check: ${item.name}" else "${item.amount} ${item.unit} ${item.name}") }
                match.preview?.warnings?.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
    Text("Suggest a new meal", style = MaterialTheme.typography.titleLarge)
    OutlinedTextField(preferences, { preferences = it }, label = { Text("Preferences, e.g. quick vegetarian dinner") }, enabled = enabled, modifier = Modifier.fillMaxWidth())
    Text("Gemini sends the relevant pantry ingredients and preferences to Google when you tap below. Apple suggestions use the device model. Drafts open for review before saving.", style = MaterialTheme.typography.bodySmall)
    Button(enabled = enabled && count in 1..1000 && preferences.length <= 2000, onClick = { onAction { onDraft(validateRecipeDraft(api.geminiIdea(MealIdeaRequest(count!!, preferences)))) } }) { Text("Ask Gemini") }
    TextButton(enabled = enabled, onClick = { appleStatus = appleRecipeAvailability() }) { Text("Check Apple availability") }
    if (appleStatus != "available") Text(appleStatus)
    Button(enabled = enabled && appleStatus == "available" && count in 1..1000 && preferences.length <= 2000, onClick = { onAction {
        val raw = generateAppleRecipe(mealIdeaPrompt(pantry, MealIdeaRequest(count!!, preferences)))
        val draft = Json.decodeFromString<GeneratedRecipeDraft>(raw).reviewed("Apple Intelligence")
        onDraft(draft)
    } }) { Text("Ask Apple Intelligence") }
}
