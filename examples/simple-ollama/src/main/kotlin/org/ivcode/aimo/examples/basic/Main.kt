package org.ivcode.aimo.examples.basic

import org.ivcode.aimo.core.dao.AimoChatClientDao
import org.ivcode.aimo.core.dao.AimoChatClientDaoMemory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@SpringBootApplication
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}

@Configuration
class SimpleOllamaConfig {

    @Bean
    fun createAimoDao(): AimoChatClientDao {
        return AimoChatClientDaoMemory()
    }
}
