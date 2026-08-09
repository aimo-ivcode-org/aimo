plugins {
	kotlin("jvm").apply(false)
	id("io.spring.dependency-management").apply(false)
	id("org.jetbrains.dokka").apply(false)
	id("org.jetbrains.dokka-javadoc").apply(false)
	id("org.ivcode.core.gradle-gh-pages")
}

// GhPages Configuration
ghPages {
	modules {
		"aimo-core" to {
			jacoco { enabled = true }
			javadoc { enabled = true }
			kdoc { enabled = true}
		}

		"aimo-mcp-client" to {
			jacoco { enabled = true }
			javadoc { enabled = true }
			kdoc { enabled = true}
		}

		"aimo-mcp-server" to {
			jacoco { enabled = true }
			javadoc { enabled = true }
			kdoc { enabled = true}
		}

		"aimo-model-bedrock" to {
			jacoco { enabled = true }
			javadoc { enabled = true }
			kdoc { enabled = true}
		}

		"aimo-model-ollama" to {
			jacoco { enabled = true }
			javadoc { enabled = true }
			kdoc { enabled = true}
		}

		"aimo-server" to {
			jacoco { enabled = true }
			javadoc { enabled = true }
			kdoc { enabled = true}
		}

		"aimo-plugin-ui" to {
			javadoc { enabled = true }
			kdoc { enabled = true}
		}
	}
}

group = "org.ivcode"
version = "0.1-SNAPSHOT"

tasks.register("buildAll") {
	description = "Builds all modules and generates all documentation."
    dependsOn("dokkaPages")
}
tasks.register("clean") {
	description = "Cleans all build artifacts from all modules."
    layout.buildDirectory.asFile.get().deleteRecursively()
}

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

	// Use AWS SDK BOM for dependency management
	apply(plugin = "io.spring.dependency-management")

	extensions.configure<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension> {
		imports {
			mavenBom("software.amazon.awssdk:bom:2.31.52")
		}
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

	tasks.register("buildAll") {
		group = "build"
		description = "Builds all modules and generates all documentation."
	}

	// Configure Java toolchain for subprojects that apply the Java plugin.
	// This will only run in projects that actually apply the 'java' plugin,
	// so projects that don't apply it will be skipped.
	pluginManager.withPlugin("java") {
		tasks.named("buildAll") {
			dependsOn("build")
		}

		extensions.configure(org.gradle.api.plugins.JavaPluginExtension::class.java) {
			withSourcesJar()
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
		pluginManager.apply("org.jetbrains.dokka")
		pluginManager.apply("org.jetbrains.dokka-javadoc")
		pluginManager.apply("jacoco")

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

	pluginManager.withPlugin("org.jetbrains.dokka") {
		extensions.configure<org.jetbrains.dokka.gradle.DokkaExtension> {
			dokkaSourceSets.configureEach {
				val modulePath = project.projectDir
					.relativeTo(rootDir)
					.invariantSeparatorsPath

				sourceLink {
					// local source directory
					localDirectory.set(file("src/main/kotlin"))

					// GitHub remote URL
					remoteUrl.set(
						uri("https://github.com/aimo-ivcode-org/aimo/blob/main/${modulePath}/src/main/kotlin/")
					)

					// maps line numbers to GitHub
					remoteLineSuffix.set("#L")
				}
			}
		}
	}
}
