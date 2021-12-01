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

    fun getRawUri(context: Context, fileResource: Int): Uri {
        return RawResourceDataSource.buildRawResourceUri(fileResource)
    }

    /**
     * Returns a Content Uri for the AlbumArtContentProvider
     */
    fun File.asAlbumArtContentUri(): Uri {
        return Uri.Builder()
            .scheme(ContentResolver.SCHEME_CONTENT)
            .authority(AUTHORITY)
            .appendPath(this.path)
            .build()
    }

    inline val MediaMetadataCompat.mediaUri: Uri
        get() = this.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI).toUri()

    /**
     * Extension method for building an [ExtractorMediaSource] from a [MediaMetadataCompat] object.
     *
     * For convenience, place the [MediaDescriptionCompat] into the tag so it can be retrieved later.
     */
    fun MediaMetadataCompat.toMediaSource(dataSourceFactory: DataSource.Factory) =
        ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaUri)

    /**
     * Extension method for building a [ConcatenatingMediaSource] given a [List]
     * of [MediaMetadataCompat] objects.
     */
    fun List<MediaMetadataCompat>.toMediaSource(
        dataSourceFactory: DataSource.Factory
    ): ConcatenatingMediaSource {

        val concatenatingMediaSource = ConcatenatingMediaSource()
        forEach {
            concatenatingMediaSource.addMediaSource(it.toMediaSource(dataSourceFactory))
        }
        return concatenatingMediaSource
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

//    fun getAudioFilesObjects(context: Context): List<Song> {
//        val audioFilesObjects = mutableListOf<Song>()
//        AudioFilesSource.getAudioFilesMetadata(context).forEach { audioFileMeta ->
//            audioFilesObjects.add(
//                Song(
//                    mediaId = audioFileMeta.description.mediaId,
//                    songUri = audioFileMeta.description.mediaUri,
//                    title = audioFileMeta.description.title.toString(),
//                    // TODO: Subtitle is probably not an artist!
//                    artist = audioFileMeta.description.subtitle.toString(),
//                    //artist = audioFileMeta.getString(MediaMetadataCompat.METADATA_KEY_ARTIST),
//                    imageBitmap = audioFileMeta.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)
//                )
//            )
//        }
//        Timber.d("getAudioFilesObjects returned $audioFilesObjects")
//        return audioFilesObjects
//    }



    private const val AUTHORITY = "com.lacrima.android.audioplayer"
}