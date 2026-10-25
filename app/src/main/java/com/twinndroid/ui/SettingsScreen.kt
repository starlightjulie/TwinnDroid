package com.twinndroid.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.twinndroid.BuildConfig
import com.twinndroid.R
import com.twinndroid.data.Languages
import com.twinndroid.data.OfficialBuild
import com.twinndroid.data.BackupManager
import com.twinndroid.engine.SigningKeys
import com.twinndroid.install.ShizukuInstaller
import rikka.shizuku.Shizuku

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var useShizuku by remember { mutableStateOf(vm.settings.useShizuku) }
    var autoUpdate by remember { mutableStateOf(vm.settings.autoUpdate) }
    var checkUpdates by remember { mutableStateOf(vm.settings.checkUpdates) }
    val official = remember { OfficialBuild.isOfficial(context) }
    var fingerprint by remember { mutableStateOf(runCatching { SigningKeys.fingerprint(context) }.getOrDefault("?")) }

    DisposableEffect(Unit) {
        val permListener = Shizuku.OnRequestPermissionResultListener { _, _ -> tick++ }
        val binderListener = Shizuku.OnBinderReceivedListener { tick++ }
        val deadListener = Shizuku.OnBinderDeadListener { tick++ }
        Shizuku.addRequestPermissionResultListener(permListener)
        Shizuku.addBinderReceivedListenerSticky(binderListener)
        Shizuku.addBinderDeadListener(deadListener)
        onDispose {
            Shizuku.removeRequestPermissionResultListener(permListener)
            Shizuku.removeBinderReceivedListener(binderListener)
            Shizuku.removeBinderDeadListener(deadListener)
        }
    }

    val exportKey = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-pkcs12")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)!!.use { SigningKeys.export(context, it) }
        }.onFailure { vm.message = context.getString(R.string.export_failed, it.message) }
    }
    val importKey = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openInputStream(uri)!!.use { SigningKeys.import(context, it) }
            fingerprint = SigningKeys.fingerprint(context)
        }.onFailure { vm.message = context.getString(R.string.key_invalid, it.message) }
    }

    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)!!.use { BackupManager.export(context, it) }
        }.onSuccess { vm.message = context.getString(R.string.backup_saved) }.onFailure { vm.message = context.getString(R.string.backup_failed, it.message) }
    }
    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openInputStream(uri)!!.use { BackupManager.restore(context, it) }
            fingerprint = SigningKeys.fingerprint(context)
            useShizuku = vm.settings.useShizuku
            autoUpdate = vm.settings.autoUpdate
        }.onSuccess { vm.message = context.getString(R.string.backup_restored) }.onFailure { vm.message = context.getString(R.string.restore_failed, it.message) }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = { IconButton(onClick = { vm.screen = Screen.Home }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val running = remember(tick) { ShizukuInstaller.isRunning() }
            val granted = remember(tick) { ShizukuInstaller.hasPermission() }

            SettingsCard(stringResource(R.string.language)) {
                Text(stringResource(R.string.language_desc), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { vm.screen = Screen.Language }) {
                    Icon(Icons.Rounded.Translate, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(Languages.currentName() ?: stringResource(R.string.language_system))
                }
            }

            SettingsCard(stringResource(R.string.appearance)) {
                Text(stringResource(R.string.appearance_desc), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { vm.screen = Screen.Appearance }) { Text(stringResource(R.string.appearance_open)) }
            }

            SettingsCard(stringResource(R.string.shizuku_title)) {
                Text(
                    when {
                        granted -> stringResource(R.string.shizuku_ready)
                        running -> stringResource(R.string.shizuku_unauthorized)
                        else -> stringResource(R.string.shizuku_not_running)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                when {
                    running && !granted -> Button(onClick = ShizukuInstaller::requestPermission) { Text(stringResource(R.string.shizuku_authorize)) }
                    !running -> OutlinedButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
                    }) { Text(stringResource(R.string.shizuku_get)) }
                }
                ToggleRow(stringResource(R.string.shizuku_use), stringResource(R.string.shizuku_use_desc), useShizuku) {
                    useShizuku = it; vm.settings.useShizuku = it
                }
            }

            SettingsCard(stringResource(R.string.updates)) {
                Text(
                    stringResource(R.string.updates_desc),
                    style = MaterialTheme.typography.bodyMedium,
                )
                ToggleRow(stringResource(R.string.auto_update), stringResource(R.string.auto_update_desc), autoUpdate, enabled = granted || autoUpdate) {
                    autoUpdate = it; vm.settings.autoUpdate = it
                }
                ToggleRow(stringResource(R.string.check_app_updates), stringResource(R.string.check_app_updates_desc), checkUpdates) {
                    checkUpdates = it; vm.settings.checkUpdates = it
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { vm.checkForAppUpdate(manual = true) }) { Text(stringResource(R.string.check_now)) }
            }

            SettingsCard(stringResource(R.string.signing_key)) {
                Text(
                    stringResource(R.string.signing_key_desc),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text("SHA-256", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(fingerprint, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { exportKey.launch("twinndroid-key.p12") }) { Text(stringResource(R.string.export)) }
                    OutlinedButton(onClick = { importKey.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.import_)) }
                }
            }

            SettingsCard(stringResource(R.string.backup_title)) {
                Text(
                    stringResource(R.string.backup_desc),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { exportBackup.launch("TwinnDroid-backup.zip") }) { Text(stringResource(R.string.back_up)) }
                    OutlinedButton(onClick = { importBackup.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }) { Text(stringResource(R.string.restore)) }
                }
            }

            SettingsCard(stringResource(R.string.about)) {
                Text("TwinnDroid ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.about_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (official) Icons.Rounded.Verified else Icons.Rounded.WarningAmber, null,
                        Modifier.size(18.dp),
                        tint = if (official) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(if (official) R.string.official_build else R.string.unofficial_build),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (official) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }
                if (!official) {
                    Text(
                        stringResource(R.string.unofficial_build_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { context.openUrl(CREATOR_GITHUB) }) {
                        Icon(painterResource(R.drawable.ic_github), null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("StarlightJulie")
                    }
                    OutlinedButton(onClick = { context.openUrl(CREATOR_KOFI) }) {
                        Icon(Icons.Rounded.LocalCafe, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Ko-fi")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.license_notice),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { context.openUrl(LICENSE_URL) },
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    TwinnCard {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
