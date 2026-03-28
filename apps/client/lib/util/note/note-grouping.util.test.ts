import { WorkspaceNote } from '@/lib/types';
import { groupNotesByDate } from './note-grouping.util';

function makeNote(overrides: Partial<WorkspaceNote> = {}): WorkspaceNote {
  return {
    id: overrides.id ?? 'note-1',
    entityId: 'entity-1',
    workspaceId: 'workspace-1',
    title: overrides.title ?? 'Test note',
    content: [],
    entityTypeKey: 'contacts',
    entityTypeIcon: 'user',
    entityTypeColour: '#000',
    ...overrides,
  };
}

describe('groupNotesByDate', () => {
  it('returns empty array for empty input', () => {
    expect(groupNotesByDate([])).toEqual([]);
  });

  it('returns empty array for null/undefined input', () => {
    expect(groupNotesByDate(null as unknown as WorkspaceNote[])).toEqual([]);
    expect(groupNotesByDate(undefined as unknown as WorkspaceNote[])).toEqual([]);
  });

  it('groups notes created today under "Today"', () => {
    const now = new Date();
    const notes = [makeNote({ id: '1', createdAt: now }), makeNote({ id: '2', createdAt: now })];
    const result = groupNotesByDate(notes);
    expect(result).toHaveLength(1);
    expect(result[0].label).toBe('Today');
    expect(result[0].notes).toHaveLength(2);
  });

  it('groups notes from yesterday under "Yesterday"', () => {
    const yesterday = new Date();
    yesterday.setDate(yesterday.getDate() - 1);
    const notes = [makeNote({ id: '1', createdAt: yesterday })];
    const result = groupNotesByDate(notes);
    expect(result).toHaveLength(1);
    expect(result[0].label).toBe('Yesterday');
  });

  it('groups notes from 3 days ago under "This week"', () => {
    const threeDaysAgo = new Date();
    threeDaysAgo.setDate(threeDaysAgo.getDate() - 3);
    const notes = [makeNote({ id: '1', createdAt: threeDaysAgo })];
    const result = groupNotesByDate(notes);
    expect(result).toHaveLength(1);
    expect(result[0].label).toBe('This week');
  });

  it('groups notes from 10 days ago under "Last week"', () => {
    const tenDaysAgo = new Date();
    tenDaysAgo.setDate(tenDaysAgo.getDate() - 10);
    const notes = [makeNote({ id: '1', createdAt: tenDaysAgo })];
    const result = groupNotesByDate(notes);
    expect(result).toHaveLength(1);
    expect(result[0].label).toBe('Last week');
  });

  it('groups notes from 30 days ago under "Older"', () => {
    const thirtyDaysAgo = new Date();
    thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);
    const notes = [makeNote({ id: '1', createdAt: thirtyDaysAgo })];
    const result = groupNotesByDate(notes);
    expect(result).toHaveLength(1);
    expect(result[0].label).toBe('Older');
  });

  it('maintains group order: Today > Yesterday > This week > Last week > Older', () => {
    const now = new Date();
    const yesterday = new Date(now); yesterday.setDate(now.getDate() - 1);
    const thisWeek = new Date(now); thisWeek.setDate(now.getDate() - 4);
    const older = new Date(now); older.setDate(now.getDate() - 30);

    const notes = [
      makeNote({ id: '1', createdAt: older }),
      makeNote({ id: '2', createdAt: now }),
      makeNote({ id: '3', createdAt: yesterday }),
      makeNote({ id: '4', createdAt: thisWeek }),
    ];

    const result = groupNotesByDate(notes);
    const labels = result.map((g) => g.label);
    expect(labels).toEqual(['Today', 'Yesterday', 'This week', 'Older']);
  });

  it('handles notes without createdAt by putting them in "Older"', () => {
    const notes = [makeNote({ id: '1', createdAt: undefined })];
    const result = groupNotesByDate(notes);
    expect(result[0].label).toBe('Older');
  });
});
