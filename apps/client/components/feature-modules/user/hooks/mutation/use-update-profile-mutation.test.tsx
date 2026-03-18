import { renderHook, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useUpdateProfileMutation } from './use-update-profile-mutation';
import { useAuth } from '@/components/provider/auth-context';
import { updateUser } from '@/components/feature-modules/user/service/user.service';
import { toast } from 'sonner';
import { ReactNode } from 'react';
import type { User, SaveUserRequest } from '@/lib/types/user';

jest.mock('@/components/provider/auth-context', () => ({
  useAuth: jest.fn(),
}));

jest.mock('@/components/feature-modules/user/service/user.service', () => ({
  updateUser: jest.fn(),
}));

jest.mock('sonner', () => ({
  toast: {
    loading: jest.fn(() => 'toast-id'),
    success: jest.fn(),
    error: jest.fn(),
    dismiss: jest.fn(),
  },
}));

const mockUseAuth = useAuth as jest.MockedFunction<typeof useAuth>;
const mockUpdateUser = updateUser as jest.MockedFunction<typeof updateUser>;

const fakeUser: SaveUserRequest = {
  name: 'Test User',
  email: 'test@example.com',
  removeAvatar: false,
};

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { mutations: { retry: false } },
  });
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
  Wrapper.displayName = 'QueryWrapper';
  return Wrapper;
}

describe('useUpdateProfileMutation', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockUseAuth.mockReturnValue({
      session: { access_token: 'test-token' },
      user: { id: 'user-1' },
    } as unknown as ReturnType<typeof useAuth>);
  });

  it('calls updateUser with user data and avatar blob', async () => {
    const updatedUser = { ...fakeUser, name: 'Updated' };
    mockUpdateUser.mockResolvedValue(updatedUser);
    const avatarBlob = new Blob(['img'], { type: 'image/png' });

    const { result } = renderHook(() => useUpdateProfileMutation(), {
      wrapper: createWrapper(),
    });

    await act(async () => {
      await result.current.mutateAsync({ user: fakeUser, avatar: avatarBlob });
    });

    expect(mockUpdateUser).toHaveBeenCalledWith(
      { access_token: 'test-token' },
      fakeUser,
      avatarBlob,
    );
  });

  it('shows loading toast on mutate and success toast on completion', async () => {
    mockUpdateUser.mockResolvedValue(fakeUser);

    const { result } = renderHook(() => useUpdateProfileMutation(), {
      wrapper: createWrapper(),
    });

    await act(async () => {
      await result.current.mutateAsync({ user: fakeUser });
    });

    expect(toast.loading).toHaveBeenCalledWith('Updating profile...');
    expect(toast.dismiss).toHaveBeenCalledWith('toast-id');
    expect(toast.success).toHaveBeenCalledWith('Profile updated successfully');
  });

  it('shows error toast on failure', async () => {
    mockUpdateUser.mockRejectedValue(new Error('Network error'));

    const { result } = renderHook(() => useUpdateProfileMutation(), {
      wrapper: createWrapper(),
    });

    await act(async () => {
      try {
        await result.current.mutateAsync({ user: fakeUser });
      } catch {
        // expected
      }
    });

    expect(toast.dismiss).toHaveBeenCalledWith('toast-id');
    expect(toast.error).toHaveBeenCalledWith('Failed to update profile: Network error');
  });

  it('invokes options callbacks when provided', async () => {
    mockUpdateUser.mockResolvedValue(fakeUser);
    const onSuccess = jest.fn();
    const onMutate = jest.fn();

    const { result } = renderHook(
      () => useUpdateProfileMutation({ onSuccess, onMutate }),
      { wrapper: createWrapper() },
    );

    await act(async () => {
      await result.current.mutateAsync({ user: fakeUser });
    });

    expect(onMutate).toHaveBeenCalled();
    expect(onSuccess).toHaveBeenCalled();
  });
});
