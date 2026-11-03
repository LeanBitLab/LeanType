// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.remember
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.settings.preferences.PreferenceCategory

import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs

@Composable
fun SearchSettingsScreen(
    onClickBack: () -> Unit,
    title: String,
    settings: List<Any?>,
    headerContent: (@Composable () -> Unit)? = null,
    content: @Composable (ColumnScope.() -> Unit)? = null // overrides settings if not null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val customCount = context.prefs().getInt("custom_layouts_count", 0)
    SearchScreen(
        onClickBack = onClickBack,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        },
        headerContent = headerContent,
        content = {
            if (content != null) content()
            else {
                Scaffold(
                    contentWindowInsets = WindowInsets(0),
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                ) { innerPadding ->
                    // Calculate groups only when settings change
                    val groups = remember(settings) {
                        val result = mutableListOf<Pair<Int?, MutableList<String>>>()
                        var currentGroup = mutableListOf<String>()
                        var currentTitle: Int? = null
                        
                        // Initial group (if starts without category)
                        result.add(null to currentGroup)

                        settings.forEach { item ->
                            if (item is Int) {
                                currentTitle = item
                                currentGroup = mutableListOf()
                                result.add(currentTitle to currentGroup)
                            } else if (item is String) {
                                currentGroup.add(item)
                            }
                        }
                        result.filter { it.second.isNotEmpty() }
                    }

                    // jump from a long press in the search results: scroll to the setting and highlight it
                    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                    var highlightKey by remember { mutableStateOf<String?>(null) }
                    // fine-tune sections opened only to find the setting, and the setting to scroll to
                    var openedForJump by remember { mutableStateOf(emptySet<String>()) }
                    var scrollKey by remember { mutableStateOf<String?>(null) }
                    LaunchedEffect(groups) {
                        val keysOnScreen = groups.flatMap { it.second }
                        if (scrollKey == null) {
                            val found = SettingsJump.take(keysOnScreen)
                            val step = jumpThroughFineTuneSections(context, settings, openedForJump, found)
                            openedForJump = step.opened
                            if (step.prefsChanged) (context.getActivity() as? SettingsActivity)?.prefChanged()
                            if (found == null) return@LaunchedEffect
                            scrollKey = found
                            // closing the other sections changes the list, scroll after that
                            if (step.prefsChanged) return@LaunchedEffect
                        }
                        val jumpKey = scrollKey ?: return@LaunchedEffect
                        scrollKey = null
                        if (jumpKey !in keysOnScreen) return@LaunchedEffect
                        val index = groups.indexOfFirst { jumpKey in it.second }
                        highlightKey = jumpKey
                        listState.animateScrollToItem(index + if (headerContent != null) 1 else 0)
                        kotlinx.coroutines.delay(2500)
                        highlightKey = null
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
                    ) {
                        if (headerContent != null) {
                            item {
                                headerContent()
                            }
                        }
                        items(groups, key = { (titleRes, keys) -> "${titleRes}_${keys.hashCode()}" }) { (titleRes, keys) ->
                            androidx.compose.material3.Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                colors = androidx.compose.material3.CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                 )
                            ) {
                                Column {
                                    if (titleRes != null) {
                                        PreferenceCategory(stringResource(titleRes))
                                    }
                                    keys.forEach { key ->
                                        androidx.compose.runtime.key(key) {
                                            val highlight by androidx.compose.animation.animateColorAsState(
                                                if (key == highlightKey) MaterialTheme.colorScheme.primaryContainer
                                                else androidx.compose.ui.graphics.Color.Transparent,
                                                label = "jump highlight"
                                            )
                                            Box(Modifier.background(highlight)) {
                                                SettingsActivity.settingsContainer[key]?.Preference()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        filteredItems = { 
            SettingsActivity.settingsContainer.filter(it).filter { setting ->
                val key = setting.key
                if (key.startsWith(helium314.keyboard.latin.settings.Settings.PREF_LAYOUT_PREFIX + "CUSTOM")) {
                    val index = key.removePrefix(helium314.keyboard.latin.settings.Settings.PREF_LAYOUT_PREFIX + "CUSTOM").toIntOrNull() ?: 0
                    if (index > customCount) return@filter false
                }
                if (key == "add_custom_layout") return@filter false
                when (helium314.keyboard.latin.BuildConfig.FLAVOR) {
                    "offline" -> {
                        val isOldAndroid = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O
                        !key.startsWith("gemini") &&
                        !key.startsWith("groq") &&
                        !key.startsWith("huggingface") &&
                        !key.startsWith("ai_provider") &&
                        (!isOldAndroid || (!key.startsWith("offline_model_path") && !key.startsWith("load_offline_ai_plugin") && !key.startsWith("custom_ai_")))
                    }
                    else -> true
                }
            } 
        },
        // long press on a search result opens the screen where the setting is
        itemContent = { setting -> JumpOnLongPress(setting.key) { setting.Preference() } }
    )
}

private const val FINE_TUNE_SECTION_PREFIX = "expand_fine_tune_"

internal class JumpStep(val opened: Set<String>, val prefsChanged: Boolean)

/**
 * A setting in a closed fine-tune section is not in the list. Then all closed sections of the screen are
 * opened; once the setting is [found], only the section that contains it stays open. If the setting is
 * still missing (hidden for another reason), the sections are closed again.
 */
internal fun jumpThroughFineTuneSections(
    context: android.content.Context,
    settings: List<Any?>,
    opened: Set<String>,
    found: String?,
): JumpStep {
    val prefs = context.prefs()
    fun close(keys: Collection<String>) = prefs.edit().apply { keys.forEach { putBoolean(it, false) } }.apply()
    if (found != null) {
        if (opened.isEmpty()) return JumpStep(emptySet(), false)
        val index = settings.indexOf(found)
        val owner = settings.subList(0, index.coerceAtLeast(0)).lastOrNull { it is String && it in opened }
        val toClose = opened - setOfNotNull(owner as String?)
        close(toClose)
        return JumpStep(emptySet(), toClose.isNotEmpty())
    }
    if (opened.isNotEmpty()) {
        close(opened)
        return JumpStep(emptySet(), true)
    }
    if (!SettingsJump.isPending()) return JumpStep(emptySet(), false)
    val closed = settings.filterIsInstance<String>()
        .filter { it.startsWith(FINE_TUNE_SECTION_PREFIX) && !prefs.getBoolean(it, false) }
    if (closed.isEmpty()) return JumpStep(emptySet(), false)
    prefs.edit().apply { closed.forEach { putBoolean(it, true) } }.apply()
    return JumpStep(closed.toSet(), true)
}

/** A setting to show and highlight on its screen after a long press in the search results */
object SettingsJump {
    private const val VALID_MS = 3000L
    private var key: String? = null
    private var time = 0L

    fun request(key: String) {
        this.key = key
        time = android.os.SystemClock.uptimeMillis()
    }

    fun isPending() = key != null && android.os.SystemClock.uptimeMillis() - time <= VALID_MS

    /** The requested key if it is on this screen; it is then used up */
    fun take(keysOnScreen: Collection<String>): String? {
        val k = key ?: return null
        if (android.os.SystemClock.uptimeMillis() - time > VALID_MS) { key = null; return null }
        if (k !in keysOnScreen) return null
        key = null
        return k
    }
}

@Composable
private fun JumpOnLongPress(key: String, content: @Composable () -> Unit) {
    val destination = SettingsActivity.settingsContainer.screenOf(key)
    if (destination == null) {
        content()
        return
    }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Box(Modifier.pointerInput(key) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            // null on timeout: the finger stayed down without moving, a long press
            val endedEarly = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                }
                true
            }
            if (endedEarly == null) {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                SettingsJump.request(key)
                SettingsDestination.navigateTo(destination)
                // the setting itself must not react to this touch
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
            }
        }
    }) { content() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T: Any?> SearchScreen(
    onClickBack: () -> Unit,
    title: @Composable () -> Unit,
    filteredItems: (String) -> List<T>,
    itemContent: @Composable (T) -> Unit,
    icon: @Composable (() -> Unit)? = null,
    menu: List<Pair<String, () -> Unit>>? = null,
    headerContent: (@Composable () -> Unit)? = null,
    content: @Composable (ColumnScope.() -> Unit)? = null,
) {
    // searchText and showSearch should have the same remember or rememberSaveable
    // saveable survives orientation changes and switching between screens, but shows the
    // keyboard in unexpected situations such as going back from another screen, which is rather annoying
    var searchText by remember { mutableStateOf(TextFieldValue()) }
    var showSearch by remember { mutableStateOf(false) }
    Scaffold(contentWindowInsets = WindowInsets(0))
    { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {

            fun setShowSearch(value: Boolean) {
                showSearch = value
                if (!value) searchText = TextFieldValue()
            }
            BackHandler {
                if (showSearch || searchText.text.isNotEmpty()) setShowSearch(false)
                else onClickBack()
            }
            Surface(
                color = androidx.compose.ui.graphics.Color.Transparent,
            ) {
                Column {
                    TopAppBar(
                        title = title,
                        windowInsets = WindowInsets(0),
                        navigationIcon = {
                            BackButton {
                                if (showSearch) setShowSearch(false)
                                else onClickBack()
                            }
                        },
                        actions = {
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier.padding(end = 12.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                if (icon == null)
                                    IconButton(onClick = { setShowSearch(!showSearch) }) { SearchIcon() }
                                else
                                    icon()
                                if (menu != null)
                                    Box {
                                        var showMenu by remember { mutableStateOf(false) }
                                        IconButton(
                                            onClick = { showMenu = true }
                                        ) { Icon(painterResource(R.drawable.ic_arrow_left), "menu", Modifier.rotate(-90f)) }
                                        DropdownMenu(
                                            expanded = showMenu,
                                            onDismissRequest = { showMenu = false }
                                        ) {
                                            menu.forEach {
                                                DropdownMenuItem(
                                                    text = { Text(it.first) },
                                                    onClick = { showMenu = false; it.second() }
                                                )
                                            }
                                        }
                                    }
                            }
                        },
                    )
                    ExpandableSearchField(
                        expanded = showSearch,
                        onDismiss = { setShowSearch(false) },
                        search = searchText,
                        onSearchChange = { searchText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                        )
                    )
                }
            }
            CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyLarge) {
                if (searchText.text.isBlank() && content != null) {
                    Column {
                        content()
                    }
                } else {
                    val items = remember(searchText.text, filteredItems) { filteredItems(searchText.text) }
                    Scaffold(
                        contentWindowInsets = WindowInsets(0)
                    ) { innerPadding ->
                        LazyColumn(contentPadding = innerPadding) {
                            if (searchText.text.isBlank() && headerContent != null) {
                                item {
                                    headerContent()
                                }
                            }
                            items(items) {
                                itemContent(it)
                            }
                        }
                    }
                }
            }
        }
    }
}

// from StreetComplete
/** Expandable text field that can be dismissed and requests focus when it is expanded */
@Composable
fun ExpandableSearchField(
    expanded: Boolean,
    onDismiss: () -> Unit,
    search: TextFieldValue,
    onSearchChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    colors: TextFieldColors = TextFieldDefaults.colors(),
    placeholder: @Composable (() -> Unit)? = { Text(stringResource(R.string.search_placeholder)) },
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(expanded) {
        if (expanded) focusRequester.requestFocus()
    }
    AnimatedVisibility(visible = expanded, modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = search,
            onValueChange = onSearchChange,
            placeholder = placeholder,
            modifier = modifier
                .focusRequester(focusRequester)
                .clip(androidx.compose.foundation.shape.CircleShape),
            trailingIcon = { IconButton(onClick = {
                if (search.text.isBlank()) onDismiss()
                else onSearchChange(TextFieldValue())
            }) { CloseIcon(android.R.string.cancel) } },
            singleLine = true,
            shape = androidx.compose.foundation.shape.CircleShape,
            colors = colors,
            textStyle = contentTextDirectionStyle,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
        )
    }
}
