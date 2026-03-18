// user/custom.ts
// Custom types for user domain (inlined from OpenAPI operations)

import type { User } from "./models";
import type { SaveUserRequest } from "../models/SaveUserRequest";

// ----- Request Payloads -----

export type { SaveUserRequest };

// updateUserProfile uses SaveUserRequest as request body
export type UpdateUserProfileRequest = SaveUserRequest;

// ----- Response Payloads -----

// All user operations return User type
export type GetCurrentUserResponse = User;
export type GetUserByIdResponse = User;
export type UpdateUserProfileResponse = User;

// ----- Path Parameter Types (inlined from OpenAPI operations) -----

export interface GetUserByIdPathParams {
    userId: string;
}

export interface DeleteUserProfileByIdPathParams {
    userId: string;
}
