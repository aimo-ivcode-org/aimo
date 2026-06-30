package org.ivcode.aimo.examples.basic

import org.ivcode.aimo.core.dao.AimoChatClientDao
import org.ivcode.aimo.core.dao.AimoChatClientDaoFile
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper
import java.io.File

@SpringBootApplication
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}

@Configuration
class SimpleOllamaConfig {

    @Bean
    fun createAimoDao(
        @Value("\${aimo.data-dir:./data}") dataDirPath: String,
        objectMapper: ObjectMapper
    ): AimoChatClientDao {
        val dataDir = File(dataDirPath)
        return AimoChatClientDaoFile(dataDir, objectMapper)
    }
}
