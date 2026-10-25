package com.twinndroid.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.twinndroid.BuildConfig
import com.twinndroid.R
import com.twinndroid.data.AppUpdate
import com.twinndroid.data.Languages
import com.twinndroid.data.UpdateChecker

const val CREATOR_GITHUB = "https://github.com/starlightjulie"
const val CREATOR_KOFI = "https://ko-fi.com/vvanadium"
const val LICENSE_URL = "https://github.com/starlightjulie/TwinnDroid/blob/main/ADDITIONAL-TERMS.md"

fun Context.openUrl(url: String) {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Small "Made by StarlightJulie · Ko-fi" line that sits above the tab bar. */
@Composable
fun CreatorCredit(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dim = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.credit_made_by), style = MaterialTheme.typography.labelSmall, color = dim)
        CreditLink(
            icon = { Icon(painterResource(R.drawable.ic_github), null, Modifier.size(13.dp), tint = dim) },
            label = "StarlightJulie",
        ) { context.openUrl(CREATOR_GITHUB) }
        Text("·", style = MaterialTheme.typography.labelSmall, color = dim)
        CreditLink(
            icon = { Icon(Icons.Rounded.LocalCafe, null, Modifier.size(14.dp), tint = dim) },
            label = "Ko-fi",
        ) { context.openUrl(CREATOR_KOFI) }
    }
}

@Composable
private fun CreditLink(icon: @Composable () -> Unit, label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
    }
}

/** Startup pop-up when GitHub has a newer release. */
@Composable
fun UpdateDialog(update: AppUpdate, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.SystemUpdate, null) },
        title = { Text(stringResource(R.string.update_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.update_versions, update.version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.padding(top = 8.dp))
                HorizontalDivider()
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
                    Text(update.title, style = MaterialTheme.typography.titleSmall)
                    if (update.notes.isNotBlank()) {
                        Spacer(Modifier.padding(top = 4.dp))
                        Text(update.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { context.openUrl(UpdateChecker.LATEST_RELEASE_URL); onDismiss() }) {
                Text(stringResource(R.string.update_now))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_dismiss)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageScreen(vm: MainViewModel) {
    val current = Languages.current()
    val selectedTag = Languages.currentLanguage()?.tag
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
                title = { Text(stringResource(R.string.language)) },
                navigationIcon = {
                    IconButton(onClick = { vm.screen = Screen.Settings }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            TwinnCard {
                Column(Modifier.padding(vertical = 8.dp)) {
                    LanguageRow(stringResource(R.string.language_system), null, current.isEmpty()) { Languages.apply("") }
                    Languages.all.forEach { lang ->
                        LanguageRow(lang.nativeName, lang.tag, lang.tag == selectedTag) { Languages.apply(lang.tag) }
                    }
                }
            }
            Text(
                stringResource(R.string.language_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@Composable
private fun LanguageRow(name: String, tag: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            if (tag != null) Text(tag, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
    }
}
