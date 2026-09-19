package com.example.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

data class RecordingState(
    val isRecording: Boolean = false,
    val durationSeconds: Int = 0,
    val amplitude: Float = 0f, // 0.0 to 1.0
    val recordedFile: File? = null
)

class AudioRecorderManager(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var timerJob: Job? = null

    private val _recordingState = MutableStateFlow(RecordingState())
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    companion object {
        private const val TAG = "AudioRecorderManager"
    }

    fun startRecording(): Boolean {
        try {
            stopRecording()

            val file = File(context.cacheDir, "recording_${UUID.randomUUID()}.m4a")
            currentOutputFile = file

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            _recordingState.update {
                RecordingState(
                    isRecording = true,
                    durationSeconds = 0,
                    amplitude = 0f,
                    recordedFile = null
                )
            }

            startTimer()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            stopRecording()
            return false
        }
    }

    fun stopRecording(): File? {
        stopTimer()
        var resultFile: File? = null
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            resultFile = currentOutputFile
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping recorder", e)
        }
        mediaRecorder = null

        _recordingState.update {
            it.copy(
                isRecording = false,
                amplitude = 0f,
                recordedFile = resultFile
            )
        }
        return resultFile
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            var seconds = 0
            while (isActive && _recordingState.value.isRecording) {
                delay(100)
                val amp = try {
                    val rawAmp = mediaRecorder?.maxAmplitude ?: 0
                    (rawAmp / 32767f).coerceIn(0f, 1f)
                } catch (e: Exception) {
                    0f
                }
                seconds++
                _recordingState.update {
                    it.copy(
                        durationSeconds = seconds / 10,
                        amplitude = amp
                    )
                }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    /**
     * Creates a high-fidelity synthetic WAV audio sample suitable for testing Cartesia's voice clone API
     * if the user does not want to record live microphone audio.
     */
    fun createSampleWavFile(name: String = "sample_voice"): File {
        val sampleRate = 44100
        val durationSeconds = 6
        val numSamples = sampleRate * durationSeconds
        val file = File(context.cacheDir, "${name}_${UUID.randomUUID()}.wav")

        val byteData = ByteArray(numSamples * 2)
        val buffer = ByteBuffer.wrap(byteData).order(ByteOrder.LITTLE_ENDIAN)

        // Generate dynamic multi-tone voice formant simulation
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val f0 = 150.0 + 30.0 * Math.sin(2.0 * Math.PI * 1.5 * t) // Fundamental pitch modulation
            val f1 = 800.0 // First formant
            val f2 = 1500.0 // Second formant

            val amp = 0.5 * Math.sin(2.0 * Math.PI * f0 * t) +
                    0.25 * Math.sin(2.0 * Math.PI * f1 * t) +
                    0.15 * Math.sin(2.0 * Math.PI * f2 * t)

            val window = (0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / numSamples))
            val sampleVal = (amp * window * 20000).toInt().coerceIn(-32768, 32767).toShort()
            buffer.putShort(sampleVal)
        }

        FileOutputStream(file).use { fos ->
            writeWavHeader(fos, sampleRate, 1, 16, byteData.size)
            fos.write(byteData)
            fos.flush()
        }

        return file
    }

    private fun writeWavHeader(
        out: FileOutputStream,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
        pcmDataLength: Int
    ) {
        val totalDataLen = pcmDataLength + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // SubChunk1Size (16 for PCM)
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // AudioFormat (1 for PCM)
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = (sampleRate shr 8 and 0xff).toByte()
        header[26] = (sampleRate shr 16 and 0xff).toByte()
        header[27] = (sampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = blockAlign.toByte()
        header[33] = 0
        header[34] = bitsPerSample.toByte()
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (pcmDataLength and 0xff).toByte()
        header[41] = (pcmDataLength shr 8 and 0xff).toByte()
        header[42] = (pcmDataLength shr 16 and 0xff).toByte()
        header[43] = (pcmDataLength shr 24 and 0xff).toByte()

        out.write(header, 0, 44)
    }
}
