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
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.isValidNumber
import helium314.keyboard.latin.database.ClipboardDao
import helium314.keyboard.latin.databinding.ClipboardSuggestionBinding
import helium314.keyboard.latin.utils.InputTypeUtils
import helium314.keyboard.latin.utils.ToolbarKey
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import kotlin.concurrent.thread

class ClipboardHistoryManager(
        private val latinIME: LatinIME
) : ClipboardManager.OnPrimaryClipChangedListener {

    private lateinit var clipboardManager: ClipboardManager
    private var clipboardSuggestionView: View? = null
    private var clipboardDao: ClipboardDao? = null
    private var dontShowCurrentSuggestion: Boolean = false
    private var mediaStoreObserver: ContentObserver? = null

    fun onCreate() {
        clipboardManager = latinIME.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.addPrimaryClipChangedListener(this)
        clipboardDao = ClipboardDao.getInstance(latinIME)
        if (latinIME.mSettings.current.mClipboardHistoryEnabled)
            thread { fetchPrimaryClip() }
        thread { cleanUpImageCache() }
        registerMediaStoreObserver()
    }

    private fun registerMediaStoreObserver() {
        if (mediaStoreObserver == null) {
            mediaStoreObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    super.onChange(selfChange, uri)
                    if (latinIME.mSettings.current.mSuggestScreenshots) {
                        // Sometimes MediaStore needs a brief moment to finish writing the file
                        Handler(Looper.getMainLooper()).postDelayed({
                            dontShowCurrentSuggestion = false
                            // Force suggestion strip update
                            latinIME.setNeutralSuggestionStrip()
                        }, 1000) // 1 second delay
                    }
                }
            }
            latinIME.contentResolver.registerContentObserver(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                mediaStoreObserver!!
            )
        }
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
        clipboardManager.removePrimaryClipChangedListener(this)
        mediaStoreObserver?.let {
            latinIME.contentResolver.unregisterContentObserver(it)
            mediaStoreObserver = null
        }
    }

    override fun onPrimaryClipChanged() {
        // Make sure we read clipboard content only if history settings is set
        if (latinIME.mSettings.current.mClipboardHistoryEnabled) {
            thread { fetchPrimaryClip() }
            dontShowCurrentSuggestion = false
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
            clipboardDao?.addClip(timeStamp, false, content, imageUri)
        }
    }

    private fun cacheImage(uri: android.net.Uri): String? {
        try {
            val resolver = latinIME.contentResolver
            val mimeType = resolver.getType(uri) ?: "image/png"
            val extension = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "png"
            
            val cacheDir = java.io.File(latinIME.cacheDir, "clipboard_images")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            
            // Generate a unique filename
            val file = java.io.File(cacheDir, "img_${System.currentTimeMillis()}.$extension")
            
            resolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return file.absolutePath
        } catch (e: Exception) {
            return null
        }
    }

    fun toggleClipPinned(id: Long) {
        clipboardDao?.togglePinned(id)
    }

    fun clearHistory() {
        clipboardDao?.clearNonPinned()
        ClipboardManagerCompat.clearPrimaryClip(clipboardManager)
        removeClipboardSuggestion()
    }

    fun canRemove(index: Int) = clipboardDao?.isPinned(index) == false

    fun removeEntry(index: Int): ClipboardHistoryEntry? {
        if (!canRemove(index)) return null
        return clipboardDao?.deleteClipAt(index)
    }

    fun restoreEntry(entry: ClipboardHistoryEntry) {
        clipboardDao?.restoreClip(entry)
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

    private fun isClipSensitive(inputType: Int): Boolean {
        ClipboardManagerCompat.getClipSensitivity(clipboardManager.primaryClip?.description)?.let { return it }
        return InputTypeUtils.isPasswordInputType(inputType)
    }

    fun getClipboardSuggestionView(editorInfo: EditorInfo?, parent: ViewGroup?): View? {
        clipboardSuggestionView = null

        // check for screenshot first if enabled
        if (latinIME.mSettings.current.mSuggestScreenshots) {
            val screenshotView = getScreenshotSuggestionView(parent)
            if (screenshotView != null) {
                clipboardSuggestionView = screenshotView
                return screenshotView
            }
        }

        // get the content, or return null
        if (!latinIME.mSettings.current.mSuggestClipboardContent) return null
        if (dontShowCurrentSuggestion) return null
        if (parent == null) return null
        val clipData = clipboardManager.primaryClip ?: return null
        if (clipData.itemCount == 0 || clipData.description?.hasMimeType("text/*") == false) return null
        val clipItem = clipData.getItemAt(0) ?: return null
        val timeStamp = ClipboardManagerCompat.getClipTimestamp(clipData)
        if (System.currentTimeMillis() - timeStamp > RECENT_TIME_MILLIS) return null
        val content = clipItem.coerceToText(latinIME)
        if (TextUtils.isEmpty(content)) return null
        val inputType = editorInfo?.inputType ?: InputType.TYPE_NULL
        if (InputTypeUtils.isNumberInputType(inputType) && !content.isValidNumber()) return null

        // create the view
        val binding = ClipboardSuggestionBinding.inflate(LayoutInflater.from(latinIME), parent, false)
        val textView = binding.clipboardSuggestionText
        latinIME.mSettings.getCustomTypeface()?.let { textView.typeface = it }
        textView.text = (if (isClipSensitive(inputType)) "*".repeat(content.length) else content)
            .take(200) // truncate displayed text for performance reasons
        val clipIcon = latinIME.mKeyboardSwitcher.keyboard.mIconsSet.getIconDrawable(ToolbarKey.PASTE.name.lowercase())
        textView.setCompoundDrawablesRelativeWithIntrinsicBounds(clipIcon, null, null, null)
        textView.setOnClickListener {
            dontShowCurrentSuggestion = true
            latinIME.onTextInput(content.toString())
            AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, it, HapticEvent.KEY_PRESS)
            binding.root.isGone = true
        }
        val closeButton = binding.clipboardSuggestionClose
        closeButton.setImageDrawable(latinIME.mKeyboardSwitcher.keyboard.mIconsSet.getIconDrawable(ToolbarKey.CLOSE_HISTORY.name.lowercase()))
        closeButton.setOnClickListener { removeClipboardSuggestion() }

        val colors = latinIME.mSettings.current.mColors
        textView.setTextColor(colors.get(ColorType.KEY_TEXT))
        clipIcon?.let { colors.setColor(it, ColorType.KEY_ICON) }
        colors.setColor(closeButton, ColorType.REMOVE_SUGGESTION_ICON)
        colors.setBackground(binding.root, ColorType.CLIPBOARD_SUGGESTION_BACKGROUND)

        clipboardSuggestionView = binding.root
        return clipboardSuggestionView
    }

    private var lastSuggestedScreenshotUri: String? = null

    private fun getScreenshotSuggestionView(parent: ViewGroup?): View? {
        if (parent == null || dontShowCurrentSuggestion) return null
        
        // Permission check
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (latinIME.checkCallingOrSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return null
        }

        // Query MediaStore for latest screenshot
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
                    // DATE_ADDED is in seconds!
                    val dateAdded = it.getLong(dateIndex) * 1000L
                    val diff = System.currentTimeMillis() - dateAdded
                    
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

                    if (diff < RECENT_SCREENSHOT_TIME_MILLIS) {
                        var isScreenshot = fileName.contains("Screenshot", ignoreCase = true) || fullPath.contains("Screenshot", ignoreCase = true) || fullPath.contains("Pictures", ignoreCase = true)
                        
                        if (!isScreenshot) continue // Try next image

                        val idIndex = it.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media._ID)
                        val id = it.getLong(idIndex)
                        val contentUri = android.content.ContentUris.withAppendedId(
                            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                        )

                        val isAlreadySuggested = contentUri.toString() == lastSuggestedScreenshotUri

                        // Save screenshot to clipboard history if enabled
                        if (!isAlreadySuggested) {
                            if (latinIME.mSettings.current.mClipboardHistoryEnabled) {
                                thread {
                                    val cachedPath = cacheImage(contentUri)
                                    if (cachedPath != null) {
                                        Handler(Looper.getMainLooper()).post {
                                            clipboardDao?.addClip(System.currentTimeMillis(), false, "[Screenshot]", cachedPath)
                                        }
                                    }
                                }
                            }
                            lastSuggestedScreenshotUri = contentUri.toString()
                        }

                        val binding = ClipboardSuggestionBinding.inflate(LayoutInflater.from(latinIME), parent, false)
                        val textView = binding.clipboardSuggestionText
                        textView.text = "Screenshot"
                        
                        // Try to load thumbnail
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            try {
                                val thumb = latinIME.contentResolver.loadThumbnail(contentUri, android.util.Size(120, 120), null)
                                
                                // Crop to square
                                val size = Math.min(thumb.width, thumb.height)
                                val x = (thumb.width - size) / 2
                                val y = (thumb.height - size) / 2
                                val croppedThumb = android.graphics.Bitmap.createBitmap(thumb, x, y, size, size)
                                
                                val drawable = android.graphics.drawable.BitmapDrawable(latinIME.resources, croppedThumb)
                                textView.setCompoundDrawablesRelativeWithIntrinsicBounds(drawable, null, null, null)
                            } catch (e: Exception) {
                                val clipIcon = latinIME.mKeyboardSwitcher.keyboard.mIconsSet.getIconDrawable(ToolbarKey.PASTE.name.lowercase())
                                textView.setCompoundDrawablesRelativeWithIntrinsicBounds(clipIcon, null, null, null)
                            }
                        }

                        textView.setOnClickListener {
                            dontShowCurrentSuggestion = true
                            lastSuggestedScreenshotUri = contentUri.toString()
                            latinIME.onImageSelected(contentUri.toString())
                            AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, it, HapticEvent.KEY_PRESS)
                            binding.root.isGone = true
                        }
                        
                        val closeButton = binding.clipboardSuggestionClose
                        closeButton.setImageDrawable(latinIME.mKeyboardSwitcher.keyboard.mIconsSet.getIconDrawable(ToolbarKey.CLOSE_HISTORY.name.lowercase()))
                        closeButton.setOnClickListener { 
                            dontShowCurrentSuggestion = true
                            lastSuggestedScreenshotUri = contentUri.toString()
                            removeClipboardSuggestion() 
                        }

                        val colors = latinIME.mSettings.current.mColors
                        textView.setTextColor(colors.get(ColorType.KEY_TEXT))
                        colors.setColor(closeButton, ColorType.REMOVE_SUGGESTION_ICON)
                        colors.setBackground(binding.root, ColorType.CLIPBOARD_SUGGESTION_BACKGROUND)
                        
                        return binding.root
                    } else {
                        break // Too old, stop searching
                    }
                }
            }
        } catch (e: Exception) {
            helium314.keyboard.latin.utils.Log.e("ClipboardHistoryManager", "Failed to query screenshots", e)
        }
        
        return null
    }

    private fun removeClipboardSuggestion() {
        dontShowCurrentSuggestion = true
        val csv = clipboardSuggestionView ?: return
        if (csv.parent != null && !csv.isGone) {
            // clipboard view is shown ->
            latinIME.setNeutralSuggestionStrip()
            latinIME.mHandler.postResumeSuggestions(false)
        }
        csv.isGone = true
    }

    companion object {
        const val RECENT_TIME_MILLIS = 3 * 60 * 1000L // 3 minutes (for clipboard suggestions)
        const val RECENT_SCREENSHOT_TIME_MILLIS = 4 * 60 * 1000L // 4 minutes
    }
}
