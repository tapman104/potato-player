package com.potato.player.home

import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            .background(Color(0xFF0A0A0A))
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
                            .padding(horizontal = 20.dp)
                            .padding(top = 48.dp, bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "VIDEOS",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF9800),
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .height(2.dp)
                                    .fillMaxWidth(0.15f)
                                    .background(Color(0xFFFF9800))
                            )
                        }
                        
                        Spacer(modifier = Modifier.size(24.dp))
                        
                        Column {
                            Text(
                                text = "PLAYLISTS",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFAAAAAA),
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .height(2.dp)
                                    .fillMaxWidth(0.2f)
                                    .background(Color.Transparent)
                            )
                        }
                        
                        Spacer(modifier = Modifier.weight(1f))
                        
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFF1C1C1C), CircleShape)
                                .clickable(onClick = onSettingsClick),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = Color(0xFFAAAAAA), modifier = Modifier.size(24.dp))
                        }
                    }

                    if (showVideoFiles) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            item {
                                MediaSearchBar(
                                    query = searchQuery,
                                    onQueryChange = { viewModel.onSearchQueryChanged(it) }
                                )
                            }

                            item {
                                RecentFilesRow(
                                    files = state.recentFiles,
                                    onFilePicked = onFilePicked
                                )
                            }

                            items(state.files) { file ->
                                MediaFileRow(
                                    file = file,
                                    onClick = { onFilePicked(file.uri) }
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.Button(
                                onClick = { filePickerLauncher.launch(arrayOf("video/*")) },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                            ) {
                                Text("Open File", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
