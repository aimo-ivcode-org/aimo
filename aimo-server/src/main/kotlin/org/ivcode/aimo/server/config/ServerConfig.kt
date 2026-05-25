package org.ivcode.aimo.server.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.ivcode.aimo.server.controller.ChatController
import org.ivcode.aimo.server.controller.ConversationController
import org.ivcode.aimo.server.controller.HistoryController
import org.ivcode.aimo.server.service.ChatService
import org.ivcode.aimo.server.service.ConversationService
import org.ivcode.aimo.server.service.HistoryService

@Configuration
@Import(value = [
    ChatController::class,
    ChatService::class,
    HistoryController::class,
    HistoryService::class,
    ConversationController::class,
    ConversationService::class
])
class ServerConfig {


    /** Permissive CORS for local dev (e.g. Vite on another port than the API). */
    @Bean
    fun aimoCorsConfigurer(): WebMvcConfigurer {
        return object : WebMvcConfigurer {
            override fun addCorsMappings(registry: CorsRegistry) {
                registry.addMapping("/**")
                    .allowedOriginPatterns("*")
                    .allowedMethods("*")
                    .allowedHeaders("*")
                    .exposedHeaders("*")
            }
        }
    }
}