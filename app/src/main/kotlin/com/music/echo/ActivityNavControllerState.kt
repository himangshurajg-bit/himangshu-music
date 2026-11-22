package echo.music.iad1tya

import androidx.navigation.NavHostController

/** Keeps Activity callbacks pointed at the NavHostController owned by the current composition. */
internal class ActivityNavControllerState {
  private var controller: NavHostController? = null

  fun attach(navController: NavHostController) {
    controller = navController
  }

  fun detach(navController: NavHostController) {
    if (controller === navController) controller = null
  }

  fun currentOrNull(): NavHostController? = controller

  /** Returns false while the Compose NavHost is not yet attached instead of dereferencing lateinit. */
  fun navigateToUpdate(): Boolean {
    val activeController = controller ?: return false
    activeController.navigate("update") { launchSingleTop = true }
    return true
  }
}
