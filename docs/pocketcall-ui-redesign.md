# PocketCall Android UI

The Android app now opens in a conversation, with a bottom composer and a left
drawer. All visible product branding is PocketCall.

## Navigation and behavior

- New call clears the current conversation only when no request or recording is active.
- Text submission clears the composer while retaining the submitted prompt for
  the existing needs_info / needs_confirmation / scheduled API flow.
- Confirmation and missing details appear inline. Editing uses the same existing
  refinement fields; final confirmation still sends the backend's agent_result.
- The plus menu opens phone contacts, a number prompt, manual details, or upcoming calls.
- Recording uses MediaRecorder, with a measured, smoothed amplitude timeline.
  Cancel discards the file; finish uploads it to the existing transcription route.
  The returned transcript is submitted as a normal message. This is not streaming STT.
- Upcoming, history, contacts, calendar and settings are drawer destinations.
  Drafts and conversation state survive navigation and configuration changes.
- The calendar groups calls by IST and keeps malformed legacy dates visible.
- Cancellation requires a separate confirmation. Call again prepares a new prompt
  and requires a fresh time and confirmation.
- Contacts come from the phone with permission. Ten are initially visible;
  search covers the entire imported phonebook, not just the visible ten.
- Theme (System / Light / Dark), name, testing user ID and backend address are
  saved locally. There is no new authentication or cloud conversation storage.

## Code map

- ui/CallAiApp.kt: navigation, sheets, permissions and persistent preferences.
- ui/CallAiViewModel.kt: existing API flow plus conversation presentation state.
- ui/VoiceCapture.kt: recording lifecycle and real amplitude sampling.
- ui/PhoneContacts.kt: existing contact query and normalization, moved out of UI.
- ui/components/: composer, waveform, icons, rows and call detail sheet.
- ui/screens/: chat, contact list, upcoming/history and settings.
- ui/CallCalendar.kt: month grid and selected-day calls.
- ui/theme/: neutral light/dark palette, typography and motion durations.

The Flask backend, Retrofit routes and request/response DTOs, contact matching,
ElevenLabs, Exotel and scheduling implementation are unchanged by this redesign.
The earlier Flask merge-marker repair is a separate existing working-tree change.

## Verification

From android:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest --console=plain
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain
```

Instrumentation covers contact search beyond ten entries, composer modes,
confirmation buttons, themes, calendar navigation, cancellation confirmation and
the real Retrofit confirmation request against a local HTTP fixture. It does
not call any paid providers.

Verified on 2026-09-27: debug APK builds, 9 unit tests pass, and 6 emulator
instrumentation tests pass. Light/dark screenshots and the actual small-phone
keyboard/sidebar layouts were reviewed. Screenshots are in the ignored
android/app/build/ui-review-final directory. Physical microphone quality and
live provider delivery still require a real-device end-to-end test.

For a real-device retest, run Flask and use the computer's Wi-Fi backend address.
Check contact and microphone permission denial/grant, spoken input, missing time,
edited confirmation, one scheduled call, drawer/history refresh and cancellation.
An actual Exotel call still needs a public audio/callback URL, as before.
