package echo.music.iad1tya.stems

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StemDeckAdvancedTest {

  @Test
  fun tapTempoMedianAndClamp() {
    // 4 taps 500ms apart => 120 BPM
    assertEquals(120, TapTempoCalculator.bpmFromTaps(listOf(0L, 500L, 1000L, 1500L)))
    // Too few taps
    assertNull(TapTempoCalculator.bpmFromTaps(listOf(0L)))
    assertNull(TapTempoCalculator.bpmFromTaps(emptyList()))
    // Intervals outside 250..1500 ignored; one valid interval left
    assertEquals(120, TapTempoCalculator.bpmFromTaps(listOf(0L, 100L, 600L)))
    // 250ms interval => exactly 240 BPM (max)
    assertEquals(240, TapTempoCalculator.bpmFromTaps(listOf(0L, 250L, 500L, 750L)))
    // Faster than 250ms ignored (above max)
    assertNull(TapTempoCalculator.bpmFromTaps(listOf(0L, 200L, 400L, 600L)))
  }

  @Test
  fun tapTempoRespectsWindowAndMaxTaps() {
    // Taps older than 3000ms window dropped; only recent taps count
    val now = 10000L
    assertEquals(120, TapTempoCalculator.bpmFromTaps(listOf(0L, 9000L, 9500L, 10000L), nowMs = now))
    // More than 8 taps: only last 8 in window used
    val many = (0..12).map { it * 500L }
    assertEquals(120, TapTempoCalculator.bpmFromTaps(many, nowMs = many.last()))
  }

  @Test
  fun liveBeatClockBeatsAndDownbeats() {
    val settings = ClickSettings(enabled = true, bpm = 120, beatsPerBar = 4) // 0.5s per beat
    val s0 = LiveBeatClock.stateAt(settings, 0f)
    assertEquals(0L, s0.beatIndex)
    assertEquals(1, s0.beatInBar)
    assertTrue(s0.isDownbeat)
    assertEquals(0f, s0.positionInBeat, 0.001f)

    val s1 = LiveBeatClock.stateAt(settings, 0.5f)
    assertEquals(1L, s1.beatIndex)
    assertEquals(2, s1.beatInBar)
    assertFalse(s1.isDownbeat)

    val s4 = LiveBeatClock.stateAt(settings, 2.0f)
    assertEquals(4L, s4.beatIndex)
    assertEquals(1, s4.beatInBar)
    assertTrue(s4.isDownbeat)

    val mid = LiveBeatClock.stateAt(settings, 0.25f)
    assertEquals(0.5f, mid.positionInBeat, 0.001f)
    assertEquals(0.25, mid.secondsToNextBeat, 0.001)
  }

  @Test
  fun liveBeatClockRespectsLoopStart() {
    val settings = ClickSettings(bpm = 120, beatsPerBar = 4)
    val s = LiveBeatClock.stateAt(settings, 2.5f, loopStartSec = 2.0f)
    // 0.5s after loop start => beat 1 again
    assertEquals(1L, s.beatIndex)
    assertEquals(2, s.beatInBar)
  }

  @Test
  fun loopRangeNormalizedAndValid() {
    val r = LoopRangeLogic.normalized(startSec = -5f, endSec = 10f, durationSec = 60f)
    assertEquals(0f, r.startSec, 0.001f)
    assertEquals(10f, r.endSec, 0.001f)
    assertTrue(r.isValid)
    assertEquals(10f, r.durationSec, 0.001f)
    assertTrue(r.contains(5f))
    assertFalse(r.contains(10f))
    // End before start clamps to start
    val r2 = LoopRangeLogic.normalized(startSec = 30f, endSec = 10f, durationSec = 60f)
    assertEquals(30f, r2.startSec, 0.001f)
    assertEquals(30f, r2.endSec, 0.001f)
    assertFalse(r2.isValid)
    // Null end = open loop
    val r3 = LoopRangeLogic.normalized(startSec = 0f, endSec = null, durationSec = 60f)
    assertNull(r3.durationSec)
    assertFalse(r3.isValid)
  }

  @Test
  fun queuePickNormalized() {
    assertEquals(listOf("vocals", "bass"), QueueLogic.normalizePick(listOf("VOCALS", "bass", "guitar", "bass", "")))
    // Empty/invalid falls back to default 4
    assertEquals(QueueLogic.defaultPick, QueueLogic.normalizePick(emptyList()))
    assertEquals(QueueLogic.defaultPick, QueueLogic.normalizePick(listOf("unknown")))
    assertEquals("Vocals, Drums", QueueLogic.label(listOf("vocals", "drums")))
    val item = StemQueueItem(uriString = "content://a", name = "Song", pickedStemIds = listOf("bass"))
    assertEquals(listOf(StemId.Bass), item.pickedStems)
  }

  @Test
  fun libraryVisibleTrashFavorite() {
    val entries = listOf(
      StemLibraryEntry(displayName = "A.wav", favorite = false),
      StemLibraryEntry(displayName = "B.wav", favorite = true),
      StemLibraryEntry(displayName = "C.wav", favorite = false, trashed = true),
    )
    assertEquals(listOf("A.wav", "B.wav"), LibraryLogic.visible(entries).map { it.displayName })
    assertEquals(listOf("B.wav"), LibraryLogic.visible(entries, favoritesOnly = true).map { it.displayName })
    val trashed = LibraryLogic.trashByName(entries, "A.wav")
    assertEquals(listOf("B.wav"), LibraryLogic.visible(trashed).map { it.displayName })
    val fav = LibraryLogic.toggleFavorite(entries, "A.wav")
    assertTrue(fav.first { it.displayName == "A.wav" }.favorite)
    val unfav = LibraryLogic.toggleFavorite(fav, "A.wav")
    assertFalse(unfav.first { it.displayName == "A.wav" }.favorite)
  }

  @Test
  fun songMetadataReadoutAndKeys() {
    val meta = SongMetadata(key = "Am", tempoBpm = 120, loudnessLabel = "Streaming −14 LUFS", durationSec = 180f)
    val readout = meta.readout()
    assertTrue(readout.contains("180.0s") || readout.contains("180"))
    assertTrue(readout.contains("Am"))
    assertTrue(readout.contains("120"))
    assertTrue(MetadataLogic.commonKeys.contains("C"))
    assertTrue(MetadataLogic.commonKeys.contains("Am"))
    assertEquals("—", MetadataLogic.formatBpm(null))
    assertEquals("128", MetadataLogic.formatBpm(128))
  }

  @Test
  fun videoQualityFrom() {
    assertEquals(VideoExportQuality.HD720, VideoExportQuality.from(null))
    assertEquals(VideoExportQuality.HD1080, VideoExportQuality.from("hd1080"))
    assertEquals(VideoExportQuality.HD720, VideoExportQuality.from("unknown"))
    assertEquals(1280, VideoExportQuality.HD720.width)
    assertEquals(720, VideoExportQuality.HD720.height)
    assertEquals(1920, VideoExportQuality.HD1080.width)
    assertEquals(1080, VideoExportQuality.HD1080.height)
  }

  @Test
  fun advancedSnapshotRoundTrip() {
    val original = StemMixerSnapshot(
      markers = listOf(
        StemMarker(id = "m1", timeSec = 12.5f, label = "Marker 1 12.5s"),
        StemMarker(id = "m2", timeSec = 45f, label = "Marker 2 45.0s"),
      ),
      library = listOf(
        StemLibraryEntry(displayName = "Mix.wav", uriString = "content://m", kind = "mix", favorite = true, tags = listOf("fav")),
      ),
      queue = listOf(
        StemQueueItem(uriString = "content://song", name = "Song", pickedStemIds = listOf("vocals", "bass")),
      ),
      metronomeLive = true,
      manualKey = "Am",
      manualTempo = 120,
      videoQuality = VideoExportQuality.HD1080,
      stemPick = listOf("vocals", "bass"),
    )
    val restored = StemMixerSnapshot.fromJson(original.toJson())
    assertEquals(2, restored.markers.size)
    assertEquals("m1", restored.markers[0].id)
    assertEquals(12.5f, restored.markers[0].timeSec, 0.001f)
    assertEquals(1, restored.library.size)
    assertTrue(restored.library[0].favorite)
    assertEquals(listOf("fav"), restored.library[0].tags)
    assertEquals(1, restored.queue.size)
    assertEquals("Song", restored.queue[0].name)
    assertEquals(listOf("vocals", "bass"), restored.queue[0].pickedStemIds)
    assertTrue(restored.metronomeLive)
    assertEquals("Am", restored.manualKey)
    assertEquals(120, restored.manualTempo)
    assertEquals(VideoExportQuality.HD1080, restored.videoQuality)
    assertEquals(listOf("vocals", "bass"), restored.stemPick)
  }

  @Test
  fun legacySnapshotWithoutAdvancedStillLoads() {
    val json = StemMixerSnapshot().toJson()
    json.remove("parity")
    val restored = StemMixerSnapshot.fromJson(json)
    assertTrue(restored.markers.isEmpty())
    assertTrue(restored.library.isEmpty())
    assertTrue(restored.queue.isEmpty())
    assertFalse(restored.metronomeLive)
    assertEquals("", restored.manualKey)
    assertNull(restored.manualTempo)
    assertEquals(VideoExportQuality.HD720, restored.videoQuality)
    assertEquals(listOf("vocals", "drums", "bass", "other"), restored.stemPick)
  }
}
