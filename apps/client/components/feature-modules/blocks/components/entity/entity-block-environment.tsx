// 'use client';

// import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
// import { Button } from '@riven/ui';
// import { Skeleton } from '@/components/ui/skeleton';
// import { AlertCircle, PlusIcon } from 'lucide-react';
// import { FC, ReactNode, useMemo, useState } from 'react';
// import { BlockEditProvider } from '../../context/block-edit-provider';
// import {
//   BlockEnvironmentProvider,
//   useBlockEnvironment,
// } from '../../context/block-environment-provider';
// import { BlockFocusProvider } from '../../context/block-focus-provider';
// import { BlockHydrationProvider } from '../../context/block-hydration-provider';
// import { RenderElementProvider } from '../../context/block-renderer-provider';
// import { GridContainerProvider } from '../../context/grid-container-provider';
// import { GridProvider } from '../../context/grid-provider';
// import { LayoutChangeProvider } from '../../context/layout-change-provider';
// import { LayoutHistoryProvider } from '../../context/layout-history-provider';
// import {
//     TrackedEnvironmentProvider,
//     useTrackedEnvironment,
// } from "../../context/tracked-environment-provider";
// import { useBlockTypes } from "../../hooks/use-block-types";
// import { useEntityLayout } from "../../hooks/use-entity-layout";
// import { BlockEnvironmentGridSync } from "../../hooks/use-environment-grid-sync";
// import type { BlockType, WrapElementProvider } from "@/lib/types/block";
// import { createBlockInstanceFromType } from "../../util/block/factory/instance.factory";
// import { blockTypesToOptions } from "../../util/type-picker-options";
// import type { ApplicationEntityType, BlockType as GeneratedBlockType } from "@/lib/types";
// import { BlockEditDrawer, EditModeIndicator } from "../forms";
// import { ENTITY_TYPE_OPTIONS, TypePickerModal } from "../modals/type-picker-modal";
// import { KeyboardNavigationHandler } from "../navigation/keyboard-navigation-handler";
// import { WidgetEnvironmentSync } from "../sync/widget.sync";
// import { AddBlockDialog } from "./add-block-dialog";

// /**
//  * Props for EntityBlockEnvironment component.
//  *
//  * This is the generic wrapper that manages block environments for any entity type.
//  */
// export interface EntityBlockEnvironmentProps {
//     /** UUID of the entity (client, workspace, etc.) */
//     entityId: string;
//     /** Type of entity (from ApplicationEntityType enum, e.g. 'ENTITY' for user-defined entities) */
//     entityType: ApplicationEntityType;
//     /** Workspace ID for the entity */
//     workspaceId: string;
//     /** Optional toolbar component to render above the grid */
//     renderToolbar?: () => React.ReactNode;
//     /** Whether to show the default "Add Block" toolbar (default: true) */
//     showDefaultToolbar?: boolean;
//     /** Optional wrapper function to customize the outer container */
//     renderWrapper?: (children: React.ReactNode) => React.ReactNode;
//     /** Optional element wrapper for editor panels (slash menu, toolbar, etc.) */
//     wrapElement?: (args: WrapElementProvider) => ReactNode;
// }

// /**
//  * EntityBlockEnvironment - Generic block environment wrapper for entities.
//  *
//  * This component:
//  * - Loads the block environment for an entity (with lazy initialization)
//  * - Manages all required providers in the correct hierarchy
//  * - Handles loading and error states
//  * - Renders the grid with all blocks
//  * - Supports custom toolbars and wrappers
//  *
//  * @example
//  * <EntityBlockEnvironment
//  *   entityId={clientId}
//  *   entityType={ApplicationEntityType.Entity}
//  *   workspaceId={workspaceId}
//  *   renderToolbar={() => <ClientToolbar />}
//  * />
//  */
// export const EntityBlockEnvironment: FC<EntityBlockEnvironmentProps> = ({
//   entityId,
//   entityType,
//   workspaceId,
//   renderToolbar,
//   showDefaultToolbar = true,
//   renderWrapper,
//   wrapElement,
// }) => {
//   const { environment, isLoading, error } = useEntityLayout(workspaceId, entityId, entityType);

//   const gridOptions = useMemo(() => {
//     return environment?.layout?.layout;
//   }, [environment]);

//   // Loading state
//   if (isLoading) {
//     return (
//       <div className="h-full w-full space-y-4">
//         <Skeleton className="h-12 w-full" />
//         <div className="grid grid-cols-2 gap-4">
//           <Skeleton className="h-64 w-full" />
//           <Skeleton className="h-64 w-full" />
//         </div>
//         <Skeleton className="h-48 w-full" />
//       </div>
//     );
//   }

//   // Error state
//   if (error) {
//     return (
//       <Alert variant="destructive">
//         <AlertCircle className="h-4 w-4" />
//         <AlertTitle>Failed to load block environment</AlertTitle>
//         <AlertDescription>
//           {error.message || 'An unexpected error occurred while loading the block environment.'}
//         </AlertDescription>
//       </Alert>
//     );
//   }

//   // No environment found (shouldn't happen with lazy initialization)
//   if (!environment) {
//     return (
//       <Alert>
//         <AlertCircle className="h-4 w-4" />
//         <AlertTitle>No block environment found</AlertTitle>
//         <AlertDescription>
//           Unable to load or create block environment for this entity.
//         </AlertDescription>
//       </Alert>
//     );
//   }

//   const toolbar = renderToolbar ? (
//     renderToolbar()
//   ) : (
//     <EntityToolbar workspaceId={workspaceId} entityType={entityType} />
//   );

//   // Render the complete block environment with provider hierarchy
//   const content = (
//     <BlockEnvironmentProvider
//       workspaceId={workspaceId}
//       entityId={entityId}
//       entityType={entityType}
//       environment={environment}
//     >
//       <GridProvider initialOptions={gridOptions}>
//         <LayoutHistoryProvider>
//           <LayoutChangeProvider>
//             <TrackedEnvironmentProvider>
//               <BlockHydrationProvider>
//                 <BlockFocusProvider>
//                   <BlockEditProvider>
//                     <EditModeIndicator />
//                     <KeyboardNavigationHandler />
//                     {(showDefaultToolbar || renderToolbar) && toolbar}
//                     <BlockEnvironmentGridSync />
//                     <WidgetEnvironmentSync />
//                     <GridContainerProvider>
//                       <RenderElementProvider wrapElement={wrapElement} />
//                     </GridContainerProvider>
//                     <BlockEditDrawer />
//                   </BlockEditProvider>
//                 </BlockFocusProvider>
//               </BlockHydrationProvider>
//             </TrackedEnvironmentProvider>
//           </LayoutChangeProvider>
//         </LayoutHistoryProvider>
//       </GridProvider>
//     </BlockEnvironmentProvider>
//   );

//   // Apply custom wrapper if provided
//   return renderWrapper ? renderWrapper(content) : content;
// };

// /**
//  * Default toolbar component for entity block environments.
//  *
//  * Provides an "Add Block" button that opens a dialog for selecting
//  * and adding block types to the layout.
//  */
// interface EntityToolbarProps {
//     workspaceId: string;
//     entityType: ApplicationEntityType;
// }

// const EntityToolbar: FC<EntityToolbarProps> = ({ workspaceId, entityType }) => {
//   const [dialogOpen, setDialogOpen] = useState(false);
//   const [typePickerOpen, setTypePickerOpen] = useState(false);
//   const [selectedBlockType, setSelectedBlockType] = useState<BlockType | null>(null);
//   const [pickerConfig, setPickerConfig] = useState<{
//     title: string;
//     description: string;
//     multiSelect: boolean;
//     required: boolean;
//     options: import('../modals/type-picker-modal').TypeOption[];
//   } | null>(null);

//     const { addTrackedBlock } = useTrackedEnvironment();
//     const { environment } = useBlockEnvironment();
//     const { data: availableBlockTypes } = useBlockTypes(workspaceId);

//   const handleBlockTypeSelect = (blockType: BlockType) => {
//     // Check if this block type requires type selection
//     if (blockType.key === 'entity_reference') {
//       // Entity reference: single select entity type (required)
//       setSelectedBlockType(blockType);
//       setPickerConfig({
//         title: 'Select Entity Type',
//         description: 'Choose which type of entities this block will reference',
//         multiSelect: false,
//         required: true,
//         options: ENTITY_TYPE_OPTIONS,
//       });
//       setTypePickerOpen(true);
//       setDialogOpen(false);
//     } else if (blockType.key === 'block_list' || blockType.key === 'content_block_list') {
//       // Block list: multi-select block types (optional)
//       // Convert available block types to options
//       const blockTypeOptions = availableBlockTypes ? blockTypesToOptions(availableBlockTypes) : [];

//       setSelectedBlockType(blockType);
//       setPickerConfig({
//         title: 'Select Allowed Block Types',
//         description: 'Choose which block types can be added to this list (or allow all)',
//         multiSelect: true,
//         required: false,
//         options: blockTypeOptions,
//       });
//       setTypePickerOpen(true);
//       setDialogOpen(false);
//     } else {
//       // Regular block: create immediately
//       createAndAddBlock(blockType);
//     }
//   };

//   const handleTypeSelect = (selectedTypes: string[] | null) => {
//     if (!selectedBlockType) return;

//     if (selectedBlockType.key === 'entity_reference') {
//       // Entity reference: selectedTypes[0] is the EntityType
//       const selectedValue = selectedTypes?.[0];
//       const entityType =
//         selectedValue && Object.values(EntityType).includes(selectedValue as EntityType)
//           ? (selectedValue as EntityType)
//           : undefined;
//       if (!entityType) return; // Required field, bail if invalid
//       createAndAddBlock(selectedBlockType, { entityType });
//     } else if (
//       selectedBlockType.key === 'block_list' ||
//       selectedBlockType.key === 'content_block_list'
//     ) {
//       // Block list: selectedTypes is the array of allowed block type keys (or null for all)
//       createAndAddBlock(selectedBlockType, { allowedTypes: selectedTypes });
//     }

//     setSelectedBlockType(null);
//     setPickerConfig(null);
//   };

//   const createAndAddBlock = (
//     blockType: BlockType,
//     options?: { entityType?: EntityType; allowedTypes?: string[] | null },
//   ) => {
//     // Create a new block instance from the selected type
//     const newBlock = createBlockInstanceFromType(blockType, workspaceId, {
//       name: blockType.name,
//       entityType: options?.entityType,
//       allowedTypes: options?.allowedTypes,
//     });

//     // Add the block to the environment
//     addTrackedBlock(newBlock);

//     // Close dialogs
//     setDialogOpen(false);
//     setTypePickerOpen(false);
//   };

//   const handleTypeModalOpenChange = (open: boolean) => {
//     setTypePickerOpen(open);
//     if (!open) {
//       setSelectedBlockType(null);
//       setPickerConfig(null);
//     }
//   };

//   const hasBlocks = environment.trees.length > 0;

//     return (
//         <div className="mb-4">
//             {/* Show empty state if no blocks exist */}
//             {!hasBlocks && (
//                 <div className="flex flex-col items-center justify-center py-12 px-4 border-2 border-dashed border-border rounded-lg bg-muted/10">
//                     <div className="text-center space-y-3 max-w-md">
//                         <h3 className="text-lg font-semibold text-foreground">No blocks yet</h3>
//                         <p className="text-sm text-muted-foreground">
//                             Get started by adding your first block. Choose from layout containers,
//                             lists, references, and more.
//                         </p>
//                         <Button onClick={() => setDialogOpen(true)} size="lg" className="mt-4">
//                             <PlusIcon className="size-4 mr-2" />
//                             Add Your First Block
//                         </Button>
//                     </div>
//                 </div>
//             )}

//             {/* Show toolbar button if blocks exist */}
//             {hasBlocks && (
//                 <div className="flex gap-2">
//                     <Button onClick={() => setDialogOpen(true)} variant="outline" size="sm">
//                         <PlusIcon className="size-4 mr-2" />
//                         Add Block
//                     </Button>
//                 </div>
//             )}

//             {/* Add Block Dialog */}
//             <AddBlockDialog
//                 open={dialogOpen}
//                 onOpenChange={setDialogOpen}
//                 workspaceId={workspaceId}
//                 onBlockTypeSelect={handleBlockTypeSelect}
//             />

//             {/* Type Picker Modal */}
//             {pickerConfig && (
//                 <TypePickerModal
//                     open={typePickerOpen}
//                     onOpenChange={handleTypeModalOpenChange}
//                     title={pickerConfig.title}
//                     description={pickerConfig.description}
//                     options={pickerConfig.options}
//                     multiSelect={pickerConfig.multiSelect}
//                     required={pickerConfig.required}
//                     onSelect={handleTypeSelect}
//                 />
//             )}
//         </div>
//       )}

//       {/* Show toolbar button if blocks exist */}
//       {hasBlocks && (
//         <div className="flex gap-2">
//           <Button onClick={() => setDialogOpen(true)} variant="outline" size="sm">
//             <PlusIcon className="mr-2 size-4" />
//             Add Block
//           </Button>
//         </div>
//       )}

//       {/* Add Block Dialog */}
//       <AddBlockDialog
//         open={dialogOpen}
//         onOpenChange={setDialogOpen}
//         workspaceId={workspaceId}
//         entityType={entityType}
//         onBlockTypeSelect={handleBlockTypeSelect}
//       />

//       {/* Type Picker Modal */}
//       {pickerConfig && (
//         <TypePickerModal
//           open={typePickerOpen}
//           onOpenChange={handleTypeModalOpenChange}
//           title={pickerConfig.title}
//           description={pickerConfig.description}
//           options={pickerConfig.options}
//           multiSelect={pickerConfig.multiSelect}
//           required={pickerConfig.required}
//           onSelect={handleTypeSelect}
//         />
//       )}
//     </div>
//   );
// };
