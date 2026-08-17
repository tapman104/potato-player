package com.potato.player.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.potato.player.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onChangelog: () -> Unit,
    onLicenses: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    viewModel: AboutViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    
    val comingSoonMessage = stringResource(R.string.coming_soon)

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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // --- SECTION: APP INFO ---
            item {
                Text(
                    text = stringResource(R.string.section_app),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.app_name)) },
                    leadingContent = { Icon(Icons.Default.Movie, contentDescription = null) }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.app_version_label)) },
                    supportingContent = {
                        val buildTypeCapitalized = uiState.buildType.replaceFirstChar { it.uppercase() }
                        Text("${uiState.appVersion} ($buildTypeCapitalized)")
                    },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) }
                )
            }
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // --- SECTION: DEVICE INFO ---
            item {
                Text(
                    text = stringResource(R.string.section_device),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.android_version)) },
                    supportingContent = { Text("${uiState.androidVersion} (API ${uiState.apiLevel})") },
                    leadingContent = { Icon(Icons.Default.Android, contentDescription = null) }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.manufacturer)) },
                    supportingContent = { Text(uiState.manufacturer.replaceFirstChar { it.uppercase() }) },
                    leadingContent = { Icon(Icons.Default.PhoneAndroid, contentDescription = null) }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.model)) },
                    supportingContent = { Text(uiState.model) },
                    leadingContent = { Icon(Icons.Default.Devices, contentDescription = null) }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.device_codename)) },
                    supportingContent = { Text(uiState.device) },
                    leadingContent = { Icon(Icons.Default.Code, contentDescription = null) }
                )
            }
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // --- SECTION: LIBRARIES ---
            item {
                Text(
                    text = stringResource(R.string.section_libraries),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.mpv)) },
                    supportingContent = { Text(stringResource(R.string.version_info_coming_soon)) },
                    leadingContent = { Icon(Icons.Default.PlayCircle, contentDescription = null) }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.ffmpeg)) },
                    supportingContent = { Text(stringResource(R.string.version_info_coming_soon)) },
                    leadingContent = { Icon(Icons.Default.VideoFile, contentDescription = null) }
                )
            }
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // --- SECTION: SOURCE ---
            item {
                Text(
                    text = stringResource(R.string.section_source),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.github)) },
                    supportingContent = { Text("tapman104/potato-ultra-x") },
                    leadingContent = {
                        Icon(painterResource(R.drawable.ic_github), contentDescription = null)
                    },
                    trailingContent = {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    modifier = Modifier.clickable {
                        context.openUrl("https://github.com/tapman104/potato-ultra-x")
                    }
                )
            }
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // --- SECTION: DONATE ---
            item {
                Text(
                    text = stringResource(R.string.section_donate),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.buy_me_a_coffee)) },
                    supportingContent = { Text("@tapman") },
                    leadingContent = {
                        Icon(painterResource(R.drawable.ic_buymeacoffee), contentDescription = null)
                    },
                    trailingContent = { Icon(Icons.Default.OpenInNew, contentDescription = null) },
                    modifier = Modifier.clickable {
                        context.openUrl("https://buymeacoffee.com/tapman")
                    }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.kofi)) },
                    supportingContent = { Text("@tapman") },
                    leadingContent = {
                        Icon(painterResource(R.drawable.ic_kofi), contentDescription = null)
                    },
                    trailingContent = { Icon(Icons.Default.OpenInNew, contentDescription = null) },
                    modifier = Modifier.clickable {
                        context.openUrl("https://ko-fi.com/tapman")
                    }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.upi)) },
                    supportingContent = { Text(stringResource(R.string.upi_description)) },
                    leadingContent = { Icon(Icons.Default.Payments, contentDescription = null) },
                    modifier = Modifier.clickable {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(comingSoonMessage)
                        }
                    }
                )
            }
        }
    }
}

private fun Context.openUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, getString(R.string.no_app_found_to_open_link), Toast.LENGTH_SHORT).show()
    }
}
