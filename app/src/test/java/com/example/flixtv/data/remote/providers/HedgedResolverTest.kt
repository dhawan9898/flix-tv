package com.example.flixtv.data.remote.providers

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HedgedResolverTest {

    @Test
    fun fastFirstServerWinsAndNoOtherIsStarted() = runTest {
        val started = mutableListOf<String>()
        val result = resolveFirst(listOf("a", "b", "c"), 100) { hash ->
            started += hash
            delay(10)
            hash
        }
        assertEquals("a", result)
        assertEquals(listOf("a"), started)
    }

    @Test
    fun slowFirstServerIsHedgedAndNextCanWin() = runTest {
        val started = mutableListOf<String>()
        val result = resolveFirst(listOf("slow", "fast"), 50) { hash ->
            started += hash
            delay(if (hash == "slow") 1000 else 10)
            hash
        }
        assertEquals("fast", result)
        assertEquals(listOf("slow", "fast"), started)
        assertTrue("did not wait for the slow server", currentTime < 500)
    }

    @Test
    fun failingServerHandsOverImmediately() = runTest {
        val result = resolveFirst(listOf("bad", "empty", "good"), 10_000) { hash ->
            when (hash) {
                "bad" -> throw IllegalStateException("403")
                "empty" -> null
                else -> {
                    delay(5)
                    hash
                }
            }
        }
        assertEquals("good", result)
        assertTrue("did not wait out the hedge delay", currentTime < 500)
    }

    @Test
    fun nullWhenEveryServerFails() = runTest {
        val result = resolveFirst<String>(listOf("a", "b"), 10) { throw IllegalStateException("down") }
        assertNull(result)
    }

    @Test
    fun nullForEmptyList() = runTest {
        assertNull(resolveFirst(emptyList(), 10) { "x" })
    }
}
