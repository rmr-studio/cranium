import { workspaceNoteKeys } from './use-workspace-notes';

describe('workspaceNoteKeys', () => {
  const workspaceId = 'ws-123';

  it('generates all key', () => {
    expect(workspaceNoteKeys.all(workspaceId)).toEqual(['workspace-notes', 'ws-123']);
  });

  it('generates list key without search', () => {
    expect(workspaceNoteKeys.list(workspaceId)).toEqual([
      'workspace-notes',
      'ws-123',
      'list',
      '',
    ]);
  });

  it('generates list key with search', () => {
    expect(workspaceNoteKeys.list(workspaceId, 'hello')).toEqual([
      'workspace-notes',
      'ws-123',
      'list',
      'hello',
    ]);
  });

  it('generates detail key', () => {
    expect(workspaceNoteKeys.detail(workspaceId, 'note-456')).toEqual([
      'workspace-notes',
      'ws-123',
      'detail',
      'note-456',
    ]);
  });

  it('list key with undefined search defaults to empty string', () => {
    expect(workspaceNoteKeys.list(workspaceId, undefined)).toEqual([
      'workspace-notes',
      'ws-123',
      'list',
      '',
    ]);
  });
});
