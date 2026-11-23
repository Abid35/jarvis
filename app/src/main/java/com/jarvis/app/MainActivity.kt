   package com.jarvis.app

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
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
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ---------------------------------------------------------------------------
// Arc reactor animation
// ---------------------------------------------------------------------------
class ArcReactorView(context: Context) : View(context) {

    enum class Mode { IDLE, LISTENING, THINKING, SPEAKING }

    var mode: Mode = Mode.IDLE

    private var angle = 0f
    private var phase = 0f
    private var last = System.nanoTime()

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val tri = Path()

    private val cyan = Color.parseColor("#00E5FF")
    private val white = Color.parseColor("#E8FFFF")

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        last = System.nanoTime()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.nanoTime()
        var dt = (now - last) / 1_000_000_000f
        last = now
        if (dt > 0.1f) dt = 0.1f

        val speed = when (mode) {
            Mode.IDLE -> 18f
            Mode.LISTENING -> 55f
            Mode.THINKING -> 200f
            Mode.SPEAKING -> 90f
        }
        val rate = when (mode) {
            Mode.IDLE -> 1.5f
            Mode.LISTENING -> 3f
            Mode.THINKING -> 5f
            Mode.SPEAKING -> 7f
        }
        val amp = when (mode) {
            Mode.IDLE -> 0.05f
            Mode.LISTENING -> 0.10f
            Mode.THINKING -> 0.12f
            Mode.SPEAKING -> 0.25f
        }
        angle = (angle + speed * dt) % 360f
        phase += rate * dt
        val glow = 1f + amp * sin(phase)

        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f * 0.95f
        val d = resources.displayMetrics.density

        // soft outer glow
        val glowAlpha = (110 * glow).toInt().coerceIn(0, 255)
        fill.style = Paint.Style.FILL
        fill.shader = RadialGradient(
            cx, cy, r,
            intArrayOf(Color.argb(glowAlpha, 0, 229, 255), Color.argb(0, 0, 229, 255)),
            floatArrayOf(0.35f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r, fill)
        fill.shader = null

        // outer thin ring
        stroke.style = Paint.Style.STROKE
        stroke.color = cyan
        stroke.alpha = 200
        stroke.strokeWidth = 2f * d
        canvas.drawCircle(cx, cy, r * 0.97f, stroke)

        // rotating segmented ring
        stroke.alpha = 230
        stroke.strokeWidth = 9f * d
        stroke.strokeCap = Paint.Cap.BUTT
        val rs = r * 0.86f
        rect.set(cx - rs, cy - rs, cx + rs, cy + rs)
        canvas.save()
        canvas.rotate(angle, cx, cy)
        for (i in 0 until 12) {
            canvas.drawArc(rect, i * 30f, 20f, false, stroke)
        }
        canvas.restore()

        // counter-rotating ring
        stroke.alpha = 160
        stroke.strokeWidth = 3f * d
        val rc = r * 0.74f
        rect.set(cx - rc, cy - rc, cx + rc, cy + rc)
        canvas.save()
        canvas.rotate(-angle * 1.6f, cx, cy)
        for (i in 0 until 3) {
            canvas.drawArc(rect, i * 120f, 80f, false, stroke)
        }
        canvas.restore()

        // coil blocks
        fill.style = Paint.Style.FILL
        fill.color = cyan
        for (i in 0 until 10) {
            fill.alpha = (150 * glow).toInt().coerceIn(0, 255)
            canvas.save()
            canvas.rotate(i * 36f, cx, cy)
            rect.set(cx - r * 0.07f, cy - r * 0.64f, cx + r * 0.07f, cy - r * 0.50f)
            canvas.drawRoundRect(rect, 4f * d, 4f * d, fill)
            canvas.restore()
        }

        // inner ring
        stroke.color = cyan
        stroke.alpha = 255
        stroke.strokeWidth = 2f * d
        canvas.drawCircle(cx, cy, r * 0.44f, stroke)

        // triangle
        tri.reset()
        val tr = r * 0.30f * glow
        for (i in 0 until 3) {
            val a = Math.toRadians((-90 + i * 120).toDouble())
            val x = cx + (tr * cos(a)).toFloat()
            val y = cy + (tr * sin(a)).toFloat()
            if (i == 0) tri.moveTo(x, y) else tri.lineTo(x, y)
        }
        tri.close()
        fill.color = cyan
        fill.alpha = 70
        canvas.drawPath(tri, fill)
        stroke.color = white
        stroke.strokeWidth = 2.5f * d
        canvas.drawPath(tri, stroke)

        // glowing core
        val cr = r * 0.20f * glow
        fill.shader = RadialGradient(
            cx, cy, cr,
            intArrayOf(Color.WHITE, cyan, Color.argb(0, 0, 229, 255)),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, cr, fill)
        fill.shader = null

        postInvalidateOnAnimation()
    }
}

// ---------------------------------------------------------------------------
// Accessibility service (Home, Back, Lock, Screenshot, Power menu)
// ---------------------------------------------------------------------------
class JarvisAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: JarvisAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}
}

// ---------------------------------------------------------------------------
// Main screen
// ---------------------------------------------------------------------------
class MainActivity : Activity(), TextToSpeech.OnInitListener {

    companion object {
        val MODELS = arrayOf("gemini-flash-lite-latest", "gemini-flash-latest", "gemini-2.5-flash")
        val WAKE_WORDS = arrayOf("jarvis", "jarvas", "jarves", "jervis")
        const val DEFAULT_CITY = "Quetta"
        const val SYSTEM = "You are JARVIS, a brilliant, friendly AI assistant in the style of ChatGPT, with a light touch of polite British butler wit from Iron Man. Give complete, well-explained, genuinely helpful answers: be as detailed as the question deserves, explain your reasoning, and give examples or step-by-step instructions when they help. Write in clear plain paragraphs and simple numbered steps. Do not use markdown symbols, tables or emojis because your reply is also read aloud. For coding or maths questions, explain in words. Use Google Search when you need current facts. Address the user as sir only occasionally."
    }

    private lateinit var chatBox: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var keyInput: EditText
    private lateinit var cityInput: EditText
    private lateinit var settingsPanel: LinearLayout
    private lateinit var wakeSwitch: Switch
    private lateinit var statusText: TextView
    private lateinit var reactor: ArcReactorView

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var wakeEnabled = true
    private var inForeground = false
    private var listening = false
    private var speaking = false
    private var awakeUntil = 0L
    private val chat = mutableListOf<Pair<String, String>>()

    private val prefs by lazy { getSharedPreferences("jarvis", Context.MODE_PRIVATE) }

    private val knownApps = mapOf(
        "facebook" to "com.facebook.katana",
        "messenger" to "com.facebook.orca",
        "whatsapp" to "com.whatsapp",
        "youtube" to "com.google.android.youtube",
        "instagram" to "com.instagram.android",
        "chrome" to "com.android.chrome",
        "telegram" to "org.telegram.messenger",
        "snapchat" to "com.snapchat.android",
        "spotify" to "com.spotify.music",
        "gmail" to "com.google.android.gm",
        "maps" to "com.google.android.apps.maps",
        "playstore" to "com.android.vending",
        "tiktok" to "com.zhiliaoapp.musically",
        "twitter" to "com.twitter.android"
    )

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun styleButton(b: Button) {
        val bg = GradientDrawable()
        bg.cornerRadius = dp(28).toFloat()
        bg.setColor(Color.parseColor("#1500E5FF"))
        bg.setStroke(dp(2), Color.parseColor("#00E5FF"))
        b.background = bg
        b.setTextColor(Color.parseColor("#00E5FF"))
    }

    private fun isAwake(): Boolean = System.currentTimeMillis() < awakeUntil

    private fun keepAwake(ms: Long) {
        awakeUntil = System.currentTimeMillis() + ms
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val match = LinearLayout.LayoutParams.MATCH_PARENT
        val wrap = LinearLayout.LayoutParams.WRAP_CONTENT

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#02060C"), Color.parseColor("#0B1E33"))
        )
        root.setPadding(dp(16), dp(36), dp(16), dp(16))

        // top bar
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL

        val title = TextView(this)
        title.text = "J.A.R.V.I.S."
        title.textSize = 24f
        title.letterSpacing = 0.25f
        title.setTextColor(Color.parseColor("#00E5FF"))
        top.addView(title, LinearLayout.LayoutParams(0, wrap, 1f))

        val settingsBtn = Button(this)
        settingsBtn.text = "SETTINGS"
        settingsBtn.textSize = 12f
        styleButton(settingsBtn)
        settingsBtn.setPadding(dp(14), 0, dp(14), 0)
        settingsBtn.setOnClickListener {
            settingsPanel.visibility = if (settingsPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        top.addView(settingsBtn, LinearLayout.LayoutParams(wrap, dp(40)))
        root.addView(top, LinearLayout.LayoutParams(match, wrap))

        // status
        statusText = TextView(this)
        statusText.text = "STARTING"
        statusText.textSize = 12f
        statusText.letterSpacing = 0.3f
        statusText.gravity = Gravity.CENTER
        statusText.setTextColor(Color.parseColor("#7FF3FF"))
        root.addView(statusText, LinearLayout.LayoutParams(match, wrap))

        // arc reactor (tap it to wake me or to make me stop talking)
        reactor = ArcReactorView(this)
        reactor.setOnClickListener { reactorTapped() }
        root.addView(reactor, LinearLayout.LayoutParams(match, dp(240)))

        // settings panel (shown only until a key is saved)
        settingsPanel = LinearLayout(this)
        settingsPanel.orientation = LinearLayout.VERTICAL
        settingsPanel.setPadding(0, dp(4), 0, dp(8))

        val keyLabel = TextView(this)
        keyLabel.text = "GEMINI API KEY"
        keyLabel.textSize = 11f
        keyLabel.setTextColor(Color.GRAY)
        settingsPanel.addView(keyLabel)

        keyInput = EditText(this)
        keyInput.hint = "Paste key here"
        keyInput.setTextColor(Color.WHITE)
        keyInput.setHintTextColor(Color.GRAY)
        keyInput.setSingleLine(true)
        keyInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        keyInput.setText(prefs.getString("key", ""))
        settingsPanel.addView(keyInput)

        val cityLabel = TextView(this)
        cityLabel.text = "CITY FOR WEATHER"
        cityLabel.textSize = 11f
        cityLabel.setTextColor(Color.GRAY)
        settingsPanel.addView(cityLabel)

        cityInput = EditText(this)
        cityInput.setTextColor(Color.WHITE)
        cityInput.setSingleLine(true)
        cityInput.setText(prefs.getString("city", DEFAULT_CITY))
        settingsPanel.addView(cityInput)

        wakeSwitch = Switch(this)
        wakeSwitch.text = "Listen for the word Jarvis"
        wakeSwitch.isChecked = true
        wakeSwitch.setTextColor(Color.parseColor("#C8D6E5"))
        wakeSwitch.setOnCheckedChangeListener { _, checked ->
            wakeEnabled = checked
            if (checked) startListening() else stopListening()
        }
        settingsPanel.addView(wakeSwitch, LinearLayout.LayoutParams(match, dp(48)))

        val saveBtn = Button(this)
        saveBtn.text = "SAVE"
        styleButton(saveBtn)
        saveBtn.setOnClickListener { saveSettings() }
        settingsPanel.addView(saveBtn, LinearLayout.LayoutParams(match, dp(48)))

        val savedKey = prefs.getString("key", "") ?: ""
        settingsPanel.visibility = if (savedKey.isBlank()) View.VISIBLE else View.GONE
        root.addView(settingsPanel, LinearLayout.LayoutParams(match, wrap))

        // chat
        chatBox = LinearLayout(this)
        chatBox.orientation = LinearLayout.VERTICAL
        scroll = ScrollView(this)
        scroll.addView(chatBox)
        root.addView(scroll, LinearLayout.LayoutParams(match, 0, 1f))

        setContentView(root)

        addMessage("Say \"Jarvis\" to wake me, then ask anything: open Facebook, flashlight on, call Ali, set alarm for 6 30 am, lock the phone, or any question. Tap the reactor to wake me, or to make me stop talking.", 2)

        tts = TextToSpeech(this, this)
        requestPermissions(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.CALL_PHONE
            ), 1
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            if (wakeEnabled && inForeground) startListening()
        } else {
            addMessage("Please allow the microphone for Jarvis in Settings, Apps, Jarvis, Permissions.", 2)
        }
    }

    override fun onResume() {
        super.onResume()
        inForeground = true
        if (wakeEnabled && !speaking) startListening()
    }

    override fun onPause() {
        super.onPause()
        inForeground = false
        cancelListening()
        tts?.stop()
        speaking = false
        setMode(ArcReactorView.Mode.IDLE, "PAUSED")
    }

    private fun saveSettings() {
        val k = keyInput.text.toString().trim()
        val c = cityInput.text.toString().trim()
        val e = prefs.edit()
        if (k.isNotEmpty()) e.putString("key", k)
        e.putString("city", if (c.isEmpty()) DEFAULT_CITY else c)
        e.apply()
        settingsPanel.visibility = View.GONE
        addMessage("Settings saved, sir.", 2)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.setLanguage(Locale.UK)
            tts?.setPitch(0.8f)
            tts?.setSpeechRate(0.97f)
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    if (utteranceId == "jarvis_last") handler.post { afterSpeak() }
                }
                override fun onError(utteranceId: String?) {
                    if (utteranceId == "jarvis_last") handler.post { afterSpeak() }
                }
            })
            ttsReady = true
            speak("Jarvis online. Say my name when you need me, sir.")
        } else {
            addMessage("Text to speech failed to start. Check Settings, Text-to-speech.", 2)
        }
    }

    // kind: 0 = you, 1 = Jarvis, 2 = system note
    private fun addMessage(text: String, kind: Int) {
        runOnUiThread {
            val tv = TextView(this)
            tv.text = text
            tv.textSize = 15f
            tv.setTextIsSelectable(true)
            tv.setPadding(dp(12), dp(8), dp(12), dp(8))
            val bg = GradientDrawable()
            bg.cornerRadius = dp(14).toFloat()
            when (kind) {
                0 -> {
                    tv.setTextColor(Color.parseColor("#E8EEF5"))
                    bg.setColor(Color.parseColor("#22FFFFFF"))
                }
                1 -> {
                    tv.setTextColor(Color.parseColor("#00E5FF"))
                    bg.setColor(Color.parseColor("#2200E5FF"))
                }
                else -> {
                    tv.setTextColor(Color.parseColor("#8FA3B8"))
                    bg.setColor(Color.parseColor("#11FFFFFF"))
                }
            }
            tv.background = bg
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, dp(4), 0, dp(4))
            lp.gravity = if (kind == 0) Gravity.END else Gravity.START
            chatBox.addView(tv, lp)
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun setMode(m: ArcReactorView.Mode, label: String) {
        runOnUiThread {
            reactor.mode = m
            statusText.text = label
        }
    }

    // Tap the reactor: wake me, or interrupt me while I am talking.
    private fun reactorTapped() {
        if (!wakeEnabled) wakeSwitch.isChecked = true
        tts?.stop()
        speaking = false
        keepAwake(15000)
        cancelListening()
        startListening()
    }

    private fun prettify(t: String): String {
        return t.replace(Regex("(?m)^\\s*[*-]\\s+"), "\u2022 ")
            .replace(Regex("(?m)^#+\\s*"), "")
            .replace("**", "")
            .replace("__", "")
            .replace("`", "")
            .replace("*", "")
            .trim()
    }

    private fun splitForSpeech(text: String): List<String> {
        val max = 3000
        val out = mutableListOf<String>()
        var rest = text
        while (rest.length > max) {
            var cut = rest.lastIndexOf(". ", max)
            if (cut < 1000) cut = max else cut += 1
            out.add(rest.substring(0, cut).trim())
            rest = rest.substring(cut).trim()
        }
        if (rest.isNotEmpty()) out.add(rest)
        return out
    }

    private fun speak(text: String) {
        val shown = prettify(text)
        addMessage(shown, 1)
        val spoken = shown.replace("\u2022", "").replace("\n", " ").replace(Regex("\\s+"), " ").trim()
        cancelListening()
        speaking = true
        setMode(ArcReactorView.Mode.SPEAKING, "SPEAKING")
        if (!ttsReady || spoken.isEmpty()) {
            afterSpeak()
            return
        }
        val chunks = splitForSpeech(spoken)
        for ((i, part) in chunks.withIndex()) {
            val id = if (i == chunks.size - 1) "jarvis_last" else "jarvis_$i"
            val queueMode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts?.speak(part, queueMode, null, id)
        }
    }

    private fun afterSpeak() {
        speaking = false
        keepAwake(12000)
        if (wakeEnabled && inForeground) {
            handler.postDelayed({ startListening() }, 400)
        } else if (!wakeEnabled) {
            setMode(ArcReactorView.Mode.IDLE, "PAUSED - TAP THE REACTOR TO WAKE ME")
        } else {
            setMode(ArcReactorView.Mode.IDLE, "PAUSED")
        }
    }

    private fun cancelListening() {
        listening = false
        recognizer?.cancel()
    }

    private fun stopListening() {
        cancelListening()
        setMode(ArcReactorView.Mode.IDLE, "PAUSED - TAP THE REACTOR TO WAKE ME")
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
            listening = false
            if (error == SpeechRecognizer.ERROR_CLIENT) return
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                setMode(ArcReactorView.Mode.IDLE, "NO MICROPHONE ACCESS")
                addMessage("The Google speech service has no microphone permission. Allow it in Settings, Apps, Google, Permissions, Microphone.", 2)
                return
            }
            if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                recognizer?.destroy()
                recognizer = null
            }
            val delay = if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) 400L else 2500L
            handler.postDelayed({ startListening() }, delay)
        }

        override fun onResults(results: Bundle?) {
            listening = false
            val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = list?.firstOrNull()?.lowercase(Locale.ROOT) ?: ""
            handleSpeech(text)
        }
    }

    private fun startListening() {
        if (listening || speaking || !inForeground || !wakeEnabled) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            addMessage("Speech recognition is not available. Install or update the Google app.", 2)
            return
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer?.setRecognitionListener(listener)
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        listening = true
        if (isAwake()) {
            setMode(ArcReactorView.Mode.LISTENING, "LISTENING")
        } else {
            setMode(ArcReactorView.Mode.IDLE, "SAY \"JARVIS\" TO WAKE ME")
        }
        recognizer?.startListening(intent)
    }

    private fun handleSpeech(raw: String) {
        var cmd = raw.trim()
        val heardWake = WAKE_WORDS.firstOrNull { cmd.contains(it) }

        if (heardWake != null) {
            cmd = cmd.substringAfter(heardWake).trim(' ', ',', '.')
            if (cmd.isEmpty()) {
                keepAwake(15000)
                speak("Yes, sir?")
                return
            }
        } else if (!isAwake()) {
            handler.postDelayed({ startListening() }, 300)
            return
        }
        if (cmd.isEmpty()) {
            handler.postDelayed({ startListening() }, 300)
            return
        }

        addMessage(cmd, 0)
        val command = cmd

        if (has(command, "go to sleep", "goodbye", "stop listening")) {
            wakeSwitch.isChecked = false
            speak("Goodbye, sir. Tap the reactor whenever you need me.")
            return
        }

        setMode(ArcReactorView.Mode.THINKING, "PROCESSING")
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
        runOnUiThread {
            try {
                startActivity(i)
            } catch (e: Exception) {
                addMessage("Could not open that: " + e.message, 2)
            }
        }
    }

    private fun globalAction(action: Int, done: String): String {
        val svc = JarvisAccessibilityService.instance
        if (svc == null) {
            launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return "I need accessibility access for that, sir. Please switch on Jarvis in the list that just opened."
        }
        svc.performGlobalAction(action)
        return done
    }

    private fun runCommand(c: String): String {
        // Flashlight
        if (has(c, "flashlight", "torch")) {
            return setTorch(!c.contains("off"))
        }

        // Accessibility actions
        if (has(c, "go home", "home screen")) {
            return globalAction(AccessibilityService.GLOBAL_ACTION_HOME, "Going home, sir.")
        }
        if (has(c, "go back")) {
            return globalAction(AccessibilityService.GLOBAL_ACTION_BACK, "Going back, sir.")
        }
        if (has(c, "recent apps", "show recents")) {
            return globalAction(AccessibilityService.GLOBAL_ACTION_RECENTS, "Here are your recent apps, sir.")
        }
        if (c.contains("notifications")) {
            return globalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS, "Opening notifications, sir.")
        }
        if (c.contains("quick settings")) {
            return globalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS, "Opening quick settings, sir.")
        }
        if (has(c, "lock the phone", "lock my phone", "lock the screen", "lock screen") && Build.VERSION.SDK_INT >= 28) {
            return globalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN, "Locking the phone, sir.")
        }
        if (c.contains("screenshot") && Build.VERSION.SDK_INT >= 28) {
            return globalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT, "Screenshot taken, sir.")
        }
        if (has(c, "power off", "restart", "reboot", "turn off the phone", "switch off the phone", "shut down the phone")) {
            return globalAction(
                AccessibilityService.GLOBAL_ACTION_POWER_DIALOG,
                "I opened the power menu, sir. Android does not let apps shut the phone down directly, so tap Power off or Restart."
            )
        }

        // Wi-Fi, Bluetooth, airplane (Android blocks silent toggling)
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
            val saved = prefs.getString("city", DEFAULT_CITY) ?: DEFAULT_CITY
            val city = if (c.contains(" in ")) c.substringAfter(" in ").trim() else saved
            try {
                val url = URL("https://wttr.in/" + URLEncoder.encode(city, "UTF-8") + "?format=%C+%t")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 20000
                val t = conn.inputStream.bufferedReader().use { it.readText() }.trim()
                return "In $city it is " + t.replace("+", "") + ", sir."
            } catch (e: Exception) {
                return askGemini(c)
            }
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

        // Open any app
        val openMatch = Regex("(?:^|\\s)(?:open|launch)\\s+(.+)$").find(c)
        if (openMatch != null) {
            val spoken = openMatch.groupValues[1].trim()
            val result = openApp(spoken)
            if (result != null) return result
            if (c.startsWith("open ") || c.startsWith("launch ")) {
                return "I could not find an app called $spoken, sir."
            }
        }

        // Google search in the browser
        if (c.startsWith("search ") || c.startsWith("google ")) {
            val q = c.removePrefix("search for ").removePrefix("search ").removePrefix("google ")
            launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + URLEncoder.encode(q, "UTF-8"))))
            return "Searching Google for $q, sir."
        }

        // YouTube
        if (c.startsWith("play ")) {
            val q = c.removePrefix("play ").removeSuffix(" on youtube")
            launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + URLEncoder.encode(q, "UTF-8"))))
            return "Playing $q on YouTube, sir."
        }

        // Everything else: ask the AI (with live Google Search)
        return askGemini(c)
    }

    private fun norm(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

    private fun openApp(spoken: String): String? {
        val cleaned = Regex("\\b(the|app|application)\\b").replace(spoken, "")
        val name = norm(cleaned)
        if (name.isEmpty()) return null
        val pm = packageManager
        val apps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        )
        var match = apps.firstOrNull { norm(it.loadLabel(pm).toString()) == name }
        if (match == null) {
            match = apps.firstOrNull {
                val l = norm(it.loadLabel(pm).toString())
                l.length >= 3 && (l.contains(name) || name.contains(l))
            }
        }
        if (match != null) {
            val li = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
            if (li != null) {
                launch(li)
                return "Opening " + match.loadLabel(pm) + ", sir."
            }
        }
        val pkg = knownApps[name]
        if (pkg != null) {
            val li = pm.getLaunchIntentForPackage(pkg)
            if (li != null) {
                launch(li)
                return "Opening $spoken, sir."
            }
        }
        return null
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

    // -----------------------------------------------------------------------
    // Gemini
    // -----------------------------------------------------------------------
    private fun request(model: String, key: String, withSearch: Boolean): Pair<Int, String> {
        val contents = JSONArray()
        for ((role, text) in chat) {
            val part = JSONObject().put("text", text)
            contents.put(JSONObject().put("role", role).put("parts", JSONArray().put(part)))
        }
        val sys = JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM)))
        val body = JSONObject()
            .put("system_instruction", sys)
            .put("contents", contents)
            .put("generationConfig", JSONObject().put("maxOutputTokens", 2048))
        if (withSearch) {
            body.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
        }

        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("x-goog-api-key", key)
        conn.connectTimeout = 20000
        conn.readTimeout = 90000
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toString().toByteArray()) }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val resp = stream?.bufferedReader()?.use { it.readText() } ?: ""
        return Pair(code, resp)
    }

    private fun parseAnswer(resp: String): String {
        val parts = JSONObject(resp)
            .getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts")
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            sb.append(parts.getJSONObject(i).optString("text", ""))
        }
        return sb.toString().trim()
    }

    private fun dropLastUser() {
        if (chat.isNotEmpty() && chat[chat.size - 1].first == "user") {
            chat.removeAt(chat.size - 1)
        }
    }

    private fun askGemini(q: String): String {
        val key = prefs.getString("key", "") ?: ""
        if (key.isBlank()) return "Please open Settings and save your Gemini key, sir."
        chat.add("user" to q)
        while (chat.size > 20) chat.removeAt(0)

        var lastErr = ""
        var timeouts = 0
        outer@ for (model in MODELS) {
            for (useSearch in listOf(true, false)) {
                try {
                    val (code, resp) = request(model, key, useSearch)
                    if (code in 200..299) {
                        val answer = parseAnswer(resp)
                        if (answer.isNotEmpty()) {
                            chat.add("model" to answer)
                            return answer
                        }
                        lastErr = "Empty answer from $model"
                        continue
                    }
                    if (resp.contains("API_KEY_INVALID") || resp.contains("API key not valid")) {
                        dropLastUser()
                        return "Your Gemini key was rejected, sir. Please save a new key in Settings."
                    }
                    if (code == 429) {
                        dropLastUser()
                        return "I have reached the free limit for now, sir. Please try again in a little while."
                    }
                    lastErr = "Gemini error $code ($model): " + resp.take(150)
                } catch (e: UnknownHostException) {
                    dropLastUser()
                    return "I cannot reach the internet, sir."
                } catch (e: SocketTimeoutException) {
                    timeouts++
                    lastErr = "Timeout ($model)"
                    if (timeouts >= 2) break@outer
                } catch (e: Exception) {
                    lastErr = "Error ($model): " + e.message
                }
            }
        }
        dropLastUser()
        addMessage(lastErr, 2)
        return "I am having trouble reaching my servers, sir."
    }

    override fun onDestroy() {
        super.onDestroy()
        recognizer?.destroy()
        tts?.shutdown()
    }
}






									   
