import {
  NavigationMenuContent,
  NavigationMenuItem,
  NavigationMenuTrigger,
} from '@/components/ui/navigation-menu';
import { BookOpen, FileQuestionMark, FileText } from 'lucide-react';
import Link from 'next/link';
import { BGPattern } from '../ui/background/grids';
import { ShaderContainer, ThemeStaticImages } from '../ui/shader-container';
import { DropdownListItem } from './dropdown-list-item';

export function ResourcesMenu() {
  const gradients: ThemeStaticImages = {
    light: 'images/texture/static-gradient-4.webp',
    dark: 'images/texture/static-gradient-4.webp',
    amber: 'images/texture/static-gradient-4.webp',
  };

  const shaders = {
    light: {
      base: '#7d1441',
      colors: ['#1a6080', '#3a8aaa', '#78b8cc'] as [string, string, string],
    },
    dark: {
      base: '#7d1441',
      colors: ['#0f3d5c', '#1a2a3f', '#0d1f2d'] as [string, string, string],
    },
    amber: {
      base: '#7d1441',
      colors: ['#2a6878', '#4a8a8e', '#7ab0a8'] as [string, string, string],
    },
  };

  return (
    <NavigationMenuItem>
      <NavigationMenuTrigger className="bg-transparent text-muted-foreground hover:text-foreground data-[state=open]:text-foreground">
        Resources
      </NavigationMenuTrigger>
      <NavigationMenuContent>
        <BGPattern
          variant="dots"
          size={12}
          fill="color-mix(in srgb, var(--foreground) 20%, transparent)"
          mask="none"
          className="z-20"
          style={{
            maskImage:
              'radial-gradient(ellipse at center, black 30%, transparent 75%), linear-gradient(to bottom, black 0%, black 40%, transparent 65%)',
            maskComposite: 'intersect',
            WebkitMaskImage:
              'radial-gradient(ellipse at center, black 30%, transparent 75%), linear-gradient(to bottom, black 0%, black 40%, transparent 65%)',
            WebkitMaskComposite: 'source-in' as string,
          }}
        />
        <div className="flex w-128 flex-col">
          <h4 className="px-3 pt-1 pb-4 font-display text-xs tracking-widest text-muted-foreground uppercase">
            Get Started
          </h4>
          <section className="flex w-full list-none gap-1">
            <div className="h-full w-full">
              <DropdownListItem
                label="FAQ"
                description="Find answers to common questions about Riven"
                href="/resources/faq"
                icon={FileQuestionMark}
              />
            </div>
            <div className="h-full w-full">
              <DropdownListItem
                label="Changelog"
                description="What we shipping"
                href="/resources/blog/category/changelog"
                icon={FileText}
              />
            </div>
          </section>

          <ShaderContainer
            className="relative z-0 mt-4! ml-0! px-0 py-4"
            staticImages={gradients}
            shaders={shaders}
          >
            <Link
              href={'/resources/blogs'}
              className="z-20 flex w-auto grow flex-row items-center gap-3 rounded-sm p-3 transition-colors"
            >
              <BookOpen className="size-10 text-white" />

              <div className="flex w-full flex-col gap-0.5 font-display">
                <span className="text-xl leading-tight font-semibold tracking-tighter text-white">
                  Blogs
                </span>
                <span className="text-base leading-snug tracking-tight text-white">
                  Guides, insights and updates
                </span>
              </div>
            </Link>
          </ShaderContainer>
        </div>
      </NavigationMenuContent>
    </NavigationMenuItem>
  );
}
