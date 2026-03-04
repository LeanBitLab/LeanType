// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.DictionaryInfoUtils
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.settings.NextScreenIcon
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.preferences.LoadGestureLibPreference
import helium314.keyboard.settings.preferences.LoadEmojiLibPreference
import helium314.keyboard.latin.common.Links
import helium314.keyboard.settings.preferences.Preference
import androidx.compose.ui.platform.LocalUriHandler

@Composable
fun LibrariesHubScreen(
    onClickBack: () -> Unit,
    onClickDictionaries: () -> Unit,
) {
    val context = LocalContext.current
    val gestureInstalled = JniUtils.sHaveGestureLib
    
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.libraries_hub_title),
        settings = emptyList(), // Not used because content is provided
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
        ) { innerPadding ->
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding)
                    .padding(vertical = 8.dp)
            ) {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column {
                        // Gesture Library
                        LoadGestureLibPreference(
                            title = stringResource(R.string.libraries_hub_gesture_title),
                            icon = R.drawable.ic_settings_gesture,
                            summary = if (gestureInstalled) stringResource(R.string.libraries_status_active) else stringResource(R.string.libraries_status_not_installed)
                        )

                        // Dictionaries
                        Preference(
                            name = stringResource(R.string.libraries_hub_dictionary_title),
                            description = "", // No description
                            onClick = onClickDictionaries,
                            icon = R.drawable.ic_dictionary
                        ) { NextScreenIcon() }

                        // Emoji Libraries
                        val emojiDicts = DictionaryInfoUtils.getLocalesWithEmojiDicts(context)
                        LoadEmojiLibPreference(
                            title = stringResource(R.string.libraries_hub_emoji_title),
                            summary = if (emojiDicts.isEmpty()) 
                                stringResource(R.string.libraries_status_not_installed)
                            else 
                                stringResource(R.string.libraries_status_active) + ": " + emojiDicts.joinToString { it.displayLanguage },
                            icon = R.drawable.ic_emoji_smileys_emotion
                        )

                        // Documentation & Features
                        val uriHandler = LocalUriHandler.current
                        Preference(
                            name = "Features Guide",
                            description = "View the detailed features.md guide on GitHub",
                            onClick = { uriHandler.openUri("https://github.com/LeanBitLab/HeliboardL/blob/main/docs/FEATURES.md") },
                            icon = R.drawable.ic_settings_about_wiki
                        ) { NextScreenIcon() }
                    }
                }
            }
        }
    }
}
