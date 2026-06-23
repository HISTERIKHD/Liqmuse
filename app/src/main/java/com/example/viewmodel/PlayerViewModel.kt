package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.model.Track
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import android.content.Context
import android.net.Uri
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import android.util.Log
import com.example.service.MusicScannerService
import com.example.service.PlaybackService
import android.provider.DocumentsContract
import android.content.Intent

data class PlayerUiState(
    val currentTrack: Track,
    val isPlaying: Boolean = false,
    val progressSeconds: Int = 0,
    val isShuffled: Boolean = false,
    val isRepeating: Boolean = false,
    val showLyrics: Boolean = false,
    val isQueueExpanded: Boolean = false,
    val playlist: List<Track> = emptyList(),
    val permissionGranted: Boolean? = null,
    val isScanning: Boolean = false,
    val selectedFolderName: String? = null
)

class PlayerViewModel : ViewModel() {

    private val originalTracks = listOf(
        Track(
            id = 1,
            title = "Mirage",
            artist = "Oshwa",
            durationSeconds = 228, // 3:48
            albumArtResId = R.drawable.img_mirage,
            lyrics = listOf(
                "We float where the silence",
                "kisses the sea",
                "Colors of tomorrow",
                "wash over me",
                "In the echo of everything",
                "I find a place to be"
            ),
            isLiked = false
        ),
        Track(
            id = 2,
            title = "Elysian",
            artist = "Oshwa",
            durationSeconds = 135, // 2:15
            albumArtResId = R.drawable.img_elysian,
            lyrics = listOf(
                "Glow in the valley",
                "Whispers of the sun",
                "Golden rays on silver streams",
                "Where the dreams have begun",
                "Hold on to the horizon",
                "We are finally one"
            ),
            isLiked = true
        ),
        Track(
            id = 3,
            title = "Infinity",
            artist = "Oshwa",
            durationSeconds = 242, // 4:02
            albumArtResId = R.drawable.img_mirage,
            lyrics = listOf(
                "Counting stars in a spiral",
                "Deep into the black",
                "We are light speed traveling",
                "And never looking back",
                "Infinity is calling us",
                "Across the stellar track"
            ),
            isLiked = false
        ),
        Track(
            id = 4,
            title = "Stardust",
            artist = "Oshwa",
            durationSeconds = 190, // 3:10
            albumArtResId = R.drawable.img_elysian,
            lyrics = listOf(
                "We are made of stardust",
                "Burning in the night",
                "Shedding older versions",
                "Stepping into the light",
                "Every atom singing",
                "Shining pure and bright"
            ),
            isLiked = false
        ),
        Track(
            id = 5,
            title = "Dreamer",
            artist = "Oshwa",
            durationSeconds = 174, // 2:54
            albumArtResId = R.drawable.img_mirage,
            lyrics = listOf(
                "Drifting through the nebulas",
                "Dreaming wide awake",
                "Every step we follow",
                "Every path we make",
                "In the cosmic ocean",
                "With every breath we take"
            ),
            isLiked = true
        )
    )

    private var basePlaylist: List<Track> = originalTracks

    private val _uiState = MutableStateFlow(
        PlayerUiState(
            currentTrack = originalTracks[0],
            playlist = originalTracks
        )
    )
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var playbackJob: Job? = null

    private var appContext: Context? = null

    init {
        // Start playback observer loop
        observePlayback()
        observeBackgroundScanner()
        observeServiceControls()
    }

    fun initializeWithContext(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            // Listen changes in uiState to automatically update background service notification
            viewModelScope.launch {
                _uiState.collect { state ->
                    appContext?.let { ctx ->
                        PlaybackService.updateState(ctx, state.currentTrack, state.isPlaying)
                    }
                }
            }
            // Proactively restore previous favorites folder directory if persisted
            loadSavedCustomFolder(context)
        }
    }

    private fun observeServiceControls() {
        viewModelScope.launch {
            PlaybackService.playPauseRequest.collect {
                togglePlayPause()
            }
        }
        viewModelScope.launch {
            PlaybackService.nextRequest.collect {
                nextTrack()
            }
        }
        viewModelScope.launch {
            PlaybackService.prevRequest.collect {
                previousTrack()
            }
        }
    }

    private fun observeBackgroundScanner() {
        viewModelScope.launch {
            MusicScannerService.isScanning.collect { scanning ->
                _uiState.update { it.copy(isScanning = scanning) }
            }
        }
        viewModelScope.launch {
            MusicScannerService.scannedTracks.collect { tracks ->
                if (tracks != null) {
                    val finalTracks = if (tracks.isNotEmpty()) tracks else originalTracks
                    basePlaylist = finalTracks
                    _uiState.update { state ->
                        state.copy(
                            playlist = finalTracks,
                            currentTrack = finalTracks.first()
                        )
                    }
                }
            }
        }
    }

    private fun observePlayback() {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                if (_uiState.value.isPlaying) {
                    _uiState.update { state ->
                        val nextProgress = state.progressSeconds + 1
                        if (nextProgress >= state.currentTrack.durationSeconds) {
                            // Current song ended
                            if (state.isRepeating) {
                                // Repeat active: loop current
                                state.copy(progressSeconds = 0)
                            } else {
                                // Go to next track
                                val nextIndex = getNextTrackIndex(state)
                                val nextTrack = state.playlist[nextIndex]
                                state.copy(
                                    currentTrack = nextTrack,
                                    progressSeconds = 0
                                )
                            }
                        } else {
                            state.copy(progressSeconds = nextProgress)
                        }
                    }
                }
            }
        }
    }

    private fun getNextTrackIndex(state: PlayerUiState): Int {
        val currentIndex = state.playlist.indexOfFirst { it.id == state.currentTrack.id }
        if (currentIndex == -1) return 0
        return (currentIndex + 1) % state.playlist.size
    }

    private fun getPreviousTrackIndex(state: PlayerUiState): Int {
        val currentIndex = state.playlist.indexOfFirst { it.id == state.currentTrack.id }
        if (currentIndex == -1) return 0
        return if (currentIndex == 0) state.playlist.size - 1 else currentIndex - 1
    }

    fun togglePlayPause() {
        _uiState.update { it.copy(isPlaying = !it.isPlaying) }
    }

    fun playTrack(track: Track) {
        _uiState.update {
            it.copy(
                currentTrack = track,
                isPlaying = true,
                progressSeconds = 0
            )
        }
    }

    fun nextTrack() {
        _uiState.update { state ->
            val nextIndex = getNextTrackIndex(state)
            state.copy(
                currentTrack = state.playlist[nextIndex],
                isPlaying = true,
                progressSeconds = 0
            )
        }
    }

    fun previousTrack() {
        _uiState.update { state ->
            val prevIndex = getPreviousTrackIndex(state)
            state.copy(
                currentTrack = state.playlist[prevIndex],
                isPlaying = true,
                progressSeconds = 0
            )
        }
    }

    fun seekTo(seconds: Int) {
        _uiState.update { state ->
            val clampedSeconds = seconds.coerceIn(0, state.currentTrack.durationSeconds)
            state.copy(progressSeconds = clampedSeconds)
        }
    }

    fun toggleShuffle() {
        _uiState.update { state ->
            val newShuffleState = !state.isShuffled
            val newPlaylist = if (newShuffleState) {
                // Shuffle current active basePlaylist but keep currenttrack as first element
                val shuffled = basePlaylist.filter { it.id != state.currentTrack.id }.shuffled()
                listOf(state.currentTrack) + shuffled
            } else {
                // Restore basePlaylist order, maintaining updated isLiked elements
                val currentLikes = state.playlist.associate { it.id to it.isLiked }
                basePlaylist.map { t -> t.copy(isLiked = currentLikes[t.id] ?: t.isLiked) }
            }
            state.copy(
                isShuffled = newShuffleState,
                playlist = newPlaylist
            )
        }
    }

    fun toggleRepeat() {
        _uiState.update { it.copy(isRepeating = !it.isRepeating) }
    }

    fun toggleLike() {
        _uiState.update { state ->
            val updatedTrack = state.currentTrack.copy(isLiked = !state.currentTrack.isLiked)
            val updatedPlaylist = state.playlist.map {
                if (it.id == state.currentTrack.id) updatedTrack else it
            }
            basePlaylist = basePlaylist.map {
                if (it.id == state.currentTrack.id) updatedTrack else it
            }
            state.copy(
                currentTrack = updatedTrack,
                playlist = updatedPlaylist
            )
        }
    }

    fun toggleLikeForTrack(trackId: Int) {
        _uiState.update { state ->
            val updatedPlaylist = state.playlist.map {
                if (it.id == trackId) it.copy(isLiked = !it.isLiked) else it
            }
            val updatedCurrentTrack = if (state.currentTrack.id == trackId) {
                state.currentTrack.copy(isLiked = !state.currentTrack.isLiked)
            } else {
                state.currentTrack
            }
            basePlaylist = basePlaylist.map {
                if (it.id == trackId) it.copy(isLiked = !it.isLiked) else it
            }
            state.copy(
                currentTrack = updatedCurrentTrack,
                playlist = updatedPlaylist
            )
        }
    }

    fun toggleLyrics() {
        _uiState.update { it.copy(showLyrics = !it.showLyrics) }
    }

    fun setQueueExpanded(expanded: Boolean) {
        _uiState.update { it.copy(isQueueExpanded = expanded) }
    }

    fun setPermissionGranted(granted: Boolean) {
        _uiState.update { it.copy(permissionGranted = granted) }
    }

    fun loadLocalAudioFiles(context: Context) {
        MusicScannerService.startMusicScan(context)
    }

    fun importAudioFiles(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val importedTracks = mutableListOf<Track>()
            val retriever = MediaMetadataRetriever()
            
            uris.forEachIndexed { index, uri ->
                try {
                    retriever.setDataSource(context, uri)
                    val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: "Imported Track ${index + 1}"
                    val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
                    val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Unknown Album"
                    val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    val durationMs = durationStr?.toIntOrNull() ?: 180000
                    val durationSec = durationMs / 1000

                    val uniqueId = -(System.currentTimeMillis() % 10000000).toInt() - index

                    importedTracks.add(
                        Track(
                            id = uniqueId,
                            title = title,
                            artist = artist,
                            album = album,
                            durationSeconds = if (durationSec > 0) durationSec else 180,
                            albumArtResId = R.drawable.img_mirage,
                            lyrics = listOf(
                                "This track was imported directly into Liquid Glass.",
                                "Title: $title",
                                "Artist: $artist",
                                "Album: $album",
                                "Playing locally with high-fidelity bypass."
                            ),
                            isLiked = false
                        )
                    )
                } catch (e: Exception) {
                    Log.e("PlayerViewModel", "Error metadata extraction for $uri: ${e.message}")
                }
            }
            try {
                retriever.release()
            } catch (e: Exception) {}

            if (importedTracks.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    val currentList = _uiState.value.playlist.toMutableList()
                    val isShowingDemo = currentList.size == originalTracks.size && currentList.all { demo -> originalTracks.any { orig -> orig.id == demo.id } }
                    
                    val newList = if (isShowingDemo) {
                        importedTracks
                    } else {
                        importedTracks + currentList
                    }
                    basePlaylist = newList
                    _uiState.update { state ->
                        state.copy(
                            playlist = newList,
                            currentTrack = newList.first(),
                            permissionGranted = true
                        )
                    }
                }
            }
        }
    }

    fun scanCustomFolder(context: Context, treeUri: Uri) {
        _uiState.update { it.copy(isScanning = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val importedTracks = mutableListOf<Track>()
            var folderName = "Custom Folder"
            try {
                // Ensure peristable URI access
                try {
                    context.contentResolver.takePersistableUriPermission(
                        treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    Log.w("PlayerViewModel", "No persistable permissions: ${e.message}")
                }

                // Save selected folder to SharedPrefs for absolute persistence
                context.getSharedPreferences("music_player_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("custom_favorites_folder_uri", treeUri.toString())
                    .apply()

                // Resolve user friendly name
                try {
                    val folderDocUri = DocumentsContract.buildDocumentUriUsingTree(
                        treeUri, 
                        DocumentsContract.getTreeDocumentId(treeUri)
                    )
                    context.contentResolver.query(folderDocUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cur ->
                        if (cur.moveToFirst()) {
                            folderName = cur.getString(0) ?: "Custom Folder"
                        }
                    }
                } catch (e: Exception) {}

                val docId = DocumentsContract.getTreeDocumentId(treeUri)
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
                val projection = arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                )

                context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

                    var index = 0
                    while (cursor.moveToNext()) {
                        val childId = cursor.getString(idCol)
                        val childName = cursor.getString(nameCol) ?: "Track ${index + 1}"
                        val mimeType = cursor.getString(mimeCol) ?: ""

                        val isAudio = mimeType.startsWith("audio/") ||
                                childName.endsWith(".mp3", ignoreCase = true) ||
                                childName.endsWith(".m4a", ignoreCase = true) ||
                                childName.endsWith(".wav", ignoreCase = true) ||
                                childName.endsWith(".flac", ignoreCase = true) ||
                                childName.endsWith(".ogg", ignoreCase = true)

                        if (isAudio) {
                            val singleDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                            var title = childName.substringBeforeLast(".")
                            var artist = "Folder Library"
                            var album = folderName
                            var durationSec = 180

                            val retriever = MediaMetadataRetriever()
                            try {
                                retriever.setDataSource(context, singleDocUri)
                                val metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                                if (!metaTitle.isNullOrEmpty()) title = metaTitle
                                val metaArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                                if (!metaArtist.isNullOrEmpty()) artist = metaArtist
                                val metaAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                                if (!metaAlbum.isNullOrEmpty()) album = metaAlbum
                                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toIntOrNull()
                                if (durationMs != null && durationMs > 0) {
                                    durationSec = durationMs / 1000
                                }
                            } catch (e: Exception) {
                                Log.e("PlayerViewModel", "Error fetching track metadata: ${e.message}")
                            } finally {
                                try { retriever.release() } catch (e: Exception) {}
                            }

                            val uniqueId = -(System.currentTimeMillis() % 10000000).toInt() - index
                            importedTracks.add(
                                Track(
                                    id = uniqueId,
                                    title = title,
                                    artist = artist,
                                    album = album,
                                    durationSeconds = durationSec,
                                    albumArtResId = R.drawable.img_elysian, // Fallback premium visual
                                    lyrics = listOf(
                                        "Loaded from folder: $folderName",
                                        "File name: $childName",
                                        "Liquid Glass premium rendering active."
                                    ),
                                    isLiked = true
                                )
                            )
                            index++
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error scanning tree Uri: ${e.message}", e)
            }

            withContext(Dispatchers.Main) {
                if (importedTracks.isNotEmpty()) {
                    basePlaylist = importedTracks
                    _uiState.update { state ->
                        state.copy(
                            playlist = importedTracks,
                            currentTrack = importedTracks.first(),
                            selectedFolderName = folderName,
                            isScanning = false,
                            permissionGranted = true
                        )
                    }
                } else {
                    _uiState.update { state ->
                        state.copy(
                            isScanning = false,
                            selectedFolderName = "$folderName (Empty)"
                        )
                    }
                }
            }
        }
    }

    fun loadSavedCustomFolder(context: Context) {
        val savedUriStr = context.getSharedPreferences("music_player_prefs", Context.MODE_PRIVATE)
            .getString("custom_favorites_folder_uri", null)
        if (savedUriStr != null) {
            try {
                val uri = Uri.parse(savedUriStr)
                scanCustomFolder(context, uri)
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Failed to auto restore custom favorites folder: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        playbackJob?.cancel()
    }
}
