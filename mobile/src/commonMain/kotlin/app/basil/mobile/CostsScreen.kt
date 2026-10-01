package app.basil.mobile

import app.basil.contracts.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable fun CostSummary(estimate: CostEstimate) {
    Text((if (estimate.missingPrices.isEmpty()) "Estimated cost: " else "Known cost so far: ") + moneyLabel(estimate.knownMinor, estimate.currency))
    estimate.perServingMinor?.let { Text("${moneyLabel(it, estimate.currency)} per serving" + if (estimate.missingPrices.isNotEmpty()) " for priced ingredients" else "") }
    if (estimate.missingPrices.isNotEmpty()) Text("Prices or amounts missing: ${estimate.missingPrices.joinToString()}")
    estimate.sources.forEach { Text("${it.name}: ${moneyLabel(it.totalMinor, estimate.currency)} for ${it.quantity} ${it.unit}, bought ${it.purchasedOn}", style = MaterialTheme.typography.bodySmall) }
}

@Composable fun CostsScreen(api: BasilApi, enabled: Boolean, products: List<Product>, movements: List<StockMovement>,
    onAction: (suspend () -> Unit) -> Unit, onError: (String) -> Unit) {
    var summary by remember { mutableStateOf<BudgetSummary?>(null) }
    var purchases by remember { mutableStateOf<List<Purchase>>(emptyList()) }
    var month by remember { mutableStateOf("") }
    var limit by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("SEK") }
    var scannedText by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf<ReceiptDraft?>(null) }
    var correction by remember { mutableStateOf<Purchase?>(null) }
    var trip by remember { mutableStateOf<CostEstimate?>(null) }
    val reader = rememberPhotoTextReader({ scannedText = it }, onError)
    LaunchedEffect(api) { onAction { summary = api.budget(); month = summary!!.month; currency = summary!!.settings.currency; limit = summary!!.settings.monthlyLimitMinor?.let { moneyLabel(it, "").trim() }.orEmpty(); purchases = api.purchases() } }
    LaunchedEffect(api.cachedSnapshot()?.version) { purchases = api.purchases() }
    Text("Costs and budget", style = MaterialTheme.typography.headlineSmall)
    OutlinedTextField(month, { month = it }, label = { Text("Budget month (YYYY-MM)") }, enabled = enabled, modifier = Modifier.fillMaxWidth())
    TextButton(enabled = enabled && month.isNotBlank(), onClick = { onAction { summary = api.budget(month); purchases = api.purchases() } }) { Text("Refresh budget") }
    summary?.let { result ->
        Text("Spent: ${moneyLabel(result.spentMinor, result.settings.currency)}")
        result.remainingMinor?.let { Text("Remaining: ${moneyLabel(it, result.settings.currency)}") }
    }
    OutlinedTextField(limit, { limit = it }, label = { Text("Monthly grocery limit (optional)") }, enabled = enabled, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(currency, { currency = it.uppercase() }, label = { Text("Currency") }, enabled = enabled, modifier = Modifier.fillMaxWidth())
    TextButton(enabled = enabled, onClick = { onAction { api.setBudget(BudgetSettings(currency, limit.takeIf(String::isNotBlank)?.let(::parseMinor))); summary = api.budget(month) } }) { Text("Save budget") }
    TextButton(enabled = enabled, onClick = { onAction { trip = api.shoppingCost() } }) { Text("Estimate shopping packages") }
    trip?.let { CostSummary(it) }
    Text("Scan a receipt", style = MaterialTheme.typography.titleLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(enabled = enabled, onClick = reader.capture) { Text("Photograph receipt") }
        TextButton(enabled = enabled, onClick = reader.choose) { Text("Choose receipt photo") }
    }
    OutlinedTextField(scannedText, { scannedText = it }, label = { Text("Receipt text") }, minLines = 4, enabled = enabled, modifier = Modifier.fillMaxWidth())
    Button(enabled = enabled && scannedText.isNotBlank(), onClick = { onAction { draft = api.receiptDraft(scannedText); correction = null } }) { Text("Extract receipt") }
    draft?.let { current -> ReceiptEditor(current, correction, currency, products, movements, enabled,
        onSave = { request -> onAction {
            val old = correction
            if (old == null) api.acceptReceipt(request)
            else api.correctReceipt(old.receipt.id, ReceiptCorrection(old.revision, request.store, request.purchasedOn, request.totalMinor, request.lines.associate { it.id to it.totalMinor }))
            draft = null; correction = null; scannedText = ""; api.refresh(); purchases = api.purchases(); summary = api.budget(month)
        } }, onCancel = { draft = null; correction = null }) }
    Text("Recorded purchases", style = MaterialTheme.typography.titleLarge)
    purchases.asReversed().forEach { purchase ->
        Text("${purchase.receipt.purchasedOn} · ${purchase.receipt.store} · ${moneyLabel(purchase.receipt.totalMinor, purchase.receipt.currency)}")
        TextButton(enabled = enabled, onClick = {
            correction = purchase
            draft = ReceiptDraft(purchase.receipt.store, purchase.receipt.purchasedOn, purchase.receipt.totalMinor, purchase.receipt.lines, listOf("Price corrections keep existing stock effects. Use Pantry to correct stock quantities separately."))
        }) { Text("Correct purchase") }
    }
}

@Composable private fun ReceiptEditor(draft: ReceiptDraft, existing: Purchase?, currency: String, products: List<Product>, movements: List<StockMovement>,
    enabled: Boolean, onSave: (ReceiptRequest) -> Unit, onCancel: () -> Unit) {
    val id = remember(draft) { existing?.receipt?.id ?: shoppingId() }
    var store by remember(draft) { mutableStateOf(draft.store) }
    var date by remember(draft) { mutableStateOf(draft.purchasedOn.orEmpty()) }
    var total by remember(draft) { mutableStateOf(draft.totalMinor?.let { moneyLabel(it, "").trim() }.orEmpty()) }
    var reference by remember(draft) { mutableStateOf(existing?.receipt?.reference.orEmpty()) }
    var lines by remember(draft) { mutableStateOf(draft.lines) }
    var linePrices by remember(draft) { mutableStateOf(draft.lines.associate { it.id to moneyLabel(it.totalMinor, "").trim() }) }
    var mapped by remember(draft) { mutableStateOf<Set<String>>(emptySet()) }
    Text(if (existing == null) "Review receipt" else "Correct receipt", style = MaterialTheme.typography.titleLarge)
    draft.warnings.forEach { Text(it) }
    OutlinedTextField(store, { store = it }, label = { Text("Store") }, enabled = enabled, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(date, { date = it }, label = { Text("Purchase date (YYYY-MM-DD)") }, enabled = enabled, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(total, { total = it }, label = { Text("Printed receipt total") }, enabled = enabled, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(reference, { reference = it }, label = { Text("Receipt number (optional)") }, enabled = enabled && existing == null, modifier = Modifier.fillMaxWidth())
    lines.forEach { line ->
        fun update(next: ReceiptLine) { lines = lines.map { if (it.id == line.id) next else it } }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(line.name, { update(line.copy(name = it)) }, label = { Text("Receipt item") }, enabled = enabled && existing == null, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(linePrices[line.id].orEmpty(), { linePrices = linePrices + (line.id to it) }, label = { Text("Item total price") }, enabled = enabled, modifier = Modifier.fillMaxWidth())
                if (existing == null) {
                    TextButton(enabled = enabled, onClick = { mapped = if (line.id in mapped) mapped - line.id else mapped + line.id }) { Text("Ingredient and stock details") }
                    if (line.id in mapped) {
                        products.takeLast(5).forEach { product -> TextButton(enabled = enabled, onClick = { update(line.copy(ingredient = product.ingredient, quantity = product.packageAmount, unit = product.unit)) }) { Text(product.name) } }
                        OutlinedTextField(line.ingredient, { update(line.copy(ingredient = it)) }, label = { Text("Ingredient (optional)") }, enabled = enabled, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(line.quantity.orEmpty(), { update(line.copy(quantity = it.takeIf(String::isNotBlank))) }, label = { Text("Purchased quantity, all packages") }, enabled = enabled, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(line.unit, { update(line.copy(unit = it)) }, label = { Text("Unit: g, ml, piece") }, enabled = enabled, modifier = Modifier.fillMaxWidth())
                        var packages by remember(line.id) { mutableStateOf(line.packages.toString()) }
                        OutlinedTextField(packages, { packages = it; update(line.copy(packages = it.toIntOrNull() ?: 0)) }, label = { Text("Number of packages") }, enabled = enabled, modifier = Modifier.fillMaxWidth())
                        Text("Spending only is the default. Add stock only if you haven't already scanned it.")
                        Row { listOf("none", "add", "link").forEach { action -> FilterChip(line.stockAction == action, onClick = { update(line.copy(stockAction = action, linkedAddId = null, linkedAddIds = emptyList())) }, enabled = enabled, label = { Text(when(action) { "none" -> "Spend only"; "add" -> "Add stock"; else -> "Link scan" }) }) } }
                        if (line.stockAction == "link") movements.filter { it.kind == "add" && it.deltas.all { d -> d.lot.name == line.ingredient && d.lot.unit == line.unit } }.takeLast(10).forEach { scan ->
                            FilterChip(scan.id in line.linkedAddIds || line.linkedAddId == scan.id, onClick = { update(line.copy(linkedAddId = null, linkedAddIds = if (scan.id in line.linkedAddIds) line.linkedAddIds - scan.id else line.linkedAddIds + scan.id)) }, enabled = enabled, label = { Text(scan.deltas.joinToString { "${it.amount} ${it.lot.unit}" } + " · " + scan.at.take(16).replace('T', ' ')) })
                        }
                    }
                    TextButton(enabled = enabled, onClick = { lines = lines.filterNot { it.id == line.id } }) { Text("Remove receipt line") }
                }
            }
        }
    }
    if (existing == null) TextButton(enabled = enabled, onClick = {
        val line = ReceiptLine(shoppingId(), "", 0); lines = lines + line; linePrices = linePrices + (line.id to "")
    }) { Text("Add receipt line") }
    val pricesValid = lines.all { runCatching { parseMinor(linePrices[it.id].orEmpty()) }.isSuccess }
    if (pricesValid) Text("Item sum: " + moneyLabel(lines.sumOf { parseMinor(linePrices[it.id].orEmpty()) }, currency))
    Button(enabled = enabled && lines.isNotEmpty() && pricesValid && store.isNotBlank() && date.isNotBlank() && runCatching { parseMinor(total) }.isSuccess,
        onClick = { onSave(ReceiptRequest(id, store, date, currency, parseMinor(total), lines.map { it.copy(totalMinor = parseMinor(linePrices[it.id].orEmpty())) }, reference)) }) { Text(if (existing == null) "Record receipt" else "Save receipt correction") }
    TextButton(enabled = enabled, onClick = onCancel) { Text("Cancel receipt") }
}
