package org.ivcode.aimo.ui.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer


@Configuration
class WebConfig : WebMvcConfigurer {
    override fun addViewControllers(registry: ViewControllerRegistry) {
        // Forward the root URL to the static index.html
        registry.addViewController("/")
            .setViewName("forward:index.html")
    }
}
