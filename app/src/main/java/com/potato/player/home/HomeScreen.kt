package com.potato.player.home

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.potato.player.home.components.FolderCard
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
    ) {
        when (val state = uiState) {
            is HomeUiState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }

            is HomeUiState.PermissionRequired -> {
                PermissionCard(
                    onRequestPermission = { },
                    onPermissionResult = { granted -> viewModel.onPermissionResult(granted) }
                )
            }

            is HomeUiState.Ready -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Potato Player",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = Color.White)
                        }
                    }

                    MediaSearchBar(
                        query = searchQuery,
                        onQueryChange = { viewModel.onSearchQueryChanged(it) }
                    )

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            RecentFilesRow(
                                files = state.recentFiles,
                                onFilePicked = onFilePicked
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        items(state.folders) { folderGroup ->
                            FolderCard(
                                folderGroup = folderGroup,
                                onToggleExpand = { viewModel.onToggleFolder(folderGroup.folderPath) },
                                onFilePicked = onFilePicked
                            )
                        }
                    }
                }
            }
        }
    }
}
