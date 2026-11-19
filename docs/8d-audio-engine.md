# 8D stereo audio engine

## Architecture

Himangshu Music is a native Kotlin/Jetpack Compose Android app, so this feature uses its existing Media3 playback path rather than adding a Flutter bridge, a second player, Oboe or C++. `MusicService` creates one `EightDAudioProcessor` for each ExoPlayer instance and includes it in the existing `DefaultAudioSink` processor chain. The service observes the audio preferences and publishes an immutable parameter snapshot; the processor reads it on the audio thread. The player UI and normal media-session controls remain on the existing player.

`EightDAudioProcessor` accepts interleaved stereo PCM16 and PCM float. It uses mid/side width control and equal-power pan gains, then smooths pan and control values over short intervals. Slow, fast, reverse, wave, random, static and custom-sine modes select the motion trajectory. Bass enhancement is a low-frequency shelf derived from a one-pole low-pass state. Optional short ambience and echo are parallel feedback delay lines; the direct path has no added block delay. A soft knee limits peaks above the output ceiling. The toggle-off path copies PCM bytes directly, bypassing every 8D effect.

## What this does and does not mean

The engine changes the played stereo samples; its moving dot is only a visual preview. Stereo panning creates left/right motion, not physical 360-degree localization. There is no HRTF, binaural renderer, waveform analyzer, head tracking, or arbitrary user-drawn motion curve. “Custom Motion” is a sine orbit adjusted with speed, depth and direction.

The settings screen reports wired, Bluetooth and USB headset-type outputs when Android exposes them. That is device-type detection only: it does not prove that the selected route is stereo, test left/right channels, or establish compatibility. Hardware headphone/audio-device tests remain necessary. Multichannel input is rejected by this processor so Media3 can select its normal supported path; mono cannot be panned into stereo. The processor is in Media3's normal sink chain; the separate USB bit-perfect sink is not validated for 8D processing, and bit-perfect/offload routing may bypass app DSP.

This implementation is Android/Kotlin-specific and does not add native Oboe/AAudio or C++ code. The controls and settings are local app preferences.
## Repeatable verification

1. Run `./gradlew :audio-dsp:testDebugUnitTest --no-daemon --console=plain`. `EightDAudioProcessorTest` covers exact disabled PCM16 bypass, changing stereo samples in fast orbit, width control affecting the signal, float PCM clipping bounds, and safe multichannel rejection.
2. Run `./gradlew :app:compileUniversalGmsDebugKotlin --no-daemon --console=plain` to compile the app integration.
3. On a test phone, use stereo headphones and a stereo music source. Compare 8D off/on on the same passage; try each motion mode and move speed, depth, width, bass, ambience, echo and intensity sliders while audio is playing. Confirm playback, seek, queue, pause/resume, background playback and audio-route changes still work.
4. Listen for clicks when toggling, changing sliders, and switching modes. At high volume, check peak-heavy material for obvious distortion; test mono and multichannel material and verify they remain on the documented fallback path. Compare bypass against the original source and separately test wired, Bluetooth and USB outputs. Record devices, Android versions, route types, and findings before making compatibility claims.

## Verification status

Automated processor tests are included in the Android CI workflow, and app unit tests/build compile the integration. Check the current branch's CI result before treating this implementation as build-verified; an earlier branch's test result does not validate later source changes. No Android phone, wired/Bluetooth headphone, USB DAC or bit-perfect/offload route test is claimed.
