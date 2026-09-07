package com.potato.player.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.potato.player.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedScreen(
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.category_advanced)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back)
                        )
                    }
                }
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
                    text = stringResource(R.string.settings_desc_advanced),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.configuration_location)) },
                    supportingContent = { Text(stringResource(R.string.coming_soon)) }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.mpv_conf)) },
                    supportingContent = { Text(stringResource(R.string.coming_soon)) }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.input_conf)) },
                    supportingContent = { Text(stringResource(R.string.coming_soon)) }
                )
            }
        }
    }
}
