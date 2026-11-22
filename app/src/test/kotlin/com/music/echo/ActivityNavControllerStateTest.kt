package echo.music.iad1tya

import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ActivityNavControllerStateTest {
  @Test
  fun updateNavigationIsSafeBeforeAttachAndWorksAfterAttach() {
    val state = ActivityNavControllerState()

    assertNull(state.currentOrNull())
    assertFalse(state.navigateToUpdate())

    val controller = createTestNavController()
    state.attach(controller)

    assertTrue(state.navigateToUpdate())
    assertEquals("update", controller.currentDestination?.route)

    state.detach(controller)
    assertNull(state.currentOrNull())
    assertFalse(state.navigateToUpdate())
  }

  @Test
  fun disposingOldCompositionDoesNotClearNewController() {
    val state = ActivityNavControllerState()
    val oldController = createTestNavController()
    val activeController = createTestNavController()

    state.attach(oldController)
    state.attach(activeController)
    state.detach(oldController)

    assertTrue(state.navigateToUpdate())
    assertEquals("update", activeController.currentDestination?.route)
    assertEquals("home", oldController.currentDestination?.route)
  }

  private fun createTestNavController(): NavHostController {
    val context = RuntimeEnvironment.getApplication()
    return NavHostController(context).apply {
      navigatorProvider.addNavigator(ComposeNavigator())
      graph =
        createGraph(startDestination = "home") {
          composable("home") {}
          composable("update") {}
        }
    }
  }
}
