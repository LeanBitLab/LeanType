// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.text.InputType
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.isGone
import helium314.keyboard.compat.ClipboardManagerCompat
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import android.graphics.Outline
import android.view.ViewOutlineProvider
import helium314.keyboard.latin.utils.dpToPx
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.isValidNumber
import helium314.keyboard.latin.database.ClipboardDao
import helium314.keyboard.latin.databinding.ClipboardSuggestionBinding
import helium314.keyboard.latin.databinding.ScreenshotSuggestionBinding
import helium314.keyboard.latin.ocr.OcrPluginLoader
import helium314.keyboard.latin.ocr.OcrPipeline
import helium314.keyboard.latin.ocr.ScreenshotHelper
import helium314.keyboard.latin.utils.InputTypeUtils
import helium314.keyboard.latin.utils.ToolbarKey
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import kotlin.concurrent.thread
import helium314.keyboard.latin.utils.ExecutorUtils
import helium314.keyboard.latin.utils.prefs

class ClipboardHistoryManager(
        private val latinIME: LatinIME
) : ClipboardManager.OnPrimaryClipChangedListener {

    private lateinit var clipboardManager: ClipboardManager
    // Cache the main-thread Handler. ClipboardHistoryManager is a
    // singleton scoped to the IME service, so a single Handler bound
    // to the main Looper is fine for the whole process. This avoids
    // allocating a fresh Handler on every postDelayed().
    private val mainHandler = Handler(Looper.getMainLooper())
    private var clipboardSuggestionView: View? = null
    private var _clipboardDao: ClipboardDao? = null
    private var clipboardDao: ClipboardDao?
        get() {
            if (_clipboardDao == null || _clipboardDao?.isClosed == true) {
                _clipboardDao = ClipboardDao.getInstance(latinIME)
            }
            return _clipboardDao
        }
        set(value) {
            _clipboardDao = value
        }
    private var dontShowCurrentSuggestion: Boolean = false
    // ponytail: track last clip state to avoid resetting dismiss state on duplicate events
    private var lastPrimaryClipText: String? = null
    private var lastPrimaryClipUri: String? = null
    private var lastPrimaryClipTimestamp: Long = 0L

    private data class ScreenshotInfo(
        val uri: Uri,
        val fileName: String,
        val fullPath: String,
        val dateAdded: Long
    )

    @Volatile
    private var cachedScreenshotInfo: ScreenshotInfo? = null

    private var screenshotObserver: ContentObserver? = null
    private var screenshotDebounceRunnable: Runnable? = null

    private fun registerScreenshotObserver() {
        if (screenshotObserver != null) return
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (latinIME.checkCallingOrSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        try {
            val observer = object : ContentObserver(mainHandler) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    super.onChange(selfChange, uri)
                    if (!latinIME.isInputViewShown) return
                    if (latinIME.mSettings.current.mSuggestScreenshots) {
                        screenshotDebounceRunnable?.let { mainHandler.removeCallbacks(it) }
                        val runnable = Runnable {
                            if (!latinIME.isInputViewShown) return@Runnable
                            updateLatestScreenshotCache {
                                latinIME.tryShowClipboardSuggestion()
                            }
                        }
                        screenshotDebounceRunnable = runnable
                        mainHandler.postDelayed(runnable, 300L)
                    }
                }
            }
            screenshotObserver = observer
            latinIME.contentResolver.registerContentObserver(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
        } catch (e: Exception) {
            // Ignore observer registration failures
        }
    }

    private fun unregisterScreenshotObserver() {
        screenshotDebounceRunnable?.let {
            mainHandler.removeCallbacks(it)
            screenshotDebounceRunnable = null
        }
        try {
            screenshotObserver?.let {
                latinIME.contentResolver.unregisterContentObserver(it)
                screenshotObserver = null
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun updateLatestScreenshotCache(onComplete: (() -> Unit)? = null) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (latinIME.checkCallingOrSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            cachedScreenshotInfo = null
            onComplete?.invoke()
            return
        }
        if (!ScreenshotHelper.isExternalStorageAvailable(latinIME)) {
            cachedScreenshotInfo = null
            onComplete?.invoke()
            return
        }

        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute {
            val projection = mutableListOf(
                android.provider.MediaStore.Images.Media._ID,
                android.provider.MediaStore.Images.Media.DISPLAY_NAME,
                android.provider.MediaStore.Images.Media.DATE_ADDED
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                projection.add(android.provider.MediaStore.Images.Media.RELATIVE_PATH)
            } else {
                @Suppress("DEPRECATION")
                projection.add(android.provider.MediaStore.Images.Media.DATA)
            }
            
            val sortOrder = "${android.provider.MediaStore.Images.Media.DATE_ADDED} DESC"
            
            try {
                val cursor = latinIME.contentResolver.query(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection.toTypedArray(),
                    null,
                    null,
                    sortOrder
                )
                
                cursor?.use {
                    var count = 0
                    while (it.moveToNext() && count < 10) {
                        count++
                        val dateIndex = it.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DATE_ADDED)
                        val dateAdded = it.getLong(dateIndex) * 1000L
                        val diff = System.currentTimeMillis() - dateAdded
                        
                        if (diff < RECENT_SCREENSHOT_TIME_MILLIS) {
                            val nameIndex = it.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DISPLAY_NAME)
                            val fileName = it.getString(nameIndex) ?: ""
                            
                            var fullPath = "Unknown"
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val relIndex = it.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.RELATIVE_PATH)
                                fullPath = it.getString(relIndex) ?: ""
                            } else {
                                val dataIndex = it.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DATA)
                                @Suppress("DEPRECATION")
                                fullPath = it.getString(dataIndex) ?: ""
                            }

                            val isScreenshot = fileName.contains("Screenshot", ignoreCase = true)
                                || fileName.contains("Screen", ignoreCase = true)
                                || fullPath.contains("Screenshot", ignoreCase = true)
                                || fullPath.contains("Screenshots", ignoreCase = true)
                                || fullPath.contains("Pictures", ignoreCase = true)
                                || fullPath.contains("DCIM", ignoreCase = true)
                            if (isScreenshot) {
                                val idIndex = it.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media._ID)
                                val id = it.getLong(idIndex)
                                val contentUri = android.content.ContentUris.withAppendedId(
                                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                                )
                                val prefs = latinIME.prefs()
                                val rawDeletedSet = prefs.getStringSet("deleted_screenshot_uris", emptySet()) ?: emptySet()
                                val deletedSet = rawDeletedSet.filter { it.startsWith("content://") || it.contains("clipboard_images") }.toSet()
                                if (deletedSet.contains(contentUri.toString())) {
                                    continue
                                }

                                if (cachedScreenshotInfo?.uri != contentUri) {
                                    dontShowCurrentSuggestion = false
                                }
                                cachedScreenshotInfo = ScreenshotInfo(contentUri, fileName, fullPath, dateAdded)
                                
                                if (latinIME.mSettings.current.mClipboardHistoryEnabled) {
                                    val cachedPath = cacheImage(contentUri)
                                    if (cachedPath != null && !deletedSet.contains(cachedPath) && !deletedSet.contains(contentUri.toString())) {
                                        clipboardDao?.addClip(System.currentTimeMillis(), false, "[Screenshot]", cachedPath)
                                    }
                                }

                                mainHandler.post {
                                    onComplete?.invoke()
                                    latinIME.tryShowClipboardSuggestion()
                                }
                                return@execute
                            }
                        } else {
                            break
                        }
                    }
                }
            } catch (e: IllegalArgumentException) {
                helium314.keyboard.latin.utils.Log.d("ClipboardHistoryManager", "Screenshot volume unavailable: ${e.message}")
            } catch (e: Exception) {
                helium314.keyboard.latin.utils.Log.e("ClipboardHistoryManager", "Failed to query screenshots in background", e)
            }
            cachedScreenshotInfo = null
            mainHandler.post {
                onComplete?.invoke()
            }
        }
    }

    fun stopListening() {
        try {
            if (::clipboardManager.isInitialized) {
                clipboardManager.removePrimaryClipChangedListener(this)
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun onCreate() {
        clipboardManager = latinIME.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (latinIME.prefs().getBoolean(helium314.keyboard.latin.settings.Settings.PREF_ENABLE_CLIPBOARD_LISTENER, helium314.keyboard.latin.settings.Defaults.PREF_ENABLE_CLIPBOARD_LISTENER)) {
            clipboardManager.addPrimaryClipChangedListener(this)
        }
        clipboardDao = ClipboardDao.getInstance(latinIME)
        // ponytail: initialize last clip state
        try {
            val clipData = clipboardManager.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                lastPrimaryClipText = clipData.getItemAt(0)?.coerceToText(latinIME)?.toString()
                lastPrimaryClipUri = clipData.getItemAt(0)?.uri?.toString()
                lastPrimaryClipTimestamp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) clipData.description.timestamp else 0L
            }
        } catch (e: Exception) {
            // Ignore
        }
        if (latinIME.mSettings.current.mClipboardHistoryEnabled)
            ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute { fetchPrimaryClip() }
        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute { cleanUpImageCache() }
    }

    fun onStartInputView() {
        val prefs = latinIME.prefs()
        val lastDismissed = prefs.getString("last_dismissed_screenshot_uri", "")
        if (cachedScreenshotInfo != null && cachedScreenshotInfo?.uri?.toString() != lastDismissed) {
            dontShowCurrentSuggestion = false
        }
        val clipData = try {
            clipboardManager.primaryClip
        } catch (e: Exception) {
            null
        }
        val currentText = if (clipData != null && clipData.itemCount > 0) {
            clipData.getItemAt(0)?.coerceToText(latinIME)?.toString()
        } else {
            null
        }
        val lastDismissedClipboard = prefs.getString("last_dismissed_clipboard_text", "")
        if (!currentText.isNullOrEmpty() && currentText != lastDismissedClipboard && currentText != lastPrimaryClipText) {
            dontShowCurrentSuggestion = false
        }
        if (latinIME.mSettings.current.mSuggestScreenshots) {
            registerScreenshotObserver()
            updateLatestScreenshotCache()
        } else {
            unregisterScreenshotObserver()
        }
    }

    fun onFinishInputView() {
        unregisterScreenshotObserver()
        mainHandler.removeCallbacksAndMessages(null)
    }

    fun onWindowHidden() {
        unregisterScreenshotObserver()
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun cleanUpImageCache() {
        try {
            val cacheDir = java.io.File(latinIME.cacheDir, "clipboard_images")
            if (!cacheDir.exists()) return
            
            val validUris = clipboardDao?.getClips()?.mapNotNull { it.imageUri }?.toSet() ?: emptySet()
            
            cacheDir.listFiles()?.forEach { file ->
                if (!validUris.contains(file.absolutePath)) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            helium314.keyboard.latin.utils.Log.e("ClipboardHistoryManager", "Failed to clean up image cache: " + e.message)
        }
    }

    fun onDestroy() {
        unregisterScreenshotObserver()
        if (::clipboardManager.isInitialized) {
            try {
                clipboardManager.removePrimaryClipChangedListener(this)
            } catch (e: Exception) {
                // Ignore if already unregistered or manager unavailable
            }
        }
        mainHandler.removeCallbacksAndMessages(null)
        ClipboardDao.closeInstance()
    }

    override fun onPrimaryClipChanged() {
        // Read clipboard content if history or suggestion setting is enabled
        if (latinIME.mSettings.current.mClipboardHistoryEnabled || latinIME.mSettings.current.mSuggestClipboardContent) {
            // ponytail: ignore duplicate events where clipboard contents didn't actually change
            val clipData = try {
                clipboardManager.primaryClip
            } catch (e: Exception) {
                null
            }
            val currentText = if (clipData != null && clipData.itemCount > 0) {
                clipData.getItemAt(0)?.coerceToText(latinIME)?.toString()
            } else {
                null
            }
            val currentUri = if (clipData != null && clipData.itemCount > 0) {
                clipData.getItemAt(0)?.uri?.toString()
            } else {
                null
            }
            val currentTimestamp = if (clipData != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                clipData.description.timestamp
            } else {
                0L
            }

            val hasChanged = currentText != lastPrimaryClipText
                    || currentUri != lastPrimaryClipUri
                    || (currentTimestamp != 0L && currentTimestamp != lastPrimaryClipTimestamp)

            if (hasChanged) {
                lastPrimaryClipText = currentText
                lastPrimaryClipUri = currentUri
                lastPrimaryClipTimestamp = currentTimestamp
                dontShowCurrentSuggestion = false
                val prefs = latinIME.prefs()
                prefs.edit().remove("last_dismissed_clipboard_text").apply()

                // Immediately update suggestion strip on UI thread without waiting for background DB I/O
                mainHandler.post {
                    if (latinIME.isInputViewShown) {
                        latinIME.tryShowClipboardSuggestion()
                    }
                }

                if (latinIME.mSettings.current.mClipboardHistoryEnabled) {
                    ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute {
                        fetchPrimaryClip()
                    }
                }
            }
        }
    }

    private fun fetchPrimaryClip() {
        val clipData = try {
            clipboardManager.primaryClip
        } catch (e: Exception) {
            null
        }
        
        if (clipData == null || clipData.itemCount == 0) return
        
        var hasText = clipData.description?.hasMimeType("text/*") == true || clipData.description?.hasMimeType("text/plain") == true || clipData.description?.hasMimeType("text/html") == true
        var hasImage = clipData.description?.hasMimeType("image/*") == true
        
        if (!hasImage && clipData.itemCount > 0) {
            val uri = clipData.getItemAt(0)?.uri
            if (uri != null) {
                val type = latinIME.contentResolver.getType(uri)
                if (type?.startsWith("image/") == true) {
                    hasImage = true
                }
            }
        }
        
        if (!hasText && !hasImage) return
        
        clipData.getItemAt(0)?.let { clipItem ->
            val timeStamp = ClipboardManagerCompat.getClipTimestamp(clipData)
            var content = ""
            var imageUri: String? = null
            
            if (hasImage && clipItem.uri != null) {
                // Determine mime type and copy to local cache
                imageUri = cacheImage(clipItem.uri)
                if (imageUri != null) {
                    content = "[Image]"
                }
            }
            if (hasText && imageUri == null) {
                content = clipItem.coerceToText(latinIME)?.toString() ?: ""
            }
            
            if (TextUtils.isEmpty(content) && imageUri == null) return
            // passwords (marked as sensitive by the copying app) and one-time codes are not kept in the history
            if (latinIME.mSettings.current.mClipboardSkipSensitive && imageUri == null
                && isSensitiveForHistory(clipData.description, content)) return
            clipboardDao?.addClip(timeStamp, false, content, imageUri)
        }
    }

    private fun cacheImage(uri: android.net.Uri): String? {
        try {
            val resolver = latinIME.contentResolver
            val cacheDir = java.io.File(latinIME.cacheDir, "clipboard_images")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            
            val md = java.security.MessageDigest.getInstance("MD5")
            val digest = md.digest(uri.toString().toByteArray())
            val hash = digest.joinToString("") { "%02x".format(it) }
            val suffix = if (latinIME.mSettings.current.mCompressScreenshots) "_compressed" else ""
            val file = java.io.File(cacheDir, "img_${hash}${suffix}.jpg")
            if (file.exists() && file.length() > 0) {
                return file.absolutePath
            }
            
            if (!latinIME.mSettings.current.mCompressScreenshots) {
                resolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                    return file.absolutePath
                }
                return null
            }
            
            resolver.openInputStream(uri)?.use { input ->
                val options = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                android.graphics.BitmapFactory.decodeStream(input, null, options)
                
                resolver.openInputStream(uri)?.use { actualInput ->
                    val reqWidth = 1024
                    val reqHeight = 1024
                    var inSampleSize = 1
                    if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                        val halfHeight = options.outHeight / 2
                        val halfWidth = options.outWidth / 2
                        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                            inSampleSize *= 2
                        }
                    }
                    
                    val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                        this.inSampleSize = inSampleSize
                    }
                    val bitmap = android.graphics.BitmapFactory.decodeStream(actualInput, null, decodeOptions)
                    if (bitmap != null) {
                        file.outputStream().use { output ->
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, output)
                        }
                        bitmap.recycle()
                        return file.absolutePath
                    }
                }
            }
            return null
        } catch (e: Exception) {
            return null
        }
    }

    fun toggleClipPinned(id: Long) {
        clipboardDao?.togglePinned(id)
    }

    fun clearHistory() {
        clipboardDao?.clearNonPinned()
        cachedScreenshotInfo = null
        ClipboardManagerCompat.clearPrimaryClip(clipboardManager)
        removeClipboardSuggestion()
    }

    private fun markScreenshotDeleted(imageUri: String) {
        val prefs = latinIME.prefs()
        val rawDeletedSet = prefs.getStringSet("deleted_screenshot_uris", emptySet()) ?: emptySet()
        val deletedSet = rawDeletedSet.filter { it.startsWith("content://") || it.contains("clipboard_images") }.toMutableSet()
        deletedSet.add(imageUri)
        val curCached = cachedScreenshotInfo
        if (curCached != null) {
            val md = java.security.MessageDigest.getInstance("MD5")
            val hash = md.digest(curCached.uri.toString().toByteArray()).joinToString("") { "%02x".format(it) }
            if (imageUri.contains(hash) || curCached.fullPath == imageUri || curCached.uri.toString() == imageUri) {
                deletedSet.add(curCached.uri.toString())
                prefs.edit().putString("last_dismissed_screenshot_uri", curCached.uri.toString()).apply()
                cachedScreenshotInfo = null
            }
        }
        prefs.edit().putStringSet("deleted_screenshot_uris", deletedSet).apply()
    }

    fun canRemove(index: Int) = clipboardDao?.isPinned(index) == false

    fun removeEntry(index: Int): ClipboardHistoryEntry? {
        if (!canRemove(index)) return null
        val entry = clipboardDao?.deleteClipAt(index)
        if (entry != null) {
            if (entry.imageUri != null) {
                markScreenshotDeleted(entry.imageUri)
            }
            try {
                val primaryText = retrieveClipboardContent().toString()
                if (primaryText == entry.text || (entry.text == "[Screenshot]" && entry.imageUri != null)) {
                    ClipboardManagerCompat.clearPrimaryClip(clipboardManager)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        return entry
    }

    fun deleteEntryById(id: Long): ClipboardHistoryEntry? {
        val entry = clipboardDao?.deleteClipById(id) ?: return null
        if (entry.imageUri != null) {
            markScreenshotDeleted(entry.imageUri)
        }
        try {
            val primaryText = retrieveClipboardContent().toString()
            if (primaryText == entry.text || (entry.text == "[Screenshot]" && entry.imageUri != null)) {
                ClipboardManagerCompat.clearPrimaryClip(clipboardManager)
            }
        } catch (_: Exception) {}
        return entry
    }

    fun deleteEntries(ids: Collection<Long>): List<ClipboardHistoryEntry> {
        return ids.mapNotNull { deleteEntryById(it) }
    }

    fun setClipsPinned(ids: Collection<Long>, pinned: Boolean) {
        ids.forEach { clipboardDao?.setPinned(it, pinned) }
        sortHistoryEntries()
    }

    fun restoreEntry(entry: ClipboardHistoryEntry) {
        clipboardDao?.restoreClip(entry)
    }

    fun updateClipText(id: Long, newText: String) {
        clipboardDao?.updateClipText(id, newText)
    }

    fun sortHistoryEntries() {
        clipboardDao?.sort()
    }

    // We do not want to update history while user is visualizing it, so we check retention only
    // when history is about to be shown
    fun prepareClipboardHistory() = clipboardDao?.clearOldClips(true)

    fun getHistorySize() = clipboardDao?.count() ?: 0

    fun getClips() = clipboardDao?.getClips() ?: emptyList()

    fun getHistoryEntry(position: Int) = clipboardDao?.getAt(position)

    fun getHistoryEntryContent(id: Long) = clipboardDao?.get(id)

    fun setHistoryChangeListener(listener: ClipboardDao.Listener?) {
        clipboardDao?.listener = listener
    }

    fun retrieveClipboardContent(): CharSequence {
        val clipData = clipboardManager.primaryClip ?: return ""
        if (clipData.itemCount == 0) return ""
        return clipData.getItemAt(0)?.coerceToText(latinIME) ?: ""
    }

    /** Clip marked as sensitive (e.g. by password managers), or a one-time code (4 to 8 digits) */
    private fun isSensitiveForHistory(description: android.content.ClipDescription?, content: String): Boolean =
        ClipboardManagerCompat.getClipSensitivity(description) == true || isOneTimeCode(content)

    private fun isClipSensitive(inputType: Int): Boolean {
        ClipboardManagerCompat.getClipSensitivity(clipboardManager.primaryClip?.description)?.let { return it }
        return InputTypeUtils.isPasswordInputType(inputType)
    }

    fun getClipboardSuggestionView(editorInfo: EditorInfo?, parent: ViewGroup?): View? {
        clipboardSuggestionView = null
        if (dontShowCurrentSuggestion) return null
        if (parent == null) return null

        val suggestScreenshot = latinIME.mSettings.current.mSuggestScreenshots
        val suggestText = latinIME.mSettings.current.mSuggestClipboardContent

        // Check text clip availability and timestamp
        val inputType = editorInfo?.inputType ?: InputType.TYPE_NULL
        var textClipContent: CharSequence? = null
        var textClipTimestamp: Long = 0L
        if (suggestText) {
            val clipData = clipboardManager.primaryClip
            if (clipData != null && clipData.itemCount > 0 && clipData.description?.hasMimeType("text/*") != false) {
                val clipItem = clipData.getItemAt(0)
                val timeStamp = ClipboardManagerCompat.getClipTimestamp(clipData)
                if (System.currentTimeMillis() - timeStamp <= RECENT_TIME_MILLIS) {
                    val content = clipItem?.coerceToText(latinIME)
                    val prefs = latinIME.prefs()
                    val lastDismissedText = prefs.getString("last_dismissed_clipboard_text", "")
                    val numberOk = !InputTypeUtils.isNumberInputType(inputType) || content?.isValidNumber() == true
                    if (!TextUtils.isEmpty(content) && content.toString() != lastDismissedText && numberOk) {
                        textClipContent = content
                        textClipTimestamp = timeStamp
                    }
                }
            }
        }

        // Check screenshot availability and timestamp
        var screenshotTimestamp: Long = 0L
        if (suggestScreenshot) {
            val screenshotInfo = cachedScreenshotInfo
            if (screenshotInfo != null) {
                val prefs = latinIME.prefs()
                val lastDismissedScreenshotUri = prefs.getString("last_dismissed_screenshot_uri", "")
                val rawDeletedSet = prefs.getStringSet("deleted_screenshot_uris", emptySet()) ?: emptySet()
                val isDismissed = screenshotInfo.uri.toString() == lastDismissedScreenshotUri ||
                    rawDeletedSet.contains(screenshotInfo.uri.toString()) ||
                    rawDeletedSet.contains(screenshotInfo.fullPath)
                val diff = System.currentTimeMillis() - screenshotInfo.dateAdded
                if (diff < RECENT_SCREENSHOT_TIME_MILLIS && !isDismissed) {
                    screenshotTimestamp = screenshotInfo.dateAdded
                }
            }
        }

        // Prioritize newest: if screenshot is newer than text, try to show screenshot
        if (screenshotTimestamp > 0L && (textClipContent == null || screenshotTimestamp >= textClipTimestamp)) {
            val screenshotView = getScreenshotSuggestionView(parent)
            if (screenshotView != null) {
                clipboardSuggestionView = screenshotView
                return screenshotView
            }
        }

        // Otherwise show text if available
        if (textClipContent != null) {
            val view = buildTextClipboardSuggestionView(textClipContent, inputType, parent)
            clipboardSuggestionView = view
            return view
        }

        // Fallback to screenshot if text is not available
        if (suggestScreenshot) {
            val screenshotView = getScreenshotSuggestionView(parent)
            if (screenshotView != null) {
                clipboardSuggestionView = screenshotView
                return screenshotView
            }
        }

        return null
    }

    private fun buildTextClipboardSuggestionView(content: CharSequence, inputType: Int, parent: ViewGroup): View {
        val binding = ClipboardSuggestionBinding.inflate(LayoutInflater.from(latinIME), parent, false)
        val textView = binding.clipboardSuggestionText
        latinIME.mSettings.getCustomTypeface()?.let { textView.typeface = it }
        textView.text = (if (isClipSensitive(inputType)) "*".repeat(content.length) else content)
            .take(200) // truncate displayed text for performance reasons
        val clipIcon = latinIME.mKeyboardSwitcher.keyboard?.mIconsSet?.getIconDrawable(ToolbarKey.PASTE.name.lowercase())
        textView.setCompoundDrawablesRelativeWithIntrinsicBounds(clipIcon, null, null, null)
        textView.setOnClickListener {
            dontShowCurrentSuggestion = true
            latinIME.onTextInput(content.toString())
            AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, it, HapticEvent.KEY_PRESS)
            binding.root.isGone = true
        }
        val closeButton = binding.clipboardSuggestionClose
        closeButton.setImageDrawable(latinIME.mKeyboardSwitcher.keyboard?.mIconsSet?.getIconDrawable(ToolbarKey.CLOSE_HISTORY.name.lowercase()))
        closeButton.setOnClickListener {
            val prefs = latinIME.prefs()
            prefs.edit().putString("last_dismissed_clipboard_text", content.toString()).apply()
            AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, it, HapticEvent.KEY_PRESS)
            binding.root.isGone = true
            removeClipboardSuggestion()
        }

        val colors = latinIME.mSettings.current.mColors
        textView.setTextColor(colors.get(ColorType.KEY_TEXT))
        clipIcon?.let { colors.setColor(it, ColorType.KEY_ICON) }
        colors.setColor(closeButton, ColorType.REMOVE_SUGGESTION_ICON)
        colors.setBackground(binding.root, ColorType.CLIPBOARD_SUGGESTION_BACKGROUND)

        return binding.root
    }

    private var lastSuggestedScreenshotUri: String? = null

    private fun getScreenshotSuggestionView(parent: ViewGroup?): View? {
        if (parent == null || dontShowCurrentSuggestion) return null
        
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (latinIME.checkCallingOrSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return null
        }

        val screenshotInfo = cachedScreenshotInfo
        if (screenshotInfo == null) {
            updateLatestScreenshotCache()
            return null
        }

        val prefs = latinIME.prefs()
        val lastDismissedScreenshotUri = prefs.getString("last_dismissed_screenshot_uri", "")
        if (screenshotInfo.uri.toString() == lastDismissedScreenshotUri) return null

        val rawDeletedSet = prefs.getStringSet("deleted_screenshot_uris", emptySet()) ?: emptySet()
        if (rawDeletedSet.contains(screenshotInfo.uri.toString()) || rawDeletedSet.contains(screenshotInfo.fullPath)) {
            cachedScreenshotInfo = null
            return null
        }

        val diff = System.currentTimeMillis() - screenshotInfo.dateAdded
        if (diff >= RECENT_SCREENSHOT_TIME_MILLIS) {
            cachedScreenshotInfo = null
            return null
        }

        val contentUri = screenshotInfo.uri
        val isAlreadySuggested = contentUri.toString() == lastSuggestedScreenshotUri

        if (!isAlreadySuggested) {
            if (latinIME.mSettings.current.mClipboardHistoryEnabled) {
                ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute {
                    val cachedPath = cacheImage(contentUri)
                    if (cachedPath != null) {
                        mainHandler.post {
                            clipboardDao?.addClip(System.currentTimeMillis(), false, "[Screenshot]", cachedPath)
                        }
                    }
                }
            }
            lastSuggestedScreenshotUri = contentUri.toString()
        }

        val ocrEnabled = OcrPluginLoader.hasPlugin(latinIME) && latinIME.prefs().getBoolean(OcrPluginLoader.PREF_OCR_SUGGEST_SCREENSHOT_TEXT, true)
        val binding = ScreenshotSuggestionBinding.inflate(LayoutInflater.from(latinIME), parent, false)
        val pasteButton = binding.screenshotPasteButton
        val thumbnailImage = binding.screenshotThumbnailImage
        val extractButton = binding.screenshotExtractTextButton
        val closeButton = binding.screenshotSuggestionClose

        val cornerRadius = 8.dpToPx(latinIME.resources).toFloat()
        pasteButton.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
            }
        }
        pasteButton.clipToOutline = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val thumb = latinIME.contentResolver.loadThumbnail(contentUri, android.util.Size(160, 160), null)
                thumbnailImage.setImageBitmap(thumb)
            } catch (e: Exception) {
                val clipIcon = latinIME.mKeyboardSwitcher.keyboard?.mIconsSet?.getIconDrawable(ToolbarKey.PASTE.name.lowercase())
                thumbnailImage.setImageDrawable(clipIcon)
            }
        }

        if (ocrEnabled) {
            extractButton.visibility = View.VISIBLE
            extractButton.setImageResource(R.drawable.ic_ocr_extract)

            extractButton.setOnClickListener {
                dontShowCurrentSuggestion = true
                lastSuggestedScreenshotUri = contentUri.toString()
                AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, it, HapticEvent.KEY_PRESS)
                binding.root.isGone = true

                ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute {
                    val bitmap = ScreenshotHelper.loadScaledBitmap(latinIME, contentUri)
                    if (bitmap != null) {
                        OcrPipeline(latinIME).processImage(
                            bitmap = bitmap,
                            onSuccess = { lines ->
                                latinIME.mKeyboardSwitcher.showOcrResult(lines)
                            },
                            onError = { err ->
                                Toast.makeText(latinIME, err, Toast.LENGTH_SHORT).show()
                            }
                        )
                    } else {
                        mainHandler.post {
                            Toast.makeText(latinIME, R.string.ocr_screenshot_load_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        } else {
            extractButton.visibility = View.GONE
        }

        pasteButton.setOnClickListener {
            val prefs = latinIME.prefs()
            val rawDeletedSet = prefs.getStringSet("deleted_screenshot_uris", emptySet()) ?: emptySet()
            val deletedSet = rawDeletedSet.filter { it.startsWith("content://") || it.contains("clipboard_images") }.toMutableSet()
            deletedSet.add(contentUri.toString())
            prefs.edit().putStringSet("deleted_screenshot_uris", deletedSet)
                .putString("last_dismissed_screenshot_uri", contentUri.toString()).apply()
            cachedScreenshotInfo = null
            dontShowCurrentSuggestion = true
            lastSuggestedScreenshotUri = contentUri.toString()
            latinIME.onImageSelected(contentUri.toString())
            AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, it, HapticEvent.KEY_PRESS)
            binding.root.isGone = true
            removeClipboardSuggestion()
        }

        val closeDrawable = latinIME.mKeyboardSwitcher.keyboard?.mIconsSet?.getIconDrawable(ToolbarKey.CLOSE_HISTORY.name.lowercase())
            ?: androidx.core.content.ContextCompat.getDrawable(latinIME, R.drawable.ic_close)
        closeButton.setImageDrawable(closeDrawable)
        closeButton.setOnClickListener { 
            val prefs = latinIME.prefs()
            val rawDeletedSet = prefs.getStringSet("deleted_screenshot_uris", emptySet()) ?: emptySet()
            val deletedSet = rawDeletedSet.filter { it.startsWith("content://") || it.contains("clipboard_images") }.toMutableSet()
            deletedSet.add(contentUri.toString())
            prefs.edit().putStringSet("deleted_screenshot_uris", deletedSet)
                .putString("last_dismissed_screenshot_uri", contentUri.toString()).apply()
            cachedScreenshotInfo = null
            dontShowCurrentSuggestion = true
            lastSuggestedScreenshotUri = contentUri.toString()
            AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, it, HapticEvent.KEY_PRESS)
            binding.root.isGone = true
            removeClipboardSuggestion() 
        }

        val colors = latinIME.mSettings.current.mColors
        colors.setBackground(binding.root, ColorType.CLIPBOARD_SUGGESTION_BACKGROUND)
        colors.setColor(extractButton, ColorType.REMOVE_SUGGESTION_ICON)
        colors.setColor(closeButton, ColorType.REMOVE_SUGGESTION_ICON)

        return binding.root
    }

    private fun removeClipboardSuggestion() {
        dontShowCurrentSuggestion = true
        clipboardSuggestionView?.isGone = true
        clipboardSuggestionView = null
        latinIME.removeExternalSuggestions()
    }

    companion object {
        const val RECENT_TIME_MILLIS = 1 * 60 * 1000L // 1 minute (for clipboard suggestions)
        const val RECENT_SCREENSHOT_TIME_MILLIS = 5 * 60 * 1000L // 5 minutes
        private val ONE_TIME_CODE = Regex("\\d{4,8}")
        fun isOneTimeCode(text: String) = ONE_TIME_CODE.matches(text.trim())
    }

}
