import { NoteService } from '@/components/feature-modules/entity/service/note.service';
import { Session } from '@/lib/auth';

jest.mock('@/lib/api/note-api', () => ({
  createNoteApi: jest.fn(),
}));

jest.mock('@/lib/util/service/service.util', () => ({
  validateSession: jest.fn(),
  validateUuid: jest.fn(),
}));

jest.mock('@/lib/util/error/error.util', () => ({
  normalizeApiError: jest.fn(),
}));

import { createNoteApi } from '@/lib/api/note-api';
import { validateSession, validateUuid } from '@/lib/util/service/service.util';
import { normalizeApiError } from '@/lib/util/error/error.util';

const mockSession: Session = {
  access_token: 'token',
  expires_at: 9999999999,
  user: { id: 'user-1', email: 'test@test.com', metadata: {} },
};

const mockApi = {
  getWorkspaceNotes: jest.fn(),
  getWorkspaceNote: jest.fn(),
  getNotesForEntity: jest.fn(),
  createNote: jest.fn(),
  updateNote: jest.fn(),
  deleteNote: jest.fn(),
};

beforeEach(() => {
  jest.clearAllMocks();
  (createNoteApi as jest.Mock).mockReturnValue(mockApi);
});

describe('NoteService.getWorkspaceNotes', () => {
  const session = mockSession;
  const workspaceId = '123e4567-e89b-12d3-a456-426614174000';

  it('validates session and workspace ID', async () => {
    mockApi.getWorkspaceNotes.mockResolvedValue({ items: [], totalCount: 0 });
    await NoteService.getWorkspaceNotes(session, workspaceId);
    expect(validateSession).toHaveBeenCalledWith(session);
    expect(validateUuid).toHaveBeenCalledWith(workspaceId);
  });

  it('passes search, cursor, and limit to API', async () => {
    mockApi.getWorkspaceNotes.mockResolvedValue({ items: [], totalCount: 0 });
    await NoteService.getWorkspaceNotes(session, workspaceId, 'test', 'cursor-1', 20);
    expect(mockApi.getWorkspaceNotes).toHaveBeenCalledWith({
      workspaceId,
      search: 'test',
      cursor: 'cursor-1',
      limit: 20,
    });
  });

  it('passes undefined optional params when not provided', async () => {
    mockApi.getWorkspaceNotes.mockResolvedValue({ items: [], totalCount: 0 });
    await NoteService.getWorkspaceNotes(session, workspaceId);
    expect(mockApi.getWorkspaceNotes).toHaveBeenCalledWith({
      workspaceId,
      search: undefined,
      cursor: undefined,
      limit: undefined,
    });
  });

  it('calls normalizeApiError on failure', async () => {
    const error = new Error('API error');
    mockApi.getWorkspaceNotes.mockRejectedValue(error);
    (normalizeApiError as jest.Mock).mockRejectedValue(error);
    await expect(NoteService.getWorkspaceNotes(session, workspaceId)).rejects.toThrow(
      'API error',
    );
    expect(normalizeApiError).toHaveBeenCalledWith(error);
  });
});

describe('NoteService.getWorkspaceNote', () => {
  const session = mockSession;
  const workspaceId = '123e4567-e89b-12d3-a456-426614174000';
  const noteId = '223e4567-e89b-12d3-a456-426614174001';

  it('validates session, workspace ID, and note ID', async () => {
    mockApi.getWorkspaceNote.mockResolvedValue({ id: noteId });
    await NoteService.getWorkspaceNote(session, workspaceId, noteId);
    expect(validateSession).toHaveBeenCalledWith(session);
    expect(validateUuid).toHaveBeenCalledWith(workspaceId);
    expect(validateUuid).toHaveBeenCalledWith(noteId);
  });

  it('passes workspaceId and noteId to API', async () => {
    mockApi.getWorkspaceNote.mockResolvedValue({ id: noteId });
    await NoteService.getWorkspaceNote(session, workspaceId, noteId);
    expect(mockApi.getWorkspaceNote).toHaveBeenCalledWith({ workspaceId, noteId });
  });

  it('calls normalizeApiError on failure', async () => {
    const error = new Error('Not found');
    mockApi.getWorkspaceNote.mockRejectedValue(error);
    (normalizeApiError as jest.Mock).mockRejectedValue(error);
    await expect(
      NoteService.getWorkspaceNote(session, workspaceId, noteId),
    ).rejects.toThrow('Not found');
    expect(normalizeApiError).toHaveBeenCalledWith(error);
  });
});

describe('NoteService.getNotesForEntity', () => {
  const session = mockSession;
  const workspaceId = '123e4567-e89b-12d3-a456-426614174000';
  const entityId = '323e4567-e89b-12d3-a456-426614174002';

  it('validates session, workspace ID, and entity ID', async () => {
    mockApi.getNotesForEntity.mockResolvedValue([]);
    await NoteService.getNotesForEntity(session, workspaceId, entityId);
    expect(validateSession).toHaveBeenCalledWith(session);
    expect(validateUuid).toHaveBeenCalledWith(workspaceId);
    expect(validateUuid).toHaveBeenCalledWith(entityId);
  });

  it('passes search param to API', async () => {
    mockApi.getNotesForEntity.mockResolvedValue([]);
    await NoteService.getNotesForEntity(session, workspaceId, entityId, 'test');
    expect(mockApi.getNotesForEntity).toHaveBeenCalledWith({
      workspaceId,
      entityId,
      search: 'test',
    });
  });

  it('calls normalizeApiError on failure', async () => {
    const error = new Error('API error');
    mockApi.getNotesForEntity.mockRejectedValue(error);
    (normalizeApiError as jest.Mock).mockRejectedValue(error);
    await expect(
      NoteService.getNotesForEntity(session, workspaceId, entityId),
    ).rejects.toThrow('API error');
    expect(normalizeApiError).toHaveBeenCalledWith(error);
  });
});

describe('NoteService.createNote', () => {
  const session = mockSession;
  const workspaceId = '123e4567-e89b-12d3-a456-426614174000';
  const entityId = '323e4567-e89b-12d3-a456-426614174002';

  it('validates session, workspace ID, and entity ID', async () => {
    mockApi.createNote.mockResolvedValue({ id: 'new-note' });
    await NoteService.createNote(session, workspaceId, entityId, { content: [] });
    expect(validateSession).toHaveBeenCalledWith(session);
    expect(validateUuid).toHaveBeenCalledWith(workspaceId);
    expect(validateUuid).toHaveBeenCalledWith(entityId);
  });

  it('passes request body to API', async () => {
    const request = { content: [{ type: 'paragraph' }], title: 'Test' };
    mockApi.createNote.mockResolvedValue({ id: 'new-note' });
    await NoteService.createNote(session, workspaceId, entityId, request);
    expect(mockApi.createNote).toHaveBeenCalledWith({
      workspaceId,
      entityId,
      createNoteRequest: request,
    });
  });

  it('calls normalizeApiError on failure', async () => {
    const error = new Error('Create failed');
    mockApi.createNote.mockRejectedValue(error);
    (normalizeApiError as jest.Mock).mockRejectedValue(error);
    await expect(
      NoteService.createNote(session, workspaceId, entityId, { content: [] }),
    ).rejects.toThrow('Create failed');
    expect(normalizeApiError).toHaveBeenCalledWith(error);
  });
});

describe('NoteService.updateNote', () => {
  const session = mockSession;
  const workspaceId = '123e4567-e89b-12d3-a456-426614174000';
  const noteId = '223e4567-e89b-12d3-a456-426614174001';

  it('validates session, workspace ID, and note ID', async () => {
    mockApi.updateNote.mockResolvedValue({ id: noteId });
    await NoteService.updateNote(session, workspaceId, noteId, { content: [] });
    expect(validateSession).toHaveBeenCalledWith(session);
    expect(validateUuid).toHaveBeenCalledWith(workspaceId);
    expect(validateUuid).toHaveBeenCalledWith(noteId);
  });

  it('passes request body to API', async () => {
    const request = { content: [{ type: 'paragraph' }], title: 'Updated' };
    mockApi.updateNote.mockResolvedValue({ id: noteId });
    await NoteService.updateNote(session, workspaceId, noteId, request);
    expect(mockApi.updateNote).toHaveBeenCalledWith({
      workspaceId,
      noteId,
      updateNoteRequest: request,
    });
  });

  it('calls normalizeApiError on failure', async () => {
    const error = new Error('Update failed');
    mockApi.updateNote.mockRejectedValue(error);
    (normalizeApiError as jest.Mock).mockRejectedValue(error);
    await expect(
      NoteService.updateNote(session, workspaceId, noteId, { content: [] }),
    ).rejects.toThrow('Update failed');
    expect(normalizeApiError).toHaveBeenCalledWith(error);
  });
});

describe('NoteService.deleteNote', () => {
  const session = mockSession;
  const workspaceId = '123e4567-e89b-12d3-a456-426614174000';
  const noteId = '223e4567-e89b-12d3-a456-426614174001';

  it('validates session, workspace ID, and note ID', async () => {
    mockApi.deleteNote.mockResolvedValue(undefined);
    await NoteService.deleteNote(session, workspaceId, noteId);
    expect(validateSession).toHaveBeenCalledWith(session);
    expect(validateUuid).toHaveBeenCalledWith(workspaceId);
    expect(validateUuid).toHaveBeenCalledWith(noteId);
  });

  it('calls deleteNote on API', async () => {
    mockApi.deleteNote.mockResolvedValue(undefined);
    await NoteService.deleteNote(session, workspaceId, noteId);
    expect(mockApi.deleteNote).toHaveBeenCalledWith({ workspaceId, noteId });
  });

  it('calls normalizeApiError on failure', async () => {
    const error = new Error('Delete failed');
    mockApi.deleteNote.mockRejectedValue(error);
    (normalizeApiError as jest.Mock).mockRejectedValue(error);
    await expect(
      NoteService.deleteNote(session, workspaceId, noteId),
    ).rejects.toThrow('Delete failed');
    expect(normalizeApiError).toHaveBeenCalledWith(error);
  });
});
