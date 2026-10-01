package app.basil.android

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

/** Uses the local backend; fixture is injected into the real WebView, with no TLS bypass. */
class RecipeBrowserTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun renderedRecipeSavesAndKeepsBrowserOpen() {
        rule.waitUntil(15_000) { rule.onAllNodes(hasText("Browse recipes") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Browse recipes").performClick()
        rule.waitForIdle()
        fun findBrowser(view: View): WebView? {
            if (view is WebView) return view
            if (view is ViewGroup) for (index in 0 until view.childCount) findBrowser(view.getChildAt(index))?.let { return it }
            return null
        }
        var browserView: WebView? = null
        rule.waitUntil(15_000) {
            rule.runOnUiThread { browserView = findBrowser(rule.activity.window.decorView) }
            browserView != null
        }
        rule.waitUntil(30_000) {
            var finished = false
            rule.runOnUiThread { finished = browserView?.progress == 100 }
            finished
        }
        rule.runOnUiThread {
            val browser = requireNotNull(browserView)
            browser.stopLoading()
            browser.loadDataWithBaseURL("https://example.com/rendered-recipe", """
                <html><head><title>Rendered dinner</title></head><body><h1>Rendered dinner</h1>
                <p>Two eggs. Cook them.</p><script>
                const recipe = document.createElement('script'); recipe.type = 'application/ld+json';
                recipe.textContent = JSON.stringify({'@type':'Recipe', name:'Rendered dinner', recipeYield:'2',
                    recipeIngredient:['2 eggs'], recipeInstructions:['Cook them.']});
                document.head.appendChild(recipe);
                </script></body></html>
            """.trimIndent(), "text/html", "UTF-8", "https://example.com/rendered-recipe")
        }
        rule.waitUntil(15_000) {
            var loaded = false
            rule.runOnUiThread { loaded = browserView?.title == "Rendered dinner" && browserView?.progress == 100 }
            loaded
        }
        rule.waitUntil(15_000) { rule.onAllNodes(hasText("Save recipe") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        saveScreenshot("browser-fixture.png")
        rule.onNodeWithText("Save recipe").performClick()
        try {
            rule.waitUntil(15_000) { rule.onAllNodesWithText("Saved Rendered dinner").fetchSemanticsNodes().isNotEmpty() }
        } catch (failure: Throwable) {
            android.util.Log.e("BasilCaptureTest", rule.onRoot().printToString())
            throw failure
        }
        rule.onNodeWithText("Search or website").assertExists()
        rule.onNodeWithText("View saved").performClick()
        rule.onNodeWithText("Rendered dinner").assertExists()
        rule.onNodeWithText("Open original").assertExists()
        rule.onNodeWithText("Saved from https://example.com/rendered-recipe").assertExists()
        saveScreenshot("browser-saved.png")
    }
    private fun saveScreenshot(name: String) {
        val bitmap = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        java.io.File(rule.activity.filesDir, name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
