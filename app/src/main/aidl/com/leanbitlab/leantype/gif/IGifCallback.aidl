package com.leanbitlab.leantype.gif;

import com.leanbitlab.leantype.gif.GifItem;
import android.os.ParcelFileDescriptor;

oneway interface IGifCallback {
    void onItems(int requestId, in List<GifItem> items, String nextPos);
    void onFetched(int requestId, in ParcelFileDescriptor pfd, String mimeType, int width, int height);
    void onError(int requestId, int code, String message);
}
