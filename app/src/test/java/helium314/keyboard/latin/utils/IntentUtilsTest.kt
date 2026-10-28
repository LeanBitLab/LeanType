package helium314.keyboard.latin.utils

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class IntentUtilsTest {

    @Test
    fun testHandleSendIntentKey_one() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val shadowContext = shadowOf(context)
        shadowContext.clearBroadcastIntents()

        IntentUtils.handleSendIntentKey(context, KeyCode.SEND_INTENT_ONE)

        val broadcastIntents = shadowContext.broadcastIntents
        assertEquals(1, broadcastIntents.size)

        val intent = broadcastIntents[0]
        assertEquals("helium314.keyboard.latin.ACTION_SEND_INTENT", intent.action)
        assertEquals(1, intent.getIntExtra("EXTRA_NUMBER", -1))
    }

    @Test
    fun testHandleSendIntentKey_two() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val shadowContext = shadowOf(context)
        shadowContext.clearBroadcastIntents()

        IntentUtils.handleSendIntentKey(context, KeyCode.SEND_INTENT_TWO)

        val broadcastIntents = shadowContext.broadcastIntents
        assertEquals(1, broadcastIntents.size)

        val intent = broadcastIntents[0]
        assertEquals("helium314.keyboard.latin.ACTION_SEND_INTENT", intent.action)
        assertEquals(2, intent.getIntExtra("EXTRA_NUMBER", -1))
    }

    @Test
    fun testHandleSendIntentKey_three() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val shadowContext = shadowOf(context)
        shadowContext.clearBroadcastIntents()

        IntentUtils.handleSendIntentKey(context, KeyCode.SEND_INTENT_THREE)

        val broadcastIntents = shadowContext.broadcastIntents
        assertEquals(1, broadcastIntents.size)

        val intent = broadcastIntents[0]
        assertEquals("helium314.keyboard.latin.ACTION_SEND_INTENT", intent.action)
        assertEquals(3, intent.getIntExtra("EXTRA_NUMBER", -1))
    }

    @Test
    fun testHandleSendIntentKey_unrelatedKey() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val shadowContext = shadowOf(context)
        shadowContext.clearBroadcastIntents()

        // Use an arbitrary key code to see if it still sends the broadcast correctly based on math
        IntentUtils.handleSendIntentKey(context, -20010)

        val broadcastIntents = shadowContext.broadcastIntents
        assertEquals(1, broadcastIntents.size)

        val intent = broadcastIntents[0]
        assertEquals("helium314.keyboard.latin.ACTION_SEND_INTENT", intent.action)
        // intentNumber = (-20000 + 1) - (-20010) = 11
        assertEquals(11, intent.getIntExtra("EXTRA_NUMBER", -1))
    }
}
