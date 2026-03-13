import { IconColour, IconType } from '@/lib/types/common';
import {
  EntityRelationshipCardinality,
  EntityType,
  EntityTypeRequestDefinition,
  RelationshipDefinition,
  RelationshipLimit,
  SaveRelationshipDefinitionRequest,
  SaveTargetRuleRequest,
  SaveTypeDefinitionRequest,
  SemanticAttributeClassification,
  SemanticGroup,
} from '@/lib/types/entity';
import { zodResolver } from '@hookform/resolvers/zod';
import { useCallback, useEffect, useRef } from 'react';
import { useFieldArray, UseFieldArrayReturn, useForm, UseFormReturn } from 'react-hook-form';
import { z } from 'zod';
import { EntityTypeService } from '../../../service/entity-type.service';
import {
  calculateCardinalityFromLimits,
  processCardinalityToLimits,
} from '../../../util/relationship.util';
import { useSaveDefinitionMutation } from '../../mutation/type/use-save-definition-mutation';

// ---- Schemas ----

// NOTE: The schema retains 'semantic-group' as a valid ruleType and the
// allowPolymorphic field for backwards compatibility with existing data.
// However, the UI currently only creates 'entity-type' rules and forces
// allowPolymorphic to false. When re-enabling these features, update the
// UI components (target-rule-list.tsx, target-rule-item.tsx, relationship-form.tsx).

const targetRuleSchema = z.object({
  id: z.string().optional(),
  ruleType: z.enum(['entity-type', 'semantic-group']),
  targetEntityTypeKey: z.string().optional(),
  semanticTypeConstraint: z.nativeEnum(SemanticGroup).optional(),
  cardinalityOverride: z.nativeEnum(EntityRelationshipCardinality).optional(),
  inverseName: z.string().min(1, 'Inverse name is required'),
});

export const relationshipFormSchema = z
  .object({
    name: z.string().min(1, 'Name is required'),
    icon: z.object({
      type: z.nativeEnum(IconType),
      colour: z.nativeEnum(IconColour),
    }),
    semanticDefinition: z.string().optional(),
    sourceLimit: z.enum(['ONE', 'UNLIMITED']),
    targetLimit: z.enum(['ONE', 'UNLIMITED']),
    allowPolymorphic: z.boolean(),
    targetRules: z.array(targetRuleSchema),
  })
  .superRefine((data, ctx) => {
    if (!data.allowPolymorphic && data.targetRules.length === 0) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: 'At least one target rule is required',
        path: ['targetRules'],
      });
    }

    data.targetRules.forEach((rule, index) => {
      if (rule.ruleType === 'entity-type') {
        if (typeof rule.targetEntityTypeKey !== 'string' || rule.targetEntityTypeKey.length === 0) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: 'Each rule must have either a target entity type or a semantic group constraint',
            path: ['targetRules', index, 'targetEntityTypeKey'],
          });
        }
      } else if (rule.ruleType === 'semantic-group') {
        if (rule.semanticTypeConstraint === undefined) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: 'Each rule must have either a target entity type or a semantic group constraint',
            path: ['targetRules', index, 'semanticTypeConstraint'],
          });
        }
      }
    });
  });

export type RelationshipFormValues = z.infer<typeof relationshipFormSchema>;

// ---- Defaults ----

// ---- Return type ----

export interface UseRelationshipFormReturn {
  form: UseFormReturn<RelationshipFormValues>;
  handleSubmit: (values: RelationshipFormValues) => void;
  handleReset: () => void;
  mode: 'create' | 'edit';
  targetRuleFieldArray: UseFieldArrayReturn<RelationshipFormValues, 'targetRules'>;
  cachedRulesRef: React.MutableRefObject<RelationshipFormValues['targetRules']>;
}

// ---- Hook ----

export function useRelationshipForm(
  workspaceId: string,
  entityType: EntityType,
  availableTypes: EntityType[],
  open: boolean,
  onSave: () => void,
  onCancel: () => void,
  relationship?: RelationshipDefinition,
): UseRelationshipFormReturn {
  const form = useForm<RelationshipFormValues>({
    defaultValues: {
      name: '',
      icon: { type: IconType.Link2, colour: IconColour.Neutral },
      semanticDefinition: '',
      sourceLimit: 'ONE',
      targetLimit: 'UNLIMITED',
      allowPolymorphic: false,
      targetRules: [],
    },
    resolver: zodResolver(relationshipFormSchema),
  });

  const targetRuleFieldArray = useFieldArray({
    control: form.control,
    name: 'targetRules',
  });

  const cachedRulesRef = useRef<RelationshipFormValues['targetRules']>([]);

  // Populate form when opening in edit mode (or when availableTypes loads after modal opens)
  useEffect(() => {
    if (!open || !relationship) return;
    if (availableTypes.length === 0) return;

    const limits = processCardinalityToLimits(relationship.cardinalityDefault);
    const sourceLimit: 'ONE' | 'UNLIMITED' =
      limits.source === RelationshipLimit.SINGULAR ? 'ONE' : 'UNLIMITED';
    const targetLimit: 'ONE' | 'UNLIMITED' =
      limits.target === RelationshipLimit.SINGULAR ? 'ONE' : 'UNLIMITED';

    const targetRules: RelationshipFormValues['targetRules'] = relationship.targetRules.map(
      (rule) => {
        const hasEntityType = !!rule.targetEntityTypeId;
        const ruleType: 'entity-type' | 'semantic-group' = hasEntityType
          ? 'entity-type'
          : 'semantic-group';
        const targetEntityTypeKey = hasEntityType
          ? availableTypes.find((et) => et.id === rule.targetEntityTypeId)?.key
          : undefined;

        return {
          id: rule.id,
          ruleType,
          targetEntityTypeKey,
          semanticTypeConstraint: rule.semanticTypeConstraint,
          cardinalityOverride: rule.cardinalityOverride,
          inverseName: rule.inverseName,
        };
      },
    );

    form.reset({
      name: relationship.name,
      icon: relationship.icon,
      semanticDefinition: '',
      sourceLimit,
      targetLimit,
      allowPolymorphic: relationship.allowPolymorphic,
      targetRules,
    });
  }, [open, relationship, availableTypes]);

  // Reset form on modal close (after animation)
  useEffect(() => {
    if (!open) {
      setTimeout(() => {
        form.reset({
          name: '',
          icon: { type: IconType.Link2, colour: IconColour.Neutral },
          semanticDefinition: '',
          sourceLimit: 'ONE',
          targetLimit: 'UNLIMITED',
          allowPolymorphic: false,
          targetRules: [],
        });
      }, 500);
    }
  }, [open]);

  const { mutateAsync: saveDefinition } = useSaveDefinitionMutation(workspaceId, undefined, {
    onSuccess: () => {
      onSave();
    },
  });

  const handleSubmit = useCallback(
    async (values: RelationshipFormValues) => {
      const id = relationship?.id;

      const sourceLimit =
        values.sourceLimit === 'ONE' ? RelationshipLimit.SINGULAR : RelationshipLimit.MANY;
      const targetLimit =
        values.targetLimit === 'ONE' ? RelationshipLimit.SINGULAR : RelationshipLimit.MANY;
      const cardinalityDefault = calculateCardinalityFromLimits(sourceLimit, targetLimit);

      const targetRules: SaveTargetRuleRequest[] = values.allowPolymorphic
        ? []
        : values.targetRules.map((rule) => ({
            id: rule.id,
            targetEntityTypeId: rule.targetEntityTypeKey
              ? EntityTypeService.resolveEntityTypeId(availableTypes, rule.targetEntityTypeKey)
              : undefined,
            semanticTypeConstraint: rule.semanticTypeConstraint,
            cardinalityOverride: rule.cardinalityOverride,
            inverseName: rule.inverseName,
          }));

      const definition: SaveRelationshipDefinitionRequest = {
        type: EntityTypeRequestDefinition.SaveRelationship,
        id,
        key: entityType.key,
        name: values.name,
        iconType: values.icon.type,
        iconColour: values.icon.colour,
        allowPolymorphic: values.allowPolymorphic,
        cardinalityDefault,
        targetRules,
        semantics: values.semanticDefinition
          ? {
              classification: SemanticAttributeClassification.RelationalReference,
              definition: values.semanticDefinition,
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
    [relationship, entityType, availableTypes],
  );

  return {
    form,
    handleSubmit,
    handleReset: onCancel,
    mode: relationship ? 'edit' : 'create',
    targetRuleFieldArray,
    cachedRulesRef,
  };
}
