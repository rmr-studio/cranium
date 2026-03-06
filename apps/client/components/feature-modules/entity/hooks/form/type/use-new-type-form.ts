import { IconColour, IconType } from '@/lib/types/common';
import { CreateEntityTypeRequest, EntityType, SemanticGroup } from '@/lib/types/entity';
import { iconFormSchema } from '@/lib/util/form/common/icon.form';
import { toKeyCase } from '@/lib/util/utils';
import { zodResolver } from '@hookform/resolvers/zod';
import { useRouter } from 'next/navigation';
import { useForm, UseFormReturn } from 'react-hook-form';
import { z } from 'zod';
import { usePublishEntityTypeMutation } from '../../mutation/type/use-publish-type-mutation';

export const baseEntityTypeFormSchema = z
  .object({
    singularName: z.string().min(1, 'Singular variant of the name is required'),
    pluralName: z.string().min(1, 'Plural variant of the name is required'),
    description: z.string().optional(),
    semanticGroup: z.nativeEnum(SemanticGroup),
    tags: z.array(z.string()),
  })
  .extend(iconFormSchema.shape);

export type NewEntityTypeFormValues = z.infer<typeof baseEntityTypeFormSchema>;
export interface UseEntityTypeFormReturn {
  form: UseFormReturn<NewEntityTypeFormValues>;
  handleSubmit: (values: NewEntityTypeFormValues) => Promise<void>;
}

export function useNewEntityTypeForm(workspaceId: string): UseEntityTypeFormReturn {
  const router = useRouter();

  const form = useForm<NewEntityTypeFormValues>({
    defaultValues: {
      singularName: '',
      pluralName: '',
      description: '',
      semanticGroup: SemanticGroup.Uncategorized,
      tags: [],
      icon: {
        type: IconType.Database,
        colour: IconColour.Neutral,
      },
    },
    resolver: zodResolver(baseEntityTypeFormSchema),
  });

  const handleSubmit = async (values: NewEntityTypeFormValues): Promise<void> => {
    const request: CreateEntityTypeRequest = {
      key: toKeyCase(values.pluralName),
      name: {
        singular: values.singularName,
        plural: values.pluralName,
      },
      icon: values.icon,
      semanticGroup: values.semanticGroup,
      semantics:
        values.description || values.tags.length > 0
          ? { definition: values.description, tags: values.tags }
          : undefined,
    };

    await publishType(request);
  };

  const { mutateAsync: publishType } = usePublishEntityTypeMutation(workspaceId, {
    onSuccess: (response: EntityType) => {
      router.push(
        `/dashboard/workspace/${workspaceId}/entity/${response.key}/settings?tab=attributes`,
      );
    },
  });

  return {
    form,
    handleSubmit,
  };
}
