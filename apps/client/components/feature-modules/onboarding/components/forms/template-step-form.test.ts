import { toggleBundleSelection } from './template-step-form';

describe('toggleBundleSelection', () => {
  it('selects a bundle when nothing is selected', () => {
    expect(toggleBundleSelection(null, 'crm')).toBe('crm');
  });

  it('replaces the current selection when a different bundle is clicked', () => {
    expect(toggleBundleSelection('crm', 'hr')).toBe('hr');
  });

  it('deselects the current bundle when the same bundle is clicked again', () => {
    expect(toggleBundleSelection('crm', 'crm')).toBeNull();
  });
});

describe('template step formTrigger', () => {
  it('formTrigger always resolves true (optional step)', async () => {
    const formTrigger = async () => true;
    await expect(formTrigger()).resolves.toBe(true);
  });
});
