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
    <Section id="churn-retrospectives" size={24} lazyRender>
      <div className="clamp">
        <div className="space-y-10 md:space-y-14">
          <SectionDivider>Churn Retrospectives</SectionDivider>

          <div className="max-w-4xl px-4 sm:px-8 xl:max-w-6xl">
            <h2 className="font-sans text-3xl tracking-tighter text-primary md:text-4xl lg:text-5xl">
              Every problem leaves a trace.{' '}
              <span className="font-serif font-normal italic">Find the trail.</span>
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
          className="z-50 rounded-r-none lg:rounded-r-lg"
        >
          <ChurnShowcase />
        </ShaderContainer>
      </div>
    </Section>
  );
};
