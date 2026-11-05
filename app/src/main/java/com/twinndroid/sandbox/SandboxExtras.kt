package com.twinndroid.sandbox

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.nfc.cardemulation.HostApduService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import com.twinndroid.R
import kotlinx.coroutines.launch

/**
 * Runs inside the sandbox while auto-freeze apps are awake, and freezes them once the screen locks.
 * A screen-off broadcast only reaches a running app, so this has to stay alive until then.
 */
class SandboxFreezeService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val freeze = Runnable { freezeOrWait() }
    private val screen = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handler.removeCallbacks(freeze)
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                handler.postDelayed(freeze, SandboxPolicy.rules(context).freezeDelaySeconds * 1000L)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.sandbox_auto_freeze), NotificationManager.IMPORTANCE_MIN))
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.sandbox_auto_freeze_running))
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, 1, notification, type)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(this, screen, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun freezeOrWait() {
        val playing = getSystemService(AudioManager::class.java).isMusicActive
        if (playing && SandboxPolicy.rules(this).skipWhilePlaying) {
            handler.postDelayed(freeze, 60_000)
            return
        }
        SandboxPolicy.autoFreezeApps(this).forEach { SandboxPolicy.setFrozen(this, it, true) }
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(freeze)
        unregisterReceiver(screen)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "auto_freeze"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, SandboxFreezeService::class.java))
        }
    }
}

/** Home-screen shortcuts that work on frozen apps, because they go through the bridge. */
object SandboxShortcuts {
    fun pinLaunch(context: Context, app: SandboxApp): Boolean {
        val icon = app.iconPng?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        val info = ShortcutInfoCompat.Builder(context, "sandbox-${app.packageName}")
            .setShortLabel(app.label)
            .setIcon(icon?.let(IconCompat::createWithBitmap) ?: IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(shortcutIntent(context).putExtra(Sandbox.ARG_PACKAGE, app.packageName))
            .build()
        return ShortcutManagerCompat.requestPinShortcut(context, info, null)
    }

    fun pinFreezeAll(context: Context): Boolean {
        val info = ShortcutInfoCompat.Builder(context, "sandbox-freeze-all")
            .setShortLabel(context.getString(R.string.sandbox_freeze_all))
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(shortcutIntent(context))
            .build()
        return ShortcutManagerCompat.requestPinShortcut(context, info, null)
    }

    private fun shortcutIntent(context: Context) =
        Intent(context, SandboxShortcutActivity::class.java).setAction(Intent.ACTION_VIEW)
}

/** Invisible target of those shortcuts: unfreezes and opens one app, or freezes everything. */
class SandboxShortcutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra(Sandbox.ARG_PACKAGE)
        lifecycleScope.launch {
            runCatching {
                if (pkg != null) Sandbox.launch(this@SandboxShortcutActivity, pkg) else Sandbox.freezeAll(this@SandboxShortcutActivity)
            }.onFailure { Toast.makeText(this@SandboxShortcutActivity, it.message, Toast.LENGTH_LONG).show() }
            finish()
        }
    }
}

/**
 * Does nothing on its own. Android only shows the contactless payments setting when the main profile
 * has a payment service, so this makes that setting appear for payment apps inside the sandbox.
 */
class SandboxPaymentStub : HostApduService() {
    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray = byteArrayOf(0x6A, 0x82.toByte())
    override fun onDeactivated(reason: Int) = Unit

    companion object {
        private fun component(context: Context) = ComponentName(context, SandboxPaymentStub::class.java)

        fun isEnabled(context: Context): Boolean =
            context.packageManager.getComponentEnabledSetting(component(context)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED

        fun setEnabled(context: Context, on: Boolean) {
            context.packageManager.setComponentEnabledSetting(
                component(context),
                if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
