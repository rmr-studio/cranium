'use client';

import { Section } from '@/components/ui/section';
import { cn } from '@/lib/utils';

const items = [
  {
    label: 'Catching the signal',
    metric: 'Action in days, not weeks or months.',
    description:
      "Like a team member that doesn't sleep. Riven spots problems, patterns and opportunities before you even see them. Acting on it automaticlaly or giving you everything you need to act fast.",
  },
  {
    label: 'Stop the research & triage',
    metric: '4–6 hours per week',
    description:
      "No more pulling context from Shopify, Klaviyo, Slack and Support to figure out what's actually happening before anyone can decide what to do about it.",
  },
  {
    label: 'Reduce the meetings needed to align',
    metric: 'Cut the 5+ syncs per decision',
    description:
      'Standups, threads, and follow-ups to get everyone on the same page before a single action gets taken. Context-switching eats the day.',
  },
  {
    label: 'No more manual execution',
    metric: 'Automate repetitive tasks',
    description:
      'Riven studies the outcomes of past actions to know exactly what to do, and when, to get the best outcome. Then it does it, autonomously (or with your approval), every time.',
  },
];

export function TimeSaved() {
  return (
    <Section
      id="time-saved"
      size={24}
      mask="none"
      className="mx-auto border-x border-x-content/25 px-0! pb-20 2xl:max-w-[min(90dvw,var(--breakpoint-3xl))]"
    >
      <div className="relative z-10">
        {/* Heading */}
        <div className="mb-14 px-8 sm:px-12 md:mb-20">
          <h2 className="font-bit text-2xl leading-none sm:text-4xl md:text-5xl lg:text-6xl">
            What you get with Riven.
          </h2>
          <p className="mt-4 max-w-3xl font-display text-base leading-none tracking-tighter text-content/90">
            Agents watch the data, surface what matters, and execute the next step with context from
            your internal SOPS, meeting notes and brand guidelines. So your team stops drowning in
            triage, meetings, and manual follow-ups, and starts moving at the speed of the business.
          </p>
        </div>

        {/* Desktop grid */}
        <div className="hidden md:block">
          <div className="overflow-hidden border-y border-y-content/50">
            <div className="grid grid-cols-3">
              {/* Row 1: first 3 items */}
              {items.slice(0, 3).map((item, i) => (
                <div
                  key={item.label}
                  className={cn(
                    'border-b border-content/50 p-7 lg:p-8',
                    i > 0 && 'border-l border-content/50',
                  )}
                >
                  <p className="mb-2 font-display text-xs tracking-wide">{item.label}</p>
                  <p className="mb-3 font-bit text-lg font-medium tracking-tight lg:text-xl">
                    {item.metric}
                  </p>
                  <p className="font-display text-sm leading-[1.1] tracking-tighter text-content/70">
                    {item.description}
                  </p>
                </div>
              ))}

              {/* Row 2: last item + summary (col-span-2) */}
              <div className="flex flex-col justify-end p-7 lg:p-8">
                <p className="mb-2 font-display text-xs tracking-wide">{items[3].label}</p>
                <p className="mb-3 font-bit text-lg font-medium tracking-tight lg:text-xl">
                  {items[3].metric}
                </p>
                <p className="font-display text-sm leading-[1.1] tracking-tighter text-content/90">
                  {items[3].description}
                </p>
              </div>

              {/* Summary + compound stats (spans 2 columns) */}
              <div className="col-span-2 flex flex-col items-end justify-between space-y-8 border-l border-content/50 p-7 lg:p-8">
                <div className="mt-6 shrink-0 space-y-0.5 font-light tracking-tight lg:mt-0 lg:pl-8 lg:text-right">
                  <p className="font-display text-3xl text-primary lg:text-4xl xl:text-5xl">
                    Improved customer loyalty
                  </p>
                  <p className="font-display text-3xl text-primary lg:text-4xl xl:text-5xl">
                    Action in Weeks → Days
                  </p>
                  <p className="font-display text-3xl text-primary lg:text-4xl xl:text-5xl">
                    Skip the useless meetings
                  </p>
                  <p className="font-display text-3xl text-primary lg:text-4xl xl:text-5xl">
                    10+ hours back
                  </p>

                  <p className="font-bit text-3xl font-semibold lg:text-4xl xl:text-6xl">
                    1 autonomous loop
                  </p>
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Mobile layout */}
        <div className="flex flex-col gap-4 px-8 md:hidden">
          {items.map((item) => (
            <div key={item.label} className="border-l-2 border-content/50 py-4 pr-3 pl-5">
              <p className="font-bit text-base font-medium tracking-tight">{item.metric}</p>
              <p className="mt-1.5 font-display text-sm leading-[1.1] tracking-tighter text-content/90">
                {item.description}
              </p>
            </div>
          ))}
          <div className="border-l-2 border-content/50 py-4 pr-3 pl-5 font-bit">
            <div className="mt-4 space-y-0.5 font-light tracking-tight">
              <p className="font-display text-2xl text-primary lg:text-4xl xl:text-5xl">
                Action in Weeks → Days
              </p>
              <p className="font-display text-2xl text-primary lg:text-4xl xl:text-5xl">
                Skip the useless meetings
              </p>
              <p className="font-display text-2xl text-primary lg:text-4xl xl:text-5xl">
                10+ hours back
              </p>
              <p className="font-bit text-2xl font-semibold lg:text-4xl xl:text-6xl">
                1 autonomous loop
              </p>
            </div>
          </div>
        </div>
      </div>
    </Section>
  );
}
