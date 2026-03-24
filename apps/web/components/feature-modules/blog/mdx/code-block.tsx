'use client';

import { Check, Copy } from 'lucide-react';
import { useState } from 'react';

export function CodeBlock({ children, ...props }: React.HTMLAttributes<HTMLPreElement>) {
  const [copied, setCopied] = useState(false);

  const handleCopy = () => {
    const code = (children as React.ReactElement)?.props?.children ?? '';
    navigator.clipboard.writeText(typeof code === 'string' ? code : '');
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="group relative">
      <pre
        className="overflow-x-auto rounded-lg border border-border bg-card p-4 font-mono text-sm leading-relaxed"
        {...props}
      >
        {children}
      </pre>
      <button
        onClick={handleCopy}
        className="absolute right-3 top-3 rounded-sm border border-border bg-card p-1.5 opacity-0 transition-opacity group-hover:opacity-100 focus:opacity-100"
        aria-label="Copy code"
      >
        {copied ? <Check className="size-3.5 text-success" /> : <Copy className="size-3.5 text-muted-foreground" />}
      </button>
    </div>
  );
}
