package app.basil.android

import app.basil.contracts.*
import app.basil.mobile.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class HouseholdTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun inviteSecondDeviceAndSeeItsRecipe() {
        rule.waitUntil(15_000) { rule.onAllNodes(hasText("Household") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Household").performClick()
        rule.onNodeWithText("Show household members").performScrollTo().performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithText("Invite a household member").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Invite a household member").performScrollTo().performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithText("Invitation code").fetchSemanticsNodes().isNotEmpty() }
        val code = rule.onNodeWithText("Invitation code").fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        runBlocking {
            val secondDevice = BasilApi(BuildConfig.API_URL)
            try {
                secondDevice.refresh(); secondDevice.join(code)
                secondDevice.createRecipe(RecipeEditRequest("Shared household supper", 2, listOf("2 eggs"), listOf("Cook.")))
            } finally { secondDevice.close() }
        }
        rule.onNodeWithText("Sync household").performScrollTo().performClick()
        rule.waitUntil(15_000) { rule.onAllNodes(hasText("Recipes") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Recipes").performScrollTo().performClick()
        rule.onNodeWithText("Shared household supper").performScrollTo().assertExists()
    }
}
