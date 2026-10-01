package app.basil.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class RecipeLibraryTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun writeEditFavouriteAndFindRecipe() {
        rule.waitUntil(15_000) { rule.onAllNodes(hasText("Write a recipe") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Write a recipe").performScrollTo().performClick()
        rule.onNodeWithText("Recipe title").performScrollTo().performTextInput("Library test dinner")
        rule.onNodeWithText("Original servings (optional)").performScrollTo().performTextInput("2")
        rule.onNodeWithText("Ingredients — one per line").performScrollTo().performTextInput("2 eggs")
        rule.onNodeWithText("Method — one step per line").performScrollTo().performTextInput("Cook them.")
        rule.onNodeWithText("Save changes").performScrollTo().performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithText("☆ Favourite").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("☆ Favourite").performScrollTo().performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithText("★ Favourite").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Edit").performScrollTo().performClick()
        rule.onNodeWithText("Your notes").performScrollTo().performTextInput("A quick supper")
        rule.onNodeWithText("Save changes").performScrollTo().performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithText("A quick supper").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Back to recipes").performScrollTo().performClick()
        rule.onNodeWithText("Search recipes or ingredients").performScrollTo().performTextInput("quick supper")
        rule.onNodeWithText("Favourites").performScrollTo().performClick()
        rule.onNodeWithText("★ Library test dinner").assertExists()
    }
}
