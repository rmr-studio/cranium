import { useWorkspace } from '@/components/feature-modules/workspace/hooks/query/use-workspace';
import { useEntityTypes } from '@/components/feature-modules/entity/hooks/query/type/use-entity-types';
import { AttributeTypeDropdown } from '@/components/ui/attribute-type-dropdown';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@riven/ui/dialog';
import { DialogControl } from '@/lib/interfaces/interface';
import {
  EntityAttributeDefinition,
  RelationshipDefinition,
  EntityType,
  isRelationshipDefinition,
} from '@/lib/types/entity';
import { Loader2 } from 'lucide-react';
import { useParams } from 'next/navigation';
import { FC, useEffect, useMemo, useState } from 'react';
import { SchemaForm } from '../../../forms/type/attribute/schema-form';
import { RelationshipForm } from '../../../forms/type/relationship/relationship-form';
import { SchemaType } from '@/lib/types/common';

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

  const allowTypeSwitch = useMemo(() => {
    if (!selectedAttribute) return true;
    return !isRelationshipDefinition(selectedAttribute);
  }, [selectedAttribute]);

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
            {allowTypeSwitch && (
              <AttributeTypeDropdown
                open={dropdownOpen}
                setOpen={setDropdownOpen}
                onChange={setCurrentType}
                type={currentType}
              />
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
                onSuccess={onSuccess}
              />
            )}
          </section>
        </DialogHeader>
      </DialogContent>
    </Dialog>
  );
};
