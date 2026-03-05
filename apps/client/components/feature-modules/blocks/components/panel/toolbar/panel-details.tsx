import { Badge } from '@riven/ui/badge';
import { Input } from '@riven/ui/input';
import { FC } from 'react';

interface PanelDetailsProps {
  draftTitle: string;
  onDraftTitleChange: (value: string) => void;
  onTitleBlur: () => void;
  titlePlaceholder: string;
  description?: string;
  badge?: string;
}

const PanelDetails: FC<PanelDetailsProps> = ({
  draftTitle,
  onDraftTitleChange,
  onTitleBlur,
  titlePlaceholder,
  description,
  badge,
}) => {
  return (
    <div className="space-y-3">
      <div className="space-y-1">
        <label className="text-xs font-medium text-muted-foreground">Title</label>
        <Input
          aria-label="Edit title"
          value={draftTitle}
          placeholder={titlePlaceholder}
          onChange={(event) => onDraftTitleChange(event.target.value)}
          onBlur={onTitleBlur}
        />
      </div>
      {description ? (
        <div className="space-y-1">
          <span className="text-xs font-medium text-muted-foreground">Description</span>
          <p className="rounded-md border bg-muted/30 p-2 text-sm text-muted-foreground">
            {description}
          </p>
        </div>
      ) : null}
      {badge ? <Badge variant="secondary">{badge}</Badge> : null}
    </div>
  );
};

export default PanelDetails;
