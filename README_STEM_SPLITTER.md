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

## What's verified vs. still needs a phone / host

- ✅ Cloud API auth + job lifecycle tests (14 passed) + real ONNX e2e: synthetic WAV → 4 WAV stems downloaded (see test report).
- ✅ Offline Kotlin model-extraction fix + 7-case harness (prior run); synthetic 2.4 s smoke test earlier.
- ⚠️ Android `./gradlew` on this machine: previously `-9` (OOM); low-memory `:app:compileUniversalGmsDebugKotlin -Xmx512m --no-daemon` retry was still running when this report was written (memory 148 MB free) — **no APK claimed until that command actually succeeds**. Use the command in "Offline Android build" above on a machine with more RAM.
- ⚠️ Real-song listening quality + physical-device performance: not yet evaluated.
- ⚠️ Hosted deployment: **not deployed, no provider/budget approved.** Choose a host + RajTune token issuer before any public use.
