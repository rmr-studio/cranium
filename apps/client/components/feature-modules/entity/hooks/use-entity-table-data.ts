import { Entity, EntityType } from '@/lib/types/entity';
import { useMemo } from 'react';
import {
  EntityRow,
  generateColumnsFromEntityType,
  generateSearchConfigFromEntityType,
  applyColumnOrdering,
  transformEntitiesToRows,
} from '../components/tables/entity-table-utils';

export function useEntityTableData(
  entityType: EntityType,
  entities: Entity[],
  isDraftMode: boolean,
) {
  const rowData = useMemo(() => {
    const sortedEntities = [...entities].sort((a, b) => {
      const dateA = a.createdAt ? new Date(a.createdAt).getTime() : 0;
      const dateB = b.createdAt ? new Date(b.createdAt).getTime() : 0;
      return dateA - dateB;
    });

    const rows = transformEntitiesToRows(sortedEntities);

    if (isDraftMode) {
      const draftRow: EntityRow = {
        _entityId: '_draft',
        _isDraft: true,
      };
      return [...rows, draftRow];
    }

    return rows;
  }, [entities, isDraftMode]);

  const columns = useMemo(() => {
    const generatedColumns = generateColumnsFromEntityType(entityType, { enableEditing: true });
    return applyColumnOrdering(generatedColumns, entityType.columnConfiguration);
  }, [entityType.schema, entityType.relationships, entityType.columnConfiguration]);

  const searchableColumns = useMemo<string[]>(() => {
    return generateSearchConfigFromEntityType(entityType);
  }, [entityType.schema]);

  return { rowData, columns, searchableColumns };
}
