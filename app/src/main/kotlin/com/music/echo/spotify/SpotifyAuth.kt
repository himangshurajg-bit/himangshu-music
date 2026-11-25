/*
 * EchoMusic (2026)
 * © Chartreux Westia — github.com/koiverse
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 * Spotify authentication fallback approach adapted from Vivi Music (GPL-3.0):
 * https://github.com/vivizzz007/vivi-music
 */

package echo.music.iad1tya.spotify

import echo.music.iad1tya.spotify.models.SpotifyInternalToken
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.floor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Handles Spotify authentication using the web player's internal token endpoint. Uses sp_dc cookies
 * (extracted from WebView login) to obtain access tokens without requiring a Spotify Developer
 * Client ID.
 *
 * Token acquisition requires a TOTP (Time-based One-Time Password) generated from a shared secret
 * that Spotify rotates periodically. The secret and its version are fetched from a
 * community-maintained GitHub Gist.
 *
 * Reference: https://github.com/sonic-liberation/spotube-plugin-spotify
 */
internal fun spotifyAuthRetryDelayMillis(
  statusCode: Int?,
  retryAfterSec: Long?,
  attempt: Int,
): Long? {
  if (attempt >= 3 || (statusCode != null && statusCode != 429 && statusCode !in 500..599)) {
    return null
  }
  val serverDelay = retryAfterSec?.takeIf { it > 0L }?.coerceAtMost(5L)?.times(1_000L)
  return serverDelay ?: (250L shl attempt.coerceIn(0, 2))
}

object SpotifyAuth {
  private const val TOKEN_URL = "https://open.spotify.com/api/token"
  private const val SERVER_TIME_URL = "https://open.spotify.com/api/server-time"
  private const val NUANCE_GIST_URL =
    "https://api.github.com/gists/22ed9c6ba463899e933427f7de1f0eef"
  private const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

  const val LOGIN_URL =
    "https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F"

  private val json = Json {
    isLenient = true
    ignoreUnknownKeys = true
  }

  @Serializable private data class Nuance(val s: String, val v: Int)

  // Keep authentication usable when GitHub or Spotify's clock endpoint is temporarily unavailable.
  private val defaultNuance =
    Nuance(
      s = "GM3TMMJTGYZTQNZVGM4DINJZHA4TGOBYGMZTCMRTGEYDSMJRHE4TEOBUG4YTCMRUGQ4DQOJUGQYTAMRRGA2TCMJSHE3TCMBY",
      v = 61,
    )

  @Volatile private var cachedNuance: Nuance = defaultNuance
  @Volatile private var serverTimeOffsetSec: Long? = null

  @Serializable private data class GistFile(val content: String)
  @Serializable private data class GistFiles(val files: Map<String, GistFile>)
  @Serializable private data class ServerTimeResponse(val serverTime: Long)

  /**
   * Fetches an internal web-player access token using session cookies and TOTP.
   * 1. Fetches the TOTP secret from the community Gist
   * 2. Gets the server time from Spotify
   * 3. Generates a 6-digit TOTP (SHA1, 30s interval)
   * 4. Calls /api/token with the TOTP and sp_dc cookie
   */
  suspend fun fetchAccessToken(
    spDc: String,
    spKey: String = "",
    forceRefreshNuance: Boolean = false,
  ): Result<SpotifyInternalToken> {
    return try {
      val nuance =
        if (forceRefreshNuance) {
          retryTransientRequest { refreshNuance() }
        } else {
          cachedNuance
        }
      val serverTimeSec = retryTransientRequest { fetchServerTime() }
      val totp = generateTotp(nuance.s, serverTimeSec)

      val tokenUrl = buildString {
        append(TOKEN_URL)
        append("?reason=transport")
        append("&productType=web-player")
        append("&totp=$totp")
        append("&totpServer=$totp")
        append("&totpVer=${nuance.v}")
      }

      val cookieHeader = buildString {
        append("sp_dc=$spDc")
        if (spKey.isNotEmpty()) {
          append("; sp_key=$spKey")
        }
      }

      val body = retryTransientRequest {
        withContext(Dispatchers.IO) { httpGet(tokenUrl, mapOf("Cookie" to cookieHeader)) }
      }

      val token = json.decodeFromString<SpotifyInternalToken>(body)

      if (token.isAnonymous || token.accessToken.isBlank()) {
        if (!forceRefreshNuance) {
          return fetchAccessToken(spDc, spKey, forceRefreshNuance = true)
        }
        throw Spotify.SpotifyException(
          401,
          "Received anonymous token — sp_dc cookie is invalid or expired",
        )
      }

      Result.success(token)
    } catch (cancellation: CancellationException) {
      throw cancellation
    } catch (error: Exception) {
      Result.failure(error)
    }
  }

  private suspend fun refreshNuance(): Nuance =
    withContext(Dispatchers.IO) {
      try {
        val body = httpGet(NUANCE_GIST_URL, emptyMap())
        val gist = json.decodeFromString<GistFiles>(body)
        val content = gist.files.values.firstOrNull()?.content
          ?: throw Spotify.SpotifyException(500, "Gist has no files")
        val latest = json.decodeFromString<List<Nuance>>(content).maxByOrNull { it.v }
          ?: defaultNuance
        cachedNuance = latest
        latest
      } catch (error: Exception) {
        if (error is CancellationException) throw error
        cachedNuance
      }
    }

  private suspend fun fetchServerTime(): Long =
    withContext(Dispatchers.IO) {
      val now = System.currentTimeMillis() / 1000
      serverTimeOffsetSec?.let { return@withContext now + it }
      val serverTime =
        try {
          val body = httpGet(SERVER_TIME_URL, emptyMap())
          json.decodeFromString<ServerTimeResponse>(body).serverTime
        } catch (error: Exception) {
          if (error is CancellationException) throw error
          now
        }
      serverTimeOffsetSec = serverTime - now
      serverTime
    }

  private suspend fun <T> retryTransientRequest(block: suspend () -> T): T {
    var attempt = 0
    while (true) {
      try {
        return block()
      } catch (error: Exception) {
        if (error is CancellationException) throw error
        val spotifyError = error as? Spotify.SpotifyException
        val delayMillis =
          when {
            error is IOException -> spotifyAuthRetryDelayMillis(null, null, attempt)
            spotifyError != null ->
              spotifyAuthRetryDelayMillis(
                spotifyError.statusCode,
                spotifyError.retryAfterSec.takeIf { it > 0L },
                attempt,
              )
            else -> null
          } ?: throw error
        delay(delayMillis)
        attempt++
      }
    }
  }

  /**
   * Generates a 6-digit TOTP using HMAC-SHA1 (RFC 6238).
   *
   * @param secret Base32-encoded shared secret
   * @param serverTimeSec Spotify server time in seconds since epoch
   */
  private fun generateTotp(secret: String, serverTimeSec: Long): String {
    val key = base32Decode(secret)
    val interval = 30L
    val timeStep = floor(serverTimeSec.toDouble() / interval).toLong()

    val timeBytes = ByteArray(8)
    var value = timeStep
    for (i in 7 downTo 0) {
      timeBytes[i] = (value and 0xFF).toByte()
      value = value shr 8
    }

    val mac = Mac.getInstance("HmacSHA1")
    mac.init(SecretKeySpec(key, "HmacSHA1"))
    val hash = mac.doFinal(timeBytes)

    val offset = hash[hash.size - 1].toInt() and 0x0F
    val code =
      ((hash[offset].toInt() and 0x7F) shl 24) or
        ((hash[offset + 1].toInt() and 0xFF) shl 16) or
        ((hash[offset + 2].toInt() and 0xFF) shl 8) or
        (hash[offset + 3].toInt() and 0xFF)

    val otp = code % 1_000_000
    return otp.toString().padStart(6, '0')
  }

  private fun base32Decode(input: String): ByteArray {
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    val cleaned = input.uppercase().replace("=", "")

    val output = mutableListOf<Byte>()
    var buffer = 0
    var bitsLeft = 0

    for (c in cleaned) {
      val value = alphabet.indexOf(c)
      if (value < 0) continue
      buffer = (buffer shl 5) or value
      bitsLeft += 5
      if (bitsLeft >= 8) {
        bitsLeft -= 8
        output.add(((buffer shr bitsLeft) and 0xFF).toByte())
      }
    }

    return output.toByteArray()
  }

  private fun httpGet(urlString: String, extraHeaders: Map<String, String>): String {
    val connection = URL(urlString).openConnection() as HttpURLConnection
    try {
      connection.requestMethod = "GET"
      connection.instanceFollowRedirects = true
      connection.connectTimeout = 15_000
      connection.readTimeout = 15_000
      connection.setRequestProperty("User-Agent", USER_AGENT)
      connection.setRequestProperty("Accept", "application/json, text/plain, */*")
      connection.setRequestProperty("Accept-Language", "en")
      for ((key, value) in extraHeaders) {
        connection.setRequestProperty(key, value)
      }

      val responseCode = connection.responseCode
      if (responseCode !in 200..299) {
        val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
        val retryAfterSec = connection.getHeaderField("Retry-After")?.trim()?.toLongOrNull()
        throw Spotify.SpotifyException(
          responseCode,
          "HTTP $responseCode: $errorBody",
          retryAfterSec = retryAfterSec ?: 0L,
        )
      }

      return connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
      connection.disconnect()
    }
  }
}
