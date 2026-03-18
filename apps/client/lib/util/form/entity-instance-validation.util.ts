import { entityReferenceFormSchema } from '@/components/feature-modules/entity/components/tables/entity-table-utils';

import { DataFormat, DataType, SchemaOptions, SchemaType, SchemaUUID } from '@/lib/types/common';
import {
  EntityRelationshipCardinality,
  EntityType,
  RelationshipDefinition,
} from '@/lib/types/entity';
import { z } from 'zod';
import { exists } from '../utils';
import { attributeTypes } from './schema.util';

/**
 * Build a Zod schema from an EntityType definition for validation
 */
export function buildZodSchemaFromEntityType(entityType: EntityType): z.ZodObject<z.ZodRawShape> {
  const schemaShape: Record<string, z.ZodTypeAny> = {};

  // Build schemas for all attributes
  if (entityType.schema.properties) {
    Object.entries(entityType.schema.properties).forEach(([attributeId, schema]) => {
      schemaShape[attributeId] = buildFieldSchema(schema);
    });
  }

  // Build schemas for all relationships
  if (entityType.relationships) {
    entityType.relationships.forEach((relationship) => {
      schemaShape[relationship.id] = buildRelationshipFieldSchema(relationship);
    });
  }

  return z.object(schemaShape);
}

/**
 * Build a Zod schema for an individual attribute field
 */
export function buildFieldSchema(schema: SchemaUUID): z.ZodTypeAny {
  const attributeType = attributeTypes[schema.key];
  const mergedOptions: Partial<SchemaOptions> = { ...attributeType.options, ...schema.options };
  let fieldSchema: z.ZodTypeAny;

  // Base type mapping
  switch (attributeType.type) {
    case DataType.String:
      fieldSchema = buildStringSchema(schema, mergedOptions);
      break;
    case DataType.Number:
      fieldSchema = buildNumberSchema(schema, mergedOptions);
      break;
    case DataType.Boolean:
      fieldSchema = z.boolean();
      break;
    case DataType.Array:
      fieldSchema = buildArraySchema(schema, mergedOptions);
      break;
    case DataType.Object:
      fieldSchema = z.record(z.any());
      break;
    default:
      fieldSchema = z.any();
  }

  // Handle required/optional — protected fields are backend-generated, so skip client-side required checks
  if (!schema.required || schema._protected) {
    fieldSchema = fieldSchema.optional().nullable();
  } else {
    fieldSchema = fieldSchema.refine((val) => val !== null && val !== undefined, {
      message: `${schema.label || 'Field'} is required`,
    });
  }

  return fieldSchema;
}

/**
 * Build a Zod schema for string-based fields
 */
function buildStringSchema(schema: SchemaUUID, mergedOptions: Partial<SchemaOptions>): z.ZodTypeAny {
  let stringSchema: z.ZodTypeAny = z.string();

  if (schema.required && !schema._protected && !exists(mergedOptions?.minLength)) {
    stringSchema = stringSchema.min(1, `${schema.label || 'Field'} is required`);
  }

  if (mergedOptions) {
    // Min/max length constraints
    if (exists(mergedOptions.minLength)) {
      stringSchema = (stringSchema as z.ZodString).min(
        mergedOptions.minLength,
        `Must be at least ${mergedOptions.minLength} characters`,
      );
    }
    if (exists(mergedOptions.maxLength)) {
      stringSchema = (stringSchema as z.ZodString).max(
        mergedOptions.maxLength,
        `Must be at most ${mergedOptions.maxLength} characters`,
      );
    }

    // Regex pattern validation
    if (exists(mergedOptions.regex)) {
      try {
        const pattern = new RegExp(mergedOptions.regex);
        stringSchema = (stringSchema as z.ZodString).regex(pattern, `Must match pattern: ${mergedOptions.regex}`);
      } catch (error) {
        console.error('Invalid regex pattern:', mergedOptions.regex, error);
      }
    }

    // Enum validation for SELECT types
    if (exists(mergedOptions._enum) && mergedOptions._enum.length > 0) {
      // Zod enum requires at least 1 value, and values must be tuples
      const enumValues = mergedOptions._enum as [string, ...string[]];
      stringSchema = z.enum(enumValues);
    }
  }

  // Format-specific validation
  const attributeType = attributeTypes[schema.key];
  if (attributeType.format) {
    switch (attributeType.format) {
      case DataFormat.Email:
        stringSchema = stringSchema.email('Must be a valid email address');
        break;
      case DataFormat.Url:
        stringSchema = stringSchema.url('Must be a valid URL');
        break;
      case DataFormat.Date:
        // Accept ISO date strings
        stringSchema = stringSchema.regex(
          /^\d{4}-\d{2}-\d{2}$/,
          'Must be a valid date (YYYY-MM-DD)',
        );
        break;
      case DataFormat.Datetime:
        // Accept ISO datetime strings
        stringSchema = stringSchema.datetime('Must be a valid datetime');
        break;
      case DataFormat.Phone:
        // Basic phone validation (can be enhanced)
        stringSchema = stringSchema.regex(
          /^[+]?[(]?[0-9]{1,4}[)]?[-\s.]?[(]?[0-9]{1,4}[)]?[-\s.]?[0-9]{1,9}$/,
          'Must be a valid phone number',
        );
        break;
    }
  }

  return stringSchema;
}

/**
 * Build a Zod schema for number-based fields
 */
function buildNumberSchema(schema: SchemaUUID, mergedOptions: Partial<SchemaOptions>): z.ZodNumber {
  let numberSchema = z.number({
    required_error: `${schema.label || 'Field'} is required`,
    invalid_type_error: `${schema.label || 'Field'} must be a number`,
  });

  if (mergedOptions) {
    // Min/max value constraints
    if (mergedOptions.minimum !== undefined && mergedOptions.minimum !== null) {
      numberSchema = numberSchema.min(mergedOptions.minimum, `Must be at least ${mergedOptions.minimum}`);
    }
    if (mergedOptions.maximum !== undefined && mergedOptions.maximum !== null) {
      numberSchema = numberSchema.max(mergedOptions.maximum, `Must be at most ${mergedOptions.maximum}`);
    }
  }

  return numberSchema;
}

/**
 * Build a Zod schema for array-based fields (MULTI_SELECT, FILE_ATTACHMENT)
 */
// mergedOptions kept for signature consistency — future use for maxItems constraints
// eslint-disable-next-line @typescript-eslint/no-unused-vars
function buildArraySchema(schema: SchemaUUID, mergedOptions: Partial<SchemaOptions>): z.ZodArray<z.ZodTypeAny> {
  // For MULTI_SELECT, allow any string — users can create new options beyond the
  // predefined enum. The backend accepts arbitrary string values for multi-select
  // fields (no server-side enum constraint), so free-text creation is by design.
  if (schema.key === SchemaType.MultiSelect) {
    return z.array(z.string());
  }

  // For FILE_ATTACHMENT
  if (schema.key === SchemaType.FileAttachment) {
    return z.array(z.string().url('Must be a valid file URL'));
  }

  // Default: array of strings
  return z.array(z.string());
}

/**
 * Build a Zod schema for relationship fields
 */
export function buildRelationshipFieldSchema(relationship: RelationshipDefinition) {
  const isSingleSelect =
    relationship.cardinalityDefault === EntityRelationshipCardinality.OneToOne ||
    relationship.cardinalityDefault === EntityRelationshipCardinality.ManyToOne;

  let schema = entityReferenceFormSchema;

  if (isSingleSelect) {
    // Single entity ID
    schema = schema.max(1, `Only one ${relationship.name} can be selected`);
  }

  return schema;
}

/**
 * Get default value for a schema field
 */
export function getDefaultValueForSchema(schema: SchemaUUID): unknown {
  // Check for custom default value in options first
  if (schema.options?._default !== undefined && schema.options?._default !== null) {
    return schema.options._default;
  }

  const attributeType = attributeTypes[schema.key];

  switch (attributeType.type) {
    case DataType.String:
      return '';
    case DataType.Number:
      // Use minimum if specified, otherwise 0
      return schema.options?.minimum ?? 0;
    case DataType.Boolean:
      return false;
    case DataType.Array:
      return [];
    case DataType.Object:
      return {};
    default:
      return null;
  }
}

/**
 * Build default values for all fields in an entity type
 */
export function buildDefaultValuesFromEntityType(entityType: EntityType): Record<string, unknown> {
  const defaults: Record<string, unknown> = {};

  // Set defaults for attributes (uses options.default if available)
  if (entityType.schema.properties) {
    Object.entries(entityType.schema.properties).forEach(([id, schema]) => {
      defaults[id] = getDefaultValueForSchema(schema);
    });
  }

  // Set defaults for relationships (always empty arrays since entityReferenceFormSchema is array-based)
  if (entityType.relationships) {
    entityType.relationships.forEach((rel) => {
      defaults[rel.id] = [];
    });
  }

  return defaults;
}
