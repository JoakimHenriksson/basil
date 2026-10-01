package app.basil.backend

import app.basil.contracts.RecipeExport
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object RecipeArchive {
    private val json = Json { prettyPrint = true; encodeDefaults = true }
    fun export(vault: Vault, images: RecipeImages): ByteArray {
        val recipes = vault.recipes()
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("recipes.json"))
            zip.write(json.encodeToString(RecipeExport(recipes = recipes)).toByteArray())
            zip.closeEntry()
            recipes.mapNotNull { it.imagePath?.substringAfterLast('/') }.distinct().forEach { name ->
                images.file(name)?.let { photo ->
                    zip.putNextEntry(ZipEntry("media/$name")); Files.copy(photo, zip); zip.closeEntry()
                }
            }
        }
        return output.toByteArray()
    }
}
