package co.kr.qgen.core.util

import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Retry utility for handling network failures with exponential backoff
 */
object RetryUtil {
    /**
     * Execute a suspend function with retry logic
     *
     * @param maxRetries Maximum number of retry attempts (default: 3)
     * @param initialDelayMs Initial delay in milliseconds (default: 1000ms)
     * @param maxDelayMs Maximum delay in milliseconds (default: 8000ms)
     * @param factor Exponential backoff factor (default: 2.0)
     * @param retryOn Predicate to determine if an exception should trigger a retry
     * @param block The suspend function to execute
     * @return Result of the block execution
     */
    suspend fun <T> retryWithExponentialBackoff(
        maxRetries: Int = 3,
        initialDelayMs: Long = 1000,
        maxDelayMs: Long = 8000,
        factor: Double = 2.0,
        retryOn: (Throwable) -> Boolean = ::shouldRetry,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelayMs
        var lastException: Throwable? = null

        repeat(maxRetries) { attempt ->
            try {
                return block()
            } catch (e: Throwable) {
                lastException = e

                // Check if we should retry this exception
                if (!retryOn(e)) {
                    throw e
                }

                // If this is the last attempt, throw the exception
                if (attempt == maxRetries - 1) {
                    throw e
                }

                // Wait before retrying with exponential backoff
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMs)
            }
        }

        // This should never be reached, but just in case
        throw lastException ?: IllegalStateException("Retry failed without exception")
    }

    /**
     * Determines if an exception should trigger a retry
     * Retries on:
     * - SocketTimeoutException (timeout)
     * - IOException (network errors)
     * - HttpException with 504 (Gateway Timeout) or 503 (Service Unavailable)
     */
    private fun shouldRetry(throwable: Throwable): Boolean {
        return when (throwable) {
            is SocketTimeoutException -> true
            is IOException -> true
            is HttpException -> {
                val code = throwable.code()
                code == 504 || code == 503 // Gateway Timeout or Service Unavailable
            }
            else -> false
        }
    }
}
