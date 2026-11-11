// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.StorageItem
import helium314.keyboard.latin.utils.StorageItemType
import helium314.keyboard.latin.utils.StorageManagerHelper
import helium314.keyboard.latin.utils.StorageOverview
import helium314.keyboard.settings.DeleteButton
import helium314.keyboard.settings.FeedbackManager
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.preferences.PreferenceCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun StorageScreen(
    onClickBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var overview by remember { mutableStateOf<StorageOverview?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var itemToDelete by remember { mutableStateOf<StorageItem?>(null) }
    var showClearCacheConfirm by remember { mutableStateOf(false) }
    var showPruneConfirm by remember { mutableStateOf(false) }

    fun refresh() {
        isLoading = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                StorageManagerHelper.getOverview(context)
            }
            overview = result
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.libraries_storage_cache_title),
        settings = emptyList(),
    ) {
        if (isLoading && overview == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@SearchSettingsScreen
        }

        val data = overview ?: return@SearchSettingsScreen

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
        ) {
            // Header: Overview & Quick Actions
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.storage_overview_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StorageMetric(
                            label = stringResource(R.string.storage_internal_data),
                            value = StorageManagerHelper.formatBytes(context, data.totalDataBytes)
                        )
                        StorageMetric(
                            label = stringResource(R.string.storage_cache),
                            value = StorageManagerHelper.formatBytes(context, data.totalCacheBytes)
                        )
                        StorageMetric(
                            label = stringResource(R.string.storage_total_used),
                            value = StorageManagerHelper.formatBytes(context, data.totalAppBytes),
                            highlight = true
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showClearCacheConfirm = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.storage_clear_cache_button))
                        }

                        FilledTonalButton(
                            onClick = { showPruneConfirm = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.storage_prune_files_button))
                        }
                    }
                }
            }

            // Section 1: Installed Plugins
            StorageCategoryCard(
                title = stringResource(R.string.storage_installed_plugins, data.plugins.size),
                items = data.plugins,
                emptyText = stringResource(R.string.storage_no_plugins),
                onDeleteItem = { itemToDelete = it }
            )

            // Section 2: Offline Translation Models
            StorageCategoryCard(
                title = stringResource(R.string.storage_translation_models, data.translationModels.size),
                items = data.translationModels,
                emptyText = stringResource(R.string.storage_no_translation_models),
                onDeleteItem = { itemToDelete = it }
            )

            // Section 3: Offline Handwriting Models
            StorageCategoryCard(
                title = stringResource(R.string.storage_handwriting_models, data.handwritingModels.size),
                items = data.handwritingModels,
                emptyText = stringResource(R.string.storage_no_handwriting_models),
                onDeleteItem = { itemToDelete = it }
            )

            // Section 4: Cached Dictionaries
            StorageCategoryCard(
                title = stringResource(R.string.storage_cached_dictionaries, data.dictionaries.size),
                items = data.dictionaries,
                emptyText = stringResource(R.string.storage_no_dictionaries),
                onDeleteItem = { itemToDelete = it }
            )

            // Section 5: Cache & Temporary Files
            StorageCategoryCard(
                title = stringResource(R.string.storage_cache_temp_files, data.cacheItems.size),
                items = data.cacheItems,
                emptyText = stringResource(R.string.storage_cache_clean),
                onDeleteItem = { itemToDelete = it }
            )
        }
    }

    // Confirmation Dialog for deleting an individual item
    val targetItem = itemToDelete
    if (targetItem != null) {
        val typeLabel = when (targetItem.type) {
            StorageItemType.PLUGIN -> stringResource(R.string.storage_type_plugin)
            StorageItemType.TRANSLATION_MODEL -> stringResource(R.string.storage_type_translation_model)
            StorageItemType.HANDWRITING_MODEL -> stringResource(R.string.storage_type_handwriting_model)
            StorageItemType.DICTIONARY -> stringResource(R.string.storage_type_dictionary)
            StorageItemType.CACHE -> stringResource(R.string.storage_type_cache)
        }
        ConfirmationDialog(
            onDismissRequest = { itemToDelete = null },
            onConfirmed = {
                val item = targetItem
                itemToDelete = null
                scope.launch {
                    withContext(Dispatchers.IO) {
                        item.onDelete()
                    }
                    FeedbackManager.message(context, context.getString(R.string.storage_deleted_item, item.name))
                    refresh()
                }
            },
            title = { Text(stringResource(R.string.storage_delete_confirm_title)) },
            content = {
                Text(stringResource(R.string.storage_delete_confirm_message, typeLabel, targetItem.name, StorageManagerHelper.formatBytes(context, targetItem.sizeBytes)))
            },
            confirmButtonText = stringResource(R.string.delete)
        )
    }

    // Confirmation Dialog for clearing all cache
    if (showClearCacheConfirm) {
        ConfirmationDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            onConfirmed = {
                showClearCacheConfirm = false
                scope.launch {
                    val freed = withContext(Dispatchers.IO) {
                        StorageManagerHelper.clearAllCache(context)
                    }
                    FeedbackManager.message(context, context.getString(R.string.storage_cache_cleared_freed, StorageManagerHelper.formatBytes(context, freed)))
                    refresh()
                }
            },
            title = { Text(stringResource(R.string.storage_clear_cache_title)) },
            content = {
                Text(stringResource(R.string.storage_clear_cache_message))
            },
            confirmButtonText = stringResource(R.string.storage_button_clear)
        )
    }

    // Confirmation Dialog for pruning redundant files
    if (showPruneConfirm) {
        ConfirmationDialog(
            onDismissRequest = { showPruneConfirm = false },
            onConfirmed = {
                showPruneConfirm = false
                scope.launch {
                    val freed = withContext(Dispatchers.IO) {
                        StorageManagerHelper.pruneRedundantFiles(context)
                    }
                    FeedbackManager.message(context, context.getString(R.string.storage_prune_complete_freed, StorageManagerHelper.formatBytes(context, freed)))
                    refresh()
                }
            },
            title = { Text(stringResource(R.string.storage_prune_confirm_title)) },
            content = {
                Text(stringResource(R.string.storage_prune_confirm_message))
            },
            confirmButtonText = stringResource(R.string.storage_button_prune)
        )
    }
}

@Composable
private fun StorageMetric(
    label: String,
    value: String,
    highlight: Boolean = false
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.SemiBold,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun StorageCategoryCard(
    title: String,
    items: List<StorageItem>,
    emptyText: String,
    onDeleteItem: (StorageItem) -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(bottom = 4.dp)) {
            PreferenceCategory(title)

            if (items.isEmpty()) {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            } else {
                items.forEach { item ->
                    StorageItemRow(
                        item = item,
                        formattedSize = StorageManagerHelper.formatBytes(context, item.sizeBytes),
                        onDelete = { onDeleteItem(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StorageItemRow(
    item: StorageItem,
    formattedSize: String,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (item.description.isNotBlank()) {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = formattedSize,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        DeleteButton(
            onClick = onDelete,
            tint = MaterialTheme.colorScheme.error
        )
    }
}
