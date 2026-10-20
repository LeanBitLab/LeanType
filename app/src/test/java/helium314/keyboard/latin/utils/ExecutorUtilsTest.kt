package helium314.keyboard.latin.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class ExecutorUtilsTest {

    @Test
    fun testRunnableChain() {
        var count = 0
        val runnable1 = Runnable { count += 1 }
        val runnable2 = Runnable { count += 2 }

        val chain = ExecutorUtils.chain(runnable1, runnable2)
        chain.run()

        assertEquals(3, count)
    }

    @Test
    fun testRunnableChainInterrupted() {
        var count = 0
        val runnable1 = Runnable { count += 1 }
        val runnable2 = Runnable { Thread.currentThread().interrupt() }
        val runnable3 = Runnable { count += 4 } // Should not run

        val chain = ExecutorUtils.chain(runnable1, runnable2, runnable3)
        chain.run()

        assertEquals(1, count)
        // Clear interrupt flag
        Thread.interrupted()
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRunnableChainEmpty() {
        ExecutorUtils.chain()
    }
}
