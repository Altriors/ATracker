package app.atracker.data.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ProviderException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Enforces a minimum gap between requests to one provider so API rate limits are respected. */
class RateLimiter(
    private val minIntervalMs: Long,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val mutex = Mutex()
    private var last = 0L

    suspend fun acquire() = mutex.withLock {
        val wait = last + minIntervalMs - now()
        if (wait > 0) delay(wait)
        last = now()
    }
}

/**
 * Small HTTP wrapper shared by all providers: rate limiting, a polite User-Agent, retries for
 * network errors, HTTP 429 (honouring Retry-After) and 5xx. Other 4xx errors are not retried.
 */
class ApiHttp(
    private val limiter: RateLimiter,
    private val client: OkHttpClient = defaultClient,
    private val userAgent: String = "ATracker/0.1 (personal Android app)",
    private val maxAttempts: Int = 3,
    private val backoffMs: Long = 1500,
    private val maxRetryAfterMs: Long = 30_000
) {
    private class Raw(val code: Int, val retryAfterSec: Long?, val body: String)

    suspend fun get(url: String): String = execute(Request.Builder().url(url).get())

    suspend fun postJson(url: String, json: String): String =
        execute(Request.Builder().url(url).post(json.toRequestBody("application/json".toMediaType())))

    private suspend fun execute(builder: Request.Builder): String {
        val request = builder.header("User-Agent", userAgent).header("Accept", "application/json").build()
        var lastError: Exception? = null
        for (attempt in 1..maxAttempts) {
            limiter.acquire()
            try {
                val raw = withContext(Dispatchers.IO) {
                    client.newCall(request).execute().use {
                        Raw(it.code, it.header("Retry-After")?.toLongOrNull(), it.body?.string().orEmpty())
                    }
                }
                when {
                    raw.code in 200..299 -> return raw.body
                    raw.code == 429 -> {
                        lastError = ProviderException("Rate limited by the provider (HTTP 429)")
                        delay(((raw.retryAfterSec ?: 5) * 1000).coerceAtMost(maxRetryAfterMs))
                    }
                    raw.code >= 500 -> {
                        lastError = ProviderException("Provider error (HTTP ${raw.code})")
                        delay(backoffMs * attempt)
                    }
                    else -> throw ProviderException("Request rejected (HTTP ${raw.code})")
                }
            } catch (e: IOException) {
                lastError = ProviderException("Network error: ${e.message ?: e.javaClass.simpleName}", e)
                if (attempt < maxAttempts) delay(backoffMs * attempt)
            }
        }
        throw lastError ?: ProviderException("Request failed")
    }

    companion object {
        val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .callTimeout(60, TimeUnit.SECONDS)
                .build()
        }
    }
}
