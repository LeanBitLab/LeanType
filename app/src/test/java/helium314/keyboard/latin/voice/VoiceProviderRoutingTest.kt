// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodSubtype
import androidx.test.core.app.ApplicationProvider
import com.leanbitlab.leantype.voice.VoiceConstants
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.Shortcut
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.prefs
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VoiceProviderRoutingTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var richImm: RichInputMethodManager

    @Before
    fun setup() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        context = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            app.createDeviceProtectedStorageContext() else app

        DeviceProtectedUtils::class.java.getDeclaredField("prefs").apply { isAccessible = true }.set(null, null)
        prefs = context.prefs()
        prefs.edit().clear().commit()

        RichInputMethodManager.init(context)
        richImm = RichInputMethodManager.getInstance()
    }

    private fun createShortcut(packageName: String, className: String = "VoiceService"): Shortcut {
        val imi = InputMethodInfo(packageName, className, packageName, null)
        val subtype = InputMethodSubtype.InputMethodSubtypeBuilder()
            .setSubtypeMode("voice")
            .build()
        return Shortcut(imi, subtype)
    }

    @Test
    fun testDefaultVoicePackage_extractsPackageFromVoiceRecognitionService() {
        Settings.Secure.putString(
            context.contentResolver,
            "voice_recognition_service",
            "org.futo.voiceinput/org.futo.voiceinput.service.FutoRecognitionService"
        )
        val pkg = richImm.getDefaultVoicePackage(context)
        assertEquals("org.futo.voiceinput", pkg)
    }

    @Test
    fun testDefaultVoicePackage_fallsBackToAssistant() {
        Settings.Secure.putString(context.contentResolver, "voice_recognition_service", null)
        Settings.Secure.putString(
            context.contentResolver,
            "assistant",
            "com.example.assistant/com.example.assistant.AssistantService"
        )
        val pkg = richImm.getDefaultVoicePackage(context)
        assertEquals("com.example.assistant", pkg)
    }

    @Test
    fun testDefaultVoicePackage_returnsNullWhenNothingConfigured() {
        Settings.Secure.putString(context.contentResolver, "voice_recognition_service", null)
        Settings.Secure.putString(context.contentResolver, "assistant", null)
        val pkg = richImm.getDefaultVoicePackage(context)
        assertNull(pkg)
    }

    @Test
    fun testTargetShortcut_honorsExplicitUserSelection() {
        val google = createShortcut("com.google.android.googlequicksearchbox")
        val futo = createShortcut("org.futo.voiceinput")
        richImm.setShortcutsForTesting(listOf(google, futo))

        prefs.edit().putString(VoiceConstants.PREF_VOICE_THIRD_PARTY_APP, "org.futo.voiceinput").commit()
        val target = richImm.getTargetShortcut()
        assertNotNull(target)
        assertEquals("org.futo.voiceinput", target.imi.packageName)
    }

    @Test
    fun testTargetShortcut_honorsSystemDefaultService() {
        val google = createShortcut("com.google.android.googlequicksearchbox")
        val futo = createShortcut("org.futo.voiceinput")
        richImm.setShortcutsForTesting(listOf(google, futo))

        prefs.edit().putString(VoiceConstants.PREF_VOICE_THIRD_PARTY_APP, VoiceConstants.VOICE_APP_SYSTEM_DEFAULT).commit()
        Settings.Secure.putString(
            context.contentResolver,
            "voice_recognition_service",
            "org.futo.voiceinput/.service.VoiceService"
        )

        val target = richImm.getTargetShortcut()
        assertNotNull(target)
        assertEquals("org.futo.voiceinput", target.imi.packageName)
    }

    @Test
    fun testTargetShortcut_prefersNonGoogleFallbackWhenNoSystemDefault() {
        val google = createShortcut("com.google.android.googlequicksearchbox")
        val futo = createShortcut("org.futo.voiceinput")
        // Google is listed first in shortcuts (standard Android behavior on GMS devices)
        richImm.setShortcutsForTesting(listOf(google, futo))

        prefs.edit().putString(VoiceConstants.PREF_VOICE_THIRD_PARTY_APP, VoiceConstants.VOICE_APP_SYSTEM_DEFAULT).commit()
        Settings.Secure.putString(context.contentResolver, "voice_recognition_service", null)
        Settings.Secure.putString(context.contentResolver, "assistant", null)

        val target = richImm.getTargetShortcut()
        assertNotNull(target)
        // Should select non-Google over Google fallback
        assertEquals("org.futo.voiceinput", target.imi.packageName)
    }

    @Test
    fun testSetVoiceProvider_updatesPreferencesInSync() {
        richImm.setVoiceProvider(VoiceConstants.VOICE_PROVIDER_OFFLINE)
        assertEquals(VoiceConstants.VOICE_PROVIDER_OFFLINE, richImm.currentVoiceProvider)
        assertTrue(prefs.getBoolean(VoiceConstants.PREF_VOICE_OFFLINE_ENABLED, false))
        assertFalse(prefs.getBoolean(VoiceConstants.PREF_VOICE_ONLINE_ENABLED, false))
        assertTrue(richImm.isShortcutImeReady)

        richImm.setVoiceProvider(VoiceConstants.VOICE_PROVIDER_NONE)
        assertEquals(VoiceConstants.VOICE_PROVIDER_NONE, richImm.currentVoiceProvider)
        assertFalse(prefs.getBoolean(VoiceConstants.PREF_VOICE_OFFLINE_ENABLED, false))
        assertFalse(prefs.getBoolean(VoiceConstants.PREF_VOICE_ONLINE_ENABLED, false))
        assertFalse(richImm.isShortcutImeReady)
    }

    @Test
    fun testLegacyPreferenceMigration() {
        prefs.edit()
            .remove(VoiceConstants.PREF_VOICE_PROVIDER)
            .putBoolean(VoiceConstants.PREF_VOICE_OFFLINE_ENABLED, true)
            .commit()
        assertEquals(VoiceConstants.VOICE_PROVIDER_OFFLINE, richImm.currentVoiceProvider)
    }

    @Test
    fun testVoiceInputManager_statesAndEnumValues() {
        assertEquals(6, VoiceInputManager.VoiceState.values().size)
        assertTrue(VoiceInputManager.VoiceState.values().contains(VoiceInputManager.VoiceState.IDLE))
        assertTrue(VoiceInputManager.VoiceState.values().contains(VoiceInputManager.VoiceState.CONNECTING_PLUGIN))
        assertTrue(VoiceInputManager.VoiceState.values().contains(VoiceInputManager.VoiceState.STARTING_SESSION))
        assertTrue(VoiceInputManager.VoiceState.values().contains(VoiceInputManager.VoiceState.RECORDING))
        assertTrue(VoiceInputManager.VoiceState.values().contains(VoiceInputManager.VoiceState.PROCESSING_FINAL))
        assertTrue(VoiceInputManager.VoiceState.values().contains(VoiceInputManager.VoiceState.ERROR))
    }
}
