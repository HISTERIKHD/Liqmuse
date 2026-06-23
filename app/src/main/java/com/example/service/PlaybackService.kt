package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

class PlaybackService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private var synthPlayer: SynthPlayer? = null

    companion object {
        private const val TAG = "PlaybackService"
        private const val CHANNEL_ID = "liquid_glass_playback"
        private const val NOTIFICATION_ID = 888

        const val ACTION_START = "com.example.action.START"
        const val ACTION_PLAY = "com.example.action.PLAY"
        const val ACTION_PAUSE = "com.example.action.PAUSE"
        const val ACTION_NEXT = "com.example.action.NEXT"
        const val ACTION_PREV = "com.example.action.PREV"
        const val ACTION_STOP = "com.example.action.STOP"

        // Static events collected by PlayerViewModel to stay in complete synchronized flow
        val playPauseRequest = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val nextRequest = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val prevRequest = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        val activeTrackState = MutableStateFlow<Track?>(null)
        val isPlayingState = MutableStateFlow(false)

        fun updateState(context: Context, track: Track, isPlaying: Boolean) {
            activeTrackState.value = track
            isPlayingState.value = isPlaying

            val intent = Intent(context, PlaybackService::class.java).apply {
                if (isPlaying) {
                    action = ACTION_PLAY
                } else {
                    action = ACTION_PAUSE
                }
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed starting/updating service: ${e.message}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        createNotificationChannel()
        synthPlayer = SynthPlayer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        Log.d(TAG, "Service action received: $action")

        when (action) {
            ACTION_PLAY -> {
                isPlayingState.value = true
                val track = activeTrackState.value
                val freq = getFreqForTrack(track)
                synthPlayer?.start(freq)
                showPlaybackNotification()
            }
            ACTION_PAUSE -> {
                isPlayingState.value = false
                synthPlayer?.stop()
                showPlaybackNotification()
            }
            ACTION_NEXT -> {
                nextRequest.tryEmit(Unit)
            }
            ACTION_PREV -> {
                prevRequest.tryEmit(Unit)
            }
            ACTION_STOP -> {
                synthPlayer?.stop()
                stopForeground(true)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun getFreqForTrack(track: Track?): Double {
        if (track == null) return 432.0
        return when (track.id) {
            1 -> 432.0 // Mirage (Soft purple)
            2 -> 528.0 // Elysian (Transformation gold)
            3 -> 396.0 // Infinity (Starry Indigo/Root)
            4 -> 639.0 // Stardust (Heart rose)
            5 -> 741.0 // Dreamer (Clear mind violet)
            else -> {
                val mathCode = Math.abs((track.title + track.artist).hashCode()) % 300
                (mathCode + 200).toDouble()
            }
        }
    }

    private fun showPlaybackNotification() {
        val currentTrack = activeTrackState.value ?: return
        val isPlaying = isPlayingState.value

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Notification buttons PendingIntents
        val prevIntent = Intent(this, PlaybackService::class.java).apply { this.action = ACTION_PREV }
        val prevPending = PendingIntent.getService(this, 1, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val playIntent = Intent(this, PlaybackService::class.java).apply {
            this.action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
        }
        val playPending = PendingIntent.getService(this, 2, playIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val nextIntent = Intent(this, PlaybackService::class.java).apply { this.action = ACTION_NEXT }
        val nextPending = PendingIntent.getService(this, 3, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val playIconRes = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.img_mirage) // Launcher icon/resource fallback
            .setContentTitle(currentTrack.title)
            .setContentText(currentTrack.artist)
            .setSubText("Liquid Glass Player")
            .setContentIntent(openPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, "Prev", prevPending)
            .addAction(playIconRes, if (isPlaying) "Pause" else "Play", playPending)
            .addAction(android.R.drawable.ic_media_next, "Next", nextPending)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2)
            )
            .setColor(0xFF9A82E3.toInt()) // Sleek brand accent matching color
            .setColorized(true)
            .setOngoing(isPlaying)
            .setSilent(true) // Silent sound so normal beep doesn't play
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Liquid Glass Playback"
            val descriptionText = "Supports real-time background cosmic soundscape playback and notification player controls."
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        synthPlayer?.stop()
        job.cancel()
        Log.d(TAG, "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // Lightweight dynamic soft tone generator to harmonize with theme color frequencies
    private class SynthPlayer {
        private var audioTrack: AudioTrack? = null
        private var isPlaying = false
        private var thread: Thread? = null

        @Synchronized
        fun start(frequency: Double) {
            stop()
            isPlaying = true
            thread = Thread {
                val sampleRate = 22050
                val numSamples = 22050
                val buffer = ShortArray(numSamples)
                
                val minBufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                
                val finalBufferSize = if (minBufferSize > numSamples) minBufferSize else numSamples
                try {
                    audioTrack = AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        finalBufferSize,
                        AudioTrack.MODE_STREAM
                    )
                    
                    audioTrack?.play()
                    
                    var phase = 0.0
                    val phaseIncrement = 2 * Math.PI * frequency / sampleRate
                    
                    while (isPlaying) {
                        for (i in buffer.indices) {
                            // Immersive ambient soundwave, perfectly clean & delightful
                            val primarySin = Math.sin(phase)
                            val subHarmonic = 0.45 * Math.sin(phase * 0.5) // Warm meditation bass depth
                            val shimmer = 0.12 * Math.sin(phase * 2.0) // Galaxy shimmer detail
                            
                            val rawVal = (primarySin + subHarmonic + shimmer) / 1.57
                            // Moderate volume level to make it extremely pleasant, non-obtrusive, and highly luxury
                            val shortVal = (rawVal * Short.MAX_VALUE * 0.45).toInt()
                                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                            
                            buffer[i] = shortVal.toShort()
                            
                            phase += phaseIncrement
                            if (phase > 2 * Math.PI) {
                                phase -= 2 * Math.PI
                            }
                        }
                        audioTrack?.write(buffer, 0, buffer.size)
                    }
                } catch (e: Exception) {
                    Log.e("SynthPlayer", "Error during soft vibration playback: ${e.message}")
                }
            }
            thread?.start()
        }

        @Synchronized
        fun stop() {
            isPlaying = false
            try {
                thread?.join(80)
            } catch (e: Exception) {}
            thread = null
            try {
                audioTrack?.apply {
                    if (state == AudioTrack.STATE_INITIALIZED) {
                        stop()
                        release()
                    }
                }
            } catch (e: Exception) {}
            audioTrack = null
        }
    }
}
