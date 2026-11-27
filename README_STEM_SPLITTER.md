# RajTune Stem Splitter — Setup (Online + Offline)

## What this is

- **Offline (default, in APK):** Android app downloads KUIELab MDX-Net B ONNX models (CC BY 4.0) on first use from Zenodo, verifies MD5/SHA-256, then separates on-device. Models are **not** bundled in the APK. After download it works fully offline.
- **Cloud (opt-in):** `services/stem-cloud/` — authenticated FastAPI service that uploads audio, runs the **same** KUIELab ONNX models on the server, and returns 4 WAV stems. Android Cloud mode is explicit: no endpoint = no upload.

No StemDeck AGPL code, no Demucs weights, no provider accounts created by the app.

## Local cloud test (no deployment, no cost)

```bash
# 1. deps
cd services/stem-cloud
uv sync --extra test

# 2. tests (auth + jobs)
.venv/bin/python -m pytest -q tests
# expected: 14 passed

# 3. run server for local testing (auth disabled)
export PYTHONPATH=src STEM_STEM_CLOUD_DISABLE_AUTH=true RAJTUNE_JOBS_DIR=/tmp/cj RAJTUNE_MODELS_DIR=/tmp/cm
.venv/bin/python -m uvicorn rajtune_stem_cloud.app:app --port 8000

# 4. in another terminal:
curl -s http://127.0.0.1:8000/healthz
# {"status":"ok","stem_processing_ready":true}

curl -s -F audio=@/path/to/song.wav http://127.0.0.1:8000/v1/stem-jobs
# {"job_id":"...","state":"processing",...}

curl -s http://127.0.0.1:8000/v1/stem-jobs/<job_id>
curl -s http://127.0.0.1:8000/v1/stem-jobs/<job_id>/stems/vocals -o vocals.wav
```

First cloud job downloads `onnx_B.zip` (~103 MB) from Zenodo once into `models/`, verifies MD5 `39eb4bb43137a6c209caecdbcfd1d8e2` and per-model SHA-256, then caches the 4 ONNX files (~106 MiB). Later jobs reuse the cache.

## Environment variables (server)

| Var | Default | Purpose |
|---|---|---|
| `STEM_STEM_CLOUD_DISABLE_AUTH` | (off) | `true` = local testing only; principal `local-test-user`. Never for public hosts. |
| `RAJTUNE_AUTH_ISSUER` | — | HTTPS OIDC issuer (required for real auth) |
| `RAJTUNE_AUTH_AUDIENCE` | — | JWT audience |
| `RAJTUNE_AUTH_JWKS_URL` | — | HTTPS JWKS URL (validated RS256) |
| `RAJTUNE_JOBS_DIR` | `jobs` | Job storage |
| `RAJTUNE_MODELS_DIR` | `models` | ONNX cache |
| `RAJTUNE_MAX_UPLOAD_BYTES` | 100 MB | Upload cap |
| `RAJTUNE_MAX_DURATION_SEC` | 1200 | Duration cap |
| `RAJTUNE_MAX_ACTIVE_PER_USER` | 2 | Concurrency quota |

With no `RAJTUNE_AUTH_*` and auth not disabled, `POST /v1/stem-jobs` returns `503 auth_unconfigured` (fail-closed — same as before).

## API

- `POST /v1/stem-jobs` — multipart `audio`, optional `Idempotency-Key` header → `202 {job_id, state}`.
- `GET /v1/stem-jobs/{id}` — status/progress (+ `stems`, `error`).
- `GET /v1/stem-jobs/{id}/events` — SSE progress stream (polling fallback also works).
- `POST /v1/stem-jobs/{id}/cancel` — cancel queued/processing job.
- `GET /v1/stem-jobs/{id}/stems/{vocals|drums|bass|other}` — WAV download (owner-scoped).
- `DELETE /v1/stem-jobs/{id}` — cancel + delete media.
- `GET /v1/stems` — list stems. `GET /healthz` — liveness + ready flag.

Errors: all routes return JSON `{code, message, retryable}`; `401` bad token, `404` unknown/not-owned (no existence leak), `409` not ready/terminal, `413` too large/long, `415` unsupported, `429` quota (`Retry-After`), `503` unconfigured/unavailable.

## Android endpoint config

In `StemSplitterScreen` → **Cloud processing** toggle:
- **Offline (default):** no upload ever. Models download from Zenodo in-app.
- **Cloud:** enter `Cloud endpoint` (must be `https://…`, or `http://127.0.0.1|10.|192.168.` for local testing) and optional Bearer token. Tap **Upload & separate (cloud)** only after confirming. Progress, cancel, and WAV preview via the existing Play buttons. When the endpoint is blank the UI shows **"Cloud not configured - Offline mode only"** and the cloud button is disabled — no silent upload.

`StemCloudClient.kt` handles multipart upload (WAV), job polling (2 s), stem download, and saving to `Music/RajTune Stems` via MediaStore. `StemCloud` has no hard-coded host/token in the APK.

## Offline Android build (low memory)

If Gradle is OOM-killed (`exit -9`), build without the daemon:

```bash
./gradlew --no-daemon -Dorg.gradle.jvmargs="-Xmx512m -Dfile.encoding=UTF-8" :app:assembleDebug
```

If it still exits `-9`, free memory / increase the builder machine — that is an environment limit, not a code error. Do not claim the APK compiles until that command actually succeeds.

## Licensing

- **RajTune app:** GPL-3.0 (see `LICENSE`).
- **Models (both paths):** KUIELab-MDX-Net (Kim et al. 2021), Zenodo record `6513062`, **CC BY 4.0**. Attribution shown on the splitter screen and in `MODEL_NOTICES.md`. Archive MD5 `39eb4bb43137a6c209caecdbcfd1d8e2` + per-file SHA-256 verified before use.
- **Cloud service:** same CC BY 4.0 models, fresh implementation. No AGPL code copied.

## Professional mixer + equalizer (v1.4.27)

- **Per-stem mixer (live + export):** vocals/bass/drums/other (and any re-run models) each get volume (-40..+12 dB), pan (constant-power), mute, solo, EQ and optional per-stem loop. Master bus gets its own 6-band EQ, master fader (-40..+12 dB), playback speed/pitch (0.5–2.0×), timeline trim/loop and A/B original compare.
- **Equalizer (offline only):** 6 bands at 63 Hz–12 kHz (low/high shelf + peaking) with per-band gain (-12..+12 dB) and Q (0.5–4.0, persisted). Presets: Flat, Vocal Boost, Bass Boost, Drum Punch, Warm, Bright, Club, Gentle Low Cut + save/restore named presets per stem.
- **Real audio paths:** live playback uses Media3 `StemGainPanAudioProcessor` (gain/pan) and the existing `CustomEqualizerAudioProcessor` biquad chain per stem. Export uses FFmpeg `volume`, `pan` (constant-power coefficients), `equalizer`, `amix`, `atempo`/`asetrate`, `atrim`. The same settings drive both.
- **Project/session save:** mixer state persists via DataStore (`stem_mixer`), auto-saving every change. Snapshot JSON includes per-stem gain/pan/mute/solo/EQ/trim, master EQ/speed/pitch/trim/loop, source mode and named presets.
- **Waveform/level:** each stem card shows computed WAV peaks with a playhead. Level % is the waveform peak of the file.
- **Export:** WAV (pcm_s16le) or MP3 (libmp3lame 320k/192k/128k). Saved to `Music/RajTune Stems`, shareable via the standard Android share sheet. Export applies per-stem + master EQ/gain/pan/trim/speed/pitch in the FFmpeg graph.
- **Scope (offline only):** the mixer/EQ/FFmpeg mix-down apply to on-device offline stems only. Cloud stems keep the existing simple Play list; the splitter screen shows an offline-mixer notice in Cloud mode and the mixer stays disabled there. No cloud/web EQ was added and the cloud worker is unchanged. No extra upload is performed by the mixer.

## What's verified vs. still needs a phone / host

- ✅ Cloud API auth + job lifecycle tests (14 passed) + real ONNX e2e: synthetic WAV → 4 WAV stems downloaded (see test report).
- ✅ Offline Kotlin model-extraction fix + 7-case harness (prior run); synthetic 2.4 s smoke test earlier.
- ⚠️ Android `./gradlew` on this machine: previously `-9` (OOM); low-memory `:app:compileUniversalGmsDebugKotlin -Xmx512m --no-daemon` retry was still running when this report was written (memory 148 MB free) — **no APK claimed until that command actually succeeds**. Use the command in "Offline Android build" above on a machine with more RAM.
- ⚠️ Real-song listening quality + physical-device performance: not yet evaluated.
- ⚠️ Hosted deployment: **not deployed, no provider/budget approved.** Choose a host + RajTune token issuer before any public use.
