package app.basil.backend

import app.basil.contracts.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path

fun main() {
    val port = System.getenv("BASIL_PORT")?.toIntOrNull() ?: 8787
    val host = System.getenv("BASIL_HOST") ?: "127.0.0.1"
    val vault = Vault(Path.of(System.getenv("BASIL_DATA") ?: "data/vault.json"))
    embeddedServer(Netty, port = port, host = host) { basil(vault) }.start(wait = true)
}

fun Application.basil(
    vault: Vault,
    fetcher: PageFetcher = PublicPageFetcher(),
    images: RecipeImages = RecipeImages(vault.mediaDirectory),
    extractor: RecipeExtractor? = GeminiRecipeExtractor.configured(),
    products: ProductLookup = OpenFoodFactsLookup(),
) {
    install(ContentNegotiation) { json() }
    install(StatusPages) {
        exception<ShoppingConflict> { call, error -> call.respond(HttpStatusCode.Conflict, ApiError("shopping_changed", error.message!!)) }
        exception<RecipeConflict> { call, error -> call.respond(HttpStatusCode.Conflict, ApiError("recipe_changed", error.message!!)) }
        exception<BadRequestException> { call, _ -> call.respond(HttpStatusCode.BadRequest, ApiError("invalid_request", "Please check the recipe capture request.")) }
        exception<IllegalArgumentException> { call, error -> call.respond(HttpStatusCode.BadRequest, ApiError("invalid_request", error.message ?: "Please check the request.")) }
        exception<Throwable> { call, error ->
            this@basil.environment.log.error("Request failed: {}", error.javaClass.simpleName)
            call.respond(HttpStatusCode.InternalServerError, ApiError("request_failed", "The operation failed. Please try again."))
        }
    }
    val capture = RecipeCapture(vault, images, extractor)
    routing {
        get("/health") { call.respond(mapOf("status" to "ok")) }
        route("/v1") {
            get("/recipes") { call.respond(vault.recipes()) }
            post("/recipes") { call.respond(HttpStatusCode.Created, withContext(Dispatchers.IO) { vault.create(call.receive<RecipeEditRequest>()) }) }
            get("/recipes/export") {
                call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=basil-recipes.zip")
                call.respondBytes(withContext(Dispatchers.IO) { RecipeArchive.export(vault, images) }, ContentType.Application.Zip)
            }
            get("/recipes/{id}/history") { call.respond(vault.history(call.parameters["id"].orEmpty())) }
            put("/recipes/{id}") {
                val request = call.receive<RecipeEditRequest>()
                val recipe = withContext(Dispatchers.IO) { vault.edit(call.parameters["id"].orEmpty(), request) }
                if (recipe == null) call.respond(HttpStatusCode.NotFound) else call.respond(recipe)
            }
            put("/recipes/{id}/favourite") {
                val request = call.receive<FavouriteRequest>()
                val recipe = withContext(Dispatchers.IO) { vault.favourite(call.parameters["id"].orEmpty(), request.favourite) }
                if (recipe == null) call.respond(HttpStatusCode.NotFound) else call.respond(recipe)
            }
            delete("/recipes/{id}") {
                if (withContext(Dispatchers.IO) { vault.deleteRecipe(call.parameters["id"].orEmpty()) }) call.respond(HttpStatusCode.NoContent)
                else call.respond(HttpStatusCode.NotFound)
            }
            post("/recipes/import") {
                val request = call.receive<ImportRequest>()
                val recipe = withContext(Dispatchers.IO) { val (url, html) = fetcher.fetch(request.url.trim()); capture.save(CaptureRequest(url, html)) }
                call.respond(HttpStatusCode.Created, recipe)
            }
            post("/recipes/capture") {
                val request = call.receive<CaptureRequest>()
                call.respond(HttpStatusCode.Created, withContext(Dispatchers.IO) { capture.save(request) })
            }
            get("/media/{name}") {
                val file = images.file(call.parameters["name"].orEmpty())
                if (file == null) call.respond(HttpStatusCode.NotFound)
                else call.respondFile(file.toFile())
            }
            post("/recipes/{id}/preview") {
                val recipe = vault.recipe(call.parameters["id"].orEmpty())
                if (recipe == null) call.respond(HttpStatusCode.NotFound, ApiError("recipe_not_found", "That recipe is not in the vault."))
                else call.respond(Ingredients.preview(recipe, call.receive<PreviewRequest>().servings, vault.pantry()))
            }
            put("/recipes/{id}/yield") {
                val request = call.receive<YieldRequest>()
                val updated = withContext(Dispatchers.IO) { vault.setYield(call.parameters["id"].orEmpty(), request.servings) }
                if (updated == null) call.respond(HttpStatusCode.NotFound, ApiError("recipe_not_found", "That recipe is not in the vault."))
                else call.respond(updated)
            }
            get("/shopping") { call.respond(vault.shopping()) }
            post("/shopping/selections") { val request = call.receive<SelectMealRequest>(); call.respond(withContext(Dispatchers.IO) { vault.selectMeal(request) }) }
            put("/shopping/selections/{id}") { val request = call.receive<SelectionServingsRequest>(); call.respond(withContext(Dispatchers.IO) { vault.changeSelection(call.parameters["id"].orEmpty(), request) }) }
            delete("/shopping/selections/{id}") { val request = call.receive<ShoppingVersionRequest>(); call.respond(withContext(Dispatchers.IO) { vault.removeSelection(call.parameters["id"].orEmpty(), request.expectedVersion) }) }
            post("/shopping/items") { val request = call.receive<ManualShoppingRequest>(); call.respond(withContext(Dispatchers.IO) { vault.addShoppingItem(request) }) }
            put("/shopping/items/{id}") { val request = call.receive<CheckShoppingRequest>(); call.respond(withContext(Dispatchers.IO) { vault.checkShoppingItem(call.parameters["id"].orEmpty(), request) }) }
            delete("/shopping/items/{id}") { val request = call.receive<ShoppingVersionRequest>(); call.respond(withContext(Dispatchers.IO) { vault.removeShoppingItem(call.parameters["id"].orEmpty(), request.expectedVersion) }) }
            get("/products") { call.respond(vault.products()) }
            get("/products/{barcode}") {
                val code = call.parameters["barcode"].orEmpty()
                val saved = vault.product(code)
                if (saved != null) call.respond(ProductDraft(saved.barcode, saved.name, saved.packageAmount, saved.unit, "Remembered product"))
                else call.respond(withContext(Dispatchers.IO) { products.lookup(code) })
            }
            put("/products/{barcode}") { val request = call.receive<Product>(); require(Stock.barcode(request.barcode) == Stock.barcode(call.parameters["barcode"].orEmpty())) { "Product barcode mismatch." }; call.respond(withContext(Dispatchers.IO) { vault.rememberProduct(request) }) }
            get("/stock/movements") { call.respond(vault.movements()) }
            post("/stock") { val request = call.receive<StockRequest>(); call.respond(withContext(Dispatchers.IO) { vault.stock(request) }) }
            post("/stock/{id}/undo") { val request = call.receive<UndoStockRequest>(); call.respond(withContext(Dispatchers.IO) { vault.undoStock(call.parameters["id"].orEmpty(), request) }) }
            post("/recipes/{id}/cook") { val request = call.receive<CookRequest>(); call.respond(withContext(Dispatchers.IO) { vault.cook(call.parameters["id"].orEmpty(), request) }) }
            get("/pantry") { call.respond(vault.pantry()) }
            post("/pantry") { val request = call.receive<PantryRequest>(); call.respond(HttpStatusCode.Created, withContext(Dispatchers.IO) { vault.add(request) }) }
            delete("/pantry/{id}") {
                if (withContext(Dispatchers.IO) { vault.remove(call.parameters["id"].orEmpty()) }) call.respond(HttpStatusCode.NoContent)
                else call.respond(HttpStatusCode.NotFound, ApiError("item_not_found", "That item is no longer in the pantry."))
            }
        }
    }
}
