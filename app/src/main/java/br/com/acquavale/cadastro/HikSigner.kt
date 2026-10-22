package br.com.acquavale.cadastro

import android.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Artemis OpenAPI: HTTP method, Accept, Content-Type, signed headers e URI.
 * Não adicionar linhas vazias para Content-MD5/Date quando os cabeçalhos não
 * foram enviados. Ordem alfabética dos cabeçalhos assinados é obrigatória.
 */
object HikSigner {
    fun canonical(path: String, key: String, timestamp: String): String =
        "POST\n*/*\napplication/json\nx-ca-key:" + key +
            "\nx-ca-timestamp:" + timestamp + "\n" + path

    fun sign(path: String, key: String, secret: String, timestamp: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val hash = mac.doFinal(canonical(path, key, timestamp).toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }
}
