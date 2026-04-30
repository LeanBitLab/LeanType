/*
 * Copyright (C) 2026 LeanBitLab
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.ai

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class WechatAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        private const val WECHAT_PACKAGE = "com.tencent.mm"
        private const val MAX_MESSAGES = 12
        private const val MAX_MESSAGE_CHARS = 150
        private const val MAX_TOTAL_CHARS = 2000

        @Volatile
        private var instance: WechatAccessibilityService? = null

        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, WechatAccessibilityService::class.java).flattenToString()
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
        }

        fun readVisibleMessages(): List<String> {
            val root = instance?.rootInActiveWindow ?: return emptyList()
            if (root.packageName?.toString() != WECHAT_PACKAGE) return emptyList()

            val texts = ArrayList<String>()
            collectText(root, texts)
            var total = 0
            return texts
                .asReversed()
                .distinct()
                .filter { it.isNotBlank() }
                .take(MAX_MESSAGES)
                .asReversed()
                .mapNotNull { text ->
                    val clipped = text.take(MAX_MESSAGE_CHARS)
                    if (total + clipped.length > MAX_TOTAL_CHARS) return@mapNotNull null
                    total += clipped.length
                    clipped
                }
        }

        private fun collectText(node: AccessibilityNodeInfo, out: MutableList<String>) {
            val nodePackage = node.packageName?.toString()
            if (nodePackage != null && nodePackage != WECHAT_PACKAGE) return

            val text = node.text?.toString()?.trim()
            if (!text.isNullOrBlank() && !TextUtils.isDigitsOnly(text)) {
                out.add(text)
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let { child ->
                    collectText(child, out)
                    child.recycle()
                }
            }
        }
    }
}
