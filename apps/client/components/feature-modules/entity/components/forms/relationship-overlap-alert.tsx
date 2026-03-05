import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Button } from '@riven/ui/button';
import { AlertTriangle } from 'lucide-react';
import { FC } from 'react';
import { RelationshipOverlap } from '../../hooks/use-relationship-overlap-detection';

interface RelationshipOverlapAlertProps {
  overlaps: RelationshipOverlap[];
  sourceEntityKey: string;
  onDismiss: (overlapIndex: number) => void;
  onNavigateToTarget: (targetEntityKey: string, relationshipKey: string) => void;
}

export const RelationshipOverlapAlert: FC<RelationshipOverlapAlertProps> = ({
  overlaps,
  sourceEntityKey,
  onDismiss,
  onNavigateToTarget,
}) => {
  if (overlaps.length === 0) return null;

  return (
    <div className="space-y-3">
      {overlaps.map((overlap, index) => (
        <Alert
          key={`${overlap.targetEntityKey}-${overlap.existingRelationship.id}`}
          className="border-yellow-500/50 bg-yellow-50 dark:bg-yellow-950/20"
        >
          <AlertTriangle className="h-4 w-4 text-yellow-600 dark:text-yellow-500" />
          <AlertTitle className="text-sm font-semibold text-yellow-900 dark:text-yellow-100">
            Potential Relationship Overlap Detected
          </AlertTitle>
          <AlertDescription className="mt-2 text-sm text-yellow-800 dark:text-yellow-200">
            <p className="mb-3">{overlap.description}</p>

            <div className="flex flex-col gap-2 sm:flex-row">
              <Button
                type="button"
                size="sm"
                variant="outline"
                className="border-yellow-600/30 hover:bg-yellow-100 dark:hover:bg-yellow-900/30"
                onClick={() => {
                  const { relationshipKey } = overlap.suggestedAction.details;
                  if (relationshipKey) {
                    onNavigateToTarget(overlap.targetEntityKey, relationshipKey);
                  }
                }}
              >
                View & Edit {overlap.targetEntityName}
              </Button>
              <Button
                type="button"
                size="sm"
                variant="ghost"
                className="hover:bg-yellow-100 dark:hover:bg-yellow-900/30"
                onClick={() => onDismiss(index)}
              >
                Continue Anyway
              </Button>
            </div>
          </AlertDescription>
        </Alert>
      ))}
    </div>
  );
};
