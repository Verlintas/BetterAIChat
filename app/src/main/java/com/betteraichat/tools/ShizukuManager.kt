package com.betteraichat.tools

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import rikka.shizuku.Shizuku

class ShizukuManager {

    private val _granted = MutableStateFlow(checkGranted())
    val granted: StateFlow<Boolean> = _granted

    fun refresh() {
        _granted.value = checkGranted()
    }

    fun requestPermission() {
        if (checkGranted()) return
        try {
            if (Shizuku.pingBinder()) {
                Shizuku.requestPermission(REQUEST_CODE)
            }
        } catch (e: Exception) {
            _granted.value = false
        }
    }

    fun onPermissionResult(result: Boolean) {
        _granted.value = result
    }

    private fun checkGranted(): Boolean = try {
        if (!Shizuku.pingBinder()) {
            false
        } else {
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    } catch (e: Exception) {
        false
    }

    companion object {
        const val REQUEST_CODE = 10001
    }
}
