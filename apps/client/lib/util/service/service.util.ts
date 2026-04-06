import { Session } from "@/lib/auth";
import type { InitOverrideFunction } from "@/lib/types/runtime";
import { isUUID } from "validator";
import { fromError, ResponseError } from "../error/error.util";

export function validateSession(session: Session | null): asserts session is NonNullable<Session> {
    if (!session?.access_token) {
        throw fromError({
            message: "No active session found",
            status: 401,
            error: "NO_SESSION",
        });
    }
}

export function validateUuid(id: string) {
    if (!isUUID(id)) {
        throw fromError({
            message: "Invalid ID format. Expected a UUID.",
            status: 400,
            error: "INVALID_ID",
        });
    }
}

/**
 * Creates an init override that replaces the request body with a plain object,
 * bypassing the generated ToJSON serializers which have infinite mutual
 * recursion for discriminated union types (QueryFilter Or/And variants).
 *
 * Use with any generated `*Raw` API method when the request contains a QueryFilter.
 */
export function withBodyOverride(body: unknown): InitOverrideFunction {
    return async () => ({ body } as RequestInit);
}

export async function handleError(
    response: Response,
    message: (response: Response) => string
): Promise<ResponseError> {
    // Parse server error response
    let errorData;
    try {
        errorData = await response.json();
    } catch {
        errorData = {
            message: message(response),
            status: response.status,
            error: "SERVER_ERROR",
        };
    }
    return fromError(errorData);
}
