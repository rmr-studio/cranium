import { v4 as uuidv4 } from 'uuid';

export interface NoteEntry {
  id: string;
  title: string;
  content: object[];
  createdAt: string;
  updatedAt: string;
  createdBy?: string;
}

export function createEmptyNoteEntry(): NoteEntry {
  const now = new Date().toISOString();
  return {
    id: uuidv4(),
    title: '',
    content: [{ id: uuidv4(), type: 'paragraph', props: { textColor: 'default', backgroundColor: 'default', textAlignment: 'left' }, content: [], children: [] }],
    createdAt: now,
    updatedAt: now,
  };
}

export function extractNoteTitle(content: object[]): string {
  if (!content || content.length === 0) return '';
  const firstBlock = content[0] as any;
  if (!firstBlock?.content) return '';
  // BlockNote inline content is an array of { type: 'text', text: string, styles: {} } or { type: 'link', ... }
  const inlineContent = Array.isArray(firstBlock.content) ? firstBlock.content : [];
  return inlineContent
    .map((item: any) => {
      if (item.type === 'text') return item.text ?? '';
      if (item.type === 'link') {
        return (item.content ?? []).map((c: any) => c.text ?? '').join('');
      }
      return '';
    })
    .join('')
    .slice(0, 100);
}

export function formatNoteTimestamp(isoString: string): string {
  const date = new Date(isoString);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffMinutes = Math.floor(diffMs / 60000);
  const diffHours = Math.floor(diffMs / 3600000);
  const diffDays = Math.floor(diffMs / 86400000);

  if (diffMinutes < 1) return 'Just now';
  if (diffMinutes < 60) return `${diffMinutes}m ago`;
  if (diffHours < 24) return `${diffHours}h ago`;
  if (diffDays < 7) return `${diffDays}d ago`;

  return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
}
