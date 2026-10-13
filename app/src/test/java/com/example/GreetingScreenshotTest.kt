package com.example

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.material3.Text
import androidx.test.core.app.ApplicationProvider
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MainViewModel
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    composeTestRule.setContent { MyApplicationTheme { Text("Selamat datang di MyFeeNotes") } }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }

  @Test
  fun full_app_screen_render_test() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = MainViewModel(app)
    composeTestRule.setContent {
      MyApplicationTheme {
        MainAppScreen(viewModel = viewModel, onShare = { _, _ -> })
      }
    }
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/full_app_dashboard.png")
  }
}
