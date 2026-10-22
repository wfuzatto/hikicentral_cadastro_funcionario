package br.com.acquavale.cadastro

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.text.Normalizer
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import javax.net.ssl.HttpsURLConnection

data class AccessGroup(val id: String, val name: String)

class HikApi(private val settings: ServerSettings) {
    init { settings.validate() }

    /** HTTPS obrigatório; verificação de certificado e hostname do Android preservada. */
    private fun post(path: String, json: JSONObject = JSONObject()): JSONObject {
        val uri = URL("https://" + settings.host + ":" + settings.port + path)
        val conn = uri.openConnection() as HttpsURLConnection
        val timestamp = System.currentTimeMillis().toString()
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 9000
            conn.readTimeout = 20000
            conn.doOutput = true
            conn.setRequestProperty("Accept", "*/*")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("X-Ca-Key", settings.appKey)
            conn.setRequestProperty("X-Ca-Timestamp", timestamp)
            conn.setRequestProperty("X-Ca-Signature-Headers", "x-ca-key,x-ca-timestamp")
            conn.setRequestProperty(
                "X-Ca-Signature",
                HikSigner.sign(path, settings.appKey, settings.appSecret, timestamp)
            )
            conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
            val status = conn.responseCode
            val data = (if (status in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText().take(128_000) } ?: ""
            if (status !in 200..299) {
                throw IllegalStateException("Servidor respondeu HTTP " + status +
                    ". Verifique OpenAPI, porta e autorização. " + data.take(200))
            }
            val body = try { JSONObject(data) } catch (_: Exception) {
                throw IllegalStateException("Resposta não é JSON da OpenAPI: " + data.take(100))
            }
            if (body.optString("code") != "0") {
                throw IllegalStateException(
                    "HikCentral código " + body.optString("code", "?") + ": " +
                        body.optString("msg", "Operação recusada")
                )
            }
            return body
        } finally {
            conn.disconnect()
        }
    }

    fun rootOrganizationId(): String {
        val root = post("/artemis/api/resource/v1/org/rootOrg").optJSONObject("data")
        return root?.optString("orgIndexCode")?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Organização raiz não encontrada no HikCentral.")
    }

    fun groups(): List<AccessGroup> {
        val all = mutableListOf<AccessGroup>()
        var page = 1
        while (page <= 20) {
            val request = JSONObject().put("pageNo", page).put("pageSize", 500).put("type", 1)
            val data = post("/artemis/api/acs/v1/privilege/group", request).optJSONObject("data")
                ?: throw IllegalStateException("HikCentral não retornou os níveis de acesso.")
            val list = data.optJSONArray("list") ?: JSONArray()
            for (i in 0 until list.length()) {
                val item = list.getJSONObject(i)
                val id = item.optString("privilegeGroupId")
                val name = item.optString("privilegeGroupName")
                if (id.isNotBlank() && name.isNotBlank()) all.add(AccessGroup(id, name))
            }
            if (list.length() < 500 || all.size >= data.optInt("total", Int.MAX_VALUE)) break
            page++
        }
        return all
    }

    fun selectedGroups(selectedNames: List<String>, existingGroups: List<AccessGroup>): List<AccessGroup> {
        return selectedNames.map { requested ->
            val found = existingGroups.filter { normalized(it.name) == normalized(requested) }
            when (found.size) {
                1 -> found.first()
                0 -> throw IllegalStateException(
                    "Nível de acesso não encontrado: " + requested + ". Confira o nome no HikCentral."
                )
                else -> throw IllegalStateException("Há níveis duplicados: " + requested)
            }
        }
    }

    fun createPerson(fullName: String, gender: Int, photoJpeg: ByteArray, orgId: String): String {
        val parts = fullName.trim().split(Regex("\\s+"), limit = 2)
        require(parts[0].length >= 2) { "Informe o nome do colaborador." }
        require(photoJpeg.isNotEmpty()) { "A foto é obrigatória." }
        val from = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS)
        val to = from.plusYears(10)
        val person = JSONObject()
            .put("personGivenName", parts[0])
            .put("personFamilyName", parts.getOrElse(1) { "-" })
            .put("gender", gender)
            .put("orgIndexCode", orgId)
            .put("faces", JSONArray().put(JSONObject()
                .put("faceData", Base64.encodeToString(photoJpeg, Base64.NO_WRAP))))
            .put("beginTime", from.toString())
            .put("endTime", to.toString())
        val id = post("/artemis/api/resource/v1/person/single/add", person).optString("data")
        if (id.isBlank() || id == "null") {
            throw IllegalStateException("Pessoa enviada, mas servidor não retornou personId. Verifique no painel antes de repetir.")
        }
        return id
    }

    fun assign(personId: String, groupId: String) {
        val body = JSONObject()
            .put("privilegeGroupId", groupId)
            .put("type", 1)
            .put("list", JSONArray().put(JSONObject().put("id", personId)))
        post("/artemis/api/acs/v1/privilege/group/single/addPersons", body)
    }

    fun applyToDevices() {
        post("/artemis/api/visitor/v1/auth/reapplication")
    }

    companion object {
        fun normalized(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .uppercase()
    }
}
