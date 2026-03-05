'use client';

import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@riven/ui';
import { FCWC } from '@/lib/interfaces/interface';
import { JSX, useEffect, useRef, useState } from 'react';

interface Props {
  render: () => JSX.Element; // The content that will be displayed in the tooltip
}

/**
 * This is a tooltip wrapper for content that should only display a tooltip, if the initial content itself
 * has been truncated via a tailwind class
 *
 * @returns A tooltip that displays the full text when the content is truncated.
 */
export const TruncatedTooltip: FCWC<Props> = ({ render, children }) => {
  const ref = useRef<HTMLDivElement>(null);
  const [isTruncated, setIsTruncated] = useState(false);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;

    const check = () => {
      setIsTruncated(el.scrollWidth > el.clientWidth);
    };

    check();

    const resizeObserver = new ResizeObserver(check);
    resizeObserver.observe(el);

    return () => resizeObserver.disconnect();
  }, [children]);

  return (
    <TooltipProvider>
      {isTruncated ? (
        <Tooltip>
          <TooltipTrigger asChild>{children}</TooltipTrigger>
          <TooltipContent>{render()}</TooltipContent>
        </Tooltip>
      ) : (
        children
      )}
    </TooltipProvider>
  );
};
