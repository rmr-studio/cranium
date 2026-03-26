import { Note } from '@/lib/types';

/**
 * Re-export the generated Note type for convenience.
 * The generated Note has: id, entityId, workspaceId, title, content, createdAt?, updatedAt?, createdBy?, updatedBy?
 */
export type { Note };

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

export function formatNoteTimestamp(date: Date | string | undefined): string {
  if (!date) return '';
  const d = typeof date === 'string' ? new Date(date) : date;
  const now = new Date();
  const diffMs = now.getTime() - d.getTime();
  const diffMinutes = Math.floor(diffMs / 60000);
  const diffHours = Math.floor(diffMs / 3600000);
  const diffDays = Math.floor(diffMs / 86400000);

  if (diffMinutes < 1) return 'Just now';
  if (diffMinutes < 60) return `${diffMinutes}m ago`;
  if (diffHours < 24) return `${diffHours}h ago`;
  if (diffDays < 7) return `${diffDays}d ago`;

  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
}
