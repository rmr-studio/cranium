import { useRelationshipCandidates } from '@/components/feature-modules/entity/hooks/query/type/use-relationship-candidates';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@riven/ui/card';
import { EntityRelationshipDefinition, EntityType } from '@/lib/types/entity';
import { FC } from 'react';
import { Candidate } from './relationship-candidate';

interface Props {
  type: EntityType;
  onCreate: (relationship: EntityRelationshipDefinition) => void;
}
/**
 * This component shows all available existing relationships that can be linked to the current entity type.
 * This would involve:
 * - All polymorphic relationships that can be joined
 * - Other entity relationships linking to the same source (Allowing for relationship consolidation/merging)
 */
export const RelationshipLink: FC<Props> = ({ type, onCreate }) => {
  const { candidates, loading } = useRelationshipCandidates(type);

  return (
    !loading &&
    candidates.length > 0 && (
      <Card className="min-w-xs bg-transparent">
        <CardHeader>
          <CardTitle>Available Relationships</CardTitle>
          <CardDescription>
            We have found {candidates.length} existing relationship
            {candidates.length !== 1 ? 's' : ''} that can be linked to this entity type.
          </CardDescription>
        </CardHeader>
        <CardContent>
          {candidates.map((candidate) => (
            <Candidate
              key={`${candidate.existingRelationship.id}-${candidate.key}`}
              name={candidate.name}
              relationship={candidate.existingRelationship}
              icon={candidate.icon}
              onSelect={onCreate}
            />
          ))}
        </CardContent>
      </Card>
    )
  );
};
