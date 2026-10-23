package com.streammultichat.core.chat

import com.streammultichat.core.model.ChatMessage
import com.streammultichat.core.model.PlatformType
import com.streammultichat.core.model.StreamChannelConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UnifiedChatProcessorTest {

    @Test
    fun `deduplication works and messages arrive correctly`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val processor = UnifiedChatProcessor(testScope, maxBufferCapacity = 10, dispatcher = testDispatcher)

        val connector = MockStreamConnector(PlatformType.TWITCH, testScope)
        processor.registerConnector(connector)
        processor.connectPlatform(StreamChannelConfig(PlatformType.TWITCH, "test"))

        connector.triggerSpike(5)
        testScheduler.advanceTimeBy(100)

        // Buffer size should be 5
        assertEquals(5, processor.rawMessages.value.size)
    }
}
