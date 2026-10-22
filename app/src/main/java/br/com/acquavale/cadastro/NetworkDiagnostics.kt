package br.com.acquavale.cadastro

import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/** Diagnóstico não destrutivo: sem credenciais e sem dados pessoais. */
object NetworkDiagnostics {
    fun validAddress(host: String, port: Int): Boolean =
        host.matches(Regex("[A-Za-z0-9][A-Za-z0-9.-]*")) && port in 1..65535

    fun check(host: String, port: Int): String {
        if (!validAddress(host, port)) return "Informe DNS/IP e porta válidos."
        val addresses = try {
            InetAddress.getAllByName(host)
        } catch (e: UnknownHostException) {
            return "DNS: FALHOU. Não foi possível resolver $host. Confira a internet ou o DNS."
        }
        if (addresses.isEmpty()) return "DNS: sem endereço IP."

        val ip = addresses.first().hostAddress ?: "desconhecido"
        val prefix = "DNS: OK ($ip)\n"
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(addresses.first(), port), 5000)
            }
        } catch (e: Exception) {
            return prefix + "TCP $ip:$port: FALHOU. " + networkHint(e) +
                "\nTeste na rede Wi-Fi interna e confirme NAT/firewall para essa porta."
        }

        val tcpOk = prefix + "TCP $ip:$port: OK\n"
        val connection = URL("https://$host:$port/").openConnection() as HttpsURLConnection
        return try {
            connection.connectTimeout = 6000
            connection.readTimeout = 6000
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            val httpStatus = connection.responseCode
            tcpOk + "HTTPS: OK (HTTP $httpStatus). A rede e o certificado responderam." +
                "\nIsso não confirma que a porta é da OpenAPI. " +
                "Use TESTAR OPENAPI (AK/SK) para verificar a autenticação."
        } catch (e: SSLHandshakeException) {
            tcpOk + "HTTPS: FALHOU na negociação ou certificado: " +
                (e.message ?: "erro TLS") +
                "\nInstale um certificado válido para o DNS; não desative sua verificação."
        } catch (e: SSLPeerUnverifiedException) {
            tcpOk + "HTTPS: FALHOU. Certificado não corresponde ao host ou não é confiável."
        } catch (e: Exception) {
            tcpOk + "HTTPS: FALHOU. " + networkHint(e)
        } finally {
            connection.disconnect()
        }
    }

    fun explainFailure(e: Throwable, host: String, port: Int): String {
        val all = generateSequence(e as Throwable?) { it.cause }.toList()
        val networkError = all.firstOrNull { it is UnknownHostException ||
            it is ConnectException || it is SocketTimeoutException ||
            it is SSLHandshakeException || it is SSLPeerUnverifiedException ||
            it is SocketException }
        return if (networkError != null) {
            "Conexão com $host:$port não concluída. " + networkHint(networkError) +
                "\nAbra ⚙ → DIAGNOSTICAR REDE para identificar a etapa da falha."
        } else {
            "Falha na OpenAPI: " + (e.message ?: e.javaClass.simpleName) +
                "\nSe a rede estiver OK, verifique AK/SK, autorização e a porta correta."
        }
    }

    internal fun networkHint(e: Throwable): String = when (e) {
        is UnknownHostException -> "Nome DNS não resolveu. Verifique o endereço."
        is ConnectException -> "Conexão TCP recusada ou inacessível. Verifique serviço, porta e firewall/NAT."
        is SocketTimeoutException -> "Tempo esgotado. Porta bloqueada, sem NAT ou servidor sem resposta."
        is SSLHandshakeException -> "Falha na negociação/certificado TLS HTTPS."
        is SSLPeerUnverifiedException -> "Certificado HTTPS não corresponde ao servidor."
        is SocketException -> "Erro de rede/TCP: " + (e.message ?: "sem conexão")
        else -> "Erro de comunicação: " + (e.message ?: e.javaClass.simpleName)
    }
}
