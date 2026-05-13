import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@cranium/ui/tooltip';

interface OptionalTooltipProps {
  content?: React.ReactNode;
  disabled?: boolean;
  children: React.ReactElement;
}

export function OptionalTooltip({ content, children, disabled = false }: OptionalTooltipProps) {
  if (!content || disabled) {
    return children;
  }

  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>{children}</TooltipTrigger>
        <TooltipContent>{content}</TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}
