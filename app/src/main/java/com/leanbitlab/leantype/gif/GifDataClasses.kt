package com.leanbitlab.leantype.gif

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class GifItem(
    val id: String,
    val provider: String,
    val kind: Int,              // Contract.KIND_*
    val previewUri: String,     // always a content:// URI served by the plugin
    val width: Int,
    val height: Int,
    val isPack: Boolean,
    val attribution: String? = null,
    val extra: String? = null   // opaque, provider-specific (e.g. full-size media reference)
) : Parcelable

@Parcelize
data class ProviderInfo(
    val id: String,
    val displayName: String,
    val kinds: Int,
    val capabilities: Int,
    val credentialFields: List<String>,
    val configured: Boolean,
    val needsNetwork: Boolean,
    val attribution: String
) : Parcelable
