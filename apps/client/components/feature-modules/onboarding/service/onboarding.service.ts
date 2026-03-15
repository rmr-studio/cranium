import { createOnboardingApi } from '@/lib/api/onboarding-api';
import { Session } from '@/lib/auth';
import {
  CompleteOnboardingRequest,
  CompleteOnboardingResponse,
  OnboardingProfile,
  OnboardingWorkspace,
  OnboardingInvite,
} from '@/lib/types/models';
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

interface TemplatesStepShape {
  selectedBundleKey: string | null;
  bundles: unknown[];
  templates: unknown[];
}

interface TeamStepShape {
  invites: Array<{ email: string; role: WorkspaceRoles }>;
}

interface ValidatedStepData {
  profile?: ProfileStepShape;
  workspace?: WorkspaceStepShape;
  templates?: TemplatesStepShape;
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

  const templatesData = data.templates;
  const bundleKeys: string[] =
    templatesData?.selectedBundleKey ? [templatesData.selectedBundleKey] : [];
  const templateKeys: string[] = [];

  const teamData = data.team;
  const invites: OnboardingInvite[] = teamData?.invites ?? [];

  return {
    profile,
    workspace,
    bundleKeys,
    templateKeys,
    invites,
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
  ): Promise<CompleteOnboardingResponse> {
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
