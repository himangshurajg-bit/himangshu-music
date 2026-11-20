@file:OptIn(ExperimentalMaterial3Api::class)

package echo.music.iad1tya.ui.screens.settings

import android.app.Activity
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.navigation.NavController
import echo.music.iad1tya.LocalPlayerAwareWindowInsets
import echo.music.iad1tya.R
import echo.music.iad1tya.constants.AppIconTypeKey
import echo.music.iad1tya.constants.CustomShortcutIconPathKey
import echo.music.iad1tya.constants.CustomShortcutNameKey
import echo.music.iad1tya.utils.AppIconType
import echo.music.iad1tya.utils.CustomHomeShortcut
import echo.music.iad1tya.utils.IconUtils
import echo.music.iad1tya.utils.rememberEnumPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AppIconSettingsScreen(
  navController: NavController,
  activity: Activity,
  snackbarHostState: SnackbarHostState
) {
  val (appIconType, onAppIconTypeChange) =
    rememberEnumPreference(AppIconTypeKey, defaultValue = AppIconType.DEFAULT)
  val coroutineScope = rememberCoroutineScope()
  val context = LocalContext.current
  val (shortcutName, onShortcutNameChange) =
    echo.music.iad1tya.utils.rememberPreference(CustomShortcutNameKey, "RajTune")
  val (shortcutIconPath, onShortcutIconPathChange) =
    echo.music.iad1tya.utils.rememberPreference(CustomShortcutIconPathKey, "")

  val iconPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        coroutineScope.launch {
          val result =
            runCatching { withContext(Dispatchers.IO) { CustomHomeShortcut.saveIcon(context, uri) } }
          result.onSuccess { file ->
            onShortcutIconPathChange(file.absolutePath)
            snackbarHostState.showSnackbar("Custom shortcut icon selected")
          }.onFailure { error ->
            snackbarHostState.showSnackbar(error.message ?: "Couldn’t use that image")
          }
        }
      }
    }

  fun addCustomShortcut() {
    val customBitmap = CustomHomeShortcut.loadIcon(shortcutIconPath)
    val iconBitmap =
      customBitmap
        ?: ContextCompat.getDrawable(activity, R.mipmap.ic_launcher)?.toBitmap(512, 512)
    if (iconBitmap == null) {
      coroutineScope.launch { snackbarHostState.showSnackbar("Couldn’t load an app icon") }
      return
    }
    val requested = CustomHomeShortcut.requestPin(context, shortcutName, iconBitmap)
    if (customBitmap == null) iconBitmap.recycle()
    if (requested) {
      coroutineScope.launch {
        snackbarHostState.showSnackbar("Shortcut request sent; confirm it on your home screen")
      }
    } else {
      coroutineScope.launch {
        snackbarHostState.showSnackbar("This launcher doesn’t support pinned shortcuts")
      }
    }
  }

  fun handleIconChange(iconType: AppIconType) {
    if (appIconType == iconType) return
    onAppIconTypeChange(iconType)
    IconUtils.setIcon(activity, iconType)
    coroutineScope.launch {
      val result =
        snackbarHostState.showSnackbar(
          message = "Icon updated, restart to apply",
          actionLabel = "Restart"
        )
      if (result == SnackbarResult.ActionPerformed) {
        val packageManager = activity.packageManager
        val intent = packageManager.getLaunchIntentForPackage(activity.packageName)
        if (intent != null) {
          val componentName = intent.component
          val mainIntent = android.content.Intent.makeRestartActivityTask(componentName)
          activity.startActivity(mainIntent)
          Runtime.getRuntime().exit(0)
        }
      }
    }
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.surface,
    topBar = {
      TopAppBar(
        title = { Text("App name & icon", fontWeight = FontWeight.Bold) },
        navigationIcon = {
          IconButton(onClick = { navController.navigateUp() }) {
            Icon(painter = painterResource(R.drawable.arrow_back), contentDescription = "Back")
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
      )
    }
  ) { innerPadding ->
    val icons =
      listOf(
        AppIconOption(AppIconType.CRAFT, "Craft", "Craft icon by CR3TO_", R.mipmap.ic_launcher_craft),
        AppIconOption(AppIconType.SADIE, "Sadie Sink", "Sadie Sink on community demand", R.mipmap.ic_launcher_sadie),
        AppIconOption(
          AppIconType.BILLIE_EILISH,
          "Billie Eilish",
          "billie eilish suggested by Lalo salamanca",
          R.mipmap.billie_eilish_icon
        ),
        AppIconOption(AppIconType.CAT, "Cat Icon", "A Pinkish cat-themed icon", R.mipmap.cat_icon),
        AppIconOption(
          AppIconType.CRAZY_BLUE,
          "Crazy Blue Icon",
          "A vibrant crazy blue icon",
          R.mipmap.crazy_blue_icon
        ),
        AppIconOption(
          AppIconType.ECHO_CAT,
          "Echo Cat",
          "A playful cat by Alarp_Svc",
          R.mipmap.echo_cat_icon
        ),
        AppIconOption(
          AppIconType.LEGACY,
          "Legacy Icon",
          "The OG Monochrome Icon",
          R.mipmap.legacy_icon
        ),
        AppIconOption(
          AppIconType.DEFAULT,
          "Coral & White Clef",
          "Pale treble clef on a coral-red tile",
          R.mipmap.ic_launcher
        ),
        AppIconOption(
          AppIconType.CLEF_GOLD_BLUE,
          "Gold & Blue Clef",
          "Gold-and-blue treble clef on a vinyl-inspired badge",
          R.mipmap.clef_gold_blue
        ),
        AppIconOption(
          AppIconType.CLEF_MONOCHROME,
          "Mono Clef",
          "White treble clef on black",
          R.mipmap.clef_monochrome
        ),
        AppIconOption(
          AppIconType.CLEF_MONOCHROME_ALT,
          "Mono Clef Alt",
          "Alternate white treble-clef design on black",
          R.mipmap.clef_monochrome_alt
        ),
        AppIconOption(
          AppIconType.CLEF_SILVER_BLUE,
          "Clef Silver Blue",
          "Silver, blue and purple treble clef",
          R.mipmap.clef_silver_blue
        ),
        AppIconOption(
          AppIconType.CLEF_VIOLET_BLUE,
          "Clef Violet Blue",
          "Violet-blue glowing treble clef",
          R.mipmap.clef_violet_blue
        ),
        AppIconOption(
          AppIconType.CLEF_SILVER,
          "Clef Silver",
          "Polished silver treble clef",
          R.mipmap.clef_silver
        ),
        AppIconOption(
          AppIconType.CLEF_CRIMSON,
          "Clef Crimson",
          "Bright red treble clef",
          R.mipmap.clef_crimson
        ),
        AppIconOption(
          AppIconType.CLEF_GOLD,
          "Clef Gold",
          "Golden treble clef",
          R.mipmap.clef_gold
        ),
        AppIconOption(
          AppIconType.CLEF_ELECTRIC_BLUE,
          "Clef Electric Blue",
          "Bright electric-blue treble clef",
          R.mipmap.clef_electric_blue
        ),
        AppIconOption(
          AppIconType.CLEF_AMETHYST,
          "Clef Amethyst",
          "Faceted purple-pink treble clef",
          R.mipmap.clef_amethyst
        ),
        AppIconOption(
          AppIconType.CLEF_MIDNIGHT,
          "Clef Midnight",
          "Dark purple treble clef",
          R.mipmap.clef_midnight
        ),
        AppIconOption(
          AppIconType.CLEF_OPAL,
          "Clef Opal",
          "Iridescent white treble clef",
          R.mipmap.clef_opal
        ),
        AppIconOption(
          AppIconType.CLEF_TURQUOISE,
          "Clef Turquoise",
          "Turquoise-cyan treble clef",
          R.mipmap.clef_turquoise
        ),
        AppIconOption(
          AppIconType.CLEF_ROYAL_BLUE,
          "Clef Royal Blue",
          "Deep blue treble clef",
          R.mipmap.clef_royal_blue
        ),
        AppIconOption(
          AppIconType.CLEF_ROSE_COPPER,
          "Clef Rose Copper",
          "Rose-copper treble clef",
          R.mipmap.clef_rose_copper
        ),
        AppIconOption(
          AppIconType.CLEF_MAGENTA_ORANGE,
          "Clef Magenta Orange",
          "Magenta-orange treble clef",
          R.mipmap.clef_magenta_orange
        ),
        AppIconOption(AppIconType.POOKIE, "Pookie Icon", "A Cute Pink icon", R.mipmap.pookie_icon),
        AppIconOption(
          AppIconType.SABRINA_CARPENTER,
          "Sabrina Carpenter",
          "Suggested by notdieinganymore",
          R.mipmap.ic_launcher_sabrina
        ),
        AppIconOption(
          AppIconType.SABRINA_CARPENTER_2,
          "Sabrina Carpenter 2",
          "Suggested by Falvik",
          R.mipmap.ic_launcher_sabrina2
        ),
        AppIconOption(AppIconType.SKY, "Sky Icon", "A beautiful sky-themed icon", R.mipmap.sky_icon),
        AppIconOption(
          AppIconType.NEON_GLASS,
          "Neon Glass",
          "A futuristic glowing neon glass icon",
          R.mipmap.ic_launcher_neon_glass
        ),
        AppIconOption(
          AppIconType.MILLIE_BOBBY_BROWN,
          "Millie Bobby Brown",
          "Millie Bobby Brown on community demand",
          R.mipmap.ic_launcher_millie
        )
      ).sortedBy { it.title.lowercase() }

    Column(
      modifier =
        Modifier.fillMaxSize()
          .background(MaterialTheme.colorScheme.surface)
          .verticalScroll(rememberScrollState())
          .windowInsetsPadding(
            LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal)
          )
          .padding(innerPadding)
          .padding(horizontal = 16.dp)
    ) {
      Spacer(modifier = Modifier.height(8.dp))

      Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
      ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
          Text(
            "Custom home-screen shortcut",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            "Choose a name and image for a home-screen shortcut. Android keeps the app-drawer name fixed; this creates a customizable shortcut.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.height(12.dp))
          OutlinedTextField(
            value = shortcutName,
            onValueChange = { onShortcutNameChange(it.take(40)) },
            label = { Text("Shortcut name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          Spacer(modifier = Modifier.height(12.dp))
          val previewBitmap =
            remember(shortcutIconPath) {
              CustomHomeShortcut.loadIcon(shortcutIconPath)?.asImageBitmap()
                ?: ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
                  ?.toBitmap(192, 192)
                  ?.asImageBitmap()
            }
          Row(verticalAlignment = Alignment.CenterVertically) {
            if (previewBitmap != null) {
              Image(
                bitmap = previewBitmap,
                contentDescription = "Shortcut icon preview",
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp))
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                if (shortcutIconPath.isBlank()) "Using the app icon" else "Custom image selected",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
              )
              Text(
                "Pick a square image for best results",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
          Spacer(modifier = Modifier.height(12.dp))
          Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
              onClick = {
                iconPickerLauncher.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
              }
            ) {
              Text("Choose image")
            }
            if (shortcutIconPath.isNotBlank()) {
              OutlinedButton(onClick = { onShortcutIconPathChange("") }) {
                Text("Use app icon")
              }
            }
          }
          Spacer(modifier = Modifier.height(8.dp))
          Button(onClick = ::addCustomShortcut, modifier = Modifier.fillMaxWidth()) {
            Text("Add shortcut to home screen")
          }
        }
      }

      Text(
        text = "Choose your launcher icon",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
      )

      Column(modifier = Modifier.fillMaxWidth()) {
        icons.forEachIndexed { index, option ->
          val shape =
            when {
              icons.size == 1 -> RoundedCornerShape(24.dp)
              index == 0 ->
                RoundedCornerShape(
                  topStart = 24.dp,
                  topEnd = 24.dp,
                  bottomStart = 4.dp,
                  bottomEnd = 4.dp
                )
              index == icons.size - 1 ->
                RoundedCornerShape(
                  topStart = 4.dp,
                  topEnd = 4.dp,
                  bottomStart = 24.dp,
                  bottomEnd = 24.dp
                )
              else -> RoundedCornerShape(4.dp)
            }

          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            colors =
              CardDefaults.cardColors(
                containerColor =
                  if (appIconType == option.type)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                  else MaterialTheme.colorScheme.surfaceContainerHigh
              ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
          ) {
            AppIconRow(
              option = option,
              isSelected = (appIconType == option.type),
              onClick = { handleIconChange(option.type) }
            )
          }

          if (index < icons.size - 1) {
            Spacer(modifier = Modifier.height(2.dp))
          }
        }
      }

      Spacer(
        modifier =
          Modifier.windowInsetsPadding(
            LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)
          )
      )
      Spacer(modifier = Modifier.height(16.dp))
    }
  }
}

data class AppIconOption(
  val type: AppIconType,
  val title: String,
  val description: String,
  val iconRes: Int
)

@Composable
fun AppIconRow(option: AppIconOption, isSelected: Boolean, onClick: () -> Unit) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(horizontal = 20.dp, vertical = 16.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    val context = LocalContext.current
    val bitmap =
      remember(option.iconRes) {
        val drawable = ContextCompat.getDrawable(context, option.iconRes)
        drawable?.toBitmap(width = 192, height = 192)?.asImageBitmap()
      }

    Box(
      modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)),
      contentAlignment = Alignment.Center
    ) {
      if (bitmap != null) {
        Image(
          bitmap = bitmap,
          contentDescription = null,
          modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
        )
      }
    }

    Spacer(modifier = Modifier.width(20.dp))

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = option.title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = option.description,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    Spacer(modifier = Modifier.width(8.dp))
    RadioButton(selected = isSelected, onClick = onClick)
  }
}
