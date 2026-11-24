package echo.music.iad1tya.ui.screens.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import echo.music.iad1tya.R
import echo.music.iad1tya.ai.AiPlaylistFlow
import echo.music.iad1tya.ai.PlaylistFlowPlan
import echo.music.iad1tya.db.entities.PlaylistSong
import kotlinx.coroutines.launch

@Composable
fun AiPlaylistFlowDialog(
  playlistName: String,
  songs: List<PlaylistSong>,
  onDismiss: () -> Unit,
  onApply: (List<Int>) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val tracks = remember(songs) { AiPlaylistFlow.tracksFrom(songs) }
  var goal by remember { mutableStateOf("") }
  var loading by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf("") }
  var plan by remember { mutableStateOf<PlaylistFlowPlan?>(null) }
  var plannedMapIds by remember { mutableStateOf<List<Int>?>(null) }
  var plannedTracks by remember { mutableStateOf<List<echo.music.iad1tya.ai.PlaylistFlowTrack>>(emptyList()) }

  Dialog(
    onDismissRequest = { if (!loading) onDismiss() },
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier = Modifier.fillMaxWidth(0.94f).heightIn(max = 700.dp),
      shape = RoundedCornerShape(24.dp),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 6.dp,
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          stringResource(R.string.ai_playlist_flow_title),
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
        )
        Text(
          stringResource(R.string.ai_playlist_flow_privacy),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (plan == null) {
          OutlinedTextField(
            value = goal,
            onValueChange = { goal = it.take(AiPlaylistFlow.MAX_GOAL_CHARS); error = "" },
            label = { Text(stringResource(R.string.ai_playlist_flow_prompt_label)) },
            placeholder = { Text(stringResource(R.string.ai_playlist_flow_prompt_hint)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4,
            enabled = !loading,
          )
        }
        if (loading) {
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator()
            Text(stringResource(R.string.ai_playlist_flow_working))
          }
        }
        if (error.isNotBlank()) {
          Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        plan?.let { result ->
          Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(result.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
              if (result.explanation.isNotBlank()) Text(result.explanation, style = MaterialTheme.typography.bodySmall)
              result.orderedIndices.forEachIndexed { position, index ->
                val track = plannedTracks.first { it.index == index }
                Text("${position + 1}. ${track.title} · ${track.artist}", style = MaterialTheme.typography.bodySmall)
              }
            }
          }
          if (plannedMapIds != songs.map { it.map.id }) {
            Text(
              stringResource(R.string.ai_playlist_flow_changed),
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodySmall,
            )
          }
          Text(
            stringResource(R.string.ai_playlist_flow_apply_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Spacer(Modifier.height(2.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
          TextButton(onClick = onDismiss, enabled = !loading) {
            Text(stringResource(android.R.string.cancel))
          }
          if (plan == null) {
            Button(
              enabled = !loading && songs.isNotEmpty() && goal.isNotBlank(),
              onClick = {
                loading = true
                error = ""
                val requestTracks = tracks
                val requestMapIds = songs.map { it.map.id }
                scope.launch {
                  AiPlaylistFlow.create(context, playlistName, goal, requestTracks)
                    .onSuccess {
                      plan = it
                      plannedTracks = requestTracks
                      plannedMapIds = requestMapIds
                    }
                    .onFailure { error = it.message ?: context.getString(R.string.ai_playlist_flow_error) }
                  loading = false
                }
              },
            ) { Text(stringResource(R.string.ai_playlist_flow_preview)) }
          } else {
            Button(enabled = plannedMapIds == songs.map { it.map.id }, onClick = { onApply(plan!!.orderedIndices) }) {
              Text(stringResource(R.string.ai_playlist_flow_apply))
            }
          }
        }
      }
    }
  }
}
