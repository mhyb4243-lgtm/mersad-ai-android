package com.mersadai.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

data class SourceHttpResponse(
    val statusCode: Int,
    val headers: Map<String, String>,
    val body: String,
) {
    fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
}

fun interface SourceHttpTransport {
    suspend fun get(url: String, headers: Map<String, String>): SourceHttpResponse
}

class OkHttpSourceTransport : SourceHttpTransport {
    override suspend fun get(url: String, headers: Map<String, String>): SourceHttpResponse = withContext(Dispatchers.IO) {
        require(url.startsWith("https://")) { "HTTPS is required" }
        val request = Request.Builder().url(url).get().apply {
            headers.forEach { (name, value) -> header(name, value) }
        }.build()
        NetworkClients.client.newCall(request).execute().use { response ->
            SourceHttpResponse(
                statusCode = response.code,
                headers = response.headers.toMultimap().mapValues { it.value.joinToString(",") },
                body = response.body?.string().orEmpty(),
            )
        }
    }
}