package app.basil.mobile

import app.basil.contracts.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable expect fun rememberBarcodeScanner(onResult: (String) -> Unit, onError: (String) -> Unit): () -> Unit

@Composable
fun PantryScanner(products: List<Product>, draft: ProductDraft?, movements: List<StockMovement>, enabled: Boolean,
    onBarcode: (String, String, String?) -> Unit, onRemember: (Product, String, String?) -> Unit,
    onError: (String) -> Unit, onUndo: (String) -> Unit) {
    var mode by remember { mutableStateOf("add") }
    var barcode by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    val scanner = rememberBarcodeScanner({ onBarcode(it, mode, amount.takeIf(String::isNotBlank)) }, onError)
    Text("Zap a product", style = MaterialTheme.typography.titleLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(mode == "add", onClick = { mode = "add" }, enabled = enabled, label = { Text("Add") })
        FilterChip(mode == "use", onClick = { mode = "use" }, enabled = enabled, label = { Text("Use") })
    }
    Text(if (mode == "add") "A scan adds one package." else "A scan uses one package or its tracked remainder.")
    OutlinedTextField(amount, { amount = it }, label = { Text("Partial amount (optional)") }, enabled = enabled, modifier = Modifier.fillMaxWidth())
    Button(enabled = enabled, onClick = scanner, modifier = Modifier.fillMaxWidth()) { Text("Scan to ${if (mode == "add") "add" else "use"}") }
    OutlinedTextField(barcode, { barcode = it }, label = { Text("Barcode (or type it)") }, enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth())
    TextButton(enabled = enabled && barcode.isNotBlank(), onClick = { onBarcode(barcode, mode, amount.takeIf(String::isNotBlank)) }) { Text("Find barcode") }
    if (draft != null) {
        var name by remember(draft.barcode) { mutableStateOf(draft.name) }
        var ingredient by remember(draft.barcode) { mutableStateOf("") }
        var packageAmount by remember(draft.barcode) { mutableStateOf(draft.packageAmount.orEmpty()) }
        var unit by remember(draft.barcode) { mutableStateOf(draft.unit) }
        Text("Remember this product", style = MaterialTheme.typography.titleMedium)
        if (draft.source.isNotBlank()) Text("Details from ${draft.source}")
        if (draft.warning.isNotBlank()) Text(draft.warning)
        OutlinedTextField(name, { name = it }, label = { Text("Product name") }, enabled = enabled, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(ingredient, { ingredient = it }, label = { Text("Ingredient, e.g. crushed tomatoes") }, enabled = enabled, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(packageAmount, { packageAmount = it }, label = { Text("Amount in one package") }, enabled = enabled, modifier = Modifier.fillMaxWidth())
        Row { listOf("g", "ml", "piece").forEach { choice -> FilterChip(unit == choice, onClick = { unit = choice }, enabled = enabled, label = { Text(choice) }) } }
        Button(enabled = enabled && name.isNotBlank() && ingredient.isNotBlank() && packageAmount.isNotBlank(), onClick = { onRemember(Product(draft.barcode, name, ingredient, packageAmount, unit), mode, amount.takeIf(String::isNotBlank)) }) { Text("Remember and ${if (mode == "add") "add" else "use"}") }
    }
    if (products.isNotEmpty()) {
        Text("Remembered products", style = MaterialTheme.typography.titleMedium)
        products.takeLast(10).asReversed().forEach { product ->
            TextButton(enabled = enabled, onClick = { onBarcode(product.barcode, mode, amount.takeIf(String::isNotBlank)) }) { Text("${product.name} · ${product.packageAmount} ${product.unit}") }
        }
    }
    val recent = movements.filter { it.kind != "undo" && movements.none { undo -> undo.undoOf == it.id } }.takeLast(5).asReversed()
    if (recent.isNotEmpty()) Text("Recent stock changes", style = MaterialTheme.typography.titleMedium)
    recent.forEach { movement ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${movement.kind}: " + movement.deltas.joinToString { "${it.amount} ${it.lot.unit} ${it.lot.name}" }, Modifier.weight(1f))
            TextButton(enabled = enabled, onClick = { onUndo(movement.id) }) { Text("Undo") }
        }
    }
}
