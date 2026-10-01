package app.basil.mobile

import app.basil.contracts.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RecipeEditor(recipe: Recipe?, enabled: Boolean, onSave: (RecipeEditRequest) -> Unit, onCancel: () -> Unit) {
    var title by remember(recipe?.id) { mutableStateOf(recipe?.title.orEmpty()) }
    var yield by remember(recipe?.id) { mutableStateOf(recipe?.baseServings?.toString().orEmpty()) }
    var ingredients by remember(recipe?.id) { mutableStateOf(recipe?.ingredients?.joinToString("\n") { it.original }.orEmpty()) }
    var instructions by remember(recipe?.id) { mutableStateOf(recipe?.instructions?.joinToString("\n").orEmpty()) }
    var notes by remember(recipe?.id) { mutableStateOf(recipe?.notes.orEmpty()) }
    Text(if (recipe == null) "New recipe" else "Edit recipe", style = MaterialTheme.typography.headlineSmall)
    OutlinedTextField(title, { title = it }, label = { Text("Recipe title") }, enabled = enabled, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(yield, { yield = it }, label = { Text("Original servings (optional)") }, singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(ingredients, { ingredients = it }, label = { Text("Ingredients — one per line") }, minLines = 4, enabled = enabled, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(instructions, { instructions = it }, label = { Text("Method — one step per line") }, minLines = 4, enabled = enabled, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(notes, { notes = it }, label = { Text("Your notes") }, minLines = 2, enabled = enabled, modifier = Modifier.fillMaxWidth())
    Text("Keep amounts in each ingredient line, such as 425 g crushed tomatoes. Original versions and source links are retained.", style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(enabled = enabled && title.isNotBlank() && ingredients.isNotBlank() && instructions.isNotBlank() && (yield.isBlank() || yield.toIntOrNull() in 1..1000), onClick = {
            onSave(RecipeEditRequest(title, yield.toIntOrNull(), ingredients.lines().filter(String::isNotBlank), instructions.lines().filter(String::isNotBlank), notes, recipe?.revision ?: 1))
        }) { Text("Save changes") }
        TextButton(enabled = enabled, onClick = onCancel) { Text("Cancel") }
    }
}

@Composable
expect fun rememberExportSharer(): (ByteArray) -> Unit
