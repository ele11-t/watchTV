package com.ele.watchtv.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

interface VodService {
    @GET
    suspend fun getVodList(
        @Url url: String,
        @Query("ac") action: String = "videolist",
        @Query("pg") page: Int = 1,
        @Query("wd") keyword: String? = null,
        @Query("t") typeId: Int? = null
    ): VodResponse

    companion object {
        val instance: VodService by lazy {
            Retrofit.Builder()
                .baseUrl("https://localhost/") // Placeholder, using @Url for actual requests
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(VodService::class.java)
        }
        
        fun buildUrl(baseUrl: String): String {
            return "${baseUrl.trimEnd('/')}/api.php/provide/vod/at/json/"
        }
    }
}
