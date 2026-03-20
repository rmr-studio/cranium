import { createNoteApi } from '@/lib/api/note-api';
import { Session } from '@/lib/auth';
import { Note, CreateNoteRequest, UpdateNoteRequest } from '@/lib/types';
import { normalizeApiError } from '@/lib/util/error/error.util';
import { validateSession, validateUuid } from '@/lib/util/service/service.util';

export class NoteService {
  static async getNotesForEntity(
    session: Session | null,
    workspaceId: string,
    entityId: string,
    search?: string,
  ): Promise<Note[]> {
    validateSession(session);
    validateUuid(workspaceId);
    validateUuid(entityId);
    const api = createNoteApi(session);

    try {
      return await api.getNotesForEntity({ workspaceId, entityId, search });
    } catch (error) {
      return await normalizeApiError(error);
    }
  }

  static async createNote(
    session: Session | null,
    workspaceId: string,
    entityId: string,
    request: CreateNoteRequest,
  ): Promise<Note> {
    validateSession(session);
    validateUuid(workspaceId);
    validateUuid(entityId);
    const api = createNoteApi(session);

    try {
      return await api.createNote({ workspaceId, entityId, createNoteRequest: request });
    } catch (error) {
      return await normalizeApiError(error);
    }
  }

  static async updateNote(
    session: Session | null,
    workspaceId: string,
    noteId: string,
    request: UpdateNoteRequest,
  ): Promise<Note> {
    validateSession(session);
    validateUuid(workspaceId);
    validateUuid(noteId);
    const api = createNoteApi(session);

    try {
      return await api.updateNote({ workspaceId, noteId, updateNoteRequest: request });
    } catch (error) {
      return await normalizeApiError(error);
    }
  }

  static async deleteNote(
    session: Session | null,
    workspaceId: string,
    noteId: string,
  ): Promise<void> {
    validateSession(session);
    validateUuid(workspaceId);
    validateUuid(noteId);
    const api = createNoteApi(session);

    try {
      await api.deleteNote({ workspaceId, noteId });
    } catch (error) {
      await normalizeApiError(error);
    }
  }
}
