package echo.music.iad1tya.stems

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

private val Context.stemMixDataStore: DataStore<Preferences> by preferencesDataStore("stem_mixer")

/**
 * Persists professional mixer settings (per-stem gain/pan/EQ/trim, master EQ,
 * speed/pitch, trim/loop, source and named EQ presets).
 *
 * Uses org.json only so the format stays stable and dependency-light.
 */
@Singleton
internal class StemProjectStore @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  suspend fun load(): StemMixerSnapshot {
    val json = context.stemMixDataStore.data.map { prefs ->
      prefs[stringPreferencesKey("mixer_snapshot")]
    }.first()
    if (json.isNullOrBlank()) return StemMixerSnapshot()
    return runCatching { StemMixerSnapshot.fromJson(JSONObject(json)) }.getOrNull() ?: StemMixerSnapshot()
  }

  suspend fun save(snapshot: StemMixerSnapshot) {
    context.stemMixDataStore.edit { prefs ->
      prefs[KEY] = snapshot.toJson().toString()
    }
  }

  suspend fun saveEqPreset(name: String, stemId: String, bands: List<StemEqBand>) {
    val snapshot = load().copy(savedPresets = load().savedPresets + (name to SavedPreset(stemId, bands)))
    save(snapshot)
  }

  suspend fun loadEqPreset(name: String): SavedPreset? = load().savedPresets[name]

  private val KEY = stringPreferencesKey("mixer_snapshot")
}

data class SavedPreset(
  val stemId: String,
  val bands: List<StemEqBand>,
)

data class StemMixerSnapshot(
  val stems: Map<StemId, StemMixSettings> = StemId.entries.associateWith { StemMixSettings(it) },
  val master: MasterMixSettings = MasterMixSettings(),
  /** Named user presets per stem key "stemId:presetName". */
  val savedPresets: Map<String, SavedPreset> = emptyMap(),
  val lastSavedAtEpochMs: Long = 0L,
  val practiceMode: PracticeMode = PracticeMode.Advanced,
  val transposeSemitones: Int = 0,
  val click: ClickSettings = ClickSettings(),
  val loudness: LoudnessTarget = LoudnessTarget.None,
) {
  fun toJson(): JSONObject {
    val root = JSONObject()
    root.put("savedAt", lastSavedAtEpochMs)
    val stems = JSONObject()
    for ((id, settings) in this.stems) {
      stems.put(
        id.id,
        JSONObject()
          .put("gainDb", settings.gainDb)
          .put("pan", settings.pan)
          .put("muted", settings.muted)
          .put("soloed", settings.soloed)
          .put("eq", bandsToJson(settings.eqBands))
          .put("trimStartSec", settings.trimStartSec ?: JSONObject.NULL)
          .put("trimEndSec", settings.trimEndSec ?: JSONObject.NULL)
          .put("loop", settings.loopEnabled),
      )
    }
    root.put("stems", stems)
    root.put(
      "master",
      JSONObject()
        .put("eq", bandsToJson(master.eqBands))
        .put("masterGainDb", master.masterGainDb)
        .put("speed", master.speed)
        .put("pitch", master.pitch)
        .put("originalOnly", master.originalOnly)
        .put("trimStartSec", master.trimStartSec)
        .put("trimEndSec", master.trimEndSec ?: JSONObject.NULL)
        .put("loop", master.loopEnabled),
    )
    val presets = JSONObject()
    for ((k, v) in savedPresets) {
      presets.put(k, JSONObject().put("stemId", v.stemId).put("bands", bandsToJson(v.bands)))
    }
    root.put("savedPresets", presets)
    root.put(
      "parity",
      JSONObject()
        .put("mode", practiceMode.name)
        .put("transposeSemitones", transposeSemitones)
        .put(
          "click",
          JSONObject()
            .put("enabled", click.enabled)
            .put("bpm", click.bpm)
            .put("beatsPerBar", click.beatsPerBar)
            .put("countInBars", click.countInBars)
            .put("volume", click.volume),
        )
        .put("loudness", loudness.name),
    )
    return root
  }

  companion object {
    fun fromJson(obj: JSONObject): StemMixerSnapshot {
      val stemsJson = obj.optJSONObject("stems") ?: JSONObject()
      val stems = LinkedHashMap<StemId, StemMixSettings>()
      for (id in StemId.entries) {
        val s = stemsJson.optJSONObject(id.id)
        stems[id] = if (s == null) {
          StemMixSettings(id)
        } else {
          StemMixSettings(
            stemId = id,
            gainDb = s.optDouble("gainDb", 0.0).toFloat(),
            pan = s.optDouble("pan", 0.0).toFloat(),
            muted = s.optBoolean("muted"),
            soloed = s.optBoolean("soloed"),
            eqBands = parseBands(s.optJSONArray("eq")) ?: StemMixPresets.flat().eqBands,
            trimStartSec = optFloatOrNull(s, "trimStartSec"),
            trimEndSec = optFloatOrNull(s, "trimEndSec"),
            loopEnabled = s.optBoolean("loop"),
          )
        }
      }
      val masterJson = obj.optJSONObject("master") ?: JSONObject()
      val master = MasterMixSettings(
        eqBands = parseBands(masterJson.optJSONArray("eq")) ?: StemMixPresets.flat().eqBands,
        masterGainDb = masterJson.optDouble("masterGainDb", 0.0).toFloat(),
        speed = masterJson.optDouble("speed", 1.0).toFloat(),
        pitch = masterJson.optDouble("pitch", 1.0).toFloat(),
        originalOnly = masterJson.optBoolean("originalOnly"),
        trimStartSec = masterJson.optDouble("trimStartSec", 0.0).toFloat(),
        trimEndSec = optFloatOrNull(masterJson, "trimEndSec"),
        loopEnabled = masterJson.optBoolean("loop"),
      )
      val presetsJson = obj.optJSONObject("savedPresets") ?: JSONObject()
      val presets = LinkedHashMap<String, SavedPreset>()
      val keys = presetsJson.keys()
      while (keys.hasNext()) {
        val k = keys.next()
        val v = presetsJson.optJSONObject(k) ?: continue
        presets[k] = SavedPreset(v.optString("stemId"), parseBands(v.optJSONArray("bands")) ?: emptyList())
      }
      val parity = obj.optJSONObject("parity")
      val clickJson = parity?.optJSONObject("click")
      return StemMixerSnapshot(
        stems, master, presets, obj.optLong("savedAt", 0L),
        PracticeMode.from(parity?.optString("mode")),
        (parity?.optInt("transposeSemitones", 0) ?: 0).coerceIn(-12, 12),
        if (clickJson == null) ClickSettings() else ClickSettings(
          enabled = clickJson.optBoolean("enabled"),
          bpm = clickJson.optInt("bpm", 120),
          beatsPerBar = clickJson.optInt("beatsPerBar", 4),
          countInBars = clickJson.optInt("countInBars", 1),
          volume = clickJson.optDouble("volume", 0.5).toFloat(),
        ),
        LoudnessTarget.from(parity?.optString("loudness")),
      )
    }

    private fun bandsToJson(bands: List<StemEqBand>): JSONArray {
      val arr = JSONArray()
      for (band in bands) {
        arr.put(
          JSONObject()
            .put("frequency", band.frequency)
            .put("gainDb", band.gainDb)
            .put("type", band.type.name)
            .put("enabled", band.enabled)
            .put("q", band.q),
        )
      }
      return arr
    }

    private fun parseBands(arr: JSONArray?): List<StemEqBand>? {
      if (arr == null) return null
      val out = ArrayList<StemEqBand>(arr.length())
      for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        out.add(
          StemEqBand(
            frequency = o.optDouble("frequency", 1000.0),
            gainDb = o.optDouble("gainDb", 0.0),
            type = runCatching { echo.music.dsp.core.models.FilterType.valueOf(o.optString("type", "PK")) }.getOrNull()
              ?: echo.music.dsp.core.models.FilterType.PK,
            enabled = o.optBoolean("enabled", true),
            q = o.optDouble("q", 1.41).coerceIn(0.5, 10.0),
          ),
        )
      }
      return out.ifEmpty { null }
    }

    private fun optFloatOrNull(o: JSONObject, key: String): Float? {
      if (!o.has(key) || o.isNull(key)) return null
      return o.optDouble(key, 0.0).toFloat()
    }
  }
}
