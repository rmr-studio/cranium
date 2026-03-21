import { WorkspaceNote } from '@/lib/types';

export interface NoteGroup {
  label: string;
  notes: WorkspaceNote[];
}

function startOfDay(date: Date): Date {
  const d = new Date(date);
  d.setHours(0, 0, 0, 0);
  return d;
}

function getDateLabel(date: Date, now: Date): string {
  const today = startOfDay(now);
  const noteDay = startOfDay(date);
  const diffDays = Math.floor((today.getTime() - noteDay.getTime()) / 86400000);

  if (diffDays === 0) return 'Today';
  if (diffDays === 1) return 'Yesterday';
  if (diffDays < 7) return 'This week';
  if (diffDays < 14) return 'Last week';
  return 'Older';
}

export function groupNotesByDate(notes: WorkspaceNote[]): NoteGroup[] {
  if (!notes || notes.length === 0) return [];

  const now = new Date();
  const groupMap = new Map<string, WorkspaceNote[]>();
  const groupOrder = ['Today', 'Yesterday', 'This week', 'Last week', 'Older'];

  for (const note of notes) {
    const date = note.createdAt ? new Date(note.createdAt) : new Date(0);
    const label = getDateLabel(date, now);
    const group = groupMap.get(label);
    if (group) {
      group.push(note);
    } else {
      groupMap.set(label, [note]);
    }
  }

  return groupOrder
    .filter((label) => groupMap.has(label))
    .map((label) => ({ label, notes: groupMap.get(label)! }));
}
