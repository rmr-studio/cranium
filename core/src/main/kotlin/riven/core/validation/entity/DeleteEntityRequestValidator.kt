package riven.core.validation.entity

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import riven.core.enums.entity.EntitySelectType
import riven.core.models.request.entity.DeleteEntityRequest

class DeleteEntityRequestValidator : ConstraintValidator<ValidDeleteEntityRequest, DeleteEntityRequest> {

    override fun isValid(request: DeleteEntityRequest, context: ConstraintValidatorContext): Boolean {
        val violations = mutableListOf<String>()

        when (request.type) {
            EntitySelectType.BY_ID -> {
                if (request.entityIds.isNullOrEmpty()) {
                    violations.add("entityIds is required when type is BY_ID")
                }
                if (request.filter != null) {
                    violations.add("filter must not be provided when type is BY_ID")
                }
                if (!request.excludeIds.isNullOrEmpty()) {
                    violations.add("excludeIds must not be provided when type is BY_ID")
                }
                if (request.entityTypeId != null) {
                    violations.add("entityTypeId must not be provided when type is BY_ID")
                }
            }

            EntitySelectType.ALL -> {
                if (request.entityTypeId == null) {
                    violations.add("entityTypeId is required when type is ALL")
                }
                if (request.filter == null) {
                    violations.add("filter is required when type is ALL")
                }
                if (!request.entityIds.isNullOrEmpty()) {
                    violations.add("entityIds must not be provided when type is ALL")
                }
            }
        }

        if (violations.isEmpty()) return true

        context.disableDefaultConstraintViolation()
        violations.forEach { message ->
            context.buildConstraintViolationWithTemplate(message).addConstraintViolation()
        }
        return false
    }
}
