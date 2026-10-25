package com.twinndroid

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.twinndroid.ui.AppIcons
import com.twinndroid.ui.MainViewModel
import com.twinndroid.ui.TwinnRoot
import com.twinndroid.ui.theme.ThemeRepository
import com.twinndroid.ui.theme.TwinnTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

class MainActivity : AppCompatActivity() {
    private val vm: MainViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && !vm.settings.askedNotifications) {
            vm.settings.askedNotifications = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val theme = ThemeRepository.get(this)
        setContent {
            val config by theme.config.collectAsState()
            TwinnTheme(config) { TwinnRoot(vm) }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) AppIcons.applyPending(this)
    }

    override fun onResume() {
        super.onResume()
        vm.refreshClones()
    }
}
