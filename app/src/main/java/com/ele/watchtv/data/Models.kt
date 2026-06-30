package com.ele.watchtv.data

import com.google.gson.annotations.SerializedName

data class VodResponse(
    val code: Int?,
    val msg: String?,
    val page: Int?,
    val pagecount: Int?,
    val limit: String?,
    val total: Int?,
    val list: List<VodItem>?,
    @SerializedName("class")
    val categories: List<CategoryItem>?
)

data class CategoryItem(
    val type_id: Int,
    val type_name: String
)

data class VodItem(
    val vod_id: Int,
    val vod_name: String,
    val type_name: String?,
    val vod_pic: String?,
    val vod_remarks: String?,
    val vod_play_from: String?,
    val vod_play_url: String?,
    val vod_time: String?,
    val vod_content: String?,
    val vod_actor: String?,
    val vod_director: String?,
    val vod_area: String?,
    val vod_year: String?,
    // Add source info for persistence and cross-source playback
    val sourceName: String? = null,
    val sourceApiUrl: String? = null
)

data class VodSource(
    val name: String,
    val apiUrl: String
)

val AvailableSources = listOf(
    VodSource("量子资源", "https://cj.lziapi.com/api.php/provide/vod/at/json/"),
    VodSource("红牛资源", "https://www.hongniuzy2.com/api.php/provide/vod/at/json/"),
    VodSource("最大资源", "https://api.zuidapi.com/api.php/provide/vod/at/json/"),
    VodSource("金鹰资源", "https://jyzyapi.com/api.php/provide/vod/at/json/")
)

data class HistoryItem(
    val vod: VodItem,
    val sourceIndex: Int,
    val episodeIndex: Int,
    val positionMs: Long,
    val timestamp: Long = System.currentTimeMillis()
)
