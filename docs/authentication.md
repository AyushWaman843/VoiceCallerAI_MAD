# Voxie email accounts

Get Started opens Create Account. Existing users can choose Sign in. After authentication, users without a mode choose Personal or Business. Google sign-in is not included.

## Supabase setup

The existing project uses Supabase Auth (`auth.users`) to store email identities and password hashes. Full names are stored in the user's `user_metadata.full_name`. Do not add passwords to `call_jobs` or create a separate password table.

Backend environment:

```dotenv
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_PUBLISHABLE_KEY=your-publishable-key
DATABASE_URL=your-existing-postgres-connection-string
```

The local `.env` is configured with the project URL and publishable key previously provided by the owner. No service-role key is required. These settings are separate from the PostgreSQL connection string.

For email/password login without verification, keep **Enable Email provider** on and turn **Confirm email** off under Supabase Dashboard > Authentication > Sign In / Providers > Email. These are separate switches. The live project currently has the Email provider enabled, but Confirm email is still enabled. Google sign-in remains disabled.

The backend accepts a valid Supabase session without adding another email-confirmation check.

In Supabase Authentication settings, configure the Site URL to a reachable website for the confirmation-email redirect. Supabase verifies the email before redirecting; a default localhost redirect may show a browser error after verification. The app signs in with email/password after confirmation, so it does not depend on capturing that redirect's tokens. Configure custom SMTP before testing with arbitrary recipient addresses: Supabase's default email sender restricts recipients and delivery rates.

`backend/migrations/002_auth_rls.sql` enables RLS on `public.call_jobs` and revokes Data API access from `anon` and `authenticated`. It was applied to the configured Supabase project. Flask's existing database-owner connection continues to access this table, while each HTTP request checks the signed-in user's ownership. Apply this migration once on any additional Supabase project before use.

The 15 legacy `user_001` call records are preserved. They are not visible in a new user's account and are not automatically assigned to a newly registered email address.

## API

All auth responses have `Cache-Control: no-store`.

| Endpoint | Request | Result |
| --- | --- | --- |
| POST `/auth/signup` | `full_name`, `email`, `password` (8+ characters) | `confirmation_required`, or an authenticated session if the project allows immediate signup |
| POST `/auth/login` | `email`, `password` | Authenticated session |
| POST `/auth/refresh` | `refresh_token` | Rotated session tokens |
| GET `/auth/me` | Bearer access token | Verified user ID, email, full name |
| POST `/auth/mode` | Bearer access token, `{"mode":"personal"}` or `{"mode":"business"}` | Saves the user's choice in Supabase user metadata |
| POST `/auth/logout` | Bearer access token | Revokes the current server session's refresh token |

An authenticated response contains `status`, `access_token`, `refresh_token`, `expires_in`, and `user` with `id`, `email`, `full_name`. Never log this response.

`/schedule-call`, `/transcribe-audio`, `/calls`, cancellation, and retry now require `Authorization: Bearer <access_token>`. Client `user_id` and `user_name` values do not establish identity: the server uses Supabase's verified user. The Android app no longer exposes a testing-user-ID input. Existing web tester scripts must supply a token to these routes too.

`/health`, generated media, and the Exotel callbacks remain public because Exotel must fetch audio without an app login. The public audio URL is a bearer link; do not share it outside the intended call flow.

## Android sessions

Tokens are encrypted with a device-specific Android Keystore AES-GCM key and excluded from cloud backup/device transfer. Passwords are never persisted. The app refreshes expiring tokens, verifies restored sessions, and clears account-scoped state on sign-out. Requests bind the token to the backend URL used at login. Changing that URL requires signing in again.

Use Settings > Sign out to switch accounts. Changing the account does not cancel already scheduled calls. The existing scheduler still executes those jobs at the requested time.

The mode choice is stored as `user_metadata.call_mode` in Supabase and can be changed from Settings. It changes the app's starter prompts; it is not a permission, billing tier, or authorization role. Calls continue through the same backend workflow.

For local development, use the PC's current LAN URL under Connection settings on the login screen. Use HTTPS for public access so credentials and tokens are encrypted in transit. The local Flask backend remains responsible for calls, audio generation, and transcription.

## Retest

1. Start Flask and run the updated Android build. Tap Get Started.
2. After enabling Email and disabling Confirm email in Supabase, create an account using a real email you control. Sign in directly.
3. Confirm the user appears in Supabase > Authentication > Users with the entered name in metadata.
4. Schedule a call only when ready to consume the existing LLM/TTS/calling services.
5. Sign out, then sign in again. Verify your own calls remain visible.
6. In Postman, verify `/calls` without a bearer token returns 401. With one user's token, supplying another `user_id` must still return only the signed-in user's calls; cancel/retry of another user's job must return 404.

Automated checks use mocked Supabase responses and temporary SQLite data. They do not send email, invoke LLM/TTS, or place phone calls. Live setup verification checked the Supabase auth settings and applied the RLS migration without creating a test account.

References: [Supabase password auth](https://supabase.com/docs/guides/auth/passwords), [SMTP setup](https://supabase.com/docs/guides/auth/auth-smtp).
