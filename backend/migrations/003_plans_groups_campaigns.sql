-- Supabase PostgreSQL equivalent of Flask's automatic create_all/ALTER migration.
-- Run once as the database owner when deploying without application schema initialization.
CREATE TABLE IF NOT EXISTS public.account_plans (
    user_id varchar(255) PRIMARY KEY,
    plan_id varchar(32) NOT NULL,
    credits_available integer NOT NULL DEFAULT 0 CHECK (credits_available >= 0),
    organization_name varchar(120),
    timezone varchar(60) NOT NULL DEFAULT 'Asia/Kolkata',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS public.credit_events (
    id varchar(36) PRIMARY KEY,
    user_id varchar(255) NOT NULL,
    change integer NOT NULL,
    source_type varchar(32) NOT NULL,
    source_id varchar(100) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT credit_source_once UNIQUE (source_type, source_id)
);
CREATE INDEX IF NOT EXISTS ix_credit_events_user_id ON public.credit_events(user_id);
CREATE TABLE IF NOT EXISTS public.contact_groups (
    id varchar(36) PRIMARY KEY,
    user_id varchar(255) NOT NULL,
    name varchar(120) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_contact_groups_user_id ON public.contact_groups(user_id);
CREATE TABLE IF NOT EXISTS public.group_contacts (
    id varchar(36) PRIMARY KEY,
    group_id varchar(36) NOT NULL REFERENCES public.contact_groups(id) ON DELETE CASCADE,
    user_id varchar(255) NOT NULL,
    name varchar(120) NOT NULL,
    phone varchar(20) NOT NULL,
    consent_status varchar(20) NOT NULL DEFAULT 'pending',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT group_phone_once UNIQUE (group_id, phone)
);
CREATE INDEX IF NOT EXISTS ix_group_contacts_group_id ON public.group_contacts(group_id);
CREATE INDEX IF NOT EXISTS ix_group_contacts_user_id ON public.group_contacts(user_id);
CREATE TABLE IF NOT EXISTS public.contact_imports (
    id varchar(36) PRIMARY KEY,
    user_id varchar(255) NOT NULL,
    group_id varchar(36),
    group_name varchar(120) NOT NULL,
    source varchar(20) NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'review',
    rows_json text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_contact_imports_user_id ON public.contact_imports(user_id);
CREATE TABLE IF NOT EXISTS public.campaigns (
    id varchar(36) PRIMARY KEY,
    user_id varchar(255) NOT NULL,
    group_id varchar(36) NOT NULL,
    title varchar(120) NOT NULL,
    message text NOT NULL,
    audio_url text NOT NULL,
    scheduled_time timestamptz NOT NULL,
    timezone varchar(60) NOT NULL,
    contact_ids_json text NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'draft',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_campaigns_user_id ON public.campaigns(user_id);
CREATE TABLE IF NOT EXISTS public.audio_previews (
    id varchar(36) PRIMARY KEY,
    user_id varchar(255) NOT NULL,
    message text NOT NULL,
    audio_url text NOT NULL,
    expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_audio_previews_user_id ON public.audio_previews(user_id);
ALTER TABLE public.call_jobs ADD COLUMN IF NOT EXISTS campaign_id varchar(36);
ALTER TABLE public.call_jobs ADD COLUMN IF NOT EXISTS credit_state varchar(20);
ALTER TABLE public.call_jobs ADD COLUMN IF NOT EXISTS credit_reservation_id varchar(100);
ALTER TABLE public.call_jobs DROP CONSTRAINT IF EXISTS check_call_job_status;
ALTER TABLE public.call_jobs ADD CONSTRAINT check_call_job_status
    CHECK (status IN ('pending', 'calling', 'connected', 'missed', 'failed', 'cancelled'));
ALTER TABLE public.call_jobs ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.call_jobs FROM anon, authenticated;
DO $$
DECLARE table_name text;
BEGIN
    FOREACH table_name IN ARRAY ARRAY['account_plans', 'credit_events', 'contact_groups',
        'group_contacts', 'contact_imports', 'campaigns', 'audio_previews'] LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', table_name);
        EXECUTE format('REVOKE ALL ON public.%I FROM anon, authenticated', table_name);
    END LOOP;
END $$;
