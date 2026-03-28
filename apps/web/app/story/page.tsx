'use client';

import { Story } from '@/components/feature-modules/story/components/story';
import dynamic from 'next/dynamic';

const Waitlist = dynamic(() =>
  import('@/components/feature-modules/waitlist/components/waitlist').then((m) => m.Waitlist),
);

export default function Home() {
  return (
    <main className="min-h-screen overflow-x-clip">
      <Story />
      <Waitlist />
    </main>
  );
}
