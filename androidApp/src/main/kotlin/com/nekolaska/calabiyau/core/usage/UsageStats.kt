package com.nekolaska.calabiyau.core.usage

import android.content.Context
import android.os.SystemClock
import android.util.AtomicFile
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import data.ApiResult
import data.ErrorKind
import data.SharedJson
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal enum class UsageFeature(val key: String) { CARDS("cards"), VOTE("vote"), DOWNLOAD("download"), SUBTITLE("subtitle") }
internal enum class Outcome(val key: String) { OK("ok"), NETWORK("network"), DATA("data"), OTHER("other") }
internal fun ApiResult<*>.usageOutcome() = when (this) {
    is ApiResult.Success -> Outcome.OK // Includes usable offline data.
    is ApiResult.Error -> when (kind) {
        ErrorKind.NETWORK, ErrorKind.TIMEOUT, ErrorKind.CDN_BLOCKED -> Outcome.NETWORK
        ErrorKind.PARSE, ErrorKind.NOT_FOUND -> Outcome.DATA
        ErrorKind.UNKNOWN -> Outcome.OTHER
    }
}

/** One catalog visit (one active tab); a retry is not a second first-open attempt. */
internal class CardVisit {
    private var first = true
    private var awaitingRecovery = false
    suspend fun <T> load(refresh: Boolean, fetch: suspend () -> ApiResult<T>): ApiResult<T> {
        val result = fetch()
        currentCoroutineContext().ensureActive()
        if (first) {
            first = false
            awaitingRecovery = result is ApiResult.Error
            UsageStats.result(UsageFeature.CARDS, result.usageOutcome())
        } else if (refresh && awaitingRecovery && result is ApiResult.Success) {
            awaitingRecovery = false
            UsageStats.cardRecovered()
        }
        return result
    }
}

@Serializable private data class Bucket(var version: String, var seq: Int = 0, var sent: Int = 0,
    var active: Int = 0, var starts: Int = 0, val counts: MutableMap<String, Int> = mutableMapOf())
@Serializable private data class Journal(val install: String, val cohort: Int, val days: MutableMap<Int, Bucket> = mutableMapOf(),
    var lastAckMs: Long = 0, var retryAfterMs: Long = 0, var retryDelayMs: Long = 0)

/** Own endpoint, fixed vocabulary, cumulative daily counters. Disk and HTTP never execute on the UI thread. */
internal object UsageStats {
    private const val DAY = 86_400_000L
    private const val OFFSET = 8 * 3_600_000L
    private const val INTERVAL = 15 * 60_000L
    private const val MAX_BACKOFF = 6 * 3_600_000L
    fun untilTomorrow() = DAY - (System.currentTimeMillis() + OFFSET) % DAY
    private fun day() = ((System.currentTimeMillis() + OFFSET) / DAY).toInt()
    private val serial = Executors.newSingleThreadScheduledExecutor()
    private val transport = Executors.newSingleThreadExecutor()
    private val client by lazy { OkHttpClient.Builder().followRedirects(false).retryOnConnectionFailure(false)
        .callTimeout(10, TimeUnit.SECONDS).build() }
    @Volatile private var app: Context? = null
    @Volatile private var enabled = false
    @Volatile private var epoch = 0L
    private var firstForeground = true
    private var call: Call? = null // synchronized on this
    // Everything below is owned by serial.
    private var journal: Journal? = null
    private var timer: ScheduledFuture<*>? = null
    private var sending = false
    private var nextSend = 0L
    private var backoff = INTERVAL
    private fun file() = AtomicFile(File(checkNotNull(app).noBackupFilesDir, "usage-v1.json"))
    private val version by lazy { val c = checkNotNull(app); c.packageManager.getPackageInfo(c.packageName, 0).versionName.orEmpty() }

    @Synchronized fun attach(context: Context) {
        if (app != null) return
        enabled = AppPrefs.usageStatsEnabled
        app = context.applicationContext
        if (!enabled) serial.execute { file().delete() }
    }

    @Synchronized fun setEnabled(value: Boolean) {
        if (enabled == value) return
        epoch++; enabled = value; AppPrefs.usageStatsEnabled = value
        call?.cancel(); call = null
        serial.execute {
            timer?.cancel(false); timer = null; sending = false; nextSend = 0; backoff = INTERVAL
            journal = null; file().delete()
        }
        if (value) mark(active = true) // Turning it on is a visit, not a new process launch.
    }

    @Synchronized fun foreground() {
        val cold = firstForeground
        firstForeground = false
        mark(active = true, cold = cold)
    }
    fun result(feature: UsageFeature, outcome: Outcome) = mark(metric = "${feature.key}_${outcome.key}")
    fun cardRecovered() = mark(metric = "cards_recovered")

    private fun mark(active: Boolean = false, cold: Boolean = false, metric: String? = null) {
        val generation = epoch
        if (app == null || !enabled) return
        val date = day()
        serial.execute { runCatching {
            if (!enabled || generation != epoch) return@execute
            val state = journal ?: read(date).also {
                journal = it
                // A process restart must not turn five quick relaunches into five D1 writes.
                val now = System.currentTimeMillis()
                val elapsed = (now - it.lastAckMs).coerceIn(0L, INTERVAL)
                val retryWait = (it.retryAfterMs - now).coerceIn(0L, MAX_BACKOFF)
                nextSend = SystemClock.elapsedRealtime() + maxOf(INTERVAL - elapsed, retryWait)
                backoff = it.retryDelayMs.coerceIn(INTERVAL, MAX_BACKOFF)
            }
            if (date < state.cohort) return@execute
            state.days.keys.removeAll { it !in (date - 7)..date }
            val r = state.days.getOrPut(date) { Bucket(version) }
            if (metric != null || cold || (active && r.active == 0) || version != r.version) {
                r.seq = (r.seq + 1).coerceAtMost(1000000); r.version = version
                if (active) r.active = 1
                if (cold) r.starts = (r.starts + 1).coerceAtMost(10000)
                if (metric != null) r.counts[metric] = ((r.counts[metric] ?: 0) + 1).coerceAtMost(100000)
                save(state)
            }
            schedule(generation)
        } }
    }
    private fun read(date: Int): Journal = runCatching {
        SharedJson.decodeFromString(Journal.serializer(), file().readFully().toString(Charsets.UTF_8))
    }.getOrElse { Journal(UUID.randomUUID().toString(), date) }
    private fun save(s: Journal) {
        val f = file(); val stream = f.startWrite()
        try { stream.write(SharedJson.encodeToString(Journal.serializer(), s).toByteArray(Charsets.UTF_8)); f.finishWrite(stream) }
        catch (e: Exception) { f.failWrite(stream); throw e }
    }
    private fun schedule(generation: Long) {
        if (!enabled || epoch != generation || sending || timer != null || journal?.days?.values?.none { it.seq > it.sent } != false) return
        timer = serial.schedule({ timer = null; flush(generation) }, maxOf(1500L, nextSend - SystemClock.elapsedRealtime()), TimeUnit.MILLISECONDS)
    }
    private fun flush(generation: Long) {
        if (!enabled || generation != epoch) return
        val s = journal ?: return
        s.days.keys.removeAll { it !in (day()-7)..day() }
        val dirty = s.days.filterValues { it.seq > it.sent }; if (dirty.isEmpty()) return
        val revisions = dirty.mapValues { it.value.seq }
        val body = buildJsonObject {
            put("schema",1); put("install",s.install); put("cohort",s.cohort)
            putJsonArray("days") { dirty.forEach { (d,r) -> addJsonObject {
                put("day",d); put("version",r.version); put("seq",r.seq); put("active",r.active); put("starts",r.starts)
                putJsonObject("counts") { r.counts.forEach { (k,v) -> put(k,v) } }
            } } }
        }.toString()
        sending = true
        transport.execute {
            val ok = runCatching {
                val request = Request.Builder().url("https://wiki.nekolaska.vip/api/app/usage")
                    .post(body.toRequestBody("application/json".toMediaType())).build()
                val current = synchronized(this) {
                    if (!enabled || generation != epoch) return@execute
                    client.newCall(request).also { call = it }
                }
                try { current.execute().use { it.code == 200 && it.header("Content-Type").orEmpty().startsWith("application/json") &&
                    it.peekBody(256).string().let { reply -> SharedJson.parseToJsonElement(reply).jsonObject["accepted"]?.jsonPrimitive?.booleanOrNull == true } } }
                finally { synchronized(this) { if (call === current) call = null } }
            }.getOrDefault(false)
            serial.execute { if (enabled && generation == epoch) {
                sending = false
                if (ok) {
                    revisions.forEach { (d,seq) -> s.days[d]?.let { it.sent = seq } }
                    s.lastAckMs = System.currentTimeMillis(); s.retryAfterMs = 0; s.retryDelayMs = 0; backoff = INTERVAL
                }
                nextSend = SystemClock.elapsedRealtime() + backoff
                if (!ok) {
                    // Cold launches during an outage must respect the same retry budget as a live process.
                    s.retryAfterMs = System.currentTimeMillis() + backoff
                    backoff = minOf(backoff * 2, MAX_BACKOFF)
                    s.retryDelayMs = backoff
                }
                runCatching { save(s) }
                schedule(generation)
            } }
        }
    }
}
