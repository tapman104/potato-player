package com.potato.player.feature.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.potato.player.data.library.MediaLibraryRepository
import com.potato.player.data.library.VideoItem
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import com.potato.player.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(
    bucketId: Long,
    folderName: String,
    onNavigateToPlayer: (uri: String, title: String, playlist: List<VideoItem>) -> Unit,
    onBack: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: FolderViewModel = hiltViewModel()
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(folderName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (videos.isNotEmpty()) {
                            Text(
                                "${videos.size} video${if (videos.size == 1) "" else "s"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            PotatoPillBar(
                selectedTab = PillBarTab.FOLDERS,
                onFoldersClick = onBack,
                onSettingsClick = onNavigateToSettings
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                videos.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.no_videos_in_folder))
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        items(videos, key = { it.id }) { video ->
                            VideoRow(
                                video = video,
                                onClick = {
                                    onNavigateToPlayer(video.uri.toString(), video.title, videos)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoRow(
    video: VideoItem,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                video.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                "${MediaLibraryRepository.formatDuration(video.durationMs)} · ${MediaLibraryRepository.formatSize(video.sizeBytes)}",
                style = MaterialTheme.typography.bodySmall
            )
        },
        leadingContent = {
            coil3.compose.AsyncImage(
                model = video.uri,
                contentDescription = null,
                placeholder = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.PlayCircle),
                error = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.Warning),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
