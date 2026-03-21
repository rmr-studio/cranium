/* eslint-disable @typescript-eslint/no-explicit-any */
import { NoteService } from './note.service';

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

const mockApi = {
  getWorkspaceNotes: jest.fn(),
  getWorkspaceNote: jest.fn(),
};

beforeEach(() => {
  jest.clearAllMocks();
  (createNoteApi as jest.Mock).mockReturnValue(mockApi);
});

describe('NoteService.getWorkspaceNotes', () => {
  const session = { access_token: 'token' } as any;
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
  const session = { access_token: 'token' } as any;
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
