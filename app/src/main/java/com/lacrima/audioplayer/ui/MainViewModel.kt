package com.lacrima.audioplayer.ui

import android.app.Application
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_MEDIA_ID
import androidx.lifecycle.AndroidViewModel
import com.lacrima.audioplayer.data.Song
import com.lacrima.audioplayer.exoplayer.*
import com.lacrima.audioplayer.generalutils.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MainViewModel(application: Application): AndroidViewModel(application), KoinComponent {
    private val musicServiceConnection: MusicServiceConnection by inject()
    private val _mediaItems = MutableStateFlow<Resource<List<Song>>>(
        Resource.notStarted("Still haven't got any media items", null)
    )
    val mediaItems: StateFlow<Resource<List<Song>>>
        get() = _mediaItems

    val isConnected = musicServiceConnection.isConnected
    val sessionError = musicServiceConnection.sessionError
    val playbackState = musicServiceConnection.playbackState
    val currentlyPlayingSong = musicServiceConnection.currentlyPlayingSong
    val currentlyPlayingSongDuration = musicServiceConnection.currentlyPlayingSongDuration
    val fetchingFirebaseDocumentState = musicServiceConnection.fetchingFirebaseDocumentState

    init {
        _mediaItems.value = Resource.loading(null)

        musicServiceConnection.subscribe(MEDIA_ROOT_ID, object :
            MediaBrowserCompat.SubscriptionCallback() {
            override fun onChildrenLoaded(
                parentId: String,
                children: MutableList<MediaBrowserCompat.MediaItem>
            ) {
                super.onChildrenLoaded(parentId, children)
                val items = children.map {
                    Song(
                        it.mediaId ?: "",
                        it.description.mediaUri.toString(),
                        it.description.title.toString(),
                        it.description.subtitle.toString(),
                        it.description.iconUri.toString(),
                        0L
                    )
                }
                if (items.isNotEmpty()) {_mediaItems.value = Resource.success(items)} else {
                    _mediaItems.value = Resource.
                    error("Couldn't get any items due to error", items)
                }

            }
        })

    }

    private val _currentPlayerPosition = MutableStateFlow(0L)
    val currentPlayerPosition: StateFlow<Long>
        get() = _currentPlayerPosition

    fun updateCurrentPlayerPosition(positionMs: Long) {
        _currentPlayerPosition.value = positionMs
    }

    fun skipToNextSong() {
        musicServiceConnection.transportControls.skipToNext()
    }

    fun skipToPreviousSong() {
        musicServiceConnection.transportControls.skipToPrevious()
    }

    fun seekTo(position: Long) {
        musicServiceConnection.transportControls.seekTo(position)
    }

    fun playOrToggleSong(mediaItem: Song, toggle: Boolean = false) {
        val isPrepared = playbackState.value?.isPrepared ?: false
        if (isPrepared && mediaItem.mediaId
            == currentlyPlayingSong.value?.getString(METADATA_KEY_MEDIA_ID)) {
            playbackState.value?.let { playbackState ->
                when {
                    playbackState.isPlaying -> if (toggle)
                        musicServiceConnection.transportControls.pause()
                    playbackState.isPlayEnabled -> musicServiceConnection.transportControls.play()
                    else -> Unit
                }
            }
            /*
            It will be used if I'll have a list of songs (currently I don't)
             */
        } else {
            musicServiceConnection.transportControls.playFromMediaId(mediaItem.mediaId, null)
        }
    }

    override fun onCleared() {
        super.onCleared()
        musicServiceConnection.unsubscribe(MEDIA_ROOT_ID, object :
            MediaBrowserCompat.SubscriptionCallback() {})
    }
}