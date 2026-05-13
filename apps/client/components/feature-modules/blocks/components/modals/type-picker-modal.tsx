// 'use client';

// import { Button } from '@cranium/ui/button';
// import { Checkbox } from '@/components/ui/checkbox';
// import {
//     Dialog,
//     DialogContent,
//     DialogDescription,
//     DialogFooter,
//     DialogHeader,
//     DialogTitle,
// } from '@cranium/ui/dialog';
// import type { EntityType } from "@/lib/types/entity";
// import { Building, FileText, FolderKanban, Receipt, UserCircle, Users } from "lucide-react";
// import { FC, useEffect, useState } from "react";

// /**
//  * Configuration for a type option in the picker
//  */
// export interface TypeOption {
//   value: string;
//   label: string;
//   description?: string;
//   icon?: React.ComponentType<{ className?: string }>;
// }

// /**
//  * Props for the TypePickerModal component
//  */
// export interface TypePickerModalProps {
//   /** Whether the modal is open */
//   open: boolean;
//   /** Callback to change the open state */
//   onOpenChange: (open: boolean) => void;
//   /** Title for the modal */
//   title: string;
//   /** Description text */
//   description: string;
//   /** Available type options */
//   options: TypeOption[];
//   /** Whether to allow multiple selections */
//   multiSelect?: boolean;
//   /** Whether a selection is required (disables "Skip" for single select) */
//   required?: boolean;
//   /** Callback when types are selected */
//   onSelect: (selectedTypes: string[] | null) => void;
// }

// /**
//  * Generic type picker modal for selecting entity types or block types.
//  * Supports both single and multi-select modes, and optional vs required selection.
//  *
//  * @example
//  * // Entity type picker (single select, required)
//  * <TypePickerModal
//  *   title="Select Entity Type"
//  *   description="Choose which type of entities this block will reference"
//  *   options={ENTITY_TYPE_OPTIONS}
//  *   multiSelect={false}
//  *   required={true}
//  *   onSelect={(types) => createBlock({ entityType: types[0] })}
//  * />
//  *
//  * // Block type picker (multi-select, optional)
//  * <TypePickerModal
//  *   title="Select Allowed Block Types"
//  *   description="Choose which block types can be added to this list"
//  *   options={BLOCK_TYPE_OPTIONS}
//  *   multiSelect={true}
//  *   required={false}
//  *   onSelect={(types) => createBlock({ allowedTypes: types })}
//  * />
//  */
// export const TypePickerModal: FC<TypePickerModalProps> = ({
//   open,
//   onOpenChange,
//   title,
//   description,
//   options,
//   multiSelect = false,
//   required = false,
//   onSelect,
// }) => {
//   const [selectedTypes, setSelectedTypes] = useState<string[]>([]);

//   const handleToggle = (value: string) => {
//     if (multiSelect) {
//       setSelectedTypes((prev) =>
//         prev.includes(value) ? prev.filter((t) => t !== value) : [...prev, value],
//       );
//     } else {
//       setSelectedTypes([value]);
//       // For single select, immediately call onSelect and close
//       onSelect([value]);
//       onOpenChange(false);
//       setSelectedTypes([]);
//     }
//   };

//   const handleConfirm = () => {
//     onSelect(selectedTypes.length > 0 ? selectedTypes : null);
//     onOpenChange(false);
//     setSelectedTypes([]);
//   };

//   const handleSkip = () => {
//     onSelect(null);
//     onOpenChange(false);
//     setSelectedTypes([]);
//   };

//   // Reset state when modal closes
//   useEffect(() => {
//     if (!open) {
//       setSelectedTypes([]);
//     }
//   }, [open]);

//   // Single select mode renders as button grid
//   if (!multiSelect) {
//     return (
//       <Dialog open={open} onOpenChange={onOpenChange}>
//         <DialogContent className="sm:max-w-[500px]">
//           <DialogHeader>
//             <DialogTitle>{title}</DialogTitle>
//             <DialogDescription>{description}</DialogDescription>
//           </DialogHeader>
//           <div className="grid grid-cols-2 gap-3 py-4">
//             {options.map(({ value, label, icon: Icon }) => (
//               <Button
//                 key={value}
//                 variant="outline"
//                 className="h-24 flex-col gap-2"
//                 onClick={() => handleToggle(value)}
//               >
//                 {Icon && <Icon className="h-8 w-8" />}
//                 <span className="font-medium">{label}</span>
//               </Button>
//             ))}
//           </div>
//           {!required && (
//             <DialogFooter>
//               <Button variant="ghost" onClick={handleSkip}>
//                 Skip
//               </Button>
//             </DialogFooter>
//           )}
//         </DialogContent>
//       </Dialog>
//     );
//   }

//   // Multi-select mode renders as checkbox list
//   return (
//     <Dialog open={open} onOpenChange={onOpenChange}>
//       <DialogContent className="sm:max-w-[500px]">
//         <DialogHeader>
//           <DialogTitle>{title}</DialogTitle>
//           <DialogDescription>{description}</DialogDescription>
//         </DialogHeader>
//         <div className="max-h-[400px] space-y-3 overflow-y-auto py-4">
//           {options.map(({ value, label, description, icon: Icon }, index) => (
//             <label
//               key={value}
//               className="flex cursor-pointer items-start gap-3 rounded-lg border p-3 transition-colors hover:bg-accent/50"
//               htmlFor={`type-option-${index}`}
//             >
//               <Checkbox
//                 id={`type-option-${index}`}
//                 checked={selectedTypes.includes(value)}
//                 onCheckedChange={() => handleToggle(value)}
//               />
//               <div className="flex-1">
//                 <div className="flex items-center gap-2">
//                   {Icon && <Icon className="h-4 w-4 text-muted-foreground" />}
//                   <span className="font-medium">{label}</span>
//                 </div>
//                 {description && <p className="mt-1 text-sm text-muted-foreground">{description}</p>}
//               </div>
//             </label>
//           ))}
//         </div>
//         <DialogFooter className="gap-2">
//           {!required && (
//             <Button variant="ghost" onClick={handleSkip}>
//               Allow All Types
//             </Button>
//           )}
//           <Button onClick={handleConfirm} disabled={required && selectedTypes.length === 0}>
//             {selectedTypes.length > 0
//               ? `Select ${selectedTypes.length} Type${selectedTypes.length > 1 ? 's' : ''}`
//               : 'Confirm'}
//           </Button>
//         </DialogFooter>
//       </DialogContent>
//     </Dialog>
//   );
// };

// /**
//  * Predefined entity type options for entity reference blocks
//  */
// export const ENTITY_TYPE_OPTIONS: TypeOption[] = [
//   {
//     value: EntityType.CLIENT,
//     label: 'Clients',
//     icon: Users,
//   },
//   {
//     value: EntityType.INVOICE,
//     label: 'Invoices',
//     icon: Receipt,
//   },
//   {
//     value: EntityType.PROJECT,
//     label: 'Projects',
//     icon: FolderKanban,
//   },
//   {
//     value: EntityType.ORGANISATION,
//     label: 'Organizations',
//     icon: Building,
//   },
//   {
//     value: EntityType.USER,
//     label: 'Users',
//     icon: UserCircle,
//   },
//   {
//     value: EntityType.DOCUMENT,
//     label: 'Documents',
//     icon: FileText,
//   },
// ];
