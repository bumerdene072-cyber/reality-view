package studio.rainy.realityview

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test

class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun opensCameraAndSurroundScreens() {
        compose.onNodeWithText(text(R.string.open_camera)).performClick()
        compose.onNodeWithText("RTSP").assertIsDisplayed()
        open(R.string.reality)
        compose.onNodeWithText(text(R.string.ai_description)).assertIsDisplayed()
        open(R.string.surround)
        compose.onNodeWithText(text(R.string.surround_description)).assertIsDisplayed()
    }

    @Test fun opensSettingsAndCompatibility() {
        open(R.string.settings)
        compose.onNodeWithText(text(R.string.language)).assertIsDisplayed()
        open(R.string.compatibility)
        compose.onNodeWithText(text(R.string.scan_usb)).assertIsDisplayed()
    }

    private fun open(label: Int) {
        compose.onNode(hasText(text(label)) and hasClickAction())
            .performScrollTo()
            .performClick()
    }

    private fun text(label: Int): String = compose.activity.getString(label)
}
