package helium314.keyboard.latin.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
class AsyncResultHolderTest {

    @Test
    fun testSetAndGet() {
        val holder = AsyncResultHolder<String>("TestTag")
        holder.set("Result")

        // Timeout should be irrelevant since the result is already set
        val result = holder.get("Default", 1000L)
        assertEquals("Result", result)
    }

    @Test
    fun testGetTimeout() {
        val holder = AsyncResultHolder<String>("TestTag")

        // Wait for 10ms, should timeout and return default
        val result = holder.get("Default", 10L)
        assertEquals("Default", result)
    }

    @Test
    fun testSetMultipleTimes() {
        val holder = AsyncResultHolder<String>("TestTag")
        holder.set("First")
        holder.set("Second") // Should be ignored because latch count is already 0

        val result = holder.get("Default", 1000L)
        assertEquals("First", result)
    }

    @Test
    fun testSetNull() {
        val holder = AsyncResultHolder<String>("TestTag")
        holder.set(null)

        val result = holder.get("Default", 1000L)
        assertNull(result)
    }

    @Test
    fun testConcurrentGetAndSet() {
        val holder = AsyncResultHolder<String>("TestTag")
        val latch = CountDownLatch(1)
        var threadResult: String? = null

        val t = thread {
            latch.countDown()
            threadResult = holder.get("Default", 2000L)
        }

        // Wait for thread to start and block on get()
        latch.await(1000, TimeUnit.MILLISECONDS)

        // Small sleep to ensure thread is actually waiting in get()
        Thread.sleep(50)

        holder.set("ConcurrentResult")
        t.join(2000)

        assertEquals("ConcurrentResult", threadResult)
    }

    @Test
    fun testInterruptedGet() {
        val holder = AsyncResultHolder<String>("TestTag")
        val latch = CountDownLatch(1)
        var threadResult: String? = null

        val t = thread {
            latch.countDown()
            // This will block and eventually be interrupted
            threadResult = holder.get("Default", 5000L)
        }

        latch.await(1000, TimeUnit.MILLISECONDS)

        // Ensure thread is in get()
        Thread.sleep(50)

        t.interrupt()
        t.join(2000)

        // Should return the default value when interrupted
        assertEquals("Default", threadResult)
    }
}
