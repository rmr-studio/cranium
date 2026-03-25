import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '@/components/ui/accordion';
import { Section } from '@/components/ui/section';
import Link from 'next/link';
import { ReactNode } from 'react';

interface Question {
  question: string;
  answer: ReactNode;
}

const FAQ: Question[] = [
  {
    question: 'How is this different from Notion, Airtable or other tool?',
    answer: `
      All of these tools are extremely great at what they do, but they each own and operate within a single domain.
      Notion and Airtables are fantastic databases but they don't have access to the data in your email or CRM. 
      Stripe knows about your payments, but not your customer conversations.
      Intercom knows your support history, but doesn't know where each customer came from. 
      None of them see the full picture. Riven fixes this by creating one singular data ecosystem to connect your tools together seamlessly, with an AI layer that reasons across everything`,
  },
  {
    question: 'What is a customer lifecycle intelligence platform?',
    answer: `
    No. Riven sits alongside, and wraps your existing stack, providing integration capabilities to connect your tools together in one platform. 
    Riven builds context by linking your data together with meaningful relationships, surfacing patterns and insights that would be impossible to find across multiple disconnected tools.`,
  },
  {
    question: 'Does Riven replace my existing tools?',
    answer: `
    No. Riven sits alongside, and wraps your existing stack, providing integration capabilities to connect your tools together in one platform. 
    Riven builds context by linking your data together with meaningful relationships, surfacing patterns and insights that would be impossible to find across multiple disconnected tools.`,
  },
  {
    question: 'Who uses Riven?',
    answer: `
    Riven is built for scaling consumer-facing businesses that have outgrown their current tooling. 
    If you're constantly switching between tabs, manually cross-referencing data across tools to make decisions, and feeling like you've become the human integration layer holding it all together, Riven is the platform built for your stage. 
    Designed for the gap between lightweight startup tools and overbuilt enterprise platforms.`,
  },
  {
    question: 'How does Riven ensure AI answers are accurate?',
    answer: `
    Riven is built for scaling consumer-facing businesses that have outgrown their current tooling. 
    If you're constantly switching between tabs, manually cross-referencing data across tools to make decisions, and feeling like you've become the human integration layer holding it all together, Riven is the platform built for your stage. 
    Designed for the gap between lightweight startup tools and overbuilt enterprise platforms.`,
  },
  {
    question: 'Can business users access data and patterns without knowing SQL?',
    answer: `
    Riven is built for scaling consumer-facing businesses that have outgrown their current tooling. 
    If you're constantly switching between tabs, manually cross-referencing data across tools to make decisions, and feeling like you've become the human integration layer holding it all together, Riven is the platform built for your stage. 
    Designed for the gap between lightweight startup tools and overbuilt enterprise platforms.`,
  },
  {
    question: 'How is Riven different from just using N8Ns/Zapier and ChatGPT?',
    answer: `
    Riven is built for scaling consumer-facing businesses that have outgrown their current tooling. 
    If you're constantly switching between tabs, manually cross-referencing data across tools to make decisions, and feeling like you've become the human integration layer holding it all together, Riven is the platform built for your stage. 
    Designed for the gap between lightweight startup tools and overbuilt enterprise platforms.`,
  },
  {
    question: 'How is Riven different than other Analytics tools?',
    answer: `
    Riven is built for scaling consumer-facing businesses that have outgrown their current tooling. 
    If you're constantly switching between tabs, manually cross-referencing data across tools to make decisions, and feeling like you've become the human integration layer holding it all together, Riven is the platform built for your stage. 
    Designed for the gap between lightweight startup tools and overbuilt enterprise platforms.`,
  },

  {
    question: 'What integrations do you support? Do you support custom integrations?',
    answer: `
      At launch, we plan to support tooling across CRM, payments, support, communication, and marketing based domains. 
      Including tools like Stripe, HubSpot, Intercom, Gmail, Slack, and Google Ads. 
      We are prioritizing integrations based directly on waitlist feedback to ensure that teams can immediately get access to the tools they care about.
      So if your most important tool isn't listed, let us know in the form. We also plan to support webhooks and a REST API for custom data sources, with more support, and options coming post-launch.`,
  },
  {
    question: 'Is my data secure?',
    answer: `
    Your data is encrypted at rest and in transit, and we will never use your data to train AI models. 
    Riven is open source, so you can inspect the system to see exactly how your data is handled. 
    For teams that need full control, self-hosting is available so your data never leaves your own infrastructure. We're working toward SOC 2 compliance ahead of launch.`,
  },
  {
    question: 'What happens when I join the waitlist?',
    answer: `
      You will be asked a few questions that will help us better understand your issues, tool stack and priorities. 
      Once you're on the list, you'll receive periodic updates as we build and will be one of the first to secure early access when we launch. 
      Founding cohort members who opted into early testing will be invited to try the platform before it goes live. 
      Allowing you to directly shape the app, and what ships first. 
      Waitlist members will also lock in early-access pricing, with generous discounts up to 50% off their first year.
      We are also offering direct communication with anyone who wants to go deeper on their use case and help shape the product. 
      More involvement means earlier access, and a bigger say in what features and integrations we prioritize.
      `,
  },
];

const PREVIEW_COUNT = 5;

interface FaqProps {
  preview?: boolean;
}

export const Faq = ({ preview = false }: FaqProps) => {
  const items = preview ? FAQ.slice(0, PREVIEW_COUNT) : FAQ;

  return (
    <Section id="faqs" size={24} lazyRender>
      <div className="clamp relative z-10 flex flex-col">
        <h2 className="text-center text-4xl leading-none tracking-tighter text-heading md:text-5xl">
          Frequently Asked <span className="font-serif font-normal italic">Questions</span>
        </h2>
        <Accordion type="single" collapsible className="mx-auto mt-10 w-full max-w-4xl px-4">
          {items.map((item, i) => (
            <AccordionItem key={i} value={`item-${i}`}>
              <AccordionTrigger className="text-sm sm:text-base">{item.question}</AccordionTrigger>
              <AccordionContent className="text-muted-foreground">{item.answer}</AccordionContent>
            </AccordionItem>
          ))}
        </Accordion>
        {preview && (
          <Link
            href="/resources/faq"
            className="mx-auto mt-8 text-sm text-muted-foreground transition-colors hover:text-foreground"
          >
            View all questions &rarr;
          </Link>
        )}
      </div>
    </Section>
  );
};
