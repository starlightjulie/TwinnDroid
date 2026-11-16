package com.twinndroid.sandbox

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.twinndroid.R
import com.twinndroid.ui.theme.ThemeRepository
import com.twinndroid.ui.theme.TwinnTheme
import kotlinx.coroutines.launch

/**
 * "Sandbox app" in the shortcut lists of launchers and shortcut-maker apps. Pick an app and you get a
 * shortcut that opens it in the sandbox. Opened straight from an activity launcher, it asks which app to open.
 *
 * Shortcuts carry a key only TwinnDroid knows, so other apps can't use this to wake up frozen apps.
 */
class SandboxAppPicker : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra(Sandbox.ARG_PACKAGE)
        if (pkg != null && intent.getStringExtra(SandboxShortcuts.EXTRA_KEY) == SandboxShortcuts.key(this)) {
            open(pkg)
            return
        }
        if (Sandbox.status(this) != SandboxStatus.READY) {
            Toast.makeText(this, R.string.sandbox_empty, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val creating = intent.action == Intent.ACTION_CREATE_SHORTCUT
        val theme = ThemeRepository.get(this)
        setContent {
            val config by theme.config.collectAsState()
            TwinnTheme(config) {
                var apps by remember { mutableStateOf<List<SandboxApp>?>(null) }
                LaunchedEffect(Unit) {
                    apps = runCatching { Sandbox.list(this@SandboxAppPicker, includeSystem = false) }.getOrDefault(emptyList())
                        .filter { it.launchable }
                        .sortedBy { it.label.lowercase() }
                }
                AlertDialog(
                    onDismissRequest = ::finish,
                    title = { Text(stringResource(R.string.sandbox_pick_title)) },
                    text = {
                        val list = apps
                        when {
                            list == null -> Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator() }
                            list.isEmpty() -> Text(stringResource(R.string.sandbox_empty))
                            else -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                                items(list, key = { it.packageName }) { app ->
                                    AppRow(app) {
                                        if (creating) {
                                            setResult(RESULT_OK, SandboxShortcuts.resultIntent(this@SandboxAppPicker, app))
                                            finish()
                                        } else {
                                            open(app.packageName)
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = { TextButton(onClick = ::finish) { Text(stringResource(R.string.cancel)) } },
                )
            }
        }
    }

    private fun open(packageName: String) {
        lifecycleScope.launch {
            runCatching { Sandbox.launch(this@SandboxAppPicker, packageName) }
                .onFailure { Toast.makeText(this@SandboxAppPicker, it.message, Toast.LENGTH_LONG).show() }
            finish()
        }
    }
}

@androidx.compose.runtime.Composable
private fun AppRow(app: SandboxApp, onClick: () -> Unit) {
    val icon = remember(app.packageName) { app.iconPng?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        if (icon != null) Image(icon, null, Modifier.size(36.dp)) else Spacer(Modifier.size(36.dp))
        Spacer(Modifier.width(14.dp))
        Text(app.label, style = MaterialTheme.typography.bodyLarge)
    }
}
