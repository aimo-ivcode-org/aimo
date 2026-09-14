package org.ivcode.aimo.server.mcp.handler

import org.ivcode.aimo.server.mcp.annotation.McpContext
import java.lang.reflect.Method
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.kotlinFunction

/**
 * Shared reflection helpers for MCP tool and prompt invocation.
 *
 * Uses Kotlin reflection when available so default parameter values keep working,
 * while still supporting Java reflection fallback for non-Kotlin methods.
 */
internal object MethodInvocationSupport {
    /**
     * Invoke a reflected method with previously bound request values.
     *
     * @param target bean instance that owns the method.
     * @param method reflected method to invoke.
     * @param bindingResult bound values and request context.
     * @return method result.
     * @throws ReflectiveOperationException when reflective invocation fails.
     * @throws IllegalArgumentException when the provided arguments do not match the method signature.
     */
    fun invoke(target: Any, method: Method, bindingResult: ParameterBinder.BindingResult): Any? {
        val kotlinFunction = method.kotlinFunction
        return if (kotlinFunction != null) {
            // Prefer callBy so optional Kotlin parameters can keep their default values.
            kotlinFunction.isAccessible = true
            kotlinFunction.callBy(buildCallByArguments(target, method, kotlinFunction, bindingResult))
        } else {
            // Fall back to declaration-order Java reflection when Kotlin metadata is unavailable.
            val javaArgs = method.parameters.map { parameter -> bindingResult.values[parameter.name] }
            method.invoke(target, *javaArgs.toTypedArray())
        }
    }

    /**
     * Build the argument map required by Kotlin reflection callBy.
     *
     * @param target bean instance that owns the method.
     * @param method reflected method used to identify @McpContext parameters.
     * @param kotlinFunction Kotlin reflection view of the method.
     * @param bindingResult bound values and request context.
     * @return map of Kotlin parameters to invocation values.
     */
    private fun buildCallByArguments(
        target: Any,
        method: Method,
        kotlinFunction: KFunction<*>,
        bindingResult: ParameterBinder.BindingResult
    ): Map<KParameter, Any?> {
        val arguments = mutableMapOf<KParameter, Any?>()
        kotlinFunction.instanceParameter?.let { instanceParameter -> arguments[instanceParameter] = target }

        for (kotlinParameter in kotlinFunction.parameters) {
            if (kotlinParameter != kotlinFunction.instanceParameter) {
                val name = kotlinParameter.name
                if (name != null) {
                    val value = resolveKotlinParameterValue(method, name, bindingResult)
                    if (value.shouldBind) {
                        arguments[kotlinParameter] = value.argument
                    }
                }
            }
        }

        return arguments
    }

    /**
     * Resolve the invocation value for a Kotlin parameter.
     *
     * @param method reflected method used to inspect Java annotations.
     * @param parameterName Kotlin parameter name.
     * @param bindingResult bound request values and context.
     * @return resolved argument wrapper indicating whether a value should be bound.
     */
    private fun resolveKotlinParameterValue(
        method: Method,
        parameterName: String,
        bindingResult: ParameterBinder.BindingResult
    ): ResolvedInvocationArgument {
        if (bindingResult.provided.contains(parameterName)) {
            return ResolvedInvocationArgument(true, bindingResult.values[parameterName])
        }

        val contextParameter = findContextParameter(method, parameterName)
        return if (contextParameter != null) {
            ResolvedInvocationArgument(true, bindingResult.context)
        } else {
            ResolvedInvocationArgument(false, null)
        }
    }

    /**
     * Find a Java parameter that should receive injected request context.
     *
     * @param method reflected method whose parameters are being inspected.
     * @param parameterName Kotlin parameter name being resolved.
     * @return matching Java parameter when it is annotated with @McpContext.
     */
    private fun findContextParameter(method: Method, parameterName: String): java.lang.reflect.Parameter? {
        return method.parameters.firstOrNull { parameter ->
            parameter.name == parameterName && parameter.getAnnotation(McpContext::class.java) != null
        }
    }

    /**
     * Describes whether a Kotlin callBy parameter should be bound.
     *
     * @property shouldBind true when an explicit value should be placed in the callBy map.
     * @property argument resolved invocation argument.
     */
    private data class ResolvedInvocationArgument(
        val shouldBind: Boolean,
        val argument: Any?
    )
}




