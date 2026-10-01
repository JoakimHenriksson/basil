package app.basil.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class ShoppingTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun chooseMealChangeServingsAndAddAnExtra() {
        rule.waitUntil(15_000) { rule.onAllNodes(hasText("Write a recipe") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Write a recipe").performScrollTo().performClick()
        rule.onNodeWithText("Recipe title").performScrollTo().performTextInput("Shopping test dinner")
        rule.onNodeWithText("Original servings (optional)").performScrollTo().performTextInput("2")
        rule.onNodeWithText("Ingredients — one per line").performScrollTo().performTextInput("200 g rice")
        rule.onNodeWithText("Method — one step per line").performScrollTo().performTextInput("Cook rice.")
        rule.onNodeWithText("Save changes").performScrollTo().performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithText("Add to shopping").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Add to shopping").performScrollTo().performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithText("What to buy").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("200 g").performScrollTo().assertExists()
        rule.onNodeWithText("+").performScrollTo().performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithText("300 g").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Extra shopping item").performScrollTo().performTextInput("Dish soap")
        rule.onNodeWithText("Add shopping item").performScrollTo().performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithText("Dish soap").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Remove meal").performScrollTo().performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithText("Remove meal").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText("Dish soap").performScrollTo().assertExists()
        rule.onAllNodes(isToggleable()).filterToOne(isOff()).performScrollTo().performClick()
        rule.waitUntil(15_000) { rule.onAllNodes(isToggleable() and isOn()).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Refresh shopping").performScrollTo().performClick()
        rule.waitUntil(15_000) { rule.onAllNodes(isToggleable() and isOn()).fetchSemanticsNodes().isNotEmpty() }
    }
}
