package com.leanbitlab.leantype.gif;

import com.leanbitlab.leantype.gif.ProviderInfo;
import com.leanbitlab.leantype.gif.GifItem;
import com.leanbitlab.leantype.gif.IGifCallback;

interface IGifEngine {
    int getContractVersion();
    List<ProviderInfo> listProviders();
    boolean hasCredential(String provider);
    boolean setCredential(String provider, String field, String value);
    void clearCredential(String provider);

    // Async calls are oneway: they never block the host, and errors arrive via the callback.
    oneway void search(int requestId, String provider, String query, String pos, int limit, IGifCallback cb);
    oneway void trending(int requestId, String provider, String pos, int limit, IGifCallback cb);
    oneway void listPacks(int requestId, String provider, String pos, int limit, IGifCallback cb);
    oneway void getPack(int requestId, String provider, String packId, String pos, int limit, IGifCallback cb);
    oneway void fetch(int requestId, in GifItem item, IGifCallback cb);
    oneway void cancel(int requestId);
}
