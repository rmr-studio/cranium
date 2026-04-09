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
    question: 'What does Riven actually do?',
    answer: `From marketing, to sales, to product usage, to support, and to the moment your customer picks up their stuff and leaves. Each of these tools will only ever see its own slice. Stripe sees payments, Intercom sees tickets, Google Ads sees clicks. Nobody sees the whole customer.
    A customer lifecycle intelligence platform connects all of them so you can follow someone from the ad they clicked
    to the subscription they cancelled. Riven does that, and gives your team a workspace to do something about what it finds.`,
  },
  {
    question: 'Who uses Riven?',
    answer: `
    B2C SaaS and DTC e-commerce teams that don't have a data person. Maybe you're the founder who is also somehow the entire analytics department.
    Maybe you're the head of ops who spends half the week pulling reports instead of acting on them.
    You've outgrown your starter tools but you're nowhere near a six-figure Looker contract.
    You just need to know what's going on without it becoming your whole day.`,
  },
  {
    question: 'Does Riven replace my existing tools?',
    answer: `
    No. Riven wraps around the tools you already use. It connects them and links the data between them
    so you can see relationships and patterns that are impossible to spot when everything lives in separate tabs.`,
  },
  {
    question: 'How is this different from Notion, Airtable or other tool?',
    answer: `
      Notion and Airtable are great databases, but they can only see what you put into them.
      Stripe knows your payments but not your customer conversations.
      Intercom knows your support history but has no idea where each customer came from.
      They're all good at their own thing. None of them see the full picture.
      Riven connects them into one shared data layer with an AI that can reason across all of it at once.`,
  },
  {
    question: 'How does Riven ensure AI answers are accurate?',
    answer: `
    The AI only works with your actual connected data. It's not guessing from training data.
    Every answer links back to the source records so you can check what it's referencing.
    You can also set your own rules and thresholds for alerts, so automated flags are based on numbers you defined, not some black box.`,
  },
  {
    question: 'What happens when I join the waitlist?',
    answer: `
      We'll ask a few questions about your tool stack and what's giving you headaches.
      After that you'll get periodic updates as we build, and you'll be first in line for early access.
      If you opt into early testing, you can try the platform before launch and have a direct say in what ships first.
      Waitlist members also lock in early-access pricing, up to 50% off their first year.
      Want to go deeper? We're happy to talk through your use case directly. More involvement means earlier access.`,
  },
  {
    question: 'Can business users access data and patterns without knowing SQL?',
    answer: `
    Yes. Anyone on your team can ask questions in plain English like "Which customers from our March campaign have an open support ticket?"
    and get an actual list back. No SQL, no exports. There are also visual filters, saved views, and tagging for people who prefer to click around.`,
  },
  {
    question: 'How is Riven different from just using N8N/Zapier and ChatGPT?',
    answer: `You can absolutely pipe tools into ChatGPT with Zapier and get useful answers. What you can't do is build memory.    
  Riven learns as it goes — every pattern it finds, every action you take, every outcome that follows — all stays 
  connected to your data model. By month three it knows things about your business that a one-shot prompt never will.  
  It doesn't just see what's happening now. It sees it in the context of everything that came before.    `,
  },
  {
    question: 'How is Riven different from other analytics tools?',
    answer: `
    Most analytics tools show you charts about what happened. Many data sources. One flat dashboard.
    Riven connects all of your tools and lets you go further: tag accounts, set rules, push segments to other platforms, and track what happens next.
    It's a place your team works from, not just looks at.`,
  },

  {
    question: 'What integrations do you support? Do you support custom integrations?',
    answer: `
      At launch we're covering CRM, payments, support, comms, and marketing. Think Stripe, HubSpot, Intercom, Gmail, Slack, Google Ads.
      We're picking which to build first based on what the waitlist tells us, so if your most important tool isn't on the list, mention it in the form.
      We'll also support webhooks and a REST API for custom data sources after launch.`,
  },
  {
    question: 'Is my data secure?',
    answer: `
    Encrypted at rest and in transit. We will never use your data to train AI models.
    Riven is open source, so you can read the code and see exactly how your data is handled.
    If you need full control, self-hosting is an option. Your data never has to leave your infrastructure. We're also working toward SOC 2 before launch.`,
  },
];

const PREVIEW_COUNT = 6;

interface FaqProps {
  preview?: boolean;
}

export const Faq = ({ preview = false }: FaqProps) => {
  const items = preview ? FAQ.slice(0, PREVIEW_COUNT) : FAQ;

  return (
    <Section id="faqs" size={24}>
      <div className="clamp relative z-10 flex flex-col">
        <h2 className="text-center font-serif text-4xl leading-none tracking-tighter text-heading md:text-6xl">
          Frequently Asked Questions
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
