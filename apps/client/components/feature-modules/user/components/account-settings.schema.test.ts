import { accountSettingsSchema } from './account-settings';

describe('accountSettingsSchema', () => {
  it('validates valid input', () => {
    const result = accountSettingsSchema.safeParse({
      firstName: 'Jared',
      lastName: 'Tucker',
    });
    expect(result.success).toBe(true);
  });

  it('accepts empty last name', () => {
    const result = accountSettingsSchema.safeParse({
      firstName: 'Jared',
      lastName: '',
    });
    expect(result.success).toBe(true);
  });

  it('accepts missing last name (defaults to empty string)', () => {
    const result = accountSettingsSchema.safeParse({
      firstName: 'Jared',
    });
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.lastName).toBe('');
    }
  });

  it('rejects empty first name', () => {
    const result = accountSettingsSchema.safeParse({
      firstName: '',
      lastName: 'Tucker',
    });
    expect(result.success).toBe(false);
  });

  it('rejects missing first name', () => {
    const result = accountSettingsSchema.safeParse({
      lastName: 'Tucker',
    });
    expect(result.success).toBe(false);
  });

  it('rejects first name exceeding 50 characters', () => {
    const result = accountSettingsSchema.safeParse({
      firstName: 'a'.repeat(51),
      lastName: 'Tucker',
    });
    expect(result.success).toBe(false);
  });

  it('rejects last name exceeding 50 characters', () => {
    const result = accountSettingsSchema.safeParse({
      firstName: 'Jared',
      lastName: 'a'.repeat(51),
    });
    expect(result.success).toBe(false);
  });
});
