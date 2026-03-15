import { profileStepSchema } from '@/components/feature-modules/onboarding/components/forms/profile-step-form';

describe('profileStepSchema', () => {
  it('accepts valid displayName with 3+ chars', () => {
    const result = profileStepSchema.safeParse({ displayName: 'Jon' });
    expect(result.success).toBe(true);
  });

  it('accepts long displayName', () => {
    const result = profileStepSchema.safeParse({ displayName: 'Jonathan Alexander Smith' });
    expect(result.success).toBe(true);
  });

  it('rejects empty displayName', () => {
    const result = profileStepSchema.safeParse({ displayName: '' });
    expect(result.success).toBe(false);
    expect(result.error?.issues.some((i) => i.path.includes('displayName'))).toBe(true);
  });

  it('rejects displayName shorter than 3 chars', () => {
    const result = profileStepSchema.safeParse({ displayName: 'Jo' });
    expect(result.success).toBe(false);
    const issue = result.error?.issues.find((i) => i.path.includes('displayName'));
    expect(issue?.message).toMatch(/at least 3/i);
  });

  it('rejects missing displayName', () => {
    const result = profileStepSchema.safeParse({});
    expect(result.success).toBe(false);
    expect(result.error?.issues.some((i) => i.path.includes('displayName'))).toBe(true);
  });

  it('has no phone field', () => {
    const result = profileStepSchema.safeParse({
      displayName: 'Jon',
      phone: '+61400000000',
    });
    expect(result.success).toBe(true);
    if (result.success) {
      expect(Object.keys(result.data)).not.toContain('phone');
    }
  });

  it('trims whitespace before validating length', () => {
    const result = profileStepSchema.safeParse({ displayName: '  Jo  ' });
    expect(result.success).toBe(false);
    const issue = result.error?.issues.find((i) => i.path.includes('displayName'));
    expect(issue?.message).toMatch(/at least 3/i);
  });

  it('trims whitespace and passes when trimmed value has 3+ chars', () => {
    const result = profileStepSchema.safeParse({ displayName: '  Jon  ' });
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.displayName).toBe('Jon');
    }
  });
});
