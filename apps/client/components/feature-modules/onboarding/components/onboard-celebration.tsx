'use client';

import { Alert, AlertDescription } from '@/components/ui/alert';
import { Button } from '@riven/ui';
import { Propless } from '@/lib/interfaces/interface';
import { AlertTriangle, X } from 'lucide-react';
import { motion } from 'motion/react';
import { useRouter } from 'next/navigation';
import { FC, useEffect, useMemo, useState } from 'react';
import { getInitials, getPaletteColor } from '@/components/feature-modules/onboarding/utils/avatar-helpers';
import {
  useOnboardSubmission,
  useOnboardStore,
} from '@/components/feature-modules/onboarding/hooks/use-onboard-store';

const REDIRECT_SECONDS = 3;

function buildSetupSummary(
  templateResults: Array<{ success: boolean }>,
  inviteResults: Array<{ success: boolean }>,
): string {
  const succeededTemplates = templateResults.filter((r) => r.success).length;
  const succeededInvites = inviteResults.filter((r) => r.success).length;

  const parts: string[] = [];
  if (succeededTemplates > 0) {
    parts.push(
      `${succeededTemplates} template${succeededTemplates === 1 ? '' : 's'} installed`,
    );
  }
  if (succeededInvites > 0) {
    parts.push(
      `${succeededInvites} invite${succeededInvites === 1 ? '' : 's'} sent`,
    );
  }

  return parts.length > 0 ? parts.join(', ') : 'Workspace created';
}

export const OnboardCelebration: FC<Propless> = () => {
  const router = useRouter();
  const { submissionResponse, reset } = useOnboardSubmission();
  const workspaceAvatarBlob = useOnboardStore((s) => s.workspaceAvatarBlob);

  const [countdown, setCountdown] = useState(REDIRECT_SECONDS);
  const [warningVisible, setWarningVisible] = useState(true);

  const workspaceId = submissionResponse?.workspace.id ?? '';
  const workspaceName = submissionResponse?.workspace.name ?? '';

  const avatarUrl = useMemo(
    () => (workspaceAvatarBlob ? URL.createObjectURL(workspaceAvatarBlob) : null),
    [workspaceAvatarBlob],
  );

  // Revoke blob URL on unmount
  useEffect(() => {
    return () => {
      if (avatarUrl) {
        URL.revokeObjectURL(avatarUrl);
      }
    };
  }, [avatarUrl]);

  useEffect(() => {
    if (!submissionResponse || !workspaceId) return;

    if (countdown <= 0) {
      router.push(`/dashboard/workspace/${workspaceId}`);
      setTimeout(() => reset(), 0);
      return;
    }

    const timer = setInterval(() => {
      setCountdown((prev) => prev - 1);
    }, 1000);

    return () => clearInterval(timer);
  }, [countdown, submissionResponse, workspaceId, router, reset]);

  if (!submissionResponse) return null;

  const { templateResults, inviteResults } = submissionResponse;

  const initials = getInitials(workspaceName);
  const paletteColor = getPaletteColor(workspaceName);

  const failedTemplates = templateResults.filter((r) => !r.success);
  const failedInvites = inviteResults.filter((r) => !r.success);
  const hasPartialFailure = failedTemplates.length > 0 || failedInvites.length > 0;

  const setupSummary = buildSetupSummary(templateResults, inviteResults);

  const navigateToWorkspace = () => {
    router.push(`/dashboard/workspace/${workspaceId}`);
    setTimeout(() => reset(), 0);
  };

  return (
    <div className="bg-background flex h-full w-full flex-col items-center justify-center px-8">
      <motion.div
        initial={{ opacity: 0, y: 24 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, ease: [0.4, 0, 0.2, 1] }}
        className="flex w-full max-w-md flex-col items-center gap-6 text-center"
      >
        {/* Workspace avatar */}
        {avatarUrl ? (
          <img
            src={avatarUrl}
            alt={workspaceName}
            className="h-20 w-20 rounded-2xl object-cover"
          />
        ) : (
          <div
            className={`flex h-20 w-20 items-center justify-center rounded-2xl text-2xl font-bold text-white ${paletteColor}`}
          >
            {initials}
          </div>
        )}

        {/* Heading */}
        <div className="flex flex-col gap-2">
          <h1 className="text-foreground text-2xl font-bold">{workspaceName}</h1>
          <p className="text-muted-foreground text-sm">{setupSummary}</p>
        </div>

        {/* Partial failure warning */}
        {hasPartialFailure && warningVisible && (
          <Alert variant="destructive" className="text-left">
            <AlertTriangle className="h-4 w-4" />
            <AlertDescription className="flex items-start justify-between gap-2">
              <span>
                {failedTemplates.length > 0 && (
                  <span className="block">
                    {failedTemplates.length} template
                    {failedTemplates.length === 1 ? '' : 's'} could not be
                    installed.
                  </span>
                )}
                {failedInvites.length > 0 && (
                  <span className="block">
                    {failedInvites.length} invite
                    {failedInvites.length === 1 ? '' : 's'} could not be sent.
                    You can retry from workspace settings.
                  </span>
                )}
              </span>
              <button
                type="button"
                onClick={() => setWarningVisible(false)}
                className="text-destructive-foreground/70 hover:text-destructive-foreground shrink-0 transition-colors"
                aria-label="Dismiss warning"
              >
                <X className="h-4 w-4" />
              </button>
            </AlertDescription>
          </Alert>
        )}

        {/* Redirect countdown */}
        <p className="text-muted-foreground text-sm">
          Redirecting in {countdown}s...
        </p>

        {/* Go to workspace button */}
        <Button onClick={navigateToWorkspace} className="w-full">
          Go to workspace
        </Button>
      </motion.div>
    </div>
  );
};
