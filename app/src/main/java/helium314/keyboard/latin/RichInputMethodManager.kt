/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
package helium314.keyboard.latin

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import android.widget.Toast
import com.leanbitlab.leantype.voice.VoiceConstants
import helium314.keyboard.compat.locale
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.LocaleUtils.getBestMatch
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.LanguageOnSpacebarUtils
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.ScriptUtils.script
import helium314.keyboard.latin.utils.SubtypeLocaleUtils
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.getSecondaryLocales
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.latin.utils.prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Enrichment class for InputMethodManager to simplify interaction and add functionality. */
class RichInputMethodManager private constructor() {
    private lateinit var context: Context
    private lateinit var imm: InputMethodManager
    private lateinit var inputMethodInfoCache: InputMethodInfoCache
    private lateinit var currentRichInputMethodSubtype: RichInputMethodSubtype
    // Use SupervisorJob so a single failure in updateShortcutIme or one
    // of the other fire-and-forget coroutines cannot tear down the
    // scope and stop all subsequent subtype lookups. The manager is a
    // process-wide singleton, so the scope is also process-wide.
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val isInitializedInternal get() = this::imm.isInitialized

    val currentSubtypeLocale get() = forcedSubtypeForTesting?.locale ?: currentSubtype.locale

    val currentSubtype get() = forcedSubtypeForTesting ?: currentRichInputMethodSubtype

    val combiningRulesExtraValueOfCurrentSubtype get() =
        SubtypeLocaleUtils.getCombiningRulesExtraValue(currentSubtype.rawSubtype)

    val inputMethodInfoOfThisIme: InputMethodInfo? get() = inputMethodInfoCache.inputMethodOfThisIme

    val inputMethodManager: InputMethodManager get() {
        checkInitialized()
        return imm
    }

    internal var shortcuts = listOf<Shortcut>()

    internal fun setShortcutsForTesting(list: List<Shortcut>) {
        shortcuts = list
    }

    val isOnlineFlavor: Boolean
        get() = BuildConfig.FLAVOR == "standard" || BuildConfig.FLAVOR == "standardfull"

    val currentVoiceProvider: String
        get() {
            if (!this::context.isInitialized) return VoiceConstants.VOICE_PROVIDER_THIRD_PARTY
            val p = context.prefs()
            val provider = p.getString(VoiceConstants.PREF_VOICE_PROVIDER, null)
            val raw = provider ?: when {
                p.getBoolean(VoiceConstants.PREF_VOICE_OFFLINE_ENABLED, false) -> VoiceConstants.VOICE_PROVIDER_OFFLINE
                isOnlineFlavor && p.getBoolean(VoiceConstants.PREF_VOICE_ONLINE_ENABLED, false) -> VoiceConstants.VOICE_PROVIDER_ONLINE
                else -> VoiceConstants.VOICE_PROVIDER_THIRD_PARTY
            }
            return if (!isOnlineFlavor && raw == VoiceConstants.VOICE_PROVIDER_ONLINE) {
                VoiceConstants.VOICE_PROVIDER_OFFLINE
            } else {
                raw
            }
        }

    fun setVoiceProvider(provider: String) {
        if (!this::context.isInitialized) return
        val target = if (!isOnlineFlavor && provider == VoiceConstants.VOICE_PROVIDER_ONLINE) {
            VoiceConstants.VOICE_PROVIDER_OFFLINE
        } else {
            provider
        }
        val p = context.prefs()
        p.edit().apply {
            putString(VoiceConstants.PREF_VOICE_PROVIDER, target)
            putBoolean(VoiceConstants.PREF_VOICE_OFFLINE_ENABLED, target == VoiceConstants.VOICE_PROVIDER_OFFLINE)
            putBoolean(VoiceConstants.PREF_VOICE_ONLINE_ENABLED, target == VoiceConstants.VOICE_PROVIDER_ONLINE)
            apply()
        }
    }

    val isVoiceInputEnabled: Boolean
        get() = currentVoiceProvider != VoiceConstants.VOICE_PROVIDER_NONE

    val isOfflineVoiceEnabled: Boolean
        get() = currentVoiceProvider == VoiceConstants.VOICE_PROVIDER_OFFLINE

    val isShortcutImeReady: Boolean
        get() {
            return when (currentVoiceProvider) {
                VoiceConstants.VOICE_PROVIDER_OFFLINE, VoiceConstants.VOICE_PROVIDER_ONLINE -> true
                VoiceConstants.VOICE_PROVIDER_THIRD_PARTY -> shortcuts.isNotEmpty() || hasInstalledVoiceImis()
                VoiceConstants.VOICE_PROVIDER_NONE -> false
                else -> shortcuts.isNotEmpty()
            }
        }

    fun hasInstalledVoiceImis(): Boolean = getInstalledVoiceImis().isNotEmpty()

    fun getInstalledVoiceImis(): List<InputMethodInfo> {
        if (!this::imm.isInitialized || !this::context.isInitialized) return emptyList()
        val ourPkg = context.packageName
        return try {
            imm.inputMethodList.filter { imi ->
                imi.packageName != ourPkg && (0 until imi.subtypeCount).any { idx ->
                    imi.getSubtypeAt(idx).mode == "voice"
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query installed IMEs", e)
            emptyList()
        }
    }

    fun getDefaultVoicePackage(ctx: Context): String? {
        try {
            val voiceService = AndroidSettings.Secure.getString(ctx.contentResolver, "voice_recognition_service")
            if (!voiceService.isNullOrBlank()) {
                val pkg = ComponentName.unflattenFromString(voiceService)?.packageName
                    ?: voiceService.substringBefore('/')
                if (pkg.isNotBlank()) return pkg
            }
            val assistant = AndroidSettings.Secure.getString(ctx.contentResolver, "assistant")
            if (!assistant.isNullOrBlank()) {
                val pkg = ComponentName.unflattenFromString(assistant)?.packageName
                    ?: assistant.substringBefore('/')
                if (pkg.isNotBlank()) return pkg
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read default voice service from settings", e)
        }
        return null
    }

    internal fun getTargetShortcut(): Shortcut? {
        if (shortcuts.isEmpty()) return null
        val p = context.prefs()
        val selectedApp = p.getString(VoiceConstants.PREF_VOICE_THIRD_PARTY_APP, VoiceConstants.VOICE_APP_SYSTEM_DEFAULT)

        // 1. Explicit user selection
        if (!selectedApp.isNullOrEmpty() && selectedApp != VoiceConstants.VOICE_APP_SYSTEM_DEFAULT) {
            val userMatch = shortcuts.firstOrNull { it.imi.id == selectedApp || it.imi.packageName == selectedApp }
            if (userMatch != null) return userMatch
        }

        // 2. System default voice recognition service
        val defaultPackage = getDefaultVoicePackage(context)
        if (!defaultPackage.isNullOrEmpty()) {
            val sysMatch = shortcuts.firstOrNull { it.imi.packageName == defaultPackage }
            if (sysMatch != null) return sysMatch
        }

        // 3. Fallback: if there is an enabled voice IME that is not Google, prefer it if defaultPackage was unspecified
        val nonGoogle = shortcuts.firstOrNull { !it.imi.packageName.contains("google") }
        if (nonGoogle != null && defaultPackage == null) {
            return nonGoogle
        }

        // 4. Final fallback: first available
        return shortcuts.firstOrNull()
    }

    fun getEnabledInputMethodSubtypes(imi: InputMethodInfo, allowsImplicitlySelectedSubtypes: Boolean) =
        inputMethodInfoCache.getEnabledInputMethodSubtypeList(imi, allowsImplicitlySelectedSubtypes)

    fun hasMultipleEnabledIMEsOrSubtypes(shouldIncludeAuxiliarySubtypes: Boolean): Boolean {
        val enabledImis = try {
            imm.enabledInputMethodList
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get enabledInputMethodList", e)
            emptyList()
        }
        return hasMultipleEnabledSubtypes(shouldIncludeAuxiliarySubtypes, enabledImis)
    }

    fun hasMultipleEnabledSubtypesInThisIme(shouldIncludeAuxiliarySubtypes: Boolean) =
        SubtypeSettings.getEnabledSubtypes(shouldIncludeAuxiliarySubtypes).size > 1

    fun getNextSubtypeInThisIme(onlyCurrentIme: Boolean): InputMethodSubtype? {
        val currentSubtype = currentSubtype.rawSubtype
        val enabledSubtypes = SubtypeSettings.getEnabledSubtypes(true)
        val currentIndex = enabledSubtypes.indexOf(currentSubtype)
        if (currentIndex == -1) {
            Log.w(TAG, "Can't find current subtype in enabled subtypes: subtype=" +
                    SubtypeLocaleUtils.getSubtypeNameForLogging(currentSubtype))
            return if (onlyCurrentIme) enabledSubtypes[0] // just return first enabled subtype
            else null
        }
        val nextIndex = (currentIndex + 1) % enabledSubtypes.size
        if (nextIndex <= currentIndex && !onlyCurrentIme) {
            // The current subtype is the last or only enabled one and it needs to switch to next IME.
            return null
        }
        return enabledSubtypes[nextIndex]
    }

    fun findSubtypeForHintLocale(locale: Locale): InputMethodSubtype? {
        // Find the best subtype based on a locale matching
        val subtypes = SubtypeSettings.getEnabledSubtypes(true)
        var bestMatch = getBestMatch(locale, subtypes) { it.locale() }
        if (bestMatch != null) return bestMatch

        // search for first secondary language & script match
        val language = locale.language
        val script = locale.script()
        for (subtype in subtypes) {
            val subtypeLocale = subtype.locale()
            if (subtypeLocale.script() != script) continue  // need compatible script

            bestMatch = subtype
            val secondaryLocales = getSecondaryLocales(subtype.extraValue)
            for (secondaryLocale in secondaryLocales) {
                if (secondaryLocale.language == language) {
                    return bestMatch
                }
            }
        }
        // if wanted script is not compatible to current subtype, return a subtype with compatible script if available
        if (script != currentSubtypeLocale.script()) {
            return bestMatch
        }
        return null
    }

    fun onSubtypeChanged(newSubtype: InputMethodSubtype) {
        SubtypeSettings.setSelectedSubtype(context.prefs(), newSubtype)
        currentRichInputMethodSubtype = RichInputMethodSubtype.get(newSubtype)
        scope.launch { updateShortcutIme() }
        if (DEBUG) {
            Log.w(TAG, "onSubtypeChanged: $currentRichInputMethodSubtype")
        }
    }

    fun refreshSubtypeCaches() {
        inputMethodInfoCache.clear()
        currentRichInputMethodSubtype = RichInputMethodSubtype.get(SubtypeSettings.getSelectedSubtype(context.prefs()))
        scope.launch { updateShortcutIme() }
    }

    fun switchToShortcutIme(inputMethodService: InputMethodService) = scope.launch {
        val target = getTargetShortcut()
        if (target == null) {
            withContext(Dispatchers.Main) {
                val installed = getInstalledVoiceImis()
                if (installed.isNotEmpty()) {
                    Toast.makeText(
                        inputMethodService,
                        inputMethodService.getString(R.string.voice_app_not_enabled),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        inputMethodService,
                        inputMethodService.getString(R.string.voice_no_app_found),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            return@launch
        }
        val imiId = target.imi.id
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            inputMethodService.switchInputMethod(imiId, target.subtype)
        } else {
            val token = inputMethodService.window.window?.attributes?.token ?: return@launch
            @Suppress("Deprecation") imm.setInputMethodAndSubtype(token, imiId, target.subtype)
        }
    }

    // todo: is shortcutIme only voice input, or can it be something else?
    //  if always voice input, rename it and other things like mHasShortcutKey
    private fun updateShortcutIme() {
        if (DEBUG) {
            val old = shortcuts.joinToString("; ") { "${it.imi.id}: ${it.subtype.locale()}, ${it.subtype.mode}" }
            Log.d(TAG, ("Update shortcut IMEs from: $old"))
        }
        val richSubtype = currentRichInputMethodSubtype
        val implicitlyEnabledSubtype = SubtypeSettings.isEnabled(richSubtype.rawSubtype)
                && !SubtypeSettings.getEnabledSubtypes(false).contains(richSubtype.rawSubtype)
        val systemLocale = context.resources.configuration.locale()
        LanguageOnSpacebarUtils.onSubtypeChanged(richSubtype, implicitlyEnabledSubtype, systemLocale)
        LanguageOnSpacebarUtils.setEnabledSubtypes(SubtypeSettings.getEnabledSubtypes(true))

        val list = mutableListOf<Shortcut>()
        try {
            inputMethodManager.shortcutInputMethodsAndSubtypes.forEach { (imi, subtypes) ->
                subtypes.forEach { list.add(Shortcut(imi, it)) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get shortcutInputMethodsAndSubtypes", e)
        }

        try {
            val ourPkg = context.packageName
            inputMethodManager.enabledInputMethodList.filter { it.packageName != ourPkg }.forEach { imi ->
                if (list.none { it.imi.id == imi.id }) {
                    (0 until imi.subtypeCount).map { imi.getSubtypeAt(it) }
                        .filter { it.mode == "voice" }
                        .forEach { list.add(Shortcut(imi, it)) }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to scan enabledInputMethodList for voice subtypes", e)
        }
        shortcuts = list

        if (DEBUG) {
            val new = shortcuts.joinToString("; ") { "${it.imi.id}: ${it.subtype.locale()}, ${it.subtype.mode}" }
            Log.d(TAG, ("Update shortcut IMEs to: $new"))
        }
    }

    private fun hasMultipleEnabledSubtypes(shouldIncludeAuxiliarySubtypes: Boolean, imiList: List<InputMethodInfo>): Boolean {
        // Number of the filtered IMEs
        var filteredImisCount = 0

        imiList.forEach { imi ->
            // We can return true immediately after we find two or more filtered IMEs.
            if (filteredImisCount > 1) return true
            val subtypes = getEnabledInputMethodSubtypes(imi, true)
            // IMEs that have no subtypes should be counted.
            if (subtypes.isEmpty()) {
                ++filteredImisCount
                return@forEach
            }

            var auxCount = 0
            for (subtype in subtypes) {
                if (!subtype.isAuxiliary) {
                    // IMEs that have one or more non-auxiliary subtypes should be counted.
                    ++filteredImisCount
                    return@forEach
                }
                ++auxCount
            }

            // If shouldIncludeAuxiliarySubtypes is true, IMEs that have two or more auxiliary
            // subtypes should be counted as well.
            if (shouldIncludeAuxiliarySubtypes && auxCount > 1) {
                ++filteredImisCount
            }
        }

        if (filteredImisCount > 1) {
            return true
        }
        val subtypes = SubtypeSettings.getEnabledSubtypes(true)
        // imm.getEnabledInputMethodSubtypeList(null, true) will return the current IME's
        // both explicitly and implicitly enabled input method subtype.
        // (The current IME should be LatinIME.)
        return subtypes.count { it.mode == Constants.Subtype.KEYBOARD_MODE } > 1
    }

    private fun checkInitialized() {
        if (!isInitializedInternal) {
            throw RuntimeException("$TAG is used before initialization")
        }
    }

    private fun initInternal(ctx: Context) {
        if (isInitializedInternal) {
            return
        }
        imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        context = ctx
        inputMethodInfoCache = InputMethodInfoCache(ctx, imm, ctx.packageName)

        // Initialize the current input method subtype and the shortcut IME.
        refreshSubtypeCaches()
    }

    companion object {
        private val TAG = RichInputMethodManager::class.java.simpleName
        private const val DEBUG = false

        private val instance = RichInputMethodManager()

        fun getInstance(): RichInputMethodManager {
            instance.checkInitialized()
            return instance
        }

        fun init(ctx: Context) {
            instance.initInternal(ctx)
        }

        fun isInitialized() = instance.isInitializedInternal

        private var forcedSubtypeForTesting: RichInputMethodSubtype? = null

        fun forceSubtype(subtype: InputMethodSubtype) {
            forcedSubtypeForTesting = RichInputMethodSubtype.get(subtype)
        }

        fun canSwitchLanguage(): Boolean {
            if (!isInitialized()) return false
            if (Settings.getValues().mLanguageSwitchKeyToOtherSubtypes && instance.hasMultipleEnabledSubtypesInThisIme(false)) return true
            val hasMultipleImes = try {
                instance.imm.enabledInputMethodList.size > 1
            } catch (e: Exception) {
                false
            }
            if (Settings.getValues().mLanguageSwitchKeyToOtherImes && hasMultipleImes) return true
            return false
        }
    }
}

private class InputMethodInfoCache(
    private val context: Context,
    private val imm: InputMethodManager,
    private val imePackageName: String
) {
    private var cachedThisImeInfo: InputMethodInfo? = null
    private val cachedSubtypeListWithImplicitlySelected = HashMap<InputMethodInfo, List<InputMethodSubtype>>()

    private val cachedSubtypeListOnlyExplicitlySelected = HashMap<InputMethodInfo, List<InputMethodSubtype>>()

    @get:Synchronized
    val inputMethodOfThisIme: InputMethodInfo? get() {
        if (cachedThisImeInfo == null) {
            try {
                cachedThisImeInfo = imm.inputMethodList.firstOrNull { it.packageName == imePackageName }
            } catch (e: Exception) {
                Log.w("RichInputMethodManager", "Failed to query inputMethodList from IMM", e)
            }
        }
        if (cachedThisImeInfo == null) {
            try {
                val serviceInfo = context.packageManager.getServiceInfo(
                    ComponentName(context, LatinIME::class.java),
                    PackageManager.GET_META_DATA
                )
                val resolveInfo = ResolveInfo().apply { this.serviceInfo = serviceInfo }
                cachedThisImeInfo = InputMethodInfo(context, resolveInfo)
            } catch (e: Exception) {
                Log.w("RichInputMethodManager", "Failed to resolve InputMethodInfo for $imePackageName via PackageManager", e)
            }
        }
        return cachedThisImeInfo
    }

    @Synchronized
    fun getEnabledInputMethodSubtypeList(imi: InputMethodInfo, allowsImplicitlySelectedSubtypes: Boolean): List<InputMethodSubtype> {
        val cache = if (allowsImplicitlySelectedSubtypes) cachedSubtypeListWithImplicitlySelected
            else cachedSubtypeListOnlyExplicitlySelected
        cache[imi]?.let { return it }
        val result = if (imi.packageName == imePackageName) {
            // allowsImplicitlySelectedSubtypes means system should choose if nothing is enabled,
            // use it to fall back to system locales or en_US to avoid returning an empty list
            SubtypeSettings.getEnabledSubtypes(allowsImplicitlySelectedSubtypes)
        } else {
            try {
                imm.getEnabledInputMethodSubtypeList(imi, allowsImplicitlySelectedSubtypes)
            } catch (e: Exception) {
                Log.w("RichInputMethodManager", "Failed to get enabled subtypes for ${imi.packageName}", e)
                emptyList()
            }
        }
        cache[imi] = result
        return result
    }

    @Synchronized
    fun clear() {
        cachedSubtypeListWithImplicitlySelected.clear()
        cachedSubtypeListOnlyExplicitlySelected.clear()
    }
}

internal class Shortcut(val imi: InputMethodInfo, val subtype: InputMethodSubtype)
