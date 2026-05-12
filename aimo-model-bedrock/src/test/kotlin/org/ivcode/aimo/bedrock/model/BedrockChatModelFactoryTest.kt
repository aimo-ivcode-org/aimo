package org.ivcode.aimo.bedrock.model

import org.ivcode.aimo.bedrock.BedrockModelProperties
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
        val model = assertNotNull(factory.createAimoChatModel("model-a"))
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
        val model = assertNotNull(factory.createAimoChatModel("model-a"))
        assertEquals(listOf("first", "second", "third"), model.options.stopSequences)
    }

    private fun bedrockProps(
        awsAccessKeyId: String?,
        awsSecretAccessKey: String?,
        region: String = "us-west-2",
        options: Map<String, Any> = mapOf("model" to "test-model"),
    ): BedrockModelProperties {
        return BedrockModelProperties(
            region = region,
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
}
