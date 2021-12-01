package com.lacrima.audioplayer.data

import android.graphics.Bitmap
import android.net.Uri
import androidx.core.net.toUri

data class Song(
    val mediaId: String? = null,
    val songUri: Uri? = null,
    val title: String? = null,
    val artist: String? = null,
    val imageBitmap: Bitmap? = null,
    val duration: Long = 0
)