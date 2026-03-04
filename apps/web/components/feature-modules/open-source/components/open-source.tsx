'use client';

import { Section } from '@/components/ui/section';
import { Github, Loader2, Star } from 'lucide-react';
import Link from 'next/link';
import { useGitHubStars } from '../query/use-github-stars';

const GITHUB_URL = 'https://github.com/rmr-studio/riven';

function formatStars(count: number): string {
  if (count >= 1000) {
    return `${(count / 1000).toFixed(1)}K`;
  }
  return count.toString();
}

export function OpenSource() {
  const { data: stars, isLoading } = useGitHubStars();

  return (
    <Section id="open-source" size={24}>
      <div className="content-container relative z-10 flex flex-col items-center justify-center text-center">
        <h2 className="font-serif text-4xl leading-[0.95] font-normal tracking-tight text-heading sm:text-4xl md:text-5xl">
          Open source from day one
        </h2>

        <div className="mt-6 max-w-4xl text-lg text-muted-foreground sm:text-xl">
          Riven is built in the open on GitHub. We believe that transparency fosters trust.
          Self-host your own instance, inspect every line of code, or help us foster and grow a
          community of contributors, working towards to the vision we are building.
        </div>

        <Link
          href={GITHUB_URL}
          target="_blank"
          rel="noopener noreferrer"
          className="mt-10 flex items-center gap-3 rounded-full border border-border/80 bg-muted/50 px-5 py-2.5 text-sm font-medium text-foreground transition-colors hover:bg-muted"
        >
          <Github className="size-5" />
          <span>@riven</span>
          <span className="h-4 w-px bg-border" />
          <span className="flex items-center gap-1">
            {isLoading ? (
              <Loader2 className="size-3.5 animate-spin" />
            ) : stars !== undefined ? (
              <>
                <Star className="size-3.5 fill-current" />
                {formatStars(stars)}
              </>
            ) : null}
          </span>
        </Link>
      </div>
    </Section>
  );
}
