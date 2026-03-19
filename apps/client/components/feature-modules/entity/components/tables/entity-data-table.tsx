'use client';

import { ActionColumnConfig, ColumnResizingConfig, DataTable, DataTableProvider, InfiniteScrollConfig } from '@/components/ui/data-table';
import { Form } from '@/components/ui/form';
import { TruncatedChipList } from '@/components/ui/truncated-chip-list';
import {
  Entity,
  EntityAttributeDefinition,
  EntityType,
  EntityTypeDefinition,
  NoteEntry,
  QueryFilter,
  RelationshipDefinition,
  SchemaUUID,
  createEmptyNoteEntry,
  formatNoteTimestamp,
} from '@/lib/types/entity';
import { debounce } from '@/lib/util/debounce.util';
import type { ClassNameProps } from '@riven/utils';
import { cn } from '@riven/utils';

import { Button } from '@riven/ui/button';
import { Row, SortingState } from '@tanstack/react-table';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { MoreHorizontal, Plus } from 'lucide-react';
import dynamic from 'next/dynamic';
import { FC, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useConfigFormState } from '../../context/configuration-provider';
import { useEntityDraft } from '../../context/entity-provider';
import { useEntityColumnConfig } from '../../hooks/use-entity-column-config';
import { useEntityInlineEdit } from '../../hooks/use-entity-inline-edit';
import { useEntityTableData } from '../../hooks/use-entity-table-data';
import { useEntityQuery } from '../../hooks/query/use-entity-query';
import { useEntitySearch } from '../../hooks/use-entity-search';
import { EntityQueryBuilder } from '../query/entity-query-builder';
import { EntityTypeHeader } from '../ui/entity-type-header';
import { AttributeFormModal } from '../ui/modals/type/attribute-form-modal';
import { DeleteDefinitionModal } from '../ui/modals/type/delete-definition-modal';
import { ColumnHeaderPopover } from './column-header-popover';
import { ColumnVisibilityPopover } from './column-visibility-popover';
import { EntityDraftRow } from './entity-draft-row';
import EntityActionBar from './entity-table-action-bar';
import { EntityRow, isDraftRow, generateSearchConfigFromEntityType } from './entity-table-utils';
import { toast } from 'sonner';

const NoteDrawer = dynamic(
  () => import('@/components/feature-modules/entity/components/notes/note-drawer').then((m) => m.NoteDrawer),
  { ssr: false },
);

export interface Props extends ClassNameProps {
  entityType: EntityType;
  workspaceId: string;
}

export const EntityDataTable: FC<Props> = ({
  entityType,
  className,
  workspaceId,
}) => {
  const { isDraftMode, enterDraftMode } = useEntityDraft();
  const { form } = useConfigFormState();

  // Note drawer state
  const [noteDrawerState, setNoteDrawerState] = useState<{
    entity: Entity;
    attributeId: string;
    schema: SchemaUUID;
    noteId: string;
    allNotes: NoteEntry[];
  } | null>(null);

  // Search state (debounced)
  const { searchTerm, setSearchTerm, debouncedSearch, clearSearch } = useEntitySearch();

  // Sorting state (server-side via queryEntities)
  const [sorting, setSorting] = useState<SortingState>([]);

  // Query filter state (EntityQueryBuilder)
  const [queryFilter, setQueryFilter] = useState<QueryFilter | undefined>();
  const handleQueryFilterChange = useCallback((filter: QueryFilter | undefined) => {
    setQueryFilter(filter);
  }, []);

  // Searchable attribute IDs for server-side search (derived from entity type schema only)
  const searchableColumns = useMemo(
    () => generateSearchConfigFromEntityType(entityType),
    [entityType],
  );

  // Infinite query — owns all entity fetching
  const {
    data,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    isPending,
    isError,
    error,
    isPlaceholderData,
  } = useEntityQuery({
    workspaceId,
    entityTypeId: entityType.id,
    debouncedSearch: debouncedSearch || undefined,
    searchableAttributeIds: searchableColumns,
    queryFilter,
    sorting,
  });

  // Surface query errors as toasts
  useEffect(() => {
    if (isError && error) {
      toast.error(`Failed to load entities: ${error.message}`, { id: 'entity-query-error' });
    }
  }, [isError, error]);

  // Flatten pages into a single entity list
  const entities = useMemo(
    () => data?.pages.flatMap((page) => page.entities) ?? [],
    [data?.pages],
  );

  // Full table data (rows, columns) from flattened entities
  const { rowData, columns: baseColumns } = useEntityTableData(entityType, entities, isDraftMode);

  // Wrap NOTE columns with TruncatedChipList + drawer click handlers
  const columns = useMemo(() => {
    return baseColumns.map((col) => {
      if (!col.meta?.isNote) return col;

      return {
        ...col,
        cell: ({ row }: { row: Row<EntityRow> }) => {
          const entity = row.original._entity;
          const value: NoteEntry[] = row.getValue(col.accessorKey as string) ?? [];
          const colSchema = entityType.schema.properties?.[col.accessorKey as string];
          if (!entity || !colSchema) return null;

          const chips = value.map((note) => ({
            id: note.id,
            label: note.title || 'Untitled',
            subtitle: formatNoteTimestamp(note.updatedAt),
            icon: colSchema.icon,
          }));

          return (
            <TruncatedChipList
              items={chips}
              maxVisible={2}
              onChipClick={(chip) => {
                setNoteDrawerState({
                  entity,
                  attributeId: col.accessorKey as string,
                  schema: colSchema,
                  noteId: chip.id,
                  allNotes: value,
                });
              }}
              onOverflowClick={() => {
                if (value.length > 0) {
                  setNoteDrawerState({
                    entity,
                    attributeId: col.accessorKey as string,
                    schema: colSchema,
                    noteId: value[0].id,
                    allNotes: value,
                  });
                }
              }}
              emptyState={
                <button
                  type="button"
                  className="text-sm text-muted-foreground/50 opacity-0 transition-opacity group-hover/row:opacity-100 hover:text-muted-foreground"
                  onClick={(e) => {
                    e.stopPropagation();
                    const newNote = createEmptyNoteEntry();
                    setNoteDrawerState({
                      entity,
                      attributeId: col.accessorKey as string,
                      schema: colSchema,
                      noteId: newNote.id,
                      allNotes: [newNote],
                    });
                  }}
                >
                  Add note...
                </button>
              }
            />
          );
        },
      };
    });
  }, [baseColumns, entityType]);

  // Entity lookup for inline edit
  const entityMap = useMemo(
    () => new Map(entities.map((e) => [e.id, e])),
    [entities],
  );
  const getEntityById = useCallback(
    (id: string) => entityMap.get(id),
    [entityMap],
  );

  const { handleCellEdit } = useEntityInlineEdit(workspaceId, entityType, getEntityById);
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

  const emptyMessage = isPending
    ? 'Loading entities...'
    : isError
      ? 'Failed to load entities. Please try again.'
      : `No ${entityType.name.plural} found.`;

  // Search configuration — server-side via useEntitySearch
  const searchConfig = useMemo(
    () => ({
      enabled: true,
      searchableColumns,
      placeholder: `Search ${entityType.name.plural.toLowerCase()}...`,
      disabled: isDraftMode,
      serverSide: true,
      onSearchChange: setSearchTerm,
    }),
    [searchableColumns, isDraftMode, entityType.name.plural, setSearchTerm],
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

  // Row ID getter
  const getRowId = useCallback((row: EntityRow, _index: number) => row._entityId, []);

  // Infinite scroll configuration
  const infiniteScrollConfig: InfiniteScrollConfig | undefined = useMemo(
    () =>
      hasNextPage !== undefined
        ? {
            onLoadMore: fetchNextPage,
            isLoadingMore: isFetchingNextPage,
            hasMore: hasNextPage ?? false,
          }
        : undefined,
    [fetchNextPage, hasNextPage, isFetchingNextPage],
  );

  // Custom filter UI
  const filterContent = useMemo(
    () => (
      <EntityQueryBuilder
        entityType={entityType}
        value={queryFilter}
        onChange={handleQueryFilterChange}
      />
    ),
    [entityType, queryFilter, handleQueryFilterChange],
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
            enableSorting
            serverSideSorting={{
              enabled: true,
              onSortingChange: setSorting,
              sorting,
            }}
            enableDragDrop
            actionColumnConfig={actionColumnConfig}
            columnOrdering={{
              enabled: true,
              onColumnOrderChange: handleReorder,
            }}
            getRowId={(row) => row._entityId}
            search={searchConfig}
            filterContent={filterContent}
            columnResizing={columnResizingConfig}
            emptyMessage={emptyMessage}
            className={cn(className, isPlaceholderData && 'opacity-70 transition-opacity')}
            enableInlineEdit={true}
            customRowRenderer={customRowRenderer}
            addingNewEntry={isDraftMode}
            onHeaderClick={handleHeaderClick}
            endOfHeaderContent={endOfHeaderContent}
            scrollContainerClassName="max-h-[calc(100dvh-18rem)]"
            infiniteScroll={infiniteScrollConfig}
            footerContent={
              !isDraftMode ? (
                <button
                  type="button"
                  onClick={() => {
                    clearSearch();
                    enterDraftMode();
                  }}
                  className="flex w-full items-center gap-1.5 border-t border-border/40 px-3 py-1.5 text-sm text-muted-foreground/50 transition-colors hover:bg-muted/30 hover:text-muted-foreground"
                >
                  <Plus className="size-3.5" />
                  <span>New {entityType.name.singular}</span>
                </button>
              ) : undefined
            }
          />

          {/* Column header popover */}
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

        {/* Note drawer */}
        {noteDrawerState && (
          <NoteDrawer
            open={!!noteDrawerState}
            onClose={() => setNoteDrawerState(null)}
            entity={noteDrawerState.entity}
            attributeId={noteDrawerState.attributeId}
            schema={noteDrawerState.schema}
            workspaceId={workspaceId}
            entityTypeId={entityType.id}
            noteId={noteDrawerState.noteId}
            allNotes={noteDrawerState.allNotes}
          />
        )}
      </div>
    </Form>
  );
};
