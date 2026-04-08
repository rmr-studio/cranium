/**
 * Supabase implementation of the AuthProvider interface.
 * Translates between Supabase SDK and domain types.
 */

import { createClient, SupabaseClient } from "@supabase/supabase-js";
import { AuthProvider } from "../../auth-provider.interface";
import {
    Session,
    User,
    SignInCredentials,
    SignUpCredentials,
    SignUpOptions,
    SignUpResult,
    OAuthProvider,
    OAuthOptions,
    OtpVerificationParams,
    OtpResendParams,
    AuthChangeEvent,
    AuthSubscription,
} from "../../auth.types";
import { AuthError, AuthErrorCode } from "../../auth-error";
import { mapSupabaseSession, mapSupabaseUser, mapAuthChangeEvent } from "./mappers";
import { mapSupabaseError } from "./error-mapper";

/**
 * SupabaseAuthAdapter implements AuthProvider using Supabase as the backend.
 *
 * Creates and manages its own Supabase client internally.
 * Fails fast on construction if required environment variables are missing.
 *
 * @example
 * ```typescript
 * const adapter = new SupabaseAuthAdapter();
 * const session = await adapter.signIn({ type: "password", email, password });
 * ```
 */
export class SupabaseAuthAdapter implements AuthProvider {
    private readonly client: SupabaseClient;

    constructor() {
        const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
        const key = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;

        if (!url || !key) {
            throw new Error(
                "Missing required environment variables: NEXT_PUBLIC_SUPABASE_URL and NEXT_PUBLIC_SUPABASE_ANON_KEY"
            );
        }

        this.client = createClient(url, key, {
            auth: {
                autoRefreshToken: true,
                persistSession: true,
            },
        });
    }

    /**
     * Expose underlying Supabase client for escape hatches.
     * Use sparingly - prefer interface methods.
     */
    get supabaseClient(): SupabaseClient {
        return this.client;
    }

    // ========================================================================
    // Session/User Access
    // ========================================================================

    /**
     * Get the current session, or null if not authenticated.
     * May refresh the session if expired.
     */
    async getSession(): Promise<Session | null> {
        const { data, error } = await this.client.auth.getSession();

        if (error) {
            throw mapSupabaseError(error);
        }

        if (!data.session) {
            return null;
        }

        return mapSupabaseSession(data.session);
    }

    /**
     * Get the current user, or null if not authenticated.
     */
    async getUser(): Promise<User | null> {
        const { data, error } = await this.client.auth.getUser();

        if (error) {
            // getUser returns error for unauthenticated state - treat as null
            return null;
        }

        if (!data.user) {
            return null;
        }

        return mapSupabaseUser(data.user);
    }

    /**
     * Subscribe to authentication state changes.
     * Returns a subscription handle - call unsubscribe() to stop listening.
     */
    onAuthStateChange(
        callback: (event: AuthChangeEvent, session: Session | null) => void
    ): AuthSubscription {
        const { data } = this.client.auth.onAuthStateChange((supabaseEvent, supabaseSession) => {
            // Map Supabase event to domain event (filter unsupported)
            const domainEvent = mapAuthChangeEvent(supabaseEvent);
            if (!domainEvent) {
                return
            }
            
            // INITIAL_SESSION handles authentication state on page load
            if(domainEvent === 'INITIAL_SESSION'){
            
                if(!supabaseSession){
                    callback(domainEvent, null)
                    return
                }

                const session = mapSupabaseSession(supabaseSession);
                callback(domainEvent, session)

                return
            }


            const session = supabaseSession ? mapSupabaseSession(supabaseSession) : null;
            callback(domainEvent, session);
        });

        return { unsubscribe: data.subscription.unsubscribe };
    }

    /**
     * Force-refresh the current session to obtain updated JWT claims.
     */
    async refreshSession(): Promise<Session | null> {
        const { data, error } = await this.client.auth.refreshSession();

        if (error) {
            throw mapSupabaseError(error);
        }

        if (!data.session) {
            return null;
        }

        return mapSupabaseSession(data.session);
    }

    // ========================================================================
    // Authentication Actions
    // ========================================================================

    /**
     * Sign in with credentials (password, OTP, or phone).
     * @throws {AuthError} On authentication failure
     */
    async signIn(credentials: SignInCredentials): Promise<Session> {
        let result;

        switch (credentials.type) {
            case "password":
                result = await this.client.auth.signInWithPassword({
                    email: credentials.email,
                    password: credentials.password,
                });
                break;
            case "otp":
                result = await this.client.auth.verifyOtp({
                    email: credentials.email,
                    token: credentials.token,
                    type: "email",
                });
                break;
            case "phone":
                result = await this.client.auth.signInWithPassword({
                    phone: credentials.phone,
                    password: credentials.password,
                });
                break;
            default:
                // Exhaustive check - TypeScript will error if a case is missing
                const _exhaustive: never = credentials;
                throw new Error(`Unhandled credential type: ${_exhaustive}`);
        }

        if (result.error) {
            throw mapSupabaseError(result.error);
        }

        if (!result.data.session) {
            throw new AuthError(
                "Authentication succeeded but no session was returned",
                AuthErrorCode.UNKNOWN_ERROR
            );
        }

        return mapSupabaseSession(result.data.session);
    }

    /**
     * Sign up a new user with email and password.
     * @returns SignUpResult indicating whether user is authenticated or needs confirmation
     * @throws {AuthError} On registration failure (e.g., email taken)
     */
    async signUp(
        credentials: SignUpCredentials,
        options?: SignUpOptions
    ): Promise<SignUpResult> {
        const confirmationType = options?.confirmationType ?? "link";

        const { data, error } = await this.client.auth.signUp({
            email: credentials.email,
            password: credentials.password,
        });

        if (error) {
            throw mapSupabaseError(error);
        }

        // Session returned means email confirmation is disabled or user is auto-confirmed
        if (data.session) {
            return {
                status: "authenticated",
                session: mapSupabaseSession(data.session),
            };
        }

        // No session means confirmation is required.
        // For OTP flow: Supabase sends OTP code via email
        // For link flow: Supabase sends confirmation link via email
        // Note: If user already exists (obfuscated), Supabase sends no email
        // but we respond identically to prevent user enumeration.
        return {
            status: "confirmation_required",
            confirmationType,
        };
    }

    /**
     * Sign out the current user.
     * @throws {AuthError} On sign out failure
     */
    async signOut(): Promise<void> {
        const { error } = await this.client.auth.signOut();

        if (error) {
            throw mapSupabaseError(error);
        }
    }

    /**
     * Initiate OAuth sign-in flow with a provider.
     * Redirects the user to the provider's authentication page.
     * @throws {AuthError} On OAuth initiation failure
     */
    async signInWithOAuth(provider: OAuthProvider, options?: OAuthOptions): Promise<void> {
        const { data, error } = await this.client.auth.signInWithOAuth({
            provider,
            options: {
                redirectTo: options?.redirectTo,
                scopes: options?.scopes?.join(" "),
            },
        });

        if (error) {
            throw mapSupabaseError(error);
        }

        // Redirect to OAuth provider
        if (data.url) {
            window.location.href = data.url;
        }
    }

    /**
     * Verify an OTP code (e.g., for email confirmation or password recovery).
     * @throws {AuthError} On verification failure (e.g., invalid or expired token)
     */
    async verifyOtp(params: OtpVerificationParams): Promise<Session> {
        const { data, error } = await this.client.auth.verifyOtp({
            email: params.email,
            token: params.token,
            type: params.type,
        });

        if (error) {
            throw mapSupabaseError(error);
        }

        if (!data.session) {
            throw new AuthError(
                "OTP verification succeeded but no session was returned",
                AuthErrorCode.UNKNOWN_ERROR
            );
        }

        return mapSupabaseSession(data.session);
    }

    /**
     * Resend an OTP code to the user's email.
     * @throws {AuthError} On resend failure
     */
    async resendOtp(params: OtpResendParams): Promise<void> {
        // Supabase uses different methods for different OTP types
        if (params.type === "recovery") {
            // Password recovery uses resetPasswordForEmail
            const { error } = await this.client.auth.resetPasswordForEmail(params.email);
            if (error) {
                throw mapSupabaseError(error);
            }
            return;
        }

        // For signup and email_change, use the standard resend method
        const { error } = await this.client.auth.resend({
            type: params.type,
            email: params.email,
        });

        if (error) {
            throw mapSupabaseError(error);
        }
    }
}
