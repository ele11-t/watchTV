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
    val vod_year: String?
)
