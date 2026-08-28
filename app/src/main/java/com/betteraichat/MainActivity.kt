package com.betteraichat

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.betteraichat.tools.ShizukuManager
import com.betteraichat.ui.navigation.AppNavHost
import com.betteraichat.ui.theme.BetterAIChatTheme
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private fun applyLanguage() {
        val lang = (application as BetterAIChatApp).container.settings.getLanguage()
        val locale = when (lang) {
            com.betteraichat.core.storage.AppLanguage.ZH -> java.util.Locale("zh")
            com.betteraichat.core.storage.AppLanguage.EN -> java.util.Locale("en")
            else -> null
        }
        if (locale != null) {
            val config = android.content.res.Configuration(resources.configuration)
            config.setLocale(locale)
            resources.updateConfiguration(config, resources.displayMetrics)
        }
    }

    private val shizukuListener =
        rikka.shizuku.Shizuku.OnRequestPermissionResultListener { requestCode, result ->
            if (requestCode == ShizukuManager.REQUEST_CODE) {
                (application as BetterAIChatApp).container.shizukuManager.onPermissionResult(
                    result == android.content.pm.PackageManager.PERMISSION_GRANTED
                )
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyLanguage()
        enableEdgeToEdge()
        Shizuku.addRequestPermissionResultListener(shizukuListener)
        handleShareIntent(intent)
        setContent {
            BetterAIChatTheme {
                AppNavHost()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        val container = (application as BetterAIChatApp).container
        if (intent.action == Intent.ACTION_VIEW && intent.data?.scheme == "betteraichat") {
            val text = intent.data?.getQueryParameter("text")?.trim()
            if (!text.isNullOrBlank()) {
                container.pendingShareText = text
                container.shareNavTick.value = container.shareNavTick.value + 1
            }
            intent.action = Intent.ACTION_MAIN
            return
        }
        if (intent.action != Intent.ACTION_SEND) return
        when (intent.type) {
            "text/plain" -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
                if (!text.isNullOrBlank()) container.pendingShareText = text
            }
            else -> {
                if (intent.type?.startsWith("image/") == true) {
                    val uri = if (android.os.Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_STREAM)
                    }
                    if (uri != null) container.pendingShareImage = uri
                }
            }
        }
        container.shareNavTick.value = container.shareNavTick.value + 1
        intent.action = Intent.ACTION_MAIN
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuListener)
        super.onDestroy()
    }
}
