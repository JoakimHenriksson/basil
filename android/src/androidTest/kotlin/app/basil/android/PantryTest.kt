package app.basil.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

/** The backend fixture remembers the tomato tin; camera optical QA is separate. */
class PantryTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun addUsePartialAmountAndUndo() {
        rule.waitUntil(15_000) { rule.onAllNodes(hasText("Pantry") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Pantry").performClick()
        rule.onNodeWithText("Barcode (or type it)").performScrollTo().performTextInput("4006381333931")
        rule.onNodeWithText("Find barcode").performScrollTo().performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithText("425 g").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Use").performScrollTo().performClick()
        rule.onNodeWithText("Partial amount (optional)").performScrollTo().performTextInput("125")
        rule.onNodeWithText("Find barcode").performScrollTo().performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithText("300 g").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("Undo")[0].performScrollTo().performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithText("425 g").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Partial amount (optional)").performScrollTo().performTextClearance()
        rule.onNodeWithText("Find barcode").performScrollTo().performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithText("425 g").fetchSemanticsNodes().isEmpty() }
        rule.onAllNodesWithText("Undo")[0].performScrollTo().performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithText("425 g").fetchSemanticsNodes().isNotEmpty() }
    }
}
