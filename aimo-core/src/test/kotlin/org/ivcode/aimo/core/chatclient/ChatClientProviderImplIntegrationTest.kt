package org.ivcode.aimo.core.chatclient

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import org.ivcode.aimo.core.chatscope.ChatScope
import org.ivcode.aimo.core.chatscope.ChatScopeProvider
import org.ivcode.aimo.core.conversation.Conversation
import org.ivcode.aimo.core.model.AimoChatEngine
import org.ivcode.aimo.core.model.AimoChatMessage
import org.ivcode.aimo.core.model.AimoChatMessageType
import org.ivcode.aimo.core.model.AimoChatModelConfig
import org.ivcode.aimo.core.model.AimoChatOptions
import org.ivcode.aimo.core.model.AimoChatRequest
import org.ivcode.aimo.core.model.AimoChatResponse
import org.ivcode.aimo.core.model.AimoPrompt
import java.time.Instant
import java.util.UUID

class ChatClientProviderImplIntegrationTest {

    @Test
     fun `createClient applies interceptors and invokes aroundChat when chat is called`() {
         val interceptorCalled = mutableListOf<Boolean>()

         val testInterceptor = object : ChatClientInterceptor {
             override fun aroundChat(
                 request: AimoChatRequest,
                 next: (AimoChatRequest) -> AimoChatResponse
             ): AimoChatResponse {
                 interceptorCalled.add(true)
                 // allow call to proceed
                 return next(request)
             }
         }

        // Minimal ChatScope and provider
        val scope = ChatScope(id = "global", displayName = "global", description = "global")
        val scopeProvider = object : ChatScopeProvider {
            override fun getScopes(context: Map<String, Any>): List<ChatScope> = listOf(scope)
            override fun getScope(id: String, context: Map<String, Any>): ChatScope? = if (id == scope.id) scope else null
            override fun getGlobalScope(): ChatScope = scope
        }

        // Minimal conversation implementation
        val convId = UUID.randomUUID()
        val conversation = object : Conversation {
            override val chatId: UUID = convId
            override fun getMessages(maxCacheCharacters: Long?): List<AimoChatMessage>? = null
            override fun addMessages(requestId: UUID, messages: List<AimoChatMessage>, maxCacheCharacters: Long?) = Unit
            override fun getChatMetadata(): Map<String, Any> = emptyMap()
            override fun getChatProperty(property: String): Any? = null
            override fun writeChatProperty(property: String, value: Any) = Unit
            override fun deleteChatProperty(property: String): Boolean = false
        }

        // Minimal engine that returns a deterministic response
        val engine = object : AimoChatEngine {
            override val options: AimoChatOptions = AimoChatOptions()
            override fun call(prompt: AimoPrompt): AimoChatResponse {
                return AimoChatResponse(
                    chatId = convId,
                    responseId = UUID.randomUUID(),
                    messages = listOf(
                        AimoChatMessage(
                            messageId = 1,
                            type = AimoChatMessageType.ASSISTANT,
                            content = "engine-response",
                            thinking = null,
                            toolName = null,
                            done = true
                        )
                    ),
                    createdAt = Instant.now()
                )
            }

            override fun call(prompt: AimoPrompt, callback: (AimoChatResponse) -> Unit): AimoChatResponse {
                // For streaming we simply call the non-streaming and also invoke the callback once
                val resp = call(prompt)
                callback(resp)
                return resp
            }
        }

        val modelConfig = AimoChatModelConfig(name = "test", chatEngine = engine, isPrimary = true)

        val providerFactory = ChatClientProviderImpl(chatScopeProvider = scopeProvider, defaultInterceptors = emptyList())

        // Create client with our test interceptor
        val client = providerFactory.createClient(
            model = modelConfig,
            conversation = conversation,
            scope = null,
            interceptors = listOf(testInterceptor),
            includeDefaultInterceptors = false,
        )

        // Call chat which should trigger the interceptor
        val resp = client.chat(AimoChatRequest(prompt = "hi", context = emptyMap()))

        assertEquals(1, resp.messages.size)
        assertEquals("engine-response", resp.messages[0].content)
        assertTrue(interceptorCalled.isNotEmpty(), "Interceptor should have been invoked")
    }

     @Test
     fun `multiple interceptors execute in registration order`() {
         val callOrder = mutableListOf<String>()

         val int1 = object : ChatClientInterceptor {
             override fun aroundChat(
                 request: AimoChatRequest,
                 next: (AimoChatRequest) -> AimoChatResponse
             ): AimoChatResponse {
                 callOrder.add("int1-before")
                 val result = next(request)
                 callOrder.add("int1-after")
                 return result
             }
         }

         val int2 = object : ChatClientInterceptor {
             override fun aroundChat(
                 request: AimoChatRequest,
                 next: (AimoChatRequest) -> AimoChatResponse
             ): AimoChatResponse {
                 callOrder.add("int2-before")
                 val result = next(request)
                 callOrder.add("int2-after")
                 return result
             }
         }

        val scope = ChatScope(id = "global", displayName = "global", description = "global")
        val scopeProvider = object : ChatScopeProvider {
            override fun getScopes(context: Map<String, Any>): List<ChatScope> = listOf(scope)
            override fun getScope(id: String, context: Map<String, Any>): ChatScope? = if (id == scope.id) scope else null
            override fun getGlobalScope(): ChatScope = scope
        }

        val convId = UUID.randomUUID()
        val conversation = createTestConversation(convId)
        val modelConfig = AimoChatModelConfig(name = "test", chatEngine = createTestEngine(convId), isPrimary = true)
        val providerFactory = ChatClientProviderImpl(chatScopeProvider = scopeProvider, defaultInterceptors = emptyList())

        val client = providerFactory.createClient(
            model = modelConfig,
            conversation = conversation,
            interceptors = listOf(int1, int2),
            includeDefaultInterceptors = false
        )

        client.chat(AimoChatRequest(prompt = "test", context = emptyMap()))

        // Verify order: int1 wraps int2, so int1-before, int2-before, base, int2-after, int1-after
        assertEquals(
            listOf("int1-before", "int2-before", "int2-after", "int1-after"),
            callOrder
        )
    }

     @Test
     fun `interceptor can modify request before passing to next`() {
         var modifiedPromptSeen = false

         val modifyingInterceptor = object : ChatClientInterceptor {
             override fun aroundChat(
                 request: AimoChatRequest,
                 next: (AimoChatRequest) -> AimoChatResponse
             ): AimoChatResponse {
                 // Modify the request to demonstrate interceptor can modify the request
                 val modifiedRequest = request.copy(prompt = request.prompt + " [modified]")
                 return next(modifiedRequest)
             }
         }

         val captureInterceptor = object : ChatClientInterceptor {
             override fun aroundChat(
                 request: AimoChatRequest,
                 next: (AimoChatRequest) -> AimoChatResponse
             ): AimoChatResponse {
                 // Second interceptor verifies request modifications from first
                 modifiedPromptSeen = request.prompt.contains("[modified]")
                 return next(request)
             }
         }

        val scope = ChatScope(id = "global", displayName = "global", description = "global")
        val scopeProvider = object : ChatScopeProvider {
            override fun getScopes(context: Map<String, Any>): List<ChatScope> = listOf(scope)
            override fun getScope(id: String, context: Map<String, Any>): ChatScope? = if (id == scope.id) scope else null
            override fun getGlobalScope(): ChatScope = scope
        }

        val convId = UUID.randomUUID()
        val conversation = createTestConversation(convId)
        val modelConfig = AimoChatModelConfig(name = "test", chatEngine = createTestEngine(convId), isPrimary = true)
        val providerFactory = ChatClientProviderImpl(chatScopeProvider = scopeProvider, defaultInterceptors = emptyList())

        val client = providerFactory.createClient(
            model = modelConfig,
            conversation = conversation,
            interceptors = listOf(modifyingInterceptor, captureInterceptor),
            includeDefaultInterceptors = false
        )

         client.chat(AimoChatRequest(prompt = "hello", context = emptyMap()))

         // Verify first interceptor modified request, and second interceptor saw the modification
         assertTrue(modifiedPromptSeen, "Second interceptor should see request modifications from first interceptor")
     }

     @Test
     fun `exception in interceptor propagates to caller`() {
         val testException = RuntimeException("Interceptor error")

         val errorInterceptor = object : ChatClientInterceptor {
             override fun aroundChat(
                 request: AimoChatRequest,
                 next: (AimoChatRequest) -> AimoChatResponse
             ): AimoChatResponse {
                 throw testException
             }
         }

        val scope = ChatScope(id = "global", displayName = "global", description = "global")
        val scopeProvider = object : ChatScopeProvider {
            override fun getScopes(context: Map<String, Any>): List<ChatScope> = listOf(scope)
            override fun getScope(id: String, context: Map<String, Any>): ChatScope? = if (id == scope.id) scope else null
            override fun getGlobalScope(): ChatScope = scope
        }

        val convId = UUID.randomUUID()
        val conversation = createTestConversation(convId)
        val modelConfig = AimoChatModelConfig(name = "test", chatEngine = createTestEngine(convId), isPrimary = true)
        val providerFactory = ChatClientProviderImpl(chatScopeProvider = scopeProvider, defaultInterceptors = emptyList())

        val client = providerFactory.createClient(
            model = modelConfig,
            conversation = conversation,
            interceptors = listOf(errorInterceptor),
            includeDefaultInterceptors = false
        )

        var caughtException: Exception? = null
        try {
            client.chat(AimoChatRequest(prompt = "test", context = emptyMap()))
        } catch (e: Exception) {
            caughtException = e
        }

        assertEquals(testException, caughtException, "Interceptor exception should propagate to caller")
    }

     @Test
     fun `interceptor wraps streaming callbacks correctly`() {
         val interceptorCallCount = mutableListOf<Int>()
         val callbackInvocations = mutableListOf<String>()

         val trackingInterceptor = object : ChatClientInterceptor {
             override fun aroundChat(
                 request: AimoChatRequest,
                 next: (AimoChatRequest) -> AimoChatResponse
             ): AimoChatResponse {
                 interceptorCallCount.add(1)
                 return next(request)
             }
         }

        val streamingEngine = object : AimoChatEngine {
            override val options: AimoChatOptions = AimoChatOptions()
            override fun call(prompt: AimoPrompt): AimoChatResponse {
                return AimoChatResponse(
                    chatId = UUID.randomUUID(),
                    responseId = UUID.randomUUID(),
                    messages = listOf(
                        AimoChatMessage(
                            messageId = 1,
                            type = AimoChatMessageType.ASSISTANT,
                            content = "streamed",
                            thinking = null,
                            toolName = null,
                            done = true
                        )
                    ),
                    createdAt = Instant.now()
                )
            }

            override fun call(prompt: AimoPrompt, callback: (AimoChatResponse) -> Unit): AimoChatResponse {
                val response = call(prompt)
                callback(response)  // Invoke callback during streaming
                return response
            }
        }

        val scope = ChatScope(id = "global", displayName = "global", description = "global")
        val scopeProvider = object : ChatScopeProvider {
            override fun getScopes(context: Map<String, Any>): List<ChatScope> = listOf(scope)
            override fun getScope(id: String, context: Map<String, Any>): ChatScope? = if (id == scope.id) scope else null
            override fun getGlobalScope(): ChatScope = scope
        }

        val convId = UUID.randomUUID()
        val conversation = createTestConversation(convId)
        val modelConfig = AimoChatModelConfig(name = "test", chatEngine = streamingEngine, isPrimary = true)
        val providerFactory = ChatClientProviderImpl(chatScopeProvider = scopeProvider, defaultInterceptors = emptyList())

        val client = providerFactory.createClient(
            model = modelConfig,
            conversation = conversation,
            interceptors = listOf(trackingInterceptor),
            includeDefaultInterceptors = false
        )

        val response = client.chatStream(
            AimoChatRequest(prompt = "test", context = emptyMap())
        ) { _ ->
            callbackInvocations.add("chunk-received")
        }

        // Verify interceptor was called for streaming
         assertEquals(1, interceptorCallCount.size, "Interceptor should be called once for streaming")
         assertEquals(1, callbackInvocations.size, "Streaming callback should have been invoked")
         assertEquals("streamed", response.messages[0].content)
     }

     @Test
     fun `default interceptors and provided interceptors execute in correct order`() {
         val callOrder = mutableListOf<String>()

         val defaultInt1 = object : ChatClientInterceptor {
             override fun aroundChat(
                 request: AimoChatRequest,
                 next: (AimoChatRequest) -> AimoChatResponse
             ): AimoChatResponse {
                 callOrder.add("default1-before")
                 val result = next(request)
                 callOrder.add("default1-after")
                 return result
             }
         }

         val providedInt1 = object : ChatClientInterceptor {
             override fun aroundChat(
                 request: AimoChatRequest,
                 next: (AimoChatRequest) -> AimoChatResponse
             ): AimoChatResponse {
                 callOrder.add("provided1-before")
                 val result = next(request)
                 callOrder.add("provided1-after")
                 return result
             }
         }

         val scope = ChatScope(id = "global", displayName = "global", description = "global")
         val scopeProvider = object : ChatScopeProvider {
             override fun getScopes(context: Map<String, Any>): List<ChatScope> = listOf(scope)
             override fun getScope(id: String, context: Map<String, Any>): ChatScope? = if (id == scope.id) scope else null
             override fun getGlobalScope(): ChatScope = scope
         }

         val convId = UUID.randomUUID()
         val conversation = createTestConversation(convId)
         val modelConfig = AimoChatModelConfig(name = "test", chatEngine = createTestEngine(convId), isPrimary = true)

         // Create provider with default interceptor
         val providerFactory = ChatClientProviderImpl(
             chatScopeProvider = scopeProvider,
             defaultInterceptors = listOf(defaultInt1)
         )

         val client = providerFactory.createClient(
             model = modelConfig,
             conversation = conversation,
             interceptors = listOf(providedInt1),
             includeDefaultInterceptors = true
         )

         client.chat(AimoChatRequest(prompt = "test", context = emptyMap()))

         // Verify order: provided runs first (outermost), default runs second (innermost)
         // Execution: provided1-before -> default1-before -> core -> default1-after -> provided1-after
         assertEquals(
             listOf("provided1-before", "default1-before", "default1-after", "provided1-after"),
             callOrder
         )
     }

     @Test
     fun `interceptor can access request context and modify if needed`() {
         var capturedContextValue: Any? = null

         val capturingInterceptor = object : ChatClientInterceptor {
             override fun aroundChat(
                 request: AimoChatRequest,
                 next: (AimoChatRequest) -> AimoChatResponse
             ): AimoChatResponse {
                 // Capture the custom context value from request
                 capturedContextValue = request.context["custom-key"]
                 return next(request)
             }
         }

         val scope = ChatScope(id = "global", displayName = "global", description = "global")
         val scopeProvider = object : ChatScopeProvider {
             override fun getScopes(context: Map<String, Any>): List<ChatScope> = listOf(scope)
             override fun getScope(id: String, context: Map<String, Any>): ChatScope? = if (id == scope.id) scope else null
             override fun getGlobalScope(): ChatScope = scope
         }

         val convId = UUID.randomUUID()
         val conversation = createTestConversation(convId)
         val modelConfig = AimoChatModelConfig(name = "test", chatEngine = createTestEngine(convId), isPrimary = true)
         val providerFactory = ChatClientProviderImpl(chatScopeProvider = scopeProvider, defaultInterceptors = emptyList())

         // Create request with custom context values
         val customContextValue = "test-metadata"
         val requestContext = mapOf("custom-key" to customContextValue)

         val client = providerFactory.createClient(
             model = modelConfig,
             conversation = conversation,
             interceptors = listOf(capturingInterceptor),
             includeDefaultInterceptors = false
         )

         // Call chat with context
         client.chat(AimoChatRequest(prompt = "hello", context = requestContext))

         // Verify interceptor received the context value via request.context
         assertEquals(customContextValue, capturedContextValue, "Interceptor should be able to access custom context values from request")
     }

     @Test
     fun `interceptor can build new request with modified context`() {
         var modifiedContextSeen = false

         val modifyingInterceptor = object : ChatClientInterceptor {
             override fun aroundChat(
                 request: AimoChatRequest,
                 next: (AimoChatRequest) -> AimoChatResponse
             ): AimoChatResponse {
                 // Build a new request with modified context
                 val newContext = request.context.toMutableMap()
                 newContext["modified"] = true
                 val modifiedRequest = request.copy(context = newContext)
                 return next(modifiedRequest)
             }
         }

         val captureInterceptor = object : ChatClientInterceptor {
             override fun aroundChat(
                 request: AimoChatRequest,
                 next: (AimoChatRequest) -> AimoChatResponse
             ): AimoChatResponse {
                 // Verify the modification
                 modifiedContextSeen = request.context["modified"] as? Boolean ?: false
                 return next(request)
             }
         }

         val scope = ChatScope(id = "global", displayName = "global", description = "global")
         val scopeProvider = object : ChatScopeProvider {
             override fun getScopes(context: Map<String, Any>): List<ChatScope> = listOf(scope)
             override fun getScope(id: String, context: Map<String, Any>): ChatScope? = if (id == scope.id) scope else null
             override fun getGlobalScope(): ChatScope = scope
         }

         val convId = UUID.randomUUID()
         val conversation = createTestConversation(convId)
         val modelConfig = AimoChatModelConfig(name = "test", chatEngine = createTestEngine(convId), isPrimary = true)
         val providerFactory = ChatClientProviderImpl(chatScopeProvider = scopeProvider, defaultInterceptors = emptyList())

         val client = providerFactory.createClient(
             model = modelConfig,
             conversation = conversation,
             interceptors = listOf(modifyingInterceptor, captureInterceptor),
             includeDefaultInterceptors = false
         )

         client.chat(AimoChatRequest(prompt = "hello", context = emptyMap()))

         // Verify the context was modified and seen by the next interceptor
         assertTrue(modifiedContextSeen, "Second interceptor should see context modifications from first interceptor")
     }

     private fun createTestConversation(convId: UUID): Conversation {
        return object : Conversation {
            override val chatId: UUID = convId
            override fun getMessages(maxCacheCharacters: Long?): List<AimoChatMessage>? = null
            override fun addMessages(requestId: UUID, messages: List<AimoChatMessage>, maxCacheCharacters: Long?) = Unit
            override fun getChatMetadata(): Map<String, Any> = emptyMap()
            override fun getChatProperty(property: String): Any? = null
            override fun writeChatProperty(property: String, value: Any) = Unit
            override fun deleteChatProperty(property: String): Boolean = false
        }
    }

    private fun createTestEngine(convId: UUID): AimoChatEngine {
        return object : AimoChatEngine {
            override val options: AimoChatOptions = AimoChatOptions()
            override fun call(prompt: AimoPrompt): AimoChatResponse {
                return AimoChatResponse(
                    chatId = convId,
                    responseId = UUID.randomUUID(),
                    messages = listOf(
                        AimoChatMessage(
                            messageId = 1,
                            type = AimoChatMessageType.ASSISTANT,
                            content = "engine-response",
                            thinking = null,
                            toolName = null,
                            done = true
                        )
                    ),
                    createdAt = Instant.now()
                )
            }

            override fun call(prompt: AimoPrompt, callback: (AimoChatResponse) -> Unit): AimoChatResponse {
                val resp = call(prompt)
                callback(resp)
                return resp
            }
        }
    }

}
