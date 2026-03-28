-- Waitlist survey v2: DTC e-commerce focused questions
-- Adds business_overview, pain_points, pain_points_other
-- Keeps operational_headache and monthly_price for backwards compat with existing rows

alter table public.waitlist_submissions
  add column if not exists business_overview text,
  add column if not exists pain_points text[],
  add column if not exists pain_points_other text;
