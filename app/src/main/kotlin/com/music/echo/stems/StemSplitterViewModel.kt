package echo.music.iad1tya.stems

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class StemSplitterUiState(
  val sourceUri: Uri? = null,
  val sourceName: String? = null,
  val label: String = "Choose an audio file to begin",
  val percent: Int = 0,
  val running: Boolean = false,
  val outputs: List<SeparatedStem> = emptyList(),
  val error: String? = null,
  val cloudMode: Boolean = false,
  val cloudEndpoint: String = "",
  val cloudToken: String = "",
  val cloudStatus: String = "",
)

@HiltViewModel
internal class StemSplitterViewModel @Inject constructor(
  @ApplicationContext private val context: Context,
  private val engine: StemSeparationEngine,
) : ViewModel() {
  private val _state = MutableStateFlow(StemSplitterUiState())
  val state = _state.asStateFlow()
  private var job: Job? = null

  fun select(uri: Uri) {
    runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    val name = runCatching {
      context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull() ?: "Selected audio"
    _state.value = StemSplitterUiState(sourceUri = uri, sourceName = name, label = name, cloudMode = _state.value.cloudMode, cloudEndpoint = _state.value.cloudEndpoint, cloudToken = _state.value.cloudToken)
  }

  fun setCloudMode(on: Boolean) { _state.update { it.copy(cloudMode = on) } }
  fun setCloudEndpoint(v: String) { _state.update { it.copy(cloudEndpoint = v) } }
  fun setCloudToken(v: String) { _state.update { it.copy(cloudToken = v) } }

  fun start() {
    val current = _state.value
    val uri = current.sourceUri ?: return
    if (current.running) return
    if (!current.cloudMode) {
      job = viewModelScope.launch {
        _state.value = current.copy(running = true, percent = 0, outputs = emptyList(), error = null, label = "Starting separation")
        try {
          val outputs = engine.separate(uri, current.sourceName ?: "Audio") { update ->
            _state.value = _state.value.copy(label = update.label, percent = update.percent)
          }
          _state.value = _state.value.copy(running = false, percent = 100, label = "Four stems saved to Music/RajTune Stems", outputs = outputs)
        } catch (cancelled: CancellationException) {
          _state.value = _state.value.copy(running = false, label = "Separation cancelled", percent = 0)
          throw cancelled
        } catch (error: Throwable) {
          _state.value = _state.value.copy(running = false, label = "Separation failed", error = error.message ?: "Unexpected audio processing error")
        }
      }
    } else {
      startCloud()
    }
  }

  fun startCloud() {
    val current = _state.value
    val uri = current.sourceUri ?: return
    if (current.running) return
    if (current.cloudEndpoint.isBlank()) {
      _state.value = current.copy(error = "Cloud not configured - Offline mode only")
      return
    }
    job = viewModelScope.launch {
      _state.value = current.copy(running = true, percent = 0, outputs = emptyList(), error = null, cloudStatus = "Uploading audio to cloud")
      try {
        val outputs = StemCloudClient(context).separateCloud(current.cloudEndpoint, current.cloudToken, uri, current.sourceName ?: "Audio") { update ->
          _state.value = _state.value.copy(label = update.label, percent = update.percent, cloudStatus = update.label)
        }
        _state.value = _state.value.copy(running = false, percent = 100, label = "Cloud stems saved to Music/RajTune Stems", outputs = outputs, cloudStatus = "Complete")
      } catch (cancelled: CancellationException) {
        _state.value = _state.value.copy(running = false, label = "Cloud separation cancelled", percent = 0, cloudStatus = "Cancelled")
        throw cancelled
      } catch (error: Throwable) {
        _state.value = _state.value.copy(running = false, label = "Cloud separation failed", error = error.message ?: "Cloud processing error", cloudStatus = "Failed")
      }
    }
  }

  fun cancel() {
    job?.cancel()
    job = null
  }
}
