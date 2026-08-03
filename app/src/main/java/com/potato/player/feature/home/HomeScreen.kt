package com.potato.player.feature.home

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.content.pm.ActivityInfo
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

import com.potato.player.data.library.FolderItem
import com.potato.player.data.library.MediaLibraryRepository
import com.potato.player.util.MediaMetadataRepository
import com.potato.player.util.findActivity
import com.potato.player.util.lockOrientation
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToPlayer: (videoUri: String, title: String) -> Unit,
    onNavigateToFolder: (bucketId: Long, folderName: String) -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    DisposableEffect(lifecycleOwner, activity) {
        val window = activity?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        fun applyPortrait() {
            if (activity?.intent?.action == android.content.Intent.ACTION_VIEW) return
            lockOrientation(activity, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        applyPortrait()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                applyPortrait()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var folders by remember { mutableStateOf<List<FolderItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(checkPermission(context)) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun loadFolders() {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                folders = MediaLibraryRepository.getFolders(context)
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Failed to scan videos"
            } finally {
                isLoading = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermission = results.values.any { it }
        if (hasPermission) loadFolders()
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) loadFolders()
    }

    LaunchedEffect(pendingUri) {
        pendingUri?.let { uri ->
            val uriStr = uri.toString()
            val title = MediaMetadataRepository.resolveTitle(context, uri)
            onNavigateToPlayer(uriStr, title)
            pendingUri = null
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingUri = uri
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Potato Player") },
                actions = {
                    if (hasPermission) {
                        IconButton(onClick = { loadFolders() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(end = 4.dp)
            ) {
                FloatingActionButton(
                    onClick = { launcher.launch("video/*") },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Default.Folder, contentDescription = "Open Video")
                }
                FloatingActionButton(
                    onClick = { launcher.launch("*/*") }
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = "Open File")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                !hasPermission -> {
                    PermissionRequest(
                        onRequest = {
                            permissionLauncher.launch(requiredPermissions())
                        }
                    )
                }
                isLoading && folders.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                errorMessage != null && folders.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(errorMessage ?: "Error", color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { loadFolders() }) { Text("Retry") }
                        }
                    }
                }
                folders.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.VideoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Text("No videos found", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Use the buttons below to open a file",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item {
                            Text(
                                text = "${folders.size} folders · ${folders.sumOf { it.videoCount }} videos",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                            )
                        }
                        items(folders, key = { it.bucketId }) { folder ->
                            FolderRow(
                                folder = folder,
                                onClick = { onNavigateToFolder(folder.bucketId, folder.name) }
                            )
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderRow(
    folder: FolderItem,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                folder.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                "${folder.videoCount} video${if (folder.videoCount == 1) "" else "s"} · ${MediaLibraryRepository.formatSize(folder.totalSizeBytes)}",
                style = MaterialTheme.typography.bodySmall
            )
        },
        leadingContent = {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun PermissionRequest(onRequest: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.VideoLibrary,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Allow access to your videos",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Potato Player needs permission to scan and show videos on your device, just like MX Player.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRequest) {
                Text("Grant permission")
            }
        }
    }
}

private fun requiredPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

private fun checkPermission(context: android.content.Context): Boolean {
    return requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}
