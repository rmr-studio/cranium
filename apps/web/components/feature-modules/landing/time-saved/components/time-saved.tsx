'use client';

import { Section } from '@/components/ui/section';
import { cn } from '@/lib/utils';

const items = [
  {
    label: 'Catching the signal',
    metric: 'Days, not minutes',
    description:
      "Churn risk, stalled deals, broken flows — by the time a human spots them in a dashboard, it's already a fire. Signals sit buried in noise until they cost you.",
  },
  {
    label: 'Research & triage',
    metric: '4–6 hours per week',
    description:
      "Pulling context from Stripe, your CRM, and support to figure out what's actually happening before anyone can decide what to do about it.",
  },
  {
    label: 'Meetings to align',
    metric: '5+ syncs per decision',
    description:
      'Standups, threads, and follow-ups to get everyone on the same page before a single action gets taken. Context-switching eats the day.',
  },
  {
    label: 'Manual execution',
    metric: 'Every. Single. Time.',
    description:
      'Once you finally know what to do, someone still has to do it — send the email, update the CRM, flag the account, kick off the workflow.',
  },
];

export function TimeSaved() {
  return (
    <Section id="time-saved" size={24} mask="none" className="pb-20">
      <div className="clamp relative z-10 px-4 sm:px-8">
        {/* Heading */}
        <div className="mb-14 md:mb-20">
          <h2 className="font-serif text-2xl leading-none sm:text-4xl md:text-5xl lg:text-6xl">
            Less Noise. More Action.
          </h2>
          <p className="mt-4 max-w-3xl text-base leading-none tracking-tighter text-content/90">
            Riven collapses the gap between signal and action. Agents watch the data, surface what
            matters, and execute the next step — so your team stops drowning in triage, meetings,
            and manual follow-ups, and starts moving at the speed of the business.
          </p>
        </div>

        {/* Desktop grid */}
        <div className="hidden md:block">
          <div className="overflow-hidden rounded-xl border border-content/50">
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
                  <p className="mb-2 text-xs tracking-wide">{item.label}</p>
                  <p className="mb-3 font-serif text-lg font-medium tracking-tight lg:text-xl">
                    {item.metric}
                  </p>
                  <p className="text-sm leading-relaxed tracking-normal text-content/70">
                    {item.description}
                  </p>
                </div>
              ))}

              {/* Row 2: last item + summary (col-span-2) */}
              <div className="flex flex-col justify-end p-7 lg:p-8">
                <p className="mb-2 text-xs tracking-wide">{items[3].label}</p>
                <p className="mb-3 font-serif text-lg font-medium tracking-tight lg:text-xl">
                  {items[3].metric}
                </p>
                <p className="text-sm leading-relaxed tracking-normal text-content/90">
                  {items[3].description}
                </p>
              </div>

              {/* Summary + compound stats (spans 2 columns) */}
              <div className="col-span-2 flex flex-col justify-between border-l border-content/50 p-7 lg:flex-row lg:items-end lg:p-8">
                <div>
                  <p className="mb-2 text-xs tracking-wide">Close the loop</p>
                  <p className="mb-3 font-serif text-lg font-medium tracking-tight lg:text-xl">
                    What you reclaim
                  </p>
                  <p className="text-sm leading-relaxed tracking-normal text-content/90">
                    Days of lag between something happening and someone acting on it. The meetings,
                    the triage, the manual follow-through — compressed into a single autonomous
                    loop.
                  </p>
                </div>
                <div className="mt-6 shrink-0 space-y-0.5 font-light tracking-tight lg:mt-0 lg:pl-8 lg:text-right">
                  <p className="text-3xl text-primary/70 lg:text-4xl xl:text-5xl">Days → minutes</p>
                  <p className="text-3xl text-primary/80 lg:text-4xl xl:text-5xl">5+ meetings</p>
                  <p className="text-3xl text-primary/90 lg:text-4xl xl:text-5xl">10+ hours back</p>
                  <p className="font-serif text-3xl font-semibold lg:text-4xl xl:text-5xl">
                    1 autonomous loop
                  </p>
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Mobile layout */}
        <div className="flex flex-col gap-4 md:hidden">
          {items.map((item) => (
            <div key={item.label} className="border-l-2 border-content/50 py-4 pr-3 pl-5">
              <p className="font-serif text-base font-medium tracking-tight">{item.metric}</p>
              <p className="mt-1.5 text-sm leading-relaxed tracking-normal text-content/90">
                {item.description}
              </p>
            </div>
          ))}
          <div className="border-l-2 border-content/50 py-4 pr-3 pl-5">
            <p className="font-serif text-base font-medium tracking-tight">What you reclaim</p>
            <p className="mt-1.5 text-sm leading-relaxed tracking-normal text-content/90">
              Days of lag between something happening and someone acting on it. The meetings, the
              triage, the manual follow-through — compressed into a single autonomous loop.
            </p>
            <div className="mt-4 space-y-0.5 font-light tracking-tight">
              <p className="text-2xl">Days → minutes</p>
              <p className="text-2xl">5+ meetings</p>
              <p className="text-2xl">10+ hours back</p>
              <p className="font-serif text-2xl font-semibold">1 autonomous loop</p>
            </div>
          </div>
        </div>
      </div>
    </Section>
  );
}
