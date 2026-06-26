package com.ele.watchtv.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PersistenceManager(context: Context) {
    private val prefs = context.getSharedPreferences("watch_tv_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveFavorite(vod: VodItem) {
        val favorites = getFavorites().toMutableList()
        if (favorites.none { it.vod_id == vod.vod_id }) {
            favorites.add(0, vod)
            prefs.edit().putString("favorites", gson.toJson(favorites)).apply()
        }
    }

    fun removeFavorite(vodId: Int) {
        val favorites = getFavorites().toMutableList()
        if (favorites.removeIf { it.vod_id == vodId }) {
            prefs.edit().putString("favorites", gson.toJson(favorites)).apply()
        }
    }

    fun isFavorite(vodId: Int): Boolean {
        return getFavorites().any { it.vod_id == vodId }
    }

    fun getFavorites(): List<VodItem> {
        val json = prefs.getString("favorites", null) ?: return emptyList()
        val type = object : TypeToken<List<VodItem>>() {}.type
        return gson.fromJson(json, type)
    }

    fun saveHistory(historyItem: HistoryItem) {
        val history = getHistory().toMutableList()
        // Remove existing entry for the same VOD to move it to the top
        history.removeIf { it.vod.vod_id == historyItem.vod.vod_id }
        history.add(0, historyItem)
        // Keep only last 50 items
        val limitedHistory = if (history.size > 50) history.take(50) else history
        prefs.edit().putString("history", gson.toJson(limitedHistory)).apply()
    }

    fun getHistory(): List<HistoryItem> {
        val json = prefs.getString("history", null) ?: return emptyList()
        val type = object : TypeToken<List<HistoryItem>>() {}.type
        return gson.fromJson(json, type)
    }
    
    fun getHistoryForVod(vodId: Int): HistoryItem? {
        return getHistory().find { it.vod.vod_id == vodId }
    }
}
