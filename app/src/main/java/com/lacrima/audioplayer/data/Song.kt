package com.lacrima.audioplayer.data

data class Song(
    val mediaId: String = "",
    val songUrl: String = "",
    val title: String = "",
    val artist: String = "",
    val imageUrl: String = "",
    val duration: Long = 0
)