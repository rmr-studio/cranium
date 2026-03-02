package riven.core.service.analytics

import java.util.UUID

interface PostHogService {

    /** Capture an analytics event associated with a user and workspace. */
    fun capture(userId: UUID, workspaceId: UUID, event: String, properties: Map<String, Any> = emptyMap())

    /** Identify a user with optional profile properties. */
    fun identify(userId: UUID, properties: Map<String, Any> = emptyMap())

    /** Send workspace group properties to PostHog for group analytics. */
    fun groupIdentify(userId: UUID, workspaceId: UUID, properties: Map<String, Any> = emptyMap())
}
