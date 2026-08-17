package com.betteraichat.tools

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import rikka.shizuku.Shizuku

class ShizukuManager {

    private val _granted = MutableStateFlow(Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED)
    val granted: StateFlow<Boolean> = _granted

    fun refresh() {
        _granted.value = Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun requestPermission() {
        refresh()
        if (_granted.value) return
        try {
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (e: IllegalStateException) {
            _granted.value = false
        }
    }

    fun onPermissionResult(result: Boolean) {
        _granted.value = result
    }

    companion object {
        const val REQUEST_CODE = 10001
    }
}
