package com.betteraichat.core.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeyStoreCrypto(context: Context) {

    private val prefs = context.getSharedPreferences("keys", Context.MODE_PRIVATE)
    private val alias = "betteraichat_master_key"

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val out = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, out, 0, iv.size)
        System.arraycopy(encrypted, 0, out, iv.size, encrypted.size)
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    fun decrypt(data: String): String {
        return try {
            val raw = Base64.decode(data, Base64.NO_WRAP)
            val iv = raw.copyOfRange(0, 12)
            val encrypted = raw.copyOfRange(12, raw.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    fun put(name: String, value: String) {
        runCatching {
            prefs.edit().putString(name, encrypt(value)).apply()
        }
    }

    fun remove(name: String) {
        prefs.edit().remove(name).apply()
    }

    fun get(name: String): String {
        val v = prefs.getString(name, null) ?: return ""
        return try {
            decrypt(v)
        } catch (e: Exception) {
            prefs.edit().remove(name).apply()
            ""
        }
    }
}
