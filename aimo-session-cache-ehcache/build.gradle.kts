plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("java-library")
}

dependencies {
    api(project(":aimo-core"))

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.ehcache:ehcache:3.10.8")

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

