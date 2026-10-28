"""Generate the static ring to upload into Exotel's first Greeting applet."""

import math
from pathlib import Path
import struct
import wave


def create_ring(destination: Path) -> None:
    rate = 8000
    samples = bytearray()
    # Two gentle bursts, with short fades to prevent clicks on a telephone line.
    for index in range(int(rate * 1.5)):
        time = index / rate
        position = time if time < 0.65 else time - 0.85
        gain = max(0.0, min(1.0, position / 0.01, (0.65 - position) / 0.01))
        tone = math.sin(2 * math.pi * 400 * time) + math.sin(2 * math.pi * 450 * time)
        samples.extend(struct.pack("<h", round(4500 * gain * tone)))
    destination.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(destination), "wb") as audio:
        audio.setnchannels(1)
        audio.setsampwidth(2)
        audio.setframerate(rate)
        audio.writeframes(samples)


if __name__ == "__main__":
    target = Path(__file__).resolve().parents[2] / "docs" / "assets" / "voxie-start-ring.wav"
    create_ring(target)
    print(target)
