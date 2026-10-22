package br.com.acquavale.cadastro

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class BackendSettings(
    val url: String,
    val deviceToken: String,
    val caPem: String = "",
    val caFingerprint: String = ""
) {
    fun validate() {
        val uri = java.net.URI(url.trim())
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() &&
            (uri.rawPath.isNullOrEmpty() || uri.rawPath == "/") &&
            uri.rawQuery == null && uri.rawUserInfo == null && uri.rawFragment == null) {
            "Informe URL HTTPS completa, sem caminhos, parâmetros ou usuário."
        }
        require(deviceToken.isNotBlank()) { "Informe o token do dispositivo." }
        require(deviceToken.length <= 160) { "Token inválido." }
        if (caPem.isNotBlank()) {
            BackendApi.verifyCa(caPem, caFingerprint)
        }
    }
}

data class PendingEnrollment(val idempotencyKey: String)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("hikcentral_conf_v1", Context.MODE_PRIVATE)
    private val alias = "hikcentral_device_token_v1"

    init {
        // Migração sem apagar pendências antigas: elimina credenciais Artemis do app.
        if (prefs.contains("key") || prefs.contains("secret")) {
            prefs.edit().remove("key").remove("secret").apply()
            val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keys.containsAlias("hikcentral_openapi_aes_v1")) {
                keys.deleteEntry("hikcentral_openapi_aes_v1")
            }
        }
    }

    fun load() = BackendSettings(
        prefs.getString("backend_url", "") ?: "",
        decrypt(prefs.getString("device_token", "") ?: ""),
        prefs.getString("backend_ca", "") ?: "",
        prefs.getString("backend_ca_fingerprint", "") ?: ""
    )

    fun save(s: BackendSettings) {
        s.validate()
        prefs.edit()
            .putString("backend_url", s.url.trim().trimEnd('/'))
            .putString("device_token", encrypt(s.deviceToken))
            .putString("backend_ca", s.caPem)
            .putString("backend_ca_fingerprint", s.caFingerprint)
            .apply()
    }

    fun pending(): PendingEnrollment? {
        val value = prefs.getString("pending_backend", null) ?: return null
        return try { PendingEnrollment(java.util.UUID.fromString(value).toString()) }
        catch (_: Exception) { null }
    }

    fun savePending(p: PendingEnrollment) {
        check(prefs.edit().putString("pending_backend", p.idempotencyKey).commit()) {
            "Não foi possível salvar a chave de idempotência."
        }
    }
    fun clearPending() = prefs.edit().remove("pending_backend").commit()
    fun legacyPendingPersonId(): String? = try {
        JSONObject(prefs.getString("pending", "") ?: "").optString("id").ifBlank { null }
    } catch (_: Exception) { null }
    fun successCount() = prefs.getInt("success", 0)
    fun incrementSuccess() = prefs.edit().putInt("success", successCount() + 1).apply()

    private fun aesKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = ks.getKey(alias, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return generator.generateKey()
    }
    private fun encrypt(value: String): String {
        if (value.isBlank()) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey())
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
    private fun decrypt(value: String): String {
        if (value.isBlank()) return ""
        return try {
            val parts = value.split(":", limit = 2)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, aesKey(),
                GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) { "" }
    }
}
