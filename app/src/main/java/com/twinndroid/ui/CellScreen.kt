package com.twinndroid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.twinndroid.R
import com.twinndroid.data.CellOptions
import com.twinndroid.engine.TamperRisk

/** Sub-screen of the clone editor: where this clone lives, and the walls around it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CellScreen(vm: MainViewModel) {
    val state = vm.editor ?: return
    val c = state.config
    val sandboxReady = remember { vm.sandboxReady() }
    val built = !state.isNew && c.builtVersionCode > 0

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
                title = { Text(stringResource(R.string.cell_section)) },
                navigationIcon = {
                    IconButton(onClick = { vm.screen = Screen.Editor }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.tamper.risk == TamperRisk.SHIELD) {
                TwinnCard(container = MaterialTheme.colorScheme.errorContainer) {
                    Text(
                        stringResource(R.string.cell_tamper_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            TwinnCard {
                Column(Modifier.padding(16.dp)) {
                    Option(
                        stringResource(R.string.cell_in_sandbox),
                        stringResource(if (sandboxReady || c.inSandbox) R.string.cell_in_sandbox_desc else R.string.cell_sandbox_missing),
                        c.inSandbox,
                        enabled = sandboxReady || c.inSandbox,
                    ) { on -> vm.updateConfig { it.copy(inSandbox = on) } }
                }
            }

            TwinnCard {
                Column(Modifier.padding(16.dp)) {
                    Option(stringResource(R.string.cell_harden), stringResource(R.string.cell_harden_desc), c.cell != null) { on ->
                        vm.updateConfig { it.copy(cell = if (on) CellOptions() else null) }
                    }
                }
            }

            c.cell?.let { cell ->
                fun set(next: CellOptions) = vm.updateConfig { it.copy(cell = next) }
                TwinnCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Option(stringResource(R.string.cell_own_key), stringResource(R.string.cell_own_key_desc), cell.ownKey) { set(cell.copy(ownKey = it)) }
                        Option(stringResource(R.string.cell_hide_apps), stringResource(R.string.cell_hide_apps_desc), cell.hideApps) { set(cell.copy(hideApps = it)) }
                        Option(stringResource(R.string.cell_private), stringResource(R.string.cell_private_desc), cell.privateComponents) { set(cell.copy(privateComponents = it)) }
                        Option(stringResource(R.string.cell_no_storage), stringResource(R.string.cell_no_storage_desc), cell.noStorage) { set(cell.copy(noStorage = it)) }
                        Option(stringResource(R.string.cell_no_backup), stringResource(R.string.cell_no_backup_desc), cell.noBackup) { set(cell.copy(noBackup = it)) }
                        Option(stringResource(R.string.cell_no_ad_id), stringResource(R.string.cell_no_ad_id_desc), cell.noAdId) { set(cell.copy(noAdId = it)) }
                        Option(stringResource(R.string.cell_no_boot), stringResource(R.string.cell_no_boot_desc), cell.noBoot) { set(cell.copy(noBoot = it)) }
                        Option(stringResource(R.string.cell_no_internet), stringResource(R.string.cell_no_internet_desc), cell.noInternet) { set(cell.copy(noInternet = it)) }
                    }
                }
            }

            if (built) {
                Text(
                    stringResource(R.string.cell_reinstall_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun Option(title: String, desc: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
