// common/index.ts
import { type Icon } from '../models/Icon';
import { type DisplayName } from '../models/DisplayName';
import { IconColour } from '../models/IconColour';
import { IconType } from '../models/IconType';
import { DataType } from '../models/DataType';
import { DataFormat } from '../models/DataFormat';
import { SchemaType } from '../models/SchemaType';
import { type SchemaOptions } from '../models/SchemaOptions';
import type { SchemaUUID } from '../models/SchemaUUID';
import type { SchemaString } from '../models/SchemaString';
import { OptionSortingType } from '../models/OptionSortingType';
import type { DefaultValue } from '../models/DefaultValue';
import type { Static } from '../models/Static';
import type { Dynamic } from '../models/Dynamic';
import { DynamicDefaultFunction } from '../models/DynamicDefaultFunction';

// Common types shared across multiple domains
export {
  Icon,
  DisplayName,
  IconColour,
  IconType,
  DataType,
  DataFormat,
  SchemaType,
  SchemaOptions,
  SchemaUUID,
  SchemaString,
  SchemaUUID as Schema,
  OptionSortingType,
  DefaultValue,
  Static,
  Dynamic,
  DynamicDefaultFunction,
};
