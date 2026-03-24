import { DailyActions } from '@/components/feature-modules/actions/components/daily-actions';
import { FeaturedPosts } from '@/components/feature-modules/blogs/components/featured-posts';
import { ChurnRetrospective } from '@/components/feature-modules/churn-retrospective/churn-retro';
import { DashboardShowcase } from '@/components/feature-modules/hero/components/dashboard/dashboard-showcase';
import { Hero } from '@/components/feature-modules/hero/components/hero';
import { getAllPosts, getFeaturedPost } from '@/lib/blog';
import dynamic from 'next/dynamic';

const CrossDomainIntelligence = dynamic(() =>
  import('@/components/feature-modules/cross-domain-intelligence/cross-domain-section').then(
    (m) => m.CrossDomainIntelligence,
  ),
);
const TimeSaved = dynamic(() =>
  import('@/components/feature-modules/time-saved/components/time-saved').then((m) => m.TimeSaved),
);
const Faq = dynamic(() =>
  import('@/components/feature-modules/faq/components/faq').then((m) => m.Faq),
);

const Waitlist = dynamic(() =>
  import('@/components/feature-modules/waitlist/components/waitlist').then((m) => m.Waitlist),
);

export default async function Home() {
  const [featured, posts] = await Promise.all([getFeaturedPost(), getAllPosts()]);
  const recent = posts.filter((p) => p.slug !== featured?.slug).slice(0, 2);

  return (
    <main className="min-h-screen overflow-x-hidden">
      <Hero />
      <DashboardShowcase />
      <CrossDomainIntelligence />
      <TimeSaved />
      <ChurnRetrospective />
      <DailyActions />
      {featured && <FeaturedPosts featured={featured} recent={recent} />}
      <Faq />
      <Waitlist />
    </main>
  );
}
