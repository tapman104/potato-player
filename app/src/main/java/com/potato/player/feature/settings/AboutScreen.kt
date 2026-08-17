package com.potato.player.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.potato.player.BuildConfig
import com.potato.player.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onChangelog: () -> Unit,
    onLicenses: () -> Unit,
    onPrivacyPolicy: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about)) },
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
                ListItem(
                    headlineContent = { Text(stringResource(R.string.app_version_label)) },
                    supportingContent = { Text(BuildConfig.VERSION_NAME) },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.changelog)) },
                    leadingContent = { Icon(Icons.Default.History, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onChangelog)
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.licenses)) },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.Article, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onLicenses)
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.privacy_policy)) },
                    leadingContent = { Icon(Icons.Default.Shield, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onPrivacyPolicy)
                )
            }
        }
    }
}
