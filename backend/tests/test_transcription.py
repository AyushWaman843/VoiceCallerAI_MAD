import io
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

from werkzeug.datastructures import FileStorage
from app.services import transcription_service as service


class TranscriptionTests(unittest.TestCase):
    def test_local_transcription_preserves_model_quality_and_cleans_temp_file(self):
        model = Mock()
        model.transcribe.return_value = (iter([SimpleNamespace(text=" Call Aarav ")]), None)
        audio = FileStorage(stream=io.BytesIO(b"test audio"), filename="prompt.m4a")
        with patch.object(service, "_load_local_model", return_value=model):
            self.assertEqual(service._transcribe_with_local_whisper(audio, "Aarav, Appointments"), "Call Aarav")
        args, options = model.transcribe.call_args
        self.assertFalse(Path(args[0]).exists())
        self.assertEqual(options["beam_size"], 5)
        self.assertEqual(options["hotwords"], "Aarav, Appointments")
        self.assertTrue(options["without_timestamps"])
        self.assertNotIn("language", options)

    def test_failed_decode_cleans_temp_file(self):
        model = Mock()
        model.transcribe.side_effect = RuntimeError("decode failed")
        audio = FileStorage(stream=io.BytesIO(b"bad audio"), filename="prompt.m4a")
        with patch.object(service, "_load_local_model", return_value=model), self.assertRaises(RuntimeError):
            service._transcribe_with_local_whisper(audio)
        self.assertFalse(Path(model.transcribe.call_args.args[0]).exists())
