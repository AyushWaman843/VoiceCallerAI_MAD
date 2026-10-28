from time import monotonic
from threading import Lock


_live_calls = {}
_lock = Lock()


def remember_audio(job_id: str, number: str, audio_url: str, call_sid: str = "") -> None:
    now = monotonic()
    with _lock:
        _live_calls[job_id] = (now, call_sid, "".join(c for c in number if c.isdigit())[-10:], audio_url)
        for key, entry in list(_live_calls.items()):
            if now - entry[0] > 180:
                _live_calls.pop(key, None)


def live_audio(call_sid: str = "", number: str = "", job_id: str = "") -> str:
    now = monotonic()
    with _lock:
        recent = [(key, entry) for key, entry in _live_calls.items() if now - entry[0] <= 180]
    if job_id:
        return next((entry[3] for key, entry in recent if key == job_id), "")
    if call_sid:
        return next((entry[3] for _, entry in recent if entry[1] == call_sid), "")
    suffix = "".join(c for c in number if c.isdigit())[-10:]
    if suffix:
        matches = [entry[3] for _, entry in recent if entry[2] == suffix]
        return matches[0] if len(matches) == 1 else ""
    return recent[0][1][3] if len(recent) == 1 else ""


def forget_audio(job_id: str) -> None:
    with _lock:
        _live_calls.pop(job_id, None)
