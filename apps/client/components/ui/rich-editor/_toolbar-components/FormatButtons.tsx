'use client';

import React from 'react';
import { Bold, Code, Italic, Strikethrough, Underline } from 'lucide-react';

import { cn } from '@riven/utils';

import { Button } from '../../button';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '../../tooltip';

interface FormatButtonsProps {
  formats: {
    bold: boolean;
    italic: boolean;
    underline: boolean;
    strikethrough: boolean;
    code: boolean;
  };
  onFormat: (format: 'bold' | 'italic' | 'underline' | 'strikethrough' | 'code') => void;
  size?: 'sm' | 'md';
}

export function FormatButtons({ formats, onFormat, size = 'md' }: FormatButtonsProps) {
  const buttonSize = size === 'sm' ? 'h-7 w-7' : 'h-8 w-8';

  return (
    <TooltipProvider delayDuration={300}>
      <div className="flex items-center gap-1 border-x border-x-border/50 px-2">
        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              variant="ghost"
              size="icon"
              className={cn(
                buttonSize,
                'rounded-md transition-colors duration-75 hover:bg-accent/50',
                formats.bold &&
                  'bg-blue-100 text-blue-600 hover:bg-blue-100 dark:bg-blue-900/30 dark:text-blue-400 dark:hover:bg-blue-900/30',
              )}
              onClick={() => onFormat('bold')}
            >
              <Bold className="h-4 w-4" />
            </Button>
          </TooltipTrigger>
          <TooltipContent>
            <p>Bold (Ctrl+B)</p>
          </TooltipContent>
        </Tooltip>

        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              variant="ghost"
              size="icon"
              className={cn(
                buttonSize,
                'rounded-md transition-colors duration-75 hover:bg-accent/50',
                formats.italic &&
                  'bg-blue-100 text-blue-600 hover:bg-blue-100 dark:bg-blue-900/30 dark:text-blue-400 dark:hover:bg-blue-900/30',
              )}
              onClick={() => onFormat('italic')}
            >
              <Italic className="h-4 w-4" />
            </Button>
          </TooltipTrigger>
          <TooltipContent>
            <p>Italic (Ctrl+I)</p>
          </TooltipContent>
        </Tooltip>

        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              variant="ghost"
              size="icon"
              className={cn(
                buttonSize,
                'rounded-md transition-colors duration-75 hover:bg-accent/50',
                formats.underline &&
                  'bg-blue-100 text-blue-600 hover:bg-blue-100 dark:bg-blue-900/30 dark:text-blue-400 dark:hover:bg-blue-900/30',
              )}
              onClick={() => onFormat('underline')}
            >
              <Underline className="h-4 w-4" />
            </Button>
          </TooltipTrigger>
          <TooltipContent>
            <p>Underline (Ctrl+U)</p>
          </TooltipContent>
        </Tooltip>

        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              variant="ghost"
              size="icon"
              className={cn(
                buttonSize,
                'rounded-md transition-colors duration-75 hover:bg-accent/50',
                formats.strikethrough &&
                  'bg-blue-100 text-blue-600 hover:bg-blue-100 dark:bg-blue-900/30 dark:text-blue-400 dark:hover:bg-blue-900/30',
              )}
              onClick={() => onFormat('strikethrough')}
            >
              <Strikethrough className="h-4 w-4" />
            </Button>
          </TooltipTrigger>
          <TooltipContent>
            <p>Strikethrough (Ctrl+Shift+S)</p>
          </TooltipContent>
        </Tooltip>

        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              variant="ghost"
              size="icon"
              className={cn(
                buttonSize,
                'rounded-md transition-colors duration-75 hover:bg-accent/50',
                formats.code &&
                  'bg-blue-100 text-blue-600 hover:bg-blue-100 dark:bg-blue-900/30 dark:text-blue-400 dark:hover:bg-blue-900/30',
              )}
              onClick={() => onFormat('code')}
            >
              <Code className="h-4 w-4" />
            </Button>
          </TooltipTrigger>
          <TooltipContent>
            <p>Inline Code (Ctrl+E)</p>
          </TooltipContent>
        </Tooltip>
      </div>
    </TooltipProvider>
  );
}
