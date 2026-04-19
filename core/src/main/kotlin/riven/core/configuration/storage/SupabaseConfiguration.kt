package riven.core.configuration.storage

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.storage.Storage
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import riven.core.configuration.properties.ApplicationConfigurationProperties

@Configuration
@ConditionalOnProperty(name = ["storage.provider"], havingValue = "supabase")
class SupabaseConfiguration(private val config: ApplicationConfigurationProperties) {
    @Bean
    fun supabaseClient(): SupabaseClient {
        return createSupabaseClient(
            supabaseUrl = config.supabaseUrl,
            supabaseKey = config.supabaseKey
        ) {
            install(Storage)
        }
    }
}
