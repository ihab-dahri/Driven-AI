package com.example.driven_ai

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var llmInference: LlmInference
    private lateinit var tvChat: TextView
    private lateinit var chatScrollView: ScrollView
    private lateinit var etQuestion: EditText
    private lateinit var btnSend: Button
    private lateinit var btnMic: Button

    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private lateinit var modelPath: String

    private lateinit var tts: TextToSpeech
    private lateinit var speechRecognizer: SpeechRecognizer

    // Variables pour le Streaming et le BENCHMARKING
    private var currentStreamingResponse = StringBuilder()
    private var isTechnicalCommand = false
    private var inferenceStartTime: Long = 0
    private var generatedTokensCount: Int = 0

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etQuestion = findViewById(R.id.etQuestion)
        btnSend = findViewById(R.id.btnSend)
        btnMic = findViewById(R.id.btnMic)
        tvChat = findViewById(R.id.tvChat)
        chatScrollView = findViewById(R.id.chatScrollView)

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            cameraId = cameraManager.cameraIdList[0]
        } catch (e: Exception) {
            appendMessage("⚠️ Erreur : Flash introuvable.")
        }

        initVoiceFeatures()

        appendMessage("⏳ Chargement du modèle IA en RAM...")
        btnSend.isEnabled = false

        modelPath = File(getExternalFilesDir(null), "gemma.bin").absolutePath

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (!File(modelPath).exists()) {
                    throw Exception("Fichier modèle introuvable")
                }
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(512)
                    .setResultListener { partialResult, done ->
                        runOnUiThread {
                            // 1. COMPTEUR DE TOKENS : Chaque partialResult est un token (ou fragment)
                            if (partialResult.isNotEmpty()) {
                                generatedTokensCount++
                            }

                            currentStreamingResponse.append(partialResult)

                            if (currentStreamingResponse.startsWith("<")) {
                                isTechnicalCommand = true
                            }

                            if (!isTechnicalCommand) {
                                tvChat.append(partialResult)
                                chatScrollView.post { chatScrollView.fullScroll(android.view.View.FOCUS_DOWN) }
                            }

                            if (done) {
                                // 2. CALCUL DES PERFORMANCES
                                val durationSec = (System.currentTimeMillis() - inferenceStartTime) / 1000.0
                                val speed = if (durationSec > 0) generatedTokensCount / durationSec else 0.0

                                // Formatage du badge technique
                                val statsBadge = String.format(Locale.FRANCE, "[⏱️ %.1fs | ⚡ %.1f tok/s]", durationSec, speed)

                                val finalRes = currentStreamingResponse.toString().trim()
                                if (isTechnicalCommand) {
                                    processLocalFunctionCalling(finalRes, statsBadge)
                                } else {
                                    speakOut(finalRes)
                                    // Affichage des statistiques sous la réponse
                                    tvChat.append("\n$statsBadge\n\n")
                                }
                            }
                        }
                    }
                    .build()

                llmInference = LlmInference.createFromOptions(this@MainActivity, options)

                withContext(Dispatchers.Main) {
                    val msgReady = "Agent I A prêt."
                    appendMessage("✅ Agent IA 100% Hors-Ligne Prêt !")
                    btnSend.isEnabled = true
                    speakOut(msgReady)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    appendMessage("❌ Erreur de chargement : ${e.message}")
                }
            }
        }

        btnSend.setOnClickListener {
            val userText = etQuestion.text.toString()
            if (userText.isNotBlank()) {
                appendMessage("👤 Toi : $userText")
                etQuestion.text.clear()
                sendCommandToLocalAgent(userText)
            }
        }

        btnMic.setOnClickListener {
            Toast.makeText(this, "Initialisation du micro...", Toast.LENGTH_SHORT).show()
            startListening()
        }
    }

    private fun sendCommandToLocalAgent(prompt: String) {
        // 3. RÉINITIALISATION DU CHRONOMÈTRE
        currentStreamingResponse.clear()
        isTechnicalCommand = false
        generatedTokensCount = 0
        inferenceStartTime = System.currentTimeMillis() // Top départ !

        tvChat.append("\n🤖 Agent : ")
        chatScrollView.post { chatScrollView.fullScroll(android.view.View.FOCUS_DOWN) }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val systemPrompt = """
                    Tu es l'assistant de ce téléphone. Tu dois catégoriser la demande avec un code strict.

                    Exemples :
                    Demande : allume la lumière
                    Réponse : <FLASH_ON>
                    Demande : flash
                    Réponse : <FLASH_ON>
                    Demande : éteins la torche
                    Réponse : <FLASH_OFF>
                    Demande : etiens la lumiere
                    Réponse : <FLASH_OFF>
                    Demande : coupe le flash
                    Réponse : <FLASH_OFF>
                    Demande : turn off
                    Réponse : <FLASH_OFF>
                    Demande : niveau de batterie
                    Réponse : <BATTERY>
                    Demande : comment tu t'appelles ?
                    Réponse : Je suis Driven AI, votre assistant local.

                    À toi de jouer :
                    Demande : $prompt
                    Réponse :
                """.trimIndent()

                llmInference.generateResponseAsync(systemPrompt)

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    appendMessage("\n❌ Erreur d'inférence : ${e.message}")
                }
            }
        }
    }

    // Ajout d'un paramètre "stats" pour afficher les performances même sur les actions matérielles
    private fun processLocalFunctionCalling(aiResponse: String, stats: String = "") {
        val responseClean = aiResponse.lowercase()

        when {
            responseClean.contains("flash_on") || responseClean.contains("flash on") -> {
                toggleRealFlashlight(true)
                val msg = "J'ai allumé la lampe torche"
                tvChat.append("$msg 🔦\n$stats\n\n")
                speakOut(msg)
            }
            responseClean.contains("flash_off") || responseClean.contains("flash off") -> {
                toggleRealFlashlight(false)
                val msg = "J'ai éteint la lampe torche"
                tvChat.append("$msg 🌑\n$stats\n\n")
                speakOut(msg)
            }
            responseClean.contains("battery") || responseClean.contains("batterie") -> {
                val level = getBatteryLevel()
                val msg = "La batterie est actuellement à $level pour cent."
                tvChat.append("$msg 🔋\n$stats\n\n")
                speakOut(msg)
            }
            else -> {
                tvChat.append("$aiResponse\n$stats\n\n")
                speakOut(aiResponse)
            }
        }
        chatScrollView.post { chatScrollView.fullScroll(android.view.View.FOCUS_DOWN) }
    }

    private fun initVoiceFeatures() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.FRENCH
            }
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
    }

    private fun speakOut(text: String) {
        val cleanText = text.replace("*", "")
        tts.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                etQuestion.hint = "Écoute en cours... Parlez !"
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val spokenText = matches[0]
                    etQuestion.setText(spokenText)
                    etQuestion.hint = "Demande vocale ou texte..."
                    btnSend.performClick()
                }
            }
            override fun onError(error: Int) {
                val errorMsg = when(error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Erreur Audio"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permission refusée"
                    SpeechRecognizer.ERROR_NO_MATCH -> "Aucun mot reconnu"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Micro occupé"
                    else -> "Erreur $error"
                }
                etQuestion.hint = "Demande vocale ou texte..."
                Toast.makeText(this@MainActivity, "❌ $errorMsg", Toast.LENGTH_SHORT).show()
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { etQuestion.hint = "Traitement..." }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        try {
            speechRecognizer.startListening(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Erreur système vocal : ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun toggleRealFlashlight(state: Boolean) {
        try {
            cameraId?.let { id -> cameraManager.setTorchMode(id, state) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getBatteryLevel(): Int {
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun appendMessage(text: String) {
        tvChat.append("\n$text\n")
        chatScrollView.post {
            chatScrollView.fullScroll(android.view.View.FOCUS_DOWN)
        }
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        speechRecognizer.destroy()
        super.onDestroy()
    }
}