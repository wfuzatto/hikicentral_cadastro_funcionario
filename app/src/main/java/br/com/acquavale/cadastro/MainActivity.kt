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
import java.io.File
import java.util.concurrent.Executors

/**
 * Tela única de cadastramento presencial: foto, nome, sexo e liberação.
 * Não existe login de operador. Credenciais da OpenAPI são configuradas uma vez.
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
        if (store.load().appKey.isBlank() || store.load().appSecret.isBlank()) {
            statusMessage("Abra a engrenagem e configure AppKey e AppSecret da OpenAPI.", false)
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
        form.addView(label("Escolha um ou mais níveis. O aplicativo busca os IDs corretos no HikCentral.", 13f, gray, false))
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
            "Padrão automático: departamento raiz, identificação gerada pelo HikCentral, validade de 10 anos, acesso 24h conforme os níveis já configurados.",
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
            statusMessage("Há um cadastro criado sem permissões completas. Use Reaplicar antes de cadastrar outro.", false)
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
        setBusy(true)
        statusMessage("Consultando organização e níveis de acesso...", true)
        pool.execute {
            try {
                val api = HikApi(settings)
                val orgId = api.rootOrganizationId()
                val selected = api.selectedGroups(checked, api.groups())
                val jpeg = PhotoUtils.photoBytes(file)
                showProgress("Criando Person e enviando foto facial...")
                val personId = api.createPerson(name, gender, jpeg, orgId)
                store.savePending(PendingPerson(personId, selected.map { it.id }))
                finishPending(api)
                store.incrementSuccess()
                store.clearPending()
                runOnUiThread {
                    resetForm()
                    updateCount()
                    updatePending()
                    statusMessage("Funcionário cadastrado e permissões aplicadas! ID: " + personId, true)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    val pending = store.pending()
                    updatePending()
                    statusMessage(
                        if (pending != null) "Person ID " + pending.personId +
                            " já foi criado, mas falta concluir liberação: " + (e.message ?: "Falha")
                        else "Cadastro não confirmado: " + (e.message ?: "Erro de comunicação") +
                            ". Se houve timeout, confira o HikCentral antes de tentar novamente.",
                        false
                    )
                }
            } finally {
                runOnUiThread { setBusy(false) }
            }
        }
    }

    /** Em falhas intermediárias, salva os IDs ainda não atribuídos após cada sucesso. */
    private fun finishPending(api: HikApi) {
        var item = store.pending() ?: return
        for (groupId in item.remainingGroups) {
            showProgress("Atribuindo nível de acesso...")
            api.assign(item.personId, groupId)
            item = item.copy(remainingGroups = item.remainingGroups.filterNot { it == groupId })
            store.savePending(item)
        }
        showProgress("Sincronizando permissões com as catracas...")
        api.applyToDevices()
    }

    private fun retryPending() {
        if (busy) return
        val item = store.pending() ?: return
        val settings = store.load()
        setBusy(true)
        pool.execute {
            try {
                val api = HikApi(settings)
                finishPending(api)
                store.clearPending()
                store.incrementSuccess()
                runOnUiThread {
                    resetForm()
                    updateCount()
                    updatePending()
                    statusMessage("Permissões reaplicadas para Person ID " + item.personId, true)
                }
            } catch (e: Exception) {
                runOnUiThread { statusMessage(
                    "Person ID " + item.personId + " segue pendente: " + (e.message ?: ""), false
                ) }
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
        val p = store.pending()
        retryButton.visibility = if (p == null) View.GONE else View.VISIBLE
        retryButton.text = if (p == null) "REAPLICAR CADASTRO PENDENTE"
            else "REAPLICAR PERMISSÕES • Person " + p.personId
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
        val s = store.load()
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), dp(8))
        }
        fun field(title: String, value: String, password: Boolean = false): EditText {
            column.addView(label(title, 14f, dark, true), standardMargin())
            return EditText(this).apply {
                setText(value)
                textSize = 15f
                isSingleLine = true
                inputType = if (password)
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                else InputType.TYPE_CLASS_TEXT
                column.addView(this)
            }
        }
        val host = field("Servidor (DNS / IP)", s.host)
        val port = field("Porta HTTPS", s.port.toString()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val key = field("OpenAPI AppKey", s.appKey)
        val secret = field("OpenAPI AppSecret", s.appSecret, true)
        column.addView(label(
            "Não use o usuário/senha admin: habilite OpenAPI e obtenha as chaves AK/SK. Certificado HTTPS válido é obrigatório.",
            12f, gray, false
        ), sectionMargin())

        val dialog = AlertDialog.Builder(this)
            .setTitle("⚙ Configuração HikCentral")
            .setView(ScrollView(this).apply { addView(column) })
            .setNegativeButton("Cancelar", null)
            .setNeutralButton("Testar conexão", null)
            .setPositiveButton("Salvar", null)
            .create()
        dialog.setOnShowListener {
            fun collect(): ServerSettings {
                val h = host.text.toString().trim().removePrefix("https://").trimEnd('/')
                return ServerSettings(
                    h, port.text.toString().trim().toIntOrNull() ?: -1,
                    key.text.toString().trim(), secret.text.toString()
                )
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    store.save(collect())
                    statusMessage("Configuração salva neste aparelho.", true)
                    dialog.dismiss()
                } catch (e: Exception) {
                    Toast.makeText(this, e.message ?: "Dados inválidos", Toast.LENGTH_LONG).show()
                }
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val config = try { collect().also { it.validate() } }
                catch (e: Exception) {
                    Toast.makeText(this, e.message ?: "Dados inválidos", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled = false
                statusMessage("Testando OpenAPI...", true)
                pool.execute {
                    val result = try {
                        val api = HikApi(config)
                        val org = api.rootOrganizationId()
                        val countGroups = api.groups().size
                        "Conectado à OpenAPI. Organização " + org +
                            " • " + countGroups + " níveis de acesso disponíveis."
                    } catch (e: Exception) { "Teste falhou: " + (e.message ?: "") }
                    runOnUiThread {
                        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.isEnabled = true
                        val ok = result.startsWith("Conectado")
                        statusMessage(result, ok)
                        Toast.makeText(this, result, Toast.LENGTH_LONG).show()
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
