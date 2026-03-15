'use client';

import { useProfile } from '@/components/feature-modules/user/hooks/use-profile';
import {
  createWorkspaceStore,
  type WorkspaceStore,
  type WorkspaceStoreApi,
} from '@/components/feature-modules/workspace/store/workspace.store';
import { createContext, useContext, useEffect, useMemo, useRef, type ReactNode } from 'react';
import { useStore } from 'zustand';

const WorkspacesStoreContext = createContext<WorkspaceStoreApi | undefined>(undefined);

export interface WorkspacesStoreProviderProps {
  children: ReactNode;
}

export const WorkspacesStoreProvider = ({ children }: WorkspacesStoreProviderProps) => {
  const { data: user } = useProfile();
  const storeRef = useRef<WorkspaceStoreApi | null>(null);

  // Compute initial workspace ID from localStorage and user data
  const initialWorkspaceId = useMemo(() => {
    if (!user) return undefined;

    const selectedWorkspaceId = localStorage.getItem('selectedWorkspace');
    if (selectedWorkspaceId) {
      const selectedWorkspace = user.memberships.find(
        (m) => m.workspace?.id === selectedWorkspaceId,
      )?.workspace;

      if (selectedWorkspace) {
        return selectedWorkspace.id;
      }
    }

    // Fall back to first workspace
    return user.memberships[0]?.workspace?.id;
  }, [user]);

  // Create store only once
  if (!storeRef.current) {
    storeRef.current = createWorkspaceStore();
  }

  // Update store when initial workspace ID changes
  useEffect(() => {
    if (initialWorkspaceId && storeRef.current) {
      storeRef.current.setState({
        selectedWorkspaceId: initialWorkspaceId,
      });
    }
  }, [initialWorkspaceId]);

  return (
    <WorkspacesStoreContext.Provider value={storeRef.current}>
      {children}
    </WorkspacesStoreContext.Provider>
  );
};

const useWorkspaceStoreState = <T,>(selector: (store: WorkspaceStore) => T): T => {
  const context = useContext(WorkspacesStoreContext);

  if (!context) {
    throw new Error('useWorkspaceStore must be used within a WorkspacesStoreProvider');
  }

  return useStore(context, selector);
};

export const useWorkspaceStore = <T,>(selector: (store: WorkspaceStore) => T): T => {
  return useWorkspaceStoreState(selector);
};

export const useCurrentWorkspace = () => {
  return useWorkspaceStoreState((store) => store);
};
