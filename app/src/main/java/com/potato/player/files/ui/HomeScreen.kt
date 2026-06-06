package com.potato.player.player.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    onFilePicked: (Uri) -> Unit,
    onSettingsClick: () -> Unit,
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                onFilePicked(uri)
            }
        }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Center content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Open the app with a media Uri in the intent data or pick a file below.",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { launcher.launch(arrayOf("video/*", "audio/*")) }) {
                    Text("Pick Media File")
                }
            }
        }

        // Top-right settings button
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "Settings",
                tint = Color.White,
            )
        }
    }
}
