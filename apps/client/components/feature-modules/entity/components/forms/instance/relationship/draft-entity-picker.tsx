import { useEntityDraft } from "@/components/feature-modules/entity/context/entity-provider";
import { EntityLink, RelationshipDefinition } from "@/lib/types/entity";
import { FC, useCallback } from "react";
import { useFormState, useWatch } from "react-hook-form";
import { EntityRelationshipPicker } from "../entity-relationship-picker";

interface Props {
  relationship: RelationshipDefinition;
}

export const DraftEntityRelationshipPicker: FC<Props> = ({ relationship }) => {
  const { form } = useEntityDraft();

  const value: EntityLink[] = useWatch({
    control: form.control,
    name: relationship.id,
  });

  // Watch for validation errors on this specific field
  const { errors: formErrors } = useFormState({
    control: form.control,
    name: relationship.id,
  });

  const errors = formErrors[relationship.id]?.message
    ? [String(formErrors[relationship.id]?.message)]
    : formErrors[relationship.id]?.type
      ? [String(formErrors[relationship.id]?.type)]
      : undefined;

  const handleBlur = async () => {
    await form.trigger(relationship.id);
  };

  const handleChange = useCallback(
    (values: EntityLink[]) => {
      console.log('Setting relationship values:', values);
      form.setValue(relationship.id, values);
    },
    [form, relationship.id],
  );

  const handleRemove = (entityId: string) => {
    if (Array.isArray(value)) {
      const updatedValue = value.filter((link) => link.id !== entityId);
      form.setValue(relationship.id, updatedValue);
    } else {
      form.setValue(relationship.id, null);
    }
  };

  return (
    <EntityRelationshipPicker
      relationship={relationship}
      handleBlur={handleBlur}
      value={value}
      errors={errors}
      handleChange={handleChange}
      handleRemove={handleRemove}
    />
  );
};
