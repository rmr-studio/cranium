import type { User, SaveUserRequest } from '@/lib/types/user';
import { Session } from '@/lib/auth';
import { createUserApi } from '@/lib/api/user-api';
import { normalizeApiError, fromError } from '@/lib/util/error/error.util';

/**
 * Will fetch the Current authenticated user's detailed profile from the
 * active session token
 * @param {Session} session - The current active session for the user
 * @returns {User} - The user's profile
 */
export const fetchSessionUser = async (session: Session | null): Promise<User> => {
  try {
    // Validate session and access token
    if (!session?.access_token) {
      throw fromError({
        message: 'No active session found',
        status: 401,
        error: 'NO_SESSION',
      });
    }

    const api = createUserApi(session);
    return await api.getCurrentUser();
  } catch (error) {
    throw await normalizeApiError(error);
  }
};

/**
 * Update the current authenticated user's profile
 * @param {Session} session - The current active session for the user
 * @param {User} request - The updated user profile data
 * @param {Blob | null} updatedAvatar - Optional new avatar image blob
 * @returns {User} - The updated user's profile
 */
export const updateUser = async (
  session: Session | null,
  request: SaveUserRequest,
  updatedAvatar?: Blob | null,
): Promise<User> => {
  try {
    // Validate session and access token
    if (!session?.access_token) {
      throw fromError({
        message: 'No active session found',
        status: 401,
        error: 'NO_SESSION',
      });
    }

    const api = createUserApi(session);
    return await api.updateUserProfile({
      user: request,
      avatar: updatedAvatar ?? undefined,
    });
  } catch (error) {
    throw await normalizeApiError(error);
  }
};
