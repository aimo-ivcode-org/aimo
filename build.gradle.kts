plugins {
	id("io.spring.dependency-management").version("1.1.7").apply(false)
}

group = "org.ivcode"
version = "0.1-SNAPSHOT"

subprojects {

	// Ensure all subprojects inherit the root group and version so they
	// consistently use the same coordinates when publishing and resolving
	// dependency metadata.
	group = rootProject.group
	version = rootProject.version

	// Provide repository configuration to all subprojects so they don't need
	// to declare repositories locally. Prefer mavenLocal() for fast local
	// iteration, then mavenCentral.
	repositories {
		mavenLocal()
		mavenCentral()
		maven { url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/") }
	}

	// Centralized dependency versions. Subprojects can declare dependencies
	// without a version (e.g. implementation("group:artifact")) and the version
	// will be resolved from the table below.
	configurations.configureEach {
		resolutionStrategy.eachDependency {
			when ("${requested.group}:${requested.name}") {
				// add entries here:
				"org.springdoc:springdoc-openapi-starter-webmvc-ui" -> useVersion("3.0.2")
			}
		}
	}

	// Configure Java toolchain for subprojects that apply the Java plugin.
	// This will only run in projects that actually apply the 'java' plugin,
	// so projects that don't apply it will be skipped.
	pluginManager.withPlugin("java") {
		extensions.configure(org.gradle.api.plugins.JavaPluginExtension::class.java) {
			toolchain {
				languageVersion.set(org.gradle.jvm.toolchain.JavaLanguageVersion.of(21))
			}
		}
	}

	// Configure Kotlin JVM toolchain for subprojects that apply the Kotlin JVM plugin.
	// Use reflection so the root build script does not need a compile-time
	// dependency on the Kotlin Gradle plugin classes (which would cause
	// unresolved reference errors when compiling the root script).
	pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
		val kotlinExt = extensions.findByName("kotlin")
		if (kotlinExt != null) {
			try {
				val method = kotlinExt.javaClass.methods.firstOrNull { it.name == "jvmToolchain" && it.parameterCount == 1 }
				method?.invoke(kotlinExt, Integer.valueOf(21))
			} catch (ex: Exception) {
				// If reflection fails for any reason, ignore and continue. Subproject
				// will still compile with its own defaults or local configuration.
			}
		}
	}


}
