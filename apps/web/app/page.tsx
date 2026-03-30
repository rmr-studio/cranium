import { FeaturedPosts } from '@/components/feature-modules/blogs/components/featured-posts';
import { getAllPosts, getFeaturedPost } from '@/lib/blog';
import dynamic from 'next/dynamic';

const Hero = dynamic(() =>
  import('@/components/feature-modules/landing/hero/components/hero').then((m) => m.Hero),
);
const DashboardShowcase = dynamic(() =>
  import('@/components/feature-modules/landing/dashboard/components/dashboard-showcase').then(
    (m) => m.DashboardShowcase,
  ),
);
const CrossDomainIntelligence = dynamic(() =>
  import('@/components/feature-modules/landing/cross-domain-intelligence/cross-domain-section').then(
    (m) => m.CrossDomainIntelligence,
  ),
);
const TimeSaved = dynamic(() =>
  import('@/components/feature-modules/landing/time-saved/components/time-saved').then(
    (m) => m.TimeSaved,
  ),
);
const ChurnRetrospective = dynamic(() =>
  import('@/components/feature-modules/landing/churn-retrospective/churn-retro').then(
    (m) => m.ChurnRetrospective,
  ),
);
const DailyActions = dynamic(() =>
  import('@/components/feature-modules/landing/actions/components/daily-actions').then(
    (m) => m.DailyActions,
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
      <DashboardShowcase />
      <CrossDomainIntelligence />
      <TimeSaved />
      <ChurnRetrospective />
      <DailyActions />
      <FeaturedPosts featured={featured} recent={recent} />
      <Faq preview />
      <Waitlist />
    </main>
  );
}
