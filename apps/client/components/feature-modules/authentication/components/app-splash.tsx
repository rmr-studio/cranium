'use client';

import { Logo } from '@cranium/ui';
import { motion } from 'motion/react';

export const AppSplash = () => {
  return (
    <motion.div
      key="splash"
      initial={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.45, ease: [0.4, 0, 0.2, 1] }}
      className="fixed inset-0 z-50 flex items-center justify-center bg-background"
    >
      <div className="flex flex-col items-center gap-8">
        <motion.div
          initial={{ opacity: 0, scale: 0.92 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ duration: 0.5, ease: [0.16, 1, 0.3, 1] }}
        >
          <Logo size={96} />
        </motion.div>

        <div className="h-1 w-40 overflow-hidden rounded-full bg-border">
          <motion.div
            className="h-full rounded-full bg-foreground/40"
            initial={{ width: '0%' }}
            animate={{ width: '70%' }}
            transition={{
              duration: 2.4,
              ease: [0.4, 0, 0.2, 1],
            }}
          />
        </div>
      </div>
    </motion.div>
  );
};
