package cranium.core.models.workflow.node

import kotlin.reflect.KClass

/**
 * Generic service provider interface for workflow node execution.
 *
 * During runtime, nodes need access to Spring-managed services to perform
 * execution operations (entity CRUD, HTTP requests, expression evaluation, etc.).
 * Instead of passing a concrete class with all possible services, this interface
 * allows nodes to request only the specific services they need.
 *
 * ## Benefits
 *
 * - **Scalability**: Provider never needs modification as new services are added
 * - **Explicit dependencies**: Each node's execute() shows exactly what services it uses
 * - **Testability**: Easy to mock a single interface in unit tests
 * - **Type safety**: Uses KClass<T> for compile-time type checking
 *
 * ## Usage in Node Configs
 *
 * ```kotlin
 * override fun execute(
 *     dataStore: WorkflowDataStore,
 *     inputs: JsonObject,
 *     services: NodeServiceProvider
 * ): NodeOutput {
 *     // Request only the services this node needs
 *     val entityService = services.service<EntityService>()
 *     val result = entityService.saveEntity(...)
 *     return CreateEntityOutput(entityId = result.id, ...)
 * }
 * ```
 *
 * ## Implementation
 *
 * The Spring implementation ([cranium.core.service.workflow.WorkflowNodeServiceInjectionProvider])
 * wraps ApplicationContext to provide service resolution.
 *
 * @see cranium.core.service.workflow.WorkflowNodeServiceInjectionProvider
 */
interface NodeServiceProvider {
    /**
     * Retrieves a Spring-managed service by its class.
     *
     * @param T The service type to retrieve
     * @param serviceClass The KClass of the service to retrieve
     * @return The requested service instance
     * @throws org.springframework.beans.factory.NoSuchBeanDefinitionException if service not found
     */
    fun <T : Any> get(serviceClass: KClass<T>): T
}

/**
 * Inline reified extension for cleaner service access syntax.
 *
 * Usage:
 * ```kotlin
 * val entityService = services.service<EntityService>()
 * ```
 */
inline fun <reified T : Any> NodeServiceProvider.service(): T = get(T::class)
