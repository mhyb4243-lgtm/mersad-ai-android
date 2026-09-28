package com.mersadai.app.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface GitHubApi {
    @GET("search/repositories")
    suspend fun searchRepositories(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("per_page") pageSize: Int = 20,
        @Header("If-None-Match") etag: String? = null,
    ): Response<ResponseBody>
}

interface HuggingFaceApi {
    @GET("models")
    suspend fun listModels(
        @Query("search") query: String? = null,
        @Query("limit") pageSize: Int = 20,
        @Query("offset") offset: Int = 0,
        @Query("sort") sort: String = "downloads",
        @Query("direction") direction: Int = -1,
    ): Response<ResponseBody>
}

interface RssFeedSource {
    suspend fun fetchFeed(feedUrl: String, etag: String? = null): Result<RssPage>
}

data class RssPage(val body: String, val etag: String?, val hasMore: Boolean)
