import { renderHook, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useSaveDefinitionMutation } from '@/components/feature-modules/knowledge/hooks/mutation/use-save-definition-mutation';
import { useAuth } from '@/components/provider/auth-context';
import { DefinitionService } from '@/components/feature-modules/knowledge/service/definition.service';
import { definitionKeys } from '@/components/feature-modules/knowledge/hooks/query/definition-query-keys';
import { toast } from 'sonner';
import { ReactNode } from 'react';
import type { WorkspaceBusinessDefinition } from '@/lib/types/workspace';
import { DefinitionCategory, DefinitionStatus, DefinitionSource } from '@/lib/types/workspace';

jest.mock('@/components/provider/auth-context', () => ({
  useAuth: jest.fn(),
}));

jest.mock('@/components/feature-modules/knowledge/service/definition.service', () => ({
  DefinitionService: {
    createDefinition: jest.fn(),
    updateDefinition: jest.fn(),
  },
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
const mockCreate = DefinitionService.createDefinition as jest.MockedFunction<typeof DefinitionService.createDefinition>;
const mockUpdate = DefinitionService.updateDefinition as jest.MockedFunction<typeof DefinitionService.updateDefinition>;

const WORKSPACE_ID = 'ws-1';
const DEFINITION_ID = 'def-1';

const fakeDefinition: WorkspaceBusinessDefinition = {
  id: DEFINITION_ID,
  workspaceId: WORKSPACE_ID,
  term: 'MRR',
  definition: 'Monthly recurring revenue',
  category: DefinitionCategory.Metric,
  status: DefinitionStatus.Active,
  source: DefinitionSource.Manual,
  entityTypeRefs: [],
  attributeRefs: [],
  version: 1,
  createdAt: new Date(),
  updatedAt: new Date(),
};

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { mutations: { retry: false } },
  });
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
  Wrapper.displayName = 'QueryWrapper';
  return { wrapper: Wrapper, queryClient };
}

describe('useSaveDefinitionMutation', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockUseAuth.mockReturnValue({
      session: { access_token: 'test-token' },
      user: { id: 'user-1' },
    } as unknown as ReturnType<typeof useAuth>);
  });

  describe('toast lifecycle', () => {
    it('shows loading toast on mutate and success toast on create', async () => {
      mockCreate.mockResolvedValue(fakeDefinition);
      const { wrapper } = createWrapper();

      const { result } = renderHook(
        () => useSaveDefinitionMutation(WORKSPACE_ID),
        { wrapper },
      );

      await act(async () => {
        await result.current.mutateAsync({
          term: 'MRR',
          definition: 'Monthly recurring revenue',
          category: DefinitionCategory.Metric,
          source: DefinitionSource.Manual,
          entityTypeRefs: [],
          attributeRefs: [],
        });
      });

      expect(toast.loading).toHaveBeenCalledWith('Saving definition...');
      expect(toast.dismiss).toHaveBeenCalledWith('toast-id');
      expect(toast.success).toHaveBeenCalledWith('Definition created');
    });

    it('shows success toast with "updated" message on update', async () => {
      mockUpdate.mockResolvedValue(fakeDefinition);
      const { wrapper } = createWrapper();

      const { result } = renderHook(
        () => useSaveDefinitionMutation(WORKSPACE_ID, DEFINITION_ID),
        { wrapper },
      );

      await act(async () => {
        await result.current.mutateAsync({
          term: 'MRR',
          definition: 'Monthly recurring revenue',
          category: DefinitionCategory.Metric,
          entityTypeRefs: [],
          attributeRefs: [],
          version: 1,
        });
      });

      expect(toast.success).toHaveBeenCalledWith('Definition updated');
    });

    it('shows error toast on failure', async () => {
      mockCreate.mockRejectedValue(new Error('Network error'));
      const { wrapper } = createWrapper();

      const { result } = renderHook(
        () => useSaveDefinitionMutation(WORKSPACE_ID),
        { wrapper },
      );

      await act(async () => {
        try {
          await result.current.mutateAsync({
            term: 'MRR',
            definition: 'Monthly recurring revenue',
            category: DefinitionCategory.Metric,
            source: DefinitionSource.Manual,
            entityTypeRefs: [],
            attributeRefs: [],
          });
        } catch {
          // expected
        }
      });

      expect(toast.dismiss).toHaveBeenCalledWith('toast-id');
      expect(toast.error).toHaveBeenCalledWith('Failed to save definition: Network error');
    });
  });

  describe('cache invalidation', () => {
    it('invalidates definitions list on create', async () => {
      mockCreate.mockResolvedValue(fakeDefinition);
      const { wrapper, queryClient } = createWrapper();
      const invalidateSpy = jest.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(
        () => useSaveDefinitionMutation(WORKSPACE_ID),
        { wrapper },
      );

      await act(async () => {
        await result.current.mutateAsync({
          term: 'MRR',
          definition: 'Monthly recurring revenue',
          category: DefinitionCategory.Metric,
          source: DefinitionSource.Manual,
          entityTypeRefs: [],
          attributeRefs: [],
        });
      });

      expect(invalidateSpy).toHaveBeenCalledWith({
        queryKey: definitionKeys.definitions.base(WORKSPACE_ID),
      });
    });

    it('invalidates both list and detail on update', async () => {
      mockUpdate.mockResolvedValue(fakeDefinition);
      const { wrapper, queryClient } = createWrapper();
      const invalidateSpy = jest.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(
        () => useSaveDefinitionMutation(WORKSPACE_ID, DEFINITION_ID),
        { wrapper },
      );

      await act(async () => {
        await result.current.mutateAsync({
          term: 'MRR',
          definition: 'Monthly recurring revenue',
          category: DefinitionCategory.Metric,
          entityTypeRefs: [],
          attributeRefs: [],
          version: 1,
        });
      });

      expect(invalidateSpy).toHaveBeenCalledWith({
        queryKey: definitionKeys.definitions.base(WORKSPACE_ID),
      });
      expect(invalidateSpy).toHaveBeenCalledWith({
        queryKey: definitionKeys.definition.detail(WORKSPACE_ID, DEFINITION_ID),
      });
    });
  });

  describe('onSuccess callback', () => {
    it('invokes onSuccess with the returned definition', async () => {
      mockCreate.mockResolvedValue(fakeDefinition);
      const onSuccess = jest.fn();
      const { wrapper } = createWrapper();

      const { result } = renderHook(
        () => useSaveDefinitionMutation(WORKSPACE_ID, undefined, { onSuccess }),
        { wrapper },
      );

      await act(async () => {
        await result.current.mutateAsync({
          term: 'MRR',
          definition: 'Monthly recurring revenue',
          category: DefinitionCategory.Metric,
          source: DefinitionSource.Manual,
          entityTypeRefs: [],
          attributeRefs: [],
        });
      });

      expect(onSuccess).toHaveBeenCalledWith(fakeDefinition);
    });
  });
});
