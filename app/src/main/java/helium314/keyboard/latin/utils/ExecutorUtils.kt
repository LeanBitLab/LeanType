/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.utils

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

/**
 * Utilities to manage executors.
 */
object ExecutorUtils {
    private const val TAG = "ExecutorUtils"

    const val KEYBOARD = "Keyboard"
    const val SPELLING = "Spelling"

    private var sKeyboardExecutorService = newExecutorService(KEYBOARD)
    private var sSpellingExecutorService = newExecutorService(SPELLING)

    private fun newExecutorService(name: String): ScheduledExecutorService {
        val cores = Runtime.getRuntime().availableProcessors()
        val threads = maxOf(cores / 2, 1)
        return Executors.newScheduledThreadPool(threads, ExecutorFactory(name))
    }

    private class ExecutorFactory(private val mName: String) : ThreadFactory {
        override fun newThread(runnable: Runnable): Thread {
            val thread = Thread(runnable, mName)
            thread.setUncaughtExceptionHandler { _, ex ->
                Log.w(mName, runnable.javaClass.simpleName, ex)
            }
            return thread
        }
    }

    private var sExecutorServiceForTests: ScheduledExecutorService? = null

    fun setExecutorServiceForTests(executorServiceForTests: ScheduledExecutorService?) {
        sExecutorServiceForTests = executorServiceForTests
    }

    fun getBackgroundExecutor(name: String): ScheduledExecutorService {
        sExecutorServiceForTests?.let { return it }
        return when (name) {
            KEYBOARD -> sKeyboardExecutorService
            SPELLING -> sSpellingExecutorService
            else -> throw IllegalArgumentException("Invalid executor: $name")
        }
    }

    fun killTasks(name: String) {
        val executorService = getBackgroundExecutor(name)
        executorService.shutdownNow()
        try {
            executorService.awaitTermination(5, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Log.wtf(TAG, "Failed to shut down: $name")
        }
        if (executorService == sExecutorServiceForTests) {
            return
        }
        when (name) {
            KEYBOARD -> sKeyboardExecutorService = newExecutorService(KEYBOARD)
            SPELLING -> sSpellingExecutorService = newExecutorService(SPELLING)
            else -> throw IllegalArgumentException("Invalid executor: $name")
        }
    }

    fun chain(vararg runnables: Runnable): Runnable {
        return RunnableChain(*runnables)
    }

    class RunnableChain(vararg runnables: Runnable) : Runnable {
        private val mRunnables: Array<out Runnable> = runnables
        init {
            require(mRunnables.isNotEmpty()) { "Attempting to construct an empty chain" }
        }

        fun getRunnables(): Array<out Runnable> {
            return mRunnables
        }

        override fun run() {
            for (runnable in mRunnables) {
                if (Thread.interrupted()) {
                    return
                }
                runnable.run()
            }
        }
    }
}
