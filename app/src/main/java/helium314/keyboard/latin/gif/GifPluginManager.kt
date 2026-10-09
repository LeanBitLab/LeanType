package helium314.keyboard.latin.gif

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.DeadObjectException
import android.os.IBinder
import android.os.RemoteException
import androidx.core.content.ContextCompat
import com.leanbitlab.leantype.gif.Contract
import com.leanbitlab.leantype.gif.IGifEngine
import com.leanbitlab.leantype.gif.SigningCerts
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.utils.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

object PluginBusy : Exception("GIF plugin busy")
class PluginException(val code: Int, message: String) : Exception(message)

class GifPluginManager private constructor(private val appContext: Context) {
    enum class State {
        NOT_INSTALLED,
        UNTRUSTED_PLUGIN,
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        HOST_TOO_OLD,
        PLUGIN_TOO_OLD,
        UNAVAILABLE
    }

    private enum class Install { MISSING, UNTRUSTED, OK }

    companion object {
        private const val TAG = "GifPluginManager"
        const val PLUGIN_PACKAGE = "com.leanbitlab.leantype.gif"
        const val PLUGIN_ACTION = "com.leanbitlab.leantype.gif.ENGINE"
        private const val MIN_CONTRACT = 1
        private const val MAX_CONTRACT = 1
        private const val MAX_ATTEMPTS = 5

        // Signing-cert SHA-256 digests (lowercase hex)
        private val PINNED_PLUGIN_CERTS = setOf(
            "ac7360ae311a36f2cc1caf1dad4848e18e191c55aaf128838fc34c92c6838016", // release
            "ca2d7663d16db859ee3ec70b349f0ab232d2e16eefdd4f4eff8b451ba694509e", // debug
            "01cfc72120c539ec4cc846cd70d25f83e3fc694d8bdcd2211454a8c993c4f5a3"  // plugin-release.jks
        )

        private val SETTLED = setOf(
            State.CONNECTED,
            State.NOT_INSTALLED,
            State.UNTRUSTED_PLUGIN,
            State.HOST_TOO_OLD,
            State.PLUGIN_TOO_OLD,
            State.UNAVAILABLE
        )

        @Volatile
        private var instance: GifPluginManager? = null

        fun get(context: Context): GifPluginManager =
            instance ?: synchronized(this) {
                instance ?: GifPluginManager(context.applicationContext).also { instance = it }
            }
    }

    private val lock = Any()
    private val _state = MutableStateFlow(State.NOT_INSTALLED)
    val state: StateFlow<State> = _state.asStateFlow()

    private var generation = 1
    private val _connectionGeneration = MutableStateFlow(1)
    val connectionGeneration: StateFlow<Int> = _connectionGeneration.asStateFlow()

    @Volatile
    private var engineRef: IGifEngine? = null
    val engine: IGifEngine? get() = engineRef

    // Dedicated fixed pool of 2 daemon threads for plugin binder transactions (isolated from Dispatchers.IO)
    private val pluginDispatcher = Executors.newFixedThreadPool(2) { r ->
        Thread(r, "gif-plugin-worker").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val pluginScope = CoroutineScope(SupervisorJob() + pluginDispatcher)
    private val slots = Semaphore(2)

    private val clientRefs = mutableSetOf<String>()
    private var bound = false
    private var linked: IBinder? = null
    private var started = false
    private var incompatible = false
    private var attempts = 0
    private var backoffMs = 500L
    private var rebindJob: Job? = null

    private val reqId = AtomicInteger(1)
    fun nextRequestId(): Int = reqId.getAndIncrement()

    private val deathRecipient = IBinder.DeathRecipient {
        val gen = synchronized(lock) { generation }
        onConnectionLost(gen)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val gen = synchronized(lock) {
                if (clientRefs.isEmpty() || !bound) return
                try {
                    service.linkToDeath(deathRecipient, 0)
                } catch (_: RemoteException) {
                    onConnectionLost(generation)
                    return
                }
                linked = service
                generation
            }

            val candidate = IGifEngine.Stub.asInterface(service)
            pluginScope.launch {
                val version = try {
                    candidate.contractVersion
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to read contract version", e)
                    synchronized(lock) {
                        if (gen == generation) onConnectionLost(gen)
                    }
                    return@launch
                }

                synchronized(lock) {
                    if (gen != generation || clientRefs.isEmpty() || !bound) return@synchronized
                    when {
                        version > MAX_CONTRACT -> {
                            incompatible = true
                            _state.value = State.HOST_TOO_OLD
                            unbindExactlyOnce()
                        }
                        version < MIN_CONTRACT -> {
                            incompatible = true
                            _state.value = State.PLUGIN_TOO_OLD
                            unbindExactlyOnce()
                        }
                        else -> {
                            engineRef = candidate
                            attempts = 0
                            backoffMs = 500L
                            generation++
                            _connectionGeneration.value = generation
                            _state.value = State.CONNECTED
                        }
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            val gen = synchronized(lock) { generation }
            onConnectionLost(gen)
        }

        override fun onBindingDied(name: ComponentName) {
            val gen = synchronized(lock) { generation }
            onConnectionLost(gen)
        }

        override fun onNullBinding(name: ComponentName) {
            val gen = synchronized(lock) { generation }
            onConnectionLost(gen)
        }
    }

    private fun onConnectionLost(gen: Int) {
        val shouldRebind = synchronized(lock) {
            if (gen != generation) return
            generation++
            _connectionGeneration.value = generation
            engineRef = null
            unbindExactlyOnce()
            if (incompatible) return

            if (clientRefs.isEmpty()) {
                _state.value = stateForInstall()
                false
            } else {
                attempts++
                if (attempts >= MAX_ATTEMPTS) {
                    _state.value = State.UNAVAILABLE
                    false
                } else {
                    _state.value = State.DISCONNECTED
                    true
                }
            }
        }
        if (shouldRebind) scheduleRebind()
    }

    private fun unbindExactlyOnce() {
        linked?.let { runCatching { it.unlinkToDeath(deathRecipient, 0) } }
        linked = null
        engineRef = null
        if (bound) {
            runCatching { appContext.unbindService(connection) }
            bound = false
        }
    }

    private fun scheduleRebind() {
        rebindJob?.cancel()
        rebindJob = pluginScope.launch {
            val jitter = Random.nextLong(0, backoffMs + 1)
            delay(backoffMs / 2 + jitter / 2)
            backoffMs = (backoffMs * 2).coerceAtMost(16_000L)
            synchronized(lock) {
                if (clientRefs.isNotEmpty() && !incompatible && _state.value != State.UNAVAILABLE) {
                    bindIfNeeded()
                }
            }
        }
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.data?.schemeSpecificPart != PLUGIN_PACKAGE) return
            if (intent.action == Intent.ACTION_PACKAGE_REMOVED && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return

            synchronized(lock) {
                generation++
                _connectionGeneration.value = generation
                incompatible = false
                attempts = 0
                backoffMs = 500L
                unbindExactlyOnce()
                refresh()
                if (clientRefs.isNotEmpty() && intent.action != Intent.ACTION_PACKAGE_REMOVED) {
                    bindIfNeeded()
                }
            }
        }
    }

    /** Call once from App.onCreate(). Only registers the receiver; does not bind. */
    fun start() = synchronized(lock) {
        if (started) return@synchronized
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(appContext, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        started = true
        refresh()
    }

    /** Client acquisition with bitset refcounting ("picker", "settings", "insert"). */
    fun acquire(client: String) = synchronized(lock) {
        val wasEmpty = clientRefs.isEmpty()
        val added = clientRefs.add(client)
        if (client == "picker" && _state.value == State.UNAVAILABLE) {
            // Re-opening picker resets breaker
            attempts = 0
            _state.value = State.DISCONNECTED
        }
        if (wasEmpty || added) {
            bindIfNeeded()
        }
    }

    /** Client release. Service stays bound while other clients are active. */
    fun release(client: String) = synchronized(lock) {
        if (!clientRefs.remove(client)) return@synchronized
        if (clientRefs.isEmpty()) {
            rebindJob?.cancel()
            unbindExactlyOnce()
            refresh()
        }
    }

    fun retry() = synchronized(lock) {
        attempts = 0
        backoffMs = 500L
        incompatible = false
        if (clientRefs.isNotEmpty()) {
            _state.value = State.DISCONNECTED
            bindIfNeeded()
        } else {
            refresh()
        }
    }

    private fun allowedCerts(): Set<String> {
        val certs = PINNED_PLUGIN_CERTS.toMutableSet()
        if (BuildConfig.DEBUG) {
            certs.addAll(SigningCerts.sha256Digests(appContext.packageManager, appContext.packageName))
        }
        return certs
    }

    private fun installStatus(): Install {
        val pm = appContext.packageManager
        val resolvable = pm.queryIntentServices(Intent(PLUGIN_ACTION).setPackage(PLUGIN_PACKAGE), 0).isNotEmpty()
        if (!resolvable) return Install.MISSING
        val pluginDigests = SigningCerts.sha256Digests(pm, PLUGIN_PACKAGE)
        val allowed = allowedCerts()
        return if (pluginDigests.any { it in allowed }) Install.OK else Install.UNTRUSTED
    }

    private fun stateForInstall(): State = when (installStatus()) {
        Install.MISSING -> State.NOT_INSTALLED
        Install.UNTRUSTED -> State.UNTRUSTED_PLUGIN
        Install.OK -> State.DISCONNECTED
    }

    fun refresh() = synchronized(lock) {
        if (engineRef != null) {
            _state.value = State.CONNECTED
            return@synchronized
        }
        if (bound) return@synchronized
        _state.value = stateForInstall()
    }

    private fun bindIfNeeded() {
        if (engineRef != null || bound) return
        when (installStatus()) {
            Install.MISSING -> { _state.value = State.NOT_INSTALLED; return }
            Install.UNTRUSTED -> { _state.value = State.UNTRUSTED_PLUGIN; return }
            Install.OK -> {}
        }
        val ok = try {
            appContext.bindService(Intent(PLUGIN_ACTION).setPackage(PLUGIN_PACKAGE), connection, Context.BIND_AUTO_CREATE)
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException on bindService", e)
            false
        }
        if (ok) {
            bound = true
            _state.value = State.CONNECTING
        } else {
            attempts++
            runCatching { appContext.unbindService(connection) }
            bound = false
            if (attempts >= MAX_ATTEMPTS) {
                _state.value = State.UNAVAILABLE
            } else {
                _state.value = State.DISCONNECTED
                scheduleRebind()
            }
        }
    }

    /**
     * Waits until the plugin is connected or reaches a terminal state.
     */
    suspend fun awaitEngine(timeoutMs: Long = 5_000): IGifEngine? = withTimeoutOrNull(timeoutMs) {
        synchronized(lock) {
            if (_state.value == State.CONNECTED && engineRef != null) return@withTimeoutOrNull engineRef
        }
        state.first { it in SETTLED }
        synchronized(lock) {
            if (_state.value == State.CONNECTED) engineRef else null
        }
    }

    /**
     * Executes a call against IGifEngine using the dedicated pluginDispatcher and bounded slots.
     */
    suspend fun <T> call(timeoutMs: Long = 3_000, block: (IGifEngine) -> T): Result<T> {
        if (!slots.tryAcquire()) return Result.failure(PluginBusy)
        return try {
            withContext(pluginDispatcher) {
                withTimeout(timeoutMs) {
                    val engine = awaitEngine(timeoutMs)
                        ?: return@withTimeout Result.failure(IllegalStateException("plugin unavailable: ${_state.value}"))
                    try {
                        Result.success(block(engine))
                    } catch (e: DeadObjectException) {
                        val gen = synchronized(lock) { generation }
                        onConnectionLost(gen)
                        Result.failure(e)
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Result.failure(e)
        } finally {
            slots.release()
        }
    }
}
