package echo.music.iad1tya.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import java.net.URLEncoder
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private data class FocusMode(
  val title: String,
  val description: String,
  val query: String,
  val timerType: String? = null,
)

private val focusModes = listOf(
  FocusMode("Deep Focus", "Steady instrumental textures with minimal vocals or abrupt changes.", "instrumental deep focus ambient music playlist"),
  FocusMode("Mathematics Mode", "Repetitive electronic and ambient soundscapes, without lyrics.", "instrumental electronic ambient math study music playlist"),
  FocusMode("Physics Mode", "Calm, structured instrumental soundscapes for problem-solving.", "calm structured instrumental ambient physics study music"),
  FocusMode("Reading Mode", "Gentle ambient and acoustic instrumentals kept in the background.", "gentle ambient acoustic instrumental reading music playlist"),
  FocusMode("Memory & Revision", "Quiet, low-variation background music for review sessions.", "calm minimal instrumental memory revision study music"),
  FocusMode("Coding Mode", "Instrumental electronic ambience with a consistent feel.", "instrumental electronic ambient coding focus music playlist"),
  FocusMode("Exam Preparation", "Choose a concentration-session length, then find matching music.", "instrumental exam preparation concentration music", "focus"),
  FocusMode("Relaxed Study", "Soft piano and ambient instrumentals for a gentler study pace.", "soft piano ambient relaxed study instrumental music"),
  FocusMode("Pomodoro Focus", "A 25-minute focus / 5-minute break timer, with music searches for both.", "instrumental pomodoro focus study music playlist", "pomodoro"),
  FocusMode("Night Study", "Low-stimulation ambient music; keep device brightness comfortable.", "low stimulation ambient instrumental night study music"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusModesScreen(navController: NavController) {
  BackHandler { navController.popBackStack() }
  var concentrationMinutes by rememberSaveable { mutableIntStateOf(25) }
  var timerType by rememberSaveable { mutableStateOf("focus") }
  var isBreak by rememberSaveable { mutableStateOf(false) }
  var timerRunning by rememberSaveable { mutableStateOf(false) }
  var remainingSeconds by rememberSaveable { mutableIntStateOf(0) }

  LaunchedEffect(timerRunning, remainingSeconds) {
    if (!timerRunning) return@LaunchedEffect
    if (remainingSeconds > 0) {
      delay(1_000)
      remainingSeconds -= 1
    } else if (timerType == "pomodoro") {
      isBreak = !isBreak
      remainingSeconds = (if (isBreak) 5 else 25) * 60
    } else {
      timerRunning = false
    }
  }

  fun startTimer(type: String) {
    timerType = type
    isBreak = false
    remainingSeconds = (if (type == "pomodoro") 25 else concentrationMinutes) * 60
    timerRunning = true
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Study focus") },
        navigationIcon = {
          IconButton(onClick = { navController.popBackStack() }) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
          }
        },
      )
    }
  ) { padding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(padding),
      contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Text(
          "Pick a listening profile and explore matching music. These are low-distraction starting points, not a promise that music improves focus.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      item {
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
          Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Concentration timer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("Custom session: $concentrationMinutes minutes", style = MaterialTheme.typography.bodyMedium)
            Slider(
              value = concentrationMinutes.toFloat(),
              onValueChange = { concentrationMinutes = it.roundToInt() },
              valueRange = 15f..90f,
              steps = 14,
            )
            if (timerRunning || remainingSeconds > 0) {
              Text(
                text = if (timerType == "pomodoro") "${if (isBreak) "Break" else "Focus"} · ${formatTime(remainingSeconds)}" else "Focus · ${formatTime(remainingSeconds)}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
              )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Button(
                onClick = {
                  if (timerRunning) timerRunning = false
                  else if (remainingSeconds > 0) timerRunning = true
                  else startTimer(timerType)
                }
              ) {
                Icon(if (timerRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null)
                Text(if (timerRunning) "Pause" else "Start custom")
              }
              OutlinedButton(onClick = { timerRunning = false; remainingSeconds = 0; isBreak = false }) {
                Icon(Icons.Rounded.Stop, contentDescription = null)
                Text("Reset")
              }
            }
            Text("The timer is visual only; music keeps playing independently.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
      items(focusModes, key = { it.title }) { mode ->
        Card(
          shape = RoundedCornerShape(24.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
          Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(mode.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(mode.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Button(onClick = {
                val encoded = URLEncoder.encode(mode.query, "UTF-8")
                navController.navigate("search/$encoded")
              }) { Text("Find music") }
              if (mode.timerType != null) {
                OutlinedButton(onClick = { startTimer(mode.timerType) }) {
                  Text(if (mode.timerType == "pomodoro") "Start 25 / 5" else "Start custom session")
                }
              }
            }
          }
        }
      }
    }
  }
}

private fun formatTime(seconds: Int): String = "%02d:%02d".format(seconds / 60, seconds % 60)
