package com.betteraichat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.betteraichat.tools.ShizukuManager
import com.betteraichat.ui.navigation.AppNavHost
import com.betteraichat.ui.theme.BetterAIChatTheme
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

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
        enableEdgeToEdge()
        Shizuku.addRequestPermissionResultListener(shizukuListener)
        setContent {
            BetterAIChatTheme {
                AppNavHost()
            }
        }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuListener)
        super.onDestroy()
    }
}
