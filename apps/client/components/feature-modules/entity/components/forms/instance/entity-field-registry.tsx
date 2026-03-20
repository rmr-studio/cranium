import { SchemaUUID } from '@/lib/types/common';
import { SchemaType } from '@/lib/types/common';
import { FC } from 'react';

// Import existing widgets from blocks module
import { FormWidgetProps } from '@/components/feature-modules/blocks/components/forms';
import { CheckboxWidget } from '@/components/feature-modules/blocks/components/forms/widgets/checkbox-widget';
import { CurrencyInputWidget } from '@/components/feature-modules/blocks/components/forms/widgets/currency-input-widget';
import { DatePickerWidget } from '@/components/feature-modules/blocks/components/forms/widgets/date-picker-widget';
import { DropdownWidget } from '@/components/feature-modules/blocks/components/forms/widgets/dropdown-widget';
import { EmailInputWidget } from '@/components/feature-modules/blocks/components/forms/widgets/email-input-widget';
import { FileUploadWidget } from '@/components/feature-modules/blocks/components/forms/widgets/file-upload-widget';
import { MultiSelectWidget } from '@/components/feature-modules/blocks/components/forms/widgets/multi-select-widget';
import { NumberInputWidget } from '@/components/feature-modules/blocks/components/forms/widgets/number-input-widget';
import { PhoneInputWidget } from '@/components/feature-modules/blocks/components/forms/widgets/phone-input-widget';
import { SliderWidget } from '@/components/feature-modules/blocks/components/forms/widgets/slider-widget';
import { TextInputWidget } from '@/components/feature-modules/blocks/components/forms/widgets/text-input-widget';

/**
 * Registry mapping SchemaType to widget components
 * Reuses existing widgets from blocks module where possible
 */
export const entityFieldWidgetRegistry: Record<SchemaType, FC<FormWidgetProps>> = {
  [SchemaType.Text]: TextInputWidget,
  [SchemaType.Number]: NumberInputWidget,
  [SchemaType.Checkbox]: CheckboxWidget,
  [SchemaType.Email]: EmailInputWidget,
  [SchemaType.Phone]: PhoneInputWidget,
  [SchemaType.Date]: DatePickerWidget,
  [SchemaType.Datetime]: DatePickerWidget, // with time enabled via schema
  [SchemaType.Currency]: CurrencyInputWidget,
  [SchemaType.Percentage]: NumberInputWidget, // will show % suffix
  [SchemaType.Rating]: SliderWidget,
  [SchemaType.Select]: DropdownWidget,
  [SchemaType.MultiSelect]: MultiSelectWidget,
  [SchemaType.Url]: TextInputWidget, // with URL validation
  [SchemaType.Location]: TextInputWidget, // TODO: Create LocationWidget (simple text for now)
  [SchemaType.FileAttachment]: FileUploadWidget,
  [SchemaType.Object]: TextInputWidget,
};

/**
 * Get widget component for a given schema
 */
export function getWidgetForSchema(schema: SchemaUUID): FC<FormWidgetProps> {
  return entityFieldWidgetRegistry[schema.key];
}

/**
 * Check if a schema type has a widget implementation
 */
export function hasWidgetForSchema(schema: SchemaUUID): boolean {
  return schema.key in entityFieldWidgetRegistry;
}
