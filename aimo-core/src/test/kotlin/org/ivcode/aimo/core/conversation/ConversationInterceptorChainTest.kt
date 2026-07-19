package org.ivcode.aimo.core.conversation

import org.ivcode.aimo.core.model.AimoChatMessage
import org.ivcode.aimo.core.model.AimoChatMessageType
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Tests for ConversationInterceptor chain of responsibility pattern.
 *
 * Verifies:
 * - Interceptors execute in registration order
 * - Metadata propagates through chain
 * - Interceptors can modify metadata
 * - ChatId is properly passed to interceptors
 */
class ConversationInterceptorChainTest {

    @Test
    fun `single interceptor executes and calls proceed`() {
        val callLog = mutableListOf<String>()
        val chatId = UUID.randomUUID()

        val interceptor = LoggingInterceptor("test", callLog)
        val metadata = mutableMapOf<String, Any>()

        val result = buildAndExecuteChain(listOf(interceptor), chatId, metadata) { _, _ ->
            callLog.add("final-action")
            null
        }

        assertEquals(2, callLog.size)
        assertEquals("test", callLog[0], "Interceptor should execute first")
        assertEquals("final-action", callLog[1], "Final action should execute last")
        assertNull(result)
    }

    @Test
    fun `multiple interceptors execute in order`() {
        val callLog = mutableListOf<String>()
        val chatId = UUID.randomUUID()

        val interceptor1 = LoggingInterceptor("first", callLog)
        val interceptor2 = LoggingInterceptor("second", callLog)
        val interceptor3 = LoggingInterceptor("third", callLog)
        val metadata = mutableMapOf<String, Any>()

        buildAndExecuteChain(listOf(interceptor1, interceptor2, interceptor3), chatId, metadata) { _, _ ->
            callLog.add("final-action")
            null
        }

        assertEquals(4, callLog.size)
        assertEquals("first", callLog[0])
        assertEquals("second", callLog[1])
        assertEquals("third", callLog[2])
        assertEquals("final-action", callLog[3])
    }

    @Test
    fun `interceptors can modify metadata`() {
        val callLog = mutableListOf<String>()
        val chatId = UUID.randomUUID()

        val modifyingInterceptor = object : ConversationInterceptor {
            override fun interceptGet(chain: ConversationInterceptor.GetChain, chatId: UUID, metadata: MutableMap<String, Any>): Conversation? {
                metadata["modified"] = true
                callLog.add("modified")
                return chain.proceed(chatId, metadata)
            }

            override fun interceptDelete(chain: ConversationInterceptor.DeleteChain, chatId: UUID, metadata: MutableMap<String, Any>): Boolean {
                metadata["modified"] = true
                return chain.proceed(chatId, metadata)
            }
        }

        val readingInterceptor = object : ConversationInterceptor {
            override fun interceptGet(chain: ConversationInterceptor.GetChain, chatId: UUID, metadata: MutableMap<String, Any>): Conversation? {
                val value = metadata["modified"]
                callLog.add("read:$value")
                return chain.proceed(chatId, metadata)
            }

            override fun interceptDelete(chain: ConversationInterceptor.DeleteChain, chatId: UUID, metadata: MutableMap<String, Any>): Boolean {
                return chain.proceed(chatId, metadata)
            }
        }

        val metadata = mutableMapOf<String, Any>()

        buildAndExecuteChain(listOf(modifyingInterceptor, readingInterceptor), chatId, metadata) { _, md ->
            assertEquals(true, md["modified"], "Metadata modification should be visible in final action")
            null
        }

        assertTrue(callLog.contains("modified"))
        assertTrue(callLog.contains("read:true"))
    }

    @Test
    fun `getMessages operation has correct metadata keys`() {
        var capturedMetadata: MutableMap<String, Any>? = null
        val chatId = UUID.randomUUID()

        val capturingInterceptor = object : ConversationInterceptor {
            override fun interceptGet(chain: ConversationInterceptor.GetChain, chatId: UUID, metadata: MutableMap<String, Any>): Conversation? {
                capturedMetadata = metadata
                return chain.proceed(chatId, metadata)
            }

            override fun interceptDelete(chain: ConversationInterceptor.DeleteChain, chatId: UUID, metadata: MutableMap<String, Any>): Boolean {
                return chain.proceed(chatId, metadata)
            }
        }

        val metadata = mutableMapOf<String, Any>(
            "maxCacheCharacters" to 1000L
        )

        buildAndExecuteChain(listOf(capturingInterceptor), chatId, metadata) { _, _ -> null }

        assertNotNull(capturedMetadata)
        assertEquals(1000L, capturedMetadata!!["maxCacheCharacters"])
    }

    @Test
    fun `addMessages operation has correct metadata keys`() {
        var capturedMetadata: MutableMap<String, Any>? = null
        var capturedChatId: UUID? = null
        val chatId = UUID.randomUUID()

        val capturingInterceptor = object : ConversationInterceptor {
            override fun interceptGet(chain: ConversationInterceptor.GetChain, cid: UUID, metadata: MutableMap<String, Any>): Conversation? {
                capturedChatId = cid
                capturedMetadata = metadata
                return chain.proceed(cid, metadata)
            }

            override fun interceptDelete(chain: ConversationInterceptor.DeleteChain, cid: UUID, metadata: MutableMap<String, Any>): Boolean {
                return chain.proceed(cid, metadata)
            }
        }

        val requestId = UUID.randomUUID()
        val messages = listOf(
            AimoChatMessage(
                messageId = 1,
                type = AimoChatMessageType.USER,
                content = "test",
                thinking = null,
                toolName = null,
                done = true
            )
        )
        val metadata = mutableMapOf<String, Any>(
            "requestId" to requestId,
            "messages" to messages
        )

        buildAndExecuteChain(listOf(capturingInterceptor), chatId, metadata) { _, _ -> null }

        assertNotNull(capturedChatId)
        assertNotNull(capturedMetadata)
        assertEquals(chatId, capturedChatId)
        assertEquals(requestId, capturedMetadata!!["requestId"])
        assertEquals(messages, capturedMetadata!!["messages"])
    }

    @Test
    fun `writeChatProperty operation has correct metadata keys`() {
        var capturedMetadata: MutableMap<String, Any>? = null
        val chatId = UUID.randomUUID()

        val capturingInterceptor = object : ConversationInterceptor {
            override fun interceptGet(chain: ConversationInterceptor.GetChain, cid: UUID, metadata: MutableMap<String, Any>): Conversation? {
                capturedMetadata = metadata
                return chain.proceed(cid, metadata)
            }

            override fun interceptDelete(chain: ConversationInterceptor.DeleteChain, cid: UUID, metadata: MutableMap<String, Any>): Boolean {
                return chain.proceed(cid, metadata)
            }
        }

        val metadata = mutableMapOf<String, Any>(
            "property" to "testKey",
            "value" to "testValue"
        )

        buildAndExecuteChain(listOf(capturingInterceptor), chatId, metadata) { _, _ -> null }

        assertNotNull(capturedMetadata)
        assertEquals("testKey", capturedMetadata!!["property"])
        assertEquals("testValue", capturedMetadata!!["value"])
    }

    @Test
    fun `interceptors can short-circuit the chain`() {
        val callLog = mutableListOf<String>()
        val chatId = UUID.randomUUID()

        val shortCircuitInterceptor = object : ConversationInterceptor {
            override fun interceptGet(chain: ConversationInterceptor.GetChain, cid: UUID, metadata: MutableMap<String, Any>): Conversation? {
                callLog.add("short-circuit")
                // Short-circuit by returning null (conversation not found)
                return null
            }

            override fun interceptDelete(chain: ConversationInterceptor.DeleteChain, cid: UUID, metadata: MutableMap<String, Any>): Boolean {
                callLog.add("short-circuit-delete")
                // Short-circuit by returning false
                return false
            }
        }

        val neverCalledInterceptor = LoggingInterceptor("never-called", callLog)

        val metadata = mutableMapOf<String, Any>()

        val result = buildAndExecuteChain(listOf(shortCircuitInterceptor, neverCalledInterceptor), chatId, metadata) { _, _ ->
            callLog.add("final-action")
            null
        }

        assertEquals(1, callLog.size)
        assertEquals("short-circuit", callLog[0])
        assertNull(result)
    }

    @Test
    fun `empty interceptor list executes final action directly`() {
        val callLog = mutableListOf<String>()
        val chatId = UUID.randomUUID()
        val metadata = mutableMapOf<String, Any>()

        buildAndExecuteChain(emptyList(), chatId, metadata) { _, _ ->
            callLog.add("final-action")
            null
        }

        assertEquals(1, callLog.size)
        assertEquals("final-action", callLog[0])
    }

    @Test
    fun `interceptors can return nullable results`() {
        val chatId = UUID.randomUUID()
        val interceptor = object : ConversationInterceptor {
            override fun interceptGet(chain: ConversationInterceptor.GetChain, cid: UUID, metadata: MutableMap<String, Any>): Conversation? {
                return chain.proceed(cid, metadata)
            }

            override fun interceptDelete(chain: ConversationInterceptor.DeleteChain, cid: UUID, metadata: MutableMap<String, Any>): Boolean {
                return chain.proceed(cid, metadata)
            }
        }

        val metadata = mutableMapOf<String, Any>()

        val result = buildAndExecuteChain(listOf(interceptor), chatId, metadata) { _, _ -> null }

        assertNull(result)
    }

    @Test
    fun `interceptors receive correct chatId`() {
        var capturedChatIds = mutableListOf<UUID>()
        val originalChatId = UUID.randomUUID()

        val trackingInterceptor = object : ConversationInterceptor {
            override fun interceptGet(chain: ConversationInterceptor.GetChain, cid: UUID, metadata: MutableMap<String, Any>): Conversation? {
                capturedChatIds.add(cid)
                return chain.proceed(cid, metadata)
            }

            override fun interceptDelete(chain: ConversationInterceptor.DeleteChain, cid: UUID, metadata: MutableMap<String, Any>): Boolean {
                capturedChatIds.add(cid)
                return chain.proceed(cid, metadata)
            }
        }

        val metadata = mutableMapOf<String, Any>()

        buildAndExecuteChain(listOf(trackingInterceptor), originalChatId, metadata) { cid, _ ->
            capturedChatIds.add(cid)
            null
        }

        assertEquals(2, capturedChatIds.size)
        assertTrue(capturedChatIds.all { it == originalChatId })
    }

    // Helper methods

    private fun buildAndExecuteChain(
        interceptors: List<ConversationInterceptor>,
        chatId: UUID,
        metadata: MutableMap<String, Any>,
        finalAction: (UUID, MutableMap<String, Any>) -> Conversation?
    ): Conversation? {
        val chain = buildChain(interceptors, 0, finalAction)
        return chain.proceed(chatId, metadata)
    }

     private fun buildChain(
         interceptors: List<ConversationInterceptor>,
         index: Int,
         finalAction: (UUID, MutableMap<String, Any>) -> Conversation?
     ): ConversationInterceptor.GetChain {
         return object : ConversationInterceptor.GetChain {
             override fun proceed(chatId: UUID, metadata: MutableMap<String, Any>): Conversation? {
                 return if (index < interceptors.size) {
                     val nextChain = buildChain(interceptors, index + 1, finalAction)
                     interceptors[index].interceptGet(nextChain, chatId, metadata)
                 } else {
                     finalAction(chatId, metadata)
                 }
             }
         }
     }

     // Tests for DeleteChain (interceptDelete)

     @Test
     fun `single delete interceptor executes and calls proceed`() {
         val callLog = mutableListOf<String>()
         val chatId = UUID.randomUUID()

         val interceptor = object : ConversationInterceptor {
             override fun interceptGet(chain: ConversationInterceptor.GetChain, chatId: UUID, metadata: MutableMap<String, Any>): Conversation? {
                 return chain.proceed(chatId, metadata)
             }

             override fun interceptDelete(chain: ConversationInterceptor.DeleteChain, chatId: UUID, metadata: MutableMap<String, Any>): Boolean {
                 callLog.add("interceptor")
                 return chain.proceed(chatId, metadata)
             }
         }
         val metadata = mutableMapOf<String, Any>()

         val result = buildAndExecuteDeleteChain(listOf(interceptor), chatId, metadata) { _, _ ->
             callLog.add("final-action")
             true
         }

         assertEquals(2, callLog.size)
         assertEquals("interceptor", callLog[0], "Interceptor should execute first")
         assertEquals("final-action", callLog[1], "Final action should execute last")
         assertTrue(result)
     }

     @Test
     fun `multiple delete interceptors execute in order`() {
         val callLog = mutableListOf<String>()
         val chatId = UUID.randomUUID()

         val interceptor1 = object : ConversationInterceptor {
             override fun interceptGet(chain: ConversationInterceptor.GetChain, chatId: UUID, metadata: MutableMap<String, Any>): Conversation? {
                 return chain.proceed(chatId, metadata)
             }

             override fun interceptDelete(chain: ConversationInterceptor.DeleteChain, chatId: UUID, metadata: MutableMap<String, Any>): Boolean {
                 callLog.add("first")
                 return chain.proceed(chatId, metadata)
             }
         }

         val interceptor2 = object : ConversationInterceptor {
             override fun interceptGet(chain: ConversationInterceptor.GetChain, chatId: UUID, metadata: MutableMap<String, Any>): Conversation? {
                 return chain.proceed(chatId, metadata)
             }

             override fun interceptDelete(chain: ConversationInterceptor.DeleteChain, chatId: UUID, metadata: MutableMap<String, Any>): Boolean {
                 callLog.add("second")
                 return chain.proceed(chatId, metadata)
             }
         }

         val interceptor3 = object : ConversationInterceptor {
             override fun interceptGet(chain: ConversationInterceptor.GetChain, chatId: UUID, metadata: MutableMap<String, Any>): Conversation? {
                 return chain.proceed(chatId, metadata)
             }

             override fun interceptDelete(chain: ConversationInterceptor.DeleteChain, chatId: UUID, metadata: MutableMap<String, Any>): Boolean {
                 callLog.add("third")
                 return chain.proceed(chatId, metadata)
             }
         }

         val metadata = mutableMapOf<String, Any>()

         buildAndExecuteDeleteChain(listOf(interceptor1, interceptor2, interceptor3), chatId, metadata) { _, _ ->
             callLog.add("final-action")
             true
         }

         assertEquals(4, callLog.size)
         assertEquals("first", callLog[0])
         assertEquals("second", callLog[1])
         assertEquals("third", callLog[2])
         assertEquals("final-action", callLog[3])
     }

     @Test
     fun `delete interceptors can modify metadata`() {
         val callLog = mutableListOf<String>()
         val chatId = UUID.randomUUID()

         val modifyingInterceptor = object : ConversationInterceptor {
             override fun interceptGet(chain: ConversationInterceptor.GetChain, chatId: UUID, metadata: MutableMap<String, Any>): Conversation? {
                 return chain.proceed(chatId, metadata)
             }

             override fun interceptDelete(chain: ConversationInterceptor.DeleteChain, chatId: UUID, metadata: MutableMap<String, Any>): Boolean {
                 metadata["modified"] = true
                 callLog.add("modified")
                 return chain.proceed(chatId, metadata)
             }
         }

         val readingInterceptor = object : ConversationInterceptor {
             override fun interceptGet(chain: ConversationInterceptor.GetChain, chatId: UUID, metadata: MutableMap<String, Any>): Conversation? {
                 return chain.proceed(chatId, metadata)
             }

             override fun interceptDelete(chain: ConversationInterceptor.DeleteChain, chatId: UUID, metadata: MutableMap<String, Any>): Boolean {
                 val value = metadata["modified"]
                 callLog.add("read:$value")
                 return chain.proceed(chatId, metadata)
             }
         }

         val metadata = mutableMapOf<String, Any>()

         buildAndExecuteDeleteChain(listOf(modifyingInterceptor, readingInterceptor), chatId, metadata) { _, md ->
             assertEquals(true, md["modified"], "Metadata modification should be visible in final action")
             true
         }

         assertTrue(callLog.contains("modified"))
         assertTrue(callLog.contains("read:true"))
     }

     @Test
     fun `delete interceptors can short-circuit the chain`() {
         val callLog = mutableListOf<String>()
         val chatId = UUID.randomUUID()

         val shortCircuitInterceptor = object : ConversationInterceptor {
             override fun interceptGet(chain: ConversationInterceptor.GetChain, cid: UUID, metadata: MutableMap<String, Any>): Conversation? {
                 return chain.proceed(cid, metadata)
             }

             override fun interceptDelete(chain: ConversationInterceptor.DeleteChain, cid: UUID, metadata: MutableMap<String, Any>): Boolean {
                 callLog.add("short-circuit-delete")
                 // Short-circuit by returning false (delete denied)
                 return false
             }
         }

         val neverCalledInterceptor = object : ConversationInterceptor {
             override fun interceptGet(chain: ConversationInterceptor.GetChain, chatId: UUID, metadata: MutableMap<String, Any>): Conversation? {
                 return chain.proceed(chatId, metadata)
             }

             override fun interceptDelete(chain: ConversationInterceptor.DeleteChain, chatId: UUID, metadata: MutableMap<String, Any>): Boolean {
                 callLog.add("never-called")
                 return chain.proceed(chatId, metadata)
             }
         }

         val metadata = mutableMapOf<String, Any>()

         val result = buildAndExecuteDeleteChain(listOf(shortCircuitInterceptor, neverCalledInterceptor), chatId, metadata) { _, _ ->
             callLog.add("final-action")
             true
         }

         assertEquals(1, callLog.size)
         assertEquals("short-circuit-delete", callLog[0])
         assertFalse(result, "Delete should return false when short-circuited")
     }

     @Test
     fun `empty delete interceptor list executes final action directly`() {
         val callLog = mutableListOf<String>()
         val chatId = UUID.randomUUID()
         val metadata = mutableMapOf<String, Any>()

         buildAndExecuteDeleteChain(emptyList(), chatId, metadata) { _, _ ->
             callLog.add("final-action")
             true
         }

         assertEquals(1, callLog.size)
         assertEquals("final-action", callLog[0])
     }

     @Test
     fun `delete interceptors receive correct chatId`() {
         var capturedChatIds = mutableListOf<UUID>()
         val originalChatId = UUID.randomUUID()

         val trackingInterceptor = object : ConversationInterceptor {
             override fun interceptGet(chain: ConversationInterceptor.GetChain, cid: UUID, metadata: MutableMap<String, Any>): Conversation? {
                 return chain.proceed(cid, metadata)
             }

             override fun interceptDelete(chain: ConversationInterceptor.DeleteChain, cid: UUID, metadata: MutableMap<String, Any>): Boolean {
                 capturedChatIds.add(cid)
                 return chain.proceed(cid, metadata)
             }
         }

         val metadata = mutableMapOf<String, Any>()

         buildAndExecuteDeleteChain(listOf(trackingInterceptor), originalChatId, metadata) { cid, _ ->
             capturedChatIds.add(cid)
             true
         }

         assertEquals(2, capturedChatIds.size)
         assertTrue(capturedChatIds.all { it == originalChatId })
     }

     // Helper methods for DeleteChain

     private fun buildAndExecuteDeleteChain(
         interceptors: List<ConversationInterceptor>,
         chatId: UUID,
         metadata: MutableMap<String, Any>,
         finalAction: (UUID, MutableMap<String, Any>) -> Boolean
     ): Boolean {
         val chain = buildDeleteChain(interceptors, 0, finalAction)
         return chain.proceed(chatId, metadata)
     }

     private fun buildDeleteChain(
         interceptors: List<ConversationInterceptor>,
         index: Int,
         finalAction: (UUID, MutableMap<String, Any>) -> Boolean
     ): ConversationInterceptor.DeleteChain {
         return object : ConversationInterceptor.DeleteChain {
             override fun proceed(chatId: UUID, metadata: MutableMap<String, Any>): Boolean {
                 return if (index < interceptors.size) {
                     val nextChain = buildDeleteChain(interceptors, index + 1, finalAction)
                     interceptors[index].interceptDelete(nextChain, chatId, metadata)
                 } else {
                     finalAction(chatId, metadata)
                 }
             }
         }
     }

    private class LoggingInterceptor(
        private val name: String,
        private val callLog: MutableList<String>
    ) : ConversationInterceptor {
        override fun interceptGet(chain: ConversationInterceptor.GetChain, chatId: UUID, metadata: MutableMap<String, Any>): Conversation? {
            callLog.add(name)
            return chain.proceed(chatId, metadata)
        }

        override fun interceptDelete(chain: ConversationInterceptor.DeleteChain, chatId: UUID, metadata: MutableMap<String, Any>): Boolean {
            callLog.add(name)
            return chain.proceed(chatId, metadata)
        }
    }
}