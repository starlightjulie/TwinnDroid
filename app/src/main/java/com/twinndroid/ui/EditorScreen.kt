package com.twinndroid.ui

import com.twinndroid.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GppBad
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.twinndroid.data.IconMode
import com.twinndroid.data.SourceKind
import com.twinndroid.engine.TamperRisk

/** Permissions people most often strip from alt-account clones. */
private val SENSITIVE = setOf(
    "android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.ACCESS_BACKGROUND_LOCATION", "android.permission.READ_CONTACTS",
    "android.permission.CAMERA", "android.permission.RECORD_AUDIO", "android.permission.READ_PHONE_STATE",
    "android.permission.READ_CALL_LOG", "android.permission.READ_SMS", "android.permission.GET_ACCOUNTS",
    "android.permission.BLUETOOTH_SCAN", "android.permission.NEARBY_WIFI_DEVICES",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(vm: MainViewModel) {
    val state = vm.editor ?: return
    val c = state.config
    var permsExpanded by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
                title = { Text(stringResource(if (state.isNew) R.string.new_clone_title else R.string.customize_clone)) },
                navigationIcon = { IconButton(onClick = vm::closeEditor) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) } },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f)) {
                Button(
                    onClick = vm::saveAndBuild,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(52.dp),
                ) {
                    Icon(Icons.Rounded.Check, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (state.isNew) R.string.clone_install else R.string.save_rebuild))
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Preview
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Box(
                    Modifier.size(88.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    state.preview?.let { Image(it, null, Modifier.size(72.dp)) }
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(c.label.ifBlank { stringResource(R.string.unnamed) }, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(R.string.clone_of, c.sourceLabel + (state.sourceVersion?.let { " $it" } ?: "")),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(if (c.sourceKind == SourceKind.INSTALLED) R.string.from_installed else R.string.from_apk_file),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            if (state.tamper.risk != TamperRisk.NONE) {
                val shield = state.tamper.risk == TamperRisk.SHIELD
                TwinnCard(container = if (shield) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(Modifier.padding(16.dp)) {
                        Icon(
                            if (shield) Icons.Rounded.GppBad else Icons.Rounded.Info,
                            null,
                            tint = if (shield) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                stringResource(if (shield) R.string.tamper_shield_title else R.string.tamper_integrity_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (shield) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                if (shield) stringResource(R.string.tamper_shield, state.tamper.product.orEmpty()) else stringResource(R.string.tamper_integrity),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (shield) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Section(stringResource(R.string.identity)) {
                OutlinedTextField(
                    value = c.label,
                    onValueChange = { v -> vm.updateConfig { it.copy(label = v) } },
                    label = { Text(stringResource(R.string.app_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = c.clonePackage,
                    onValueChange = { v -> vm.updateConfig { it.copy(clonePackage = v.replace(" ", "")) } },
                    label = { Text(stringResource(R.string.package_name)) },
                    singleLine = true,
                    isError = state.packageError != null,
                    supportingText = {
                        Text(
                            state.packageError
                                ?: if (!state.isNew && c.builtVersionCode > 0) stringResource(R.string.package_change_warning)
                                else stringResource(R.string.package_hint),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Section(stringResource(R.string.icon)) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { vm.screen = Screen.IconEditor }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) { state.preview?.let { Image(it, null, Modifier.size(44.dp)) } }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when (c.iconMode) {
                                IconMode.ORIGINAL -> stringResource(R.string.icon_original)
                                IconMode.BADGE -> stringResource(R.string.badge)
                                IconMode.CUSTOM -> stringResource(R.string.icon_custom)
                            },
                        )
                        val badgeLine = stringResource(R.string.badge_quoted, c.badgeText)
                        val adjusted = stringResource(R.string.adjusted)
                        val iconHint = stringResource(R.string.icon_hint)
                        Text(
                            buildString {
                                if (c.iconMode == IconMode.BADGE) append(badgeLine)
                                if (!c.iconAdjust.isIdentity) { if (isNotEmpty()) append(" · "); append(adjusted) }
                                if (isEmpty()) append(iconHint)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
                }
            }

            Section(stringResource(R.string.permissions)) {
                val removed = c.removedPermissions.toSet()
                Row(
                    Modifier.fillMaxWidth().clickable { permsExpanded = !permsExpanded }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.permissions_kept, state.permissions.size - removed.size, state.permissions.size))
                        Text(
                            stringResource(R.string.permissions_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(if (permsExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                }
                if (permsExpanded) {
                    if (state.permissions.any { it in SENSITIVE }) {
                        TextButton(onClick = {
                            vm.updateConfig { cfg -> cfg.copy(removedPermissions = (cfg.removedPermissions + state.permissions.filter { it in SENSITIVE }).distinct()) }
                        }) { Text(stringResource(R.string.remove_sensitive)) }
                    }
                    HorizontalDivider()
                    state.permissions.forEach { perm ->
                        val kept = perm !in removed
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                vm.updateConfig { cfg ->
                                    cfg.copy(removedPermissions = if (kept) cfg.removedPermissions + perm else cfg.removedPermissions - perm)
                                }
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = kept, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    perm.substringAfterLast('.'),
                                    fontWeight = if (perm in SENSITIVE) FontWeight.SemiBold else FontWeight.Normal,
                                )
                                Text(perm, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            Section(stringResource(R.string.advanced)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.debuggable))
                        Text(
                            stringResource(R.string.debuggable_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = c.debuggable, onCheckedChange = { v -> vm.updateConfig { it.copy(debuggable = v) } })
                }
            }

            TwinnCard(container = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.padding(16.dp)) {
                    Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.resign_note),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    TwinnCard {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
