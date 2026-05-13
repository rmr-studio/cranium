-- Waitlist submission table for getcranium.io landing page
-- Stores waitlist signups and optional survey responses from the multi-step form

create table if not exists public.waitlist_submissions (
  id          uuid primary key default gen_random_uuid(),
  name        text not null,
  email       text not null unique,
  integrations      text[],
  monthly_price     text,
  operational_headache text,
  involvement       text check (involvement in ('WAITLIST', 'EARLY_TESTING', 'CALL_EARLY_TESTING')),
  created_at  timestamptz not null default now()
);

-- RLS: anon users can insert (signup uses upsert to handle survey updates)
alter table public.waitlist_submissions enable row level security;

create policy "Anyone can join the waitlist"
  on public.waitlist_submissions for insert
  to anon
  with check (true);

-- Index for email lookups (unique constraint already creates one, but explicit for clarity)
create index if not exists idx_waitlist_email on public.waitlist_submissions (email);
