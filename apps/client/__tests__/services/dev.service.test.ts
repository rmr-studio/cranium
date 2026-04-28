import { DevService } from '@/components/feature-modules/dev/service/dev.service';
import { createDevApi } from '@/lib/api/dev-api';
import type { Session } from '@/lib/auth';
import { normalizeApiError } from '@/lib/util/error/error.util';

jest.mock('@/lib/api/dev-api');
jest.mock('@/lib/util/error/error.util', () => ({
  normalizeApiError: jest.fn((error: unknown) => {
    throw error;
  }),
}));

const mockedCreateDevApi = createDevApi as jest.MockedFunction<typeof createDevApi>;
const mockedNormalizeApiError = normalizeApiError as jest.MockedFunction<typeof normalizeApiError>;

const session = { access_token: 'token' } as unknown as Session;

describe('DevService', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('seedWorkspace', () => {
    it('calls api.seedWorkspace with workspaceId and returns response', async () => {
      const response = { entitiesCreated: 3, relationshipsCreated: 1, alreadySeeded: false };
      const seedWorkspace = jest.fn().mockResolvedValue(response);
      mockedCreateDevApi.mockReturnValue({ seedWorkspace } as never);

      const result = await DevService.seedWorkspace(session, 'ws-1');

      expect(seedWorkspace).toHaveBeenCalledWith({ workspaceId: 'ws-1' });
      expect(result).toBe(response);
    });

    it('routes errors through normalizeApiError', async () => {
      const error = new Error('boom');
      const seedWorkspace = jest.fn().mockRejectedValue(error);
      mockedCreateDevApi.mockReturnValue({ seedWorkspace } as never);

      await expect(DevService.seedWorkspace(session, 'ws-1')).rejects.toBe(error);
      expect(mockedNormalizeApiError).toHaveBeenCalledWith(error);
    });
  });

  describe('reinstallTemplate', () => {
    it('calls api.reinstallTemplate with workspaceId + templateKey and returns response', async () => {
      const response = {
        templateName: 'crm',
        entityTypesCreated: 2,
        relationshipsCreated: 1,
      };
      const reinstallTemplate = jest.fn().mockResolvedValue(response);
      mockedCreateDevApi.mockReturnValue({ reinstallTemplate } as never);

      const result = await DevService.reinstallTemplate(session, 'ws-1', 'crm');

      expect(reinstallTemplate).toHaveBeenCalledWith({ workspaceId: 'ws-1', templateKey: 'crm' });
      expect(result).toBe(response);
    });

    it('routes errors through normalizeApiError', async () => {
      const error = new Error('nope');
      const reinstallTemplate = jest.fn().mockRejectedValue(error);
      mockedCreateDevApi.mockReturnValue({ reinstallTemplate } as never);

      await expect(DevService.reinstallTemplate(session, 'ws-1', 'crm')).rejects.toBe(error);
      expect(mockedNormalizeApiError).toHaveBeenCalledWith(error);
    });
  });
});
