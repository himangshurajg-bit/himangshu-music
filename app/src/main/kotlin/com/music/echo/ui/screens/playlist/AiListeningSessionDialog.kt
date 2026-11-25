package echo.music.iad1tya.ui.screens.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import echo.music.iad1tya.LocalPlayerConnection
import echo.music.iad1tya.R
import echo.music.iad1tya.ai.AiListeningSession
import echo.music.iad1tya.ai.ListeningSessionPlan
import echo.music.iad1tya.ai.ListeningSessionTrack
import echo.music.iad1tya.db.entities.PlaylistSong
import echo.music.iad1tya.extensions.toMediaItem
import echo.music.iad1tya.playback.queues.ListQueue
import kotlinx.coroutines.launch

@Composable
fun AiListeningSessionDialog(
  playlistName: String,
  songs: List<PlaylistSong>,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val player = LocalPlayerConnection.current
  val scope = rememberCoroutineScope()
  var budget by remember { mutableIntStateOf(30) }
  var loading by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf("") }
  var plan by remember { mutableStateOf<ListeningSessionPlan?>(null) }
  var plannedMapIds by remember { mutableStateOf<List<Pair<Int, String>>?>(null) }
  var plannedTracks by remember { mutableStateOf<List<ListeningSessionTrack>>(emptyList()) }
  val tracks = remember(songs) { AiListeningSession.tracksFrom(songs) }

  Dialog(
    onDismissRequest = { if (!loading) onDismiss() },
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier = Modifier.fillMaxWidth(0.94f).heightIn(max = 680.dp),
      shape = RoundedCornerShape(24.dp),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 6.dp,
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          stringResource(R.string.ai_listening_session_title),
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
        )
        Text(
          stringResource(R.string.ai_listening_session_privacy),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(15, 30, 45).forEach { minutes ->
            FilterChip(
              selected = budget == minutes,
              onClick = {
                budget = minutes
                plan = null
                plannedMapIds = null
                plannedTracks = emptyList()
                error = ""
              },
              label = { Text(stringResource(R.string.ai_listening_session_minutes, minutes)) },
              enabled = !loading,
            )
          }
        }
        if (loading) {
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator()
            Text(stringResource(R.string.ai_listening_session_working))
          }
        }
        if (error.isNotBlank())
          Text(
            error,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
          )
        plan?.let { result ->
          Card(
            colors =
              CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
              )
          ) {
            Column(
              Modifier.fillMaxWidth().padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                result.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
              )
              Text(
                stringResource(
                  R.string.ai_listening_session_duration,
                  result.durationSeconds / 60,
                  result.durationSeconds % 60,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
              )
              if (result.explanation.isNotBlank())
                Text(result.explanation, style = MaterialTheme.typography.bodySmall)
              result.trackIndices.forEachIndexed { position, index ->
                val track = plannedTracks.first { it.index == index }
                Text(
                  "${position + 1}. ${track.title} · ${track.artist}",
                  style = MaterialTheme.typography.bodySmall,
                )
              }
            }
          }
          if (plannedMapIds != songs.map { it.map.id to it.map.songId }) {
            Text(
              stringResource(R.string.ai_listening_session_changed),
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodySmall,
            )
          }
        }
        Text(
          stringResource(R.string.ai_listening_session_disclaimer),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
          TextButton(onClick = onDismiss, enabled = !loading) {
            Text(stringResource(android.R.string.cancel))
          }
          if (plan == null) {
            Button(
              enabled = !loading && songs.isNotEmpty(),
              onClick = {
                loading = true
                error = ""
                val requestTracks = tracks
                val requestMapIds = songs.map { it.map.id to it.map.songId }
                scope.launch {
                  val result = AiListeningSession.create(context, playlistName, requestTracks, budget)
                  result
                    .onSuccess {
                      plan = it
                      plannedTracks = requestTracks
                      plannedMapIds = requestMapIds
                    }
                    .onFailure {
                      error = it.message ?: context.getString(R.string.ai_listening_session_error)
                    }
                  loading = false
                }
              },
            ) {
              Text(stringResource(R.string.ai_listening_session_build))
            }
          } else {
            Button(
              enabled = player != null && plannedMapIds == songs.map { it.map.id to it.map.songId },
              onClick = {
                val chosen =
                  plan!!.trackIndices.map { index ->
                    songs[index].song.toMediaItem()
                  }
                player?.playQueue(ListQueue(title = plan!!.title, items = chosen))
                onDismiss()
              },
            ) {
              Text(stringResource(R.string.ai_listening_session_play))
            }
          }
        }
      }
    }
  }
}
