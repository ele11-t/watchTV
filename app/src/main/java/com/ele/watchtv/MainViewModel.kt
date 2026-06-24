package com.ele.watchtv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ele.watchtv.data.CategoryItem
import com.ele.watchtv.data.VodItem
import com.ele.watchtv.data.VodService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    private val _vodList = MutableStateFlow<List<VodItem>>(emptyList())
    val vodList: StateFlow<List<VodItem>> = _vodList

    private val _categories = MutableStateFlow<List<CategoryItem>>(emptyList())
    val categories: StateFlow<List<CategoryItem>> = _categories

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

    private fun fetchCategories() {
        viewModelScope.launch {
            try {
                val response = VodService.instance.getVodList(action = "list")
                val excludedNames = listOf("电影片", "连续剧", "综艺片", "动漫片", "娱乐新闻", "电影资讯", "新闻资讯", "演员")
                _categories.value = response.categories?.filter { it.type_name !in excludedNames } ?: emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun selectCategory(typeId: Int?) {
        if (currentTypeId == typeId) return
        currentTypeId = typeId
        currentKeyword = null // 优化点 2：切换分类时清空搜索词
        fetchVodList(keyword = null)
    }

    fun fetchVodList(keyword: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            currentKeyword = keyword
            currentPage = 1
            isLastPage = false
            try {
                val response = VodService.instance.getVodList(
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
        if (_isLoading.value || isLastPage) return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val nextPage = currentPage + 1
                val response = VodService.instance.getVodList(
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
