import { getInverseCardinality } from '@/components/feature-modules/entity/util/relationship.util';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@riven/ui/card';
import { IconCell } from '@/components/ui/icon/icon-cell';
import { type Icon } from '@/lib/types/common';
import { EntityRelationshipCardinality, EntityRelationshipDefinition } from '@/lib/types/entity';
import { Link } from 'lucide-react';
import { FC } from 'react';

interface Props {
  name: string;
  icon: Icon;
  onSelect: (relationship: EntityRelationshipDefinition) => void;
  relationship: EntityRelationshipDefinition;
}

const getCardinalityDescription = (
  cardinality: EntityRelationshipCardinality,
  entityName: string,
): string => {
  const inverse = getInverseCardinality(cardinality);

  switch (inverse) {
    case EntityRelationshipCardinality.OneToOne:
    case EntityRelationshipCardinality.OneToMany:
      return `Stores one ${entityName.toLowerCase()} reference`;
    case EntityRelationshipCardinality.ManyToOne:
    case EntityRelationshipCardinality.ManyToMany:
      return `Stores unlimited ${entityName.toLowerCase()} references`;
  }
};

export const Candidate: FC<Props> = ({ name, icon, relationship, onSelect }) => {
  const { icon: iconType, colour } = icon;

  return (
    <Card
      className="cursor-pointer gap-0 p-2 hover:bg-accent/70"
      onClick={() => {
        onSelect(relationship);
      }}
    >
      <CardHeader className="m-0 p-0">
        <div className="flex items-center">
          <IconCell readonly iconType={iconType} colour={colour} className="mr-3 size-4" />
          <div className="w-auto grow">
            <CardTitle className="text-sm">{name}</CardTitle>
            <CardDescription className="text-xs">
              {getCardinalityDescription(relationship.cardinality, name)}
            </CardDescription>
          </div>
        </div>
      </CardHeader>
      <CardContent className="flex items-center justify-end p-0 pr-1 text-xs font-semibold text-primary/80">
        <Link className="mr-2 size-3" />
        Link to this Entity
      </CardContent>
    </Card>
  );
};
