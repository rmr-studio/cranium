-- Drop all survey columns from waitlist_submissions.
-- Final shape: id, name, email, created_at.

alter table public.waitlist_submissions
  drop column if exists integrations,
  drop column if exists involvement,
  drop column if exists business_overview,
  drop column if exists pain_points,
  drop column if exists pain_points_other,
  drop column if exists monthly_price,
  drop column if exists operational_headache;
