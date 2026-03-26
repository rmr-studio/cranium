import { cn } from '@riven/utils';
import { AnimatePresence } from 'framer-motion';
import { CheckIcon, Circle } from 'lucide-react';
import { FC } from 'react';
import { Control, useWatch } from 'react-hook-form';
import { Registration } from '@/components/feature-modules/authentication/components/register';

interface Props {
  control: Control<Registration>;
  visible: boolean;
}

export const PasswordRequirements: FC<Props> = ({ control, visible }) => {
  const passwordRequirementsValues = [
    'length',
    'uppercase',
    'lowercase',
    'number',
    'specialCharacter',
  ] as const;

  const password = useWatch({ control, name: 'password' });

  type PasswordRequirements = (typeof passwordRequirementsValues)[number];
  const passwordValidationRequirements: Record<PasswordRequirements, RegExp> = {
    length: /.{8,}/,
    uppercase: /[A-Z]/,
    lowercase: /[a-z]/,
    number: /\d/,
    specialCharacter: /[!@#$%^&*(),.?":{}|<>]/,
  };

  const passwordValidationTitles: Record<PasswordRequirements, string> = {
    length: '8 Characters or more',
    uppercase: 'Uppercase Letter',
    lowercase: 'Lowercase Letter',
    number: 'Number',
    specialCharacter: 'Special Character (e.g. !?<>@#$%)',
  };

  return (
    <AnimatePresence>
      {visible && (
        <section className="mt-4 text-muted-foreground">
          {passwordRequirementsValues.map((validation) => (
            <div className="flex items-center p-1" key={`password-requirement-${validation}`}>
              <CheckMarkBox
                validated={passwordValidationRequirements[validation].test(password || '')}
              />

              <p className="ml-2 text-sm font-semibold">{passwordValidationTitles[validation]}</p>
            </div>
          ))}
        </section>
      )}
    </AnimatePresence>
  );
};

interface CheckBoxProps {
  validated: boolean;
}

const CheckMarkBox: FC<CheckBoxProps> = ({ validated }) => {
  return (
    <div className="relative h-fit w-fit">
      <Circle className="h-[1.125rem] w-[1.125rem]" />
      <CheckIcon
        className={cn(
          'absolute top-1/2 left-1/2 h-[0.625rem] w-[0.625rem] -translate-x-[50%] -translate-y-[50%] stroke-2',
          validated ? 'stroke-foreground' : 'stroke-background',
        )}
      />
    </div>
  );
};
