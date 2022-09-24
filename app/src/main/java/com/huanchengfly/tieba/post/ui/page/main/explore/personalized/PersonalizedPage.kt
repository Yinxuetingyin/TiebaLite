package com.huanchengfly.tieba.post.ui.page.main.explore.personalized

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.activities.ThreadActivity
import com.huanchengfly.tieba.post.arch.collectPartialAsState
import com.huanchengfly.tieba.post.arch.pageViewModel
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.page.main.explore.Dislike
import com.huanchengfly.tieba.post.ui.page.main.explore.FeedCard
import com.huanchengfly.tieba.post.ui.widgets.compose.LazyLoad
import com.huanchengfly.tieba.post.ui.widgets.compose.LoadMoreLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PersonalizedPage(
    viewModel: PersonalizedViewModel = pageViewModel()
) {
    LazyLoad(loaded = viewModel.initialized) {
        viewModel.send(PersonalizedUiIntent.Refresh)
        viewModel.initialized = true
    }
    val context = LocalContext.current
    val isRefreshing by viewModel.uiState.collectPartialAsState(
        prop1 = PersonalizedUiState::isRefreshing,
        initial = true
    )
    val isLoadingMore by viewModel.uiState.collectPartialAsState(
        prop1 = PersonalizedUiState::isLoadingMore,
        initial = false
    )
    val currentPage by viewModel.uiState.collectPartialAsState(
        prop1 = PersonalizedUiState::currentPage,
        initial = 1
    )
    val data by viewModel.uiState.collectPartialAsState(
        prop1 = PersonalizedUiState::data,
        initial = emptyList()
    )
    val threadPersonalizedData by viewModel.uiState.collectPartialAsState(
        prop1 = PersonalizedUiState::threadPersonalizedData,
        initial = emptyList()
    )
    val refreshPosition by viewModel.uiState.collectPartialAsState(
        prop1 = PersonalizedUiState::refreshPosition,
        initial = 0
    )
    val hiddenThreadIds by viewModel.uiState.collectPartialAsState(
        prop1 = PersonalizedUiState::hiddenThreadIds,
        initial = emptyList()
    )
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = isRefreshing)
    val lazyStaggeredGridState = rememberLazyStaggeredGridState()
    var refreshCount by remember {
        mutableStateOf(0)
    }
    var showRefreshTip by remember {
        mutableStateOf(false)
    }
    rememberCoroutineScope().launch {
        viewModel.uiEventFlow
            .filterIsInstance<PersonalizedUiEvent.RefreshSuccess>()
            .collect {
                refreshCount = it.count
                showRefreshTip = true
            }
    }
    if (showRefreshTip) {
        LaunchedEffect(Unit) {
            delay(2000)
            showRefreshTip = false
        }
    }

    Box {
        SwipeRefresh(
            state = swipeRefreshState,
            onRefresh = { viewModel.send(PersonalizedUiIntent.Refresh) },
        ) {
            LoadMoreLayout(
                isLoading = isLoadingMore,
                loadEnd = false,
                onLoadMore = { viewModel.send(PersonalizedUiIntent.LoadMore(currentPage + 1)) },
            ) {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Adaptive(240.dp),
                    state = lazyStaggeredGridState
                ) {
                    itemsIndexed(
                        items = data,
                        key = { _, item -> "${item.id}" },
                        contentType = { _, item ->
                            when {
                                item.videoInfo != null -> "Video"
                                item.media.isNotEmpty() -> "Media"
                                else -> "PlainText"
                            }
                        }
                    ) { index, item ->
                        Column {
                            AnimatedVisibility(
                                visible = !hiddenThreadIds.contains(item.threadId),
                                enter = EnterTransition.None,
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                FeedCard(
                                    item = item,
                                    onClick = {
                                        ThreadActivity.launch(context, item.threadId.toString())
                                    },
                                    onAgree = {
                                        viewModel.send(
                                            PersonalizedUiIntent.Agree(
                                                item.threadId,
                                                item.firstPostId,
                                                item.agree?.hasAgree ?: 0
                                            )
                                        )
                                    },
                                ) {
                                    Dislike(
                                        personalized = threadPersonalizedData[index],
                                        onDislike = { clickTime, reasons ->
                                            viewModel.send(
                                                PersonalizedUiIntent.Dislike(
                                                    item.forumInfo?.id ?: 0,
                                                    item.threadId,
                                                    reasons,
                                                    clickTime
                                                )
                                            )
                                        }
                                    )
                                }
                            }
                            if (!hiddenThreadIds.contains(item.threadId)) {
                                if ((refreshPosition == 0 || index + 1 != refreshPosition) && index < data.size - 1) {
                                    Divider(
                                        color = ExtendedTheme.colors.divider,
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        thickness = 2.dp
                                    )
                                }
                            }
                            if (refreshPosition != 0 && index + 1 == refreshPosition) {
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.send(PersonalizedUiIntent.Refresh) }
                                        .padding(8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = null
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(text = stringResource(id = R.string.tip_refresh), style = MaterialTheme.typography.subtitle1)
                                }
                            }
                        }
                    }
                }
                LaunchedEffect(data.firstOrNull()?.id) {
                    //delay(50)
                    lazyStaggeredGridState.scrollToItem(0, 0)
                }
            }
        }

        AnimatedVisibility(
            visible = showRefreshTip,
            enter = fadeIn() + slideInVertically(),
            exit = slideOutVertically() + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 72.dp)
                    .clip(RoundedCornerShape(100))
                    .background(
                        color = ExtendedTheme.colors.accent,
                        shape = RoundedCornerShape(100)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .align(Alignment.TopCenter)
            ) {
                Text(text = stringResource(id = R.string.toast_feed_refresh, refreshCount), color = ExtendedTheme.colors.onAccent)
            }
        }
    }
}