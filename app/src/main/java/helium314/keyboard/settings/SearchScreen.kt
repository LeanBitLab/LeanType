// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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

import helium314.keyboard.latin.utils.prefs
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import helium314.keyboard.settings.preferences.LocalPreferenceLongClick
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
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
                    // one lazy row per setting (a whole card as one item composed every setting of the screen at once,
                    // which is what made opening a large screen slow); the rows of a group are drawn as one card
                    val rows = remember(groups) { settingRows(groups) }

                    // jump from a long press in the search results (see SettingsJump): show the setting
                    // here, opening its fine-tune section if needed, scroll to it and highlight it
                    val listState = rememberLazyListState()
                    var highlightKey by remember { mutableStateOf<String?>(null) }
                    val jumpTarget = jumpTarget()
                    val jumpOpened = jumpOpenedSections()
                    val jump = jumpState()
                    // the setting to scroll to once the list shows it; separate from the jump state, so that
                    // ending the jump does not cancel the scrolling
                    var scrollKey by remember { mutableStateOf<String?>(null) }
                    LaunchedEffect(groups, jumpTarget) {
                        val target = jumpTarget ?: return@LaunchedEffect
                        val shown = groups.flatMap { it.second }
                        val prefs = context.prefs()
                        when (val step = nextJumpStep(target, settings, shown, jumpOpened) { prefs.getBoolean(it, false) }) {
                            // the screen shows more settings then, and this runs again
                            is JumpStep.OpenSections -> jump.setOpenedSections(step.sections)
                            JumpStep.GiveUp -> { jump.setOpenedSections(emptySet()); jump.done() }
                            is JumpStep.Show -> {
                                // closing the other sections changes the list, this runs again after that
                                if (step.keepOpen != jumpOpened) jump.setOpenedSections(step.keepOpen)
                                else { scrollKey = target; jump.done() }
                            }
                        }
                    }
                    LaunchedEffect(scrollKey) {
                        val target = scrollKey ?: return@LaunchedEffect
                        val index = rows.indexOfFirst { it.key == target }
                        if (index >= 0) {
                            listState.animateScrollToItem(index + if (headerContent != null) 1 else 0)
                            highlightKey = target
                            delay(2500)
                            highlightKey = null
                        }
                        scrollKey = null
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
                        items(rows, key = { it.id }) { row ->
                            val shape = androidx.compose.foundation.shape.RoundedCornerShape(
                                topStart = if (row.first) 12.dp else 0.dp, topEnd = if (row.first) 12.dp else 0.dp,
                                bottomStart = if (row.last) 12.dp else 0.dp, bottomEnd = if (row.last) 12.dp else 0.dp
                            )
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = if (row.first) 8.dp else 0.dp, bottom = if (row.last) 8.dp else 0.dp)
                                    .clip(shape)
                                    // an open fine-tune section (its header and the settings inside) is shaded apart from the card
                                    .background(
                                        if (row.inOpenSection) MaterialTheme.colorScheme.surfaceContainerHigh
                                        else MaterialTheme.colorScheme.surfaceContainer
                                    )
                            ) {
                                if (row.key == null) {
                                    PreferenceCategory(stringResource(row.titleRes!!))
                                } else {
                                    val key = row.key
                                    val highlight by animateColorAsState(
                                        // a tint, not a solid color: the texts keep their contrast in light and dark themes
                                        if (key == highlightKey) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                        else androidx.compose.ui.graphics.Color.Transparent,
                                        label = "jump highlight"
                                    )
                                    // a setting further down in a large card is not on screen after scrolling to it
                                    val bringIntoView = remember { BringIntoViewRequester() }
                                    LaunchedEffect(highlightKey) { if (key == highlightKey) bringIntoView.bringIntoView() }
                                    Box(Modifier.bringIntoViewRequester(bringIntoView).background(highlight)) {
                                        SettingsActivity.settingsContainer[key]?.Preference()
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
                // fine-tune section headers do nothing in the results (the settings inside are listed themselves)
                if (key.startsWith(FINE_TUNE_SECTION_PREFIX)) return@filter false
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
        itemContent = { setting -> SearchResult(setting) }
    )
}

/**
 * A row of a settings screen: the title of a category ([key] is null) or a setting; first and last row of a card.
 * [nested]: the setting is inside an open fine-tune section, i.e. after a fine-tune header in the same card.
 * [inOpenSection]: the row is nested or the header of a fine-tune section with settings shown inside.
 */
private data class SettingRow(
    val id: String, val titleRes: Int?, val key: String?, val first: Boolean = false, val last: Boolean = false,
    val nested: Boolean = false, val inOpenSection: Boolean = false
)

private fun settingRows(groups: List<Pair<Int?, List<String>>>): List<SettingRow> = buildList {
    groups.forEachIndexed { index, (titleRes, keys) ->
        val group = buildList {
            if (titleRes != null) add(SettingRow("title_${index}_$titleRes", titleRes, null))
            var inFineTune = false
            keys.forEach {
                val isFineTuneHeader = it.startsWith(FINE_TUNE_SECTION_PREFIX)
                add(SettingRow("${index}_$it", null, it, nested = inFineTune && !isFineTuneHeader))
                if (isFineTuneHeader) inFineTune = true
            }
        }
        group.forEachIndexed { i, row ->
            val inOpenSection = row.nested || group.getOrNull(i + 1)?.nested == true
            add(row.copy(first = i == 0, last = i == group.lastIndex, inOpenSection = inOpenSection))
        }
    }
}

/** A search result: a long press opens the screen of the setting and shows it there */
@Composable
private fun SearchResult(setting: Setting) {
    val navController = LocalSettingsNavController.current
    val destination = SettingsActivity.settingsContainer.screenOf(setting.key)
    if (navController == null || destination == null) {
        setting.Preference()
        return
    }
    CompositionLocalProvider(LocalPreferenceLongClick provides { jumpToSetting(navController, destination, setting.key) }) {
        setting.Preference()
    }
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
    /** more top bar buttons, after the search button */
    actions: (@Composable () -> Unit)? = null,
    // keep content last: callers pass it as a trailing lambda
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
                            BackButton { // same as BackHandler above
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
                                actions?.invoke()
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
