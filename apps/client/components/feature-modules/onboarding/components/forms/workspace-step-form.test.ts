import { WorkspacePlan } from '@/lib/types/workspace';
import { workspaceStepSchema } from './workspace-step-form';

describe('workspaceStepSchema', () => {
  it('accepts valid displayName and plan', () => {
    const result = workspaceStepSchema.safeParse({
      displayName: 'Acme Corp',
      plan: WorkspacePlan.Free,
    });
    expect(result.success).toBe(true);
  });

  it('rejects empty displayName', () => {
    const result = workspaceStepSchema.safeParse({
      displayName: '',
      plan: WorkspacePlan.Free,
    });
    expect(result.success).toBe(false);
    expect(result.error?.issues.some((i) => i.path.includes('displayName'))).toBe(true);
  });

  it('rejects displayName shorter than 3 chars', () => {
    const result = workspaceStepSchema.safeParse({
      displayName: 'Ac',
      plan: WorkspacePlan.Startup,
    });
    expect(result.success).toBe(false);
    const issue = result.error?.issues.find((i) => i.path.includes('displayName'));
    expect(issue?.message).toMatch(/at least 3/i);
  });

  it('rejects missing displayName', () => {
    const result = workspaceStepSchema.safeParse({ plan: WorkspacePlan.Scale });
    expect(result.success).toBe(false);
    expect(result.error?.issues.some((i) => i.path.includes('displayName'))).toBe(true);
  });

  it('rejects missing plan', () => {
    const result = workspaceStepSchema.safeParse({ displayName: 'Acme Corp' });
    expect(result.success).toBe(false);
    expect(result.error?.issues.some((i) => i.path.includes('plan'))).toBe(true);
  });

  it('rejects invalid plan value', () => {
    const result = workspaceStepSchema.safeParse({
      displayName: 'Acme Corp',
      plan: 'INVALID',
    });
    expect(result.success).toBe(false);
    expect(result.error?.issues.some((i) => i.path.includes('plan'))).toBe(true);
  });

  it('accepts all valid WorkspacePlan values', () => {
    const plans = [
      WorkspacePlan.Free,
      WorkspacePlan.Startup,
      WorkspacePlan.Scale,
      WorkspacePlan.Enterprise,
    ];
    for (const plan of plans) {
      const result = workspaceStepSchema.safeParse({ displayName: 'Acme Corp', plan });
      expect(result.success).toBe(true);
    }
  });

  it('has no currency field', () => {
    const result = workspaceStepSchema.safeParse({
      displayName: 'Acme',
      plan: WorkspacePlan.Free,
      currency: 'AUD',
    });
    expect(result.success).toBe(true);
    if (result.success) {
      expect(Object.keys(result.data)).not.toContain('currency');
    }
  });
});
