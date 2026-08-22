package org.ivcode.aimo.server.mcp.registry

import org.ivcode.aimo.server.mcp.protocol.ToolDefinition
import org.ivcode.aimo.server.mcp.schema.McpSchemaGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.springframework.context.support.GenericApplicationContext
import java.io.File
import java.net.URLClassLoader
import javax.tools.JavaCompiler
import javax.tools.ToolProvider

/**
 * Tests detection of synthetic Java parameter names (e.g., "arg0") by compiling
 * a small Java class at test-time without the -parameters flag and verifying
 * that {@link McpServiceRegistry#detectSyntheticParameterNames} reports the
 * synthetic parameter names.
 */
class SyntheticParameterNamesTest {

    @Test
    fun `detects synthetic java parameter names from dynamically compiled class`() {
        val compiler: JavaCompiler? = ToolProvider.getSystemJavaCompiler()
        // If running on a JRE (no javac available) skip the test rather than fail CI
        assumeTrue(compiler != null, "JDK JavaCompiler not available; skipping test")

        // Use java.nio.Files.createTempDirectory to avoid deprecated kotlin.io.createTempDir
        val tmpDir = java.nio.file.Files.createTempDirectory("synth-param-test").toFile()
        try {
            val source = """
                public class SyntheticParamService {
                    public String foo(String a, int b) { return a + b; }
                }
            """.trimIndent()

            val srcFile = File(tmpDir, "SyntheticParamService.java")
            srcFile.writeText(source)

            // Compile the source without -parameters (default) to ensure parameter
            // names are synthetic (arg0/arg1) in the generated class file.
            val rc = compiler!!.run(null, null, null, "-d", tmpDir.absolutePath, srcFile.absolutePath)
            assertEquals(0, rc, "javac failed with exit code $rc")

            val url = tmpDir.toURI().toURL()
            val loader = URLClassLoader(arrayOf(url), this::class.java.classLoader)
            val clazz = loader.loadClass("SyntheticParamService")
            val instance = clazz.getDeclaredConstructor().newInstance()
            val method = clazz.getMethod("foo", String::class.java, java.lang.Integer.TYPE)

            val registry = McpServiceRegistry(GenericApplicationContext(), McpSchemaGenerator())

            // Build minimal ToolInfo/ManagedMcpService entries that reference the
            // dynamically compiled method so detectSyntheticParameterNames() can
            // inspect the java.lang.reflect.Parameter objects.
            val toolDef = ToolDefinition(name = "foo")
            val toolInfo = ToolInfo(id = "synthetic:foo", beanName = "synthetic", method = method, schema = toolDef)
            val managed = ManagedMcpService(beanName = "synthetic", bean = instance, tools = listOf(toolInfo), prompts = emptyList())

            // Insert into the registry's private services map via reflection
            val servicesField = McpServiceRegistry::class.java.getDeclaredField("services")
            servicesField.isAccessible = true
            val servicesAny = servicesField.get(registry)
            if (servicesAny is MutableMap<*, *>) {
                // Use reflection to avoid unchecked generic cast warnings when inserting into the map
                val put = servicesAny.javaClass.getMethod("put", Any::class.java, Any::class.java)
                put.invoke(servicesAny, "synthetic", managed)
            } else {
                fail("Unable to access services map on McpServiceRegistry")
            }

            val problems = registry.detectSyntheticParameterNames()
            assertTrue(problems.isNotEmpty(), "Expected to detect synthetic parameter names but found none")
            // At least one problem should mention the bean and a parameter index
            assertTrue(problems.any { it.contains("synthetic") && it.contains("parameter at index") })
        } finally {
            tmpDir.deleteRecursively()
        }
    }
}


