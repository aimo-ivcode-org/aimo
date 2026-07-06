pluginManagement {
    plugins {
        kotlin("jvm").version("2.1.21")
        kotlin("plugin.spring").version("2.1.21")
        id("org.springframework.boot").version("4.0.3")
        id("io.spring.dependency-management").version("1.1.7")
        id("org.gradle.toolchains.foojay-resolver-convention").version("0.8.0")
        id("org.jetbrains.dokka").version("2.2.0")
        id("org.jetbrains.dokka-javadoc").version("2.2.0")
        id("org.ivcode.core.gradle-dokka-pages").version("0.1.0-SNAPSHOT")
    }
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven { url = uri("https://s3.us-west-2.amazonaws.com/maven.ivcode.org/snapshot/") }
        maven { url = uri("https://s3.us-west-2.amazonaws.com/maven.ivcode.org/release/") }
    }
}

rootProject.name = "aimo"

// --== Modules ==-- //
include("aimo-core")
include("aimo-mcp-client")
include("aimo-model-ollama")
include("aimo-model-bedrock")
include("aimo-server")
include("aimo-plugin-ui")
include("aimo-ui")

// --== Examples ==-- //
include(":examples:simple-ollama")
include(":examples:simple-bedrock")
// include(":examples:simple-scope-demo")
