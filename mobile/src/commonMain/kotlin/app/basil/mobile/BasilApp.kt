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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.CachePolicy
import coil3.network.httpHeaders
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException

private val basilColors = lightColorScheme(
    primary = Color(0xFF285B3E), onPrimary = Color.White,
    primaryContainer = Color(0xFFDDEAD9), background = Color(0xFFF9F7F1),
    surface = Color(0xFFFFFDFA), onSurface = Color(0xFF252C23),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BasilApp(baseUrl: String, sharedUrl: String = "") {
    MaterialTheme(colorScheme = basilColors) {
        val deviceStore = rememberDeviceStore()
        val api = remember(baseUrl, deviceStore) { BasilApi(baseUrl, deviceStore) }
        var syncTick by remember { mutableIntStateOf(0) }
        val scope = rememberCoroutineScope()
        var recipes by remember { mutableStateOf<List<Recipe>>(emptyList()) }
        var products by remember { mutableStateOf<List<Product>>(emptyList()) }
        var movements by remember { mutableStateOf<List<StockMovement>>(emptyList()) }
        var productDraft by remember { mutableStateOf<ProductDraft?>(null) }
        var cooking by remember { mutableStateOf(false) }
        var reconciledUses by remember { mutableStateOf<Set<String>>(emptySet()) }
        var shopping by remember { mutableStateOf<ShoppingList?>(null) }
        var pantry by remember { mutableStateOf<List<PantryItem>>(emptyList()) }
        var editing by remember { mutableStateOf(false) }
        var deleting by remember { mutableStateOf(false) }
        var query by remember { mutableStateOf("") }
        var favouritesOnly by remember { mutableStateOf(false) }
        val shareExport = rememberExportSharer()
        var selected by remember { mutableStateOf<Recipe?>(null) }
        var preview by remember { mutableStateOf<RecipePreview?>(null) }
        var servings by remember { mutableIntStateOf(1) }
        var tab by remember { mutableStateOf("Recipes") }
        var url by remember { mutableStateOf(sharedUrl) }
        var busy by remember { mutableStateOf(false) }
        var previewLoading by remember { mutableStateOf(false) }
        var browserSaved by remember { mutableStateOf<Recipe?>(null) }
        var browserStart by remember { mutableStateOf("https://www.google.com/search?q=recipes") }
        var browserError by remember { mutableStateOf<String?>(null) }
        val uriHandler = LocalUriHandler.current
        var error by remember { mutableStateOf<String?>(null) }
        fun operate(block: suspend () -> Unit) {
            scope.launch {
                busy = true; error = null
                try {
                    block()
                    (if (api.offline) api.cachedSnapshot() else api.refresh())?.let { snapshot -> recipes = snapshot.recipes; pantry = snapshot.pantry; shopping = snapshot.shopping; products = snapshot.products; movements = snapshot.movements }
                    syncTick++
                } catch (failure: Exception) {
                    if (failure is ApiFailure && failure.status in listOf(401, 403)) {
                        recipes = emptyList(); pantry = emptyList(); shopping = null; products = emptyList(); movements = emptyList(); selected = null; preview = null; browserSaved = null; productDraft = null
                    }
                    error = failure.message ?: "Couldn't connect to Basil. Please try again."
                }
                finally { busy = false }
            }
        }
        LaunchedEffect(sharedUrl) { if (sharedUrl.isNotBlank()) { url = sharedUrl; selected = null; tab = "Recipes" } }
        LaunchedEffect(api) {
            busy = true
            try { api.refresh(); recipes = api.recipes(); pantry = api.pantry(); shopping = api.shopping(); products = api.products(); movements = api.movements() }
            catch (failure: Exception) { error = "Couldn't load your recipes. ${failure.message.orEmpty()}" }
            finally { busy = false }
        }
        LaunchedEffect(selected?.id, selected?.revision, selected?.baseServings, servings, pantry) {
            val recipe = selected ?: return@LaunchedEffect
            if (recipe.baseServings == null) return@LaunchedEffect
            previewLoading = true
            preview = null
            try { preview = api.preview(recipe.id, servings) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "Couldn't update the missing list." }
            finally { previewLoading = false }
        }
        LaunchedEffect(api) {
            while (true) {
                delay(30_000)
                if (!busy && !api.offline) {
                    try {
                        api.refresh()?.let { snapshot -> recipes = snapshot.recipes; pantry = snapshot.pantry; shopping = snapshot.shopping; products = snapshot.products; movements = snapshot.movements }
                        syncTick++
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) {
                        if (failure is ApiFailure && failure.status in listOf(401, 403)) {
                            recipes = emptyList(); pantry = emptyList(); shopping = null; products = emptyList(); movements = emptyList(); selected = null; preview = null; browserSaved = null; productDraft = null
                            error = failure.message
                        }
                    }
                }
            }
        }
        DisposableEffect(api) { onDispose { api.close() } }
        val scrollState = rememberScrollState()
        LaunchedEffect(tab, selected?.id) { scrollState.scrollTo(0) }
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (tab == "Browse") {
                Column(Modifier.fillMaxSize()) {
                    browserError?.let {
                        Row(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(it, Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { browserError = null }) { Text("Dismiss") }
                        }
                    }
                    RecipeBrowser(browserStart, !busy, browserSaved?.let { "Saved ${it.title}" },
                        onCapture = { page ->
                            browserError = null; browserSaved = null
                            scope.launch {
                                busy = true
                                try { browserSaved = api.capture(page); api.refresh(); recipes = api.recipes(); syncTick++ }
                                catch (failure: Exception) { browserError = failure.message ?: "Couldn't save this recipe." }
                                finally { busy = false }
                            }
                        },
                        onError = { browserError = it },
                        onClose = { tab = "Recipes" },
                        onViewSaved = { browserSaved?.let { selected = it; servings = it.baseServings ?: 1; tab = "Recipes" } })
                }
                return@Surface
            }
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 22.dp).verticalScroll(scrollState), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                @Suppress("UNUSED_VARIABLE") val syncVersion = syncTick
                Spacer(Modifier.height(10.dp))
                Text("Basil", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("What would you like to eat?", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("Recipes", "Pantry", "Shopping", "Household").forEach { name -> FilterChip(selected = tab == name, enabled = !busy, onClick = { tab = name; selected = null; editing = false; preview = null }, label = { Text(name) }) }
                }
                TextButton(enabled = !busy, onClick = { operate { api.refresh() } }) { Text("Sync household") }
                if (api.offline) Text("Offline · showing saved household data")
                if (api.pending.isNotEmpty()) {
                    Text("${api.pending.size} pending changes")
                    api.pending.forEach { action ->
                        Text(action.label + (action.error?.let { " · $it" } ?: " · waiting to sync"))
                        TextButton(enabled = !busy, onClick = { api.discardPending(action.id); syncTick++ }) { Text("Discard pending change") }
                    }
                }
                if (busy || previewLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { message ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(message)
                            TextButton(enabled = !busy, onClick = { operate { recipes = api.recipes(); pantry = api.pantry(); shopping = api.shopping(); products = api.products(); movements = api.movements() } }) { Text("Try again") }
                        }
                    }
                }
                if (editing) {
                    RecipeEditor(selected, !busy,
                        onSave = { request -> operate {
                            val updated = selected?.let { api.editRecipe(it.id, request) } ?: api.createRecipe(request)
                            recipes = api.recipes(); selected = updated; servings = updated.baseServings ?: 1; preview = null; editing = false
                        } }, onCancel = { editing = false })
                } else if (tab == "Household") {
                    HouseholdScreen(api, !busy, onAction = { action -> operate { action() } })
                } else if (tab == "Shopping") {
                    ShoppingScreen(shopping, recipes, !busy,
                        onRefresh = { operate { shopping = api.shopping(); products = api.products(); movements = api.movements(); recipes = api.recipes() } },
                        onServings = { id, count -> operate { shopping = api.changeSelection(id, SelectionServingsRequest(count, shopping!!.version)) } },
                        onRemoveMeal = { id -> operate { shopping = api.removeSelection(id, shopping!!.version) } },
                        onManual = { name -> operate { shopping = api.manualShopping(ManualShoppingRequest(shoppingId(), name, shopping!!.version)) } },
                        onCheck = { id, checked -> operate { shopping = api.checkShopping(id, CheckShoppingRequest(checked, shopping!!.version)) } },
                        onRemoveManual = { id -> operate { shopping = api.removeShopping(id, shopping!!.version) } })
                } else if (tab == "Pantry") {
                    PantryScanner(products, productDraft, movements, !busy,
                        onBarcode = { code, mode, amount -> operate {
                            val remembered = products.find { it.barcode == code.trim().padStart(14, '0') }
                            if (remembered == null) productDraft = api.productDraft(code.trim())
                            else {
                                api.stock(StockRequest(shoppingId(), remembered.barcode, mode, amount)); productDraft = null
                                pantry = api.pantry(); shopping = api.shopping(); movements = api.movements()
                            }
                        } },
                        onRemember = { product, mode, amount -> operate {
                            val saved = api.rememberProduct(product); products = api.products(); productDraft = null
                            api.stock(StockRequest(shoppingId(), saved.barcode, mode, amount))
                            pantry = api.pantry(); shopping = api.shopping(); movements = api.movements()
                        } }, onError = { error = it },
                        onUndo = { id -> operate { api.undoStock(id, shoppingId()); pantry = api.pantry(); shopping = api.shopping(); movements = api.movements() } })
                    PantryEntry(enabled = !busy, onAdd = { item -> operate { api.add(item); pantry = api.pantry(); shopping = api.shopping(); products = api.products(); movements = api.movements() } })
                    if (pantry.isEmpty()) Text("Add what you have. Your recipes will use it to work out what's missing.")
                    pantry.forEach { item ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(Modifier.weight(1f)) { Text(item.name, fontWeight = FontWeight.SemiBold); Text(amountLabel(item.amount, item.unit)) }
                                TextButton(enabled = !busy, onClick = { operate { api.remove(item.id); pantry = api.pantry(); shopping = api.shopping(); products = api.products(); movements = api.movements() } }) { Text("Remove") }
                            }
                        }
                    }
                } else if (selected == null) {
                    Button(onClick = { browserStart = url.takeIf { it.startsWith("https://") } ?: "https://www.google.com/search?q=recipes"; tab = "Browse"; browserSaved = null; browserError = null }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Browse recipes") }
                    Text("Keep a favourite", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(url, { url = it }, label = { Text("Recipe link") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    Button(enabled = !busy && url.isNotBlank(), modifier = Modifier.fillMaxWidth(), onClick = {
                        operate { val saved = api.import(url.trim()); recipes = api.recipes(); selected = saved; servings = saved.baseServings ?: 1; preview = null; url = "" }
                    }) { Text("Save recipe") }
                    Text("Try a recipe from ICA, Arla, or Zeinas Kitchen. A saved copy stays here even if the original page disappears.", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(enabled = !busy, onClick = { selected = null; editing = true }) { Text("Write a recipe") }
                        TextButton(enabled = !busy && recipes.isNotEmpty(), onClick = { operate { shareExport(api.exportRecipes()) } }) { Text("Export recipes") }
                    }
                    Text("Your recipes", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(query, { query = it }, label = { Text("Search recipes or ingredients") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    FilterChip(selected = favouritesOnly, onClick = { favouritesOnly = !favouritesOnly }, label = { Text("Favourites") })
                    val visible = recipes.asReversed().filter { recipe ->
                        (!favouritesOnly || recipe.favourite) && (query.isBlank() || recipe.title.contains(query.trim(), true) || recipe.notes.contains(query.trim(), true) || recipe.ingredients.any { it.original.contains(query.trim(), true) || it.name.contains(query.trim(), true) })
                    }
                    if (recipes.isNotEmpty() && visible.isEmpty()) Text("No recipes match this search.")
                    if (recipes.isEmpty()) Text("Your next good meal starts with a recipe. Paste a link above or share one from your browser.")
                    visible.forEach { recipe ->
                        Card(onClick = { selected = recipe; servings = recipe.baseServings ?: 1; preview = null }, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text((if (recipe.favourite) "★ " else "") + recipe.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("${recipe.ingredients.size} ingredients · ${recipe.yieldText.ifBlank { "Check yield" }}", style = MaterialTheme.typography.bodyMedium)
                                Text(recipe.sourceUrl.takeIf { it.isNotBlank() }?.substringAfter("://")?.substringBefore('/') ?: "Your recipe", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                } else {
                    val recipe = selected!!
                    TextButton(onClick = { selected = null; preview = null }, enabled = !busy) { Text("Back to recipes") }
                    Text(recipe.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(enabled = !busy, onClick = { operate { selected = api.favourite(recipe.id, !recipe.favourite); recipes = api.recipes() } }) { Text(if (recipe.favourite) "★ Favourite" else "☆ Favourite") }
                        TextButton(enabled = !busy, onClick = { editing = true }) { Text("Edit") }
                        TextButton(enabled = !busy, onClick = { deleting = true }) { Text("Delete") }
                    }
                    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Delete this recipe?") },
                        text = { Text("This removes the recipe and its saved revisions from your library.") },
                        confirmButton = { TextButton(enabled = !busy, onClick = { operate { api.deleteRecipe(recipe.id); recipes = api.recipes(); selected = null; deleting = false; preview = null } }) { Text("Delete") } },
                        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } })
                    recipe.imagePath?.let { path ->
                        AsyncImage(model = ImageRequest.Builder(LocalPlatformContext.current).data(baseUrl.trimEnd('/') + path).httpHeaders(api.imageHeaders()).memoryCachePolicy(CachePolicy.DISABLED).diskCachePolicy(CachePolicy.DISABLED).build(), contentDescription = recipe.title,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(220.dp))
                    }
                    if (recipe.sourceUrl.isNotBlank()) {
                        TextButton(onClick = { uriHandler.openUri(recipe.sourceUrl) }) { Text("Open original") }
                        Text("Saved from ${recipe.sourceUrl}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (recipe.notes.isNotBlank()) Text(recipe.notes)
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
                        Button(enabled = !busy && shopping != null, modifier = Modifier.fillMaxWidth(), onClick = { operate {
                            shopping = api.selectMeal(SelectMealRequest(shoppingId(), recipe.id, recipe.revision, servings, shopping!!.version))
                            selected = null; preview = null; tab = "Shopping"
                        } }) { Text("Add to shopping") }
                        Button(enabled = !busy && !previewLoading, modifier = Modifier.fillMaxWidth(), onClick = { operate { preview = api.preview(recipe.id, servings) } }) { Text("Refresh missing list") }
                    }
                    if (recipe.baseServings != null) TextButton(enabled = !busy, onClick = { cooking = true; reconciledUses = emptySet() }) { Text("Log cooked meal") }
                    if (cooking) AlertDialog(onDismissRequest = { cooking = false }, title = { Text("Log $servings servings cooked?") },
                        text = { Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())) {
                            Text("This uses the recipe's tracked ingredients. If you already used ingredients by scanning, select those Use records to count them once.")
                            movements.filter { use -> use.kind == "use" && movements.none { it.undoOf == use.id || (use.id in it.reconciledUseIds && movements.none { undo -> undo.undoOf == it.id }) } }.takeLast(10).forEach { use ->
                                Row {
                                    Checkbox(use.id in reconciledUses, enabled = !busy, onCheckedChange = { checked -> reconciledUses = if (checked) reconciledUses + use.id else reconciledUses - use.id })
                                    Text(use.deltas.joinToString { "${it.amount.removePrefix("-")} ${it.lot.unit} ${it.lot.name}" })
                                }
                            }
                        } },
                        confirmButton = { TextButton(enabled = !busy, onClick = { operate {
                            api.cook(recipe.id, CookRequest(shoppingId(), servings, recipe.revision, reconciledUses.toList()))
                            pantry = api.pantry(); shopping = api.shopping(); movements = api.movements(); cooking = false
                        } }) { Text("Log cooked") } },
                        dismissButton = { TextButton(onClick = { cooking = false }) { Text("Cancel") } })
                    preview?.let { result ->
                        result.warnings.filterNot { it in recipe.warnings }.forEach { Text(it) }
                        Text("Missing list", style = MaterialTheme.typography.titleLarge)
                        if (result.missing.isEmpty()) Text("You have the tracked ingredients for this meal.")
                        result.missing.forEach { item -> Text(if (item.needsReview) "Check: ${item.name}" else "${amountLabel(item.amount, item.unit)} ${item.name}") }
                    }
                    Text(if (preview == null) "Original ingredients" else "Ingredients for $servings", style = MaterialTheme.typography.titleLarge)
                    (preview?.ingredients ?: recipe.ingredients).forEach { item -> Text(if (item.amount != null) "${amountLabel(item.amount?.let { if (item.maxAmount != null) "$it–${item.maxAmount}" else it }, item.unit)} ${item.name}" else item.original) }
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
