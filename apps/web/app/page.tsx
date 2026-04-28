import { FeaturedPosts } from '@/components/feature-modules/blogs/components/featured-posts';
import { getAllPosts, getFeaturedPost } from '@/lib/blog';
import dynamic from 'next/dynamic';

import { Hero } from '@/components/feature-modules/landing/hero/components/hero';

const CrossDomainIntelligence = dynamic(() =>
  import('@/components/feature-modules/landing/cross-domain-intelligence/cross-domain-section').then(
    (m) => m.CrossDomainIntelligence,
  ),
);

const KnowledgeRuleBase = dynamic(() =>
  import('@/components/feature-modules/landing/knowledge-rule-base/rule-base-section').then(
    (m) => m.RuleBaseSection,
  ),
);

const PlatformSection = dynamic(() =>
  import('@/components/feature-modules/landing/platform/platform').then((m) => m.PlatformSection),
);

const LayersSection = dynamic(() =>
  import('@/components/feature-modules/landing/layers/layer').then((m) => m.LayersSection),
);

const TimeSaved = dynamic(() =>
  import('@/components/feature-modules/landing/time-saved/components/time-saved').then(
    (m) => m.TimeSaved,
  ),
);

const CohortBehaviour = dynamic(() =>
  import('@/components/feature-modules/landing/valuable-cohorts/valuable-cohorts').then(
    (m) => m.CohortBehaviour,
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
    <main className="min-h-screen overflow-x-clip">
      <Hero />
      <section className="relative mx-auto w-full lg:max-w-[min(100dvw,var(--breakpoint-3xl))]">
        <PlatformSection />
        <TimeSaved />
        <LayersSection />
        <KnowledgeRuleBase />
        <CohortBehaviour />
        <CrossDomainIntelligence />

        <FeaturedPosts featured={featured} recent={recent} />
        <Faq preview />
        <Waitlist />
      </section>
    </main>
  );
}
