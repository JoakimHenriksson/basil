package app.basil.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class SuggestionsTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun findAReadyMealFromTrackedPantryWithoutAI() {
        rule.waitUntil(15_000) { rule.onAllNodes(hasText("Write a recipe") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Write a recipe").performScrollTo().performClick()
        rule.onNodeWithText("Recipe title").performScrollTo().performTextInput("Ready egg supper")
        rule.onNodeWithText("Original servings (optional)").performScrollTo().performTextInput("2")
        rule.onNodeWithText("Ingredients — one per line").performScrollTo().performTextInput("2 eggs")
        rule.onNodeWithText("Method — one step per line").performScrollTo().performTextInput("Cook.")
        rule.onNodeWithText("Save changes").performScrollTo().performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithText("Back to recipes").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Pantry").performScrollTo().performClick()
        rule.onNodeWithText("Ingredient").performScrollTo().performTextInput("eggs")
        rule.onNodeWithText("Amount").performScrollTo().performTextInput("2")
        rule.onNodeWithText("pieces").performScrollTo().performClick()
        rule.onNodeWithText("Add to pantry").performScrollTo().performClick()
        rule.waitUntil(15_000) { rule.onAllNodes(hasText("Ideas") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Ideas").performScrollTo().performClick()
        rule.onNodeWithText("Find ready and close recipes").performScrollTo().performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithText("Ready to cook").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Ready egg supper").performScrollTo().performClick()
        rule.onNodeWithText("2 servings").performScrollTo().assertExists()
    }
}
