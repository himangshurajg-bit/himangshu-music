"""Job lifecycle tests with auth disabled (local testing)."""
import io
import wave
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from rajtune_stem_cloud.app import create_app


def make_wav_bytes(seconds: float = 0.5, sr: int = 44100) -> bytes:
    buf = io.BytesIO()
    with wave.open(buf, "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(sr)
        frames = int(seconds * sr)
        import struct
        w.writeframes(b"".join(struct.pack("<hh", 1000, 1000) for _ in range(frames)))
    return buf.getvalue()


@pytest.fixture
def client(tmp_path: Path):
    app = create_app(disable_auth=True, jobs_dir=tmp_path / "jobs", models_dir=tmp_path / "models", background=False)
    return TestClient(app)


def test_create_job_and_status(client):
    r = client.post("/v1/stem-jobs", files={"audio": ("song.wav", make_wav_bytes(), "audio/wav")})
    assert r.status_code == 202, r.text
    body = r.json()
    assert "job_id" in body and body["state"] in ("queued", "processing", "failed", "complete")
    jid = body["job_id"]
    r2 = client.get(f"/v1/stem-jobs/{jid}")
    assert r2.status_code == 200
    assert r2.json()["job_id"] == jid


def test_reject_bad_extension(client):
    r = client.post("/v1/stem-jobs", files={"audio": ("evil.exe", b"data", "application/octet-stream")})
    assert r.status_code in (415, 422), r.text


def test_cancel_and_delete(client):
    r = client.post("/v1/stem-jobs", files={"audio": ("song.wav", make_wav_bytes(), "audio/wav")})
    jid = r.json()["job_id"]
    c = client.post(f"/v1/stem-jobs/{jid}/cancel")
    assert c.status_code in (200, 202, 409), c.text
    # unknown job
    assert client.get("/v1/stem-jobs/doesnotexist").status_code == 404
    d = client.delete(f"/v1/stem-jobs/{jid}")
    assert d.status_code == 200, d.text
    assert client.get(f"/v1/stem-jobs/{jid}").status_code == 404


def test_stem_download_guards(client):
    r = client.post("/v1/stem-jobs", files={"audio": ("song.wav", make_wav_bytes(), "audio/wav")})
    jid = r.json()["job_id"]
    # not complete -> 409, unknown stem -> 404
    assert client.get(f"/v1/stem-jobs/{jid}/stems/vocals").status_code in (409, 404)
    assert client.get(f"/v1/stem-jobs/{jid}/stems/unknown").status_code == 404


def test_health_when_disabled(client):
    h = client.get("/healthz")
    assert h.json()["status"] == "ok"
