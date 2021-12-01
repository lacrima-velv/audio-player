package com.lacrima.audioplayer.data

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.MediaMetadataCompat.*
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.MetadataRetriever
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.lacrima.audioplayer.R
import com.lacrima.audioplayer.Util.getRawUri
import com.lacrima.audioplayer.Util.getRawUriForMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

object AudioFilesSource {

    // This list must be updated, if a new row resource is added or removed
    private val listOfAudioFilesRes = listOf(
        R.raw.lana_del_rey_florida_kilos,
        R.raw.kent_columbus,
        R.raw.lana_del_rey_gods_and_monsters,
        R.raw.lana_del_rey_salvatore,
        R.raw.radiohead_i_will,
        R.raw.lana_del_rey_cherry,
        R.raw.travis_idlewild
    )

    private val metadataRetriever = MediaMetadataRetriever()

    // Will be used inside a suspend function to retrieve all the metadata
    private fun getAudioFile(context: Context, uriForMetadata: Uri, uriForPlayer: Uri):
            MediaMetadataCompat {
            metadataRetriever.setDataSource(context, uriForMetadata)
        val metadata: MediaMetadataCompat

        val artworkRaw = metadataRetriever.embeddedPicture
        val title = metadataRetriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
        val artist = metadataRetriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
        val duration = metadataRetriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0

        val metadataBuilder = Builder()
            .putString(METADATA_KEY_TITLE, title)
            .putString(METADATA_KEY_DISPLAY_TITLE, title)
            .putString(METADATA_KEY_ARTIST, artist)
            .putString(METADATA_KEY_DISPLAY_DESCRIPTION, artist)
            .putString(METADATA_KEY_DISPLAY_SUBTITLE, artist)
            .putString(METADATA_KEY_MEDIA_URI, uriForPlayer.toString())
            .putString(METADATA_KEY_MEDIA_ID, uriForPlayer.toString())
            .putLong(METADATA_KEY_DURATION, duration)
            .putString(METADATA_KEY_ALBUM_ARTIST, artist)

        metadata = if (artworkRaw != null) {
            // Convert the byte array to a bitmap
            val bitmap = BitmapFactory.decodeByteArray(artworkRaw, 0, artworkRaw.size)
            metadataBuilder
                .putBitmap(METADATA_KEY_ALBUM_ART, bitmap)
                .build()
        } else {
            metadataBuilder.build()
        }

        return metadata
    }

    // Used to check if file's metadata is initialized, and to queue any callbacks if it's not
    private val onReadyListeners = mutableListOf<(Boolean) -> Unit>()

    private var state: State = State.STATE_CREATED
        set(value) {
            if(value == State.STATE_INITIALIZED || value == State.STATE_ERROR) {
                synchronized(onReadyListeners) {
                    field = value
                    onReadyListeners.forEach { listener ->
                        listener(state == State.STATE_INITIALIZED)
                    }
                }
            } else {
                field = value
            }
        }

    fun whenReady(action: (Boolean) -> Unit): Boolean {
        if(state == State.STATE_CREATED || state == State.STATE_INITIALIZING) {
            onReadyListeners += action
            return false
        } else {
            action(state == State.STATE_INITIALIZED)
            return true
        }
    }

    // Used in MusicServer to get the metadata
    var audioFilesMetadata = emptyList<MediaMetadataCompat>()

    suspend fun getAudioFilesMetadata(context: Context) = withContext(
        Dispatchers.Main){
        Timber.d("getAudioFilesMetadata() is called")
        state = State.STATE_INITIALIZING
        val audioFiles = mutableListOf<MediaMetadataCompat>()
        for (uriForMetadata in getListOfFilesUrisForMetadataRetriever(context)) {
            val uriForPlayer =
                getListOfFilesUrisForPlayer(context)[getListOfFilesUrisForMetadataRetriever(context)
                    .indexOf(uriForMetadata)]
            audioFiles.add(getAudioFile(context, uriForMetadata, uriForPlayer))
        }
        audioFilesMetadata =  audioFiles.toList()
        state = State.STATE_INITIALIZED
    }

    fun asMediaSource(dataSourceFactory: DefaultDataSource.Factory): ConcatenatingMediaSource {
        val concatenatingMediaSource = ConcatenatingMediaSource()
        audioFilesMetadata.forEach { audioFile ->
            val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(audioFile.getString(METADATA_KEY_MEDIA_URI)))
            concatenatingMediaSource.addMediaSource(mediaSource)
        }
        return concatenatingMediaSource
    }

    fun asMediaItems() = audioFilesMetadata.map { audioFile ->
        val description = MediaDescriptionCompat.Builder()
            .setMediaUri(audioFile.description.mediaUri)
            .setTitle(audioFile.description.title)
            .setSubtitle(audioFile.description.subtitle)
            .setMediaId(audioFile.description.mediaId)
            .build()
        MediaBrowserCompat.MediaItem(description, FLAG_PLAYABLE)
    }.toMutableList()

    private fun getListOfFilesUrisForMetadataRetriever(context: Context): List<Uri> {
        val listOfFileUris = mutableListOf<Uri>()
        for (res in listOfAudioFilesRes) {
            listOfFileUris.add(getRawUriForMetadataRetriever(context, res))
        }
        return listOfFileUris
    }

    private fun getListOfFilesUrisForPlayer(context: Context): List<Uri> {
        val listOfFileUris = mutableListOf<Uri>()
        for (res in listOfAudioFilesRes) {
            listOfFileUris.add(getRawUri(context, res))
        }
        return listOfFileUris
    }

    fun MediaMetadataCompat.toSong(): Song {
        return Song(
            mediaId = this.description.mediaId,
            songUri = this.description.mediaUri,
            title = this.description.title.toString(),
            artist = this.description.subtitle.toString(),
            imageBitmap = this.getBitmap(METADATA_KEY_ALBUM_ART),
            duration = this.getLong(METADATA_KEY_DURATION)
        )
    }
}

enum class State {
    STATE_CREATED,
    STATE_INITIALIZING,
    STATE_INITIALIZED,
    STATE_ERROR
}