import type {
  GetWorkspaceInvitesPathParams,
  InviteToWorkspacePathParams,
  RevokeInvitePathParams,
  SaveWorkspaceRequest,
  Workspace,
} from '@/lib/types/workspace';
import { normalizeApiError } from '@/lib/util/error/error.util';
import { validateSession, validateUuid } from '@/lib/util/service/service.util';
import { undefinedIfNull } from '@/lib/util/utils';
import { Session } from '@/lib/auth';
import { createWorkspaceApi } from '@/lib/api/workspace-api';

export class WorkspaceService {
  static async saveWorkspace(
    session: Session | null,
    request: SaveWorkspaceRequest,
    uploadedAvatar: Blob | null = null,
  ): Promise<Workspace> {
    try {
      validateSession(session);

      const workspaceApi = createWorkspaceApi(session);
      return await workspaceApi.saveWorkspace({
        workspace: request,
        file: undefinedIfNull(uploadedAvatar),
      });
    } catch (error) {
      throw await normalizeApiError(error);
    }
  }

  static async inviteToWorkspace(session: Session | null, params: InviteToWorkspacePathParams) {
    const { workspaceId, email, role } = params;
    try {
      validateSession(session);

      const workspaceApi = createWorkspaceApi(session);
      return await workspaceApi.inviteToWorkspace({
        workspaceId,
        email,
        role,
      });
    } catch (error) {
      throw await normalizeApiError(error);
    }
  }

  static async getWorkspaceInvites(session: Session | null, params: GetWorkspaceInvitesPathParams) {
    const { workspaceId } = params;
    try {
      validateSession(session);

      const workspaceApi = createWorkspaceApi(session);
      return await workspaceApi.getWorkspaceInvites({ workspaceId });
    } catch (error) {
      throw await normalizeApiError(error);
    }
  }

  static async revokeInvite(
    session: Session | null,
    params: RevokeInvitePathParams,
  ): Promise<void> {
    const { workspaceId, id } = params;
    try {
      validateSession(session);
      validateUuid(workspaceId);
      validateUuid(id);

      const workspaceApi = createWorkspaceApi(session);
      await workspaceApi.revokeInvite({ workspaceId, id });
    } catch (error) {
      throw await normalizeApiError(error);
    }
  }

  static async deleteWorkspace(session: Session | null, workspaceId: string): Promise<void> {
    try {
      validateSession(session);
      validateUuid(workspaceId);

      const workspaceApi = createWorkspaceApi(session);
      await workspaceApi.deleteWorkspace({ workspaceId });
    } catch (error) {
      throw await normalizeApiError(error);
    }
  }

  static async getWorkspace(session: Session | null, workspaceId: string): Promise<Workspace> {
    try {
      validateSession(session);
      validateUuid(workspaceId);

      const workspaceApi = createWorkspaceApi(session);
      return await workspaceApi.getWorkspace({ workspaceId });
    } catch (error) {
      throw await normalizeApiError(error);
    }
  }
}
