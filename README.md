Driven AI
An experimental Android application that uses Google Gemini's Function Calling to control physical device hardware via natural language commands.

 Features
Hardware Control: Turn the device flashlight on/off using conversational prompts.

System Monitoring: Ask the AI to retrieve real-time battery levels.

Autonomous Agent: Gemini intelligently triggers native Android APIs (CameraManager, BatteryManager) only when necessary.

 Tech Stack
Language: Kotlin (Coroutines for async tasks)

SDK: Android API 36

AI: Google Generative AI SDK (0.10.0+) & Gemini 3.6 Flash

 Quick Setup
Clone the repository and open it in Android Studio.

Generate an API key from Google AI Studio.

Insert your key in MainActivity.kt:

Kotlin
val generativeModel = GenerativeModel(
    modelName = "gemini-3.6-flash", 
    apiKey = "YOUR_API_KEY_HERE",
    tools = listOf(aiTools)
)
Build and run on a physical Android device (required for flashlight).
