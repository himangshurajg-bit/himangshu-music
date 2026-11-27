package echo.music.iad1tya.stems

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import echo.music.iad1tya.R

@Composable
internal fun StemSplitterScreen(
  navController: NavController,
  viewModel: StemSplitterViewModel = hiltViewModel(),
) {
  val context = LocalContext.current
  val state by viewModel.state.collectAsState()
  val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    uri?.let(viewModel::select)
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.stem_splitter)) },
        navigationIcon = {
          IconButton(onClick = { navController.navigateUp() }) {
            Icon(painter = painterResource(R.drawable.arrow_back), contentDescription = stringResource(R.string.back))
          }
        },
      )
    },
  ) { padding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item {
        Spacer(Modifier.height(8.dp))
        Text(
          "Split a local audio file into vocals, drums, bass and other using neural models on this device.",
          style = MaterialTheme.typography.bodyLarge,
        )
      }
      item {
        Card(modifier = Modifier.fillMaxWidth()) {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Model download", style = MaterialTheme.typography.titleMedium)
            Text("Model weights are not bundled with RajTune. First use downloads about 103 MB; keep about 214 MB free while the ZIP is checked and extracted. Afterward, the verified models stay cached in app storage (~111 MB / 106 MiB), so later separation works offline. Each minute of audio also needs about 21 MB temporary PCM and about 42 MB for the four WAV stems, plus a temporary copy of the selected file. Free space, memory and processing time depend on song length and device. If download or processing fails, tap Retry; verified cached models are reused.")
          }
        }
      }
      item {
        Card(modifier = Modifier.fillMaxWidth()) {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Model attribution", style = MaterialTheme.typography.titleMedium)
            Text("KUIELab-MDX-Net by Minseok Kim, Woosung Choi, Jaehwa Chung, Daewon Lee and Soonyoung Jung (2021), CC BY 4.0. Zenodo DOI: 10.5281/zenodo.6513062.")
            TextButton(onClick = {
              runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://doi.org/10.5281/zenodo.6513062")))
              }
            }) { Text("Model record and CC BY 4.0 license") }
          }
        }
      }
      // Cloud mode (explicit opt-in, no silent upload)
      item {
        Card(modifier = Modifier.fillMaxWidth()) {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
              Text("Cloud processing", style = MaterialTheme.typography.titleMedium)
              Switch(checked = state.cloudMode, onCheckedChange = viewModel::setCloudMode, enabled = !state.running)
            }
            if (state.cloudMode) {
              if (state.cloudEndpoint.isBlank()) {
                Text("Cloud not configured - Offline mode only. Enter an endpoint to enable upload.", style = MaterialTheme.typography.bodyMedium)
              }
              TextField(
                value = state.cloudEndpoint,
                onValueChange = viewModel::setCloudEndpoint,
                label = { Text("Cloud endpoint (https://…)") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.running,
              )
              TextField(
                value = state.cloudToken,
                onValueChange = viewModel::setCloudToken,
                label = { Text("Bearer token (optional / test)") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.running,
              )
              Text("Uploading to cloud sends your audio to the server. Offline is the default. No audio is uploaded unless you turn on Cloud and enter an endpoint.",
                style = MaterialTheme.typography.bodySmall)
              if (state.cloudStatus.isNotEmpty()) Text(state.cloudStatus, style = MaterialTheme.typography.bodyMedium)
            } else {
              Text("Offline mode is default. Switch to Cloud only when you want server processing.",
                style = MaterialTheme.typography.bodyMedium)
            }
          }
        }
      }
      item {
        OutlinedButton(
          onClick = { filePicker.launch(arrayOf("audio/*")) },
          enabled = !state.running,
          modifier = Modifier.fillMaxWidth(),
        ) { Text(state.sourceName ?: "Choose audio file") }
      }
      item {
        Button(
          onClick = viewModel::start,
          enabled = state.sourceUri != null && !state.running && (!state.cloudMode || state.cloudEndpoint.isNotBlank()),
          modifier = Modifier.fillMaxWidth(),
        ) { Text(if (state.error != null) "Retry separation" else if (state.cloudMode) "Upload & separate (cloud)" else "Separate four stems") }
      }
      if (state.cloudMode && state.cloudEndpoint.isNotBlank()) {
        item {
          OutlinedButton(
            onClick = viewModel::startCloud,
            enabled = state.sourceUri != null && !state.running,
            modifier = Modifier.fillMaxWidth(),
          ) { Text("Upload audio to cloud") }
        }
      }
      if (state.running) {
        item {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(state.label)
            LinearProgressIndicator(progress = { state.percent / 100f }, modifier = Modifier.fillMaxWidth())
            Text("${state.percent}%")
            OutlinedButton(onClick = viewModel::cancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
          }
        }
      } else {
        item { Text(state.label, style = MaterialTheme.typography.bodyMedium) }
      }
      state.error?.let { message ->
        item { Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
      }
      if (state.outputs.isNotEmpty()) {
        item {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Separated files", style = MaterialTheme.typography.titleLarge)
            Text("Saved in Music/RajTune Stems; they can also be found in Local Songs.", style = MaterialTheme.typography.bodyMedium)
          }
        }
        items(state.outputs, key = { it.name }) { output ->
          Card(modifier = Modifier.fillMaxWidth()) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(output.name.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium)
              OutlinedButton(onClick = {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                  setDataAndType(output.uri, "audio/wav")
                  addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching { context.startActivity(intent) }
              }) { Text("Play") }
            }
          }
        }
      }
    }
  }
}
