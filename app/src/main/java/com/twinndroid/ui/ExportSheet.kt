package com.twinndroid.ui

import com.twinndroid.R
import androidx.compose.ui.res.stringResource
import android.content.Intent
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.twinndroid.engine.ApkExporter
import com.twinndroid.engine.ExportFormat

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExportSheet(vm: MainViewModel, state: ExportState) {
    val context = LocalContext.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val saveApk = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")) { uri ->
        if (uri != null) vm.runExport(uri)
    }
    val saveBundle = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) vm.runExport(uri)
    }
    val src = state.source
    val total = src.files.sumOf { it.length() }
    val hasSplits = src.files.size > 1

    ModalBottomSheet(onDismissRequest = { if (!state.running) vm.closeExport() }, sheetState = sheet) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                state.iconBitmap?.let { Image(it, null, Modifier.size(56.dp)) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(src.label, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${src.versionName ?: src.versionCode} · ${Formatter.formatShortFileSize(context, total)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.splits.forEach { (name, size) ->
                    Text(
                        "$name · ${Formatter.formatShortFileSize(context, size)}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.format), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 3) {
                ExportFormat.entries.forEach { f ->
                    val enabled = !state.running && (f != ExportFormat.APK || !hasSplits)
                    val selected = state.format == f
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .border(
                                BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                RoundedCornerShape(16.dp),
                            )
                            .clickable(enabled = enabled) { vm.setExportFormat(f) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val alpha = if (enabled || selected) 1f else 0.4f
                        Text(stringResource(f.label), fontWeight = FontWeight.SemiBold, maxLines = 1, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
                        Text(".${f.ext}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            AnimatedContent(state.format, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "formatDesc") { f ->
                Text(
                    stringResource(if (f == ExportFormat.APK && hasSplits) R.string.fmt_apk_split_warning else f.description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(20.dp))
            val phase = when {
                state.running -> 1
                state.done != null -> 2
                state.error != null -> 3
                else -> 0
            }
            AnimatedContent(
                phase,
                transitionSpec = { (fadeIn(tween(220)) + scaleIn(initialScale = 0.92f)) togetherWith fadeOut(tween(150)) },
                label = "exportPhase",
            ) { p ->
                when (p) {
                    1 -> Column {
                        val shown by animateFloatAsState(state.progress, spring(stiffness = Spring.StiffnessLow), label = "exportProgress")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(if (state.format == ExportFormat.MERGED && state.progress < 0.9f) R.string.merging else R.string.saving),
                                modifier = Modifier.weight(1f),
                            )
                            Text("${(shown * 100).toInt()}%", fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { shown }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape))
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = vm::closeExport) { Text(stringResource(R.string.cancel)) }
                    }
                    2 -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Box(
                            Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onSecondary, modifier = Modifier.size(36.dp)) }
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(R.string.saved_file, ApkExporter.fileName(src, state.format)), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                val send = Intent(Intent.ACTION_SEND)
                                    .setType("application/octet-stream")
                                    .putExtra(Intent.EXTRA_STREAM, state.done)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                context.startActivity(Intent.createChooser(send, context.getString(R.string.share_app, src.label)))
                            }) { Icon(Icons.Rounded.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.share)) }
                            Button(onClick = vm::closeExport) { Text(stringResource(R.string.done)) }
                        }
                    }
                    3 -> Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text(state.error ?: stringResource(R.string.export_failed_short), color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(Modifier.height(12.dp))
                        ExportButton(state, saveApk::launch, saveBundle::launch)
                    }
                    else -> ExportButton(state, saveApk::launch, saveBundle::launch)
                }
            }
        }
    }
}

@Composable
private fun ExportButton(state: ExportState, saveApk: (String) -> Unit, saveBundle: (String) -> Unit) {
    val name = ApkExporter.fileName(state.source, state.format)
    val blocked = state.format == ExportFormat.APK && state.source.files.size > 1
    Button(
        onClick = { if (state.format.ext == "apk") saveApk(name) else saveBundle(name) },
        enabled = !blocked,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text(stringResource(R.string.export_as, stringResource(state.format.label))) }
    if (state.format == ExportFormat.MERGED) {
        Text(
            stringResource(R.string.merging_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
