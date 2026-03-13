'use client';

import { ColumnResizingConfig, DataTable, DataTableProvider } from '@/components/ui/data-table';
import { Form } from '@/components/ui/form';
import { SchemaUUID } from '@/lib/types/common';
import {
  Entity,
  EntityAttributePrimitivePayload,
  EntityAttributeRelationPayloadReference,
  EntityAttributeRequest,
  EntityLink,
  EntityPropertyType,
  EntityType,
  isRelationshipPayload,
  RelationshipDefinition,
  SaveEntityRequest,
  EntityAttributeDefinition,
  EntityTypeDefinition,
  SaveEntityResponse,
} from '@/lib/types/entity';
import { debounce } from '@/lib/util/debounce.util';
import type { ClassNameProps } from '@riven/utils';
import { cn } from '@riven/utils';

import type { QueryFilter } from '@/lib/types/models/QueryFilter';
import { Button } from '@riven/ui/button';
import { Row } from '@tanstack/react-table';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { MoreHorizontal, Plus } from 'lucide-react';
import { FC, useCallback, useMemo, useRef, useState } from 'react';
import { useConfigFormState } from '../../context/configuration-provider';
import { useEntityDraft } from '../../context/entity-provider';
import { useSaveEntityMutation } from '../../hooks/mutation/instance/use-save-entity-mutation';
import { EntityQueryBuilder } from '../query/entity-query-builder';
import { EntityTypeHeader } from '../ui/entity-type-header';
import { AttributeFormModal } from '../ui/modals/type/attribute-form-modal';
import { DeleteDefinitionModal } from '../ui/modals/type/delete-definition-modal';
import { ColumnHeaderPopover } from './column-header-popover';
import { ColumnVisibilityPopover } from './column-visibility-popover';
import { EntityDraftRow } from './entity-draft-row';
import EntityActionBar from './entity-table-action-bar';
import {
  applyColumnOrdering,
  EntityRow,
  generateColumnsFromEntityType,
  generateSearchConfigFromEntityType,
  isDraftRow,
  transformEntitiesToRows,
} from './entity-table-utils';


export interface Props extends ClassNameProps {
  entityType: EntityType;
  entities: Entity[];
  loadingEntities?: boolean;
  workspaceId: string;
}

// Internal component with draft mode hooks
export const EntityDataTable: FC<Props> = ({
  entityType,
  entities,
  loadingEntities,
  className,
  workspaceId,
}) => {
  const { isDraftMode, enterDraftMode } = useEntityDraft();
  const { form } = useConfigFormState();

  // Column header popover state
  const [activePopoverColumnId, setActivePopoverColumnId] = useState<string | null>(null);
  const [popoverAnchorEl, setPopoverAnchorEl] = useState<HTMLElement | null>(null);
  const [visibilityPopoverOpen, setVisibilityPopoverOpen] = useState(false);

  // Attribute/relationship modal state
  const [attributeDialogOpen, setAttributeDialogOpen] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [editingDefinition, setEditingDefinition] = useState<
    EntityAttributeDefinition | RelationshipDefinition | undefined
  >();
  const [deletingDefinition, setDeletingDefinition] = useState<EntityTypeDefinition | undefined>();

  const handleConflict = (request: SaveEntityRequest, response: SaveEntityResponse) => {};

  // Update entity mutation for inline editing
  const { mutateAsync: saveEntity } = useSaveEntityMutation(
    workspaceId,
    entityType.id,
    undefined,
    handleConflict,
  );

  const handleColumnResize = (columnSizing: Record<string, number>) => {
    const current = form.getValues('columnConfiguration');
    const updatedOverrides = { ...current.overrides };
    Object.entries(columnSizing).forEach(([key, width]) => {
      updatedOverrides[key] = { ...updatedOverrides[key], width };
    });
    form.setValue('columnConfiguration.overrides', updatedOverrides, { shouldDirty: true });
  };

  // Header click handler — opens column popover
  const handleHeaderClick = useCallback(
    (columnId: string, anchorEl: HTMLElement) => {
      if (isDraftMode) return;
      setActivePopoverColumnId(columnId);
      setPopoverAnchorEl(anchorEl);
    },
    [isDraftMode],
  );

  // Hide a column via form state
  const handleHideColumn = useCallback(
    (columnId: string) => {
      const current = form.getValues('columnConfiguration');
      form.setValue(
        `columnConfiguration.overrides`,
        {
          ...current.overrides,
          [columnId]: { ...current.overrides[columnId], visible: false },
        },
        { shouldDirty: true },
      );
    },
    [form],
  );

  // Visibility popover handlers
  const handleToggleVisibility = useCallback(
    (columnId: string) => {
      if (columnId === entityType.identifierKey) return;
      const current = form.getValues('columnConfiguration');
      const currentVisible = current.overrides[columnId]?.visible !== false;
      form.setValue(
        `columnConfiguration.overrides`,
        {
          ...current.overrides,
          [columnId]: { ...current.overrides[columnId], visible: !currentVisible },
        },
        { shouldDirty: true },
      );
    },
    [form, entityType.identifierKey],
  );

  const handleReorder = useCallback(
    (newOrder: string[]) => {
      form.setValue('columnConfiguration.order', newOrder, { shouldDirty: true });
    },
    [form],
  );

  const handleShowAll = useCallback(() => {
    const current = form.getValues('columnConfiguration');
    const updatedOverrides = { ...current.overrides };
    Object.keys(updatedOverrides).forEach((key) => {
      updatedOverrides[key] = { ...updatedOverrides[key], visible: true };
    });
    form.setValue('columnConfiguration.overrides', updatedOverrides, { shouldDirty: true });
  }, [form]);

  const handleHideAll = useCallback(() => {
    const current = form.getValues('columnConfiguration');
    const updatedOverrides = { ...current.overrides };
    Object.keys(updatedOverrides).forEach((key) => {
      // Never hide the identifier
      if (key === entityType.identifierKey) return;
      updatedOverrides[key] = { ...updatedOverrides[key], visible: false };
    });
    form.setValue('columnConfiguration.overrides', updatedOverrides, { shouldDirty: true });
  }, [form, entityType.identifierKey]);

  // End-of-header content
  const endOfHeaderContent = useMemo(
    () => (
      <div className="flex items-center gap-1">
        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              onClick={() => {
                setEditingDefinition(undefined);
                setAttributeDialogOpen(true);
              }}
              variant="ghost"
              size="icon"
              className="size-7"
              disabled={isDraftMode}
            >
              <Plus className="size-4" />
              <span className="sr-only">Add property</span>
            </Button>
          </TooltipTrigger>
          <TooltipContent side="top">Add property</TooltipContent>
        </Tooltip>
        <Tooltip>
          <ColumnVisibilityPopover
            entityType={entityType}
            columnConfiguration={form.getValues('columnConfiguration')}
            onToggleVisibility={handleToggleVisibility}
            onReorder={handleReorder}
            onShowAll={handleShowAll}
            onHideAll={handleHideAll}
            open={visibilityPopoverOpen}
            onOpenChange={setVisibilityPopoverOpen}
          >
            <TooltipTrigger asChild>
              <Button
                variant="ghost"
                size="icon"
                className="size-7"
                disabled={isDraftMode}
              >
                <MoreHorizontal className="size-4" />
                <span className="sr-only">Manage columns</span>
              </Button>
            </TooltipTrigger>
          </ColumnVisibilityPopover>
          <TooltipContent side="top">Column settings</TooltipContent>
        </Tooltip>
      </div>
    ),
    [
      entityType,
      form,
      visibilityPopoverOpen,
      handleToggleVisibility,
      handleReorder,
      handleShowAll,
      handleHideAll,
      isDraftMode,
    ],
  );

  // Transform entities to row data
  const rowData = useMemo(() => {
    // Sort entities by createdAt (oldest first) before transforming
    const sortedEntities = [...entities].sort((a, b) => {
      const dateA = a.createdAt ? new Date(a.createdAt).getTime() : 0;
      const dateB = b.createdAt ? new Date(b.createdAt).getTime() : 0;
      return dateA - dateB; // Ascending order (oldest first)
    });

    const rows = transformEntitiesToRows(sortedEntities);

    // Append draft row placeholder when in draft mode (at bottom)
    if (isDraftMode) {
      const draftRow: EntityRow = {
        _entityId: '_draft',
        _isDraft: true,
      };
      return [...rows, draftRow];
    }

    return rows;
  }, [entities, isDraftMode]);

  // Generate columns from entity type with inline editing enabled
  const columns = useMemo(() => {
    const generatedColumns = generateColumnsFromEntityType(entityType, { enableEditing: true });
    return applyColumnOrdering(generatedColumns, entityType.columnConfiguration);
  }, [entityType]);

  // Generate search configuration
  const searchableColumns = useMemo<string[]>(() => {
    return generateSearchConfigFromEntityType(entityType);
  }, [entityType]);

  const emptyMessage = loadingEntities
    ? 'Loading entities...'
    : `No ${entityType.name.plural} found.`;

  // Search configuration — always show when searchable columns exist
  const searchConfig = useMemo(
    () => ({
      // enabled: searchableColumns.length > 0,
      enabled: true,
      searchableColumns,
      placeholder: `Search ${entityType.name.plural.toLowerCase()}...`,
      disabled: isDraftMode,
    }),
    [searchableColumns, isDraftMode, entityType.name.plural],
  );

  // Custom row renderer for draft mode
  const customRowRenderer = useCallback(
    (row: Row<EntityRow>) => {
      // Check if this is the draft row using type-safe guard
      if (isDraftRow(row.original)) {
        return <EntityDraftRow key="_draft" entityType={entityType} row={row} />;
      }
      return null; // Use default rendering
    },
    [entityType],
  );

  // Create debounced resize handler (stable across re-renders)
  const debouncedResizeHandler = useRef(
    debounce((columnSizing: Record<string, number>) => {
      handleColumnResize(columnSizing);
    }, 500), // Wait 500ms after user stops dragging before persisting
  ).current;

  // Column resizing configuration
  const columnResizingConfig: ColumnResizingConfig = useMemo(
    () => ({
      enabled: true,
      columnResizeMode: 'onChange' as const, // Live resizing during drag
    }),
    [],
  );

  // Row ID getter for inline editing
  const getRowId = useCallback((row: EntityRow, _index: number) => row._entityId, []);

  // Cell edit handler for inline editing
  const handleCellEdit = useCallback(
    async (row: EntityRow, columnId: string, newValue: any, _oldValue: any): Promise<boolean> => {
      // Don't allow editing draft rows
      if (isDraftRow(row)) return false;
      const entity = entities.find((e) => e.id === row._entityId);
      if (!entity) return false;

      // Determine if updated column is an attribute or relationship
      const attributeDef: SchemaUUID | undefined = entityType.schema.properties?.[columnId];
      const relationshipDef: RelationshipDefinition | undefined = entityType.relationships?.find(
        (rel) => rel.id === columnId,
      );

      // Prepare updated entity payload
      if (attributeDef) {
        const payloadEntry: EntityAttributePrimitivePayload = {
          type: EntityPropertyType.Attribute,
          value: newValue,
          schemaType: attributeDef.key,
        };

        return await updateEntity(entity, columnId, { payload: payloadEntry });
      }

      if (relationshipDef) {
        const relationship: EntityLink[] = newValue;
        const relationshipEntry: EntityAttributeRelationPayloadReference = {
          type: EntityPropertyType.Relationship,
          relations: relationship.map((rel) => rel.id),
        };

        return await updateEntity(entity, columnId, { payload: relationshipEntry });
      }

      return false;
    },
    [entities, entityType],
  );

  const updateEntity = async (
    entity: Entity,
    columnId: string,
    entry: EntityAttributeRequest,
  ): Promise<boolean> => {
    const payload: Map<string, EntityAttributeRequest> = new Map();
    Object.entries(entity.payload).forEach(([key, value]) => {
      if (isRelationshipPayload(value.payload)) {
        payload.set(key, {
          payload: {
            type: EntityPropertyType.Relationship,
            relations: value.payload.relations.map((rel) => rel.id),
          },
        });
      } else {
        payload.set(key, {
          payload: {
            type: EntityPropertyType.Attribute,
            value: value.payload.value,
            schemaType: value.payload.schemaType,
          },
        });
      }
    });

    const updatedEntity: SaveEntityRequest = {
      id: entity.id,
      payload: {
        ...Object.fromEntries(payload),
        [columnId]: entry,
      },
    };

    const response = await saveEntity(updatedEntity);
    return !response.errors && !!response.entity;
  };

  // Query filter state (EntityQueryBuilder)
  const [_queryFilter, setQueryFilter] = useState<QueryFilter | undefined>();
  const handleQueryFilterChange = useCallback((filter: QueryFilter | undefined) => {
    setQueryFilter(filter);
    // TODO: integrate with server-side entity query when API hook is wired up
  }, []);

  // Custom filter UI for the entity data table
  const filterContent = useMemo(
    () => (
      <EntityQueryBuilder
        entityType={entityType}
        value={_queryFilter}
        onChange={handleQueryFilterChange}
      />
    ),
    [entityType, _queryFilter, handleQueryFilterChange],
  );

  return (
    <Form {...form}>
      <div className="w-full min-w-0 space-y-4">
        <EntityTypeHeader>
          <div className="text-sm text-muted-foreground italic">
            Manage your entities and their data
          </div>
        </EntityTypeHeader>

        {/* Data table with toolbar: search | filter + add */}
        <DataTableProvider
          initialData={rowData}
          getRowId={getRowId}
          onCellEdit={handleCellEdit}
          onColumnWidthsChange={(columnSizing) => debouncedResizeHandler(columnSizing)}
        >
          <DataTable
            columns={columns}
            rowSelection={{
              enabled: true,
              clearOnFilterChange: true,
              actionComponent: ({ selectedRows, clearSelection }) => (
                <EntityActionBar
                  selectedRows={selectedRows}
                  clearSelection={clearSelection}
                  workspaceId={workspaceId}
                  entityTypeId={entityType.id}
                />
              ),
            }}
            enableDragDrop
            alwaysShowActionHandles={true}
            getRowId={(row) => row._entityId}
            search={searchConfig}
            filterContent={filterContent}
            columnResizing={columnResizingConfig}
            emptyMessage={emptyMessage}
            className={cn(className)}
            enableInlineEdit={true}
            customRowRenderer={customRowRenderer}
            addingNewEntry={isDraftMode}
            onHeaderClick={handleHeaderClick}
            endOfHeaderContent={endOfHeaderContent}
            scrollContainerClassName="max-h-[calc(100dvh-18rem)]"
            footerContent={
              !isDraftMode ? (
                <button
                  type="button"
                  onClick={enterDraftMode}
                  className="flex w-full items-center gap-1.5 border-t border-border/40 px-3 py-1.5 text-sm text-muted-foreground/50 transition-colors hover:bg-muted/30 hover:text-muted-foreground"
                >
                  <Plus className="size-3.5" />
                  <span>New {entityType.name.singular}</span>
                </button>
              ) : undefined
            }
          />

          {/* Column header popover — must be inside DataTableProvider */}
          <ColumnHeaderPopover
            columnId={activePopoverColumnId}
            entityType={entityType}
            workspaceId={workspaceId}
            anchorEl={popoverAnchorEl}
            onClose={() => {
              setActivePopoverColumnId(null);
              setPopoverAnchorEl(null);
            }}
            onEditProperties={(def) => {
              setEditingDefinition(def.definition);
              setAttributeDialogOpen(true);
            }}
            onDelete={(def) => {
              setDeletingDefinition(def);
              setDeleteDialogOpen(true);
            }}
            onInsert={(_position, _refColumnId) => {
              setEditingDefinition(undefined);
              setAttributeDialogOpen(true);
            }}
            onHide={handleHideColumn}
          />
        </DataTableProvider>

        {/* Attribute/relationship form modal */}
        <AttributeFormModal
          dialog={{ open: attributeDialogOpen, setOpen: setAttributeDialogOpen }}
          type={entityType}
          selectedAttribute={editingDefinition}
        />

        {/* Delete definition modal */}
        {deletingDefinition && (
          <DeleteDefinitionModal
            workspaceId={workspaceId}
            dialog={{ open: deleteDialogOpen, setOpen: setDeleteDialogOpen }}
            type={entityType}
            definition={deletingDefinition}
          />
        )}
      </div>
    </Form>
  );
};
