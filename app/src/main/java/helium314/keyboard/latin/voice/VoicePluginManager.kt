// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.DeadObjectException
import android.os.IBinder
import android.os.RemoteException
import com.leanbitlab.leantype.voice.IVoiceCallback
import com.leanbitlab.leantype.voice.IVoiceEngine
import com.leanbitlab.leantype.voice.ModelImportRequest
import com.leanbitlab.leantype.voice.ModelState
import com.leanbitlab.leantype.voice.VoiceConstants
import com.leanbitlab.leantype.voice.VoiceEngineInfo
import com.leanbitlab.leantype.voice.VoiceSessionConfig
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs

class VoicePluginManager(private val context: Context) : IBinder.DeathRecipient {

    interface PluginConnectionListener {
        fun onPluginConnected(info: VoiceEngineInfo?)
        fun onPluginDisconnected()
    }

    @Volatile
    private var engine: IVoiceEngine? = null
    @Volatile
    private var isBound = false
    @Volatile
    private var isConnecting = false
    private var deathRecipientRegistered = false
    private var connectionListener: PluginConnectionListener? = null

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pendingUnbindRunnable: Runnable? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.i(TAG, "VoiceEngineService connected: $name")
            engine = IVoiceEngine.Stub.asInterface(service)
            isConnecting = false
            isBound = true
            try {
                service?.linkToDeath(this@VoicePluginManager, 0)
                deathRecipientRegistered = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to link to binder death", e)
            }
            val info = getInfo()
            mainHandler.post {
                connectionListener?.onPluginConnected(info)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "VoiceEngineService disconnected: $name")
            mainHandler.post {
                handleDisconnection()
            }
        }
    }

    fun setConnectionListener(listener: PluginConnectionListener?) {
        this.connectionListener = listener
    }

    fun isPluginInstalled(): Boolean {
        val intent = Intent().apply { component = resolveServiceComponent() }
        val list = context.packageManager.queryIntentServices(intent, 0)
        return list.isNotEmpty()
    }

    fun isPluginConnected(): Boolean = engine != null && isBound

    fun hasScheduledUnbind(): Boolean = pendingUnbindRunnable != null

    /** Cancels any pending deferred unbind of the voice plugin service. */
    fun cancelScheduledUnbind() {
        pendingUnbindRunnable?.let {
            mainHandler.removeCallbacks(it)
            pendingUnbindRunnable = null
        }
    }

    /**
     * Unbinds immediately when [delayMs] == 0, schedules an idle unbind when [delayMs] > 0,
     * or keeps the service bound when [delayMs] < 0 ("Always keep in memory").
     */
    fun unbindAfterDelay(delayMs: Long, preserveExistingSchedule: Boolean = false, canUnbind: () -> Boolean = { true }) {
        if (!isBound && !isConnecting) {
            cancelScheduledUnbind()
            return
        }
        if (delayMs < 0L) {
            cancelScheduledUnbind()
            return
        }
        if (delayMs == 0L) {
            cancelScheduledUnbind()
            if (canUnbind()) {
                unbind()
            }
            return
        }
        if (preserveExistingSchedule && pendingUnbindRunnable != null) {
            return
        }
        cancelScheduledUnbind()
        val runnable = Runnable {
            pendingUnbindRunnable = null
            if (canUnbind()) {
                unbind()
            }
        }
        pendingUnbindRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    fun bindIfNeeded(): Boolean {
        cancelScheduledUnbind()
        if (isPluginConnected()) return true
        if (isConnecting) return false

        val component = resolveServiceComponent()
        val intent = Intent().apply { this.component = component }

        Log.i(TAG, "Binding to voice plugin service: $component")
        isConnecting = true
        val flags = Context.BIND_AUTO_CREATE or Context.BIND_NOT_FOREGROUND
        val success = try {
            context.bindService(intent, connection, flags)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind to voice plugin service", e)
            false
        }

        if (!success) {
            isConnecting = false
        }
        return success
    }

    // The plugin is bound without foreground priority, which saves battery while it waits, but then Android
    // gives it less CPU and recognition takes about twice as long. While dictating, a second binding with
    // normal priority raises it (Android uses the higher one); it is removed when the dictation ends.
    private var isBoosted = false
    private val boostConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {}
        override fun onServiceDisconnected(name: ComponentName?) {}
    }

    /** Normal priority for the plugin while [active] (recording and recognizing), see [boostConnection] */
    fun setDictationPriority(active: Boolean) {
        if (active == isBoosted) return
        if (active) {
            if (!isBound) return
            isBoosted = try {
                context.bindService(Intent().apply { component = resolveServiceComponent() }, boostConnection, Context.BIND_AUTO_CREATE)
            } catch (e: Exception) {
                Log.w(TAG, "Could not raise the priority of the voice plugin", e)
                false
            }
        } else {
            isBoosted = false
            try { context.unbindService(boostConnection) } catch (e: Exception) { }
        }
    }

    fun unbind() {
        cancelScheduledUnbind()
        setDictationPriority(false)
        if (isBound || isConnecting) {
            try {
                if (deathRecipientRegistered) {
                    engine?.asBinder()?.unlinkToDeath(this, 0)
                    deathRecipientRegistered = false
                }
                context.unbindService(connection)
            } catch (e: Exception) {
                Log.e(TAG, "Error unbinding voice plugin service", e)
            } finally {
                handleDisconnection()
            }
        }
    }

    override fun binderDied() {
        Log.e(TAG, "Voice plugin binder died unexpectedly")
        handleDisconnection()
    }

    private var lastLoggedModelState: Pair<String, Int?>? = null

    private fun handleDisconnection() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            val staleBinder = engine?.asBinder() ?: return
            mainHandler.post {
                if (engine?.asBinder() === staleBinder) {
                    handleDisconnection()
                }
            }
            return
        }
        cancelScheduledUnbind()
        engine = null
        isBound = false
        isConnecting = false
        deathRecipientRegistered = false
        lastLoggedModelState = null
        connectionListener?.onPluginDisconnected()
    }

    fun getPluginPackageName(): String = resolveServiceComponent().packageName

    private fun resolveServiceComponent(): ComponentName {
        val useDebugStub = BuildConfig.DEBUG &&
                context.prefs().getBoolean(VoiceConstants.PREF_USE_DEBUG_VOICE_STUB, false)

        if (useDebugStub) {
            return ComponentName(
                context.packageName,
                "helium314.keyboard.latin.voice.DebugVoiceEngineService"
            )
        }

        if (BuildConfig.DEBUG) {
            val debugComponent = ComponentName(
                "com.leanbitlab.leantype.voice.offline.debug",
                "com.leanbitlab.leantype.voice.offline.VoiceEngineService"
            )
            val debugIntent = Intent().apply { component = debugComponent }
            if (context.packageManager.queryIntentServices(debugIntent, 0).isNotEmpty()) {
                return debugComponent
            }
        }

        return ComponentName(
            "com.leanbitlab.leantype.voice.offline",
            "com.leanbitlab.leantype.voice.offline.VoiceEngineService"
        )
    }

    fun getInfo(): VoiceEngineInfo? {
        return try {
            engine?.info
        } catch (e: DeadObjectException) {
            Log.e(TAG, "Voice plugin service died in getInfo", e)
            handleDisconnection()
            null
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to get plugin info", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get plugin info", e)
            null
        }
    }

    fun getModelState(engineType: String): ModelState? {
        return try {
            val state = engine?.getModelState(engineType)
            if (lastLoggedModelState?.first != engineType || lastLoggedModelState?.second != state?.state) {
                lastLoggedModelState = Pair(engineType, state?.state)
                Log.i(TAG, "getModelState for $engineType: ${state?.state} (${state?.message})")
            }
            state
        } catch (e: DeadObjectException) {
            Log.e(TAG, "Voice plugin service died in getModelState", e)
            handleDisconnection()
            null
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to get model state for $engineType", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get model state for $engineType", e)
            null
        }
    }

    fun importModelSafely(request: ModelImportRequest) {
        try {
            engine?.importModel(request)
        } catch (e: DeadObjectException) {
            Log.e(TAG, "Voice plugin service died in importModel", e)
            handleDisconnection()
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to import model", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import model", e)
        } finally {
            try {
                request.file.close()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to close PFD for model import", e)
            }
        }
    }

    suspend fun bindAndImport(request: ModelImportRequest): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val appContext = context.applicationContext

        if (isPluginConnected()) {
            return@withContext try {
                engine?.importModel(request)
                true
            } catch (e: DeadObjectException) {
                Log.e(TAG, "Voice plugin service died in importModel", e)
                handleDisconnection()
                false
            } catch (e: RemoteException) {
                Log.e(TAG, "importModel oneway dispatch failed", e)
                false
            } catch (e: Exception) {
                Log.e(TAG, "importModel oneway dispatch failed", e)
                false
            } finally {
                try { request.file.close() } catch (_: Exception) {}
            }
        }

        var conn: ServiceConnection? = null
        try {
            val service = kotlinx.coroutines.suspendCancellableCoroutine<IVoiceEngine?> { cont ->
                conn = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        if (cont.isActive) {
                            cont.resume(IVoiceEngine.Stub.asInterface(binder), null)
                        }
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        if (cont.isActive) {
                            cont.resume(null, null)
                        }
                    }
                }

                val component = resolveServiceComponent()
                val intent = Intent().apply { this.component = component }
                val sc = conn
                val bound = try {
                    if (sc != null) appContext.bindService(intent, sc, Context.BIND_AUTO_CREATE or Context.BIND_NOT_FOREGROUND) else false
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to bind to voice plugin", e)
                    false
                }

                if (!bound && cont.isActive) {
                    cont.resume(null, null)
                }

                cont.invokeOnCancellation {
                    try {
                        conn?.let { appContext.unbindService(it) }
                    } catch (_: Exception) {}
                }
            }

            if (service == null) {
                Log.e(TAG, "bindAndImport: service binding returned null")
                return@withContext false
            }

            // oneway AIDL call returns in microseconds
            service.importModel(request)
            Log.i(TAG, "bindAndImport: oneway importModel dispatched successfully")
            true
        } catch (e: RemoteException) {
            Log.e(TAG, "bindAndImport remote call failed", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "bindAndImport failed", e)
            false
        } finally {
            conn?.let {
                try { appContext.unbindService(it) } catch (_: Exception) {}
            }
            try { request.file.close() } catch (_: Exception) {}
        }
    }

    fun unloadModel(engineType: String) {
        try {
            engine?.unloadModel(engineType)
        } catch (e: DeadObjectException) {
            Log.e(TAG, "Voice plugin service died in unloadModel", e)
            handleDisconnection()
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to unload model $engineType", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unload model $engineType", e)
        }
    }

    fun deleteModel(engineType: String) {
        try {
            engine?.deleteModel(engineType)
        } catch (e: DeadObjectException) {
            Log.e(TAG, "Voice plugin service died in deleteModel", e)
            handleDisconnection()
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to delete model $engineType", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete model $engineType", e)
        }
    }

    fun startSession(
        config: VoiceSessionConfig,
        audioInput: android.os.ParcelFileDescriptor,
        callback: IVoiceCallback
    ): Boolean {
        cancelScheduledUnbind()
        return try {
            engine?.startSession(config, audioInput, callback)
            true
        } catch (e: DeadObjectException) {
            Log.e(TAG, "Voice plugin service died in startSession", e)
            handleDisconnection()
            false
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to start voice session", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start voice session", e)
            false
        }
    }

    fun stopSession() {
        try {
            engine?.stopSession()
        } catch (e: DeadObjectException) {
            Log.e(TAG, "Voice plugin service died in stopSession", e)
            handleDisconnection()
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to stop session", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop session", e)
        }
    }

    fun cancelSession() {
        try {
            engine?.cancelSession()
        } catch (e: DeadObjectException) {
            Log.e(TAG, "Voice plugin service died in cancelSession", e)
            handleDisconnection()
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to cancel session", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel session", e)
        }
    }

    fun release() {
        try {
            engine?.release()
        } catch (e: DeadObjectException) {
            Log.e(TAG, "Voice plugin service died in release", e)
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to release voice engine", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release voice engine", e)
        } finally {
            unbind()
        }
    }

    companion object {
        private const val TAG = "VoicePluginManager"
    }
}
