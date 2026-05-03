// workspace/custom.ts
// Custom types for workspace domain (inlined from OpenAPI operations)

// Runtime enum re-exports from generated models
export { WorkspacePlan } from "../models/WorkspacePlan";
export { WorkspaceRoles } from "../models/WorkspaceRoles";
export { WorkspaceInviteStatus } from "../models/WorkspaceInviteStatus";
export { DefinitionCategory } from "../models/DefinitionCategory";
export { DefinitionStatus } from "../models/DefinitionStatus";
export { DefinitionSource } from "../models/DefinitionSource";
export { AcquisitionChannel } from "../models/AcquisitionChannel";

import type { Workspace, WorkspaceInvite, WorkspaceRoles } from "./models";

// ----- Custom UI Types -----

/**
 * Extended membership type that includes full Workspace instead of WorkspaceDisplay.
 * Used in dashboard/workspace UI components that need full workspace details.
 */
export interface MembershipDetails {
    workspace?: Workspace;
    role: WorkspaceRoles;
    memberSince: Date;
}

/**
 * Section configuration for workspace tile layout
 */
export interface TileLayoutSection {
    id: string;
    type: string;
    title: string;
    visible: boolean;
    order: number;
    width: number;
    height: number;
    x: number;
    y: number;
}

/**
 * Configuration for workspace tile card display
 */
export interface TileLayoutConfig {
    sections: TileLayoutSection[];
    spacing: number;
    showAvatar: boolean;
    showPlan: boolean;
    showMemberCount: boolean;
    showMemberSince: boolean;
    showRole: boolean;
    showCustomAttributes: boolean;
    showAddress: boolean;
    showPaymentInfo: boolean;
    showBusinessNumber: boolean;
    showTaxId: boolean;
}

// ----- Derived Model Types -----

// Derived type from model property
export type WorkspaceInviteStatusType = WorkspaceInvite["status"];

// ----- Path Parameter Types (inlined from OpenAPI operations) -----

export interface GetWorkspacePathParams {
    workspaceId: string;
}

export interface DeleteWorkspacePathParams {
    workspaceId: string;
}

export interface UpdateMemberRolePathParams {
    workspaceId: string;
    memberId: string;
    role: WorkspaceRoles;
}

export interface InviteToWorkspacePathParams {
    workspaceId: string;
    email: string;
    role: WorkspaceRoles;
}

export interface RejectInvitePathParams {
    inviteToken: string;
}

export interface AcceptInvitePathParams {
    inviteToken: string;
}

export interface GetWorkspaceInvitesPathParams {
    workspaceId: string;
}

export interface RemoveMemberFromWorkspacePathParams {
    workspaceId: string;
    memberId: string;
}

export interface RevokeInvitePathParams {
    workspaceId: string;
    id: string;
}

// ----- Query Parameter Types (inlined from OpenAPI operations) -----

export interface GetWorkspaceQueryParams {
    includeMetadata?: boolean;
}
