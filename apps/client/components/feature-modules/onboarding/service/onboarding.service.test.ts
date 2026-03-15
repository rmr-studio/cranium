import { assemblePayload, OnboardingService } from '@/components/feature-modules/onboarding/service/onboarding.service';
import { WorkspacePlan, WorkspaceRoles } from '@/lib/types/workspace';

describe('assemblePayload', () => {
  const baseProfile = { displayName: 'Jane Smith', phone: '+1234567890' };
  const baseWorkspace = { displayName: 'Acme Corp', plan: WorkspacePlan.Startup };

  describe('happy path: all steps filled', () => {
    it('maps profile.displayName to profile.name', () => {
      const result = assemblePayload({
        profile: baseProfile,
        workspace: baseWorkspace,
      });
      expect(result.profile.name).toBe('Jane Smith');
    });

    it('maps workspace.displayName to workspace.name', () => {
      const result = assemblePayload({
        profile: baseProfile,
        workspace: baseWorkspace,
      });
      expect(result.workspace.name).toBe('Acme Corp');
    });

    it('preserves workspace.plan', () => {
      const result = assemblePayload({
        profile: baseProfile,
        workspace: baseWorkspace,
      });
      expect(result.workspace.plan).toBe(WorkspacePlan.Startup);
    });

    it('defaults workspace.defaultCurrency to USD', () => {
      const result = assemblePayload({
        profile: baseProfile,
        workspace: baseWorkspace,
      });
      expect(result.workspace.defaultCurrency).toBe('USD');
    });

    it('maps optional phone to profile.phone', () => {
      const result = assemblePayload({
        profile: baseProfile,
        workspace: baseWorkspace,
      });
      expect(result.profile.phone).toBe('+1234567890');
    });

    it('includes bundleKey when selectedBundleKey is set', () => {
      const result = assemblePayload({
        profile: baseProfile,
        workspace: baseWorkspace,
        templates: { selectedBundleKey: 'crm-bundle', bundles: [], templates: [] },
      });
      expect(result.bundleKeys).toEqual(['crm-bundle']);
    });

    it('includes invites from team step', () => {
      const result = assemblePayload({
        profile: baseProfile,
        workspace: baseWorkspace,
        team: {
          invites: [
            { email: 'alice@example.com', role: WorkspaceRoles.Admin },
            { email: 'bob@example.com', role: WorkspaceRoles.Member },
          ],
        },
      });
      expect(result.invites).toEqual([
        { email: 'alice@example.com', role: WorkspaceRoles.Admin },
        { email: 'bob@example.com', role: WorkspaceRoles.Member },
      ]);
    });
  });

  describe('skipped templates step', () => {
    it('produces empty bundleKeys when templates step is missing', () => {
      const result = assemblePayload({
        profile: baseProfile,
        workspace: baseWorkspace,
      });
      expect(result.bundleKeys).toEqual([]);
    });

    it('produces empty bundleKeys when selectedBundleKey is null', () => {
      const result = assemblePayload({
        profile: baseProfile,
        workspace: baseWorkspace,
        templates: { selectedBundleKey: null, bundles: [], templates: [] },
      });
      expect(result.bundleKeys).toEqual([]);
    });

    it('always produces empty templateKeys (templates installed via bundles)', () => {
      const result = assemblePayload({
        profile: baseProfile,
        workspace: baseWorkspace,
        templates: { selectedBundleKey: 'crm-bundle', bundles: [], templates: [] },
      });
      expect(result.templateKeys).toEqual([]);
    });
  });

  describe('skipped team step', () => {
    it('produces empty invites when team step is missing', () => {
      const result = assemblePayload({
        profile: baseProfile,
        workspace: baseWorkspace,
      });
      expect(result.invites).toEqual([]);
    });

    it('produces empty invites when team.invites is empty', () => {
      const result = assemblePayload({
        profile: baseProfile,
        workspace: baseWorkspace,
        team: { invites: [] },
      });
      expect(result.invites).toEqual([]);
    });
  });

  describe('profile without phone', () => {
    it('omits phone when not provided', () => {
      const result = assemblePayload({
        profile: { displayName: 'Jane' },
        workspace: baseWorkspace,
      });
      expect(result.profile.phone).toBeUndefined();
    });
  });
});

describe('OnboardingService.completeOnboarding', () => {
  it('throws when session is null', async () => {
    const request = assemblePayload({
      profile: { displayName: 'Jane' },
      workspace: { displayName: 'Acme', plan: WorkspacePlan.Free },
    });
    await expect(
      OnboardingService.completeOnboarding(null, request),
    ).rejects.toThrow('Session is required');
  });
});
