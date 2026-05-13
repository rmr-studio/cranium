package cranium.core.configuration.util

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.InjectionPoint
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Scope

@Configuration
class LoggerConfig {

    /**
     * Instantiates a Kotlin logger for the class where this bean is injected, with the origin source
     * referencing the origin component injecting the logger.
     */
    @Bean
    @Scope("prototype")
    fun kotlinLogging(source: InjectionPoint): KLogger {
        return KotlinLogging.logger(
            source.methodParameter?.containingClass?.name
                ?: source.field?.declaringClass?.name
                ?: throw IllegalArgumentException("Cannot resolve logger class")
        )
    }
}