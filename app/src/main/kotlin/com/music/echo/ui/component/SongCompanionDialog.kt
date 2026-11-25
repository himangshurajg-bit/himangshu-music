package echo.music.iad1tya.ui.component

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
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import echo.music.iad1tya.R
import echo.music.iad1tya.ai.AiPlaylistGenerator
import echo.music.iad1tya.ai.AiSongCompanion
import echo.music.iad1tya.ai.SongCompanionMode
import echo.music.iad1tya.ai.AiSongCompanionPrompt
import echo.music.iad1tya.constants.AiProviderKey
import echo.music.iad1tya.utils.dataStore
import echo.music.iad1tya.utils.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SongCompanionDialog(
  title: String,
  artists: String,
  album: String?,
  lyrics: String,
  selectedLines: List<String>,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val initialMode = if (selectedLines.isNotEmpty()) SongCompanionMode.EXPLAIN_LINES else SongCompanionMode.THEMES
  var mode by remember(title, selectedLines) { mutableStateOf(initialMode) }
  var question by rememberSaveable(title) { mutableStateOf("") }
  var answer by rememberSaveable(title) { mutableStateOf("") }
  var error by rememberSaveable(title) { mutableStateOf("") }
  var loading by rememberSaveable(title) { mutableStateOf(false) }
  var mixLoading by rememberSaveable(title) { mutableStateOf(false) }
  var mixStatus by rememberSaveable(title) { mutableStateOf("") }
  var mixError by rememberSaveable(title) { mutableStateOf("") }

  Dialog(
    onDismissRequest = { if (!loading && !mixLoading) onDismiss() },
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
          text = stringResource(R.string.song_companion_title),
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = "$title · $artists",
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = stringResource(R.string.song_companion_privacy_note),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val hasLyrics = lyrics.isNotBlank()
        if (!hasLyrics) {
          Text(
            text = stringResource(R.string.song_companion_no_lyrics),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          FilterChip(
            selected = mode == SongCompanionMode.THEMES,
            onClick = { mode = SongCompanionMode.THEMES; answer = ""; error = "" },
            enabled = hasLyrics,
            label = { Text(stringResource(R.string.song_companion_themes)) },
          )
          FilterChip(
            selected = mode == SongCompanionMode.EXPLAIN_LINES,
            onClick = { mode = SongCompanionMode.EXPLAIN_LINES; answer = ""; error = "" },
            enabled = hasLyrics,
            label = { Text(stringResource(R.string.song_companion_explain)) },
          )
          FilterChip(
            selected = mode == SongCompanionMode.QUESTION,
            onClick = { mode = SongCompanionMode.QUESTION; answer = ""; error = "" },
            enabled = hasLyrics,
            label = { Text(stringResource(R.string.song_companion_ask)) },
          )
        }

        if (mode == SongCompanionMode.QUESTION) {
          OutlinedTextField(
            value = question,
            onValueChange = { question = it.take(500); answer = ""; error = "" },
            label = { Text(stringResource(R.string.song_companion_question_hint)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4,
            enabled = !loading,
          )
        }

        if (selectedLines.isNotEmpty()) {
          Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.padding(12.dp)) {
              Text(
                text = stringResource(R.string.song_companion_selected_context),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
              )
              Spacer(Modifier.height(4.dp))
              Text(selectedLines.joinToString("\n"), style = MaterialTheme.typography.bodyMedium)
            }
          }
        }

        if (loading) {
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator()
            Text(stringResource(R.string.song_companion_thinking), style = MaterialTheme.typography.bodyMedium)
          }
        }
        if (error.isNotBlank()) {
          Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        if (answer.isNotBlank()) {
          Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Text(
              text = answer,
              modifier = Modifier.padding(14.dp),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }

        Spacer(Modifier.height(4.dp))
        Text(
          text = stringResource(R.string.song_companion_vibe_mix_note),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (mixLoading) {
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator()
            Text(stringResource(R.string.song_companion_vibe_mix_working))
          }
        }
        if (mixStatus.isNotBlank()) {
          Text(mixStatus, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        }
        if (mixError.isNotBlank()) {
          Text(mixError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Button(
          enabled = !loading && !mixLoading && title.isNotBlank() && artists.isNotBlank(),
          modifier = Modifier.fillMaxWidth(),
          onClick = {
            mixLoading = true
            mixError = ""
            mixStatus = ""
            scope.launch {
              try {
                val provider = context.dataStore.get(AiProviderKey, "OpenRouter")
                if (!AiSongCompanionPrompt.supportsVibeMixProvider(provider)) {
                  mixError = context.getString(R.string.song_companion_chat_provider_required)
                  return@launch
                }
                val playlistId =
                  AiPlaylistGenerator.generatePlaylist(
                    context = context,
                    userPrompt = AiSongCompanionPrompt.buildVibeMixPrompt(title, artists),
                    onLog = { log -> withContext(Dispatchers.Main) { mixStatus = log } },
                  )
                if (playlistId != null) {
                  mixStatus = context.getString(R.string.song_companion_vibe_mix_done)
                } else {
                  mixError = context.getString(R.string.song_companion_vibe_mix_failed)
                  mixStatus = ""
                }
              } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
              } catch (_: Exception) {
                mixError = context.getString(R.string.song_companion_vibe_mix_failed)
                mixStatus = ""
              } finally {
                mixLoading = false
              }
            }
          },
        ) {
          Text(stringResource(R.string.song_companion_vibe_mix))
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
        ) {
          TextButton(onClick = onDismiss, enabled = !loading && !mixLoading) {
            Text(stringResource(android.R.string.cancel))
          }
          Button(
            enabled = hasLyrics && !loading && !mixLoading && (mode != SongCompanionMode.QUESTION || question.isNotBlank()),
            onClick = {
              loading = true
              error = ""
              answer = ""
              scope.launch {
                val result =
                  AiSongCompanion.ask(
                    context = context,
                    mode = mode,
                    title = title,
                    artists = artists,
                    album = album,
                    lyrics = lyrics,
                    selectedLines = selectedLines,
                    question = question,
                  )
                result.onSuccess { answer = it }.onFailure {
                  error = it.message ?: context.getString(R.string.song_companion_generic_error)
                }
                loading = false
              }
            },
          ) {
            Text(stringResource(R.string.song_companion_ask))
          }
        }
      }
    }
  }
}
