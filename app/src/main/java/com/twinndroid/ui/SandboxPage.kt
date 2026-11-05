package com.twinndroid.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddHome
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.twinndroid.R
import com.twinndroid.sandbox.Sandbox
import com.twinndroid.sandbox.SandboxStatus

/** Sandbox tab: set up the work profile, then manage everything inside it. */
@Composable
fun SandboxPage(main: MainViewModel, vm: SandboxViewModel, active: Boolean) {
    LifecycleResumeEffect(Unit) {
        vm.onScreenChanged(true)
        vm.refreshStatus()
        onPauseOrDispose { vm.onScreenChanged(false) }
    }
    // The pager keeps this page alive next door, and each bridge call briefly takes focus.
    LaunchedEffect(active, vm.status) {
        if (active && vm.status == SandboxStatus.READY && vm.apps == null) vm.reload()
    }
    val setup = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) vm.onSetupFinished() else vm.refreshStatus()
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.addFile(uri)
    }
    val pickToSend = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.sendFiles(uris)
    }
    var picking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "title") {
            Column(Modifier.statusBarsPadding().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp)) {
                Text(stringResource(R.string.sandbox_title), style = MaterialTheme.typography.headlineMedium)
                Text(
                    stringResource(R.string.sandbox_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when {
            vm.finishing -> item(key = "finishing") { Centered { CircularProgressIndicator() } }
            vm.status == SandboxStatus.READY -> {
                item(key = "actions") {
                    ButtonRow(Modifier.padding(horizontal = 16.dp)) {
                        val grow = Modifier.weight(1f)
                        FilledTonalButton(onClick = { picking = true }, modifier = grow, contentPadding = TightButtonPadding) {
                            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); OneLine(stringResource(R.string.sandbox_add_app))
                        }
                        OutlinedButton(onClick = { pickFile.launch(arrayOf("*/*")) }, modifier = grow, contentPadding = TightButtonPadding) {
                            Icon(Icons.Rounded.FolderOpen, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); OneLine(stringResource(R.string.sandbox_add_apk))
                        }
                    }
                }
                if (Sandbox.supportsFileTransfer) item(key = "files") {
                    ButtonRow(Modifier.padding(horizontal = 16.dp)) {
                        val grow = Modifier.weight(1f)
                        OutlinedButton(onClick = { pickToSend.launch(arrayOf("*/*")) }, modifier = grow, contentPadding = TightButtonPadding) {
                            Icon(Icons.Rounded.Upload, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); OneLine(stringResource(R.string.sandbox_send_files))
                        }
                        OutlinedButton(onClick = vm::bringFilesOut, modifier = grow, contentPadding = TightButtonPadding) {
                            Icon(Icons.Rounded.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); OneLine(stringResource(R.string.sandbox_bring_files))
                        }
                    }
                }
                item(key = "filters") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(selected = vm.showSystem, onClick = vm::toggleSystem, label = { Text(stringResource(R.string.show_system_apps)) })
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { vm.addFreezeAllShortcut() }) { Icon(Icons.Rounded.AddHome, stringResource(R.string.sandbox_freeze_shortcut)) }
                        IconButton(onClick = { vm.freezeAll() }) { Icon(Icons.Rounded.AcUnit, stringResource(R.string.sandbox_freeze_all)) }
                        IconButton(onClick = { vm.reload() }) { Icon(Icons.Rounded.Refresh, stringResource(R.string.sandbox_refresh)) }
                    }
                }
                val apps = vm.apps
                when {
                    apps == null -> item(key = "loading") { Centered { CircularProgressIndicator() } }
                    apps.isEmpty() -> item(key = "empty") { Hinted(stringResource(R.string.sandbox_empty)) }
                    else -> items(apps, key = { it.app.packageName }) { SandboxAppCard(vm, it) }
                }
                item(key = "rules") { RulesCard(vm, onDelete = { confirmDelete = true }) }
            }
            vm.status == SandboxStatus.AVAILABLE -> item(key = "setup") {
                InfoCard(stringResource(R.string.sandbox_setup_title), stringResource(R.string.sandbox_setup_desc)) {
                    Button(onClick = { setup.launch(Sandbox.setupIntent(main.getApplication())) }) {
                        Text(stringResource(R.string.sandbox_setup_button))
                    }
                }
            }
            vm.status == SandboxStatus.OTHER_OWNER -> item(key = "taken") {
                InfoCard(stringResource(R.string.sandbox_taken_title), stringResource(R.string.sandbox_taken_desc)) {}
            }
            vm.status == SandboxStatus.PAUSED -> item(key = "paused") {
                InfoCard(stringResource(R.string.sandbox_paused_title), stringResource(R.string.sandbox_paused_desc)) {
                    OutlinedButton(onClick = vm::refreshStatus) { Text(stringResource(R.string.sandbox_refresh)) }
                }
            }
            else -> item(key = "unsupported") {
                InfoCard(stringResource(R.string.sandbox_unsupported_title), stringResource(R.string.sandbox_unsupported_desc)) {}
            }
        }
    }

    if (vm.busy) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
    if (picking) {
        AddAppSheet(main, onDismiss = { picking = false }) { picking = false; vm.addOriginal(it.packageName) }
    }
    vm.options?.let { (entry, options) -> SandboxOptionsSheet(vm, entry, options) }
    vm.message?.let { text ->
        AlertDialog(
            onDismissRequest = { vm.message = null },
            confirmButton = { TextButton(onClick = { vm.message = null }) { Text(stringResource(R.string.ok)) } },
            text = { Text(text) },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.sandbox_delete_title)) },
            text = { Text(stringResource(R.string.sandbox_delete_desc)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteSandbox() }) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun Hinted(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
    )
}

@Composable
private fun InfoCard(title: String, body: String, actions: @Composable () -> Unit) {
    TwinnCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(Icons.Rounded.GppGood)
                Spacer(Modifier.width(14.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            actions()
        }
    }
}

@Composable
private fun SandboxAppCard(vm: SandboxViewModel, entry: SandboxEntry) {
    val app = entry.app
    var menu by remember { mutableStateOf(false) }
    TwinnCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                entry.icon?.let { Image(it, null, Modifier.size(44.dp)) } ?: Spacer(Modifier.size(44.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (app.frozen) {
                        Text(stringResource(R.string.sandbox_frozen), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more)) }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sandbox_app_options)) },
                            leadingIcon = { Icon(Icons.Rounded.Tune, null) },
                            onClick = { menu = false; vm.openOptions(entry) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sandbox_shortcut)) },
                            leadingIcon = { Icon(Icons.Rounded.AddHome, null) },
                            onClick = { menu = false; vm.addShortcut(entry) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sandbox_reset)) },
                            leadingIcon = { Icon(Icons.Rounded.RestartAlt, null) },
                            onClick = { menu = false; vm.clearData(entry) },
                        )
                        if (!app.isSystem) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sandbox_copy_out)) },
                                leadingIcon = { Icon(Icons.Rounded.Unarchive, null) },
                                onClick = { menu = false; vm.copyOut(entry) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sandbox_uninstall)) },
                                leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                                onClick = { menu = false; vm.uninstall(entry) },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            ButtonRow {
                val grow = Modifier.weight(1f)
                if (app.launchable) {
                    FilledTonalButton(onClick = { vm.open(entry) }, modifier = grow, contentPadding = TightButtonPadding) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); OneLine(stringResource(R.string.open))
                    }
                }
                OutlinedButton(onClick = { vm.setFrozen(entry, !app.frozen) }, modifier = grow, contentPadding = TightButtonPadding) {
                    Icon(if (app.frozen) Icons.Rounded.WbSunny else Icons.Rounded.AcUnit, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    OneLine(stringResource(if (app.frozen) R.string.sandbox_unfreeze else R.string.sandbox_freeze))
                }
            }
        }
    }
}

@Composable
private fun RulesCard(vm: SandboxViewModel, onDelete: () -> Unit) {
    val rules = vm.rules ?: return
    TwinnCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.sandbox_rules), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            RuleRow(R.string.sandbox_rule_clipboard, R.string.sandbox_rule_clipboard_desc, rules.blockClipboard) { vm.setRules(rules.copy(blockClipboard = it)) }
            RuleRow(R.string.sandbox_rule_share, R.string.sandbox_rule_share_desc, rules.blockShareIn) { vm.setRules(rules.copy(blockShareIn = it)) }
            RuleRow(R.string.sandbox_rule_contacts, R.string.sandbox_rule_contacts_desc, rules.blockContacts) { vm.setRules(rules.copy(blockContacts = it)) }
            RuleRow(R.string.sandbox_rule_debug, R.string.sandbox_rule_debug_desc, rules.allowDebugging) { vm.setRules(rules.copy(allowDebugging = it)) }
            RuleRow(R.string.sandbox_skip_playing, R.string.sandbox_skip_playing_desc, rules.skipWhilePlaying) { vm.setRules(rules.copy(skipWhilePlaying = it)) }
            Text(
                stringResource(R.string.sandbox_freeze_delay, rules.freezeDelaySeconds),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 14.dp),
            )
            var delay by remember(rules.freezeDelaySeconds) { mutableFloatStateOf(rules.freezeDelaySeconds.toFloat()) }
            Slider(
                value = delay,
                onValueChange = { delay = it },
                onValueChangeFinished = { vm.setRules(rules.copy(freezeDelaySeconds = delay.toInt())) },
                valueRange = 0f..300f,
                steps = 9,
            )
            RuleRow(R.string.sandbox_payment, R.string.sandbox_payment_desc, vm.paymentStub, vm::togglePaymentStub)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onDelete) { Text(stringResource(R.string.sandbox_delete)) }
        }
    }
}

@Composable
private fun RuleRow(title: Int, desc: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title))
            Text(stringResource(desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SandboxOptionsSheet(vm: SandboxViewModel, entry: SandboxEntry, options: com.twinndroid.sandbox.SandboxAppOptions) {
    ModalBottomSheet(onDismissRequest = vm::closeOptions) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
            Text(entry.app.label, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.sandbox_block_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for ((group, blocked) in options.permissions) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp).toggleable(blocked, role = Role.Switch) { vm.setPermissionBlocked(group, it) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(permissionGroupLabel(group)), Modifier.weight(1f))
                    Switch(checked = blocked, onCheckedChange = null)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            RuleRow(R.string.sandbox_auto_freeze, R.string.sandbox_auto_freeze_desc, options.autoFreeze, vm::setAutoFreeze)
            RuleRow(R.string.sandbox_widgets, R.string.sandbox_widgets_desc, options.widgets, vm::setWidgets)
            RuleRow(R.string.sandbox_interaction, R.string.sandbox_interaction_desc, options.interaction, vm::setInteraction)
        }
    }
}

private fun permissionGroupLabel(group: String): Int = when (group) {
    "camera" -> R.string.perm_camera
    "microphone" -> R.string.perm_microphone
    "location" -> R.string.perm_location
    "contacts" -> R.string.perm_contacts
    "calendar" -> R.string.perm_calendar
    "phone" -> R.string.perm_phone
    "sms" -> R.string.perm_sms
    "files" -> R.string.perm_files
    "nearby" -> R.string.perm_nearby
    else -> R.string.perm_sensors
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAppSheet(main: MainViewModel, onDismiss: () -> Unit, onPick: (AppEntry) -> Unit) {
    LaunchedEffect(Unit) { main.loadApps() }
    var showSystem by remember { mutableStateOf(false) }
    val apps = main.apps
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.sandbox_add_app), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 8.dp))
            Text(
                stringResource(R.string.sandbox_add_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
            FilterChip(
                selected = showSystem,
                onClick = { showSystem = !showSystem },
                label = { Text(stringResource(R.string.show_system_apps)) },
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        if (apps == null) {
            Centered { CircularProgressIndicator() }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(apps.filter { showSystem || !it.isSystem }, key = { it.packageName }) { app ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(app) }.padding(horizontal = 24.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        app.icon?.let { Image(it, null, Modifier.size(40.dp)) } ?: Icon(Icons.Rounded.Apps, null, Modifier.size(40.dp))
                        Spacer(Modifier.width(14.dp))
                        Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
