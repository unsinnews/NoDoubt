package com.nodoubt.core.config

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Keys never leave Android Keystore. Only authenticated ciphertext reaches SharedPreferences. */
internal class SecretVault(context: Context) {
    private val preferences = context.getSharedPreferences("nodoubt_secret_vault", Context.MODE_PRIVATE)
    private val alias = "nodoubt.configuration.v1"

    fun get(profile: String, reference: String): String {
        val storageKey = "$profile/$reference"
        val encoded = preferences.getString(storageKey, null) ?: return ""
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        require(bytes.size > 28) { "加密凭据损坏，请重新填写" }
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            updateAAD(storageKey.toByteArray())
            doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
        }
    }

    fun put(profile: String, reference: String, value: String) {
        transaction(profile, mapOf(reference to value)) { }
    }

    fun <T> transaction(profile: String, values: Map<String, String>, action: () -> T): T {
        val encoded = values.map { (reference, value) ->
            val storageKey = "$profile/$reference"
            storageKey to value.takeIf { it.isNotBlank() }?.let { encrypt(storageKey, it) }
        }.toMap()
        return SecretTransaction.update(encoded, { preferences.getString(it, null) }, { changes ->
            val editor = preferences.edit()
            changes.forEach { (key, value) -> if (value == null) editor.remove(key) else editor.putString(key, value) }
            check(editor.commit()) { "无法更新加密凭据" }
        }, action)
    }

    private fun encrypt(storageKey: String, value: String): String {
        val encrypted = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, key())
            updateAAD(storageKey.toByteArray())
            iv + doFinal(value.toByteArray())
        }
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    fun copyProfile(source: String, target: String) {
        preferences.all.keys.filter { it.startsWith("$source/") }.forEach { stored ->
            val reference = stored.removePrefix("$source/")
            put(target, reference, get(source, reference))
        }
    }

    fun deleteProfile(profile: String) {
        val editor = preferences.edit()
        preferences.all.keys.filter { it.startsWith("$profile/") }.forEach(editor::remove)
        check(editor.commit()) { "无法删除配置集凭据" }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
            generateKey()
        }
    }
}
