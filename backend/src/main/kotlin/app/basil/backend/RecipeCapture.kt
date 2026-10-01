package app.basil.backend

import app.basil.contracts.*
import org.jsoup.Jsoup
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

fun interface ImageFetcher { fun fetch(url: String): Download }

class RecipeImages(private val directory: Path, private val fetcher: ImageFetcher = ImageFetcher {
    PublicPageFetcher().download(it, 5_000_000, setOf("image/jpeg", "image/png", "image/webp")).second
}) {
    fun preserve(url: String): String {
        PublicPageFetcher.validateUrl(url)
        val image = fetcher.fetch(url)
        require(image.bytes.size in 12..5_000_000) { "The recipe photo is too large or empty." }
        val mime = image.contentType.substringBefore(';').trim().lowercase()
        val bytes = image.bytes
        val valid = when (mime) {
            "image/jpeg" -> bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte()
            "image/png" -> bytes.take(8).toByteArray().contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 13, 10, 26, 10))
            "image/webp" -> String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP"
            else -> false
        }
        require(valid) { "This recipe photo has an unsupported format." }
        val extension = when (mime) { "image/jpeg" -> "jpg"; "image/png" -> "png"; else -> "webp" }
        val name = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } + ".$extension"
        Files.createDirectories(directory)
        val temp = Files.createTempFile(directory, "photo-", ".tmp")
        try { Files.write(temp, bytes); Files.move(temp, directory.resolve(name), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
        finally { Files.deleteIfExists(temp) }
        return "/v1/media/$name"
    }
    fun file(name: String): Path? {
        if (!Regex("[a-f0-9]{64}\\.(jpg|png|webp)").matches(name)) return null
        return directory.resolve(name).takeIf { Files.isRegularFile(it) }
    }
}

class RecipeCapture(
    private val vault: Vault,
    private val images: RecipeImages,
    private val extractor: RecipeExtractor? = null,
) {
    fun save(request: CaptureRequest): Recipe {
        PublicPageFetcher.validateUrl(request.url)
        require(request.html.toByteArray(Charsets.UTF_8).size in 1..PublicPageFetcher.MAX_BYTES) { "This page is empty or too large to capture." }
        vault.recipes().find { it.sourceUrl == request.url }?.let { return it }
        val importer = RecipeImporter()
        val recipe = try { importer.parse(request.html, request.url, structuredOnly = true) }
        catch (_: NoStructuredRecipe) {
            if (extractor == null) importer.parse(request.html, request.url)
            else try { extractor.extract(request.url, extractionText(request.html)) }
            catch (_: Exception) {
                val fallback = runCatching { importer.parse(request.html, request.url) }.getOrNull()
                    ?: throw IllegalArgumentException("Recipe extraction is unavailable. Try again or use a page with structured recipe data.")
                fallback.copy(warnings = fallback.warnings + "Gemini was unavailable. Check this text import.")
            }
        }
        val document = Jsoup.parse(request.html, request.url)
        val imageCandidate = document.selectFirst("meta[property=og:image], meta[name=twitter:image]")?.attr("content")
            ?: document.selectFirst("article img[src], main img[src], [itemprop=image][src]")?.attr("src")
        val pageImage = imageCandidate
            ?.takeIf { it.isNotBlank() }?.let { runCatching { java.net.URI(request.url).resolve(it).toString() }.getOrNull() }
        val illustrated = if (recipe.originalImageUrl == null) recipe.copy(originalImageUrl = pageImage) else recipe
        val withImage = illustrated.originalImageUrl?.let { url ->
            try { illustrated.copy(imagePath = images.preserve(url)) }
            catch (_: Exception) { illustrated.copy(warnings = illustrated.warnings + "The recipe was saved, but its photo could not be copied.") }
        } ?: illustrated
        return vault.save(withImage)
    }
    companion object {
        fun extractionText(html: String): String {
            val document = Jsoup.parse(html)
            val title = document.selectFirst("h1")?.text() ?: document.title()
            document.select("script, style, nav, footer, header, aside, form, input, textarea, button, noscript, iframe").remove()
            val content = document.selectFirst(".entry-content, article, main") ?: document.body()
            return (title + "\n" + content.wholeText()).trim().take(60_000)
        }
    }
}
