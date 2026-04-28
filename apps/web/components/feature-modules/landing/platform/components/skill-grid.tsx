'use client';

import { cn } from '@riven/utils';
import { FC } from 'react';

import type { SkillCard } from '../data/use-case-types';

interface Props {
  skills: SkillCard[];
  className?: string;
}

export const SkillGrid: FC<Props> = ({ skills, className }) => (
  <div className={cn('flex flex-col gap-4', className)}>
    {skills.map((skill) => (
      <article
        key={skill.title}
        className="rounded-md bg-amber-50/50 p-5 shadow-lg backdrop-blur-2xl"
      >
        <header className="flex items-center gap-2.5">
          <span className="grid size-8 shrink-0 place-items-center rounded-lg border border-border/60 bg-muted/40 text-foreground">
            {skill.icon}
          </span>
          <h3 className="text-sm font-semibold text-foreground">{skill.title}</h3>
        </header>

        <ul className="mt-3 flex flex-col gap-1.5">
          {skill.bullets.map((bullet, i) => (
            <li key={i} className="flex gap-2 text-xs leading-relaxed text-muted-foreground">
              <span
                aria-hidden
                className="mt-1.5 size-1 shrink-0 rounded-full bg-muted-foreground/50"
              />
              <span className="min-w-0">{bullet}</span>
            </li>
          ))}
        </ul>
      </article>
    ))}
  </div>
);
