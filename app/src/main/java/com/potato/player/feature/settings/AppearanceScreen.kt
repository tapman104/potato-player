package com.potato.player.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.potato.player.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    viewModel: AppearanceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showThemeDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.category_appearance)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                Text(
                    text = stringResource(R.string.section_interface),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
                )
            }
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Column {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.theme_mode)) },
                            supportingContent = {
                                val label = when (uiState.themeMode) {
                                    "system" -> stringResource(R.string.theme_system)
                                    "light"  -> stringResource(R.string.theme_light)
                                    "dark"   -> stringResource(R.string.theme_dark)
                                    "amoled" -> stringResource(R.string.theme_amoled)
                                    else     -> uiState.themeMode
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(label)
                                    
                                    val themePreview = when (uiState.themeMode) {
                                        "light" -> {
                                            @Composable {
                                                Box(
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .background(Color(0xFFFFFBFE), CircleShape)
                                                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                                )
                                            }
                                        }
                                        "dark" -> {
                                            @Composable {
                                                Box(
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .background(Color(0xFF1C1B1F), CircleShape)
                                                )
                                            }
                                        }
                                        "amoled" -> {
                                            @Composable {
                                                Box(
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .background(Color.Black, CircleShape)
                                                        .border(1.dp, Color.White, CircleShape)
                                                )
                                            }
                                        }
                                        else -> null
                                    }
                                    
                                    if (themePreview != null) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        themePreview()
                                    }
                                }
                            },
                            modifier = Modifier.clickable { showThemeDialog = true }
                        )
                    }
                }
            }
        }

        if (showThemeDialog) {
            AlertDialog(
                onDismissRequest = { showThemeDialog = false },
                title = { Text(stringResource(R.string.dialog_theme_mode)) },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        val options = listOf(
                            "system" to stringResource(R.string.theme_system),
                            "light"  to stringResource(R.string.theme_light),
                            "dark"   to stringResource(R.string.theme_dark),
                            "amoled" to stringResource(R.string.theme_amoled)
                        )
                        options.forEach { (code, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .selectable(
                                        selected = (code == uiState.themeMode),
                                        onClick = {
                                            viewModel.setThemeMode(code)
                                            showThemeDialog = false
                                        }
                                    )
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (code == uiState.themeMode),
                                    onClick = null
                                )
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(start = 16.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showThemeDialog = false }) {
                        Text(stringResource(R.string.close))
                    }
                }
            )
        }
    }
}
