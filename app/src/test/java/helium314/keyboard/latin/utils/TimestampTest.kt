package helium314.keyboard.latin.utils

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.settings.Settings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TimestampTest {

    private lateinit var context: Application

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        Settings.init(context)
    }

    @Test
    fun testGetTimestamp_returnsValidString() {
        val timestamp = getTimestamp(context)
        assertNotNull(timestamp)
        assertTrue(timestamp.isNotEmpty())
    }

    @Test
    fun testGetTimestampFormatter_returnsValidFormatter() {
        val formatter = getTimestampFormatter(context)
        assertNotNull(formatter)
    }

    @Test
    fun testCheckTimestampFormat_validFormat_returnsTrue() {
        assertTrue(checkTimestampFormat("yyyy-MM-dd HH:mm:ss"))
        assertTrue(checkTimestampFormat("dd/MM/yyyy"))
        assertTrue(checkTimestampFormat("HH:mm"))
    }

    @Test
    fun testCheckTimestampFormat_invalidFormat_returnsFalse() {
        assertFalse(checkTimestampFormat("invalid_format!@#"))
        assertFalse(checkTimestampFormat("q")) // invalid character for pattern
    }

    @Test
    fun testGetTimestampFormatter_withInvalidPreference_fallsBackToDefault() {
        // Set invalid format in preferences
        context.prefs().edit().putString(Settings.PREF_TIMESTAMP_FORMAT, "invalid_format!@#").commit()

        val formatter = getTimestampFormatter(context)
        assertNotNull(formatter)
        // Should fallback to default format which usually doesn't throw, we check if we got a formatter
        val formatted = formatter.format(java.util.Calendar.getInstance().time)
        assertTrue(formatted.isNotEmpty())
    }
}
