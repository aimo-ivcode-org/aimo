plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("java-library")
}

dependencies {
    api(project(":aimo-core"))
    api(project(":aimo-server"))
    api(project(":aimo-ui"))

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("tools.jackson.module:jackson-module-kotlin")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
}

tasks.test {
    useJUnitPlatform()
}

tasks.named("bootJar") {
    enabled = false
}
tasks.named("jar") {
    enabled = true
}