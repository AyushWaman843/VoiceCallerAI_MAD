package com.ayush.madv2.ui

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.pow

internal class VoiceCapture(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    var recording by mutableStateOf(false)
        private set
    var samples by mutableStateOf(List(37) { 0f })
        private set
    private var smoothed = 0f

    fun start() {
        cancel()
        val audio = File.createTempFile("voice_prompt_", ".m4a", context.cacheDir)
        @Suppress("DEPRECATION")
        val instance = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        file = audio
        recorder = instance
        try {
            instance.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)
                setAudioEncodingBitRate(96000)
                setOutputFile(audio.absolutePath)
                prepare()
                start()
            }
            samples = List(37) { 0f }
            smoothed = 0f
            recording = true
        } catch (error: Exception) { cancel(); throw error }
    }

    fun sample() {
        val peak = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
        val normalized = ((peak / 32767f - .015f).coerceAtLeast(0f) / .985f).pow(.45f)
        smoothed = .55f * smoothed + .45f * normalized
        samples = samples.drop(1) + smoothed
    }

    fun finish(): File? {
        val audio = file
        return try {
            recorder?.stop()
            audio
        } catch (error: Exception) {
            audio?.delete()
            null
        } finally {
            recorder?.release()
            recorder = null
            file = null
            recording = false
        }
    }

    fun cancel() {
        runCatching { recorder?.reset() }
        runCatching { recorder?.release() }
        recorder = null
        file?.delete()
        file = null
        recording = false
    }
}

@Composable
internal fun rememberVoiceCapture(onInterrupted: () -> Unit): VoiceCapture {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val capture = remember { VoiceCapture(context.applicationContext) }
    val interrupted by rememberUpdatedState(onInterrupted)
    LaunchedEffect(capture.recording) {
        while (capture.recording) { capture.sample(); delay(80) }
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && capture.recording) {
                capture.cancel()
                interrupted()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            capture.cancel()
            interrupted()
        }
    }
    return capture
}
