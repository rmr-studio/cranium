import { useEntityTypes } from '@/components/feature-modules/entity/hooks/query/type/use-entity-types';
import { useWorkspace } from '@/components/feature-modules/workspace/hooks/query/use-workspace';
import { AttributeTypeDropdown } from '@/components/ui/attribute-type-dropdown';
import { DialogControl } from '@/lib/interfaces/interface';
import { IconColour, IconType, SchemaType } from '@/lib/types/common';
import {
  EntityAttributeDefinition,
  EntityType,
  isRelationshipDefinition,
  RelationshipDefinition,
} from '@/lib/types/entity';
import { attributeTypes } from '@/lib/util/form/schema.util';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@riven/ui/dialog';
import { Lock, Loader2 } from 'lucide-react';
import { useParams } from 'next/navigation';
import { FC, useEffect, useMemo, useState } from 'react';
import { IconCell } from '@/components/ui/icon/icon-cell';
import { SchemaForm } from '../../../forms/type/attribute/schema-form';
import { RelationshipForm } from '../../../forms/type/relationship/relationship-form';

interface Props {
  dialog: DialogControl;
  type: EntityType;
  selectedAttribute?: EntityAttributeDefinition | RelationshipDefinition;
  onSuccess?: (definitionId: string) => void;
}

export const AttributeFormModal: FC<Props> = ({ dialog, type, selectedAttribute, onSuccess }) => {
  const { open, setOpen: onOpenChange } = dialog;
  const isEditMode = Boolean(selectedAttribute);
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const [currentType, setCurrentType] = useState<SchemaType | 'RELATIONSHIP'>(SchemaType.Text);
  const { data: workspace } = useWorkspace();
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const { data: allEntityTypes } = useEntityTypes(workspaceId);

  const selectedSemanticMetadata = useMemo(() => {
    if (!selectedAttribute || isRelationshipDefinition(selectedAttribute)) return undefined;
    return type.semantics?.attributes?.[selectedAttribute.id];
  }, [selectedAttribute, type.semantics]);

  const isTargetSide = useMemo(() => {
    if (!selectedAttribute || !isRelationshipDefinition(selectedAttribute)) return false;
    return selectedAttribute.sourceEntityTypeId !== type.id;
  }, [selectedAttribute, type.id]);

  const sourceEntityTypeKey = useMemo(() => {
    if (!isTargetSide || !selectedAttribute || !isRelationshipDefinition(selectedAttribute)) return undefined;
    return allEntityTypes?.find((et) => et.id === selectedAttribute.sourceEntityTypeId)?.key;
  }, [isTargetSide, selectedAttribute, allEntityTypes]);

  useEffect(() => {
    if (!selectedAttribute) {
      setCurrentType(SchemaType.Text);
      return;
    }
    if (isRelationshipDefinition(selectedAttribute)) {
      setCurrentType('RELATIONSHIP');
      return;
    }
    setCurrentType(selectedAttribute.schema.key);
  }, [selectedAttribute]);

  const isRelationship = useMemo(() => {
    return currentType === 'RELATIONSHIP';
  }, [currentType]);

  const allowTypeSwitch = !isEditMode;

  const currentTypeMetadata = useMemo(() => {
    if (currentType === 'RELATIONSHIP') {
      return { label: 'Relationship', icon: { type: IconType.Link2, colour: IconColour.Neutral } };
    }
    return attributeTypes[currentType];
  }, [currentType]);

  if (!workspace) {
    return (
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent>
          <div className="flex items-center justify-center p-6">
            <Loader2 className="size-6 animate-spin" />
          </div>
        </DialogContent>
      </Dialog>
    );
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        className="max-h-[90vh] w-full min-w-11/12 overflow-y-auto lg:min-w-6xl"
        onEscapeKeyDown={(e) => {
          if (dropdownOpen) {
            e.preventDefault();
          }
        }}
      >
        <DialogHeader>
          <DialogTitle>{isEditMode ? 'Edit attribute' : 'Create attribute'}</DialogTitle>
          <DialogDescription>
            {isEditMode
              ? 'Update the attribute or relationship'
              : 'Add a new attribute or relationship to your entity type'}
          </DialogDescription>
          <section className="flex flex-col gap-6">
            {allowTypeSwitch ? (
              <AttributeTypeDropdown
                open={dropdownOpen}
                setOpen={setDropdownOpen}
                onChange={setCurrentType}
                type={currentType}
              />
            ) : (
              <div className="flex items-center gap-2 rounded-md border border-border/50 bg-muted/40 px-3 py-2">
                <IconCell
                  readonly
                  className="size-4"
                  type={currentTypeMetadata.icon.type}
                  colour={currentTypeMetadata.icon.colour}
                />
                <span className="text-sm font-medium">{currentTypeMetadata.label}</span>
                <Lock className="ml-auto size-3.5 text-muted-foreground/60" />
              </div>
            )}
            {isRelationship ? (
              <RelationshipForm
                workspaceId={workspace.id}
                dialog={dialog}
                type={type}
                relationship={selectedAttribute as RelationshipDefinition | undefined}
                isTargetSide={isTargetSide}
                sourceEntityTypeKey={sourceEntityTypeKey}
                onSuccess={onSuccess}
              />
            ) : (
              <SchemaForm
                workspaceId={workspace.id}
                dialog={dialog}
                currentType={currentType as SchemaType}
                type={type}
                attribute={selectedAttribute as EntityAttributeDefinition | undefined}
                semanticMetadata={selectedSemanticMetadata}
                onSuccess={onSuccess}
              />
            )}
          </section>
        </DialogHeader>
      </DialogContent>
    </Dialog>
  );
};
