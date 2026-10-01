package app.basil.backend

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.TimeUnit

fun interface PageFetcher { fun fetch(url: String): Pair<String, String> }

data class Download(val bytes: ByteArray, val contentType: String)

class PublicPageFetcher : PageFetcher {
    override fun fetch(url: String): Pair<String, String> {
        val (resolved, download) = download(url, MAX_BYTES, setOf("text/html", "application/xhtml+xml"))
        val charset = runCatching { java.nio.charset.Charset.forName(download.contentType.substringAfter("charset=", "UTF-8").substringBefore(';').trim()) }.getOrDefault(Charsets.UTF_8)
        return resolved to download.bytes.toString(charset)
    }
    private val client = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .callTimeout(20, TimeUnit.SECONDS)
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = InetAddress.getAllByName(hostname).toList().also { addresses ->
                require(addresses.isNotEmpty() && addresses.all(::isPublicAddress)) { "Only public recipe sites can be imported." }
            }
        })
        .build()
    fun download(url: String, maxBytes: Int, allowedTypes: Set<String>): Pair<String, Download> {
        var current = url
        repeat(5) {
            validateUrl(current)
            client.newCall(Request.Builder().url(current).header("User-Agent", "Mozilla/5.0 BasilRecipeImporter/0.1").build()).execute().use { response ->
                if (response.code in 300..399) {
                    val location = requireNotNull(response.header("Location")) { "The recipe site returned an invalid redirect." }
                    current = URI(current).resolve(location).toString()
                } else {
                    require(response.isSuccessful) { "The recipe site returned HTTP ${response.code}." }
                    val type = response.header("Content-Type").orEmpty()
                    require(type.substringBefore(';').trim().lowercase() in allowedTypes) { "This resource has an unsupported content type." }
                    val body = requireNotNull(response.body)
                    require(body.contentLength() <= maxBytes) { "This page is too large to import." }
                    val bytes = body.byteStream().readNBytes(maxBytes + 1)
                    require(bytes.size <= maxBytes) { "This page is too large to import." }
                    return current to Download(bytes, type)
                }
            }
        }
        throw IllegalArgumentException("This link redirects too many times.")
    }
    companion object {
        const val MAX_BYTES = 2_000_000
        fun validateUrl(url: String) {
            require(url.length <= 2048) { "The recipe link is too long." }
            val uri = try { URI(url) } catch (_: java.net.URISyntaxException) { throw IllegalArgumentException("Enter a valid HTTPS recipe link.") }
            require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.port in listOf(-1, 443)) { "Use an HTTPS recipe link without credentials or a custom port." }
        }
        fun isPublicAddress(address: InetAddress): Boolean {
            if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress) return false
            val bytes = address.address.map { it.toInt() and 255 }
            if (bytes.size == 4) {
                if (bytes[0] == 0 || bytes[0] >= 224 || bytes[0] == 100 && bytes[1] in 64..127) return false
                if (bytes[0] == 192 && bytes[1] == 0 || bytes[0] == 198 && bytes[1] in 18..19) return false
                if (bytes.take(3) in listOf(listOf(192, 0, 2), listOf(198, 51, 100), listOf(203, 0, 113))) return false
            } else if (bytes.size == 16) {
                if (bytes[0] and 0xe0 != 0x20) return false
                if (bytes.take(4) == listOf(0x20, 0x01, 0x0d, 0xb8)) return false
            }
            return true
        }
    }
}
