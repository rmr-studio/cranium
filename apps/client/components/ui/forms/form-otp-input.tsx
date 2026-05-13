import type { ClassNameProps } from '@cranium/utils';
import { FormFieldProps } from '@/lib/interfaces/interface';

import { cn } from '@cranium/utils';
import { REGEXP_ONLY_CHARS, REGEXP_ONLY_DIGITS, REGEXP_ONLY_DIGITS_AND_CHARS } from 'input-otp';
import { FC, Fragment } from 'react';
import { InputOTP, InputOTPGroup, InputOTPSeparator, InputOTPSlot } from '../input-otp';

type OTPRegex = 'numeric' | 'alphanumeric' | 'alphabetical';

const OTPRegexMap: Record<OTPRegex, string> = {
  numeric: REGEXP_ONLY_DIGITS,
  alphanumeric: REGEXP_ONLY_DIGITS_AND_CHARS,
  alphabetical: REGEXP_ONLY_CHARS,
};

interface FormOTPProps extends ClassNameProps {
  field: FormFieldProps<string>;
  size: number;
  groups: number;
  regex?: OTPRegex;
}

export const FormOTPInput: FC<FormOTPProps> = ({ field, size, groups, regex, className }) => {
  // Calculate the number of slots per group
  const slotsPerGroup = Math.ceil(size / groups);

  return (
    <InputOTP maxLength={size} {...field} pattern={OTPRegexMap[regex ?? 'alphanumeric']}>
      {Array.from({ length: groups }, (_, groupIndex) => (
        <Fragment key={`otp-group-${groupIndex}`}>
          <InputOTPGroup>
            {Array.from({ length: slotsPerGroup }, (_, slotIndex) => {
              const slotIndexGlobal = groupIndex * slotsPerGroup + slotIndex;
              return (
                slotIndexGlobal < size && (
                  <InputOTPSlot
                    className={cn(className)}
                    key={`otp-slot-${slotIndexGlobal}`}
                    index={slotIndexGlobal}
                  />
                )
              );
            })}
          </InputOTPGroup>
          {/* Add separator between groups, but not after the last group */}
          {groupIndex < groups - 1 && <InputOTPSeparator />}
        </Fragment>
      ))}
    </InputOTP>
  );
};
