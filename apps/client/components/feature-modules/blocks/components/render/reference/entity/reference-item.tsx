// 'use client';

// import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
// import { Badge } from '@riven/ui/badge';
// import { Card, CardContent, CardHeader, CardTitle } from '@riven/ui/card';
// import { Skeleton } from "@/components/ui/skeleton";
// import { AlertCircle, ChevronRight } from "lucide-react";
// import { FC } from "react";
// import type { ReferenceItem } from "@/lib/types/block";

// // Reference type may not exist in OpenAPI - using unknown
// type Reference = unknown;

// interface EntityReferenceItemProps {
//   id: string;
//   item: ReferenceItem;
//   reference: Reference | undefined;
//   isLoading: boolean;
//   error: Error | null;
//   variant: 'singleton' | 'list';
//   onRemove?: () => void;
// }

// const EXCLUDED_KEYS = new Set(['__typename', 'createdAt', 'updatedAt', 'deletedAt']);

// /**
//  * Wraps a single entity reference in a PanelWrapper.
//  * Similar to ListItem, but for entity references.
//  *
//  * Supports two variants:
//  * - singleton: Full card with all entity fields (for 1 entity)
//  * - list: Compact card with name + subtitle (for 2+ entities)
//  */
// export const EntityReferenceItem: FC<EntityReferenceItemProps> = ({
//   id,
//   item,
//   reference,
//   isLoading,
//   error,
//   variant,
// }) => {
//   // Loading state
//   if (isLoading) {
//     return (
//       <Card className="border-0 shadow-none">
//         <CardHeader>
//           <Skeleton className="h-6 w-48" />
//         </CardHeader>
//         <CardContent className="space-y-2">
//           <Skeleton className="h-4 w-full" />
//           <Skeleton className="h-4 w-3/4" />
//           {variant === 'singleton' && (
//             <>
//               <Skeleton className="h-4 w-5/6" />
//               <Skeleton className="h-4 w-2/3" />
//             </>
//           )}
//         </CardContent>
//       </Card>
//     );
//   }

//   // Error state
//   if (error) {
//     return (
//       <Alert variant="destructive">
//         <AlertCircle className="h-4 w-4" />
//         <AlertTitle>Failed to load entity</AlertTitle>
//         <AlertDescription>{error.message}</AlertDescription>
//       </Alert>
//     );
//   }

//   // Entity not found or access denied
//   if (!reference || !reference.entity || reference.warning) {
//     return (
//       <Alert variant="destructive">
//         <AlertCircle className="h-4 w-4" />
//         <AlertTitle>Entity unavailable</AlertTitle>
//         <AlertDescription>
//           {reference?.warning ||
//             'This entity could not be loaded. It may have been deleted or you may not have access.'}
//         </AlertDescription>
//       </Alert>
//     );
//   }

//   const entity = reference.entity as Record<string, unknown>;

//   // Format entity type for display
//   const formattedEntityType = reference.entityType
//     .split('_')
//     .map((word) => word.charAt(0) + word.slice(1).toLowerCase())
//     .join(' ');

//   // Get display name
//   const displayName: string =
//     item?.labelOverride ??
//     (entity.name as string) ??
//     (entity.title as string) ??
//     (entity.id as string) ??
//     'Entity';

//   // Singleton variant - full card with all fields
//   if (variant === 'singleton') {
//     return (
//       <Card className="border-0 shadow-none">
//         <CardHeader>
//           <div className="flex items-center justify-between">
//             <CardTitle className="text-lg">{displayName}</CardTitle>
//             <Badge variant="secondary">{item?.badge || formattedEntityType}</Badge>
//           </div>
//         </CardHeader>
//         <CardContent>
//           <dl className="grid grid-cols-1 gap-4 text-sm md:grid-cols-2">
//             {Object.entries(entity).map(([key, value]) => {
//               if (value === null || value === undefined || EXCLUDED_KEYS.has(key)) {
//                 return null;
//               }

//               const formattedKey = key
//                 .replace(/([A-Z])/g, ' $1')
//                 .replace(/^./, (str) => str.toUpperCase())
//                 .trim();

//               let displayValue: string;
//               if (typeof value === 'object') {
//                 displayValue = Array.isArray(value) ? `[${value.length} items]` : '[Complex data]';
//               } else if (typeof value === 'boolean') {
//                 displayValue = value ? 'Yes' : 'No';
//               } else {
//                 displayValue = String(value);
//               }

//               return (
//                 <div key={key} className="space-y-1">
//                   <dt className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">
//                     {formattedKey}
//                   </dt>
//                   <dd className="break-words text-foreground">{displayValue}</dd>
//                 </div>
//               );
//             })}
//           </dl>
//         </CardContent>
//       </Card>
//     );
//   }

//   // List variant - compact card with name + subtitle
//   const subtitle =
//     (entity.email as string) ?? (entity.description as string) ?? (entity.status as string) ?? null;

//   return (
//     <Card className="cursor-pointer border-0 shadow-none transition-colors hover:bg-accent/50">
//       <CardContent className="p-4">
//         <div className="flex items-center justify-between">
//           <div className="min-w-0 flex-1">
//             <div className="mb-1 flex items-center gap-2">
//               <h4 className="truncate text-sm font-medium">{displayName}</h4>
//               <Badge variant="secondary" className="text-xs">
//                 {item?.badge || formattedEntityType}
//               </Badge>
//             </div>
//             {subtitle && (
//               <p className="truncate text-xs text-muted-foreground">{String(subtitle)}</p>
//             )}
//           </div>
//           <ChevronRight className="ml-2 h-4 w-4 flex-shrink-0 text-muted-foreground" />
//         </div>
//       </CardContent>
//     </Card>
//   );
// };
