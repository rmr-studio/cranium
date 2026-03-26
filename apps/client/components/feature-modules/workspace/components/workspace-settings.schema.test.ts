import { workspaceSettingsSchema } from '@/components/feature-modules/workspace/components/workspace-settings';

describe('workspaceSettingsSchema', () => {
  it('validates valid input', () => {
    const result = workspaceSettingsSchema.safeParse({ name: 'RMR Studio' });
    expect(result.success).toBe(true);
  });

  it('rejects empty name', () => {
    const result = workspaceSettingsSchema.safeParse({ name: '' });
    expect(result.success).toBe(false);
  });

  it('rejects name shorter than 3 characters', () => {
    const result = workspaceSettingsSchema.safeParse({ name: 'AB' });
    expect(result.success).toBe(false);
  });

  it('accepts name with exactly 3 characters', () => {
    const result = workspaceSettingsSchema.safeParse({ name: 'ABC' });
    expect(result.success).toBe(true);
  });

  it('rejects name exceeding 100 characters', () => {
    const result = workspaceSettingsSchema.safeParse({ name: 'a'.repeat(101) });
    expect(result.success).toBe(false);
  });

  it('accepts name at 100 characters', () => {
    const result = workspaceSettingsSchema.safeParse({ name: 'a'.repeat(100) });
    expect(result.success).toBe(true);
  });

  it('rejects missing name', () => {
    const result = workspaceSettingsSchema.safeParse({});
    expect(result.success).toBe(false);
  });
});
