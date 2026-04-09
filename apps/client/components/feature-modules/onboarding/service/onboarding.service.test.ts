import { assemblePayload, OnboardingService } from '@/components/feature-modules/onboarding/service/onboarding.service';
import { WorkspacePlan, WorkspaceRoles } from '@/lib/types/workspace';
import { BusinessType, DefinitionCategory, AcquisitionChannel } from '@/lib/types/workspace';

describe('assemblePayload', () => {
  const baseProfile = { displayName: 'Jane Smith', phone: '+1234567890' };
  const baseWorkspace = {
    displayName: 'Acme Corp',
    plan: WorkspacePlan.Startup,
    businessType: BusinessType.B2CSaas,
  };

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

    it('includes businessType from workspace step', () => {
      const result = assemblePayload({
        profile: baseProfile,
        workspace: baseWorkspace,
      });
      expect(result.businessType).toBe(BusinessType.B2CSaas);
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

  describe('business definitions', () => {
    it('includes definitions with non-empty text', () => {
      const result = assemblePayload({
        profile: baseProfile,
        workspace: baseWorkspace,
        definitions: {
          definitions: [
            { term: 'MRR', definition: 'Monthly recurring revenue', category: DefinitionCategory.Metric, isCustom: false, defaultDefinition: 'Monthly recurring revenue' },
            { term: 'Churn', definition: '', category: DefinitionCategory.Metric, isCustom: false, defaultDefinition: 'Default churn' },
            { term: 'Enterprise', definition: 'Large accounts', category: DefinitionCategory.Segment, isCustom: true },
          ],
        },
      });
      expect(result.businessDefinitions).toEqual([
        { term: 'MRR', definition: 'Monthly recurring revenue', category: DefinitionCategory.Metric, isCustomized: false },
        { term: 'Enterprise', definition: 'Large accounts', category: DefinitionCategory.Segment, isCustomized: true },
      ]);
    });

    it('produces empty array when definitions step is missing', () => {
      const result = assemblePayload({
        profile: baseProfile,
        workspace: baseWorkspace,
      });
      expect(result.businessDefinitions).toEqual([]);
    });

    it('produces empty array when all definitions are blank', () => {
      const result = assemblePayload({
        profile: baseProfile,
        workspace: baseWorkspace,
        definitions: {
          definitions: [
            { term: 'MRR', definition: '', category: DefinitionCategory.Metric, isCustom: false, defaultDefinition: 'Default' },
            { term: 'Churn', definition: '   ', category: DefinitionCategory.Metric, isCustom: false, defaultDefinition: 'Default' },
          ],
        },
      });
      expect(result.businessDefinitions).toEqual([]);
    });

    it('strips isCustom from output definitions', () => {
      const result = assemblePayload({
        profile: baseProfile,
        workspace: baseWorkspace,
        definitions: {
          definitions: [
            { term: 'MRR', definition: 'Monthly recurring revenue', category: DefinitionCategory.Metric, isCustom: true },
          ],
        },
      });
      expect(result.businessDefinitions![0]).not.toHaveProperty('isCustom');
    });

    it('sets isCustomized true when definition differs from default', () => {
      const result = assemblePayload({
        profile: baseProfile,
        workspace: baseWorkspace,
        definitions: {
          definitions: [
            { term: 'Churn', definition: 'No purchase in 60 days', category: DefinitionCategory.Metric, isCustom: false, defaultDefinition: 'No purchase in 90 days' },
          ],
        },
      });
      expect(result.businessDefinitions![0].isCustomized).toBe(true);
    });

    it('sets isCustomized false when definition matches default', () => {
      const result = assemblePayload({
        profile: baseProfile,
        workspace: baseWorkspace,
        definitions: {
          definitions: [
            { term: 'Churn', definition: 'No purchase in 90 days', category: DefinitionCategory.Metric, isCustom: false, defaultDefinition: 'No purchase in 90 days' },
          ],
        },
      });
      expect(result.businessDefinitions![0].isCustomized).toBe(false);
    });

    it('sets isCustomized true for custom (user-added) terms', () => {
      const result = assemblePayload({
        profile: baseProfile,
        workspace: baseWorkspace,
        definitions: {
          definitions: [
            { term: 'My Term', definition: 'My definition', category: DefinitionCategory.Custom, isCustom: true },
          ],
        },
      });
      expect(result.businessDefinitions![0].isCustomized).toBe(true);
    });
  });

  describe('acquisition channels', () => {
    it('includes selected channels', () => {
      const result = assemblePayload({
        profile: baseProfile,
        workspace: baseWorkspace,
        channels: { selectedChannels: [AcquisitionChannel.GoogleAds, AcquisitionChannel.Referral] },
      });
      expect(result.acquisitionChannels).toEqual([AcquisitionChannel.GoogleAds, AcquisitionChannel.Referral]);
    });

    it('produces empty array when channels step is missing', () => {
      const result = assemblePayload({
        profile: baseProfile,
        workspace: baseWorkspace,
      });
      expect(result.acquisitionChannels).toEqual([]);
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
      workspace: { displayName: 'Acme', plan: WorkspacePlan.Free, businessType: BusinessType.DtcEcommerce },
    });
    await expect(
      OnboardingService.completeOnboarding(null, request),
    ).rejects.toThrow('Session is required');
  });
});
