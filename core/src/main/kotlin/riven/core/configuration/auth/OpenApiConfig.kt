package cranium.core.configuration.auth

import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.core.jackson.ModelResolver
import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@OpenAPIDefinition(
    info = io.swagger.v3.oas.annotations.info.Info(
        title = "Cranium Core API",
        version = "v1",
        description = "API documentation for Cranium Core services."
    ),
    tags = [
        Tag(name = "workflow", description = "Workflow Metadata, Graph and Execution Management Endpoints"),
        Tag(name = "workspace", description = "Workspace, Workspace Member and Workspace Invite Management Endpoints"),
        Tag(name = "entity", description = "Entity Metadata, Attribute and Type Management Endpoints"),
        Tag(name = "user", description = "User and User Profile Management Endpoints"),
        Tag(name = "block", description = "Block Metadata and Configuration Management Endpoints"),
    ]
)
@Configuration
class OpenApiConfig {

    init {
        ModelResolver.enumsAsRef = true
        ModelConverters.getInstance().addConverter(AnyTypeModelConverter())
    }


    @Bean
    fun customOpenAPI(): OpenAPI {
        return OpenAPI()
            .addSecurityItem(SecurityRequirement().addList("bearerAuth"))
            .components(
                Components()
                    .addSecuritySchemes(
                        "bearerAuth",
                        SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                    )
            )
    }

}