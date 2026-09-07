/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.settings.SettingsValues
import helium314.keyboard.latin.sound.CustomSoundManager
import kotlin.math.max
import kotlin.math.min

/**
 * This class gathers audio feedback and haptic feedback functions.
 *
 * It offers a consistent and simple interface that allows LatinIME to forget about the
 * complexity of settings and the like.
 */
class AudioAndHapticFeedbackManager private constructor() {
    private var mContext: Context? = null
    private var mAudioManager: AudioManager? = null
    private var mVibrator: Vibrator? = null

    private var mSettingsValues: SettingsValues? = null
    private var mSoundOn = false
    private var mDoNotDisturb = false

    private fun initInternal(context: Context) {
        mContext = context.applicationContext
        mAudioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        mVibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        mContext?.let { CustomSoundManager.getInstance(it) }
    }

    fun performHapticAndAudioFeedback(
        code: Int,
        viewToPerformHapticFeedbackOn: View?,
        hapticEvent: HapticEvent
    ) {
        performHapticFeedback(viewToPerformHapticFeedbackOn, hapticEvent)
        performAudioFeedback(code, hapticEvent)
    }

    fun hasVibrator(): Boolean = mVibrator?.hasVibrator() == true

    fun hasAmplitudeControl(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mVibrator?.hasAmplitudeControl() == true

    fun vibrate(milliseconds: Long) {
        vibrate(milliseconds, -1)
    }

    fun vibrate(milliseconds: Long, amplitudePercent: Int) {
        val vibrator = mVibrator ?: return
        if (milliseconds <= 0) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val safeAmplitude = if (amplitudePercent < 0) {
                VibrationEffect.DEFAULT_AMPLITUDE
            } else {
                (amplitudePercent * 255 / 100).coerceIn(1, 255)
            }
            vibrator.vibrate(VibrationEffect.createOneShot(milliseconds, safeAmplitude))
        } else {
            vibrator.vibrate(milliseconds)
        }
    }

    private fun reevaluateIfSoundIsOn(): Boolean {
        val settings = mSettingsValues ?: return false
        val audioManager = mAudioManager ?: return false
        if (!settings.mSoundOn) return false
        if (settings.mSoundMuteInDnd && mDoNotDisturb) return false
        if (settings.mSoundMuteInSilent && audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) return false
        return true
    }

    @JvmOverloads
    fun performAudioFeedback(code: Int, hapticEvent: HapticEvent, keyXRatio: Float = 0.5f) {
        if (!mSoundOn || hapticEvent != HapticEvent.KEY_PRESS) return

        val volume = mSettingsValues?.mKeypressSoundVolume ?: -0.01f
        val context = mContext
        if (context != null) {
            val played = CustomSoundManager.getInstance(context).playSound(code, volume, keyXRatio)
            if (played) return
        }

        val audioManager = mAudioManager ?: return
        val sound = when (code) {
            KeyCode.DELETE -> AudioManager.FX_KEYPRESS_DELETE
            Constants.CODE_ENTER -> AudioManager.FX_KEYPRESS_RETURN
            Constants.CODE_SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
            else -> AudioManager.FX_KEYPRESS_STANDARD
        }
        audioManager.playSoundEffect(sound, volume)
    }

    fun performHapticFeedback(viewToPerformHapticFeedbackOn: View?, hapticEvent: HapticEvent) {
        val settings = mSettingsValues ?: return
        if (!settings.mVibrateOn || (mDoNotDisturb && !settings.mVibrateInDndMode)) return
        if (hapticEvent == HapticEvent.NO_HAPTICS) return

        if (hapticEvent.allowCustomDuration && (settings.mKeypressVibrationDuration >= 0 || settings.mKeypressVibrationAmplitude >= 0)) {
            val duration = if (settings.mKeypressVibrationDuration >= 0) settings.mKeypressVibrationDuration else 15
            vibrate(duration.toLong(), settings.mKeypressVibrationAmplitude)
            return
        }

        viewToPerformHapticFeedbackOn?.performHapticFeedback(
            hapticEvent.feedbackConstant,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        )
    }

    fun onSettingsChanged(settingsValues: SettingsValues?) {
        mSettingsValues = settingsValues
        mSoundOn = reevaluateIfSoundIsOn()
        val context = mContext
        if (context != null && settingsValues?.mKeypressSoundStyle != null) {
            CustomSoundManager.getInstance(context).setSoundPack(settingsValues.mKeypressSoundStyle)
        }
    }

    fun onRingerModeChanged(doNotDisturb: Boolean) {
        mDoNotDisturb = doNotDisturb
        mSoundOn = reevaluateIfSoundIsOn()
    }

    fun onStartInputView() {
        mContext?.let { if (mSoundOn) CustomSoundManager.getInstance(it).onStartInputView() }
    }

    fun onFinishInputView() {
        mContext?.let { CustomSoundManager.getInstance(it).onFinishInputView() }
    }

    fun onDestroy() {
        mContext?.let { CustomSoundManager.getInstance(it).onDestroy() }
    }

    companion object {
        private val sInstance = AudioAndHapticFeedbackManager()

        fun getInstance(): AudioAndHapticFeedbackManager = sInstance

        fun init(context: Context) {
            sInstance.initInternal(context)
        }
    }
}
