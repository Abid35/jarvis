package com.jarvis.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    companion object {
        const val MODEL = "gemini-flash-latest"
        const val CITY = "Quetta"
        const val SYSTEM = "You are JARVIS, a witty, polite British AI butler like in Iron Man. Address the user as sir. You are speaking aloud, so reply in at most two short sentences with no lists, no markdown and no emojis."
    }

    private lateinit var logView: TextView
    private lateinit var scroll: ScrollView
    private lateinit var talkBtn: Button
    private lateinit var keyInput: EditText
    private lateinit var alwaysSwitch: Switch

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var alwaysListen = false
    private var skipWake = false
    private val chat = mutableListOf<Pair<String, String>>()

    private val prefs by lazy { getSharedPreferences("jarvis", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.BLACK)
        root.setPadding(32, 64, 32, 32)

        val title = TextView(this)
        title.text = "J.A.R.V.I.S."
        title.textSize = 30f
        title.setTextColor(Color.parseColor("#00E5FF"))
        title.gravity = Gravity.CENTER
        root.addView(title)

        keyInput = EditText(this)
        keyInput.hint = "Paste Gemini API key here"
        keyInput.setTextColor(Color.WHITE)
        keyInput.setHintTextColor(Color.GRAY)
        keyInput.setSingleLine(true)
        keyInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        keyInput.setText(prefs.getString("key", ""))
        root.addView(keyInput)

        val saveBtn = Button(this)
        saveBtn.text = "Save key"
        saveBtn.setOnClickListener {
            prefs.edit().putString("key", keyInput.text.toString().trim()).apply()
            log("Key saved.")
        }
        root.addView(saveBtn)

        alwaysSwitch = Switch(this)
        alwaysSwitch.text = "Always listen (say: Jarvis ...)"
        alwaysSwitch.setTextColor(Color.WHITE)
        alwaysSwitch.setOnCheckedChangeListener { _, checked ->
            alwaysListen = checked
            if (checked) startListening() else stopListening()
        }
        root.addView(alwaysSwitch)

        logView = TextView(this)
        logView.setTextColor(Color.WHITE)
        logView.textSize = 16f
        logView.text = "Say: open WhatsApp, flashlight on, call Ali, set alarm for 6 30 am, weather, or ask anything.\n\n"
        scroll = ScrollView(this)
        scroll.addView(logView)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        talkBtn = Button(this)
        talkBtn.text = "TAP TO TALK"
        talkBtn.textSize = 20f
        talkBtn.setOnClickListener { tapToTalk() }
        root.addView(talkBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 220))

        setContentView(root)

        tts = TextToSpeech(this, this)
        requestPermissions(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.CALL_PHONE
            ), 1
        )
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.setLanguage(Locale.UK)
            tts?.setPitch(0.8f)
            tts?.setSpeechRate(0.95f)
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    handler.post { afterSpeak() }
                }
                override fun onError(utteranceId: String?) {
                    handler.post { afterSpeak() }
                }
            })
            ttsReady = true
            speak("Jarvis online. At your service, sir.")
        } else {
            log("Text to speech failed to start. Check Settings, Text-to-speech.")
        }
    }

    private fun log(line: String) {
        runOnUiThread {
            logView.append(line + "\n\n")
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun speak(text: String) {
        log("JARVIS: $text")
        recognizer?.cancel()
        if (ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
        } else {
            afterSpeak()
        }
    }

    private fun afterSpeak() {
        if (alwaysListen) {
            handler.postDelayed({ startListening() }, 500)
        }
    }

    private fun tapToTalk() {
        skipWake = true
        recognizer?.cancel()
        startListening()
    }

    private fun stopListening() {
        recognizer?.cancel()
        talkBtn.text = "TAP TO TALK"
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onError(error: Int) {
            talkBtn.text = "TAP TO TALK"
            if (error == 9) {
                log("Microphone permission missing. Allow it in Settings, Apps, Jarvis.")
                return
            }
            if (alwaysListen) {
                handler.postDelayed({ startListening() }, 700)
            }
        }

        override fun onResults(results: Bundle?) {
            val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = list?.firstOrNull()?.lowercase(Locale.ROOT) ?: ""
            handleSpeech(text)
        }
    }

    private fun startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            log("Speech recognition is not available. Install or update the Google app.")
            return
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer?.setRecognitionListener(listener)
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        talkBtn.text = "LISTENING..."
        recognizer?.startListening(intent)
    }

    private fun handleSpeech(raw: String) {
        talkBtn.text = "TAP TO TALK"
        val needWake = alwaysListen && !skipWake
        skipWake = false
        var cmd = raw.trim()

        if (needWake) {
            if (!cmd.contains("jarvis")) {
                if (alwaysListen) startListening()
                return
            }
            cmd = cmd.substringAfter("jarvis").trim(' ', ',')
            if (cmd.isEmpty()) {
                skipWake = true
                speak("Yes, sir?")
                return
            }
        }
        if (cmd.isEmpty()) {
            if (alwaysListen) startListening()
            return
        }

        log("YOU: $cmd")
        val command = cmd

        if (has(command, "go to sleep", "goodbye", "shut down")) {
            alwaysSwitch.isChecked = false
            speak("Goodbye, sir.")
            return
        }

        Thread {
            val reply = try {
                runCommand(command)
            } catch (e: Exception) {
                "Something went wrong, sir."
            }
            runOnUiThread { speak(reply) }
        }.start()
    }

    private fun has(c: String, vararg words: String): Boolean = words.any { c.contains(it) }

    private fun launch(i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }

    private fun runCommand(c: String): String {
        // Flashlight
        if (has(c, "flashlight", "torch")) {
            return setTorch(!c.contains("off"))
        }
        // Wi-Fi (Android blocks silent toggling, so open the panel)
        if (has(c, "wifi", "wi-fi", "wi fi") && has(c, "turn", "switch", "enable", "disable", "open", "connect")) {
            val i = if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY) else Intent(Settings.ACTION_WIFI_SETTINGS)
            launch(i)
            return "Android does not let apps flip Wi-Fi directly, sir. I opened the Wi-Fi panel for you."
        }
        if (c.contains("bluetooth") && has(c, "turn", "switch", "enable", "disable", "open")) {
            launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            return "Opening Bluetooth settings, sir."
        }
        if (c.contains("airplane") || c.contains("flight mode")) {
            launch(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
            return "Opening airplane mode settings, sir."
        }
        // Volume
        if (has(c, "volume up", "louder")) {
            adjustVolume(AudioManager.ADJUST_RAISE)
            return "Volume increased, sir."
        }
        if (has(c, "volume down", "quieter", "lower the volume")) {
            adjustVolume(AudioManager.ADJUST_LOWER)
            return "Volume lowered, sir."
        }
        // Battery, time, date
        if (c.contains("battery")) {
            val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            return "Battery is at $pct percent, sir."
        }
        if (has(c, "what time", "the time")) {
            return "It is " + SimpleDateFormat("h:mm a", Locale.UK).format(Date()) + ", sir."
        }
        if (has(c, "what day", "the date", "today's date")) {
            return "Today is " + SimpleDateFormat("EEEE, d MMMM yyyy", Locale.UK).format(Date()) + ", sir."
        }
        // Weather
        if (c.contains("weather") || c.contains("temperature")) {
            val city = if (c.contains(" in ")) c.substringAfter(" in ").trim() else CITY
            val url = URL("https://wttr.in/" + URLEncoder.encode(city, "UTF-8") + "?format=%C+%t")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            val t = conn.inputStream.bufferedReader().use { it.readText() }.trim()
            return "In $city it is " + t.replace("+", "") + ", sir."
        }
        // Timer
        if (c.contains("timer")) {
            val m = Regex("(\\d+)\\s*(second|minute|hour)").find(c) ?: return "For how long, sir?"
            val n = m.groupValues[1].toInt()
            val mult = when (m.groupValues[2]) {
                "second" -> 1
                "minute" -> 60
                else -> 3600
            }
            val i = Intent(AlarmClock.ACTION_SET_TIMER)
            i.putExtra(AlarmClock.EXTRA_LENGTH, n * mult)
            i.putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            launch(i)
            return "Timer set, sir."
        }
        // Alarm
        if (has(c, "alarm", "wake me")) {
            val cleaned = c.replace(".", "").replace("a m", "am").replace("p m", "pm")
            val m = Regex("(\\d{1,2})(?:[: ](\\d{2}))?\\s*(am|pm)?").find(cleaned) ?: return "For what time, sir?"
            var h = m.groupValues[1].toInt()
            val min = if (m.groupValues[2].isEmpty()) 0 else m.groupValues[2].toInt()
            val ap = m.groupValues[3]
            if (ap == "pm" && h < 12) h += 12
            if (ap == "am" && h == 12) h = 0
            if (h > 23 || min > 59) return "I did not catch that time, sir."
            val i = Intent(AlarmClock.ACTION_SET_ALARM)
            i.putExtra(AlarmClock.EXTRA_HOUR, h)
            i.putExtra(AlarmClock.EXTRA_MINUTES, min)
            i.putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            launch(i)
            return "Alarm set for " + String.format(Locale.UK, "%d:%02d", h, min) + ", sir."
        }
        // Call
        if (c.startsWith("call ")) {
            val target = c.removePrefix("call ").trim()
            val digits = target.replace(" ", "").replace("-", "")
            val number: String
            if (digits.matches(Regex("\\+?\\d{5,}"))) {
                number = digits
            } else {
                number = findNumber(target) ?: return "I could not find $target in your contacts, sir."
            }
            val uri = Uri.parse("tel:" + Uri.encode(number))
            val granted = checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
            launch(Intent(if (granted) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri))
            return "Calling $target, sir."
        }
        // Open an app
        if (c.startsWith("open ") || c.startsWith("launch ")) {
            val name = c.substringAfter(" ").replace(" app", "").replace("the ", "").trim()
            if (name.isBlank()) return "Which app, sir?"
            val pm = packageManager
            val apps = pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
            )
            val match = apps.firstOrNull { it.loadLabel(pm).toString().lowercase() == name }
                ?: apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(name) }
                ?: return "I could not find an app called $name, sir."
            val li = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
                ?: return "I could not open $name, sir."
            launch(li)
            return "Opening " + match.loadLabel(pm) + ", sir."
        }
        // Web search
        if (c.startsWith("search ") || c.startsWith("google ")) {
            val q = c.removePrefix("search for ").removePrefix("search ").removePrefix("google ")
            launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + URLEncoder.encode(q, "UTF-8"))))
            return "Searching for $q, sir."
        }
        // YouTube
        if (c.startsWith("play ")) {
            val q = c.removePrefix("play ").removeSuffix(" on youtube")
            launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + URLEncoder.encode(q, "UTF-8"))))
            return "Playing $q on YouTube, sir."
        }
        // Everything else: ask the AI
        return askGemini(c)
    }

    private fun setTorch(on: Boolean): String {
        val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return "I could not find a flashlight, sir."
        cm.setTorchMode(id, on)
        return if (on) "Flashlight on, sir." else "Flashlight off, sir."
    }

    private fun adjustVolume(direction: Int) {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        for (n in 1..3) {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        }
    }

    private fun findNumber(name: String): String? {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        val phone = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val cursor = contentResolver.query(
            phone,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
            arrayOf("%$name%"),
            null
        )
        cursor?.use {
            if (it.moveToFirst()) return it.getString(0)
        }
        return null
    }

    private fun askGemini(q: String): String {
        val key = prefs.getString("key", "") ?: ""
        if (key.isBlank()) return "Please paste your Gemini key at the top and tap Save, sir."
        chat.add("user" to q)
        while (chat.size > 10) chat.removeAt(0)
        try {
            val contents = JSONArray()
            for ((role, text) in chat) {
                val part = JSONObject().put("text", text)
                contents.put(JSONObject().put("role", role).put("parts", JSONArray().put(part)))
            }
            val sys = JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM)))
            val body = JSONObject()
                .put("system_instruction", sys)
                .put("contents", contents)
                .put("generationConfig", JSONObject().put("maxOutputTokens", 500))

            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-goog-api-key", key)
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val resp = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) {
                log("Gemini error $code: " + resp.take(200))
                chat.removeAt(chat.size - 1)
                return "My servers returned an error, sir."
            }
            val answer = JSONObject(resp)
                .getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
                .getJSONObject(0).getString("text").trim()
            chat.add("model" to answer)
            return answer
        } catch (e: Exception) {
            log("Error: " + e.message)
            if (chat.isNotEmpty() && chat.last().first == "user") chat.removeAt(chat.size - 1)
            return "I am having trouble reaching my servers, sir."
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        recognizer?.destroy()
        tts?.shutdown()
    }
}
