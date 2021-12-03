package com.lacrima.audioplayer

import android.content.ContentResolver
import android.content.Context
import android.content.res.Resources
import android.net.Uri
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.util.TypedValue
import android.view.View
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.RawResourceDataSource
import com.lacrima.audioplayer.data.AudioFilesSource
import com.lacrima.audioplayer.data.Song
import timber.log.Timber
import java.io.File

object Util {
    fun getRawUriForMetadataRetriever(context: Context, fileResource: Int): Uri {
        Timber.d("getRawUri returned ${Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + File.pathSeparator +
                "//" + context.packageName + "/raw/" + fileResource)}")
        return Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + File.pathSeparator +
                "//" + context.packageName + "/raw/" + fileResource)
    }

    fun getRawUri(fileResource: Int): Uri {
        return RawResourceDataSource.buildRawResourceUri(fileResource)
    }

    /**
     * Convert dp to pixels
     */
    val Int.toPixels
        get() = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            this.toFloat(),
            Resources.getSystem().displayMetrics
        ).toInt()

    /**
     * Apply window insets to the bottom of the view
     */
    fun setUiWindowInsetsBottom(view: View, bottomPadding: Int = 0) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            v.updatePadding(bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom + bottomPadding)
            insets
        }
    }

    /**
     * Apply window insets to the top of the view
     */
    fun setUiWindowInsetsTop(view: View, topPadding: Int = 0) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            v.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top + topPadding)
            insets
        }
    }
}