package app.basil.mobile

interface AppleDraftProvider {
    fun availability(): String
    fun generate(prompt: String, callback: (String?, String?) -> Unit)
    fun cancel()
}
expect fun appleRecipeAvailability(): String
expect suspend fun generateAppleRecipe(prompt: String): String
