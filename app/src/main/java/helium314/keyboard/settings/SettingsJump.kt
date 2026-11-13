// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController

/*
 * Jump from a long press in the settings search to the screen of a setting, which then scrolls to the
 * setting and highlights it. What the screen is asked to show, and the fine-tune sections opened for it,
 * belong to the back stack entry of that screen: nothing is written to the preferences, nothing is kept
 * in a global object, and it ends when the screen is left.
 */

private const val JUMP_TO = "settings_jump_to"
private const val JUMP_OPENED_SECTIONS = "settings_jump_opened_sections"
internal const val FINE_TUNE_SECTION_PREFIX = "expand_fine_tune_"

/** The nav controller of the settings, used by the search results to open the screen of a setting */
val LocalSettingsNavController = staticCompositionLocalOf<NavController?> { null }

/** Opens [destination] and asks it to show the setting [key] */
fun jumpToSetting(navController: NavController, destination: String, key: String) {
    navController.navigate(destination)
    navController.currentBackStackEntry?.savedStateHandle?.set(JUMP_TO, key)
}

/** State of the screen this is called in: null outside of the navigation (e.g. in a preview) */
@Composable
private fun screenState(): SavedStateHandle? = (LocalViewModelStoreOwner.current as? NavBackStackEntry)?.savedStateHandle

/** The setting this screen is asked to show, if any */
@Composable
internal fun jumpTarget(): String? {
    val state = screenState() ?: return null
    val target by state.getStateFlow<String?>(JUMP_TO, null).collectAsState()
    return target
}

/** Fine-tune sections opened on this screen to show a setting; they are not saved */
@Composable
fun jumpOpenedSections(): Set<String> {
    val state = screenState() ?: return emptySet()
    val opened by state.getStateFlow(JUMP_OPENED_SECTIONS, ArrayList<String>()).collectAsState()
    return opened.toSet()
}

/** Whether a fine-tune section is open: as the user left it, or opened to show a setting (see [jumpOpenedSections]) */
@Composable
fun fineTuneExpanded(prefs: SharedPreferences, key: String, default: Boolean): Boolean =
    prefs.getBoolean(key, default) || key in jumpOpenedSections()

/** Changes of this screen's jump state, for use outside of composition */
class JumpState internal constructor(private val state: SavedStateHandle?) {
    fun setOpenedSections(sections: Collection<String>) { state?.set(JUMP_OPENED_SECTIONS, ArrayList(sections)) }
    /** The user opens or closes the section [key]: from now on it is as the user wants */
    fun userToggledSection(key: String) {
        val opened = state?.get<ArrayList<String>>(JUMP_OPENED_SECTIONS) ?: return
        if (key in opened) setOpenedSections(opened - key)
    }
    fun done() { state?.remove<String>(JUMP_TO) }
}

@Composable
fun jumpState() = JumpState(screenState())

/** What to do next to show [target] on a screen */
internal sealed interface JumpStep {
    /** Open these closed fine-tune sections, the setting may be in one of them */
    data class OpenSections(val sections: Set<String>) : JumpStep
    /** The setting is shown; of the sections opened for the jump, keep only [keepOpen] (the one with the setting) */
    data class Show(val keepOpen: Set<String>) : JumpStep
    /** The setting is not on this screen, or hidden for another reason: close the opened sections again */
    data object GiveUp : JumpStep
}

/**
 * [settings]: the screen's list (setting keys and section titles), [shown]: the keys in the list now,
 * [opened]: sections opened for this jump, [isOpen]: whether a section is open as the user left it.
 */
internal fun nextJumpStep(
    target: String,
    settings: List<Any?>,
    shown: Collection<String>,
    opened: Set<String>,
    isOpen: (String) -> Boolean,
): JumpStep {
    if (target in shown) {
        val index = settings.indexOf(target)
        val owner = settings.subList(0, index.coerceAtLeast(0)).lastOrNull { it is String && it in opened } as String?
        return JumpStep.Show(setOfNotNull(owner))
    }
    if (opened.isNotEmpty()) return JumpStep.GiveUp
    val closed = settings.filterIsInstance<String>().filter { it.startsWith(FINE_TUNE_SECTION_PREFIX) && !isOpen(it) }
    return if (closed.isEmpty()) JumpStep.GiveUp else JumpStep.OpenSections(closed.toSet())
}
