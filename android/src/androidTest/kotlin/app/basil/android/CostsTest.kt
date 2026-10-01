package app.basil.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class CostsTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun reviewReceiptSetBudgetAndCorrectSpending() {
        rule.waitUntil(15_000) { rule.onAllNodes(hasText("Costs") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Costs").performClick()
        rule.waitUntil(15_000) { rule.onAllNodes(hasText("Save budget") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Budget month (YYYY-MM)").performScrollTo().performTextReplacement("2026-10")
        rule.onNodeWithText("Monthly grocery limit (optional)").performScrollTo().performTextInput("100")
        rule.onNodeWithText("Save budget").performScrollTo().performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithText("Remaining: 100.00 SEK").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Receipt text").performScrollTo().performTextInput("ICA\n2026-10-02\nTomato tin 20,00\nTOTALT 20,00")
        rule.onNodeWithText("Extract receipt").performScrollTo().performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithText("Review receipt").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Ingredient and stock details").performScrollTo().performClick()
        rule.onNodeWithText("Ingredient (optional)").performScrollTo().performTextInput("crushed tomatoes")
        rule.onNodeWithText("Purchased quantity, all packages").performScrollTo().performTextInput("425")
        rule.onNodeWithText("Record receipt").performScrollTo().performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithText("Remaining: 80.00 SEK").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Correct purchase").performScrollTo().performClick()
        rule.onNodeWithText("Printed receipt total").performScrollTo().performTextReplacement("15")
        rule.onNodeWithText("Item total price").performScrollTo().performTextReplacement("15")
        rule.onNodeWithText("Save receipt correction").performScrollTo().performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithText("Remaining: 85.00 SEK").fetchSemanticsNodes().isNotEmpty() }
    }
}
