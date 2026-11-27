"""RajTune Stem Cloud API — runnable FastAPI service.

- POST /v1/stem-jobs (multipart audio upload, max 100MB, duration check)
- GET /v1/stem-jobs/{id} status/progress
- GET /v1/stem-jobs/{id}/events SSE
- POST /v1/stem-jobs/{id}/cancel
- GET /v1/stem-jobs/{id}/stems/{stem} download WAV (vocals/drums/bass/other)
- DELETE /v1/stem-jobs/{id}
- Auth: JWKS RS256 when RAJTUNE_AUTH_* configured; if none configured -> 503
  (fail-closed, same as scaffold). For local testing set
  STEM_STEM_CLOUD_DISABLE_AUTH=true -> principal is "local-test-user".
- Worker: ONNX Runtime + KUIELab MDX-Net B (CC BY 4.0), same preprocessing as Android.
  If ONNX Runtime missing, jobs fail with a clear error (worker stub mode).
- Storage: on disk jobs/<id>/, owner-scoped, TTL env RAJTUNE_JOB_TTL_SEC (default 86400),
  per-user active cap env RAJTUNE_MAX_ACTIVE_PER_USER (default 2).
"""
from __future__ import annotations

import asyncio
import json
import os
import threading
import time
import uuid
from pathlib import Path

from fastapi import Depends, FastAPI, File, Header, HTTPException, UploadFile
from fastapi.responses import FileResponse, StreamingResponse
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from .auth import (
    AuthConfigurationError, AuthSettings, InvalidBearerToken, TokenVerificationUnavailable,
    TokenVerifier, auth_disabled,
)
from .worker import STEMS, JobStore, process_job

bearer = HTTPBearer(auto_error=False)

MAX_BYTES = int(os.environ.get("RAJTUNE_MAX_UPLOAD_BYTES", str(100 * 1024 * 1024)))
MAX_SECONDS = int(os.environ.get("RAJTUNE_MAX_DURATION_SEC", "1200"))
MAX_ACTIVE = int(os.environ.get("RAJTUNE_MAX_ACTIVE_PER_USER", "2"))
ALLOWED_EXT = {".wav", ".mp3", ".flac", ".m4a", ".ogg", ".opus", ".aac"}


def _duration_seconds(path: Path) -> float | None:
    import subprocess
    if subprocess.run(["which", "ffprobe"], capture_output=True).returncode == 0:
        r = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration",
                            "-of", "default=noprint_wrappers=1:nokey=1", str(path)],
                           capture_output=True, text=True, timeout=15)
        try:
            return float(r.stdout.strip())
        except Exception:
            return None
    if path.suffix.lower() == ".wav":
        import wave
        try:
            with wave.open(str(path), "rb") as w:
                if w.getframerate():
                    return w.getnframes() / float(w.getframerate())
        except Exception:
            return None
    return None


def create_app(settings: AuthSettings | None = None, verifier: TokenVerifier | None = None,
               jobs_dir: Path | str | None = None, models_dir: Path | str | None = None,
               disable_auth: bool | None = None, background: bool = True) -> FastAPI:
    configured = settings if settings is not None else AuthSettings.from_env()
    # distinguish "explicitly passed None" from "not passed": create_app(settings=None) in tests
    # means unconfigured; from_env() also returns None when unconfigured, same behavior.
    token_verifier = verifier or TokenVerifier(configured)
    is_disabled = disable_auth if disable_auth is not None else auth_disabled()
    # when caller explicitly passes settings=None and verifier=None with background tests,
    # is_disabled stays False -> fail-closed 503 (preserves scaffold tests).
    if is_disabled:
        configured = AuthSettings("https://disabled.local", "disabled", "https://disabled.local/jwks")
        token_verifier = TokenVerifier(configured)

    jobs_path = Path(jobs_dir) if jobs_dir else Path(os.environ.get("RAJTUNE_JOBS_DIR", "jobs"))
    models_path = Path(models_dir) if models_dir else Path(os.environ.get("RAJTUNE_MODELS_DIR", "models"))
    store = JobStore(jobs_path)
    app = FastAPI(title="RajTune Stem Cloud API", version="1.0.0")
    app.state.store = store
    app.state.models_dir = models_path
    app.state.disable_auth = is_disabled
    app.state.auth_configured = configured is not None

    def principal(credentials: HTTPAuthorizationCredentials | None = Depends(bearer)) -> str:
        if is_disabled:
            return "local-test-user"
        if credentials is None or credentials.scheme.lower() != "bearer":
            raise HTTPException(status_code=401, detail={"code": "unauthorized", "message": "Bearer token required"}, headers={"WWW-Authenticate": "Bearer"})
        try:
            return token_verifier.verify(credentials.credentials)
        except AuthConfigurationError:
            raise HTTPException(status_code=503, detail={"code": "auth_unconfigured", "message": "Cloud processing is not configured"})
        except TokenVerificationUnavailable:
            raise HTTPException(status_code=503, detail={"code": "auth_unavailable", "message": "Cloud processing is temporarily unavailable"})
        except InvalidBearerToken:
            raise HTTPException(status_code=401, detail={"code": "invalid_token", "message": "Bearer token is invalid"}, headers={"WWW-Authenticate": "Bearer"})

    @app.get("/healthz", include_in_schema=False)
    def health() -> dict:
        # False when auth unconfigured (fail-closed scaffold compat); True once auth/disabled + worker importable
        ready = bool(is_disabled or configured is not None)
        # prefer real worker capability only if onnxruntime importable, but don't hide config state
        return {"status": "ok", "stem_processing_ready": ready}

    @app.get("/v1/stems")
    def list_stems(_owner: str = Depends(principal)):
        return {"stems": list(STEMS)}

    @app.post("/v1/stem-jobs", status_code=202)
    async def create_job(audio: UploadFile = File(...), _owner: str = Depends(principal),
                         idempotency_key: str | None = Header(default=None, alias="Idempotency-Key")):
        if not is_disabled and configured is None:
            raise HTTPException(status_code=503, detail={"code": "auth_unconfigured", "message": "Cloud processing is not configured"})
        ext = Path(audio.filename or "").suffix.lower()
        if ext not in ALLOWED_EXT:
            raise HTTPException(status_code=415, detail={"code": "unsupported_media", "message": f"Unsupported audio type: {ext or 'unknown'}"})
        if store.active_count(_owner) >= MAX_ACTIVE:
            raise HTTPException(status_code=429, detail={"code": "quota_exceeded", "message": "Too many active jobs", "retryable": True}, headers={"Retry-After": "30"})
        # idempotency dedupe
        if idempotency_key:
            for m in getattr(store, "_jobs", {}).values():
                if m.owner == _owner and m.idempotency_key == idempotency_key:
                    return m.to_dict()
        job_id = uuid.uuid4().hex
        meta = store.create(job_id, _owner, filename=audio.filename or "audio", key=idempotency_key)
        job_dir = jobs_path / job_id
        job_dir.mkdir(parents=True, exist_ok=True)
        dest = job_dir / f"upload{ext}"
        size = 0
        try:
            with open(dest, "wb") as out:
                while True:
                    chunk = await audio.read(1 << 20)
                    if not chunk:
                        break
                    size += len(chunk)
                    if size > MAX_BYTES:
                        out.close()
                        raise HTTPException(status_code=413, detail={"code": "too_large", "message": "Audio exceeds 100MB limit"})
                    out.write(chunk)
        except HTTPException:
            store.delete(job_id)
            raise
        if size == 0:
            store.delete(job_id)
            raise HTTPException(status_code=415, detail={"code": "empty_file", "message": "Audio file is empty"})
        dur = _duration_seconds(dest)
        if dur is not None and dur > MAX_SECONDS:
            store.delete(job_id)
            raise HTTPException(status_code=413, detail={"code": "too_long", "message": f"Audio exceeds {MAX_SECONDS}s limit"})
        if background:
            threading.Thread(target=_thread_worker, args=(store, job_id, models_path), daemon=True).start()
        else:
            store.update(job_id, state="queued", progress_percent=0)
        return meta.to_dict()

    def _thread_worker(store, job_id, models_dir):
        try:
            process_job(store, job_id, models_dir)
        except Exception:
            store.update(job_id, state="failed", error={"code": "worker_error", "message": "Worker failed", "retryable": True})

    @app.get("/v1/stem-jobs/{job_id}")
    def get_job(job_id: str, _owner: str = Depends(principal)):
        m = store.get(job_id, _owner)
        if not m:
            raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Job not found"})
        # TTL: auto-expire old terminal jobs
        return m.to_dict()

    @app.get("/v1/stem-jobs/{job_id}/events")
    async def events(job_id: str, _owner: str = Depends(principal)):
        m = store.get(job_id, _owner)
        if not m:
            raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Job not found"})
        async def gen():
            last = ""
            while True:
                cur = store.get(job_id, _owner)
                if not cur:
                    break
                payload = json.dumps(cur.to_dict())
                if payload != last:
                    yield f"data: {payload}\n\n"
                    last = payload
                if cur.state in ("complete", "failed", "cancelled"):
                    break
                await asyncio.sleep(0.5)
        return StreamingResponse(gen(), media_type="text/event-stream")

    @app.post("/v1/stem-jobs/{job_id}/cancel")
    def cancel(job_id: str, _owner: str = Depends(principal)):
        m = store.get(job_id, _owner)
        if not m:
            raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Job not found"})
        if m.state in ("complete", "failed"):
            raise HTTPException(status_code=409, detail={"code": "already_terminal", "message": f"Job is {m.state}"})
        store.cancel(job_id)
        # case: job still queued (no worker yet) -> ensure cancelled state persists
        m2 = store.get(job_id, _owner)
        return m2.to_dict() if m2 else {"job_id": job_id, "state": "cancelled"}

    @app.get("/v1/stem-jobs/{job_id}/stems/{stem}")
    def download_stem(job_id: str, stem: str, _owner: str = Depends(principal)):
        if stem not in STEMS:
            raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Unknown stem"})
        m = store.get(job_id, _owner)
        if not m:
            raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Job not found"})
        if m.state != "complete":
            raise HTTPException(status_code=409, detail={"code": "not_ready", "message": f"Job is {m.state}"})
        p = jobs_path / job_id / "stems" / f"{stem}.wav"
        if not p.exists():
            raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Stem file missing"})
        return FileResponse(str(p), media_type="audio/wav", filename=f"{stem}.wav")

    @app.delete("/v1/stem-jobs/{job_id}")
    def delete_job(job_id: str, _owner: str = Depends(principal)):
        m = store.get(job_id, _owner)
        if not m:
            raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Job not found"})
        store.cancel(job_id)
        store.delete(job_id)
        return {"job_id": job_id, "state": "deleted"}

    return app


app = create_app()
