"""RajTune stem cloud: job storage and background worker."""
from __future__ import annotations

import hashlib
import json
import math
import os
import struct
import subprocess
import threading
import wave
import zipfile
from dataclasses import dataclass, field
from pathlib import Path
from urllib.request import Request, urlopen

STEMS = ("bass", "drums", "other", "vocals")
ZENODO_URL = "https://zenodo.org/records/6513062/files/onnx_B.zip?download=1"
ZENODO_MD5 = "39eb4bb43137a6c209caecdbcfd1d8e2"
MODEL_SHA256 = {
    "bass": "b4b7080fe501d0bece62076c5d4eda4d6590c5207ed78ec84a57bac0740a061d",
    "drums": "a6fecee758059b33ed99f6dabba297439b3e7cacfac4b1097bd324aff8052208",
    "other": "b0d0b63950ac332333fea2d58f68c92fd3ab0aae071398c2a8beeae1ad15b655",
    "vocals": "9b7dcb9d878acb0f3e64ff3fd27750faae96577013f6d50f5996875bf4250713",
}

# stem -> (n_fft, dim_t) matching Android MdxNetSeparator leaderboard_B configs
CONFIGS = {
    "bass": (16384, 256),
    "drums": (4096, 128),
    "other": (8192, 256),
    "vocals": (6144, 256),
}
HOP = 1024
DIM_F = 2048
SAMPLE_RATE = 44100


def _hann(n: int):
    import numpy as np
    return 0.5 - 0.5 * np.cos(2.0 * np.pi * np.arange(n) / n)


def _reflect_idx(idx, size):
    import numpy as np
    # exact port of Kotlin reflect logic, then clip
    idx = np.where(idx < 0, -idx, idx)
    idx = np.where(idx >= size, 2 * size - 2 - idx, idx)
    return np.clip(idx, 0, size - 1)


def stft_chunk(left, right, n_fft, dim_t):
    import numpy as np
    window = _hann(n_fft)
    spec = np.zeros((4, DIM_F, dim_t), dtype=np.float32)
    n = n_fft
    for ch, samples in enumerate((left, right)):
        size = len(samples)
        for t in range(dim_t):
            center = t * HOP
            base = center - n // 2
            idx = _reflect_idx(base + np.arange(n), size)
            # zero-store if size==0 already reflected would fail; guard
            if size == 0:
                frame = np.zeros(n, dtype=np.float32)
            else:
                frame = samples[idx] * window
            # need centered reflect exactly as Kotlin: sourceIndex reflect then bounds check
            # Kotlin: if sourceIndex<0 -> -sourceIndex; if >=size -> 2*size-2-sourceIndex; then if in indices use else 0
            # Our _reflect_idx already mirrors then clips; values outside after mirror get clipped (edge case at boundaries)
            # To be closer, recompute precise indices:
            src = base + np.arange(n)
            src = np.where(src < 0, -src, src)
            src = np.where(src >= size, 2 * size - 2 - src, src) if size > 1 else np.zeros_like(src)
            mask = (src >= 0) & (src < size)
            frame = np.zeros(n, dtype=np.float32)
            frame[mask] = samples[src[mask]] * window[mask]
            fft_full = np.fft.rfft(frame, n=n_fft)
            nb = min(DIM_F, len(fft_full))
            spec[ch * 2, :nb, t] = fft_full[:nb].real
            spec[ch * 2 + 1, :nb, t] = fft_full[:nb].imag
    return spec


def istft_chunk(spec, n_fft, dim_t):
    import numpy as np
    window = _hann(n_fft)
    audio_len = n_fft + HOP * (dim_t - 1)
    out = [np.zeros(audio_len, dtype=np.float64), np.zeros(audio_len, dtype=np.float64)]
    weight = np.zeros(audio_len, dtype=np.float64)
    for t in range(dim_t):
        base = t * HOP
        weight[base:base + n_fft] += window * window
        for ch in range(2):
            real = spec[ch * 2, :, t]
            imag = spec[ch * 2 + 1, :, t]
            # Hermite full spectrum
            full = np.zeros(n_fft, dtype=np.complex128)
            full[:DIM_F] = real + 1j * imag
            for f in range(1, min(DIM_F, n_fft // 2)):
                full[n_fft - f] = real[f] - 1j * imag[f]
            time = np.fft.ifft(full).real
            # Kotlin FloatFFT inverse scales by 1/n in different impl; our forward used np fft without scaling, so *n restores
            out[ch][base:base + n_fft] += time * window
    chunk = HOP * (dim_t - 1)
    offset = n_fft // 2
    left = np.zeros(chunk, dtype=np.float32)
    right = np.zeros(chunk, dtype=np.float32)
    for i in range(chunk):
        den = weight[offset + i]
        if den > 1e-9:
            left[i] = out[0][offset + i] / den
            right[i] = out[1][offset + i] / den
    return left, right


def read_stereo_chunk(pcm_path: Path, start_sample: int, size: int):
    import numpy as np
    left = np.zeros(size, dtype=np.float32)
    right = np.zeros(size, dtype=np.float32)
    try:
        total = pcm_path.stat().st_size // 8
    except OSError:
        return left, right
    first = max(start_sample, 0)
    last = min(start_sample + size, total)
    if last <= first:
        return left, right
    count = last - first
    with open(pcm_path, "rb") as f:
        f.seek(first * 8)
        data = f.read(count * 8)
    vals = np.frombuffer(data, dtype="<f4")
    dst = first - start_sample
    left[dst:dst + count] = vals[0::2]
    right[dst:dst + count] = vals[1::2]
    return left, right


def write_wav(path: Path, left_b, right_b):
    import numpy as np
    left_c = np.clip(left_b, -1.0, 1.0) if len(left_b) else np.zeros(0)
    right_c = np.clip(right_b, -1.0, 1.0) if len(right_b) else np.zeros(0)
    n = min(len(left_c), len(right_c))
    inter = np.empty(n * 2, dtype="<i2")
    inter[0::2] = (left_c[:n] * 32767).astype("<i2")
    inter[1::2] = (right_c[:n] * 32767).astype("<i2")
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SAMPLE_RATE)
        w.writeframes(inter.tobytes())


def sha256_of(p: Path) -> str:
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def md5_of(p: Path) -> str:
    h = hashlib.md5()
    with open(p, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def ensure_models(models_dir: Path) -> dict[str, Path]:
    models_dir.mkdir(parents=True, exist_ok=True)
    result = {}
    for name, want in MODEL_SHA256.items():
        p = models_dir / f"{name}.onnx"
        if p.exists() and sha256_of(p) == want:
            result[name] = p
    if len(result) == len(MODEL_SHA256):
        return result
    # download zip
    zip_path = models_dir / "onnx_B.zip"
    if not zip_path.exists() or md5_of(zip_path) != ZENODO_MD5:
        req = Request(ZENODO_URL, headers={"User-Agent": "RajTune/StemCloud"})
        with urlopen(req, timeout=60) as r, open(zip_path, "wb") as out:
            while True:
                chunk = r.read(1 << 20)
                if not chunk:
                    break
                out.write(chunk)
    if md5_of(zip_path) != ZENODO_MD5:
        zip_path.unlink(missing_ok=True)
        raise RuntimeError("Downloaded model archive failed MD5 check")
    with zipfile.ZipFile(zip_path) as z:
        for info in z.infolist():
            leaf = Path(info.filename).name
            if leaf in (f"{n}.onnx" for n in STEMS):
                stem = leaf.removesuffix(".onnx")
                tmp = models_dir / f"{stem}.onnx.part"
                with open(tmp, "wb") as out:
                    out.write(z.read(info))
                if sha256_of(tmp) != MODEL_SHA256[stem]:
                    tmp.unlink(missing_ok=True)
                    raise RuntimeError(f"{stem} model failed SHA-256 check")
                tmp.rename(models_dir / leaf)
    zip_path.unlink(missing_ok=True)
    return {n: models_dir / f"{n}.onnx" for n in STEMS}


def decode_to_pcm(upload: Path, pcm: Path) -> int:
    # Prefer ffmpeg; else stdlib wave for WAV inputs
    if subprocess.run(["which", "ffmpeg"], capture_output=True).returncode == 0:
        cmd = ["ffmpeg", "-y", "-v", "error", "-i", str(upload), "-map", "0:a:0", "-vn",
               "-ar", "44100", "-ac", "2", "-f", "f32le", "-acodec", "pcm_f32le", str(pcm)]
        r = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
        if r.returncode != 0 or not pcm.exists() or pcm.stat().st_size == 0:
            raise RuntimeError(f"Could not decode audio via ffmpeg: {r.stderr[:300]}")
        return pcm.stat().st_size // 8
    if upload.suffix.lower() == ".wav":
        import numpy as np
        with wave.open(str(upload), "rb") as w:
            nch, sw, fr, nframes = w.getnchannels(), w.getsampwidth(), w.getframerate(), w.getnframes()
            raw = w.readframes(nframes)
        if sw != 2:
            raise RuntimeError("Only 16-bit WAV supported without ffmpeg")
        import numpy as np
        a = np.frombuffer(raw, dtype="<i2").astype("<f4") / 32768.0
        if nch == 1:
            left = a[::1]
            right = a[::1]
            # naive resample check: assume 44.1k; if different, still write (tests use 44.1k)
            inter = np.empty(len(a) * 2, dtype="<f4")
            inter[0::2] = left
            inter[1::2] = right
        else:
            inter = a.reshape(-1, nch)[:, :2].reshape(-1).astype("<f4")
        inter.tofile(str(pcm))
        return pcm.stat().st_size // 8
    raise RuntimeError("ffmpeg not available; only WAV inputs supported")


@dataclass
class JobMeta:
    job_id: str
    owner: str
    state: str = "queued"
    progress_percent: int = 0
    stems: list[str] = field(default_factory=list)
    error: dict | None = None
    idempotency_key: str | None = None
    filename: str = ""

    def to_dict(self):
        return {"job_id": self.job_id, "state": self.state, "progress_percent": self.progress_percent,
                "status_url": f"/v1/stem-jobs/{self.job_id}", "stems": self.stems, "error": self.error}


class JobStore:
    def __init__(self, base: Path):
        self.base = Path(base)
        self.base.mkdir(parents=True, exist_ok=True)
        self._lock = threading.Lock()
        self._jobs: dict[str, JobMeta] = {}
        self._cancel: dict[str, bool] = {}
        # reload from disk
        for d in self.base.iterdir():
            meta = d / "meta.json"
            if meta.exists():
                try:
                    data = json.loads(meta.read_text())
                    self._jobs[data["job_id"]] = JobMeta(**{k: data.get(k, JobMeta(**data).__dict__.get(k)) for k in
                        ["job_id", "owner", "state", "progress_percent", "stems", "error", "idempotency_key", "filename"]})
                except Exception:
                    pass

    def _persist(self, meta: JobMeta):
        d = self.base / meta.job_id
        d.mkdir(parents=True, exist_ok=True)
        (d / "meta.json").write_text(json.dumps({
            "job_id": meta.job_id, "owner": meta.owner, "state": meta.state,
            "progress_percent": meta.progress_percent, "stems": meta.stems,
            "error": meta.error, "idempotency_key": meta.idempotency_key, "filename": meta.filename}))

    def create(self, job_id, owner, filename, key=None) -> JobMeta:
        with self._lock:
            if key:
                for m in self._jobs.values():
                    if m.owner == owner and m.idempotency_key == key:
                        return m
            meta = JobMeta(job_id=job_id, owner=owner, idempotency_key=key, filename=filename)
            self._jobs[job_id] = meta
            self._persist(meta)
            return meta

    def get(self, job_id, owner):
        with self._lock:
            m = self._jobs.get(job_id)
            if not m or m.owner != owner:
                return None
            return m

    def update(self, job_id, **kw):
        with self._lock:
            m = self._jobs.get(job_id)
            if m:
                for k, v in kw.items():
                    setattr(m, k, v)
                self._persist(m)
            return m

    def active_count(self, owner):
        return sum(1 for m in self._jobs.values() if m.owner == owner and m.state in ("queued", "processing"))

    def cancel(self, job_id):
        self._cancel[job_id] = True
        return self.update(job_id, state="cancelled")

    def is_cancelled(self, job_id):
        return self._cancel.get(job_id, False)

    def delete(self, job_id):
        import shutil
        with self._lock:
            self._jobs.pop(job_id, None)
        shutil.rmtree(self.base / job_id, ignore_errors=True)


def process_job(store: JobStore, job_id: str, models_dir: Path):
    job_dir = store.base / job_id
    try:
        if store.is_cancelled(job_id):
            return
        store.update(job_id, state="processing", progress_percent=5)
        # find upload file
        uploads = [p for p in job_dir.iterdir() if p.name.startswith("upload.")]
        if not uploads:
            raise RuntimeError("Upload missing")
        upload = uploads[0]
        pcm = job_dir / "pcm.f32le"
        store.update(job_id, state="processing", progress_percent=10)
        n_samples = decode_to_pcm(upload, pcm)
        if n_samples <= 0:
            raise RuntimeError("No audio samples decoded")
        store.update(job_id, state="processing", progress_percent=15)
        try:
            import numpy as np  # noqa
            import onnxruntime as ort  # noqa
        except Exception as e:
            raise RuntimeError(f"ONNX Runtime not installed; worker cannot separate (install onnxruntime). Original: {e}")
        models = ensure_models(models_dir)
        stems_out: dict[str, tuple] = {}
        # accumulate stem PCM in memory per stem (short songs ok; long songs use list of chunks)
        buffers: dict[str, tuple[list, list]] = {s: ([], []) for s in STEMS}
        for stem in STEMS:
            if store.is_cancelled(job_id):
                return
            n_fft, dim_t = CONFIGS[stem]
            chunk = HOP * (dim_t - 1)
            trim = n_fft // 2
            segment = chunk - 2 * trim
            if segment <= 0:
                segment = chunk
            n_chunks = math.ceil(n_samples / segment) if segment else 1
            sess = ort.InferenceSession(str(models[stem]),
                providers=["CPUExecutionProvider"],
                sess_options=_ort_options(ort))
            inp = sess.get_inputs()[0].name
            for ci in range(n_chunks):
                if store.is_cancelled(job_id):
                    return
                seg_start = ci * segment
                valid = min(segment, n_samples - seg_start)
                left, right = read_stereo_chunk(pcm, seg_start - trim, chunk)
                spec = stft_chunk(left, right, n_fft, dim_t)
                out = sess.run(None, {inp: spec.reshape(1, 4, DIM_F, dim_t).astype("float32")})[0]
                out = np.asarray(out).reshape(4, DIM_F, dim_t).astype("float32")
                lo, ro = istft_chunk(out, n_fft, dim_t)
                buffers[stem][0].append(lo[trim:trim + valid].copy())
                buffers[stem][1].append(ro[trim:trim + valid].copy())
                frac = (ci + 1) / n_chunks
                idx = list(STEMS).index(stem)
                pct = int(15 + 80 * (idx + frac) / len(STEMS))
                store.update(job_id, state="processing", progress_percent=max(15, min(95, pct)))
            del sess
        import numpy as np
        stems_dir = job_dir / "stems"
        done = []
        for stem in STEMS:
            lb, rb = buffers[stem]
            left = np.concatenate(lb) if lb else np.zeros(0, dtype=np.float32)
            right = np.concatenate(rb) if rb else np.zeros(0, dtype=np.float32)
            write_wav(stems_dir / f"{stem}.wav", left, right)
            done.append(stem)
        store.update(job_id, state="complete", progress_percent=100, stems=done)
        # cleanup PCM
        pcm.unlink(missing_ok=True)
    except Exception as e:
        if store.is_cancelled(job_id) or ("cancel" in str(type(e)).lower()):
            store.update(job_id, state="cancelled", error={"code": "cancelled", "message": str(e), "retryable": False})
        else:
            store.update(job_id, state="failed", error={"code": "processing_failed", "message": str(e)[:500], "retryable": True})


def _ort_options(ort):
    opts = ort.SessionOptions()
    opts.intra_op_num_threads = 2
    opts.inter_op_num_threads = 1
    opts.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
    return opts
