import { SchemaOptions, SchemaType, OptionSortingType } from "@/lib/types/common";
import {
    EntityAttributeDefinition,
    EntityType,
    EntityTypeRequestDefinition,
    EntityTypeSemanticMetadata,
    SaveAttributeDefinitionRequest,
    SaveTypeDefinitionRequest,
    SemanticAttributeClassification,
} from "@/lib/types/entity";
import { attributeTypes } from "@/lib/util/form/schema.util";
import { zodResolver } from "@hookform/resolvers/zod";
import { useCallback, useEffect } from "react";
import { useForm, UseFormReturn } from "react-hook-form";
import { z } from "zod";
import { useSaveDefinitionMutation } from "../../mutation/type/use-save-definition-mutation";
import { uuid } from "@/lib/util/utils";

// Zod schema
export const attributeFormSchema = z
    .object({
        selectedType: z.nativeEnum(SchemaType),
        name: z.string().min(1, "Name is required"),
        required: z.boolean(),
        unique: z.boolean(),
        // Attribute Schema options
        enumValues: z.array(z.string()).optional().nullable(),
        enumSorting: z.nativeEnum(OptionSortingType).optional().nullable(),
        minimum: z.coerce.number().optional().nullable(),
        maximum: z.coerce.number().optional().nullable(),
        minLength: z.coerce.number().min(0).optional().nullable(),
        maxLength: z.coerce.number().min(0).optional().nullable(),
        regex: z.string().optional().nullable(),
        // Semantic context fields
        classification: z.nativeEnum(SemanticAttributeClassification).optional().nullable(),
        definition: z.string().optional().nullable(),
    })
    .refine(
        (data) => {
            // If selectedType is select or multi-select, at least 2 enum values must be provided
            if (
                data.selectedType === SchemaType.Select ||
                data.selectedType === SchemaType.MultiSelect
            ) {
                return data.enumValues && data.enumValues.length >= 2;
            }
            return true;
        },
        {
            message: "At least 2 options are required for select and multi-select types",
            path: ["enumValues"], // Attach error to the enumValues field
        }
    );

export interface useEntityTypeAttributeSchemaFormReturn {
  form: UseFormReturn<AttributeFormValues>;
  handleSubmit: (values: AttributeFormValues) => void;
  handleReset: () => void;
  currentType: SchemaType;
  mode: 'create' | 'edit';
}

export type AttributeFormValues = z.infer<typeof attributeFormSchema>;

export function useEntityTypeAttributeSchemaForm(
  workspaceId: string,
  type: EntityType,
  open: boolean,
  onSave: () => void,
  onCancel: () => void,
  attribute?: EntityAttributeDefinition,
  semanticMetadata?: EntityTypeSemanticMetadata,
): useEntityTypeAttributeSchemaFormReturn {
    // Always start form as blank, this is because we would want it to reset to blank when a dialogue is re-opened.
    // Regardless of selected attribute, or previous changes.
    const form = useForm<AttributeFormValues>({
        resolver: zodResolver(attributeFormSchema),
        defaultValues: {
            selectedType: SchemaType.Text,
            name: "",
            required: false,
            unique: false,
            enumValues: [],
            enumSorting: OptionSortingType.Manual,
            minimum: undefined,
            maximum: undefined,
            minLength: undefined,
            maxLength: undefined,
            regex: undefined,
            classification: undefined,
            definition: undefined,
        },
    });

  // Determines if we are currently editing an existing attribute
  const isEditMode = Boolean(attribute);
  const currentType = form.watch('selectedType');

  const handleReset = useCallback(() => {
    form.reset();
    onCancel();
  }, []);

  // Pre-populate schema options for specific types.
  // Provided they have been provided default options (ie. Rating type with min/max values)
  useEffect(() => {
    if (!isEditMode) {
      const attribute = attributeTypes[currentType];
      if (!attribute) return;

      // TODO. Expand this to cover more types as needed.
      if (attribute?.options) {
        if (attribute.options.minimum !== undefined) {
          form.setValue('minimum', attribute.options.minimum);
        }
        if (attribute.options.maximum !== undefined) {
          form.setValue('maximum', attribute.options.maximum);
        }
      }
    }
  }, [currentType, isEditMode]);

  // When the dialogue is opened for editing, populate the form with existing attribute data, or blank for new attribute
  useEffect(() => {
    if (!open || !attribute) return;

    const { schema } = attribute;

    // Parse Attribute Payload
    const attributeType = attributeTypes[schema.key];
    if (!attributeType) return;

    form.reset({
      selectedType: attributeType.key,
      name: schema.label,
      required: schema.required,
      unique: schema.unique,
      enumValues: schema.options?._enum,
      enumSorting: schema.options?.enumSorting,
      minimum: schema.options?.minimum,
      maximum: schema.options?.maximum,
      minLength: schema.options?.minLength,
      maxLength: schema.options?.maxLength,
      regex: schema.options?.regex,
      classification: semanticMetadata?.classification ?? undefined,
      definition: semanticMetadata?.definition ?? undefined,
    });
  }, [open, attribute, semanticMetadata]);

  useEffect(() => {
    if (!open) {
      form.reset();
    }
  }, [open]);

  const { mutateAsync: saveDefinition } = useSaveDefinitionMutation(workspaceId, {
    onSuccess: () => {
      onSave();
    },
  });

  const handleSubmit = useCallback(
    async (values: AttributeFormValues) => {
      const attributeType = attributeTypes[values.selectedType];
      if (!attributeType) return;

      const id = attribute?.id || uuid();

      const options: SchemaOptions = {
        _enum: values.enumValues ?? undefined,
        enumSorting: values.enumSorting ?? undefined,
        minimum: values.minimum ?? undefined,
        maximum: values.maximum ?? undefined,
        minLength: values.minLength ?? undefined,
        maxLength: values.maxLength ?? undefined,
        regex: values.regex ?? undefined,
      };

      const hasSemantics = values.classification || values.definition;

      const definition: SaveAttributeDefinitionRequest = {
        id,
        type: EntityTypeRequestDefinition.SaveSchema,
        key: type.key,
        schema: {
          key: attributeType.key,
          _protected: false,
          type: attributeType.type,
          format: attributeType.format,
          label: values.name,
          required: values.required,
          icon: attributeType.icon,
          unique: values.unique,
          options: options,
        },
        semantics: hasSemantics
          ? {
              classification: values.classification ?? undefined,
              definition: values.definition ?? undefined,
              tags: [],
            }
          : undefined,
      };

      const request: SaveTypeDefinitionRequest = {
        index: undefined,
        definition,
      };

      await saveDefinition(request);
    },
    [attribute, type.key],
  );

  return {
    form,
    handleSubmit,
    handleReset,
    currentType,
    mode: isEditMode ? 'edit' : 'create',
  };
}
