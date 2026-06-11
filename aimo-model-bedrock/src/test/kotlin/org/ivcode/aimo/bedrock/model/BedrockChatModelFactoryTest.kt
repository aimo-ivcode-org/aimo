package org.ivcode.aimo.bedrock.model

import org.ivcode.aimo.bedrock.BedrockModelProperties
import org.ivcode.aimo.bedrock.BedrockContextProperties
import org.ivcode.aimo.bedrock.PromptCachingStrategy
import org.ivcode.aimo.core.AimoChatMessage
import org.ivcode.aimo.core.AimoChatMessageType
import org.ivcode.aimo.core.model.AimoPrompt
import org.ivcode.aimo.core.model.AimoToolDefinition
import org.ivcode.aimo.bedrock.client.ConverseRequest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

@DisplayName("BedrockChatModelFactory credential normalization and pooling")
class BedrockChatModelFactoryTest {

    @Test
    @DisplayName("throws when only awsAccessKeyId is provided")
    fun throwsWhenOnlyAccessKeyProvided() {
        val properties = mapOf(
            "model-a" to bedrockProps(
                awsAccessKeyId = "AKIA123",
                awsSecretAccessKey = null,
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            BedrockChatModelFactory(properties)
        }
    }

    @Test
    @DisplayName("throws when only awsSecretAccessKey is provided")
    fun throwsWhenOnlySecretProvided() {
        val properties = mapOf(
            "model-a" to bedrockProps(
                awsAccessKeyId = null,
                awsSecretAccessKey = "secret",
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            BedrockChatModelFactory(properties)
        }
    }

    @Test
    @DisplayName("normalizes blank credentials to default chain and reuses one client")
    fun normalizesBlankCredentialsToDefaultChain() {
        val properties = mapOf(
            "model-a" to bedrockProps(awsAccessKeyId = "  ", awsSecretAccessKey = "\t"),
            "model-b" to bedrockProps(awsAccessKeyId = null, awsSecretAccessKey = null),
        )

        val factory = BedrockChatModelFactory(properties)
        assertEquals(1, clientPoolSize(factory))
    }

    @Test
    @DisplayName("different secrets for same region/access key do not share pooled client")
    fun doesNotPoolDifferentSecretsForSameAccessKey() {
        val properties = mapOf(
            "model-a" to bedrockProps(
                awsAccessKeyId = "AKIA123",
                awsSecretAccessKey = "secret-1",
            ),
            "model-b" to bedrockProps(
                awsAccessKeyId = "AKIA123",
                awsSecretAccessKey = "secret-2",
            ),
        )

        val factory = BedrockChatModelFactory(properties)
        assertEquals(2, clientPoolSize(factory))
    }

    @Test
    @DisplayName("trims credentials before pooling identity")
    fun trimsCredentialsBeforePooling() {
        val properties = mapOf(
            "model-a" to bedrockProps(
                awsAccessKeyId = " AKIA123 ",
                awsSecretAccessKey = " secret-1 ",
            ),
            "model-b" to bedrockProps(
                awsAccessKeyId = "AKIA123",
                awsSecretAccessKey = "secret-1",
            ),
        )

        val factory = BedrockChatModelFactory(properties)
        assertEquals(1, clientPoolSize(factory))
    }

    @Test
    @DisplayName("coerces mixed-type stop list to trimmed non-blank strings")
    fun coercesMixedTypeStopListSafely() {
        val properties = mapOf(
            "model-a" to bedrockProps(
                awsAccessKeyId = null,
                awsSecretAccessKey = null,
                options = mapOf(
                    "model" to "test-model",
                    "stop" to listOf("  alpha  ", 42, true, " ", null, "beta"),
                ),
            ),
        )

        val factory = BedrockChatModelFactory(properties)
        val model = assertNotNull(factory.getModel("model-a"))
        assertEquals(listOf("alpha", "42", "true", "beta"), model.options.stopSequences)
    }

    @Test
    @DisplayName("parses comma-delimited stop string and drops blank entries")
    fun parsesCommaDelimitedStopStringSafely() {
        val properties = mapOf(
            "model-a" to bedrockProps(
                awsAccessKeyId = null,
                awsSecretAccessKey = null,
                options = mapOf(
                    "model" to "test-model",
                    "stop" to " first, , second ,third ,, ",
                ),
            ),
        )

        val factory = BedrockChatModelFactory(properties)
        val model = assertNotNull(factory.getModel("model-a"))
        assertEquals(listOf("first", "second", "third"), model.options.stopSequences)
    }

    @Test
    @DisplayName("forwards frequency/presence penalties into additional model request fields")
    fun forwardsPenaltyOptionsIntoAdditionalModelRequestFields() {
        val properties = mapOf(
            "model-a" to bedrockProps(
                awsAccessKeyId = null,
                awsSecretAccessKey = null,
                options = mapOf(
                    "model" to "test-model",
                    "frequencyPenalty" to 0.4,
                    "presencePenalty" to 0.2,
                ),
            ),
        )

        val factory = BedrockChatModelFactory(properties)
        val model = assertNotNull(factory.getModel("model-a"))
        val request = buildRequest(model)

        assertEquals(0.4, request.additionalModelRequestFields?.get("frequency_penalty"))
        assertEquals(0.2, request.additionalModelRequestFields?.get("presence_penalty"))
    }

    @Test
    @DisplayName("does not override explicit penalty fields in additional model request fields")
    fun doesNotOverrideExplicitPenaltyAdditionalFields() {
        val properties = mapOf(
            "model-a" to bedrockProps(
                awsAccessKeyId = null,
                awsSecretAccessKey = null,
                options = mapOf(
                    "model" to "test-model",
                    "frequencyPenalty" to 0.4,
                    "presencePenalty" to 0.2,
                    "additional-model-request-fields" to mapOf(
                        "frequency_penalty" to 0.9,
                        "presence_penalty" to 0.8,
                    ),
                ),
            ),
        )

        val factory = BedrockChatModelFactory(properties)
        val model = assertNotNull(factory.getModel("model-a"))
        val request = buildRequest(model)

        assertEquals(0.9, request.additionalModelRequestFields?.get("frequency_penalty"))
        assertEquals(0.8, request.additionalModelRequestFields?.get("presence_penalty"))
    }

    @Test
    @DisplayName("promptCaching SYSTEM adds only system checkpoint")
    fun promptCachingSystemStrategy() {
        val properties = mapOf(
            "model-a" to bedrockProps(
                awsAccessKeyId = null,
                awsSecretAccessKey = null,
                context = BedrockContextProperties(
                    promptCaching = true,
                    promptCachingStrategy = PromptCachingStrategy.SYSTEM,
                ),
            ),
        )

        val factory = BedrockChatModelFactory(properties)
        val model = assertNotNull(factory.getModel("model-a"))
        val request = buildRequestWithTool(model)

        assertEquals(true, request.cachePointAfterSystem)
        assertEquals(false, request.cachePointAfterTools)
    }

    @Test
    @DisplayName("promptCaching SYSTEM_AND_TOOLS adds system and tools checkpoints")
    fun promptCachingSystemAndToolsStrategy() {
        val properties = mapOf(
            "model-a" to bedrockProps(
                awsAccessKeyId = null,
                awsSecretAccessKey = null,
                context = BedrockContextProperties(
                    promptCaching = true,
                    promptCachingStrategy = PromptCachingStrategy.SYSTEM_AND_TOOLS,
                ),
            ),
        )

        val factory = BedrockChatModelFactory(properties)
        val model = assertNotNull(factory.getModel("model-a"))
        val request = buildRequestWithTool(model)

        assertEquals(true, request.cachePointAfterSystem)
        assertEquals(true, request.cachePointAfterTools)
    }

    private fun bedrockProps(
        awsAccessKeyId: String?,
        awsSecretAccessKey: String?,
        region: String = "us-west-2",
        options: Map<String, Any> = mapOf("model" to "test-model"),
        context: BedrockContextProperties = BedrockContextProperties(),
    ): BedrockModelProperties {
        return BedrockModelProperties(
            region = region,
            context = context,
            options = options,
            awsAccessKeyId = awsAccessKeyId,
            awsSecretAccessKey = awsSecretAccessKey,
        )
    }

    private fun clientPoolSize(factory: BedrockChatModelFactory): Int {
        val field = BedrockChatModelFactory::class.java.getDeclaredField("clients")
        field.isAccessible = true
        val clients = field.get(factory) as Map<*, *>
        return clients.size
    }

    private fun buildRequest(model: org.ivcode.aimo.core.model.AimoChatModelConfig): ConverseRequest {
        val method = model.chatEngine.javaClass.getDeclaredMethod("buildRequest", AimoPrompt::class.java)
        method.isAccessible = true
        val prompt = AimoPrompt(
            messages = listOf(
                AimoChatMessage(
                    messageId = 0,
                    type = AimoChatMessageType.SYSTEM,
                    content = "system",
                    thinking = null,
                    toolName = null,
                    toolCallId = null,
                    toolCalls = null,
                    done = null,
                ),
                AimoChatMessage(
                    messageId = 1,
                    type = AimoChatMessageType.USER,
                    content = "hello",
                    thinking = null,
                    toolName = null,
                    toolCallId = null,
                    toolCalls = null,
                    done = null,
                )
            )
        )
        return method.invoke(model.chatEngine, prompt) as ConverseRequest
    }

    private fun buildRequestWithTool(model: org.ivcode.aimo.core.model.AimoChatModelConfig): ConverseRequest {
        val method = model.chatEngine.javaClass.getDeclaredMethod("buildRequest", AimoPrompt::class.java)
        method.isAccessible = true
        val prompt = AimoPrompt(
            messages = listOf(
                AimoChatMessage(
                    messageId = 0,
                    type = AimoChatMessageType.SYSTEM,
                    content = "system",
                    thinking = null,
                    toolName = null,
                    toolCallId = null,
                    toolCalls = null,
                    done = null,
                ),
                AimoChatMessage(
                    messageId = 1,
                    type = AimoChatMessageType.USER,
                    content = "hello",
                    thinking = null,
                    toolName = null,
                    toolCallId = null,
                    toolCalls = null,
                    done = null,
                )
            ),
            tools = listOf(
                AimoToolDefinition(
                    name = "get_title",
                    description = "Gets title",
                    inputSchema = tools.jackson.module.kotlin.jacksonObjectMapper().createObjectNode().put("type", "object"),
                )
            )
        )
        return method.invoke(model.chatEngine, prompt) as ConverseRequest
    }
}
