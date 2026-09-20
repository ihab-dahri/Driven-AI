package com.example.driven_ai

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var llmInference: LlmInference
    private lateinit var tvChat: TextView
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null

    // Chemin du modèle poussé manuellement sur le téléphone via Device Explorer
    private lateinit var modelPath: String

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etQuestion = findViewById<EditText>(R.id.etQuestion)
        val btnSend = findViewById<Button>(R.id.btnSend)
        tvChat = findViewById(R.id.tvChat)

        // 1. Initialisation Matérielle
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            cameraId = cameraManager.cameraIdList[0]
        } catch (e: Exception) {
            appendMessage("⚠️ Erreur : Flash introuvable.")
        }

        // 2. Chargement du Modèle Local (Asynchrone car très lourd)

        appendMessage("⏳ Chargement du modèle IA en RAM (cela peut prendre quelques secondes)...")
        btnSend.isEnabled = false

        modelPath = File(getExternalFilesDir(null), "gemma.bin").absolutePath

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (!File(modelPath).exists()) {
                    throw Exception("Fichier modèle introuvable dans $modelPath")
                }

                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(512)
                    .build()

                llmInference = LlmInference.createFromOptions(this@MainActivity, options)

                withContext(Dispatchers.Main) {
                    appendMessage("✅ Agent IA 100% Hors-Ligne Prêt !")
                    btnSend.isEnabled = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    appendMessage("❌ Erreur de chargement : ${e.message}")
                }
            }
        }

        // 3. Gestion de l'envoi
        btnSend.setOnClickListener {
            val userText = etQuestion.text.toString()
            if (userText.isNotBlank()) {
                appendMessage("👤 Toi : $userText")
                etQuestion.text.clear()
                sendCommandToLocalAgent(userText)
            }
        }
    }

    private fun sendCommandToLocalAgent(prompt: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Création d'un "System Prompt" pour forcer le comportement de l'IA locale
                val systemPrompt = """
                    Tu es l'assistant du téléphone. Analyse la demande de l'utilisateur.
                    Si l'utilisateur demande d'allumer la lumière ou la lampe torche, réponds EXACTEMENT : <FLASH_ON>
                    Si l'utilisateur demande d'éteindre la lumière, réponds EXACTEMENT : <FLASH_OFF>
                    Si l'utilisateur demande le niveau de batterie, réponds EXACTEMENT : <BATTERY>
                    Sinon, réponds normalement et brièvement à la question.
                    
                    Demande de l'utilisateur : $prompt
                    Réponse :
                """.trimIndent()

                // Génération de la réponse (calcul 100% sur le CPU/GPU du téléphone)
                val response = llmInference.generateResponse(systemPrompt)

                withContext(Dispatchers.Main) {
                    processLocalFunctionCalling(response)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    appendMessage("❌ Erreur d'inférence : ${e.message}")
                }
            }
        }
    }

    // 4. Analyse syntaxique (Notre version "maison" du Function Calling)
    // 4. Analyse syntaxique plus robuste
    private fun processLocalFunctionCalling(aiResponse: String) {
        // On met tout en minuscules pour éviter les problèmes de majuscules
        val responseClean = aiResponse.lowercase()

        when {
            responseClean.contains("flash_on") || responseClean.contains("flash on") -> {
                toggleRealFlashlight(true)
                appendMessage("🤖 Agent : J'ai allumé la lampe torche 🔦 (Exécution locale).")
            }
            responseClean.contains("flash_off") || responseClean.contains("flash off") -> {
                toggleRealFlashlight(false)
                appendMessage("🤖 Agent : J'ai éteint la lampe torche 🌑 (Exécution locale).")
            }
            responseClean.contains("battery") || responseClean.contains("batterie") -> {
                val level = getBatteryLevel()
                appendMessage("🤖 Agent : La batterie est actuellement à $level%. 🔋")
            }
            else -> {
                // Réponse conversationnelle classique
                appendMessage("🤖 Agent : $aiResponse")
            }
        }
    }

    // --- FONCTIONS MATÉRIELLES (Inchangées) ---
    private fun toggleRealFlashlight(state: Boolean) {
        try {
            cameraId?.let { id ->
                cameraManager.setTorchMode(id, state)
            }
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
    }
}