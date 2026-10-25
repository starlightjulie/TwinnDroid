package com.twinndroid.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import com.twinndroid.R
import androidx.compose.ui.res.stringResource
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/** Second tab: pick an installed app or an APK file to clone. */
@Composable
fun NewClonePage(vm: MainViewModel) {
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.newFromFile(uri)
    }
    AppListPage(
        vm = vm,
        title = stringResource(R.string.new_clone_title),
        subtitle = stringResource(R.string.new_clone_desc),
        trailing = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        header = {
            TwinnCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Row(
                    Modifier.fillMaxWidth().clickable { pickFile.launch(arrayOf("*/*")) }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBubble(Icons.Rounded.FolderOpen)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.from_apk_file), style = MaterialTheme.typography.titleMedium)
                        Text(
                            ".apk, .apks, .xapk or .apkm",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
                }
            }
        },
        onPick = { vm.newFromInstalled(it.packageName) },
    )
}

/** Third tab: save any installed app as an installable file. */
@Composable
fun ExtractPage(vm: MainViewModel) {
    AppListPage(
        vm = vm,
        title = stringResource(R.string.extract_title),
        subtitle = stringResource(R.string.extract_desc),
        trailing = Icons.Rounded.FileDownload,
        header = {},
        onPick = { vm.openExport(it.packageName) },
    )
    vm.export?.let { ExportSheet(vm, it) }
}

@Composable
fun IconBubble(icon: ImageVector) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
}

@Composable
private fun AppListPage(
    vm: MainViewModel,
    title: String,
    subtitle: String,
    trailing: ImageVector,
    header: @Composable () -> Unit,
    onPick: (AppEntry) -> Unit,
) {
    LaunchedEffect(Unit) { vm.loadApps() }
    var query by rememberSaveable { mutableStateOf("") }
    var showSystem by rememberSaveable { mutableStateOf(false) }
    val apps = vm.apps
    val filtered = remember(apps, query, showSystem) {
        apps.orEmpty().filter { a ->
            (showSystem || !a.isSystem) &&
                (query.isBlank() || a.label.contains(query, true) || a.packageName.contains(query, true))
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "title") {
            Column(Modifier.statusBarsPadding().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp)) {
                Text(title, style = MaterialTheme.typography.headlineMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item(key = "header") { header() }
        item(key = "search") {
            Column(Modifier.padding(horizontal = 16.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, stringResource(R.string.clear)) } },
                    placeholder = { Text(stringResource(R.string.search_apps)) },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.8f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.padding(top = 8.dp)) {
                    FilterChip(selected = showSystem, onClick = { showSystem = !showSystem }, label = { Text(stringResource(R.string.show_system_apps)) })
                }
            }
        }
        if (apps == null) {
            item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }
        items(filtered, key = { it.packageName }) { app ->
            TwinnCard(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .animateItem(placementSpec = spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold)),
                shape = RoundedCornerShape(20.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(app) }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    app.icon?.let { Image(it, null, Modifier.size(44.dp)) } ?: Spacer(Modifier.size(44.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            app.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(trailing, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
