// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.leanbitlab.leantype.voice.VoiceEngineInfo
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VoicePluginManagerTest {

    private class FakeBindContext(base: Context) : ContextWrapper(base) {
        var bindCalls = 0
        var unbindCalls = 0
        var lastConn: ServiceConnection? = null
        private var activeConn: ServiceConnection? = null

        override fun getApplicationContext(): Context = this

        override fun createDeviceProtectedStorageContext(): Context = this

        override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean {
            bindCalls++
            lastConn = conn
            activeConn = conn
            return true
        }

        override fun unbindService(conn: ServiceConnection) {
            unbindCalls++
            if (activeConn === conn) {
                activeConn = null
            }
        }

        fun simulateConnected(binder: IBinder = Binder()) {
            lastConn?.onServiceConnected(
                ComponentName("com.leanbitlab.leantype.voice.offline", "VoiceEngineService"),
                binder
            )
        }
    }

    private lateinit var fakeContext: FakeBindContext
    private lateinit var manager: VoicePluginManager

    @Before
    fun setup() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        fakeContext = FakeBindContext(app)
        manager = VoicePluginManager(fakeContext)
    }

    @Test
    fun unbindAfterDelay_noopWhenNotBound() {
        manager.unbindAfterDelay(300_000L)
        assertFalse(manager.hasScheduledUnbind())
        assertEquals(0, fakeContext.unbindCalls)
    }

    @Test
    fun unbindAfterDelay_zeroUnbindsImmediately() {
        assertTrue(manager.bindIfNeeded())
        fakeContext.simulateConnected()
        assertTrue(manager.isPluginConnected())

        manager.unbindAfterDelay(0L)
        assertEquals(1, fakeContext.unbindCalls)
        assertFalse(manager.isPluginConnected())
        assertFalse(manager.hasScheduledUnbind())
    }

    @Test
    fun unbindAfterDelay_positiveWaitsUntilIdleDelayElapses() {
        assertTrue(manager.bindIfNeeded())
        fakeContext.simulateConnected()

        manager.unbindAfterDelay(60_000L)
        assertTrue(manager.hasScheduledUnbind())
        assertEquals(0, fakeContext.unbindCalls)
        assertTrue(manager.isPluginConnected())

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(59))
        assertEquals(0, fakeContext.unbindCalls)
        assertTrue(manager.isPluginConnected())

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals(1, fakeContext.unbindCalls)
        assertFalse(manager.isPluginConnected())
        assertFalse(manager.hasScheduledUnbind())
    }

    @Test
    fun bindIfNeeded_cancelsPendingUnbind() {
        assertTrue(manager.bindIfNeeded())
        fakeContext.simulateConnected()

        manager.unbindAfterDelay(60_000L)
        assertTrue(manager.hasScheduledUnbind())

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30))
        assertTrue(manager.bindIfNeeded())
        assertFalse(manager.hasScheduledUnbind())

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(60))
        assertEquals(0, fakeContext.unbindCalls)
        assertTrue(manager.isPluginConnected())
    }

    @Test
    fun unbindAfterDelay_negativeKeepsBoundAlways() {
        assertTrue(manager.bindIfNeeded())
        fakeContext.simulateConnected()

        manager.unbindAfterDelay(60_000L)
        assertTrue(manager.hasScheduledUnbind())

        manager.unbindAfterDelay(-1000L)
        assertFalse(manager.hasScheduledUnbind())

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(30))
        assertEquals(0, fakeContext.unbindCalls)
        assertTrue(manager.isPluginConnected())
    }

    @Test
    fun unbindAfterDelay_preserveExistingScheduleKeepsEarlierDeadline() {
        assertTrue(manager.bindIfNeeded())
        fakeContext.simulateConnected()

        manager.unbindAfterDelay(60_000L)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(45))
        manager.unbindAfterDelay(60_000L, preserveExistingSchedule = true)
        assertEquals(0, fakeContext.unbindCalls)

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(15))
        assertEquals(1, fakeContext.unbindCalls)
    }

    @Test
    fun unbindAfterDelay_respectsCanUnbindGuard() {
        assertTrue(manager.bindIfNeeded())
        fakeContext.simulateConnected()

        var canUnbind = false
        manager.unbindAfterDelay(10_000L, canUnbind = { canUnbind })
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        assertEquals(0, fakeContext.unbindCalls)
        assertTrue(manager.isPluginConnected())

        canUnbind = true
        manager.unbindAfterDelay(0L, canUnbind = { canUnbind })
        assertEquals(1, fakeContext.unbindCalls)
    }

    @Test
    fun resolveMaxDurationSec_allowsExplicitUnlimitedAndDefaultsInvalid() {
        assertEquals(30, VoiceInputManager.resolveMaxDurationSec(null))
        assertEquals(30, VoiceInputManager.resolveMaxDurationSec("invalid"))
        assertEquals(30, VoiceInputManager.resolveMaxDurationSec("-5"))
        assertEquals(15, VoiceInputManager.resolveMaxDurationSec("15"))
        assertEquals(30, VoiceInputManager.resolveMaxDurationSec("30"))
        assertEquals(60, VoiceInputManager.resolveMaxDurationSec("60"))
        assertEquals(600, VoiceInputManager.resolveMaxDurationSec("600"))
        assertEquals(3600, VoiceInputManager.resolveMaxDurationSec("3600"))
        assertEquals(0, VoiceInputManager.resolveMaxDurationSec("0"))
    }

    @Test
    fun shouldKeepScreenOnForVoice_requiresVisibleWindowAndActiveSpeechLease() {
        assertFalse(
            VoiceInputManager.shouldKeepScreenOnForVoice(
                state = VoiceInputManager.VoiceState.RECORDING,
                prefEnabled = false,
                isImeWindowVisible = true,
                isRecordingKeepAliveActive = true
            )
        )
        assertFalse(
            VoiceInputManager.shouldKeepScreenOnForVoice(
                state = VoiceInputManager.VoiceState.RECORDING,
                prefEnabled = true,
                isImeWindowVisible = false,
                isRecordingKeepAliveActive = true
            )
        )
        assertTrue(
            VoiceInputManager.shouldKeepScreenOnForVoice(
                state = VoiceInputManager.VoiceState.RECORDING,
                prefEnabled = true,
                isImeWindowVisible = true,
                isRecordingKeepAliveActive = true
            )
        )
        assertFalse(
            VoiceInputManager.shouldKeepScreenOnForVoice(
                state = VoiceInputManager.VoiceState.RECORDING,
                prefEnabled = true,
                isImeWindowVisible = true,
                isRecordingKeepAliveActive = false
            )
        )
        assertTrue(
            VoiceInputManager.shouldKeepScreenOnForVoice(
                state = VoiceInputManager.VoiceState.PROCESSING_FINAL,
                prefEnabled = true,
                isImeWindowVisible = true,
                isRecordingKeepAliveActive = false
            )
        )
        assertFalse(
            VoiceInputManager.shouldKeepScreenOnForVoice(
                state = VoiceInputManager.VoiceState.IDLE,
                prefEnabled = true,
                isImeWindowVisible = true,
                isRecordingKeepAliveActive = true
            )
        )
        assertTrue(VoiceInputManager.shouldKeepRecordingScreenOn(59_999L))
        assertFalse(VoiceInputManager.shouldKeepRecordingScreenOn(60_000L))
    }

    @Test
    fun binderDied_fromWorkerThreadMarshalsDisconnectToMainLooper() {
        assertTrue(manager.bindIfNeeded())
        fakeContext.simulateConnected()
        shadowOf(Looper.getMainLooper()).idle()

        var disconnectCallbacks = 0
        var callbackOnMainThread = false
        manager.setConnectionListener(object : VoicePluginManager.PluginConnectionListener {
            override fun onPluginConnected(info: VoiceEngineInfo?) {}
            override fun onPluginDisconnected() {
                disconnectCallbacks++
                callbackOnMainThread = Looper.myLooper() == Looper.getMainLooper()
            }
        })

        val worker = Thread { manager.binderDied() }
        worker.start()
        worker.join()

        assertEquals(0, disconnectCallbacks)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, disconnectCallbacks)
        assertTrue(callbackOnMainThread)
        assertFalse(manager.isPluginConnected())
    }
}
