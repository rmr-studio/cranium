import { OkButton } from '@/components/feature-modules/waitlist/components/ok-button';
import { CheckCircle2 } from 'lucide-react';
import { motion } from 'motion/react';

export function BridgeStep({ onContinue, onSkip }: { onContinue: () => void; onSkip: () => void }) {
  return (
    <div className="py-16 text-center md:py-24">
      <motion.div
        initial={{ scale: 0 }}
        animate={{ scale: 1 }}
        transition={{
          type: 'spring',
          stiffness: 200,
          damping: 15,
          delay: 0.1,
        }}
      >
        <CheckCircle2 className="mx-auto h-14 w-14 text-teal-500" />
      </motion.div>
      <h3 className="mt-6 text-2xl font-normal text-heading md:text-3xl">You&apos;re in.</h3>
      <p className="mx-auto mt-3 max-w-md text-muted-foreground">
        Want to help shape what we build first? These 4 quick questions directly influence our
        roadmap.
      </p>
      <div className="mt-10 flex flex-col items-center gap-4">
        <OkButton onClick={onContinue} label="Help shape the product" />
        <button
          type="button"
          onClick={onSkip}
          className="cursor-pointer text-sm text-muted-foreground transition-colors hover:text-foreground"
        >
          Skip for now
        </button>
      </div>
    </div>
  );
}
