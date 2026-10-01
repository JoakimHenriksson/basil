package app.basil.backend

import app.basil.contracts.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

@Serializable private data class VaultState(val recipes: List<Recipe> = emptyList(), val pantry: List<PantryItem> = emptyList(), val recipeHistory: List<Recipe> = emptyList(), val shopping: ShoppingState = ShoppingState(), val products: List<Product> = emptyList(), val movements: List<StockMovement> = emptyList())

/** Local prototype storage. Production household persistence is a later slice. */
class Vault(private val file: Path) {
    val mediaDirectory: Path = file.toAbsolutePath().parent.resolve("media")
    private val json = Json { prettyPrint = true }
    private var state = if (Files.exists(file)) json.decodeFromString<VaultState>(Files.readString(file)) else VaultState()
    @Synchronized fun recipes(): List<Recipe> = state.recipes
    @Synchronized fun pantry(): List<PantryItem> = state.pantry
    @Synchronized fun recipe(id: String): Recipe? = state.recipes.find { it.id == id }
    @Synchronized fun setYield(id: String, servings: Int): Recipe? {
        require(servings in 1..1000) { "Choose between 1 and 1000 base servings." }
        val old = recipe(id) ?: return null
        val next = old.copy(baseServings = servings, revision = old.revision + 1)
        persist(state.copy(recipes = state.recipes.map { if (it.id == id) next else it }, recipeHistory = state.recipeHistory + old))
        return next
    }
    @Synchronized fun save(recipe: Recipe): Recipe {
        val existing = state.recipes.find { recipe.sourceUrl.isNotBlank() && it.sourceUrl == recipe.sourceUrl }
        if (existing != null) return existing
        persist(state.copy(recipes = state.recipes + recipe))
        return recipe
    }
    @Synchronized fun history(id: String): List<Recipe> = state.recipeHistory.filter { it.id == id } + listOfNotNull(recipe(id))
    @Synchronized fun edit(id: String, request: RecipeEditRequest): Recipe? {
        val old = recipe(id) ?: return null
        if (request.expectedRevision != old.revision) throw RecipeConflict()
        val next = validateEdit(request, old).copy(revision = old.revision + 1)
        persist(state.copy(recipes = state.recipes.map { if (it.id == id) next else it }, recipeHistory = state.recipeHistory + old))
        return next
    }
    @Synchronized fun create(request: RecipeEditRequest): Recipe {
        val blank = Recipe(UUID.randomUUID().toString(), "", "", null, "", emptyList(), emptyList(), java.time.Instant.now().toString())
        return save(validateEdit(request, blank))
    }
    private fun validateEdit(request: RecipeEditRequest, old: Recipe): Recipe {
        require(request.title.isNotBlank() && request.title.length <= 250) { "Enter a recipe title of up to 250 characters." }
        require(request.baseServings == null || request.baseServings in 1..1000) { "Choose between 1 and 1000 base servings." }
        require(request.ingredientLines.size in 1..200 && request.ingredientLines.all { it.isNotBlank() && it.length <= 1000 }) { "Enter at least one ingredient, with one ingredient per line." }
        require(request.instructions.size in 1..200 && request.instructions.all { it.isNotBlank() && it.length <= 10_000 }) { "Enter at least one cooking step." }
        require(request.notes.length <= 20_000) { "Recipe notes are too long." }
        val ingredients = request.ingredientLines.map(Ingredients::parse)
        val warnings = buildList {
            if (request.baseServings == null) add("The recipe yield needs review before scaling.")
            if (ingredients.any { it.needsReview }) add("Some ingredient amounts need review.")
        }
        return old.copy(title = request.title.trim(), baseServings = request.baseServings,
            yieldText = request.baseServings?.let { "$it servings" }.orEmpty(), ingredients = ingredients,
            instructions = request.instructions.map(String::trim), notes = request.notes.trim(), warnings = warnings)
    }
    @Synchronized fun favourite(id: String, value: Boolean): Recipe? {
        val updated = recipe(id)?.copy(favourite = value) ?: return null
        persist(state.copy(recipes = state.recipes.map { if (it.id == id) updated else it }))
        return updated
    }
    @Synchronized fun deleteRecipe(id: String): Boolean {
        if (recipe(id) == null) return false
        persist(state.copy(recipes = state.recipes.filterNot { it.id == id }, recipeHistory = state.recipeHistory.filterNot { it.id == id }))
        return true
    }
    @Synchronized fun add(request: PantryRequest): PantryItem {
        require(request.name.isNotBlank() && request.name.length <= 150) { "Enter an ingredient name." }
        require(request.unit in setOf("g", "ml", "piece")) { "Choose grams, millilitres, or pieces." }
        require(request.amount.length <= 30 && Ingredients.number(request.amount).signum() > 0) { "Enter a positive amount." }
        val item = PantryItem(UUID.randomUUID().toString(), Ingredients.canonical(request.name, request.unit), Ingredients.display(Ingredients.number(request.amount)), request.unit)
        persist(state.copy(pantry = state.pantry + item))
        return item
    }
    @Synchronized fun remove(id: String): Boolean {
        if (state.pantry.none { it.id == id }) return false
        persist(state.copy(pantry = state.pantry.filterNot { it.id == id }))
        return true
    }
    @Synchronized fun shopping(): ShoppingList = Shopping.list(state.shopping, state.pantry)
    private fun checkShoppingVersion(expected: String) = Shopping.requireVersion(state.shopping, state.pantry, expected)
    private fun saveShopping(next: ShoppingState): ShoppingList {
        persist(state.copy(shopping = next.copy(revision = state.shopping.revision + 1)))
        return shopping()
    }
    @Synchronized fun selectMeal(request: SelectMealRequest): ShoppingList {
        require(request.id.matches(Regex("[a-zA-Z0-9-]{1,80}"))) { "Invalid selection ID." }
        state.shopping.selections.find { it.id == request.id }?.let {
            require(it.recipe.id == request.recipeId && it.recipe.revision == request.recipeRevision && it.servings == request.servings) { "Selection ID already used." }
            return shopping()
        }
        checkShoppingVersion(request.expectedVersion)
        require(state.shopping.selections.size < 100) { "The shopping list already contains 100 meals." }
        val recipe = requireNotNull(recipe(request.recipeId)) { "That recipe is no longer available." }
        if (recipe.revision != request.recipeRevision) throw RecipeConflict()
        Ingredients.preview(recipe, request.servings, emptyList())
        return saveShopping(state.shopping.copy(selections = state.shopping.selections + MealSelection(request.id, recipe, request.servings)))
    }
    @Synchronized fun changeSelection(id: String, request: SelectionServingsRequest): ShoppingList {
        checkShoppingVersion(request.expectedVersion)
        val selected = requireNotNull(state.shopping.selections.find { it.id == id }) { "That meal is no longer selected." }
        Ingredients.preview(selected.recipe, request.servings, emptyList())
        return saveShopping(state.shopping.copy(selections = state.shopping.selections.map { if (it.id == id) it.copy(servings = request.servings) else it }))
    }
    @Synchronized fun removeSelection(id: String, version: String): ShoppingList {
        checkShoppingVersion(version)
        return saveShopping(state.shopping.copy(selections = state.shopping.selections.filterNot { it.id == id }))
    }
    @Synchronized fun addShoppingItem(request: ManualShoppingRequest): ShoppingList {
        require(request.id.matches(Regex("[a-zA-Z0-9-]{1,80}"))) { "Invalid item ID." }
        require(request.name.isNotBlank() && request.name.length <= 250) { "Enter an item of up to 250 characters." }
        val id = "manual-${request.id}"
        state.shopping.manual.find { it.id == id }?.let {
            require(it.name == request.name.trim()) { "Item ID already used." }
            return shopping()
        }
        checkShoppingVersion(request.expectedVersion)
        require(state.shopping.manual.size < 200) { "The list already contains 200 manual items." }
        return saveShopping(state.shopping.copy(manual = state.shopping.manual + ShoppingItem(id, request.name.trim(), null, "", manual = true)))
    }
    @Synchronized fun checkShoppingItem(id: String, request: CheckShoppingRequest): ShoppingList {
        checkShoppingVersion(request.expectedVersion)
        val item = requireNotNull(shopping().items.find { it.id == id }) { "That shopping item changed. Refresh the list." }
        val next = if (item.manual) state.shopping.copy(manual = state.shopping.manual.map { if (it.id == id) it.copy(checked = request.checked) else it })
            else state.shopping.copy(checks = if (request.checked) state.shopping.checks + (id to Shopping.signature(item, state.shopping.selections)) else state.shopping.checks - id)
        return saveShopping(next)
    }
    @Synchronized fun removeShoppingItem(id: String, version: String): ShoppingList {
        checkShoppingVersion(version)
        return saveShopping(state.shopping.copy(manual = state.shopping.manual.filterNot { it.id == id }))
    }
    @Synchronized fun products(): List<Product> = state.products
    @Synchronized fun product(barcode: String): Product? = state.products.find { it.barcode == Stock.barcode(barcode) }
    @Synchronized fun rememberProduct(product: Product): Product {
        val barcode = Stock.barcode(product.barcode)
        require(product.name.isNotBlank() && product.name.length <= 250 && product.ingredient.isNotBlank() && product.ingredient.length <= 150) { "Enter the product and ingredient names." }
        require(product.unit in setOf("g", "ml", "piece")) { "Choose grams, millilitres or pieces." }
        val saved = product.copy(barcode = barcode, name = product.name.trim(), ingredient = Ingredients.canonical(product.ingredient, product.unit), packageAmount = Ingredients.display(Stock.positive(product.packageAmount)))
        require(state.pantry.none { it.barcode == barcode && (it.name != saved.ingredient || it.unit != saved.unit) }) { "Use or correct existing stock before changing this product's ingredient or unit." }
        persist(state.copy(products = state.products.filterNot { it.barcode == barcode } + saved))
        return saved
    }
    @Synchronized fun movements(): List<StockMovement> = state.movements
    private fun validOperation(id: String) { require(id.matches(Regex("[a-zA-Z0-9-]{1,80}"))) { "Invalid operation ID." } }
    @Synchronized fun stock(request: StockRequest): StockMovement {
        validOperation(request.id)
        val barcode = Stock.barcode(request.barcode)
        require(request.kind in setOf("add", "use")) { "Choose Add or Use." }
        state.movements.find { it.id == request.id }?.let {
            require(it.kind == request.kind && it.barcode == barcode && (request.amount == null || it.deltas.sumOf { d -> Ingredients.number(d.amount).abs() }.compareTo(Stock.positive(requireNotNull(request.amount))) == 0)) { "Operation ID already used." }
            return it
        }
        val product = requireNotNull(product(barcode)) { "Remember this product first." }
        val amount = request.amount?.let(Stock::positive) ?: if (request.kind == "add") Ingredients.number(product.packageAmount)
            else state.pantry.firstOrNull { it.barcode == barcode }?.amount?.let(Ingredients::number)?.min(Ingredients.number(product.packageAmount))
                ?: throw IllegalArgumentException("This product isn't in your pantry.")
        val deltas = if (request.kind == "add") {
            val lot = PantryItem(UUID.randomUUID().toString(), product.ingredient, "0", product.unit, barcode)
            listOf(StockDelta(lot, Ingredients.display(amount)))
        } else Stock.consume(state.pantry, product.ingredient, product.unit, amount, barcode)
        val movement = StockMovement(request.id, request.kind, java.time.Instant.now().toString(), deltas, barcode)
        persist(state.copy(pantry = Stock.apply(state.pantry, deltas), movements = state.movements + movement))
        return movement
    }
    @Synchronized fun undoStock(targetId: String, request: UndoStockRequest): StockMovement {
        validOperation(request.id)
        state.movements.find { it.id == request.id }?.let { require(it.undoOf == targetId) { "Operation ID already used." }; return it }
        val target = requireNotNull(state.movements.find { it.id == targetId }) { "That operation wasn't found." }
        require(target.kind != "undo") { "Use Add or Use to make a new correction." }
        require(state.movements.none { it.undoOf == targetId }) { "This operation was already undone." }
        require(state.movements.none { targetId in it.reconciledUseIds && state.movements.none { undo -> undo.undoOf == it.id } }) { "Undo the linked cooking log first." }
        val deltas = target.deltas.map { it.copy(amount = Ingredients.display(-Ingredients.number(it.amount))) }
        val movement = StockMovement(request.id, "undo", java.time.Instant.now().toString(), deltas, undoOf = targetId)
        persist(state.copy(pantry = Stock.apply(state.pantry, deltas), movements = state.movements + movement))
        return movement
    }
    @Synchronized fun cook(id: String, request: CookRequest): StockMovement {
        validOperation(request.id)
        state.movements.find { it.id == request.id }?.let {
            require(it.kind == "cook" && it.recipeId == id && it.servings == request.servings && it.reconciledUseIds == request.reconciledUseIds) { "Operation ID already used." }
            return it
        }
        val recipe = requireNotNull(recipe(id)) { "That recipe wasn't found." }
        if (recipe.revision != request.recipeRevision) throw RecipeConflict()
        val requirements = Ingredients.preview(recipe, request.servings, emptyList())
        require(requirements.ingredients.none { !it.optional && (it.needsReview || it.amount == null) }) { "Review all required ingredient amounts before logging this meal." }
        require(request.reconciledUseIds.distinct().size == request.reconciledUseIds.size) { "Choose each Use scan once." }
        val linked = request.reconciledUseIds.map { useId ->
            val movement = requireNotNull(state.movements.find { it.id == useId && it.kind == "use" }) { "Select a recorded Use scan." }
            require(state.movements.none { it.undoOf == useId || (useId in it.reconciledUseIds && state.movements.none { undo -> undo.undoOf == it.id }) }) { "That Use scan was already reconciled or undone." }
            movement
        }.flatMap { it.deltas }.groupBy { it.lot.name to it.lot.unit }.mapValues { (_, values) -> values.sumOf { -Ingredients.number(it.amount) } }.toMutableMap()
        val needed = requirements.ingredients.filterNot { it.optional }.groupBy { it.name to it.unit }.mapValues { (_, values) -> values.sumOf { Ingredients.number(requireNotNull(it.maxAmount ?: it.amount)) } }
        require(linked.all { (key, amount) -> key in needed && amount <= needed.getValue(key) }) { "Those Use scans exceed or don't match the meal's ingredients. Review your selection." }
        val deltas = needed.flatMap { (key, quantity) -> Stock.consume(state.pantry, key.first, key.second, quantity - (linked[key] ?: java.math.BigDecimal.ZERO)) }
        val movement = StockMovement(request.id, "cook", java.time.Instant.now().toString(), deltas, recipeId = id, servings = request.servings, reconciledUseIds = request.reconciledUseIds)
        persist(state.copy(pantry = Stock.apply(state.pantry, deltas), movements = state.movements + movement))
        return movement
    }
    @Synchronized fun fork(file: Path): Vault {
        require(!Files.exists(file)) { "The household vault already exists." }
        val target = Vault(file)
        target.persist(state)
        if (Files.isDirectory(mediaDirectory)) {
            Files.createDirectories(target.mediaDirectory)
            Files.list(mediaDirectory).use { paths -> paths.filter { Files.isRegularFile(it) }.forEach { Files.copy(it, target.mediaDirectory.resolve(it.fileName)) } }
        }
        return target
    }
    @Synchronized fun snapshot(): HouseholdSnapshot {
        val version = java.security.MessageDigest.getInstance("SHA-256").digest(json.encodeToString(state).toByteArray()).joinToString("") { "%02x".format(it) }
        return HouseholdSnapshot(version, state.recipes, state.pantry, shopping(), state.products, state.movements)
    }
    private fun persist(next: VaultState) {
        val validChecks = Shopping.list(next.shopping, next.pantry).items.filter { it.checked && !it.manual }.map { it.id }.toSet()
        val normalized = next.copy(shopping = next.shopping.copy(checks = next.shopping.checks.filterKeys { it in validChecks }))
        Files.createDirectories(file.toAbsolutePath().parent)
        val temporary = Files.createTempFile(file.toAbsolutePath().parent, "basil-", ".json")
        try {
            Files.writeString(temporary, json.encodeToString(normalized))
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            state = normalized
        } finally { Files.deleteIfExists(temporary) }
    }
}

class RecipeConflict : RuntimeException("This recipe changed. Reopen it before saving your edit.")
