# RajTune cloud stem separation — integration design

Status: design only. No service provider, account, endpoint, deployment, or paid plan has been selected or approved. This document defines the smallest safe integration boundary while preserving the existing downloadable offline mode.

## Recommendation

Keep the existing on-device KUIELab MDX-Net ONNX path for offline use. For online use, build a separate RajTune-owned HTTPS API and GPU worker around the same KUIELab checkpoints, subject to verifying the current Zenodo CC BY 4.0 attribution and model artifacts at release time. Use StemDeck only as a behavior/API reference: do not copy its AGPL-3.0-only source or expose its documented internal, unauthenticated API. Do not use Demucs pretrained weights unless their rights are clarified.

Keep the Android feature independent of any hosting vendor. Bind the cloud client to a configured HTTPS base URL and a RajTune-issued, short-lived user token from the app's established identity system. If RajTune has no suitable user identity/token issuer, that is a deployment prerequisite; do not substitute a shared embedded API key or anonymous public access.

## Proposed API v1

All routes require TLS and an authenticated principal. Every job and download is scoped to that principal; a guessed job ID must not reveal another user's existence, status, or output.

| Route | Purpose |
|---|---|
| `POST /v1/stem-jobs` | Multipart audio upload and job creation. Accept only supported audio files, inspect actual file signatures, cap upload bytes and decoded duration, and reject malformed media. Return `202` with an opaque `job_id`, state `queued`, and a status URL. Require an `Idempotency-Key` so a client retry cannot create duplicate jobs. |
| `GET /v1/stem-jobs/{job_id}` | Read this owner's job state and progress (`queued`, `processing`, `complete`, `failed`, `cancelled`), with bounded progress and a stable machine-readable error on failure. |
| `GET /v1/stem-jobs/{job_id}/events` | Optional Server-Sent Events progress stream; `GET` status polling remains the fallback for mobile networks. Events contain no source audio, signed URLs, or other users' data. |
| `GET /v1/stem-jobs/{job_id}/stems/{stem}` | Download one completed WAV (`vocals`, `drums`, `bass`, `other`) after rechecking ownership and completion. Use short-lived authorized streaming or a narrowly scoped short-lived download URL; never a public object URL. |
| `DELETE /v1/stem-jobs/{job_id}` | Cancel queued work where possible and request deletion of uploaded audio and results. A completed job remains subject to the automatic retention deadline even if cancellation is no longer possible. |

Use JSON errors with `code`, user-safe `message`, and `retryable`; never return stack traces or filesystem/object-store paths. Return `429` with `Retry-After` for per-user quotas; enforce a global concurrency ceiling to protect spend.

## Server shape

1. HTTPS API/gateway validates short-lived RajTune identity tokens and rate limits requests.
2. Upload handler streams into private, encrypted, temporary object storage; validates size, media signature and duration without trusting filename/declared MIME; generates an unguessable job ID; stores owner, state, quota and expiry metadata separately from media.
3. A bounded queue dispatches jobs to isolated GPU workers. Workers use only licensed KUIELab weights and a pinned, reproducible inference implementation; they do not run StemDeck or arbitrary user code. Validate all four outputs, sample rate, channel count, duration and finite samples before marking complete.
4. Results are stored privately and served only through the ownership-checked API. Enforce short automatic retention for input and output, immediate deletion when requested, and cleanup for abandoned/failed jobs.
5. Operational telemetry contains timings, byte counts, state and non-sensitive error codes only. Do not log audio, filenames, authorization tokens, signed URLs, or audio-derived metadata. Add per-user and global request, duration, storage and GPU-runtime budgets with alerts and a hard stop.

## Android client behavior

Keep `Offline` and `Cloud` as explicit modes. Offline continues to download verified weights in-app and processes on-device. Cloud mode must explain that the selected audio is uploaded for server processing, show applicable size/duration limits and retention, then start only after the user selects Cloud and confirms the upload. Provide progress, cancel, retry with the same idempotency key after an uncertain network result, and WAV preview/export using the app's existing audio pathways. Do not silently fall back from offline to cloud or upload a track to recover an on-device error. Store no long-lived service secret in the APK.

## Required pre-deployment decisions and checks

- Identify the existing RajTune account/authentication mechanism and confirm that the Android client can obtain scoped short-lived tokens; otherwise design that identity layer before exposing the API.
- Select a GPU host/region only after comparing cold-start behavior, supported GPU/runtime, persistent-storage charges, egress, idle/minimum billing, data location and account controls. The preliminary RunPod Serverless figure researched for this task was about `$0.58/GPU-hour` equivalent, not a per-song quote; actual per-song cost depends on model runtime, cold starts, storage and transfer. No provider is selected and no recurring budget is approved.
- Establish upload size/duration, per-user quota, queue/concurrency, retention and deletion commitments from measured real workload and provider limits.
- Verify rights and attribution for the exact model weights and inference code; keep license notices with both app and server distribution.
- Add API contract tests for auth/ownership isolation, wrong-user job IDs/downloads, limits, idempotent retries, cancellation, retention/deletion, corrupt/oversized media, all four output formats, timeout/disconnect and quota exhaustion.
- Test the complete Android online round trip against a staging endpoint before any public release; separately run real-song listening/quality evaluation and physical Android performance validation for offline mode. Neither is currently established.

## Local backend scaffold status

A fail-closed, vendor-neutral Python/FastAPI scaffold now lives in `services/stem-cloud/`. It validates short-lived RS256 OIDC bearer tokens against a configured HTTPS issuer, audience and JWKS endpoint; all cloud job creation remains unavailable (503) until durable storage, quotas and a licensed processing worker are implemented. Its health response deliberately reports `stem_processing_ready: false`. Tests use generated ephemeral keys and a fake JWKS resolver; no provider or external identity service is contacted.

The RajTune source review found no RajTune-owned token issuer or Firebase/OIDC auth integration. Its YouTube account cookies and Discord/Spotify sessions are not a suitable RajTune service identity, so the scaffold does not reuse them. The Android feature therefore remains offline-only; no Cloud mode button or silent upload path was added.

## Explicitly not done

This is not a live endpoint, deployment, cloud account, selected vendor, budget approval, production auth implementation, or verified online processing. The current Android feature remains local and uncommitted on `feature/rajtune-stem-splitter-2026-11-26`.
