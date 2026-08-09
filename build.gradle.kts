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

				// Include module-level documentation files, but only if the file's
				// first non-empty heading is a valid Dokka classifier: either
				// "Module <name>" or "Package <name>". This prevents Dokka's
				// publication tasks from failing on arbitrary README.md files.
				// Look for module-level docs either at the project root or under
				// `src/main/kotlin/**/Module.md` (some modules place them inside
				// the package tree). Normalize to the set of existing files.
				val rootCandidates = listOf("Module.md", "module.md", "packages.md", "Package.md")
					.map { file(it) }
					.filter { it.exists() }

				val srcCandidates = fileTree("src/main/kotlin") {
					include("**/Module.md")
					include("**/module.md")
					include("**/packages.md")
					include("**/Package.md")
				}.files

				val moduleCandidates = (rootCandidates + srcCandidates).distinct()
					.filter { f ->
						try {
							val firstRaw = f.readLines().firstOrNull { it.trim().isNotEmpty() }?.trim() ?: ""
							val first = firstRaw.trimStart('#', ' ', '\t')
							first.startsWith("Module ") || first.startsWith("Package ")
						} catch (e: Exception) {
							false
						}
					}
				if (moduleCandidates.isNotEmpty()) {
					includes.from(moduleCandidates)
				}

				// Discover and include package markdown files under src/main/kotlin for this module.
				// Only include package files whose first heading starts with "Package ".
				val srcRoot = file("src/main/kotlin")
				if (srcRoot.exists()) {
					val pkgFiles = fileTree(srcRoot) {
						include("**/package.md")
						include("**/Package.md")
					}.files

					val validPkgFiles = pkgFiles.filter { f ->
						try {
							val firstRaw = f.readLines().firstOrNull { it.trim().isNotEmpty() }?.trim() ?: ""
							val first = firstRaw.trimStart('#', ' ', '\t')
							first.startsWith("Package ")
						} catch (e: Exception) {
							false
						}
					}

					if (validPkgFiles.isNotEmpty()) {
						includes.from(validPkgFiles)
					}
					val skipped = pkgFiles - validPkgFiles
					if (skipped.isNotEmpty()) {
						logger.lifecycle("Dokka: skipped including ${skipped.size} package.md files that lack a 'Package <name>' header")
					}
				}
			}
		}
	}
}
