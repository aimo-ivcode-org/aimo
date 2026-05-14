pluginManagement {
    plugins {
        kotlin("jvm").version("2.2.21")
        kotlin("plugin.spring").version("2.2.21")
        id("org.springframework.boot").version("4.0.3")
        id("io.spring.dependency-management").version("1.1.7")
        id("org.gradle.toolchains.foojay-resolver-convention").version("0.8.0")
    }
    repositories {
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
        maven { url = uri("https://repo.spring.io/milestone") }
        maven { url = uri("https://repo.spring.io/snapshot") }
        maven { url = uri("https://s3.us-west-2.amazonaws.com/maven.ivcode.org/snapshot/") }
        maven { url = uri("https://s3.us-west-2.amazonaws.com/maven.ivcode.org/release/") }
    }
}

rootProject.name = "aimo"

// --== Modules ==-- //
include("aimo-core")
include("aimo-model-ollama")
include("aimo-model-bedrock")
include("aimo-session-cache-ehcache")
include("aimo-server")
include("aimo-plugin-ui")
include("aimo-ui")

// --== Examples ==-- //
include(":examples:simple-ollama")
include(":examples:simple-bedrock")
