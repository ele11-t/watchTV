package com.ele.watchtv

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.ele.watchtv.data.VodItem
import com.ele.watchtv.ui.theme.WatchTvTheme

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WatchTvTheme {
                var selectedVod by remember { mutableStateOf<VodItem?>(null) }
                var searchQuery by remember { mutableStateOf("") }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        Surface(
                            shadowElevation = 8.dp,
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 4.dp
                        ) {
                            Column {
                                CenterAlignedTopAppBar(
                                    title = { 
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_logo),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(32.dp).padding(end = 8.dp)
                                            )
                                            val currentSource by viewModel.currentSource.collectAsState()
                                            Text(
                                                currentSource.name, 
                                                style = MaterialTheme.typography.titleLarge,
                                                color = MaterialTheme.colorScheme.primary
                                            ) 
                                        }
                                    },
                                    actions = {
                                        var showMenu by remember { mutableStateOf(false) }
                                        IconButton(onClick = { showMenu = true }) {
                                            Icon(Icons.Default.Tune, contentDescription = "Switch Source")
                                        }
                                        DropdownMenu(
                                            expanded = showMenu,
                                            onDismissRequest = { showMenu = false }
                                        ) {
                                            com.ele.watchtv.data.AvailableSources.forEach { source ->
                                                DropdownMenuItem(
                                                    text = { Text(source.name) },
                                                    onClick = {
                                                        showMenu = false
                                                        searchQuery = ""
                                                        viewModel.setSource(source)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                )
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { 
                                        searchQuery = it
                                        viewModel.fetchVodList(if (it.isEmpty()) null else it)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    placeholder = { Text("搜索影片、导演、演员...") },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { 
                                                searchQuery = ""
                                                viewModel.fetchVodList(null)
                                            }) {
                                                Icon(Icons.Default.Close, contentDescription = null)
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(28.dp),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                )
                                // 监听分类切换，同步清空 UI 上的搜索框
                                val categoriesState by viewModel.vodList.collectAsState()
                                LaunchedEffect(categoriesState) {
                                    // 仅当 ViewModel 内部的 keyword 为空但 UI 还有字时重置 UI
                                    // 这种方式比较稳妥，避免了 UI 状态与逻辑状态的冲突
                                    // 但由于 selectCategory 直接置空了逻辑状态，我们可以在此处检测
                                }
                                CategoryRow(viewModel, onCategorySelected = { searchQuery = "" })
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        val vodList by viewModel.vodList.collectAsState()
                        val isLoading by viewModel.isLoading.collectAsState()

                        if (isLoading && vodList.isEmpty()) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        } else {
                            VodGrid(
                                vodList = vodList,
                                onVodClick = { vod -> selectedVod = vod },
                                onReachEnd = { viewModel.loadMore() }
                            )
                        }

                        selectedVod?.let { vod ->
                            PlayerDialog(vod = vod, onDismiss = { selectedVod = null })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryRow(viewModel: MainViewModel, onCategorySelected: () -> Unit) {
    val categories by viewModel.categories.collectAsState()
    val displayMode by viewModel.displayMode.collectAsState()
    var selectedId by remember { mutableStateOf<Int?>(null) }

    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = displayMode == DisplayMode.FAVORITES,
                onClick = { 
                    selectedId = -1
                    onCategorySelected()
                    viewModel.setDisplayMode(DisplayMode.FAVORITES)
                },
                label = { Text("收藏") },
                shape = RoundedCornerShape(16.dp),
                leadingIcon = { Icon(Icons.Default.Favorite, null, modifier = Modifier.size(18.dp)) }
            )
        }
        item {
            FilterChip(
                selected = displayMode == DisplayMode.HISTORY,
                onClick = { 
                    selectedId = -2
                    onCategorySelected()
                    viewModel.setDisplayMode(DisplayMode.HISTORY)
                },
                label = { Text("历史") },
                shape = RoundedCornerShape(16.dp)
            )
        }
        item {
            FilterChip(
                selected = displayMode == DisplayMode.NORMAL && selectedId == null,
                onClick = { 
                    selectedId = null
                    onCategorySelected()
                    viewModel.setDisplayMode(DisplayMode.NORMAL)
                    viewModel.selectCategory(null)
                },
                label = { Text("全部") },
                shape = RoundedCornerShape(16.dp)
            )
        }
        items(categories) { category ->
            FilterChip(
                selected = displayMode == DisplayMode.NORMAL && selectedId == category.type_id,
                onClick = { 
                    selectedId = category.type_id
                    onCategorySelected()
                    viewModel.setDisplayMode(DisplayMode.NORMAL)
                    viewModel.selectCategory(category.type_id)
                },
                label = { Text(category.type_name) },
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

@Composable
fun VodGrid(
    vodList: List<VodItem>,
    onVodClick: (VodItem) -> Unit,
    onReachEnd: () -> Unit
) {
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

    // Observe scroll position and trigger load more
    LaunchedEffect(gridState, vodList) {
        snapshotFlow { 
            val layoutInfo = gridState.layoutInfo
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            if (visibleItemsInfo.isEmpty()) {
                false
            } else {
                val lastVisibleItemIndex = visibleItemsInfo.last().index
                val totalItemsCount = layoutInfo.totalItemsCount
                // 自动补全逻辑：如果最后一个可见项目接近列表末尾，则尝试加载更多
                lastVisibleItemIndex >= totalItemsCount - 5
            }
        }.collect { shouldLoadMore ->
            if (shouldLoadMore) {
                onReachEnd()
            }
        }
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(vodList) { vod ->
            VodCard(vod, onClick = { onVodClick(vod) })
        }
    }
}

@Composable
fun VodCard(vod: VodItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(vod.vod_pic)
                    .crossfade(true)
                    .build(),
                contentDescription = vod.vod_name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.7f),
                contentScale = ContentScale.Crop
            )

            // 底部阴影渐变和文字
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.7f)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                            startY = 300f
                        )
                    )
            ) {
                Text(
                    text = vod.vod_name,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
            }

            // 状态标签 (如: 高清, 更新至12集)
            if (!vod.vod_remarks.isNullOrEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp)
                ) {
                    Text(
                        text = vod.vod_remarks,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlayerDialog(vod: VodItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val configuration = LocalConfiguration.current
    val view = LocalView.current
    val viewModel: MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    
    // 自动感应：当屏幕物理旋转为横屏时
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    
    // 手动全屏状态
    var isManualFullscreen by remember { mutableStateOf(false) }
    var isManualPortrait by remember { mutableStateOf(false) }
    val isEffectivelyFullscreen = isLandscape || isManualFullscreen || isManualPortrait

    // 处理系统状态栏和导航栏
    LaunchedEffect(isEffectivelyFullscreen) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        if (isEffectivelyFullscreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // 解析并过滤有效播放线路 (优先保留 M3U8 格式)
    val parsedData = remember(vod.vod_play_from, vod.vod_play_url) {
        val fromNames = vod.vod_play_from?.split("$$$") ?: emptyList()
        val urlGroups = vod.vod_play_url?.split("$$$") ?: emptyList()
        
        val allParsed = fromNames.zip(urlGroups).mapNotNull { (name, urlGroup) ->
            val episodes = urlGroup.split("#").mapNotNull {
                val parts = it.split("$")
                if (parts.size >= 2) parts[0] to parts[1] else null
            }
            if (episodes.isNotEmpty()) name to episodes else null
        }

        // 过滤：仅保留线路名包含 m3u8 或链接以 m3u8 结尾的线路
        val filtered = allParsed.filter { (name, episodes) ->
            name.lowercase().contains("m3u8") || 
            episodes.firstOrNull()?.second?.lowercase()?.contains("m3u8") == true
        }

        filtered.ifEmpty { allParsed }
    }

    val sources = remember(parsedData) { parsedData.map { it.first } }
    val allEpisodes = remember(parsedData) { parsedData.map { it.second } }

    // 加载历史记录
    val history = remember(vod.vod_id) { viewModel.getHistoryForVod(vod.vod_id) }

    var selectedSourceIndex by remember { mutableIntStateOf(history?.sourceIndex ?: 0) }
    
    val currentEpisodes = remember(allEpisodes, selectedSourceIndex) {
        allEpisodes.getOrNull(selectedSourceIndex) ?: emptyList()
    }

    var currentIndex by remember(currentEpisodes) { mutableIntStateOf(history?.episodeIndex ?: 0) }
    
    val currentUrl = remember(currentEpisodes, currentIndex) { 
        currentEpisodes.getOrNull(currentIndex)?.second 
    }
    val currentEpisodeName = remember(currentEpisodes, currentIndex) { 
        currentEpisodes.getOrNull(currentIndex)?.first ?: "" 
    }

    // 保存历史记录
    LaunchedEffect(selectedSourceIndex, currentIndex) {
        viewModel.savePlaybackHistory(vod, selectedSourceIndex, currentIndex, 0L)
    }

    Dialog(
        onDismissRequest = {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !(isManualFullscreen || isManualPortrait)
        )
    ) {
        // 在 Dialog 内部监听物理返回键
        if (isManualFullscreen || isManualPortrait) {
            androidx.activity.compose.BackHandler {
                isManualFullscreen = false
                isManualPortrait = false
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = if (isEffectivelyFullscreen) Color.Black else MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 如果不是全屏，显示标题和关闭按钮
                if (!isEffectivelyFullscreen) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${vod.vod_name} $currentEpisodeName",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f)
                        )
                        
                        val favorites by viewModel.favorites.collectAsState()
                        val isFav = favorites.any { it.vod_id == vod.vod_id }
                        IconButton(onClick = { viewModel.toggleFavorite(vod) }) {
                            Icon(
                                if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (isFav) Color.Red else LocalContentColor.current
                            )
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }

                Box(
                    modifier = if (isEffectivelyFullscreen) Modifier.weight(1f).fillMaxWidth() 
                              else Modifier.fillMaxWidth().wrapContentHeight()
                ) {
                    if (currentUrl != null) {
                        VideoPlayer(
                            urls = currentEpisodes.map { it.second },
                            initialIndex = currentIndex,
                            isFullscreen = isEffectivelyFullscreen,
                            onToggleFullscreen = {
                                if (isManualFullscreen) {
                                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                    isManualFullscreen = false
                                } else {
                                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                    isManualFullscreen = true
                                    isManualPortrait = false
                                }
                            },
                            onTogglePortrait = {
                                if (isManualPortrait) {
                                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                    isManualPortrait = false
                                } else {
                                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                    isManualPortrait = true
                                    isManualFullscreen = false
                                }
                            },
                            onIndexChange = { newIndex ->
                                currentIndex = newIndex
                            }
                        )
                    } else {
                        Text("暂无播放链接", modifier = Modifier.align(Alignment.Center))
                    }
                }

                // 如果不是全屏，显示详情和选集
                if (!isEffectivelyFullscreen) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .weight(1f)
                    ) {
                        // 视频详情信息
                        val info = listOfNotNull(vod.vod_year, vod.vod_area, vod.type_name).joinToString(" | ")
                        if (info.isNotEmpty()) {
                            Text(text = info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                        if (!vod.vod_actor.isNullOrEmpty()) {
                            Text(
                                text = "主演: ${vod.vod_actor}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        if (!vod.vod_content.isNullOrEmpty()) {
                            var isExpanded by remember { mutableStateOf(false) }
                            Text(
                                text = android.text.Html.fromHtml(vod.vod_content, android.text.Html.FROM_HTML_MODE_LEGACY).toString(),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = if (isExpanded) 5 else 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .clickable { isExpanded = !isExpanded }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // 线路选择
                        Text(text = "播放线路", style = MaterialTheme.typography.titleMedium)
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(sources.size) { index ->
                                FilterChip(
                                    selected = selectedSourceIndex == index,
                                    onClick = { selectedSourceIndex = index },
                                    label = { Text(sources[index]) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "选集", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))

                        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                            columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 80.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(currentEpisodes.size) { index ->
                                val (name, url) = currentEpisodes[index]
                                val isSelected = index == currentIndex
                                Button(
                                    onClick = {
                                        currentIndex = index
                                    },
                                    colors = if (isSelected) ButtonDefaults.buttonColors() 
                                             else ButtonDefaults.filledTonalButtonColors(),
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPlayer(
    urls: List<String>, 
    initialIndex: Int,
    isFullscreen: Boolean, 
    onToggleFullscreen: () -> Unit,
    onTogglePortrait: () -> Unit,
    onIndexChange: (Int) -> Unit
) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : androidx.media3.common.Player.Listener {
                override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                    onIndexChange(currentMediaItemIndex)
                }
            })
        }
    }

    LaunchedEffect(urls) {
        val mediaItems = urls.map { MediaItem.fromUri(it) }
        exoPlayer.setMediaItems(mediaItems)
        exoPlayer.prepare()
        exoPlayer.seekTo(initialIndex, 0L)
        exoPlayer.playWhenReady = true
    }

    LaunchedEffect(initialIndex) {
        if (exoPlayer.currentMediaItemIndex != initialIndex) {
            exoPlayer.seekTo(initialIndex, 0L)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isFullscreen) Modifier.fillMaxHeight() else Modifier.aspectRatio(16 / 9f))
            .background(Color.Black)
    ) {
        AndroidView(
            factory = {
                PlayerView(context).apply {
                    player = exoPlayer
                    useController = true // 让 ExoPlayer 自带基础控制栏
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 自定义全屏切换按钮组
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 40.dp, end = 8.dp) // 增加底部间距以避开播放器自带控制栏
                .background(Color.Black.copy(alpha = 0.5f), shape = MaterialTheme.shapes.small)
        ) {
            // 竖屏全屏按钮
            IconButton(onClick = onTogglePortrait) {
                Icon(
                    imageVector = Icons.Default.StayCurrentPortrait,
                    contentDescription = "Portrait Fullscreen",
                    tint = Color.White
                )
            }
            
            // 横屏全屏按钮
            IconButton(onClick = onToggleFullscreen) {
                Icon(
                    imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = "Fullscreen",
                    tint = Color.White
                )
            }
        }
    }
}
