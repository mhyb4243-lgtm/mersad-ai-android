package com.mersadai.app.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object NetworkClients {
    internal val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(false)
            .addInterceptor { chain ->
                require(chain.request().url.scheme == "https") { "HTTPS is required" }
                chain.proceed(chain.request())
            }
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .build()
    }

    val github: GitHubApi by lazy {
        retrofit("https://api.github.com/").create(GitHubApi::class.java)
    }

    val huggingFace: HuggingFaceApi by lazy {
        retrofit("https://huggingface.co/api/").create(HuggingFaceApi::class.java)
    }

    private fun retrofit(baseUrl: String): Retrofit {
        require(baseUrl.startsWith("https://"))
        return Retrofit.Builder().baseUrl(baseUrl).client(client).build()
    }
}
