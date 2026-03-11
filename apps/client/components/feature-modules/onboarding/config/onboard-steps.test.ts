import { ONBOARD_STEPS, SECTION_WIDTH } from './onboard-steps';

describe('onboard-steps', () => {
  describe('SECTION_WIDTH', () => {
    it('is 800', () => {
      expect(SECTION_WIDTH).toBe(800);
    });
  });

  describe('ONBOARD_STEPS', () => {
    it('has exactly 4 entries', () => {
      expect(ONBOARD_STEPS).toHaveLength(4);
    });

    it('each entry has all required fields', () => {
      for (const step of ONBOARD_STEPS) {
        expect(step).toHaveProperty('id');
        expect(step).toHaveProperty('label');
        expect(step).toHaveProperty('optional');
        expect(step).toHaveProperty('cameraX');
        expect(step).toHaveProperty('PreviewComponent');
      }
    });

    it('step ids are profile, workspace, templates, team in order', () => {
      expect(ONBOARD_STEPS[0].id).toBe('profile');
      expect(ONBOARD_STEPS[1].id).toBe('workspace');
      expect(ONBOARD_STEPS[2].id).toBe('templates');
      expect(ONBOARD_STEPS[3].id).toBe('team');
    });

    it('profile and workspace are required (optional: false)', () => {
      expect(ONBOARD_STEPS[0].optional).toBe(false);
      expect(ONBOARD_STEPS[1].optional).toBe(false);
    });

    it('templates and team are optional (optional: true)', () => {
      expect(ONBOARD_STEPS[2].optional).toBe(true);
      expect(ONBOARD_STEPS[3].optional).toBe(true);
    });

    it('cameraX values are monotonically increasing', () => {
      for (let i = 1; i < ONBOARD_STEPS.length; i++) {
        expect(ONBOARD_STEPS[i].cameraX).toBeGreaterThan(ONBOARD_STEPS[i - 1].cameraX);
      }
    });

    it('cameraX values match index * SECTION_WIDTH', () => {
      for (let i = 0; i < ONBOARD_STEPS.length; i++) {
        expect(ONBOARD_STEPS[i].cameraX).toBe(i * SECTION_WIDTH);
      }
    });

    it('all PreviewComponents are functions', () => {
      for (const step of ONBOARD_STEPS) {
        expect(typeof step.PreviewComponent).toBe('function');
      }
    });

    it('all entries have non-empty string ids and labels', () => {
      for (const step of ONBOARD_STEPS) {
        expect(typeof step.id).toBe('string');
        expect(step.id.length).toBeGreaterThan(0);
        expect(typeof step.label).toBe('string');
        expect(step.label.length).toBeGreaterThan(0);
      }
    });
  });
});
