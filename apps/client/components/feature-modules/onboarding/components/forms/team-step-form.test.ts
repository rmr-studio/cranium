import { WorkspaceRoles } from '@/lib/types/workspace';
import {
  inviteEntrySchema,
  INVITE_ROLES,
  INVITE_SOFT_CAP,
  isDuplicateEmail,
} from '@/components/feature-modules/onboarding/components/forms/team-step-form';

describe('inviteEntrySchema', () => {
  it('accepts valid email with Member role', () => {
    const result = inviteEntrySchema.safeParse({
      email: 'test@example.com',
      role: WorkspaceRoles.Member,
    });
    expect(result.success).toBe(true);
  });

  it('accepts valid email with Admin role', () => {
    const result = inviteEntrySchema.safeParse({
      email: 'test@example.com',
      role: WorkspaceRoles.Admin,
    });
    expect(result.success).toBe(true);
  });

  it('rejects invalid email format', () => {
    const result = inviteEntrySchema.safeParse({
      email: 'not-an-email',
      role: WorkspaceRoles.Member,
    });
    expect(result.success).toBe(false);
    expect(result.error?.issues.some((i) => i.path.includes('email'))).toBe(true);
  });

  it('rejects empty email string', () => {
    const result = inviteEntrySchema.safeParse({
      email: '',
      role: WorkspaceRoles.Member,
    });
    expect(result.success).toBe(false);
    expect(result.error?.issues.some((i) => i.path.includes('email'))).toBe(true);
  });

  it('rejects Owner role since it is excluded from the schema enum', () => {
    const result = inviteEntrySchema.safeParse({
      email: 'test@example.com',
      role: WorkspaceRoles.Owner,
    });
    expect(result.success).toBe(false);
    expect(result.error?.issues.some((i) => i.path.includes('role'))).toBe(true);
  });
});

describe('INVITE_ROLES', () => {
  it('contains only Admin and Member (not Owner)', () => {
    expect(INVITE_ROLES).toEqual([WorkspaceRoles.Admin, WorkspaceRoles.Member]);
    expect(INVITE_ROLES).not.toContain(WorkspaceRoles.Owner);
  });
});

describe('isDuplicateEmail', () => {
  it('returns true when email exists in list', () => {
    const invites = [{ email: 'a@b.com', role: WorkspaceRoles.Member }];
    expect(isDuplicateEmail('a@b.com', invites)).toBe(true);
  });

  it('returns false when email does not exist in list', () => {
    const invites = [{ email: 'a@b.com', role: WorkspaceRoles.Member }];
    expect(isDuplicateEmail('c@d.com', invites)).toBe(false);
  });
});

describe('INVITE_SOFT_CAP', () => {
  it('equals 10', () => {
    expect(INVITE_SOFT_CAP).toBe(10);
  });
});
