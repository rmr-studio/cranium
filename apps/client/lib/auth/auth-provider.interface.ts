/**
 * Provider-agnostic authentication interface.
 * Adapters implement this to normalize different auth backends (Supabase, Auth0, etc.).
 *
 * All methods return Promises for consistent async handling.
 * Auth action methods (signIn, signUp, etc.) throw AuthError on failure.
 *
 * @example
 * ```typescript
 * class SupabaseAuthAdapter implements AuthProvider {
 *     async signIn(credentials: SignInCredentials): Promise<Session> {
 *         // Map Supabase-specific calls to the interface
 *     }
 * }
 * ```
 */

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
} from "./auth.types";

export interface AuthProvider {
    // ========================================================================
    // Session/User Access
    // ========================================================================

    /**
     * Get the current session, or null if not authenticated.
     * May refresh the session if expired.
     */
    getSession(): Promise<Session | null>;

    /**
     * Get the current user, or null if not authenticated.
     */
    getUser(): Promise<User | null>;

    /**
     * Subscribe to authentication state changes.
     * Returns a subscription handle - call unsubscribe() to stop listening.
     */
    onAuthStateChange(
        callback: (event: AuthChangeEvent, session: Session | null) => void
    ): AuthSubscription;

    /**
     * Force-refresh the current session to obtain updated JWT claims.
     * Useful after server-side changes (e.g., workspace membership) that
     * aren't reflected in the current token.
     * @returns The refreshed session, or null if not authenticated
     */
    refreshSession(): Promise<Session | null>;

    // ========================================================================
    // Authentication Actions
    // ========================================================================

    /**
     * Sign in with credentials (password, OTP, or phone).
     * @throws {AuthError} On authentication failure
     */
    signIn(credentials: SignInCredentials): Promise<Session>;

    /**
     * Sign up a new user with email and password.
     * @returns SignUpResult indicating whether user is authenticated or needs confirmation
     * @throws {AuthError} On registration failure (e.g., email taken)
     */
    signUp(credentials: SignUpCredentials, options?: SignUpOptions): Promise<SignUpResult>;

    /**
     * Sign out the current user.
     * @throws {AuthError} On sign out failure
     */
    signOut(): Promise<void>;

    /**
     * Initiate OAuth sign-in flow with a provider.
     * Redirects the user to the provider's authentication page.
     * @throws {AuthError} On OAuth initiation failure
     */
    signInWithOAuth(provider: OAuthProvider, options?: OAuthOptions): Promise<void>;

    /**
     * Verify an OTP code (e.g., for email confirmation or password recovery).
     * @throws {AuthError} On verification failure (e.g., invalid or expired token)
     */
    verifyOtp(params: OtpVerificationParams): Promise<Session>;

    /**
     * Resend an OTP code to the user's email.
     * @throws {AuthError} On resend failure
     */
    resendOtp(params: OtpResendParams): Promise<void>;
}
