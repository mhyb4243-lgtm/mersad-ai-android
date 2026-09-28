package com.mersadai.app.data.remote

import okhttp3.Headers

data class RateLimitHeaders(
    val remaining: Long?,
    val resetEpochSeconds: Long?,
    val retryAfterSeconds: Long?,
) {
    companion object {
        fun from(headers: Headers): RateLimitHeaders = RateLimitHeaders(
            remaining = headers["X-RateLimit-Remaining"]?.toLongOrNull(),
            resetEpochSeconds = headers["X-RateLimit-Reset"]?.toLongOrNull(),
            retryAfterSeconds = headers["Retry-After"]?.toLongOrNull(),
        )
    }
}

sealed interface RemoteFailure {
    data class RateLimited(val retryAfterSeconds: Long?, val resetEpochSeconds: Long?) : RemoteFailure
    data object Forbidden : RemoteFailure
    data class Http(val statusCode: Int) : RemoteFailure

    companion object {
        fun from(statusCode: Int, headers: Headers): RemoteFailure = when (statusCode) {
            429 -> RateLimitHeaders.from(headers).let { RateLimited(it.retryAfterSeconds, it.resetEpochSeconds) }
            403 -> Forbidden
            else -> Http(statusCode)
        }
    }
}
