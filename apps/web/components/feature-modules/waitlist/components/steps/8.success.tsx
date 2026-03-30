import { CheckCircle2 } from 'lucide-react';
import { motion } from 'motion/react';

export function SuccessStep({ completedSurvey }: { completedSurvey: boolean }) {
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
      <h3 className="mt-6 text-2xl font-normal text-heading md:text-3xl">You are on the list!</h3>
      <p className="mx-auto mt-3 max-w-md text-muted-foreground">
        {completedSurvey
          ? "Thanks for helping shape the product. We'll use your input to prioritize what we build."
          : "Thanks for joining the waitlist. We can't wait to share updates and exciting features with you."}
      </p>
    </div>
  );
}
