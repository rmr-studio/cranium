'use client';

import { useEffect, useState } from 'react';

type Phase = 'waiting' | 'typing' | 'shown' | 'selecting' | 'empty';

interface TypewriterState {
  promptIndex: number;
  charCount: number;
  phase: Phase;
}

export interface TypewriterResult {
  text: string;
  phase: Phase;
  selected: boolean;
}

export const useTypewriterCycle = (prompts: string[], startDelay = 0): TypewriterResult => {
  const [state, setState] = useState<TypewriterState>({
    promptIndex: 0,
    charCount: 0,
    phase: 'waiting',
  });

  const prompt = prompts[state.promptIndex];

  useEffect(() => {
    let id: ReturnType<typeof setTimeout>;

    switch (state.phase) {
      case 'waiting':
        id = setTimeout(() => setState((s) => ({ ...s, phase: 'typing' })), startDelay);
        break;

      case 'typing':
        if (state.charCount < prompt.length) {
          id = setTimeout(
            () => setState((s) => ({ ...s, charCount: s.charCount + 1 })),
            12 + Math.random() * 14,
          );
        } else {
          id = setTimeout(() => setState((s) => ({ ...s, phase: 'shown' })), 0);
        }
        break;

      case 'shown':
        id = setTimeout(() => setState((s) => ({ ...s, phase: 'selecting' })), 2500);
        break;

      case 'selecting':
        id = setTimeout(
          () =>
            setState((s) => ({
              promptIndex: (s.promptIndex + 1) % prompts.length,
              charCount: 0,
              phase: 'empty',
            })),
          600,
        );
        break;

      case 'empty':
        id = setTimeout(() => setState((s) => ({ ...s, phase: 'typing' })), 500);
        break;
    }

    return () => clearTimeout(id);
  }, [state.phase, state.charCount, prompt, prompts, startDelay]);

  return {
    text: state.phase === 'empty' ? '' : prompt.slice(0, state.charCount),
    phase: state.phase,
    selected: state.phase === 'selecting',
  };
};
