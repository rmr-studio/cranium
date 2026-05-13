import { Section } from '@/components/ui/section';
import { ShaderContainer, type ShaderColors } from '@/components/ui/shader-container';
import { ArrowRight, BookOpen } from 'lucide-react';
import Link from 'next/link';

export const Story = () => {
  const gradient = 'images/texture/static-gradient-4.webp';

  const shaders: ShaderColors = {
    base: '#7d1441',
    colors: ['#1a6080', '#3a8aaa', '#78b8cc'],
  };

  return (
    <Section id="story" size={24} className="mt-18">
      <article className="relative z-10 mx-auto flex max-w-5xl flex-col px-6 lg:px-8">
        <h1 className="text-center font-sans font-serif text-4xl leading-none tracking-tighter text-heading sm:text-6xl">
          Why I am building Cranium
        </h1>

        <ShaderContainer
          className=":border :shadow-none relative z-0 mt-8 mb-4 ml-0! px-0 py-4 shadow-lg shadow-foreground/40"
          staticImage={gradient}
          shaders={shaders}
        >
          <Link
            href={'/resources/blog/why-i-am-building-cranium'}
            className="z-20 flex items-center gap-3 rounded-sm px-2.5 py-1.5 transition-colors"
          >
            <BookOpen className="size-6 text-white" />

            <div className="mb-0.5 flex items-center font-display text-xl text-white">
              Read the original post
              <ArrowRight className="mt-0.5 ml-2 size-4 transition-transform group-hover:translate-x-0.5" />
            </div>
          </Link>
        </ShaderContainer>

        <section className="mt-8 space-y-6 text-base leading-relaxed tracking-tight text-content/80 md:text-lg md:leading-relaxed">
          <h2 className="text-xl font-semibold tracking-tight text-heading">The problem</h2>

          <p>
            Running a company, building new features, growing your audience base, keeping them
            happy. Every one of these is a full-time job. And for each one, there&apos;s a tool.
            Probably a good one. Maybe even a great one.
          </p>

          <p>
            But here is the thing no one talks about. These tools assume you have systems, or quite
            frankly, egregious amounts of time to bring the data from each and every platform all
            together to make any sort of decision. Your marketing analytics live in five different
            platforms. Your support tickets live somewhere else. Your payment data sits in Stripe.
            Your product usage or Shopify metrics sit in yet another dashboard. And the moment a
            customer churns, you are scrambling to find out whether or not this was an isolated
            incident, even though you know it&apos;s probably not.
          </p>

          <p>
            The rise of SaaS gave businesses access to expert-level functionality overnight. AI and
            automation made it possible for a single person to run what used to take an entire team.
            But all of it assumes you have the time, or the systems, to pull it all together. And
            most of us don&apos;t. We just become the glue. Opening tab after tab, cross-referencing
            dashboards, copying numbers into spreadsheets, trying to assemble some version of the
            truth.
          </p>

          <p>
            For teams who have better things to do, marketing campaigns to run, customers to talk
            to, or for someone trying to run a company alone — this overhead adds up quickly. You
            are not lacking information. You are lacking a system that keeps everything connected.
          </p>

          <p className="text-content">
            Your time is too valuable to be spent as the glue holding your business together.
          </p>
        </section>

        <hr className="mx-auto my-8 w-48 border-border" />

        <section className="space-y-6 text-base leading-relaxed tracking-tight text-content/80 md:text-lg md:leading-relaxed">
          <h2 className="text-xl font-semibold tracking-tight text-heading">The idea</h2>

          <p>
            I&apos;m not building another tool, AI wrapper or measly dashboard that you add to the
            pile and forget about. I am building a system where everything stays connected,
            automatically interlinked, concepts and patterns shared from one tool to another to
            truly redefine what it means for a business to be unified.
          </p>

          <p>
            When a customer churns, you shouldn&apos;t need to open six tabs to piece together why.
            The pattern was already there. Their acquisition channel, their struggles during
            onboarding, the numerous support tickets they filed, the usage that got smaller and
            smaller each week. It was all painting a story for you, the chapters just weren&apos;t
            pieced together.
          </p>

          <p>
            Cranium connects it. Not by replacing your tools, but by sitting between them and building
            the relationships they can&apos;t build on their own — covering the full consumer
            lifecycle. From the ad that brought someone in to the moment they get frustrated enough
            to leave. The data always visible, always traceable, always ready to tell you
            what&apos;s actually happening.
          </p>
        </section>

        <hr className="mx-auto my-8 w-48 border-border" />

        <section className="space-y-6 text-base leading-relaxed tracking-tight text-content/80 md:text-lg md:leading-relaxed">
          <h2 className="text-xl font-semibold tracking-tight text-heading">The focus</h2>

          <p>
            Cranium is built for the B2C SaaS or DTC E-commerce founder who is also somehow the entire
            data team. The head of ops who spends half their week pulling reports instead of acting
            on them. The solo operator running the entire business and somehow still expected to
            &ldquo;be data-dcranium&rdquo; without a single analyst on payroll.
          </p>

          <p>
            I&apos;m not building for enterprise. I&apos;m not building for companies with dedicated
            BI teams and six-figure Looker contracts. I&apos;m building for the people doing the
            work, who need lifecycle visibility without the overhead.
          </p>

          <div>
            <p className="mb-4">That means:</p>
            <ul className="space-y-2 pl-5">
              <li className="relative pl-3 before:absolute before:top-2.5 before:left-0 before:size-1 before:rounded-full before:bg-primary/50">
                Patterns that surface themselves, not ones you have to go hunting for
              </li>
              <li className="relative pl-3 before:absolute before:top-2.5 before:left-0 before:size-1 before:rounded-full before:bg-primary/50">
                A system that gets smarter as more data flows through, not one that demands more
                configuration
              </li>
              <li className="relative pl-3 before:absolute before:top-2.5 before:left-0 before:size-1 before:rounded-full before:bg-primary/50">
                Answers that are ready before you think to ask the question
              </li>
              <li className="relative pl-3 before:absolute before:top-2.5 before:left-0 before:size-1 before:rounded-full before:bg-primary/50">
                Zero tolerance for &ldquo;just check the dashboard&rdquo; as a solution
              </li>
            </ul>
          </div>

          <p className="text-content">
            Your tools already have the answers. They just don&apos;t talk to each other. Yet.
          </p>
        </section>

        <hr className="mx-auto my-16 w-48 border-border" />

        <div className="flex justify-center">
          <Link
            href="/resources/blog"
            className="group flex items-center gap-2 font-mono text-sm tracking-wide text-muted-foreground uppercase transition-colors hover:text-heading"
          >
            Read more on the blog
            <ArrowRight className="size-4 transition-transform group-hover:translate-x-0.5" />
          </Link>
        </div>
      </article>
    </Section>
  );
};
