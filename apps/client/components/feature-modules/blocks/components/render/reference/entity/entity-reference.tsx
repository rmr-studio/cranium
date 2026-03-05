import { useBlockHydration } from "@/components/feature-modules/blocks/hooks/use-block-hydration";
import type {
    EntityReferenceMetadata,
    ReferenceNode,
} from "@/lib/types/block";
import { Button } from '@riven/ui/button';
import type { EntityType } from "@/lib/types/entity";
import { RefreshCw } from "lucide-react";
import { FC, useMemo } from "react";
import { useTrackedEnvironment } from "../../../../context/tracked-environment-provider";
import { UseEntityReferenceToolbar } from "../../../../hooks/use-entity-references";
import { getTitle } from "../../../../util/block/block.util";
import { PanelWrapper } from "../../../panel/panel-wrapper";
import { EntityReferenceEmptyState } from "./empty-state";
import { EntityReferenceList } from "./reference-list";
import { EntityView } from "./reference-view";

interface Props {
  node: ReferenceNode;
  payload: EntityReferenceMetadata;
}

export const EntityReference: FC<Props> = ({ node, payload }) => {
  const { removeTrackedBlock } = useTrackedEnvironment();

  const { items, listType } = payload;
  const { block } = node;
  const { id, type } = block;

  const itemCount = items.length;
  const { error, refetch, isRefetching } = useBlockHydration(id);
  // Get entity reference toolbar actions and modal
  // Use listType if set, otherwise default to CLIENT
  // const { customActions, modal } = UseEntityReferenceToolbar({
  //   blockId: id,
  //   entityType: listType
  //   currentItems: items || [],
  //   multiSelect: true,
  //   readonly: payload.readonly,
  // });

  // Quick actions for the block
  const quickActions = useMemo(() => {
    if (!payload.deletable) return [];
    return [
      {
        id: 'delete',
        label: 'Delete block',
        shortcut: '⌘⌫',
        onSelect: () => removeTrackedBlock(id),
      },
    ];
  }, [id, removeTrackedBlock, payload.deletable]);

  // Retry button for error state
  const retryButton = error ? (
    <Button
      variant="outline"
      size="sm"
      onClick={() => refetch()}
      disabled={isRefetching}
      className="absolute top-2 right-2"
    >
      <RefreshCw className={`mr-2 size-3.5 ${isRefetching ? 'animate-spin' : ''}`} />
      Retry
    </Button>
  ) : null;

  const title = getTitle(node);

  // Determine content based on item count
  let content: React.ReactNode;

  // Case 1: Empty state (0 items)
  if (itemCount === 0) {
    content = <EntityReferenceEmptyState />;
  }
  // Case 2: Singleton rendering (1 item)
  else if (itemCount === 1) {
    const item = items[0];
    content = <EntityView blockId={id} item={item} />;
  }
  // Case 3: List rendering (2+ items)
  else {
    content = <EntityReferenceList blockId={id} items={items} />;
  }

  return (
    <>
      <PanelWrapper
        id={id}
        title={title}
        description={type.description}
        quickActions={quickActions}
        allowInsert={false}
        allowEdit={false}
        allowDelete={payload.deletable}
        onDelete={() => removeTrackedBlock(id)}
        customActions={customActions}
        customControls={retryButton}
      >
        {content}
      </PanelWrapper>
      {modal}
    </>
  );
};
