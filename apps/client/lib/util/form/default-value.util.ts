import {
  DefaultValue,
  Dynamic,
  DynamicDefaultFunction,
  SchemaType,
  Static,
} from '@/lib/types/common';

/** Type guard for Static default values. */
export function isStaticDefault(dv: DefaultValue): dv is Static {
  return dv.type === 'Static';
}

/** Type guard for Dynamic default values. */
export function isDynamicDefault(dv: DefaultValue): dv is Dynamic {
  return dv.type === 'Dynamic';
}

/**
 * Resolve a DefaultValue to a concrete value suitable for form initialisation.
 * Static values are returned as-is; dynamic values are evaluated at call time.
 */
export function resolveDefaultValue(dv: DefaultValue): unknown {
  if (isStaticDefault(dv)) {
    return dv.value;
  }

  if (isDynamicDefault(dv)) {
    switch (dv._function) {
      case DynamicDefaultFunction.CurrentDate: {
        // Use local calendar components so users near midnight in non-UTC
        // timezones get today's date, not UTC's yesterday/tomorrow.
        const d = new Date();
        const pad = (n: number) => String(n).padStart(2, '0');
        return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
      }
      case DynamicDefaultFunction.CurrentDatetime:
        return new Date().toISOString();
      default:
        return undefined;
    }
  }

  return undefined;
}

/** Build a Static DefaultValue from a raw form value. */
export function buildStaticDefaultValue(value: unknown): Static {
  return { type: 'Static', value: value as object };
}

/** Build a Dynamic DefaultValue from a function enum. */
export function buildDynamicDefaultValue(fn: DynamicDefaultFunction): Dynamic {
  return { type: 'Dynamic', _function: fn };
}

/**
 * Map of SchemaType to compatible DynamicDefaultFunction options.
 * Types not listed here only support static defaults.
 */
export const dynamicFunctionsForType: Partial<Record<SchemaType, DynamicDefaultFunction[]>> = {
  [SchemaType.Date]: [DynamicDefaultFunction.CurrentDate],
  [SchemaType.Datetime]: [DynamicDefaultFunction.CurrentDatetime],
};

/** Human-readable labels for each dynamic default function. */
export const dynamicFunctionLabels: Record<DynamicDefaultFunction, string> = {
  [DynamicDefaultFunction.CurrentDate]: 'Current date',
  [DynamicDefaultFunction.CurrentDatetime]: 'Current date & time',
};

/** Human-readable descriptions for each dynamic default function. */
export const dynamicFunctionDescriptions: Record<DynamicDefaultFunction, string> = {
  [DynamicDefaultFunction.CurrentDate]:
    'Automatically set to the current date when a record is created',
  [DynamicDefaultFunction.CurrentDatetime]:
    'Automatically set to the current date and time when a record is created',
};
