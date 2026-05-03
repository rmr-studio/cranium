import { FeaturedPosts } from '@/components/feature-modules/blogs/components/featured-posts';
import { getAllPosts, getFeaturedPost } from '@/lib/blog';
import dynamic from 'next/dynamic';

import { Hero } from '@/components/feature-modules/landing/hero/components/hero';
import { Preview } from '@/components/feature-modules/landing/preview/components/preview';

const Setup = dynamic(() =>
  import('@/components/feature-modules/landing/setup/components/setup').then((m) => m.Setup),
);

const Features = dynamic(() =>
  import('@/components/feature-modules/landing/features/components/features').then(
    (m) => m.Features,
  ),
);

const Integrations = dynamic(() =>
  import('@/components/feature-modules/landing/connections/components/connections').then(
    (m) => m.Connections,
  ),
);

const TimeSaved = dynamic(() =>
  import('@/components/feature-modules/landing/time-saved/components/time-saved').then(
    (m) => m.TimeSaved,
  ),
);

const Faq = dynamic(() =>
  import('@/components/feature-modules/landing/faq/components/faq').then((m) => m.Faq),
);
const Waitlist = dynamic(() =>
  import('@/components/feature-modules/waitlist/components/waitlist').then((m) => m.Waitlist),
);

export default async function Home() {
  const [featured, posts] = await Promise.all([getFeaturedPost(), getAllPosts()]);
  const recent = posts.filter((p) => p.slug !== featured?.slug).slice(0, 3);

  return (
    <main className="relative min-h-screen overflow-x-clip">
      <section className="fixed inset-0"></section>
      <section
        className="absolute inset-0 top-0 h-screen bg-white"
        style={{
          maskImage: 'linear-gradient(to bottom, transparent, black 0%, black 40%, transparent)',
          WebkitMaskImage:
            'linear-gradient(to bottom, transparent, black 0%, black 40%, transparent)',
        }}
      ></section>
      <section className="relative mx-auto w-full">
        <div
          aria-hidden
          className="pointer-events-none absolute inset-y-0 left-1/2 z-[70] w-full -translate-x-1/2 border-x border-x-neutral-500/40 2xl:max-w-[min(90vw,var(--breakpoint-3xl))]"
        />
        <Hero />
        <div className="relative h-[16rem] sm:h-[20rem]">
          <Preview />
        </div>
        <div className="relative h-full">
          <Features />
        </div>
        <Integrations />
        <TimeSaved />
        <Setup />

        <FeaturedPosts featured={featured} recent={recent} />
        <Faq preview />
        <Waitlist />
      </section>
    </main>
  );
}
