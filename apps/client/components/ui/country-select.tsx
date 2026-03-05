import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from '@riven/ui';
import { cn } from '@riven/utils';
import { useState } from 'react';
import * as RPNInput from 'react-phone-number-input';
import { Button } from './button';
import { Popover, PopoverContent, PopoverTrigger } from './popover';
import { ScrollArea } from './scroll-area';

import flags from 'react-phone-number-input/flags';

import { countryCodeToName } from '@/lib/util/country/country.util';
import { CheckIcon, ChevronsUpDown } from 'lucide-react';

export type CountryEntry = { label: string; value: RPNInput.Country | undefined };

type CountrySelectProps = {
  disabled?: boolean;
  value: RPNInput.Country;
  options: CountryEntry[];
  onChange: (country: RPNInput.Country) => void;
  includePhoneCode?: boolean;
  showCountryName?: boolean;
  className?: string;
  renderFlag?: (country: RPNInput.Country, countryName: string) => React.ReactNode;
  error?: string;
};

export const CountrySelect = ({
  disabled,
  value: selectedCountry,
  options: countryList,
  includePhoneCode = true,
  showCountryName = false,
  className,
  onChange,
  error,
}: CountrySelectProps) => {
  const [open, setOpen] = useState(false);

  const handleCountrySelect = (country: RPNInput.Country) => {
    onChange(country);
    setOpen(false);
  };

  return (
    <Popover modal={false} open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          type="button"
          variant="outline"
          className={cn(
            'flex h-full items-center justify-center gap-1 rounded-s-md rounded-e-none border-r-0 px-3 py-1 focus:z-10',
            className,
          )}
          disabled={disabled}
        >
          <FlagComponent country={selectedCountry} countryName={selectedCountry} />
          {showCountryName && (
            <span className="flex-1 text-sm">{countryCodeToName[selectedCountry]}</span>
          )}
          <ChevronsUpDown
            className={cn('-mr-2 size-4 opacity-50', disabled ? 'hidden' : 'opacity-100')}
          />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="mx-4 w-[300px] p-0">
        <Command>
          <CommandInput placeholder="Search country..." />
          <CommandList>
            <ScrollArea className="h-72" onWheel={(e) => e.stopPropagation()}>
              <CommandEmpty>No country found.</CommandEmpty>
              <CommandGroup>
                {countryList.map(({ value, label }) =>
                  value ? (
                    <CountrySelectOption
                      includePhoneCode={includePhoneCode}
                      key={value}
                      country={value}
                      countryName={label}
                      selectedCountry={selectedCountry}
                      onChange={handleCountrySelect}
                    />
                  ) : null,
                )}
              </CommandGroup>
            </ScrollArea>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
};

interface CountrySelectOptionProps extends RPNInput.FlagProps {
  selectedCountry: RPNInput.Country;
  includePhoneCode?: boolean;
  onChange: (country: RPNInput.Country) => void;
}

const CountrySelectOption = ({
  country,
  countryName,
  selectedCountry,
  includePhoneCode = true,
  onChange,
}: CountrySelectOptionProps) => {
  return (
    <CommandItem className="gap-2" onSelect={() => onChange(country)}>
      <FlagComponent country={country} countryName={countryName} />
      <span className="flex-1 text-sm">{countryName}</span>
      {includePhoneCode && (
        <span className="text-sm text-foreground/50">{`+${RPNInput.getCountryCallingCode(
          country,
        )}`}</span>
      )}
      <CheckIcon
        className={`ml-auto size-4 ${country === selectedCountry ? 'opacity-100' : 'opacity-0'}`}
      />
    </CommandItem>
  );
};

export const FlagComponent = ({ country, countryName }: RPNInput.FlagProps) => {
  const Flag = flags[country];

  return (
    <span className="flex h-4 w-6 justify-center overflow-hidden rounded-sm bg-foreground/20 [&_svg]:size-full">
      {Flag && <Flag title={countryName} />}
    </span>
  );
};
