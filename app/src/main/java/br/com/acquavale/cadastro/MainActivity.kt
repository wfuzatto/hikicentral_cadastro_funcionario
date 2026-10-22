package br.com.acquavale.cadastro

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.util.concurrent.Executors

/**
 * Tela única de cadastramento presencial: foto, nome, sexo e liberação.
 * Sem login de operador. Android fala exclusivamente com backend seguro.
 */
class MainActivity : Activity() {
    private val teal = Color.rgb(4, 104, 89)
    private val dark = Color.rgb(18, 37, 40)
    private val gray = Color.rgb(103, 118, 124)
    private val pool = Executors.newSingleThreadExecutor()
    private lateinit var store: SettingsStore
    private lateinit var nameInput: EditText
    private lateinit var genderGroup: RadioGroup
    private lateinit var faceView: ImageView
    private lateinit var status: TextView
    private lateinit var count: TextView
    private lateinit var saveButton: Button
    private lateinit var cameraButton: Button
    private lateinit var retryButton: Button
    private val sectorChecks = linkedMapOf<String, CheckBox>()
    private var photoFile: File? = null
    private var busy = false

    private val sectorNames = listOf(
        "REFEITORIO",
        "DESENVOLVIMENTO",
        "LOJA ACQUAVALE",
        "CATRACAS SABIA",
        "SAIDA ACQUAVALE",
        "ENTRADA ACQUAVALE"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SettingsStore(this)
        buildScreen()
        updateCount()
        updatePending()
        if (store.load().url.isBlank() || store.load().deviceToken.isBlank()) {
            statusMessage("Abra a engrenagem e configure URL HTTPS + token individual.", false)
        }
        if (store.legacyPendingPersonId() != null) {
            statusMessage("Existe pendência do app antigo com Person já criado. Conferir manualmente no HikCentral.", false)
        }
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(250, 251, 251))
        }
        setContentView(root)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(10), dp(10), dp(10))
            setBackgroundColor(teal)
        }
        val headerLabel = label("Cadastro rápido", 20f, Color.WHITE, true)
        header.addView(headerLabel, LinearLayout.LayoutParams(0, dp(52), 1f).apply {
            gravity = Gravity.CENTER_VERTICAL
        })
        val gear = label("⚙", 30f, Color.WHITE, false).apply {
            gravity = Gravity.CENTER
            contentDescription = "Configurar servidor HikCentral"
            setOnClickListener { openSettings() }
        }
        header.addView(gear, LinearLayout.LayoutParams(dp(55), dp(52)))
        root.addView(header)
        // Respeitar a barra de status no Android 15+ (edge-to-edge).
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            header.setPadding(dp(20), dp(10) + top, dp(10), dp(10))
            root.setPadding(0, 0, 0, bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)

        val scroll = ScrollView(this).apply { isFillViewport = true }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(15), dp(20), dp(30))
        }
        scroll.addView(form)

        form.addView(label("CADASTRO DE FUNCIONÁRIOS", 13f, teal, true))
        form.addView(label("HikCentral • Acqua Vale", 15f, gray, false), standardMargin())

        status = label("Pronto para cadastrar.", 14f, gray, false).apply {
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(Color.rgb(234, 244, 241), 10)
        }
        form.addView(status, standardMargin())

        count = label("", 12f, gray, false)
        form.addView(count, standardMargin())

        form.addView(label("1. Fotografia facial *", 17f, dark, true), sectionMargin())
        form.addView(label("Rosto centralizado, boa iluminação e sem óculos escuros.", 13f, gray, false))
        faceView = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_camera)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.rgb(229, 236, 234))
            contentDescription = "Prévia da foto de reconhecimento facial"
            setOnClickListener { takePhoto() }
        }
        form.addView(faceView, LinearLayout.LayoutParams(-1, dp(225)).apply { topMargin = dp(10) })
        cameraButton = button("TIRAR FOTO", false).apply { setOnClickListener { takePhoto() } }
        form.addView(cameraButton, standardMargin())

        form.addView(label("2. Nome completo *", 17f, dark, true), sectionMargin())
        nameInput = EditText(this).apply {
            hint = "Nome e sobrenome do colaborador"
            textSize = 16f
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_CAP_WORDS or InputType.TYPE_TEXT_VARIATION_PERSON_NAME
            isSingleLine = true
        }
        form.addView(nameInput, standardMargin())

        form.addView(label("3. Sexo *", 17f, dark, true), sectionMargin())
        genderGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        listOf("Masculino", "Feminino", "Não informado").forEachIndexed { i, text ->
            genderGroup.addView(RadioButton(this).apply {
                id = 300 + i
                this.text = text
                textSize = 16f
                setPadding(0, dp(2), 0, dp(2))
            })
        }
        form.addView(genderGroup, standardMargin())

        form.addView(label("4. Setor de liberação *", 17f, dark, true), sectionMargin())
        form.addView(label("Escolha um ou mais níveis. O backend verifica os IDs autorizados.", 13f, gray, false))
        val quick = button("Marcar refeitório + entrada + saída", false).apply {
            setOnClickListener {
                sectorChecks["REFEITORIO"]?.isChecked = true
                sectorChecks["ENTRADA ACQUAVALE"]?.isChecked = true
                sectorChecks["SAIDA ACQUAVALE"]?.isChecked = true
            }
        }
        form.addView(quick, standardMargin())
        sectorNames.forEach { sector ->
            val box = CheckBox(this).apply {
                text = sector
                textSize = 15f
                setTextColor(dark)
                setPadding(dp(4), dp(4), 0, dp(4))
            }
            sectorChecks[sector] = box
            form.addView(box)
        }

        saveButton = button("CADASTRAR FUNCIONÁRIO", true).apply { setOnClickListener { register() } }
        form.addView(saveButton, LinearLayout.LayoutParams(-1, dp(60)).apply { topMargin = dp(25) })
        retryButton = button("REAPLICAR CADASTRO PENDENTE", false).apply {
            setOnClickListener { retryPending() }
            visibility = View.GONE
        }
        form.addView(retryButton, standardMargin())

        form.addView(label(
            "Os dados opcionais são preenchidos no backend. A confirmação final depende também da aplicação das permissões no HikCentral.",
            12f, gray, false
        ), sectionMargin())
    }

    private fun takePhoto() {
        if (busy) return
        try {
            photoFile?.delete()
            val file = File(cacheDir, "hik_face_" + System.currentTimeMillis() + ".jpg")
            photoFile = file
            val uri: Uri = FileProvider.getUriForFile(this, packageName + ".fileprovider", file)
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                clipData = android.content.ClipData.newUri(contentResolver, "Foto facial", uri)
            }
            startActivityForResult(intent, REQUEST_PHOTO)
        } catch (e: Exception) {
            statusMessage("Não foi possível abrir a câmera: " + (e.message ?: ""), false)
        }
    }

    @Deprecated("A captura por intent funciona nos Androids suportados pelo aplicativo.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PHOTO) return
        val file = photoFile
        if (resultCode != RESULT_OK || file == null || file.length() == 0L) {
            file?.delete()
            photoFile = null
            statusMessage("Foto não capturada. Tente novamente.", false)
            return
        }
        faceView.setImageBitmap(PhotoUtils.preview(file))
        statusMessage("Foto capturada. Preencha os dados e cadastre.", true)
    }

    private fun register() {
        if (busy) return
        if (store.pending() != null) {
            statusMessage("Existe uma tentativa pendente. Consulte/retome antes de cadastrar outro funcionário.", false)
            return
        }
        if (store.legacyPendingPersonId() != null) {
            statusMessage("Pendência legada detectada: confira no HikCentral antes de continuar.", false)
            return
        }
        val name = nameInput.text.toString().trim().replace(Regex("\\s+"), " ")
        if (name.length < 2) { nameInput.error = "Informe o nome."; return }
        val gender = when (genderGroup.checkedRadioButtonId) {
            300 -> 1
            301 -> 2
            302 -> 0
            else -> { statusMessage("Selecione o sexo.", false); return }
        }
        val checked = sectorChecks.filter { it.value.isChecked }.keys.toList()
        if (checked.isEmpty()) { statusMessage("Selecione ao menos um setor.", false); return }
        val file = photoFile
        if (file == null || !file.exists() || file.length() == 0L) {
            statusMessage("Tire a fotografia do colaborador.", false)
            return
        }
        val settings = store.load()
        try { settings.validate() } catch (e: Exception) {
            statusMessage(e.message ?: "Configure o servidor.", false)
            openSettings()
            return
        }
        val key = java.util.UUID.randomUUID().toString()
        try { store.savePending(PendingEnrollment(key)) }
        catch (e: Exception) {
            statusMessage("Falha ao salvar chave de idempotência: " + (e.message ?: ""), false)
            return
        }
        setBusy(true)
        statusMessage("Enviando uma única tentativa ao backend...", true)
        pool.execute {
            try {
                val jpeg = PhotoUtils.photoBytes(file)
                val result = BackendApi(settings).register(key, name, gender, checked, jpeg)
                runOnUiThread { showBackendResult(result) }
            } catch (e: Exception) {
                runOnUiThread {
                    updatePending()
                    statusMessage(
                        "Não foi possível confirmar a tentativa. Chave preservada: " + key +
                            ". NÃO cadastre outra vez. Consulte/retome a tentativa. " +
                            backendFailure(e), false
                    )
                }
            } finally {
                runOnUiThread { setBusy(false) }
            }
        }
    }

    private fun backendFailure(e: Exception): String = when (e) {
        is javax.net.ssl.SSLHandshakeException -> "Certificado TLS ou hostname inválido."
        is java.net.ConnectException -> "Backend inacessível; verifique VPN, DNS e firewall."
        is java.net.SocketTimeoutException -> "Timeout. Não reenviar criação automaticamente."
        is BackendException -> e.message ?: "Backend recusou a operação."
        else -> e.message ?: "Falha de comunicação."
    }

    private fun showBackendResult(result: EnrollmentResult) {
        if (result.completed) {
            store.clearPending()
            store.incrementSuccess()
            resetForm()
            updateCount()
            updatePending()
            statusMessage("Funcionário confirmado pelo backend. Person: " +
                (result.personId ?: "ID não retornado"), true)
        } else {
            updatePending()
            val message = if (result.needsManualReconciliation)
                "Cadastro em estado ambíguo. NÃO recriar. Conferir com administrador. "
            else "Cadastro registrado, porém permissões/sincronização pendentes. "
            statusMessage(message + "Estado: " + result.status +
                ". Tentativa: " + result.idempotencyKey, false)
        }
    }

    private fun retryPending() {
        if (busy) return
        val attempt = store.pending() ?: return
        val config = store.load()
        setBusy(true)
        pool.execute {
            try {
                val api = BackendApi(config)
                val current = api.getStatus(attempt.idempotencyKey)
                val result = when (current.status) {
                    "PERMISSIONS_PENDING", "SYNC_PENDING" -> api.resume(attempt.idempotencyKey)
                    else -> current
                }
                runOnUiThread { showBackendResult(result) }
            } catch (e: BackendException) {
                runOnUiThread {
                    val suffix = if (e.statusCode == 404)
                        " O backend não localizou esta chave. Não descartar sem validar o servidor correto."
                        else ""
                    statusMessage("Consulta da tentativa falhou: " + (e.message ?: "") + suffix, false)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusMessage("Não foi possível consultar backend: " + backendFailure(e), false)
                }
            } finally { runOnUiThread { setBusy(false) } }
        }
    }

    private fun resetForm() {
        nameInput.text.clear()
        genderGroup.clearCheck()
        sectorChecks.values.forEach { it.isChecked = false }
        photoFile?.delete()
        photoFile = null
        faceView.setImageResource(android.R.drawable.ic_menu_camera)
    }

    private fun updatePending() {
        val pending = store.pending()
        retryButton.visibility = if (pending == null) View.GONE else View.VISIBLE
        retryButton.text = if (pending == null) "CONSULTAR CADASTRO PENDENTE"
            else "CONSULTAR / RETOMAR • " + pending.idempotencyKey.take(8)
    }

    private fun updateCount() {
        count.text = "Cadastros confirmados neste aparelho: " + store.successCount()
    }

    private fun showProgress(message: String) = runOnUiThread { statusMessage(message, true) }

    private fun statusMessage(message: String, success: Boolean) {
        status.text = message
        status.setTextColor(if (success) teal else Color.rgb(151, 45, 36))
        status.background = rounded(if (success) Color.rgb(234, 244, 241)
            else Color.rgb(255, 240, 238), 10)
    }

    private fun setBusy(value: Boolean) {
        busy = value
        saveButton.isEnabled = !value
        retryButton.isEnabled = !value
        cameraButton.isEnabled = !value
        saveButton.text = if (value) "CADASTRANDO..." else "CADASTRAR FUNCIONÁRIO"
    }

    private fun openSettings() {
        if (busy) return
        val saved = store.load()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(6), dp(22), dp(8))
        }
        fun field(caption: String, value: String, password: Boolean = false): EditText {
            container.addView(label(caption, 13f, dark, true), standardMargin())
            return EditText(this).apply {
                setText(value)
                textSize = 14f
                isSingleLine = true
                inputType = if (password)
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                else InputType.TYPE_CLASS_TEXT
                container.addView(this)
            }
        }
        val url = field("URL HTTPS do backend", saved.url).apply {
            hint = "https://backend.exemplo.local:18443"
        }
        val token = field("Token individual do dispositivo", saved.deviceToken, true)
        val ca = field("CA interna (PEM opcional; conteúdo público)", saved.caPem).apply {
            isSingleLine = false
            minLines = 2
            maxLines = 5
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            hint = "Cole aqui a CA PEM verificada pelo administrador"
        }
        val fingerprint = field("SHA-256 da CA (obtido por canal confiável)",
            saved.caFingerprint).apply {
            hint = "64 caracteres hexadecimais"
        }
        val output = label(
            "O token fica criptografado pelo Android Keystore. Não use AppKey/AppSecret do HikCentral. " +
                "CA interna: confirme o fingerprint com o administrador por canal independente.",
            12f, gray, false
        ).apply { setTextIsSelectable(true) }
        container.addView(output, sectionMargin())
        val dialog = AlertDialog.Builder(this)
            .setTitle("⚙ Configuração do backend")
            .setView(ScrollView(this).apply { addView(container) })
            .setNegativeButton("Cancelar", null)
            .setNeutralButton("Testar backend", null)
            .setPositiveButton("Salvar", null)
            .create()
        dialog.setOnShowListener {
            fun values() = BackendSettings(
                url.text.toString().trim(),
                token.text.toString().trim(),
                ca.text.toString().trim(),
                fingerprint.text.toString().trim()
            )
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    store.save(values())
                    statusMessage("Backend configurado; nenhuma credencial Artemis no Android.", true)
                    dialog.dismiss()
                } catch (e: Exception) {
                    output.text = e.message ?: "Configuração inválida."
                }
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val config = try { values().also { it.validate() } }
                catch (e: Exception) {
                    output.text = e.message ?: "Configuração inválida"
                    return@setOnClickListener
                }
                val test = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                test.isEnabled = false
                output.text = "Testando TLS, saúde e token do backend..."
                pool.execute {
                    val response = try {
                        val api = BackendApi(config)
                        api.health()
                        api.groups()
                        "Conexão HTTPS e token validados. Backend acessível."
                    } catch (e: Exception) {
                        "Falha: " + backendFailure(e)
                    }
                    runOnUiThread {
                        output.text = response
                        test.isEnabled = true
                    }
                }
            }
        }
        dialog.show()
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun button(text: String, primary: Boolean): Button = Button(this).apply {
        this.text = text
        textSize = if (primary) 15f else 13f
        isAllCaps = false
        setTextColor(if (primary) Color.WHITE else teal)
        background = rounded(if (primary) teal else Color.rgb(226, 242, 238), 12)
        setPadding(dp(12), dp(8), dp(12), dp(8))
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun standardMargin() = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
    private fun sectionMargin() = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24) }
    private fun dp(value: Int): Int = (resources.displayMetrics.density * value).toInt()

    override fun onDestroy() {
        super.onDestroy()
        pool.shutdown()
    }

    companion object { private const val REQUEST_PHOTO = 701 }
}
