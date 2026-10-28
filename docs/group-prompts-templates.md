# Group prompts and message templates

## In the Android app

1. Open Groups, expand a group, and choose **Schedule group call**.
2. All consent-approved contacts start selected. Search and deselect people; search never resets selection. Pending or opted-out contacts cannot be included.
3. Continue. The group and selection are fixed context above a single text/voice composer.
4. Say or type, for example: `Tomorrow at 3pm remind them about Monday's exam. Call each person every 5 minutes.`
5. Review the spoken message, audio, selected recipients, each call's date/time, and credit estimate. Confirm once to schedule.

The start time must be at least two minutes ahead. If the instruction has no start time, add it to the same prompt and retry; no audio is generated for incomplete requests. With no interval, all recipients share the start time. An interval staggers distinct recipients, not repeated calls to each person. Supported interval units are seconds, minutes, hours, days, weeks, whole months and years. Calendar month/year intervals clamp month-end dates to the last valid day. Review the full per-recipient schedule before confirming.

## Templates

Home → **Templates** supports creating, searching, editing, deleting and using named messages. Names are case-insensitively unique per account. Templates contain spoken text, not recipients or scheduling instructions.

Example: save `Exam reminder` with `Please bring your books for Monday's examination.` Then type or dictate `Use "Exam reminder" template tomorrow at 3pm, call each person every 5 minutes` in the group composer. For a personal call, use `Call Mom tomorrow at 3pm and use "Exam reminder" template`.

The backend resolves templates within the authenticated account, uses the saved message exactly with the normal caller introduction, and asks for correction if a name does not exist. Template text is excluded from time extraction so dates in the message cannot change the call schedule. Deleting a template does not change existing campaign drafts or scheduled calls.

## Named contacts from saved groups on Home

Business users can type or dictate these directly on Home:

- `Call Aarav from Appointments group using the reminder template tomorrow at 9:15am`.
- `Call Aarav and Riya from Appointments and Neha from Customers using the reminder template tomorrow at 9:15am`.
- `Call everyone from Appointments tomorrow at 3pm and say the clinic is closed`.

The app calls `POST /campaigns/resolve` with `raw_prompt` before personal extraction. This is a deterministic lookup, with no model or TTS charge. It returns `selected` with the exact saved contact IDs and group labels, or `not_group` for an ordinary phonebook call. Full names match first; a first name is accepted only when unique within the named group. Missing, ambiguous or unapproved people require correction. Include a group for each recipient clause. Names mentioned only in the spoken message do not add recipients.

Review/search/deselect the matched recipients before generating audio. Cross-group previews use the reserved `group_id` value `selected-groups` plus explicit `recipient_ids`. Ownership and consent are checked at preview, confirmation and execution. A number shared across groups is called once. No contacts are copied into new groups. Deleting a group with pending calls that selected its members is blocked until those calls are cancelled.

Both `use "Reminder" template` and `using the reminder template` resolve the saved template. Prefer explicit `am`/`pm` and a date such as `tomorrow` when a clock time could be ambiguous. The confirmation lists the actual resolved times.

If Templates reports HTTP 404, the running backend may predate these routes: restart Flask without restarting the Cloudflare tunnel. The tunnel's public URLs remain unchanged. The Templates screen also has Retry after a failed fetch.

## API and persistence

- `GET /templates`: list the signed-in account's templates.
- `POST /templates`: `{ "name": "Exam reminder", "message": "Please bring your books." }`.
- `PATCH /templates/{id}`: update name/message with the same body.
- `DELETE /templates/{id}`: delete an owned template.
- `POST /campaigns/preview`: `{ "group_id": "...", "recipient_ids": ["..."], "raw_prompt": "Tomorrow at 3pm say hello, call each person every 5 minutes" }`.
- Preview returns a draft with `interval` and `recipients[].scheduled_time`. Selection order is preserved, duplicated phone numbers are collapsed, and foreign, missing or opted-out IDs are rejected.
- `POST /campaigns/{id}/schedule` with `{ "confirmed": true }` rechecks ownership, consent, credits and the start time before creating one job per selected contact. Duplicate confirmation cannot schedule the draft twice.
- Existing personal `/schedule-call` also resolves template references.

Restart Flask to run the existing automatic schema migration: it creates `message_templates` and adds `campaigns.interval_json`, with an empty interval for old rows. Supabase client access to templates is disabled with the other backend-only tables. No new environment variables, API keys or dependencies are required. Rebuild/install the Android app to use the new screens.

One model extraction is used per campaign preview. One generated audio file is shared by the campaign. Re-preparing the same spoken message within an hour reuses its campaign audio; changed wording produces new audio. Existing scheduling, credit settlement and Exotel execution are reused.

## Verification

Run `backend/.venv/Scripts/python.exe -m unittest discover -s backend/tests` from the project directory, and `gradlew.bat :app:testDebugUnitTest :app:assembleDebug --offline` from `android`.

Automated coverage includes selected IDs/order, staggered jobs, empty and cross-group selections, missing start time, template CRUD/ownership, personal template use, calendar intervals, duplicate names, audio reuse, and repeatable schema migration. Tests mock model/TTS/calling services and do not place real calls or spend provider credits. On a device, check microphone permissions, search/deselection, preview playback and a final call with your existing live backend/tunnel setup.
