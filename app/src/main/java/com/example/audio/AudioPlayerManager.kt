package com.example.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
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

data class AudioPlaybackState(
    val currentMessageId: String? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0,
    val durationMs: Long = 0,
    val speed: Float = 1.0f
)

class AudioPlayerManager(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var progressJob: Job? = null

    private val _playbackState = MutableStateFlow(AudioPlaybackState())
    val playbackState: StateFlow<AudioPlaybackState> = _playbackState.asStateFlow()

    companion object {
        private const val TAG = "AudioPlayerManager"
    }

    fun play(messageId: String, filePath: String) {
        val file = File(filePath)
        if (!file.exists()) {
            Log.e(TAG, "Audio file not found: $filePath")
            return
        }

        // If already playing this message, toggle pause/play
        if (_playbackState.value.currentMessageId == messageId && mediaPlayer != null) {
            if (_playbackState.value.isPlaying) {
                pause()
            } else {
                resume()
            }
            return
        }

        stop()

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnPreparedListener { mp ->
                    applySpeed(_playbackState.value.speed)
                    mp.start()
                    val duration = mp.duration.toLong()
                    _playbackState.update {
                        it.copy(
                            currentMessageId = messageId,
                            isPlaying = true,
                            currentPositionMs = 0,
                            durationMs = duration
                        )
                    }
                    startProgressTracker()
                }
                setOnCompletionListener {
                    _playbackState.update {
                        it.copy(isPlaying = false, currentPositionMs = 0)
                    }
                    stopProgressTracker()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    stop()
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio playback", e)
            stop()
        }
    }

    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _playbackState.update { state -> state.copy(isPlaying = false) }
                stopProgressTracker()
            }
        }
    }

    fun resume() {
        mediaPlayer?.let {
            it.start()
            _playbackState.update { state -> state.copy(isPlaying = true) }
            startProgressTracker()
        }
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.let {
            val clamped = positionMs.coerceIn(0, it.duration.toLong()).toInt()
            it.seekTo(clamped)
            _playbackState.update { state -> state.copy(currentPositionMs = clamped.toLong()) }
        }
    }

    fun toggleSpeed() {
        val speeds = listOf(1.0f, 1.25f, 1.5f, 2.0f)
        val current = _playbackState.value.speed
        val nextIdx = (speeds.indexOf(current) + 1) % speeds.size
        val nextSpeed = speeds[nextIdx]
        setSpeed(nextSpeed)
    }

    fun setSpeed(speed: Float) {
        _playbackState.update { it.copy(speed = speed) }
        applySpeed(speed)
    }

    private fun applySpeed(speed: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                mediaPlayer?.let {
                    if (it.isPlaying || _playbackState.value.isPlaying) {
                        it.playbackParams = it.playbackParams.setSpeed(speed)
                    } else {
                        it.playbackParams = PlaybackParams().setSpeed(speed)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not set playback speed", e)
            }
        }
    }

    fun stop() {
        stopProgressTracker()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing MediaPlayer", e)
        }
        mediaPlayer = null
        _playbackState.update {
            it.copy(
                currentMessageId = null,
                isPlaying = false,
                currentPositionMs = 0,
                durationMs = 0
            )
        }
    }

    private fun startProgressTracker() {
        stopProgressTracker()
        progressJob = scope.launch {
            while (isActive && _playbackState.value.isPlaying) {
                mediaPlayer?.let { mp ->
                    try {
                        val currentPos = mp.currentPosition.toLong()
                        _playbackState.update { it.copy(currentPositionMs = currentPos) }
                    } catch (e: Exception) {
                        // ignore if in invalid state
                    }
                }
                delay(80)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    fun release() {
        stop()
    }
}
