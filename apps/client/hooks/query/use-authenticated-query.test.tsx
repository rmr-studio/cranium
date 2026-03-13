import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useAuthenticatedQuery } from './use-authenticated-query';
import { useAuth } from '@/components/provider/auth-context';
import { ReactNode } from 'react';

jest.mock('@/components/provider/auth-context', () => ({
  useAuth: jest.fn(),
}));

const mockUseAuth = useAuth as jest.MockedFunction<typeof useAuth>;

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}

describe('useAuthenticatedQuery', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('disables query when session is null', () => {
    mockUseAuth.mockReturnValue({
      session: null,
      loading: false,
    } as any);

    const { result } = renderHook(
      () =>
        useAuthenticatedQuery({
          queryKey: ['test'],
          queryFn: async () => 'data',
        }),
      { wrapper: createWrapper() },
    );

    expect(result.current.fetchStatus).toBe('idle');
    expect(result.current.isLoadingAuth).toBe(false);
  });

  it('disables query when auth is loading', () => {
    mockUseAuth.mockReturnValue({
      session: { access_token: 'token' },
      loading: true,
    } as any);

    const { result } = renderHook(
      () =>
        useAuthenticatedQuery({
          queryKey: ['test'],
          queryFn: async () => 'data',
        }),
      { wrapper: createWrapper() },
    );

    expect(result.current.fetchStatus).toBe('idle');
    expect(result.current.isLoadingAuth).toBe(true);
  });

  it('enables query when session exists and not loading', async () => {
    mockUseAuth.mockReturnValue({
      session: { access_token: 'token' },
      loading: false,
    } as any);

    const queryFn = jest.fn().mockResolvedValue('result');

    const { result } = renderHook(
      () =>
        useAuthenticatedQuery({
          queryKey: ['test'],
          queryFn,
        }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toBe('result');
    expect(result.current.isLoadingAuth).toBe(false);
  });

  it('respects additional enabled condition', () => {
    mockUseAuth.mockReturnValue({
      session: { access_token: 'token' },
      loading: false,
    } as any);

    const queryFn = jest.fn().mockResolvedValue('result');

    const { result } = renderHook(
      () =>
        useAuthenticatedQuery({
          queryKey: ['test'],
          queryFn,
          enabled: false,
        }),
      { wrapper: createWrapper() },
    );

    expect(result.current.fetchStatus).toBe('idle');
    expect(queryFn).not.toHaveBeenCalled();
  });
});
