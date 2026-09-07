plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("dev.detekt")
}

detekt {
    ignoreFailures = true
}

dependencies {
    // --== Aimo ==--
    implementation(project(":aimo-server"))
    implementation(project(":aimo-plugin-ui"))
    implementation(project(":aimo-model-ollama"))

    // Spring-Boot
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
}

tasks.test {
    useJUnitPlatform()
}
