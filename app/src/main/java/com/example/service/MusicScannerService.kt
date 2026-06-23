package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import com.example.R
import com.example.model.Track
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class MusicScannerService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    companion object {
        private const val TAG = "MusicScannerService"
        const val ACTION_START_SCAN = "com.example.action.START_SCAN"

        private val _scannedTracks = MutableStateFlow<List<Track>?>(null)
        val scannedTracks: StateFlow<List<Track>?> = _scannedTracks.asStateFlow()

        private val _isScanning = MutableStateFlow(false)
        val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

        /**
         * Convenience helper to trigger background scanning service
         */
        fun startMusicScan(context: Context) {
            val intent = Intent(context, MusicScannerService::class.java).apply {
                action = ACTION_START_SCAN
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start MusicScannerService: ${e.message}")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // Unbound service
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_SCAN) {
            performBackgroundScan()
        }
        return START_NOT_STICKY
    }

    private fun performBackgroundScan() {
        if (_isScanning.value) {
            Log.d(TAG, "Scan already in progress. Ignoring request.")
            return
        }

        serviceScope.launch {
            _isScanning.value = true
            Log.d(TAG, "Starting background MediaStore & directory file search Scan...")
            
            // Introduce subtle visual scanning task delay
            delay(1000)

            val tempPlaylist = mutableListOf<Track>()
            val scannedPaths = mutableSetOf<String>()

            // 1. Process MediaStore entries first (broadened selection to support newly downloaded tracks)
            val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA
            )
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%'"

            try {
                contentResolver.query(uri, projection, selection, null, null)?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                    while (cursor.moveToNext()) {
                        val idVal = cursor.getLong(idColumn)
                        val titleVal = cursor.getString(titleColumn) ?: "Unknown Track"
                        val artistVal = cursor.getString(artistColumn) ?: "Unknown Artist"
                        val albumVal = cursor.getString(albumColumn) ?: "Unknown Album"
                        val durationMs = cursor.getInt(durationColumn)
                        val durationSec = durationMs / 1000
                        val dataVal = cursor.getString(dataColumn) ?: ""

                        if (dataVal.isNotEmpty()) {
                            scannedPaths.add(dataVal.lowercase())
                        }

                        val track = Track(
                            id = idVal.toInt(),
                            title = titleVal,
                            artist = artistVal,
                            album = albumVal,
                            durationSeconds = if (durationSec > 0) durationSec else 180,
                            albumArtResId = R.drawable.img_mirage, // Fallback asset
                            lyrics = listOf(
                                "This track was scanned from your local music library.",
                                "Title: $titleVal",
                                "Artist: $artistVal",
                                "Album: $albumVal",
                                "Playing locally via Liquid Glass background service scanner."
                            ),
                            isLiked = false
                        )
                        tempPlaylist.add(track)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying MediaStore in service: ${e.message}", e)
            }

            // 1.5. Query MediaStore.Downloads on Android 10+ (API 29+) to ensure downloaded audio is found
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Log.d(TAG, "Starting special MediaStore.Downloads scan...")
                val downloadsUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                val downloadsProjection = arrayOf(
                    MediaStore.Downloads._ID,
                    MediaStore.Downloads.DISPLAY_NAME,
                    MediaStore.Downloads.MIME_TYPE,
                    MediaStore.Downloads.DURATION,
                    MediaStore.Downloads.DATA
                )
                val downloadsSelection = "${MediaStore.Downloads.MIME_TYPE} LIKE 'audio/%' OR " +
                        "${MediaStore.Downloads.DISPLAY_NAME} LIKE '%.mp3' OR " +
                        "${MediaStore.Downloads.DISPLAY_NAME} LIKE '%.m4a' OR " +
                        "${MediaStore.Downloads.DISPLAY_NAME} LIKE '%.wav' OR " +
                        "${MediaStore.Downloads.DISPLAY_NAME} LIKE '%.flac' OR " +
                        "${MediaStore.Downloads.DISPLAY_NAME} LIKE '%.ogg'"
                try {
                    contentResolver.query(downloadsUri, downloadsProjection, downloadsSelection, null, null)?.use { cursor ->
                        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                        val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.MIME_TYPE)
                        val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATA)
                        val durationColumn = cursor.getColumnIndex(MediaStore.Downloads.DURATION)

                        while (cursor.moveToNext()) {
                            val idVal = cursor.getLong(idColumn)
                            val nameVal = cursor.getString(nameColumn) ?: "Downloaded Track"
                            val mimeVal = cursor.getString(mimeColumn) ?: ""
                            val dataVal = cursor.getString(dataColumn) ?: ""
                            val durationMs = if (durationColumn != -1) cursor.getInt(durationColumn) else 0
                            val durationSec = durationMs / 1000

                            val pathLower = dataVal.lowercase()
                            if (pathLower.isNotEmpty() && scannedPaths.contains(pathLower)) {
                                continue
                            }

                            if (pathLower.isNotEmpty()) {
                                scannedPaths.add(pathLower)
                            }

                            val cleanTitle = nameVal.substringBeforeLast(".")

                            val track = Track(
                                id = idVal.toInt(),
                                title = cleanTitle,
                                artist = "Downloaded Music",
                                album = "Downloads",
                                durationSeconds = if (durationSec > 0) durationSec else 180,
                                albumArtResId = R.drawable.img_mirage,
                                lyrics = listOf(
                                    "This song was retrieved directly from your device Downloads folder.",
                                    "Filename: $nameVal",
                                    "Mime: $mimeVal",
                                    "Path: $dataVal"
                                ),
                                isLiked = false
                            )
                            tempPlaylist.add(track)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error querying MediaStore.Downloads: ${e.message}")
                }
            }

            // 2. Perform direct File System scans in case MediaStore index is stale (common for newly downloaded tracks)
            Log.d(TAG, "Starting direct directory scan in Downloads and Music...")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)

            scanLocalDirectory(downloadsDir, tempPlaylist, scannedPaths)
            scanLocalDirectory(musicDir, tempPlaylist, scannedPaths)

            Log.d(TAG, "Full scan finished. Found total unique tracks: ${tempPlaylist.size}.")
            _scannedTracks.value = tempPlaylist
            _isScanning.value = false
            stopSelf()
        }
    }

    private fun scanLocalDirectory(directory: File, results: MutableList<Track>, scannedPaths: MutableSet<String>) {
        if (!directory.exists() || !directory.isDirectory) return
        val files = directory.listFiles() ?: return

        val retriever = MediaMetadataRetriever()
        for (file in files) {
            if (file.isDirectory) {
                scanLocalDirectory(file, results, scannedPaths)
            } else {
                val absolutePath = file.absolutePath
                val pathLower = absolutePath.lowercase()
                
                // If already found in MediaStore or already processed, skip to avoid duplicates
                if (scannedPaths.contains(pathLower)) {
                    continue
                }

                if (pathLower.endsWith(".mp3") || pathLower.endsWith(".m4a") || 
                    pathLower.endsWith(".wav") || pathLower.endsWith(".ogg") || 
                    pathLower.endsWith(".flac")) {
                    
                    var title = file.nameWithoutExtension
                    var artist = "Unknown Artist"
                    var album = "Unknown Album"
                    var durationSec = 180

                    try {
                        retriever.setDataSource(absolutePath)
                        val extTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                        if (!extTitle.isNullOrEmpty()) {
                            title = extTitle
                        }
                        
                        val extArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                        if (!extArtist.isNullOrEmpty()) {
                            artist = extArtist
                        }
                        
                        val extAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                        if (!extAlbum.isNullOrEmpty()) {
                            album = extAlbum
                        }
                        
                        val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        val durationMs = durationStr?.toIntOrNull()
                        if (durationMs != null && durationMs > 0) {
                            durationSec = durationMs / 1000
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "MediaMetadataRetriever failed for $absolutePath. Using fallback values: ${e.message}")
                    }

                    val uniqueId = absolutePath.hashCode()
                    scannedPaths.add(pathLower)

                    val track = Track(
                        id = uniqueId,
                        title = title,
                        artist = artist,
                        album = album,
                        durationSeconds = if (durationSec > 0) durationSec else 180,
                        albumArtResId = R.drawable.img_mirage,
                        lyrics = listOf(
                            "This track was discovered in your download storage folders.",
                            "Location: $absolutePath",
                            "Title: $title",
                            "Artist: $artist",
                            "Bypassing native MediaStore delay."
                        ),
                        isLiked = false
                    )
                    results.add(track)

                    // Proactively ask Android's scanner to index this file for the future
                    try {
                        MediaScannerConnection.scanFile(this, arrayOf(absolutePath), null) { _, _ -> }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to run target MediaScanner: ${e.message}")
                    }
                }
            }
        }
        try {
            retriever.release()
        } catch (e: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
