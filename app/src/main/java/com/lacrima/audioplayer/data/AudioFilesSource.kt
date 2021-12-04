package com.lacrima.audioplayer.data

import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.MediaMetadataCompat.*
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DataSource
import com.lacrima.audioplayer.remote.MusicDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber


object AudioFilesSource : KoinComponent {

    private val musicDatabase: MusicDatabase by inject()

    // Used to check if files are downloaded and file's metadata is initialized.
    // It queues any callbacks if our files are not ready.
    private val onReadyListeners = mutableListOf<(Boolean) -> Unit>()

    var fetchingMetadataState: FetchingMetadataState = FetchingMetadataState.STATE_CREATED
        set(value) {
            if(value == FetchingMetadataState.STATE_INITIALIZED || value ==
                FetchingMetadataState.STATE_ERROR) {
                // Everything inside this block will only be accessed from the same thread
                synchronized(onReadyListeners) {
                    field = value
                    // Call lambdas inside onReadyListeners if state is initialized
                    onReadyListeners.forEach { listener ->
                        listener(fetchingMetadataState == FetchingMetadataState.STATE_INITIALIZED)
                    }
                }
            } else {
                field = value
            }
        }
    // When the music source is ready, we call this action
    fun whenReady(action: (Boolean) -> Unit): Boolean {
        return if (fetchingMetadataState == FetchingMetadataState.STATE_CREATED ||
            fetchingMetadataState == FetchingMetadataState.STATE_INITIALIZING) {
            // Schedule this action
            onReadyListeners += action
            false
        } else {
            // Call this action, if the music source is initialized
            action(fetchingMetadataState == FetchingMetadataState.STATE_INITIALIZED)
            true
        }
    }

    // Used in MusicServer to get the metadata
    var audioFilesMetadata = emptyList<MediaMetadataCompat>()

    suspend fun fetchMediaData() = withContext(Dispatchers.Main) {
        fetchingMetadataState = FetchingMetadataState.STATE_INITIALIZING
        val allSongs = musicDatabase.getAllSongs()
        if (allSongs.isEmpty()) {
            Timber.d("Couldn't get any songs from Firebase. " +
                    "State is $fetchingMetadataState")
        } else {
            audioFilesMetadata = allSongs.map { song ->
                Builder()
                    .putString(METADATA_KEY_TITLE, song.title)
                    .putString(METADATA_KEY_DISPLAY_TITLE, song.title)
                    .putString(METADATA_KEY_ARTIST, song.artist)
                    .putString(METADATA_KEY_DISPLAY_DESCRIPTION, song.artist)
                    .putString(METADATA_KEY_DISPLAY_SUBTITLE, song.artist)
                    .putString(METADATA_KEY_MEDIA_URI, song.songUrl)
                    .putString(METADATA_KEY_MEDIA_ID, song.mediaId)
                    .putLong(METADATA_KEY_DURATION, song.duration)
                    .putString(METADATA_KEY_ALBUM_ARTIST, song.artist)
                    .putString(METADATA_KEY_ALBUM_ART_URI, song.imageUrl)
                    .putString(METADATA_KEY_DISPLAY_ICON_URI, song.imageUrl)
                    .build()
            }
            fetchingMetadataState = FetchingMetadataState.STATE_INITIALIZED
        }
    }

    fun asMediaSource(dataSourceFactory: DataSource.Factory): ConcatenatingMediaSource {
        val concatenatingMediaSource = ConcatenatingMediaSource()
        audioFilesMetadata.forEach { song ->
            val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(song.getString(METADATA_KEY_MEDIA_URI)))
            concatenatingMediaSource.addMediaSource(mediaSource)
        }
        return concatenatingMediaSource
    }

    fun asMediaItems() = audioFilesMetadata.map { song ->
        val description = MediaDescriptionCompat.Builder()
            .setMediaUri(song.description.mediaUri)
            .setTitle(song.description.title)
            .setSubtitle(song.description.subtitle)
            .setMediaId(song.description.mediaId)
            .setIconUri(song.description.iconUri)
            .build()
        MediaBrowserCompat.MediaItem(description, FLAG_PLAYABLE)
    }.toMutableList()

    fun MediaMetadataCompat.toSong(): Song {
        return Song(
            mediaId = this.description.mediaId ?: "unknown",
            songUrl = this.description.mediaUri.toString(),
            title = this.description.title.toString(),
            artist = this.description.subtitle.toString(),
            imageUrl = this.description.iconUri.toString(),
            duration = this.getLong(METADATA_KEY_DURATION)
        )
    }
}

enum class FetchingMetadataState {
    STATE_CREATED,
    STATE_INITIALIZING,
    STATE_INITIALIZED,
    STATE_ERROR
}