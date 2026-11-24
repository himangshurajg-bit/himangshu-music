package echo.music.iad1tya.ai

import android.content.Context
import echo.music.iad1tya.api.OpenRouterService
import echo.music.iad1tya.constants.AiProviderKey
import echo.music.iad1tya.constants.OpenRouterApiKey
import echo.music.iad1tya.constants.OpenRouterBaseUrlKey
import echo.music.iad1tya.constants.OpenRouterModelKey
import echo.music.iad1tya.utils.dataStore
import echo.music.iad1tya.utils.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AiSongCompanion {
  suspend fun ask(
    context: Context,
    mode: SongCompanionMode,
    title: String,
    artists: String,
    album: String?,
    lyrics: String,
    selectedLines: List<String> = emptyList(),
    question: String = "",
  ): Result<String> = withContext(Dispatchers.IO) {
    val provider = context.dataStore.get(AiProviderKey, "OpenRouter")
    if (provider == "DeepL") {
      return@withContext Result.failure(
        IllegalStateException("Choose a chat-capable provider in AI Settings; DeepL only translates.")
      )
    }
    val key = context.dataStore.get(OpenRouterApiKey, "")
    val baseUrl =
      context.dataStore.get(
        OpenRouterBaseUrlKey,
        "https://openrouter.ai/api/v1/chat/completions",
      )
    val model = context.dataStore.get(OpenRouterModelKey, "openrouter/free")
    OpenRouterService.chat(
      systemPrompt = AiSongCompanionPrompt.buildSystemPrompt(),
      userPrompt =
        AiSongCompanionPrompt.buildUserPrompt(
          mode = mode,
          title = title,
          artists = artists,
          album = album,
          lyrics = lyrics,
          selectedLines = selectedLines,
          question = question,
        ),
      apiKey = key,
      baseUrl = baseUrl,
      model = model,
    )
  }
}
