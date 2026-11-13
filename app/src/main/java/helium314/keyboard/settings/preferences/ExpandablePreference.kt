// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.preferences

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.core.content.edit
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.jumpOpenedSections
import helium314.keyboard.settings.jumpState
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun ExpandablePreference(
    setting: Setting,
    defaultExpanded: Boolean = false,
) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val activity = ctx.getActivity() as? SettingsActivity
    val prefChanged = (activity?.prefChanged ?: MutableStateFlow(0)).collectAsState()
    val saved = remember(prefChanged.value) {
        prefs.getBoolean(setting.key, defaultExpanded)
    }
    // may be open only to show a setting from the search, without changing what is saved
    val expanded = saved || setting.key in jumpOpenedSections()
    val jump = jumpState()
    Preference(
        name = setting.title,
        description = setting.description,
        onClick = {
            val newExpanded = !expanded
            prefs.edit { putBoolean(setting.key, newExpanded) }
            jump.userToggledSection(setting.key)
            activity?.prefChanged()
        }
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_arrow_right),
            contentDescription = null,
            modifier = Modifier.rotate(if (expanded) 90f else 0f)
        )
    }
}
