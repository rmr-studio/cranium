'use client';

import { Section } from '@/components/ui/section';
import { cn } from '@/lib/utils';

const items = [
  {
    label: 'Data scattered everywhere',
    metric: '16 tabs open',
    description:
      'Your business data lives across Stripe, your CRM, support tools, and analytics dashboards. Every question starts with opening another tab.',
  },
  {
    label: 'Cross-referencing tools',
    metric: '3–5 hours per week',
    description:
      'Exporting CSVs from Stripe, cross-checking in your CRM, matching support data in spreadsheets to answer basic questions.',
  },
  {
    label: 'Building internal reports',
    metric: '2–3 hours per week',
    description:
      'Pulling numbers from four dashboards, formatting them into something your team can actually read and act on.',
  },
  {
    label: 'The spreadsheet of truth',
    metric: '3 spreadsheets deep',
    description:
      'One master spreadsheet everyone\'s afraid to touch. The "single source of truth" that nobody trusts.',
  },
];

export function TimeSaved() {
  return (
    <Section id="time-saved" navbarInverse size={24} mask="none" className="pb-20">
      <div className="clamp relative z-10 px-4 sm:px-8">
        {/* Heading */}
        <div className="mb-14 md:mb-20">
          <h2 className="font-serif text-4xl leading-none md:text-5xl lg:text-6xl">
            Less stitching. More signal.
          </h2>
          <p className="mt-4 max-w-2xl text-base leading-none tracking-tighter text-content/90">
            Riven eliminates the manual work caused by disconnected tools. Allowing you to focus on
            what matters most, growing your business.
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
                  <p className="mb-2 text-xs tracking-wide">Take back control</p>
                  <p className="mb-3 font-serif text-lg font-medium tracking-tight lg:text-xl">
                    What you save
                  </p>
                  <p className="text-sm leading-relaxed tracking-normal text-content/90">
                    Hours of manual work every week. The mental overhead of stitching together data,
                    the anxiety of making decisions based on incomplete information.
                  </p>
                </div>
                <div className="mt-6 shrink-0 space-y-0.5 font-light tracking-tight lg:mt-0 lg:pl-8 lg:text-right">
                  <p className="text-3xl text-primary/70 lg:text-4xl xl:text-5xl">8–12 hours</p>
                  <p className="text-3xl text-primary/80 lg:text-4xl xl:text-5xl">16 tabs</p>
                  <p className="text-3xl text-primary/90 lg:text-4xl xl:text-5xl">3 spreadsheets</p>
                  <p className="font-serif text-3xl font-semibold lg:text-4xl xl:text-5xl">
                    1 massive headache
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
            <p className="font-serif text-base font-medium tracking-tight">What you save</p>
            <p className="mt-1.5 text-sm leading-relaxed tracking-normal text-content/90">
              Hours of manual work every week. The mental overhead of stitching together data, the
              anxiety of making decisions based on incomplete information.
            </p>
            <div className="mt-4 space-y-0.5 font-light tracking-tight">
              <p className="text-2xl">8–12 hours</p>
              <p className="text-2xl">16 tabs</p>
              <p className="text-2xl">3 spreadsheets</p>
              <p className="font-serif text-2xl font-semibold">1 massive headache</p>
            </div>
          </div>
        </div>
      </div>
    </Section>
  );
}
