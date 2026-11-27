// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Constants

/**
 * Text field to try the keyboard directly in the settings, e.g. to see a changed theme or key size
 * without leaving the settings. Shown by a button in the top bar, see [SearchScreen]. It gets the
 * focus when shown, so the keyboard opens. Nothing typed here is learned.
 */
@Composable
fun KeyboardTestField(onClose: () -> Unit, modifier: Modifier = Modifier) {
    var text by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val packageName = LocalContext.current.packageName
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(stringResource(R.string.keyboard_test_field)) },
        supportingText = { Text(stringResource(R.string.keyboard_test_field_hint)) },
        trailingIcon = {
            IconButton(onClick = { if (text.isEmpty()) onClose() else text = "" }) {
                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.delete))
            }
        },
        // like IME_FLAG_NO_PERSONALIZED_LEARNING, which Compose can't set
        keyboardOptions = KeyboardOptions(
            platformImeOptions = PlatformImeOptions("$packageName.${Constants.ImeOption.NO_PERSONALIZED_LEARNING}")
        ),
        textStyle = contentTextDirectionStyle.merge(MaterialTheme.typography.bodyLarge),
        maxLines = 3,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .focusRequester(focusRequester)
    )
}
