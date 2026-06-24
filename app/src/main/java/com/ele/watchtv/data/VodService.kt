package com.ele.watchtv.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface VodService {
    @GET("api.php/provide/vod/at/json/")
    suspend fun getVodList(
        @Query("ac") action: String = "videolist",
        @Query("pg") page: Int = 1,
        @Query("wd") keyword: String? = null,
        @Query("t") typeId: Int? = null
    ): VodResponse

    companion object {
        private const val BASE_URL = "https://cj.lziapi.com/"

        val instance: VodService by lazy {
            Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(VodService::class.java)
        }
    }
}
