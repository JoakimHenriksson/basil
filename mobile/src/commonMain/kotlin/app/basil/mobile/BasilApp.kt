package app.basil.mobile

import app.basil.contracts.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

private val basilColors = lightColorScheme(
    primary = Color(0xFF285B3E), onPrimary = Color.White,
    primaryContainer = Color(0xFFDDEAD9), background = Color(0xFFF9F7F1),
    surface = Color(0xFFFFFDFA), onSurface = Color(0xFF252C23),
)

@Composable
fun BasilApp(baseUrl: String, sharedUrl: String = "") {
    MaterialTheme(colorScheme = basilColors) {
        val api = remember(baseUrl) { BasilApi(baseUrl) }
        val scope = rememberCoroutineScope()
        var recipes by remember { mutableStateOf<List<Recipe>>(emptyList()) }
        var pantry by remember { mutableStateOf<List<PantryItem>>(emptyList()) }
        var selected by remember { mutableStateOf<Recipe?>(null) }
        var preview by remember { mutableStateOf<RecipePreview?>(null) }
        var servings by remember { mutableIntStateOf(1) }
        var tab by remember { mutableStateOf("Recipes") }
        var url by remember { mutableStateOf(sharedUrl) }
        var busy by remember { mutableStateOf(false) }
        var previewLoading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        fun operate(block: suspend () -> Unit) {
            scope.launch {
                busy = true; error = null
                try { block() } catch (failure: Exception) { error = failure.message ?: "Couldn't connect to Basil. Please try again." }
                finally { busy = false }
            }
        }
        LaunchedEffect(sharedUrl) { if (sharedUrl.isNotBlank()) { url = sharedUrl; selected = null; tab = "Recipes" } }
        LaunchedEffect(api) {
            busy = true
            try { recipes = api.recipes(); pantry = api.pantry() }
            catch (failure: Exception) { error = "Couldn't load your recipes. ${failure.message.orEmpty()}" }
            finally { busy = false }
        }
        LaunchedEffect(selected?.id, selected?.baseServings, servings, pantry) {
            val recipe = selected ?: return@LaunchedEffect
            if (recipe.baseServings == null) return@LaunchedEffect
            previewLoading = true
            preview = null
            try { preview = api.preview(recipe.id, servings) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "Couldn't update the missing list." }
            finally { previewLoading = false }
        }
        DisposableEffect(api) { onDispose { api.close() } }
        val scrollState = rememberScrollState()
        LaunchedEffect(tab, selected?.id) { scrollState.scrollTo(0) }
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 22.dp).verticalScroll(scrollState), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Spacer(Modifier.height(10.dp))
                Text("Basil", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("What would you like to eat?", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("Recipes", "Pantry").forEach { name -> FilterChip(selected = tab == name, enabled = !busy, onClick = { tab = name; selected = null; preview = null }, label = { Text(name) }) }
                }
                if (busy || previewLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { message ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(message)
                            TextButton(enabled = !busy, onClick = { operate { recipes = api.recipes(); pantry = api.pantry() } }) { Text("Try again") }
                        }
                    }
                }
                if (tab == "Pantry") {
                    PantryEntry(enabled = !busy, onAdd = { item -> operate { api.add(item); pantry = api.pantry() } })
                    if (pantry.isEmpty()) Text("Add what you have. Your recipes will use it to work out what's missing.")
                    pantry.forEach { item ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(Modifier.weight(1f)) { Text(item.name, fontWeight = FontWeight.SemiBold); Text(amountLabel(item.amount, item.unit)) }
                                TextButton(enabled = !busy, onClick = { operate { api.remove(item.id); pantry = api.pantry() } }) { Text("Remove") }
                            }
                        }
                    }
                } else if (selected == null) {
                    Text("Keep a favourite", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(url, { url = it }, label = { Text("Recipe link") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    Button(enabled = !busy && url.isNotBlank(), modifier = Modifier.fillMaxWidth(), onClick = {
                        operate { val saved = api.import(url.trim()); recipes = api.recipes(); selected = saved; servings = saved.baseServings ?: 1; preview = null; url = "" }
                    }) { Text("Save recipe") }
                    Text("Try a recipe from ICA, Arla, or Zeinas Kitchen. A saved copy stays here even if the original page disappears.", style = MaterialTheme.typography.bodyMedium)
                    Text("Your recipes", style = MaterialTheme.typography.titleLarge)
                    if (recipes.isEmpty()) Text("Your next good meal starts with a recipe. Paste a link above or share one from your browser.")
                    recipes.asReversed().forEach { recipe ->
                        Card(onClick = { selected = recipe; servings = recipe.baseServings ?: 1; preview = null }, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(recipe.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("${recipe.ingredients.size} ingredients · ${recipe.yieldText.ifBlank { "Check yield" }}", style = MaterialTheme.typography.bodyMedium)
                                Text(recipe.sourceUrl.substringAfter("://").substringBefore('/'), color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                } else {
                    val recipe = selected!!
                    TextButton(onClick = { selected = null; preview = null }, enabled = !busy) { Text("Back to recipes") }
                    Text(recipe.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Saved from ${recipe.sourceUrl}", style = MaterialTheme.typography.bodySmall)
                    recipe.warnings.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (recipe.baseServings == null) {
                        var baseYield by remember(recipe.id) { mutableStateOf("") }
                        Text("The original recipe makes ${recipe.yieldText.ifBlank { "an unspecified amount" }}. Choose its base yield to enable scaling.")
                        OutlinedTextField(baseYield, { baseYield = it }, label = { Text("Original servings or pieces") }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Button(enabled = !busy && baseYield.toIntOrNull() in 1..1000, onClick = {
                            operate { val updated = api.setYield(recipe.id, baseYield.toInt()); selected = updated; servings = updated.baseServings ?: 1; recipes = api.recipes() }
                        }) { Text("Set base yield") }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            OutlinedButton(enabled = !busy && !previewLoading && servings > 1, onClick = { servings--; preview = null }) { Text("−") }
                            Text("$servings ${if (recipe.yieldText.contains("makes", true)) "pieces" else "servings"}", Modifier.padding(top = 12.dp))
                            OutlinedButton(enabled = !busy && !previewLoading && servings < 1000, onClick = { servings++; preview = null }) { Text("+") }
                        }
                        Button(enabled = !busy && !previewLoading, modifier = Modifier.fillMaxWidth(), onClick = { operate { preview = api.preview(recipe.id, servings) } }) { Text("Refresh missing list") }
                    }
                    preview?.let { result ->
                        Text("Missing list", style = MaterialTheme.typography.titleLarge)
                        if (result.missing.isEmpty()) Text("You have the tracked ingredients for this meal.")
                        result.missing.forEach { item -> Text(if (item.needsReview) "Check: ${item.name}" else "${amountLabel(item.amount, item.unit)} ${item.name}") }
                    }
                    Text(if (preview == null) "Original ingredients" else "Ingredients for $servings", style = MaterialTheme.typography.titleLarge)
                    (preview?.ingredients ?: recipe.ingredients).forEach { item -> Text(if (item.amount != null) "${amountLabel(item.amount, item.unit)} ${item.name}" else item.original) }
                    Text("Method", style = MaterialTheme.typography.titleLarge)
                    recipe.instructions.forEachIndexed { index, step -> Text("${index + 1}. $step") }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun PantryEntry(enabled: Boolean, onAdd: (PantryRequest) -> Unit) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("g") }
    Text("What do you have?", style = MaterialTheme.typography.titleLarge)
    OutlinedTextField(name, { name = it }, label = { Text("Ingredient") }, enabled = enabled, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(amount, { amount = it }, label = { Text("Amount") }, enabled = enabled, modifier = Modifier.fillMaxWidth(), singleLine = true)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("g", "ml", "piece").forEach { choice -> FilterChip(selected = unit == choice, onClick = { unit = choice }, enabled = enabled, label = { Text(if (choice == "piece") "pieces" else choice) }) }
    }
    Button(enabled = enabled && name.isNotBlank() && amount.isNotBlank(), onClick = { onAdd(PantryRequest(name, amount, unit)) }, modifier = Modifier.fillMaxWidth()) { Text("Add to pantry") }
}

private fun amountLabel(amount: String?, unit: String): String = "${amount.orEmpty()} ${if (unit == "piece") "pieces" else unit}".trim()
