import { fetchSessionUser, updateUser } from '@/components/feature-modules/user/service/user.service';
import { createUserApi } from '@/lib/api/user-api';
import type { Session } from '@/lib/auth';
import type { User, SaveUserRequest } from '@/lib/types/user';

jest.mock('@/lib/api/user-api');

const mockCreateUserApi = createUserApi as jest.MockedFunction<typeof createUserApi>;

const fakeSession: Session = {
  access_token: 'test-token',
  expires_at: Date.now() + 60_000,
  user: { id: 'user-1', metadata: {} },
};

const fakeUser: User = {
  id: 'user-1',
  email: 'test@example.com',
  name: 'Test User',
  memberships: [],
};

const fakeRequest: SaveUserRequest = {
  name: 'Test User',
  email: 'test@example.com',
  removeAvatar: false,
};

describe('updateUser', () => {
  const mockUpdateUserProfile = jest.fn();

  beforeEach(() => {
    jest.clearAllMocks();
    mockCreateUserApi.mockReturnValue({
      updateUserProfile: mockUpdateUserProfile,
    } as unknown as ReturnType<typeof createUserApi>);
    mockUpdateUserProfile.mockResolvedValue(fakeUser);
  });

  it('passes avatar blob to the API when provided', async () => {
    const avatarBlob = new Blob(['avatar'], { type: 'image/png' });

    await updateUser(fakeSession, fakeRequest, avatarBlob);

    expect(mockUpdateUserProfile).toHaveBeenCalledWith({
      user: fakeRequest,
      avatar: avatarBlob,
    });
  });

  it('passes undefined avatar when null is provided', async () => {
    await updateUser(fakeSession, fakeRequest, null);

    expect(mockUpdateUserProfile).toHaveBeenCalledWith({
      user: fakeRequest,
      avatar: undefined,
    });
  });

  it('passes undefined avatar when omitted', async () => {
    await updateUser(fakeSession, fakeRequest);

    expect(mockUpdateUserProfile).toHaveBeenCalledWith({
      user: fakeRequest,
      avatar: undefined,
    });
  });

  it('rejects when session is null', async () => {
    await expect(updateUser(null, fakeRequest)).rejects.toMatchObject({
      status: 401,
      error: 'NO_SESSION',
    });
  });
});

describe('fetchSessionUser', () => {
  const mockGetCurrentUser = jest.fn();

  beforeEach(() => {
    jest.clearAllMocks();
    mockCreateUserApi.mockReturnValue({
      getCurrentUser: mockGetCurrentUser,
    } as unknown as ReturnType<typeof createUserApi>);
    mockGetCurrentUser.mockResolvedValue(fakeUser);
  });

  it('returns the current user from the API', async () => {
    const result = await fetchSessionUser(fakeSession);

    expect(result).toEqual(fakeUser);
    expect(mockGetCurrentUser).toHaveBeenCalledTimes(1);
  });

  it('rejects when session is null', async () => {
    await expect(fetchSessionUser(null)).rejects.toMatchObject({
      status: 401,
      error: 'NO_SESSION',
    });
  });
});
