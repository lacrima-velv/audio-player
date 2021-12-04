package com.lacrima.audioplayer.exoplayer

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.lacrima.audioplayer.generalutils.Event
import com.lacrima.audioplayer.generalutils.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

class MusicServiceConnection(context: Context) {

    private val _isConnected = MutableStateFlow<Event<Resource<Boolean>>>(
        Event(Resource.notStarted("The process has not started yet",null))
    )
    val isConnected: StateFlow<Event<Resource<Boolean>>>
        get() = _isConnected

    private val _sessionError = MutableStateFlow<Event<Resource<Boolean>>>(
        Event(Resource.notStarted("Didn't get any error", null))
    )
    val sessionError: StateFlow<Event<Resource<Boolean>>>
        get() = _sessionError

    private val _playbackState = MutableStateFlow<PlaybackStateCompat?>(null)
    val playbackState: StateFlow<PlaybackStateCompat?>
        get() = _playbackState

    private val _currentlyPlayingSong = MutableStateFlow<MediaMetadataCompat?>(null)
    val currentlyPlayingSong: StateFlow<MediaMetadataCompat?>
        get() = _currentlyPlayingSong

    private val _currentlyPlayingSongDuration = MutableStateFlow(0L)
    val currentlyPlayingSongDuration: StateFlow<Long>
        get() = _currentlyPlayingSongDuration


    private val mediaBrowserConnectionCallback = MediaBrowserConnectionCallback(context)

    private val mediaBrowser = MediaBrowserCompat(
        context,
        ComponentName(
            context,
            MusicService::class.java
        ),
        mediaBrowserConnectionCallback,
        null
    ).apply {
        Timber.d("MediaBrowserCompat is created")
        connect()
    }

    // Used for controlling of the playback: pause, play, rewind, etc.
    lateinit var mediaController: MediaControllerCompat

    // We need to react, when the subscription to the specific media id is finished
    fun subscribe(parentId: String, callback: MediaBrowserCompat.SubscriptionCallback) {
        mediaBrowser.subscribe(parentId, callback)
    }

    fun unsubscribe(parentId: String, callback: MediaBrowserCompat.SubscriptionCallback) {
        mediaBrowser.unsubscribe(parentId, callback)
    }

    private inner class MediaBrowserConnectionCallback(private val context: Context) :
        MediaBrowserCompat.ConnectionCallback() {

        override fun onConnected() {
            Timber.d("MediaBrowserConnectionCallback: onConnected() is called")
            mediaController = MediaControllerCompat(context, mediaBrowser.sessionToken).apply {
                registerCallback(MediaControllerCallback())
            }
            _isConnected.value = Event(Resource.success(true))
        }

        override fun onConnectionSuspended() {
            Timber.d("MediaBrowserConnectionCallback: onConnectionSuspended() is called")
            _isConnected.value = Event(Resource.error(
                "The connection to media browser was suspended", false)
            )
        }

        override fun onConnectionFailed() {
            Timber.d("MediaBrowserConnectionCallback: onConnectionFailed() is called")
            _isConnected.value = Event(Resource.error(
                "Couldn't connect to media browser", false)
            )
        }

    }

    val transportControls: MediaControllerCompat.TransportControls
        get() = mediaController.transportControls

    private inner class MediaControllerCallback : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            Timber.d("onPlaybackStateChanged() is called. playbackState is " +
                    "${_playbackState.value}")
            _playbackState.value = state
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            Timber.d("onMetadataChanged() is called")
            _currentlyPlayingSongDuration.value = metadata?.
            getLong(MediaMetadataCompat.METADATA_KEY_DURATION) ?: 0
            // onMetadataChanged could be called several times, but the song doesn't always changes
            if (_currentlyPlayingSong.value?.description?.mediaId !=
                metadata?.description?.mediaId) {
                Timber.d("Currently playing song changed")
                _currentlyPlayingSong.value = metadata
            }
        }

        override fun onSessionEvent(event: String?, extras: Bundle?) {
            super.onSessionEvent(event, extras)
            when (event) {
                ERROR_SESSION_EVENT -> _sessionError.value =
                    Event(Resource.error("Couldn't connect to Media Session", null))
            }
        }

        override fun onSessionDestroyed() {
            mediaBrowserConnectionCallback.onConnectionSuspended()
        }
    }

}

const val ERROR_SESSION_EVENT = "error_session_event"