'use client';

import { Button } from '@riven/ui/button';
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem } from '@riven/ui/command';
import { Popover, PopoverContent, PopoverTrigger } from '@riven/ui/popover';
import type { ClassNameProps } from '@riven/utils';
import { cn } from '@riven/utils';
import { Check, ChevronsUpDown } from 'lucide-react';
import { FC, useState } from 'react';
import { ScrollArea } from '../../scroll-area';

// Build currency data
const getCurrencyData = () => {
  const codes = Intl.supportedValuesOf
    ? Intl.supportedValuesOf('currency')
    : ['USD', 'EUR', 'JPY', 'GBP', 'AUD', 'CAD', 'CHF', 'CNY', 'SEK', 'NZD'];

  return codes.map((code) => {
    const formatter = new Intl.NumberFormat(undefined, {
      style: 'currency',
      currency: code,
    });
    const parts = formatter.formatToParts(1);

    const symbol = parts.find((p) => p.type === 'currency')?.value ?? code;

    // Guess country by display name
    const displayName = new Intl.DisplayNames([navigator.language], {
      type: 'currency',
    }).of(code);

    return {
      code,
      country: displayName ?? code,
      symbol,
    };
  });
};

const currencies = getCurrencyData();
type Currency = (typeof currencies)[number];

interface Props extends ClassNameProps {
  value?: string;
  onChange: (currency: string) => void;
}

export const CurrencySelector: FC<Props> = ({ value, onChange, className }) => {
  const [open, setOpen] = useState(false);
  const selected = currencies.find((c) => c.code === value);

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          role="combobox"
          aria-expanded={open}
          className={cn(`w-[280px] justify-between`, className)}
        >
          {selected ? (
            <span>
              {selected.country} – {selected.code} ({selected.symbol})
            </span>
          ) : (
            'Select currency...'
          )}
          <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="h-[400px] w-[280px] p-0">
        <Command>
          <CommandInput placeholder="Search currency or country..." />
          <CommandEmpty>No currency found.</CommandEmpty>
          <CommandGroup className="h-[400px]">
            <ScrollArea className="h-[400px]">
              {currencies.map((currency) => (
                <CommandItem
                  key={currency.code}
                  value={`${currency.code} ${currency.country}`}
                  onSelect={() => {
                    onChange(currency.code);
                    setOpen(false);
                  }}
                >
                  <Check
                    className={cn(
                      'mr-2 h-4 w-4',
                      selected?.code === currency.code ? 'opacity-100' : 'opacity-0',
                    )}
                  />
                  {currency.country} – {currency.code} ({currency.symbol})
                </CommandItem>
              ))}
            </ScrollArea>
          </CommandGroup>
        </Command>
      </PopoverContent>
    </Popover>
  );
};
