package com.ele.watchtv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ele.watchtv.data.AvailableSources
import com.ele.watchtv.data.CategoryItem
import com.ele.watchtv.data.HistoryItem
import com.ele.watchtv.data.PersistenceManager
import com.ele.watchtv.data.VodItem
import com.ele.watchtv.data.VodService
import com.ele.watchtv.data.VodSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class DisplayMode { NORMAL, FAVORITES, HISTORY }

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val persistence = PersistenceManager(application)

    private val _currentSource = MutableStateFlow(AvailableSources[0])
    val currentSource: StateFlow<VodSource> = _currentSource

    private val _displayMode = MutableStateFlow(DisplayMode.NORMAL)
    val displayMode: StateFlow<DisplayMode> = _displayMode

    private val _vodList = MutableStateFlow<List<VodItem>>(emptyList())
    val vodList: StateFlow<List<VodItem>> = _vodList

    private val _categories = MutableStateFlow<List<CategoryItem>>(emptyList())
    val categories: StateFlow<List<CategoryItem>> = _categories

    private val _favorites = MutableStateFlow<List<VodItem>>(persistence.getFavorites())
    val favorites: StateFlow<List<VodItem>> = _favorites

    private val _history = MutableStateFlow<List<HistoryItem>>(persistence.getHistory())
    val history: StateFlow<List<HistoryItem>> = _history

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var currentPage = 1
    private var currentKeyword: String? = null
    private var currentTypeId: Int? = null
    private var isLastPage = false

    init {
        fetchCategories()
        fetchVodList()
    }

    fun setSource(source: VodSource) {
        if (_currentSource.value == source) return
        _currentSource.value = source
        _displayMode.value = DisplayMode.NORMAL
        currentTypeId = null
        currentKeyword = null
        _categories.value = emptyList()
        _vodList.value = emptyList()
        fetchCategories()
        fetchVodList()
    }

    fun toggleFavorite(vod: VodItem) {
        if (persistence.isFavorite(vod.vod_id)) {
            persistence.removeFavorite(vod.vod_id)
        } else {
            persistence.saveFavorite(vod)
        }
        _favorites.value = persistence.getFavorites()
    }

    fun savePlaybackHistory(vod: VodItem, sourceIndex: Int, episodeIndex: Int, positionMs: Long) {
        val item = HistoryItem(vod, sourceIndex, episodeIndex, positionMs)
        persistence.saveHistory(item)
        _history.value = persistence.getHistory()
    }

    fun getHistoryForVod(vodId: Int) = persistence.getHistoryForVod(vodId)

    private fun fetchCategories() {
        viewModelScope.launch {
            try {
                val url = VodService.buildUrl(_currentSource.value.baseUrl)
                val response = VodService.instance.getVodList(url = url, action = "list")
                val excludedNames = listOf("电影片", "连续剧", "综艺片", "动漫片", "娱乐新闻", "电影资讯", "新闻资讯", "演员")
                _categories.value = response.categories?.filter { it.type_name !in excludedNames } ?: emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setDisplayMode(mode: DisplayMode) {
        _displayMode.value = mode
        if (mode == DisplayMode.FAVORITES) {
            _vodList.value = persistence.getFavorites()
        } else if (mode == DisplayMode.HISTORY) {
            _vodList.value = persistence.getHistory().map { it.vod }
        } else {
            fetchVodList()
        }
    }

    fun selectCategory(typeId: Int?) {
        _displayMode.value = DisplayMode.NORMAL
        if (currentTypeId == typeId) return
        currentTypeId = typeId
        currentKeyword = null // 优化点 2：切换分类时清空搜索词
        fetchVodList(keyword = null)
    }

    fun fetchVodList(keyword: String? = null) {
        if (_displayMode.value != DisplayMode.NORMAL && keyword == null) return
        if (keyword != null) _displayMode.value = DisplayMode.NORMAL

        viewModelScope.launch {
            _isLoading.value = true
            currentKeyword = keyword
            currentPage = 1
            isLastPage = false
            try {
                val url = VodService.buildUrl(_currentSource.value.baseUrl)
                val response = VodService.instance.getVodList(
                    url = url,
                    keyword = keyword,
                    page = currentPage,
                    typeId = currentTypeId
                )
                _vodList.value = response.list ?: emptyList()
                if ((response.list?.size ?: 0) < (response.limit?.toIntOrNull() ?: 20)) {
                    isLastPage = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadMore() {
        if (_isLoading.value || isLastPage || _displayMode.value != DisplayMode.NORMAL) return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val url = VodService.buildUrl(_currentSource.value.baseUrl)
                val nextPage = currentPage + 1
                val response = VodService.instance.getVodList(
                    url = url,
                    keyword = currentKeyword,
                    page = nextPage,
                    typeId = currentTypeId
                )
                val newList = response.list ?: emptyList()
                if (newList.isNotEmpty()) {
                    _vodList.value = _vodList.value + newList
                    currentPage = nextPage
                } else {
                    isLastPage = true
                }
                
                if (newList.size < (response.limit?.toIntOrNull() ?: 20)) {
                    isLastPage = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
