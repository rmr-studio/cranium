'use client';

import { ActionColumnConfig, ColumnResizingConfig, DataTable, DataTableProvider } from '@/components/ui/data-table';
import { Form } from '@/components/ui/form';
import type { QueryFilter } from '@/lib/types/models/QueryFilter';
import {
  Entity,
  EntityAttributeDefinition,
  EntityType,
  EntityTypeDefinition,
  RelationshipDefinition,
} from '@/lib/types/entity';
import { debounce } from '@/lib/util/debounce.util';
import type { ClassNameProps } from '@riven/utils';
import { cn } from '@riven/utils';

import { Button } from '@riven/ui/button';
import { Row } from '@tanstack/react-table';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { MoreHorizontal, Plus } from 'lucide-react';
import { FC, useCallback, useMemo, useRef, useState } from 'react';
import { useConfigFormState } from '../../context/configuration-provider';
import { useEntityDraft } from '../../context/entity-provider';
import { useEntityColumnConfig } from '../../hooks/use-entity-column-config';
import { useEntityInlineEdit } from '../../hooks/use-entity-inline-edit';
import { useEntityTableData } from '../../hooks/use-entity-table-data';
import { EntityQueryBuilder } from '../query/entity-query-builder';
import { EntityTypeHeader } from '../ui/entity-type-header';
import { AttributeFormModal } from '../ui/modals/type/attribute-form-modal';
import { DeleteDefinitionModal } from '../ui/modals/type/delete-definition-modal';
import { ColumnHeaderPopover } from './column-header-popover';
import { ColumnVisibilityPopover } from './column-visibility-popover';
import { EntityDraftRow } from './entity-draft-row';
import EntityActionBar from './entity-table-action-bar';
import { EntityRow, isDraftRow } from './entity-table-utils';

export interface Props extends ClassNameProps {
  entityType: EntityType;
  entities: Entity[];
  loadingEntities?: boolean;
  workspaceId: string;
}

export const EntityDataTable: FC<Props> = ({
  entityType,
  entities,
  loadingEntities,
  className,
  workspaceId,
}) => {
  const { isDraftMode, enterDraftMode } = useEntityDraft();
  const { form } = useConfigFormState();

  // Extracted hooks
  const { rowData, columns, searchableColumns } = useEntityTableData(entityType, entities, isDraftMode);
  const { handleCellEdit } = useEntityInlineEdit(workspaceId, entityType, entities);
  const {
    handleColumnResize,
    handleHideColumn,
    handleToggleVisibility,
    handleReorder,
    handleShowAll,
    handleHideAll,
  } = useEntityColumnConfig(form, entityType);

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

  // Header click handler — opens column popover
  const handleHeaderClick = useCallback(
    (columnId: string, anchorEl: HTMLElement) => {
      if (isDraftMode) return;
      setActivePopoverColumnId(columnId);
      setPopoverAnchorEl(anchorEl);
    },
    [isDraftMode],
  );

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
      if (isDraftRow(row.original)) {
        return <EntityDraftRow key="_draft" entityType={entityType} row={row} />;
      }
      return null;
    },
    [entityType],
  );

  // Create debounced resize handler (stable across re-renders)
  const debouncedResizeHandler = useRef(
    debounce((columnSizing: Record<string, number>) => {
      handleColumnResize(columnSizing);
    }, 500),
  ).current;

  // Column resizing configuration
  const columnResizingConfig: ColumnResizingConfig = useMemo(
    () => ({
      enabled: true,
      columnResizeMode: 'onChange' as const,
    }),
    [],
  );

  // Action column configuration
  const actionColumnConfig: ActionColumnConfig = useMemo(
    () => ({
      dragHandle: { enabled: true, visibility: 'hover-or-selected' },
      checkbox: { enabled: true, visibility: 'hover-or-selected' },
    }),
    [],
  );

  // Row ID getter for inline editing
  const getRowId = useCallback((row: EntityRow, _index: number) => row._entityId, []);

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
            actionColumnConfig={actionColumnConfig}
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
