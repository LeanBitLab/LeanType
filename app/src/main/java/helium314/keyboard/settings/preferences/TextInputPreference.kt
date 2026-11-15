// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.preferences

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.dialogs.TextInputDialog
import androidx.annotation.DrawableRes
import androidx.core.content.edit

@Composable
fun TextInputPreference(
    setting: Setting,
    default: String,
    @DrawableRes icon: Int = 0,
    checkTextValid: (String) -> Boolean = { true },
    singleLine: Boolean = true,
    showValue: Boolean = true,
    neutralButtonText: String? = null,
    onNeutral: () -> Unit = { },
    onConfirmed: (String) -> Unit = { }
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val prefs = LocalContext.current.prefs()
    Preference(
        name = setting.title,
        icon = icon.takeIf { it != 0 },
        onClick = { showDialog = true },
        description = if (showValue) prefs.getString(setting.key, default)?.takeIf { it.isNotEmpty() } else null
    )
    if (showDialog) {
        TextInputDialog(
            onDismissRequest = { showDialog = false },
            onConfirmed = {
                prefs.edit { putString(setting.key, it) }
                onConfirmed(it)
                KeyboardSwitcher.getInstance().setThemeNeedsReload()
            },
            initialText = prefs.getString(setting.key, default) ?: "",
            title = { Text(setting.title) },
            checkTextValid = checkTextValid,
            singleLine = singleLine,
            neutralButtonText = neutralButtonText,
            onNeutral = onNeutral
        )
    }
}
