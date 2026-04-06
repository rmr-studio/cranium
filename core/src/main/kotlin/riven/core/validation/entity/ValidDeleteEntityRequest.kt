package riven.core.validation.entity

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [DeleteEntityRequestValidator::class])
annotation class ValidDeleteEntityRequest(
    val message: String = "Invalid bulk delete request",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)
