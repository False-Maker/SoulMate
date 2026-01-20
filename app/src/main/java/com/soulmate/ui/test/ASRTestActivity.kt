package com.soulmate.ui.test

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.soulmate.R
import com.soulmate.data.service.ASRState
import com.soulmate.data.service.AliyunASRService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class ASRTestActivity : ComponentActivity() {

    @Inject
    lateinit var asrService: AliyunASRService

    private lateinit var tvStatus: TextView
    private lateinit var tvPartial: TextView
    private lateinit var tvLogs: TextView

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                appendLog("Microphone permission granted")
            } else {
                appendLog("Microphone permission denied")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_asr_test)

        tvStatus = findViewById(R.id.tvStatus)
        tvPartial = findViewById(R.id.tvPartial)
        tvLogs = findViewById(R.id.tvLogs)

        findViewById<Button>(R.id.btnInit).setOnClickListener {
            checkPermissionAndRun {
                lifecycleScope.launch {
                    appendLog("Initializing ASR Service...")
                    val result = asrService.initialize()
                    appendLog("Initialization result: $result")
                }
            }
        }

        findViewById<Button>(R.id.btnRelease).setOnClickListener {
            asrService.release()
            appendLog("ASR Service released")
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            lifecycleScope.launch {
                appendLog("Starting recognition...")
                val result = asrService.startRecognition()
                appendLog("Start result: $result")
            }
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            asrService.stopRecognition()
            appendLog("Stopping recognition...")
        }

        // Observe State
        lifecycleScope.launch {
            asrService.asrState.collectLatest { state ->
                val statusText = when (state) {
                    is ASRState.Idle -> "Idle"
                    is ASRState.Listening -> "Listening..."
                    is ASRState.Recognizing -> "Recognizing..."
                    is ASRState.Error -> "Error: ${state.message}"
                }
                tvStatus.text = statusText
                appendLog("State changed: $statusText")
            }
        }

        // Observe Partial Results
        lifecycleScope.launch {
            asrService.partialResult.collectLatest { text ->
                tvPartial.text = text
            }
        }

        // Observe Final Results
        lifecycleScope.launch {
            asrService.recognitionResult.collectLatest { text ->
                appendLog("FINAL RESULT: $text")
                tvPartial.text = text // Also verify final result update
            }
        }

        checkPermissionAndRun { }
    }

    private fun checkPermissionAndRun(action: () -> Unit) {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            action()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun appendLog(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val logLine = "$time: $msg\n"
        runOnUiThread {
            tvLogs.append(logLine)
            Log.d("ASRTestActivity", msg)
        }
    }
}
