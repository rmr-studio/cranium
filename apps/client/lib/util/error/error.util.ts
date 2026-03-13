import { ResponseError as OpenApiResponseError } from "@/lib/types";

export interface ResponseError extends Error {
    status: number;
    error: string;
    message: string;
    stackTrace?: string;
    details?: unknown;
}

/**
 * Converts an unknown error into a ResponseError with proper formatting
 */
export function fromError(error: unknown): ResponseError {
    const defaultStatus = 500;
    const defaultError = "UNKNOWN_ERROR";
    const defaultMessage = "An unexpected error occurred";

    // Already a ResponseError
    if (isResponseError(error)) {
        return error;
    }

    // Standard Error
    if (error instanceof Error) {
        return Object.assign(new Error(error.message || defaultMessage), {
            name: "ResponseError",
            status: defaultStatus,
            error: defaultError,
            message: error.message || defaultMessage,
            stackTrace: error.stack,
        });
    }

    // Custom object with message
    if (typeof error === "object" && error !== null && "message" in error) {
        const e = error as {
            message?: unknown;
            status?: unknown;
            error?: unknown;
            stackTrace?: unknown;
            [key: string]: unknown;
        };

        const message = String(e.message || defaultMessage);
        const status = Number(e.status) || defaultStatus;
        const errorCode = String(e.error || defaultError);

        return Object.assign(new Error(message), {
            name: "ResponseError",
            status,
            error: errorCode,
            message,
            stackTrace: e.stackTrace ? String(e.stackTrace) : undefined,
            details: error,
        });
    }

    // Object without message - serialize it
    if (typeof error === "object" && error !== null) {
        try {
            const serialized = JSON.stringify(error, null, 2);
            return Object.assign(new Error(serialized), {
                name: "ResponseError",
                status: defaultStatus,
                error: defaultError,
                message: `Error object: ${serialized}`,
                stackTrace: undefined,
                details: error,
            });
        } catch {
            // Fallback if JSON.stringify fails
            return Object.assign(new Error(defaultMessage), {
                name: "ResponseError",
                status: defaultStatus,
                error: defaultError,
                message: `${defaultMessage} (unable to serialize error)`,
                stackTrace: undefined,
                details: error,
            });
        }
    }

    // Primitive error
    return Object.assign(new Error(String(error) || defaultMessage), {
        name: "ResponseError",
        status: defaultStatus,
        error: defaultError,
        message: String(error) || defaultMessage,
        stackTrace: undefined,
    });
}

/**
 * Gets a human-readable string representation of the error
 */
export function formatError(error: ResponseError): string {
    const parts = [`[${error.status}] ${error.error}: ${error.message}`];

    if (error.details) {
        try {
            parts.push(`Details: ${JSON.stringify(error.details, null, 2)}`);
        } catch {
            // Ignore serialization errors
        }
    }

    if (error.stackTrace) {
        parts.push(`Stack: ${error.stackTrace}`);
    }

    return parts.join("\n");
}

/**
 * Discriminates between SaveEntityResponse (has entity/errors array) and
 * ErrorResponse (has error string + message string) from the backend.
 * Used when a 400 response could be either shape.
 */
export function isSaveEntityResponse(body: unknown): boolean {
  if (typeof body !== 'object' || body === null) return false;
  const obj = body as Record<string, unknown>;
  return 'entity' in obj || Array.isArray(obj.errors);
}

export function isResponseError(error: unknown): error is ResponseError {
    return (
        typeof error === "object" &&
        error !== null &&
        "status" in error &&
        "error" in error &&
        "message" in error
    );
}

/**
 * Normalizes OpenAPI client errors to the app's ResponseError format.
 * The backend returns ErrorResponse with { statusCode, error, message, stackTrace }
 */
export async function normalizeApiError(error: unknown): Promise<never> {
    // Handle OpenAPI-generated ResponseError
    if (error instanceof OpenApiResponseError) {
        const response = error.response;

        try {
            const body = await response.json();
            // Map backend ErrorResponse to frontend ResponseError
            throw Object.assign(new Error(body.message), {
                name: "ResponseError",
                status: body.statusCode ?? response.status,
                error: body.error ?? "API_ERROR",
                message: body.message ?? "An unexpected error occurred",
                stackTrace: body.stackTrace,
            }) as ResponseError;
        } catch (parseError) {
            // If JSON parsing fails, create error from response metadata
            if (isResponseError(parseError)) throw parseError;

            throw Object.assign(new Error(response.statusText), {
                name: "ResponseError",
                status: response.status,
                error: "API_ERROR",
                message: response.statusText || "An unexpected error occurred",
            }) as ResponseError;
        }
    }

    // Handle other errors using existing fromError
    throw fromError(error);
}
