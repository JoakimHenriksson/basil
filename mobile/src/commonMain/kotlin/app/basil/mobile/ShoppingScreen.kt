package app.basil.mobile

import app.basil.contracts.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.random.Random

fun shoppingId(): String = List(4) { Random.nextInt().toUInt().toString(16) }.joinToString("-")

@Composable
fun ShoppingScreen(list: ShoppingList?, recipes: List<Recipe>, enabled: Boolean,
    onRefresh: () -> Unit, onServings: (String, Int) -> Unit, onRemoveMeal: (String) -> Unit,
    onManual: (String) -> Unit, onCheck: (String, Boolean) -> Unit, onRemoveManual: (String) -> Unit) {
    Text("Shopping list", style = MaterialTheme.typography.headlineSmall)
    TextButton(enabled = enabled, onClick = onRefresh) { Text("Refresh shopping") }
    if (list == null) { Text("Load your list to start shopping."); return }
    Text("Meals to shop for", style = MaterialTheme.typography.titleLarge)
    if (list.selections.isEmpty()) Text("Open a recipe, choose servings, and add it to shopping.")
    list.selections.forEach { meal ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(meal.recipe.title, fontWeight = FontWeight.SemiBold)
                val current = recipes.find { it.id == meal.recipe.id }
                if (current == null || current.revision != meal.recipe.revision) Text("Using your selected saved version. Remove and add the recipe again to use an updated copy.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = enabled && meal.servings > 1, onClick = { onServings(meal.id, meal.servings - 1) }) { Text("−") }
                    Text("${meal.servings} servings", Modifier.padding(top = 12.dp))
                    TextButton(enabled = enabled && meal.servings < 1000, onClick = { onServings(meal.id, meal.servings + 1) }) { Text("+") }
                    TextButton(enabled = enabled, onClick = { onRemoveMeal(meal.id) }) { Text("Remove meal") }
                }
            }
        }
    }
    list.warnings.forEach { Text(it) }
    Text("What to buy", style = MaterialTheme.typography.titleLarge)
    Text("Stock is shared across all selected meals. Checking an item marks it bought; add the purchase to Pantry to update stock.", style = MaterialTheme.typography.bodySmall)
    if (list.items.isEmpty()) Text("Nothing missing from your tracked ingredients.")
    list.items.sortedBy { it.checked }.forEach { item ->
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(item.checked, enabled = enabled, onCheckedChange = { onCheck(item.id, it) })
                Column(Modifier.weight(1f).padding(top = 12.dp)) {
                    Text((if (item.needsReview) "Check amount: " else "") + item.name, fontWeight = FontWeight.SemiBold)
                    item.amount?.let { Text("$it ${if (item.unit == "piece") "pieces" else item.unit}") }
                    if (item.selectionIds.isNotEmpty()) Text(list.selections.filter { it.id in item.selectionIds }.joinToString { it.recipe.title }, style = MaterialTheme.typography.bodySmall)
                }
                if (item.manual) TextButton(enabled = enabled, onClick = { onRemoveManual(item.id) }) { Text("Remove item") }
            }
        }
    }
    var manual by remember { mutableStateOf("") }
    OutlinedTextField(manual, { manual = it }, enabled = enabled, label = { Text("Extra shopping item") }, modifier = Modifier.fillMaxWidth())
    Button(enabled = enabled && manual.isNotBlank(), onClick = { onManual(manual); manual = "" }) { Text("Add shopping item") }
}
