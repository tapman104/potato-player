package com.potato.player.home

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.potato.player.home.components.FolderCard
import com.potato.player.home.components.FolderExpandedFooter
import com.potato.player.home.components.FolderItemDivider
import com.potato.player.home.components.MediaFileRow
import com.potato.player.home.components.MediaSearchBar
import com.potato.player.home.components.PermissionCard
import com.potato.player.home.components.RecentFilesRow

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onFilePicked: (Uri) -> Unit,
    onSettingsClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val showVideoFiles by viewModel.showVideoFiles.collectAsState()

    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let { onFilePicked(it) } }
    )

    BackHandler(enabled = searchQuery.isNotEmpty()) {
        viewModel.onSearchQueryChanged("")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF14141C), Color(0xFF0A0A0E))
                )
            )
    ) {
        when (val state = uiState) {
            is HomeUiState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFF6C63FF),
                    strokeWidth = 2.dp
                )
            }

            is HomeUiState.PermissionRequired -> {
                PermissionCard(
                    onRequestPermission = { },
                    onPermissionResult = { granted -> viewModel.onPermissionResult(granted) }
                )
            }

            is HomeUiState.Ready -> {
                if (showVideoFiles) {
                    ReadyContent(
                        state = state,
                        searchQuery = searchQuery,
                        onSearchQueryChanged = viewModel::onSearchQueryChanged,
                        onToggleFolder = viewModel::onToggleFolder,
                        onFilePicked = onFilePicked,
                        onSettingsClick = onSettingsClick
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.Button(
                            onClick = { filePickerLauncher.launch(arrayOf("video/*")) },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF6C63FF)
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Open File", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Ready state — flat LazyColumn
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ReadyContent(
    state: HomeUiState.Ready,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onToggleFolder: (String) -> Unit,
    onFilePicked: (Uri) -> Unit,
    onSettingsClick: () -> Unit
) {
    val listState = rememberLazyListState()

    // Detect whether the list has scrolled past the first item to drive
    // the sticky-header elevation / alpha transitions.
    val hasScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                    listState.firstVisibleItemScrollOffset > 40
        }
    }
    val headerElevation by animateDpAsState(
        targetValue = if (hasScrolled) 8.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "headerElevation"
    )
    val headerAlpha by animateFloatAsState(
        targetValue = if (hasScrolled) 0.97f else 1f,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "headerAlpha"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Main flat list ────────────────────────────────────────────
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Offset the list top so content starts below the sticky header
            item(key = "__header_spacer__") {
                Spacer(modifier = Modifier.height(88.dp))
            }

            // Search bar
            item(key = "__search_bar__") {
                MediaSearchBar(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChanged
                )
            }

            // Recently played row
            item(key = "__recent_files__") {
                RecentFilesRow(
                    files = state.recentFiles,
                    onFilePicked = onFilePicked
                )
            }

            if (searchQuery.isBlank()) {
                // ── Grouped folder view ───────────────────────────────
                // Each folder expands inline as keyed items in the same
                // LazyColumn — no nested scrolling, full item recycling.
                state.folders.forEach { folderGroup ->
                    // Folder header
                    item(key = "folder_header_${folderGroup.folderPath}") {
                        FolderCard(
                            folderGroup = folderGroup,
                            onToggleExpand = { onToggleFolder(folderGroup.folderPath) }
                        )
                    }

                    if (folderGroup.isExpanded) {
                        // Expanded file rows
                        itemsIndexed(
                            items = folderGroup.files,
                            key = { _, file -> "file_${folderGroup.folderPath}_${file.uri}" }
                        ) { index, file ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF131318))
                                    .padding(horizontal = 16.dp)
                            ) {
                                MediaFileRow(
                                    file = file,
                                    onClick = { onFilePicked(file.uri) }
                                )
                            }
                            if (index < folderGroup.files.size - 1) {
                                FolderItemDivider()
                            }
                        }

                        // Rounded bottom cap for the expanded folder
                        item(key = "folder_footer_${folderGroup.folderPath}") {
                            FolderExpandedFooter()
                        }
                    }
                }
            } else {
                // ── Flat search results ───────────────────────────────
                items(
                    items = state.files,
                    key = { "search_${it.uri}" }
                ) { file ->
                    MediaFileRow(
                        file = file,
                        onClick = { onFilePicked(file.uri) }
                    )
                }
            }
        }

        // ── Sticky glassmorphism header ───────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = headerAlpha }
                .shadow(elevation = headerElevation, spotColor = Color.Black)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF14141C).copy(alpha = if (hasScrolled) 0.97f else 1f),
                            Color(0xFF14141C).copy(alpha = if (hasScrolled) 0.92f else 1f),
                            Color.Transparent
                        )
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(top = 16.dp, bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Library",
                        fontSize = if (hasScrolled) 22.sp else 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = (-0.5).sp
                    )
                    if (!hasScrolled) {
                        Text(
                            text = "${state.files.size} files",
                            fontSize = 12.sp,
                            color = Color(0xFF55556A),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFF22222E), CircleShape)
                        .clickable(onClick = onSettingsClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = "Settings",
                        tint = Color(0xFFAAAAAA),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
