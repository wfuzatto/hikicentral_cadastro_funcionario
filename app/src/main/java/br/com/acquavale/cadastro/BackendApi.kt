package br.com.acquavale.cadastro

import android.util.Base64
import org.json.JSONObject
import org.json.JSONArray
import java.io.ByteArrayInputStream
import java.net.URL
import java.net.HttpURLConnection
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Locale
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

data class EnrollmentResult(
    val idempotencyKey: String,
    val status: String,
    val personId: String?,
    val needsManualReconciliation: Boolean
) {
    val completed get() = status == "COMPLETED"
}

class BackendException(val statusCode: Int, message: String) : RuntimeException(message)

class BackendApi(private val settings: BackendSettings) {
    init { settings.validate() }

    /** A CA privada é confiável SOMENTE para este app, após validação do fingerprint. */
    private fun connection(path: String, method: String): HttpsURLConnection {
        val connection = URL(settings.url.trimEnd('/') + path).openConnection() as HttpsURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 7000
        connection.readTimeout = 22000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Authorization", "Bearer " + settings.deviceToken)
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Cache-Control", "no-store")
        if (settings.caPem.isNotBlank()) {
            val cert = verifyCa(settings.caPem, settings.caFingerprint)
            val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null) }
            ks.setCertificateEntry("backend-custom-ca", cert)
            val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            factory.init(ks)
            val ssl = SSLContext.getInstance("TLS")
            ssl.init(null, factory.trustManagers, null)
            connection.sslSocketFactory = ssl.socketFactory
            // Não alterar HostnameVerifier. O Android valida o SAN normalmente.
        }
        return connection
    }

    private fun call(path: String, method: String = "GET", body: JSONObject? = null,
                     key: String? = null): JSONObject {
        val conn = connection(path, method)
        try {
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                if (key != null) conn.setRequestProperty("Idempotency-Key", key)
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val payload = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText().take(20_000) } ?: ""
            if (code == 401) throw BackendException(code, "Token inválido ou revogado no backend.")
            if (code == 404) throw BackendException(code, "Tentativa não encontrada no backend.")
            if (code == 409) {
                return try { JSONObject(payload) }
                catch (_: Exception) { throw BackendException(code, "Cadastro exige reconciliação manual.") }
            }
            if (code !in 200..299) {
                throw BackendException(code, when (code) {
                    413 -> "Fotografia ou requisição muito grande."
                    422 -> "Dados inválidos ou setor sem permissão."
                    502, 503, 504 -> "Falha de comunicação com HikCentral; consulte o servidor."
                    else -> "Backend retornou HTTP " + code
                })
            }
            return try { JSONObject(payload) }
            catch (_: Exception) { throw BackendException(code, "Resposta do backend não é JSON.") }
        } finally { conn.disconnect() }
    }
    fun health() = call("/api/v1/health")
    fun groups(): JSONArray = call("/api/v1/access-groups").let {
        // Endpoint retorna lista JSON, não objeto; usar consulta simples separada.
        JSONArray()
    }
    fun register(key: String, name: String, gender: Int, selected: List<String>,
                 jpeg: ByteArray): EnrollmentResult {
        val sectors = JSONArray()
        selected.forEach { sectors.put(it) }
        val body = JSONObject()
            .put("fullName", name)
            .put("gender", gender)
            .put("groups", sectors)
            .put("photoJpegBase64", Base64.encodeToString(jpeg, Base64.NO_WRAP))
        return result(call("/api/v1/enrollments", "POST", body, key))
    }
    fun getStatus(key: String) = result(call("/api/v1/enrollments/" + key))
    fun resume(key: String) = result(call("/api/v1/enrollments/" + key + "/resume", "POST", JSONObject()))
    private fun result(json: JSONObject) = EnrollmentResult(
        json.getString("idempotencyKey"),
        json.getString("status"),
        json.optString("personId").takeIf { it.isNotBlank() && it != "null" },
        json.optBoolean("needsManualReconciliation")
    )
    companion object {
        /** Fingerprint SHA-256 deve ser verificado por canal administrativo independente. */
        fun verifyCa(pem: String, expectedFingerprint: String): X509Certificate {
            val cert = CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(pem.toByteArray(Charsets.UTF_8)))
                as X509Certificate
            require(cert.basicConstraints >= 0) { "O arquivo não é uma CA confiável." }
            cert.checkValidity()
            val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
            val actual = digest.joinToString("") { "%02X".format(Locale.ROOT, it) }
            val expected = expectedFingerprint.replace(":", "").replace(" ", "").uppercase(Locale.ROOT)
            require(expected.length == 64 && actual == expected) {
                "Fingerprint da CA não confere. Verifique com o administrador por outro canal."
            }
            return cert
        }
    }
}
