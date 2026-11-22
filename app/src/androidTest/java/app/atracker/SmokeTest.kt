package app.atracker

import android.graphics.Bitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

/** Drives the real app on an emulator: manual entry -> library -> progress -> library list. */
@OptIn(ExperimentalTestApi::class)
class SmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun shot(name: String) {
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.filesDir.resolve("shots")
        dir.mkdirs()
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
    }

    @Test
    fun manualTitleFlow() {
        try {
            runFlow()
        } catch (t: Throwable) {
            runCatching { shot("99-failure") }
            runCatching { android.util.Log.e("SMOKE", "SCREEN TREE:\n" + rule.onRoot().printToString(maxDepth = 12)) }
            throw t
        }
    }

    private fun runFlow() {
        rule.waitForIdle()
        rule.onNodeWithText("Welcome to ATracker").assertIsDisplayed()
        shot("01-home-empty")

        rule.onAllNodesWithText("Discover").onFirst().performClick()
        // The manual button only appears once the initial online browse has finished (success or error).
        rule.waitUntil(timeoutMillis = 90_000) {
            try {
                rule.onNodeWithTag("discover-list").performScrollToNode(hasText("Add a title manually"))
                true
            } catch (e: AssertionError) {
                false
            }
        }
        shot("02-discover")
        rule.onNodeWithText("Add a title manually").performClick()

        rule.onNodeWithText("Title *").performTextInput("Smoke Test Novel")
        shot("03-manual-form")
        rule.onNodeWithText("Save title").performScrollTo().performClick()

        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText("Smoke Test Novel").fetchSemanticsNodes().isNotEmpty() &&
                rule.onAllNodesWithText("Plan to Read").fetchSemanticsNodes().isNotEmpty()
        }
        shot("04-detail-added")

        rule.onNode(hasText("+1", substring = true)).performScrollTo().performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodes(hasText("1 / ?", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        shot("05-detail-progress")

        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.onAllNodesWithText("My Library").onFirst().performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText("Smoke Test Novel").fetchSemanticsNodes().isNotEmpty()
        }
        shot("06-library")
    }
}
