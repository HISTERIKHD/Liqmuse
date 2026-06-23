package com.example.model

data class Track(
    val id: Int,
    val title: String,
    val artist: String,
    val durationSeconds: Int,
    val albumArtResId: Int,
    val lyrics: List<String>,
    val isLiked: Boolean = false,
    val album: String = "Unknown Album"
) {
    val durationString: String
        get() {
            val minutes = durationSeconds / 60
            val seconds = durationSeconds % 60
            return String.format("%d:%02d", minutes, seconds)
        }
}
