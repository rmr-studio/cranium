package cranium.core.service.util

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import cranium.core.configuration.auth.WorkspaceSecurity

@Configuration
@EnableMethodSecurity(prePostEnabled = true)
@Import(WorkspaceSecurity::class)
class SecurityTestConfig
