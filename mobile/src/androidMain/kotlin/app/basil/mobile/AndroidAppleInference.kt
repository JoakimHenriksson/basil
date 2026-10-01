package app.basil.mobile
actual fun appleRecipeAvailability(): String = "Apple Intelligence needs a supported iPhone. Gemini and saved recipes are available here."
actual suspend fun generateAppleRecipe(prompt: String): String = error(appleRecipeAvailability())
