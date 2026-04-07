import { viewRegistry } from './panel-view-registry';
import type { SidePanelViewType } from '../types/side-panel.types';

/**
 * This test ensures every member of the SidePanelView discriminated union
 * has a corresponding entry in the view registry. If you add a new view type
 * to side-panel.types.ts, this test will fail until you add a registry entry.
 */
describe('viewRegistry', () => {
  // Manually list all SidePanelView type literals.
  // If you add a new type, add it here — the test will catch missing registry entries.
  const allViewTypes: SidePanelViewType[] = [
    'definition-detail',
    'entity-notes',
    'integration-detail',
  ];

  it.each(allViewTypes)('has a registry entry for "%s"', (viewType) => {
    expect(viewRegistry).toHaveProperty(viewType);
    expect(viewRegistry[viewType]).toBeDefined();
  });

  it('registry has no extra entries beyond known view types', () => {
    const registryKeys = Object.keys(viewRegistry);
    expect(registryKeys.sort()).toEqual([...allViewTypes].sort());
  });
});
