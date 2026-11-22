package app.atracker

import android.graphics.Bitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
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
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("shots") ?: return
        dir.mkdirs()
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteractionsProvider.onRoot() =
        this.let { androidx.compose.ui.test.onRoot() }

    @Test
    fun manualTitleFlow() {
        rule.waitForIdle()
        rule.onNodeWithText("Welcome to ATracker").assertIsDisplayed()
        shot("01-home-empty")

        rule.onAllNodesWithText("Discover").onFirst().performClick()
        // Wait until the initial online browse finished (success or error), then the manual button shows.
        rule.waitUntil(timeoutMillis = 60_000) {
            rule.onAllNodesWithText("Add a title manually").fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodes(hasScrollAction()).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Add a title manually"))
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

        androidx.test.espresso.Espresso.pressBack()
        rule.onAllNodesWithText("My Library").onFirst().performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText("Smoke Test Novel").fetchSemanticsNodes().isNotEmpty()
        }
        shot("06-library")
    }
}
