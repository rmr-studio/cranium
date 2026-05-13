import { Badge } from '@cranium/ui/badge';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@cranium/ui/card';
import { format, parseISO } from 'date-fns';
import { FC } from 'react';

interface TaskData {
  title?: string;
  assignee?: string;
  status?: string;
  dueDate?: string;
  [key: string]: unknown;
}

interface Props {
  task?: TaskData;
}

const statusTone: Record<string, string> = {
  IN_PROGRESS: 'bg-amber-100 text-amber-900',
  IN_REVIEW: 'bg-blue-100 text-blue-900',
  NOT_STARTED: 'bg-slate-100 text-slate-900',
  DONE: 'bg-emerald-100 text-emerald-900',
};

function formatDueDate(value?: string) {
  if (!value) return undefined;
  try {
    return format(parseISO(value), 'dd MMM yyyy');
  } catch {
    return value;
  }
}

export const TaskCard: FC<Props> = ({ task }) => {
  if (!task) return null;
  const { title, assignee, status, dueDate } = task;
  const badgeClass = status ? (statusTone[status] ?? 'bg-slate-100 text-slate-900') : undefined;
  const formattedDue = formatDueDate(dueDate);

  return (
    <Card className="flex flex-col transition-shadow duration-150 hover:shadow-md">
      <CardHeader className="flex flex-row items-start justify-between gap-2">
        <div>
          <CardTitle className="text-base font-semibold">{title ?? 'Untitled task'}</CardTitle>
          {assignee ? <CardDescription>Assigned to {assignee}</CardDescription> : null}
        </div>
        {status ? <Badge className={badgeClass}>{status.replace(/_/g, ' ')}</Badge> : null}
      </CardHeader>
      {(formattedDue || task?.description) && (
        <CardContent className="flex-1 space-y-1 text-sm text-muted-foreground">
          {formattedDue ? <div>Due {formattedDue}</div> : null}
          {'description' in task && task.description ? (
            <p className="text-foreground">{String(task.description)}</p>
          ) : null}
        </CardContent>
      )}
    </Card>
  );
};
