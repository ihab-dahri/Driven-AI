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
import com.google.ai.client.generativeai.Chat
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.FunctionResponsePart
import com.google.ai.client.generativeai.type.Schema
import com.google.ai.client.generativeai.type.Tool
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.defineFunction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var chatSession: Chat
    private lateinit var tvChat: TextView
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etQuestion = findViewById<EditText>(R.id.etQuestion)
        val btnSend = findViewById<Button>(R.id.btnSend)
        tvChat = findViewById(R.id.tvChat)

        // 1. Initialisation du matériel (Le Flash)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            cameraId = cameraManager.cameraIdList[0]
        } catch (e: Exception) {
            appendMessage("Erreur caméra : Impossible de trouver le flash.")
        }

        // 2. Déclaration des outils (Fonctions)
        val flashlightFunction = defineFunction(
            name = "set_flashlight",
            description = "Allume ou éteint la lampe torche (flash) du téléphone.",
            parameters = listOf(
                Schema.bool(
                    name = "is_on",
                    description = "Mettre à true pour allumer, false pour éteindre."
                )
            )
        )

        val batteryFunction = defineFunction(
            name = "get_battery_level",
            description = "Récupère le niveau de batterie actuel du téléphone de l'utilisateur en pourcentage."
        )

        // On intègre les deux fonctions dans la boîte à outils de l'Agent
        val aiTools = Tool(listOf(flashlightFunction, batteryFunction))

        // 3. Initialisation du modèle
        val generativeModel = GenerativeModel(
            modelName = "gemini-3-flash",
            apiKey = , // ⚠️ N'oublie pas de remettre ta clé API
            tools = listOf(aiTools)
        )

        chatSession = generativeModel.startChat()
        appendMessage("🤖 Agent IA prêt. Tu peux me demander d'allumer la lumière ou vérifier la batterie !")

        // 4. Gestion du bouton d'envoi
        btnSend.setOnClickListener {
            val userText = etQuestion.text.toString()
            if (userText.isNotBlank()) {
                appendMessage("👤 Toi : $userText")
                etQuestion.text.clear()
                sendCommandToAgent(userText)
            }
        }
    }

    private fun sendCommandToAgent(prompt: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = chatSession.sendMessage(prompt)
                val functionCall = response.functionCall

                if (functionCall != null) {
                    // CAS 1 : L'IA veut contrôler la lampe torche
                    if (functionCall.name == "set_flashlight") {
                        val turnOn = functionCall.args["is_on"] as? Boolean ?: false
                        toggleRealFlashlight(turnOn)

                        val resultJson = JSONObject().apply {
                            put("status", "success")
                            put("hardware_state", if (turnOn) "on" else "off")
                        }

                        val finalResponse = chatSession.sendMessage(
                            content {
                                part(FunctionResponsePart("set_flashlight", resultJson))
                            }
                        )

                        withContext(Dispatchers.Main) {
                            appendMessage("🤖 Agent : ${finalResponse.text}")
                        }
                    }
                    // CAS 2 : L'IA veut lire le niveau de batterie
                    else if (functionCall.name == "get_battery_level") {
                        val level = getBatteryLevel()

                        val resultJson = JSONObject().apply {
                            put("battery_level", level)
                            put("unit", "%")
                        }

                        val finalResponse = chatSession.sendMessage(
                            content {
                                part(FunctionResponsePart("get_battery_level", resultJson))
                            }
                        )

                        withContext(Dispatchers.Main) {
                            appendMessage("🤖 Agent : ${finalResponse.text}")
                        }
                    }
                } else {
                    // CAS 3 : Réponse texte classique
                    withContext(Dispatchers.Main) {
                        appendMessage("🤖 IA : ${response.text}")
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    appendMessage("❌ Erreur : ${e.message}")
                }
            }
        }
    }

    // --- FONCTIONS MATÉRIELLES (KOTLIN) ---

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

    // --- UTILITAIRE ---

    private fun appendMessage(text: String) {
        tvChat.append("\n$text\n")
    }
}