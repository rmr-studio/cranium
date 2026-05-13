package cranium.core.service.workflow

import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Service
import cranium.core.models.workflow.node.NodeServiceProvider
import kotlin.reflect.KClass

/**
 * Spring implementation of [NodeServiceProvider].
 *
 * Wraps Spring's [ApplicationContext] to provide on-demand service resolution
 * for workflow node execution. This allows nodes to request only the specific
 * services they need without requiring a monolithic service registry.
 *
 * ## Thread Safety
 *
 * This service is thread-safe as it delegates to Spring's ApplicationContext,
 * which handles concurrent bean access safely.
 *
 * ## Error Handling
 *
 * If a requested service is not found, Spring's [org.springframework.beans.factory.NoSuchBeanDefinitionException]
 * is thrown with a clear message indicating which service was requested.
 *
 * @property applicationContext Spring's application context for bean resolution
 */
@Service
class WorkflowNodeServiceInjectionProvider(
    private val applicationContext: ApplicationContext
) : NodeServiceProvider {

    /**
     * Retrieves a Spring-managed service by its class.
     *
     * @param T The service type to retrieve
     * @param serviceClass The KClass of the service to retrieve
     * @return The requested service instance
     * @throws org.springframework.beans.factory.NoSuchBeanDefinitionException if service not found
     */
    override fun <T : Any> get(serviceClass: KClass<T>): T =
        applicationContext.getBean(serviceClass.java)
}
