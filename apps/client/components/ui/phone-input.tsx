import * as RPNInput from 'react-phone-number-input';

import { Input } from '@riven/ui/input';
import { cn } from '@riven/utils';
import { forwardRef } from 'react';
import { CountrySelect, FlagComponent } from './country-select';

type PhoneInputProps = Omit<React.ComponentProps<'input'>, 'onChange' | 'value' | 'ref'> &
  Omit<RPNInput.Props<typeof RPNInput.default>, 'onChange'> & {
    onChange?: (value: RPNInput.Value) => void;
  };

const PhoneInput: React.ForwardRefExoticComponent<PhoneInputProps> = forwardRef<
  React.ElementRef<typeof RPNInput.default>,
  PhoneInputProps
>(({ className, onChange, ...props }, ref) => {
  return (
    <RPNInput.default
      ref={ref}
      className={cn('flex h-10', className)}
      flagComponent={FlagComponent}
      countrySelectComponent={CountrySelect}
      inputComponent={InputComponent}
      smartCaret={false}
      /**
       * Handles the onChange event.
       *
       * react-phone-number-input might trigger the onChange event as undefined
       * when a valid phone number is not entered. To prevent this,
       * the value is coerced to an empty string.
       *
       * @param {E164Number | undefined} value - The entered value
       */
      onChange={(value) => onChange?.(value || ('' as RPNInput.Value))}
      {...props}
    />
  );
});
PhoneInput.displayName = 'PhoneInput';

const InputComponent = forwardRef<HTMLInputElement, React.ComponentProps<'input'>>(
  ({ className, ...props }, ref) => (
    <Input
      className={cn('h-full rounded-s-none rounded-e-md py-1', className)}
      {...props}
      ref={ref}
    />
  ),
);
InputComponent.displayName = 'InputComponent';

export { PhoneInput };
