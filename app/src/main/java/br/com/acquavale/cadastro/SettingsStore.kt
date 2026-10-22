package br.com.acquavale.cadastro

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class ServerSettings(
    val host: String,
    val port: Int,
    val appKey: String,
    val appSecret: String
) {
    fun validate() {
        require(host.matches(Regex("[A-Za-z0-9][A-Za-z0-9.-]*"))) { "Informe um host ou IP sem https:// nem barras." }
        require(port in 1..65535) { "Porta inválida." }
        require(appKey.isNotBlank() && appSecret.isNotBlank()) {
            "Configure AppKey e AppSecret da OpenAPI (não são o login admin)."
        }
    }
}

data class PendingPerson(
    val personId: String,
    val remainingGroups: List<String>,
    val needsApply: Boolean = true
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("hikcentral_conf_v1", Context.MODE_PRIVATE)
    private val keyAlias = "hikcentral_openapi_aes_v1"

    fun load(): ServerSettings = ServerSettings(
        host = prefs.getString("host", "608905397dde.sn.mynetname.net") ?: "",
        port = prefs.getInt("port", 37443),
        appKey = decrypt(prefs.getString("key", "") ?: ""),
        appSecret = decrypt(prefs.getString("secret", "") ?: "")
    )

    fun save(config: ServerSettings) {
        config.validate()
        prefs.edit()
            .putString("host", config.host.trim())
            .putInt("port", config.port)
            .putString("key", encrypt(config.appKey))
            .putString("secret", encrypt(config.appSecret))
            .apply()
    }

    fun successCount(): Int = prefs.getInt("success", 0)
    fun incrementSuccess() = prefs.edit().putInt("success", successCount() + 1).apply()

    fun pending(): PendingPerson? {
        val str = prefs.getString("pending", null) ?: return null
        return try {
            val o = JSONObject(str)
            val groupArray = o.optJSONArray("groups") ?: JSONArray()
            PendingPerson(
                o.getString("id"),
                List(groupArray.length()) { i -> groupArray.getString(i) },
                o.optBoolean("apply", true)
            )
        } catch (_: Exception) { null }
    }

    fun savePending(p: PendingPerson) {
        val groups = JSONArray()
        p.remainingGroups.forEach { groups.put(it) }
        val obj = JSONObject().put("id", p.personId).put("groups", groups).put("apply", p.needsApply)
        prefs.edit().putString("pending", obj.toString()).commit()
    }

    fun clearPending() = prefs.edit().remove("pending").commit()

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(keyAlias, null) as? SecretKey
        if (existing != null) return existing
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(value: String): String {
        if (value.isBlank()) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val bytes = Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        return iv + ":" + bytes
    }

    private fun decrypt(value: String): String {
        if (value.isEmpty()) return ""
        return try {
            val parts = value.split(":", limit = 2)
            if (parts.size != 2) return ""
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (_: Exception) {
            // Se a chave do Keystore foi invalidada, solicitar reconfiguração.
            ""
        }
    }
}
