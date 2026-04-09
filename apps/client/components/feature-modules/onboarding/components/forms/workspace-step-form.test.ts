import { WorkspacePlan } from '@/lib/types/workspace';
import { BusinessType } from '@/lib/types/models';
import { workspaceStepSchema } from '@/components/feature-modules/onboarding/components/forms/workspace-step-form';

describe('workspaceStepSchema', () => {
  const validData = {
    displayName: 'Acme Corp',
    plan: WorkspacePlan.Free,
    businessType: BusinessType.B2CSaas,
  };

  it('accepts valid displayName, plan, and businessType', () => {
    const result = workspaceStepSchema.safeParse(validData);
    expect(result.success).toBe(true);
  });

  it('rejects empty displayName', () => {
    const result = workspaceStepSchema.safeParse({
      ...validData,
      displayName: '',
    });
    expect(result.success).toBe(false);
    expect(result.error?.issues.some((i) => i.path.includes('displayName'))).toBe(true);
  });

  it('rejects displayName shorter than 3 chars', () => {
    const result = workspaceStepSchema.safeParse({
      ...validData,
      displayName: 'Ac',
    });
    expect(result.success).toBe(false);
    const issue = result.error?.issues.find((i) => i.path.includes('displayName'));
    expect(issue?.message).toMatch(/at least 3/i);
  });

  it('rejects missing displayName', () => {
    const result = workspaceStepSchema.safeParse({
      plan: WorkspacePlan.Scale,
      businessType: BusinessType.DtcEcommerce,
    });
    expect(result.success).toBe(false);
    expect(result.error?.issues.some((i) => i.path.includes('displayName'))).toBe(true);
  });

  it('rejects missing plan', () => {
    const result = workspaceStepSchema.safeParse({
      displayName: 'Acme Corp',
      businessType: BusinessType.B2CSaas,
    });
    expect(result.success).toBe(false);
    expect(result.error?.issues.some((i) => i.path.includes('plan'))).toBe(true);
  });

  it('rejects invalid plan value', () => {
    const result = workspaceStepSchema.safeParse({
      ...validData,
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
      const result = workspaceStepSchema.safeParse({ ...validData, plan });
      expect(result.success).toBe(true);
    }
  });

  it('rejects missing businessType', () => {
    const result = workspaceStepSchema.safeParse({
      displayName: 'Acme Corp',
      plan: WorkspacePlan.Free,
    });
    expect(result.success).toBe(false);
    expect(result.error?.issues.some((i) => i.path.includes('businessType'))).toBe(true);
  });

  it('rejects invalid businessType value', () => {
    const result = workspaceStepSchema.safeParse({
      ...validData,
      businessType: 'INVALID',
    });
    expect(result.success).toBe(false);
    expect(result.error?.issues.some((i) => i.path.includes('businessType'))).toBe(true);
  });

  it('accepts all valid BusinessType values', () => {
    const types = [BusinessType.DtcEcommerce, BusinessType.B2CSaas];
    for (const businessType of types) {
      const result = workspaceStepSchema.safeParse({ ...validData, businessType });
      expect(result.success).toBe(true);
    }
  });

  it('has no currency field', () => {
    const result = workspaceStepSchema.safeParse({
      ...validData,
      currency: 'AUD',
    });
    expect(result.success).toBe(true);
    if (result.success) {
      expect(Object.keys(result.data)).not.toContain('currency');
    }
  });

  it('trims whitespace before validating length', () => {
    const result = workspaceStepSchema.safeParse({
      ...validData,
      displayName: '  Ac  ',
    });
    expect(result.success).toBe(false);
    const issue = result.error?.issues.find((i) => i.path.includes('displayName'));
    expect(issue?.message).toMatch(/at least 3/i);
  });
});
