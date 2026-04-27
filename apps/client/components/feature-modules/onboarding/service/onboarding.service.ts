import { createOnboardingApi } from '@/lib/api/onboarding-api';
import { Session } from '@/lib/auth';
import {
  CompleteOnboardingRequest,
  CompleteOnboardingResponse,
  OnboardingProfile,
  OnboardingWorkspace,
  OnboardingInvite,
  AcquisitionChannel,
  DefinitionCategory,
} from '@/lib/types/models';
import type { OnboardingBusinessDefinition } from '@/lib/types/models';


import { WorkspacePlan, WorkspaceRoles } from '@/lib/types/workspace';
import { normalizeApiError } from '@/lib/util/error/error.util';

interface ProfileStepShape {
  displayName: string;
  phone?: string;
}

interface WorkspaceStepShape {
  displayName: string;
  plan: WorkspacePlan;
}

interface DefinitionsStepShape {
  definitions: Array<{
    term: string;
    definition: string;
    category: DefinitionCategory;
    isCustom: boolean;
    defaultDefinition?: string;
  }>;
}

interface ChannelsStepShape {
  selectedChannels: AcquisitionChannel[];
}

interface TeamStepShape {
  invites: Array<{ email: string; role: WorkspaceRoles }>;
}

interface ValidatedStepData {
  profile?: ProfileStepShape;
  workspace?: WorkspaceStepShape;
  definitions?: DefinitionsStepShape;
  channels?: ChannelsStepShape;
  team?: TeamStepShape;
  [key: string]: unknown;
}

/**
 * Assembles a CompleteOnboardingRequest from validated step data collected in the store.
 * This is a pure function with no side effects.
 */
export function assemblePayload(data: ValidatedStepData): CompleteOnboardingRequest {
  const profileData = data.profile;
  const workspaceData = data.workspace;

  if (!profileData) {
    throw new Error('Profile step data is required to assemble payload');
  }
  if (!workspaceData) {
    throw new Error('Workspace step data is required to assemble payload');
  }

  const profile: OnboardingProfile = {
    name: profileData.displayName,
    ...(profileData.phone ? { phone: profileData.phone } : {}),
  };

  const workspace: OnboardingWorkspace = {
    name: workspaceData.displayName,
    plan: workspaceData.plan,
    defaultCurrency: 'USD',
  };

  const teamData = data.team;
  const invites: OnboardingInvite[] = teamData?.invites ?? [];

  const definitionsData = data.definitions;
  const businessDefinitions: OnboardingBusinessDefinition[] =
    definitionsData?.definitions
      ?.filter((d) => d.definition.trim().length > 0)
      ?.map((d) => ({
        term: d.term,
        definition: d.definition,
        category: d.category,
        isCustomized: d.isCustom || d.definition !== (d.defaultDefinition ?? ''),
      }))
      ?? [];

  const channelsData = data.channels;
  const acquisitionChannels: AcquisitionChannel[] =
    channelsData?.selectedChannels ?? [];

  return {
    profile,
    workspace,
    invites,
    businessDefinitions,
    acquisitionChannels,
  };
}

export class OnboardingService {
  /**
   * Submits all onboarding data to the backend in a single multipart request.
   */
  static async completeOnboarding(
    session: Session | null,
    request: CompleteOnboardingRequest,
    profileAvatar?: Blob | null,
    workspaceAvatar?: Blob | null,
  ): Promise<CompleteOnboardingResponse | void> {
    if (!session) {
      throw new Error('Session is required');
    }

    const api = createOnboardingApi(session);

    try {
      return await api.completeOnboarding({
        request,
        ...(profileAvatar ? { profileAvatar } : {}),
        ...(workspaceAvatar ? { workspaceAvatar } : {}),
      });
    } catch (error) {
      await normalizeApiError(error);
    }
  }
}
