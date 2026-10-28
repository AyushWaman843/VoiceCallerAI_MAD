-- Accounts and password hashes are managed by Supabase Auth in auth.users.
-- Flask connects as the database owner and enforces verified user ownership.
-- Block access through the public Data API; the app uses Flask for call data.
ALTER TABLE public.call_jobs ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE public.call_jobs FROM anon, authenticated;
