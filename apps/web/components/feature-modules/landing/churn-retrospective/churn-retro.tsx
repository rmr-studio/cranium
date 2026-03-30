import { ChurnShowcase } from '@/components/feature-modules/landing/churn-retrospective/churn-showcase-graphic';
import { Section } from '@/components/ui/section';
import { SectionDivider } from '@/components/ui/section-divider';
import { ShaderContainer, ThemeStaticImages } from '@/components/ui/shader-container';

const churnShaders = {
  light: {
    base: '#c4a882',
    colors: ['#9e4a5c', '#b87a8a', '#d4b5bc'] as [string, string, string],
  },
  dark: {
    base: '#8dbaa4',
    colors: ['#3d1a2a', '#2d1e28', '#1e1218'] as [string, string, string],
  },
  amber: {
    base: '#868ba4',
    colors: ['#7a3040', '#9a5868', '#c08898'] as [string, string, string],
  },
};

export const ChurnRetrospective = () => {
  const gradients: ThemeStaticImages = {
    light: 'images/texture/static-gradient-4.webp',
    dark: 'images/texture/static-gradient-4.webp',
    amber: 'images/texture/static-gradient-4.webp',
  };

  return (
    <Section id="churn-retrospectives" size={24}>
      <div className="clamp">
        <div className="space-y-10 md:space-y-14">
          <SectionDivider>Churn Retrospectives</SectionDivider>

          <div className="max-w-4xl px-4 sm:px-8 xl:max-w-6xl">
            <h2 className="font-serif text-3xl leading-none tracking-tighter text-primary md:text-4xl lg:text-6xl">
              Every problem leaves a trace. Find the trail.
            </h2>

            <p className="mt-4 max-w-4xl text-sm leading-none tracking-tight text-content/80 md:text-base">
              Uncover the hidden patterns and root causes behind every churn. Riven automatically
              surfaces the key moments, behaviors, and trends that lead to their departure. Stitched
              together from every tool you&apos;ve connected. Understand why customers are leaving,
              and identify the most impactful actions to keep them around.
            </p>
          </div>
        </div>

        <ShaderContainer
          staticImages={gradients}
          shaders={churnShaders}
          className="z-50 rounded-r-none p-0 shadow-lg shadow-foreground/40 lg:rounded-r-lg dark:shadow-none"
        >
          <section className="p-4 lg:p-12">
            <div className="pointer-events-none absolute inset-y-0 left-0 z-10 hidden w-24 bg-gradient-to-r from-black/60 via-black/25 to-transparent md:w-40 3xl:block" />
            <div className="pointer-events-none absolute inset-y-0 right-0 z-10 w-24 bg-gradient-to-l from-black/60 via-black/25 to-transparent md:w-40" />
            <div className="pointer-events-none absolute inset-0 z-10 opacity-60 shadow-[inset_20px_0_40px_rgba(0,0,0,0.5),inset_-20px_0_40px_rgba(0,0,0,0.5)] md:shadow-[inset_32px_0_60px_rgba(0,0,0,0.55),inset_-32px_0_60px_rgba(0,0,0,0.25)]" />
            <ChurnShowcase />
          </section>
        </ShaderContainer>
      </div>
    </Section>
  );
};
