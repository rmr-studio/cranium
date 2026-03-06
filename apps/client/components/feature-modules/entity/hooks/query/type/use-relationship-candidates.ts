import { useWorkspace } from '@/components/feature-modules/workspace/hooks/query/use-workspace';
import {
  EntityRelationshipCandidate,
  EntityType,
} from '@/lib/types/entity';
import { useEntityTypes } from './use-entity-types';

interface UseRelationshipCandidatesReturn {
  loading: boolean;
  candidates: EntityRelationshipCandidate[];
}

/**
 * This hook will fetch all current relationships for every workspace entity type that is considered polymorphic. So is a viable candidate for a new entity type to be linked to.
 */
export function useRelationshipCandidates(type: EntityType): UseRelationshipCandidatesReturn {
  const { data: workspace } = useWorkspace();
  const { data } = useEntityTypes(workspace?.id);
  if (!data)
    return {
      loading: true,
      candidates: [],
    };

  // Filter out relationships from other entity types that target this entity type
  return {
    loading: false,
    candidates: data.flatMap((et) => {
      if (!et.relationships) return [];
      return et.relationships
        .filter((def) => {
          // Only consider relationships from OTHER entity types
          if (def.sourceEntityTypeId === type.id) return false;

          // Check if this relationship targets our entity type
          return def.allowPolymorphic ||
            def.targetRules.some((r) => r.targetEntityTypeId === type.id);
        })
        .map((rel) => ({
          name: et.name.singular,
          key: et.key,
          icon: et.icon,
          existingRelationship: rel,
        }));
    }),
  };
}
