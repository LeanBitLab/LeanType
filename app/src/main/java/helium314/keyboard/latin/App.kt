// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
package helium314.keyboard.latin

import android.app.Application
import androidx.emoji2.text.EmojiCompat
import androidx.emoji2.text.DefaultEmojiCompatConfig
import androidx.work.Configuration
import helium314.keyboard.keyboard.emoji.SupportedEmojis
import helium314.keyboard.latin.define.DebugFlags
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.permissions.PermissionsUtil
import helium314.keyboard.latin.utils.LayoutUtilsCustom
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.prefs

import helium314.keyboard.latin.work.PluginWorkerFactory

class App : Application(), Configuration.Provider {

    // WorkManager Configuration.Provider — required for dynamic plugins (ML Kit Digital Ink & Translation).
    override val workManagerConfiguration: Configuration
        get() {
            val delegating = androidx.work.DelegatingWorkerFactory()
            delegating.addFactory(pluginWorkerFactory)
            return Configuration.Builder()
                .setWorkerFactory(delegating)
                .build()
        }

    override fun onCreate() {
        super.onCreate()
        DebugFlags.init(this)
        Settings.init(this)
        val useSystemEmoji = Settings.getInstance().useSystemEmoji()
        if (!useSystemEmoji) {
            val config = DefaultEmojiCompatConfig.create(this)
            if (config != null) {
                EmojiCompat.init(config)
            }
        }
        SubtypeSettings.init(this)
        RichInputMethodManager.init(this)

        AppUpgrade.checkVersionUpgrade(this)
        AppUpgrade.transferOldPinnedClips(this) // todo: remove in a few months, maybe mid 2026
        app = this
        Defaults.initDynamicDefaults(this)
        LayoutUtilsCustom.removeMissingLayouts(this) // only after version upgrade
        SupportedEmojis.load(this)
        PermissionsUtil.updateNotificationListenerComponent(this, prefs().getBoolean(Settings.PREF_AUTO_READ_OTP, false))
        helium314.keyboard.latin.gif.GifPluginManager.get(this).start()

        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        @Suppress("DEPRECATION")
        Log.i(
            "startup", "Starting ${applicationInfo.processName} version ${packageInfo.versionName} (${
                packageInfo.versionCode
            }) on Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})"
        )
    }

    companion object {
        val pluginWorkerFactory = PluginWorkerFactory()
        // used so JniUtils can access application once
        private var app: App? = null
        fun getApp(): App? {
            val application = app
            app = null
            return application
        }
    }
}
