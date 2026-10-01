package app.basil.backend

import app.basil.contracts.ProductDraft
import kotlinx.serialization.json.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

fun interface ProductLookup { fun lookup(barcode: String): ProductDraft }

class OpenFoodFactsLookup : ProductLookup {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build()
    override fun lookup(barcode: String): ProductDraft {
        Stock.barcode(barcode)
        val request = HttpRequest.newBuilder(URI("https://world.openfoodfacts.org/api/v2/product/$barcode.json?fields=product_name,product_quantity,product_quantity_unit,quantity"))
            .header("User-Agent", "Basil/0.1 (local pantry prototype)").timeout(Duration.ofSeconds(12)).GET().build()
        return runCatching {
            val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
            val body = response.body().use { it.readNBytes(256 * 1024 + 1) }
            require(response.statusCode() == 200 && body.size <= 256 * 1024)
            parse(barcode, body.decodeToString())
        }.getOrElse { ProductDraft(barcode, warning = "Product lookup is unavailable. Enter the package details once to remember them.") }
    }
    companion object {
        fun parse(barcode: String, body: String): ProductDraft {
            val root = Json.parseToJsonElement(body).jsonObject
            val product = root["product"] as? JsonObject ?: return ProductDraft(barcode, warning = "Product not found. Enter its details once.")
            fun text(key: String) = (product[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val quantity = text("product_quantity")
            val unit = text("product_quantity_unit").lowercase()
            val multiplier = when (unit) { "g", "ml" -> "1"; "kg", "l" -> "1000"; else -> null }
            val amount = if (multiplier != null) runCatching { Ingredients.display(Stock.positive(quantity) * multiplier.toBigDecimal()) }.getOrNull() else null
            return ProductDraft(barcode, text("product_name"), amount, if (unit == "ml" || unit == "l") "ml" else "g", "Open Food Facts", "Check the ingredient and package amount before remembering this product.")
        }
    }
}
