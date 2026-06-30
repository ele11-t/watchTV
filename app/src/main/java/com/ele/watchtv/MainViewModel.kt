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

enum class DisplayMode { NORMAL, FAVORITES, HISTORY, TODAY }

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

    private val _crossSourceResults = MutableStateFlow<Map<String, VodItem>>(emptyMap())
    val crossSourceResults: StateFlow<Map<String, VodItem>> = _crossSourceResults

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
        // Ensure source info is attached before saving
        val vodWithSource = if (vod.sourceName == null) {
            vod.copy(sourceName = _currentSource.value.name, sourceApiUrl = _currentSource.value.apiUrl)
        } else vod

        if (persistence.isFavorite(vodWithSource.vod_id)) {
            persistence.removeFavorite(vodWithSource.vod_id)
        } else {
            persistence.saveFavorite(vodWithSource)
        }
        _favorites.value = persistence.getFavorites()
    }

    fun savePlaybackHistory(vod: VodItem, sourceIndex: Int, episodeIndex: Int, positionMs: Long) {
        // Ensure source info is attached before saving
        val vodWithSource = if (vod.sourceName == null) {
            vod.copy(sourceName = _currentSource.value.name, sourceApiUrl = _currentSource.value.apiUrl)
        } else vod
        
        val item = HistoryItem(vodWithSource, sourceIndex, episodeIndex, positionMs)
        persistence.saveHistory(item)
        _history.value = persistence.getHistory()
    }

    fun getHistoryForVod(vodId: Int) = persistence.getHistoryForVod(vodId)

    fun searchAcrossSources(vodName: String) {
        _crossSourceResults.value = emptyMap()
        viewModelScope.launch {
            AvailableSources.forEach { source ->
                if (source.name == _currentSource.value.name) return@forEach
                
                launch {
                    try {
                        val response = VodService.instance.getVodList(url = source.apiUrl, keyword = vodName)
                        val match = response.list?.find { it.vod_name == vodName }
                        if (match != null) {
                            val enrichedMatch = match.copy(sourceName = source.name, sourceApiUrl = source.apiUrl)
                            val currentMap = _crossSourceResults.value
                            _crossSourceResults.value = currentMap + (source.name to enrichedMatch)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun clearCrossSourceResults() {
        _crossSourceResults.value = emptyMap()
    }

    private fun fetchCategories() {
        viewModelScope.launch {
            try {
                val url = _currentSource.value.apiUrl
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
            _vodList.value = persistence.getFavorites().distinctBy { "${it.sourceName}_${it.vod_id}" }
        } else if (mode == DisplayMode.HISTORY) {
            _vodList.value = persistence.getHistory().map { it.vod }.distinctBy { "${it.sourceName}_${it.vod_id}" }
        } else {
            // NORMAL or TODAY
            fetchVodList()
        }
    }

    fun selectCategory(typeId: Int?) {
        _displayMode.value = DisplayMode.NORMAL
        if (currentTypeId == typeId) return
        currentTypeId = typeId
        currentKeyword = null
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
                val source = _currentSource.value
                val response = VodService.instance.getVodList(
                    url = source.apiUrl,
                    keyword = keyword,
                    page = currentPage,
                    typeId = currentTypeId,
                    hours = if (_displayMode.value == DisplayMode.TODAY) 24 else null
                )
                val newList = response.list?.map { it.copy(sourceName = source.name, sourceApiUrl = source.apiUrl) } ?: emptyList()
                _vodList.value = newList.distinctBy { "${it.sourceName}_${it.vod_id}" }
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

    fun loadMore() {
        if (_isLoading.value || isLastPage || _displayMode.value != DisplayMode.NORMAL) return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val source = _currentSource.value
                val nextPage = currentPage + 1
                val response = VodService.instance.getVodList(
                    url = source.apiUrl,
                    keyword = currentKeyword,
                    page = nextPage,
                    typeId = currentTypeId,
                    hours = if (_displayMode.value == DisplayMode.TODAY) 24 else null
                )
                val newList = response.list?.map { it.copy(sourceName = source.name, sourceApiUrl = source.apiUrl) } ?: emptyList()
                if (newList.isNotEmpty()) {
                    val combinedList = _vodList.value + newList
                    _vodList.value = combinedList.distinctBy { "${it.sourceName}_${it.vod_id}" }
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

    fun onVodClicked(vod: VodItem, onReady: (VodItem) -> Unit) {
        // Automatic Source Switching Logic
        val sourceMatch = AvailableSources.find { it.apiUrl == vod.sourceApiUrl || it.name == vod.sourceName }
        if (sourceMatch != null && sourceMatch != _currentSource.value) {
            // Switch current source to match the VOD's source
            _currentSource.value = sourceMatch
            _displayMode.value = DisplayMode.NORMAL
            currentTypeId = null
            currentKeyword = null
            // We don't fully refresh everything here because we want to play the video immediately.
            // But we update the source so subsequent actions (like closing the dialog) feel natural.
            fetchCategories()
            // fetchVodList() // Optional: could cause UI jump, skip if immediately playing
        }
        onReady(vod)
    }
}
