import { createOnboardStore } from '@/components/feature-modules/onboarding/stores/onboard.store';

function createStore() {
  return createOnboardStore();
}

describe('onboard.store', () => {
  let store: ReturnType<typeof createStore>;

  beforeEach(() => {
    store = createStore();
  });

  describe('initial state', () => {
    it('starts at step 0', () => {
      expect(store.getState().currentStep).toBe(0);
    });

    it('starts with direction forward', () => {
      expect(store.getState().direction).toBe('forward');
    });

    it('starts with empty validatedData', () => {
      expect(store.getState().validatedData).toEqual({});
    });

    it('starts with empty liveData', () => {
      expect(store.getState().liveData).toEqual({});
    });

    it('starts with null formTrigger', () => {
      expect(store.getState().formTrigger).toBeNull();
    });
  });

  describe('goNext', () => {
    it('increments currentStep by 1', () => {
      store.getState().goNext();
      expect(store.getState().currentStep).toBe(1);
    });

    it('sets direction to forward', () => {
      store.getState().goBack();
      // direction is now backward - use goNext to change it
      store.setState({ currentStep: 1 });
      store.getState().goNext();
      expect(store.getState().direction).toBe('forward');
    });

    it('does not exceed max step index', () => {
      // Go to last step (index 4)
      store.setState({ currentStep: 4 });
      store.getState().goNext();
      expect(store.getState().currentStep).toBe(4);
    });

    it('clamps at ONBOARD_STEPS.length - 1', () => {
      store.setState({ currentStep: 100 });
      store.getState().goNext();
      // After goNext from 100, clamped to 4 (max index)
      expect(store.getState().currentStep).toBe(4);
    });
  });

  describe('goBack', () => {
    it('decrements currentStep by 1', () => {
      store.setState({ currentStep: 2 });
      store.getState().goBack();
      expect(store.getState().currentStep).toBe(1);
    });

    it('sets direction to backward', () => {
      store.setState({ currentStep: 1 });
      store.getState().goBack();
      expect(store.getState().direction).toBe('backward');
    });

    it('does not go below step 0', () => {
      store.getState().goBack();
      expect(store.getState().currentStep).toBe(0);
    });
  });

  describe('skip', () => {
    it('advances when current step is optional (definitions = index 2)', () => {
      store.setState({ currentStep: 2 });
      store.getState().skip();
      expect(store.getState().currentStep).toBe(3);
    });

    it('sets direction to forward on optional step skip', () => {
      store.setState({ currentStep: 2 });
      store.getState().skip();
      expect(store.getState().direction).toBe('forward');
    });

    it('does NOT advance when current step is required (profile = index 0)', () => {
      store.getState().skip();
      expect(store.getState().currentStep).toBe(0);
    });

    it('does NOT advance when current step is required (workspace = index 1)', () => {
      store.setState({ currentStep: 1 });
      store.getState().skip();
      expect(store.getState().currentStep).toBe(1);
    });

    it('advances when current step is optional (team = index 4)', () => {
      store.setState({ currentStep: 4 });
      store.getState().skip();
      // Clamped at last step
      expect(store.getState().currentStep).toBe(4);
    });
  });

  describe('setStepData', () => {
    it('stores data under the correct stepId key', () => {
      const profileData = { name: 'John', avatar: 'url' };
      store.getState().setStepData('profile', profileData);
      expect(store.getState().validatedData.profile).toEqual(profileData);
    });

    it('merges multiple step data entries without overwriting others', () => {
      store.getState().setStepData('profile', { name: 'John' });
      store.getState().setStepData('workspace', { name: 'Acme' });
      expect(store.getState().validatedData.profile).toEqual({ name: 'John' });
      expect(store.getState().validatedData.workspace).toEqual({ name: 'Acme' });
    });
  });

  describe('reset', () => {
    it('returns to initial state after modifications', () => {
      store.setState({ currentStep: 3, direction: 'backward' });
      store.getState().setStepData('profile', { name: 'John' });
      store.getState().reset();

      const state = store.getState();
      expect(state.currentStep).toBe(0);
      expect(state.direction).toBe('forward');
      expect(state.validatedData).toEqual({});
    });
  });

  describe('setLiveData', () => {
    it('stores data under the correct stepId key', () => {
      store.getState().setLiveData('profile', { displayName: 'John' });
      expect(store.getState().liveData['profile']).toEqual({ displayName: 'John' });
    });

    it('merges multiple step data entries without overwriting others', () => {
      store.getState().setLiveData('profile', { displayName: 'John' });
      store.getState().setLiveData('workspace', { name: 'Acme' });
      expect(store.getState().liveData['profile']).toEqual({ displayName: 'John' });
      expect(store.getState().liveData['workspace']).toEqual({ name: 'Acme' });
    });

    it('overwrites existing step data on re-call', () => {
      store.getState().setLiveData('profile', { displayName: 'John' });
      store.getState().setLiveData('profile', { displayName: 'Jane' });
      expect(store.getState().liveData['profile']).toEqual({ displayName: 'Jane' });
    });
  });

  describe('registerFormTrigger', () => {
    it('stores the trigger function', () => {
      const trigger = async () => true;
      store.getState().registerFormTrigger(trigger);
      expect(store.getState().formTrigger).not.toBeNull();
    });

    it('replaces a previously registered trigger', () => {
      const first = async () => true;
      const second = async () => false;
      store.getState().registerFormTrigger(first);
      store.getState().registerFormTrigger(second);
      expect(store.getState().formTrigger).toBe(second);
    });
  });

  describe('clearFormTrigger', () => {
    it('sets formTrigger to null', () => {
      store.getState().registerFormTrigger(async () => true);
      store.getState().clearFormTrigger();
      expect(store.getState().formTrigger).toBeNull();
    });
  });

  describe('reset (extended)', () => {
    it('clears liveData on reset', () => {
      store.getState().setLiveData('profile', { displayName: 'John' });
      store.getState().reset();
      expect(store.getState().liveData).toEqual({});
    });

    it('clears formTrigger on reset', () => {
      store.getState().registerFormTrigger(async () => true);
      store.getState().reset();
      expect(store.getState().formTrigger).toBeNull();
    });
  });

  describe('submissionStatus', () => {
    it('initializes to idle', () => {
      expect(store.getState().submissionStatus).toBe('idle');
    });

    it('transitions to loading via setSubmissionStatus', () => {
      store.getState().setSubmissionStatus('loading');
      expect(store.getState().submissionStatus).toBe('loading');
    });

    it('transitions from loading to success', () => {
      store.getState().setSubmissionStatus('loading');
      store.getState().setSubmissionStatus('success');
      expect(store.getState().submissionStatus).toBe('success');
    });

    it('transitions from loading to error', () => {
      store.getState().setSubmissionStatus('loading');
      store.getState().setSubmissionStatus('error');
      expect(store.getState().submissionStatus).toBe('error');
    });

    it('reset() clears submissionStatus back to idle', () => {
      store.getState().setSubmissionStatus('error');
      store.getState().reset();
      expect(store.getState().submissionStatus).toBe('idle');
    });
  });

  describe('submissionResponse', () => {
    it('initializes to null', () => {
      expect(store.getState().submissionResponse).toBeNull();
    });

    it('stores response via setSubmissionResponse', () => {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const fakeResponse = { workspace: { id: 'ws-1' } } as any;
      store.getState().setSubmissionResponse(fakeResponse);
      expect(store.getState().submissionResponse).toEqual(fakeResponse);
    });

    it('reset() clears submissionResponse to null', () => {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const fakeResponse = { workspace: { id: 'ws-1' } } as any;
      store.getState().setSubmissionResponse(fakeResponse);
      store.getState().reset();
      expect(store.getState().submissionResponse).toBeNull();
    });
  });

  describe('avatar blobs', () => {
    it('profileAvatarBlob initializes to null', () => {
      expect(store.getState().profileAvatarBlob).toBeNull();
    });

    it('workspaceAvatarBlob initializes to null', () => {
      expect(store.getState().workspaceAvatarBlob).toBeNull();
    });

    it('setProfileAvatarBlob stores a Blob', () => {
      const blob = new Blob(['data'], { type: 'image/png' });
      store.getState().setProfileAvatarBlob(blob);
      expect(store.getState().profileAvatarBlob).toBe(blob);
    });

    it('setWorkspaceAvatarBlob stores a Blob', () => {
      const blob = new Blob(['data'], { type: 'image/png' });
      store.getState().setWorkspaceAvatarBlob(blob);
      expect(store.getState().workspaceAvatarBlob).toBe(blob);
    });

    it('setProfileAvatarBlob accepts null', () => {
      const blob = new Blob(['data'], { type: 'image/png' });
      store.getState().setProfileAvatarBlob(blob);
      store.getState().setProfileAvatarBlob(null);
      expect(store.getState().profileAvatarBlob).toBeNull();
    });

    it('reset() clears both avatar blobs to null', () => {
      const blob = new Blob(['data'], { type: 'image/png' });
      store.getState().setProfileAvatarBlob(blob);
      store.getState().setWorkspaceAvatarBlob(blob);
      store.getState().reset();
      expect(store.getState().profileAvatarBlob).toBeNull();
      expect(store.getState().workspaceAvatarBlob).toBeNull();
    });
  });
});
