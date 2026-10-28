import unittest
from unittest.mock import patch

from app.services.live_audio import live_audio, remember_audio


class LiveAudioTests(unittest.TestCase):
    def setUp(self):
        self.cache = patch("app.services.live_audio._live_calls", {})
        self.cache.start()

    def tearDown(self):
        self.cache.stop()

    def test_single_live_call_resolves_without_database(self):
        remember_audio("call-a", "+919876543210", "https://example.test/a.wav", "sid-a")
        self.assertEqual(live_audio(call_sid="sid-a"), "https://example.test/a.wav")
        self.assertEqual(live_audio(number="9876543210"), "https://example.test/a.wav")
        self.assertEqual(live_audio(), "https://example.test/a.wav")

    def test_ambiguous_unidentified_call_does_not_guess(self):
        remember_audio("call-a", "+919876543210", "https://example.test/a.wav")
        remember_audio("call-b", "+919876543211", "https://example.test/b.wav")
        self.assertEqual(live_audio(), "")
        self.assertEqual(live_audio(number="9876543211"), "https://example.test/b.wav")
