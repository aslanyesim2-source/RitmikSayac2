package com.ritmik.sayac

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.ritmik.sayac.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var count = 0
    private var matchMode = MatchMode.EXACT
    private var language = "tr-TR"
    private val handler = Handler(Looper.getMainLooper())
    private val historyList = mutableListOf<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    // Wave bars references
    private val waveBars = mutableListOf<View>()
    private var waveAnimator: ValueAnimator? = null

    enum class MatchMode { EXACT, CONTAINS, FUZZY }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWaveBars()
        setupClickListeners()
        updateUI()
        startIdleWave()
    }

    private fun setupWaveBars() {
        // Wave bars are defined in layout; collect references
        val container = binding.waveformContainer
        for (i in 0 until container.childCount) {
            waveBars.add(container.getChildAt(i))
        }
    }

    private fun setupClickListeners() {
        // Match mode
        binding.btnModeExact.setOnClickListener { setMatchMode(MatchMode.EXACT) }
        binding.btnModeContains.setOnClickListener { setMatchMode(MatchMode.CONTAINS) }
        binding.btnModeFuzzy.setOnClickListener { setMatchMode(MatchMode.FUZZY) }

        // Language
        binding.btnLangTR.setOnClickListener { setLanguage("tr-TR") }
        binding.btnLangEN.setOnClickListener { setLanguage("en-US") }

        // Start/Stop
        binding.btnStart.setOnClickListener {
            if (isListening) stopListening() else startListening()
        }

        // Reset
        binding.btnReset.setOnClickListener { resetCounter() }
    }

    private fun setMatchMode(mode: MatchMode) {
        matchMode = mode
        binding.btnModeExact.isSelected = mode == MatchMode.EXACT
        binding.btnModeContains.isSelected = mode == MatchMode.CONTAINS
        binding.btnModeFuzzy.isSelected = mode == MatchMode.FUZZY
    }

    private fun setLanguage(lang: String) {
        language = lang
        binding.btnLangTR.isSelected = lang == "tr-TR"
        binding.btnLangEN.isSelected = lang == "en-US"
    }

    private fun startListening() {
        val target = binding.etTargetWord.text.toString().trim()
        if (target.isEmpty()) {
            showToast("Lütfen sayılacak kelimeyi girin!")
            return
        }

        if (!checkPermission()) {
            requestPermission()
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            showToast("Bu cihazda ses tanıma desteklenmiyor")
            return
        }

        isListening = true
        updateUI()
        startSpeechRecognition()
        startActiveWave()
    }

    private fun startSpeechRecognition() {
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                handler.post { binding.tvStatus.text = "Dinleniyor..." }
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                // Animate wave bars based on mic level
                val level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                animateWaveBarsRMS(level)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                handler.post { binding.tvStatus.text = "İşleniyor..." }
            }

            override fun onError(error: Int) {
                handler.post {
                    if (isListening) {
                        // Auto-restart on common non-fatal errors
                        handler.postDelayed({ if (isListening) startSpeechRecognition() }, 500)
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                handler.post {
                    matches?.firstOrNull()?.let { text ->
                        processResult(text)
                    }
                    if (isListening) startSpeechRecognition()
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                handler.post {
                    partial?.firstOrNull()?.let { text ->
                        updateTranscript(text, false)
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500)
        }

        speechRecognizer?.startListening(intent)
    }

    private fun stopListening() {
        isListening = false
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        updateUI()
        startIdleWave()
    }

    private fun processResult(text: String) {
        val target = binding.etTargetWord.text.toString().trim()
        updateTranscript(text, true)

        val matched = when (matchMode) {
            MatchMode.EXACT -> {
                val normText = normalize(text)
                val normTarget = normalize(target)
                // Count how many times target appears in text
                countOccurrences(normText, normTarget) > 0
            }
            MatchMode.CONTAINS -> normalize(text).contains(normalize(target))
            MatchMode.FUZZY -> fuzzyMatch(normalize(text), normalize(target))
        }

        if (matched) {
            count++
            bumpCounter()
            addHistory()
        }
    }

    private fun countOccurrences(text: String, target: String): Int {
        if (target.isEmpty()) return 0
        var count = 0
        var idx = text.indexOf(target)
        while (idx != -1) {
            count++
            idx = text.indexOf(target, idx + target.length)
        }
        return count
    }

    private fun fuzzyMatch(text: String, target: String): Boolean {
        val words = text.split("\\s+".toRegex())
        val targetWords = target.split("\\s+".toRegex())
        val windowSize = targetWords.size
        if (windowSize == 0) return false
        val maxDist = maxOf(1, (target.length * 0.3).toInt())
        for (i in 0..words.size - windowSize) {
            val chunk = words.subList(i, i + windowSize).joinToString(" ")
            if (levenshtein(chunk, target) <= maxDist) return true
        }
        return false
    }

    private fun levenshtein(a: String, b: String): Int {
        val m = a.length; val n = b.length
        val dp = Array(m + 1) { i -> IntArray(n + 1) { j -> if (i == 0) j else if (j == 0) i else 0 } }
        for (i in 1..m) for (j in 1..n)
            dp[i][j] = if (a[i-1] == b[j-1]) dp[i-1][j-1]
                       else 1 + minOf(dp[i-1][j], dp[i][j-1], dp[i-1][j-1])
        return dp[m][n]
    }

    private fun normalize(s: String): String {
        return s.lowercase(Locale.getDefault())
            .replace('ğ', 'g').replace('ü', 'u').replace('ş', 's')
            .replace('ı', 'i').replace('ö', 'o').replace('ç', 'c')
            .replace(Regex("[^a-z0-9\\s]"), "").trim()
    }

    private fun bumpCounter() {
        binding.tvCounter.text = count.toString()
        binding.tvWordLabel.text = binding.etTargetWord.text.toString().trim().uppercase()

        // Scale animation on counter
        val scaleX = ObjectAnimator.ofFloat(binding.tvCounter, "scaleX", 1.5f, 1f)
        val scaleY = ObjectAnimator.ofFloat(binding.tvCounter, "scaleY", 1.5f, 1f)
        scaleX.duration = 300
        scaleY.duration = 300
        scaleX.interpolator = OvershootInterpolator()
        scaleY.interpolator = OvershootInterpolator()
        AnimatorSet().apply { playTogether(scaleX, scaleY); start() }

        // Ring pulse
        val ringAlpha = ObjectAnimator.ofFloat(binding.counterRing, "alpha", 0f, 1f, 0f)
        val ringScaleX = ObjectAnimator.ofFloat(binding.counterRing, "scaleX", 0.8f, 1.3f)
        val ringScaleY = ObjectAnimator.ofFloat(binding.counterRing, "scaleY", 0.8f, 1.3f)
        ringAlpha.duration = 400
        ringScaleX.duration = 400
        ringScaleY.duration = 400
        AnimatorSet().apply { playTogether(ringAlpha, ringScaleX, ringScaleY); start() }

        // Color flash on counter
        binding.tvCounter.setTextColor(ContextCompat.getColor(this, R.color.accent))
        handler.postDelayed({
            binding.tvCounter.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        }, 350)
    }

    private fun updateTranscript(text: String, isFinal: Boolean) {
        val target = binding.etTargetWord.text.toString().trim()
        if (target.isEmpty()) {
            binding.tvTranscript.text = text
            return
        }

        val spannable = SpannableString(text)
        val lowerText = text.lowercase(Locale.getDefault())
        val lowerTarget = target.lowercase(Locale.getDefault())
        var startIdx = 0
        while (true) {
            val idx = lowerText.indexOf(lowerTarget, startIdx)
            if (idx == -1) break
            spannable.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(this, R.color.accent)),
                idx, idx + lowerTarget.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            startIdx = idx + lowerTarget.length
        }
        binding.tvTranscript.text = spannable
    }

    private fun addHistory() {
        val timestamp = timeFormat.format(Date())
        historyList.add(0, timestamp)
        if (historyList.size > 30) historyList.removeLast()
        updateHistoryView()
    }

    private fun updateHistoryView() {
        binding.tvHistory.text = historyList.joinToString("  •  ")
    }

    private fun resetCounter() {
        count = 0
        binding.tvCounter.text = "0"
        historyList.clear()
        binding.tvHistory.text = ""
        binding.tvTranscript.text = "Mikrofon başlatıldığında metin burada görünecek..."
    }

    private fun updateUI() {
        binding.btnStart.text = if (isListening) "■  DURDUR" else "▶  BAŞLAT"
        binding.btnStart.isSelected = isListening
        binding.statusDot.isSelected = isListening
        binding.tvStatus.text = if (isListening) "Dinleniyor..." else "Dinleme durduruldu"
        binding.etTargetWord.isEnabled = !isListening
        binding.tvWordLabel.text = if (binding.etTargetWord.text.toString().trim().isNotEmpty())
            binding.etTargetWord.text.toString().trim().uppercase()
        else "—"
    }

    // ---- Wave Animations ----
    private fun startIdleWave() {
        waveAnimator?.cancel()
        waveAnimator = ValueAnimator.ofFloat(0f, (Math.PI * 2).toFloat()).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { anim ->
                val phase = anim.animatedValue as Float
                waveBars.forEachIndexed { i, bar ->
                    val h = 8f + (Math.sin((phase + i * 0.4).toDouble()) * 6).toFloat()
                    bar.layoutParams.height = h.toInt().coerceAtLeast(4).dpToPx()
                    bar.requestLayout()
                }
            }
            start()
        }
    }

    private fun startActiveWave() {
        waveAnimator?.cancel()
    }

    private fun animateWaveBarsRMS(level: Float) {
        handler.post {
            waveBars.forEachIndexed { i, bar ->
                val noise = (Math.random() * 0.3).toFloat()
                val h = (4f + (level + noise) * 40f).coerceIn(4f, 48f)
                bar.layoutParams.height = h.toInt().dpToPx()
                bar.requestLayout()
            }
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    // ---- Permissions ----
    private fun checkPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun requestPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startListening()
            } else {
                showToast("Mikrofon izni gerekli!")
            }
        }
    }

    private fun showToast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        super.onDestroy()
        waveAnimator?.cancel()
        speechRecognizer?.destroy()
    }
}
