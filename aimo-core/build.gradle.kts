plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("java-library")
    id("dev.detekt")
}

detekt {
    ignoreFailures = true
}

dependencies {
    api("org.jetbrains.kotlin:kotlin-reflect")

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("tools.jackson.module:jackson-module-kotlin")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.test {
    useJUnitPlatform()
}


// This is a library module. The Spring Boot plugin (if applied) creates a bootJar
// task which requires a main class to be present. Disable the bootJar so the
// module produces a normal library JAR and the build no longer expects a main.
// Keep the regular 'jar' task enabled.
tasks.named("bootJar") {
    enabled = false
}
tasks.named("jar") {
    enabled = true
}

