package com.lacrima.audioplayer.exoplayer

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ResultReceiver
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.media.MediaBrowserServiceCompat
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueNavigator
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import com.lacrima.audioplayer.R
import com.lacrima.audioplayer.data.AudioFilesSource.asMediaItems
import com.lacrima.audioplayer.data.AudioFilesSource.asMediaSource
import com.lacrima.audioplayer.data.AudioFilesSource.audioFilesMetadata
import com.lacrima.audioplayer.data.AudioFilesSource.fetchMediaData
import com.lacrima.audioplayer.data.AudioFilesSource.whenReady
import com.lacrima.audioplayer.remote.MusicDatabase
import kotlinx.coroutines.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

const val MEDIA_ROOT_ID = "root_id"
private const val SERVICE_TAG = "MusicService"

class MusicService : MediaBrowserServiceCompat(), KoinComponent {

    private val dataSourceFactory: CacheDataSourceFactory by inject()
    private val musicDatabase: MusicDatabase by inject()

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var mediaSessionConnector: MediaSessionConnector
    private lateinit var notificationManager: MyNotificationManager
    private var currentlyPlayingSong: MediaMetadataCompat? = null

    private var isForegroundService = false

    private val myAudioAttributes = AudioAttributes.Builder()
        .setContentType(C.CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

    private var playerListener = PlayerEventListener()

    private var isPlayerInitialized = false

    /**
     * Configure ExoPlayer to handle audio focus for us.
     */
    private val exoPlayer: ExoPlayer by lazy {
        ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(myAudioAttributes, true)
            setHandleAudioBecomingNoisy(true)
            addListener(playerListener)
        }
    }

    override fun onCreate() {
        super.onCreate()

        serviceScope.launch {
            musicDatabase.setFirestoreSettings()
            musicDatabase.setListenerToFirebaseCollection()
            fetchMediaData()
        }

        val mutabilityFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }

        // Build a PendingIntent that can be used to launch the UI.
        val sessionActivityPendingIntent =
            packageManager?.getLaunchIntentForPackage(packageName)?.let { sessionIntent ->
                PendingIntent.getActivity(this, 0, sessionIntent, mutabilityFlag)
            }

        // Create a new MediaSession.
        mediaSession = MediaSessionCompat(this, SERVICE_TAG)
            .apply {
                setSessionActivity(sessionActivityPendingIntent)
                isActive = true
            }

        /**
         * In order for [MediaBrowserCompat.ConnectionCallback.onConnected] to be called,
         * a [MediaSessionCompat.Token] needs to be set on the [MediaBrowserServiceCompat].
         *
         * It is possible to wait to set the session token, if required for a specific use-case.
         * However, the token *must* be set by the time [MediaBrowserServiceCompat.onGetRoot]
         * returns, or the connection will fail silently. (The system will not even call
         * [MediaBrowserCompat.ConnectionCallback.onConnectionFailed].)
         */
        sessionToken = mediaSession.sessionToken

        /**
         * The notification manager will use our player and media session to decide when to post
         * notifications. When notifications are posted or removed our listener will be called, this
         * allows us to promote the service to foreground (required so that we're not killed if
         * the main UI is not visible).
         */
        notificationManager = MyNotificationManager(
            this,
            mediaSession.sessionToken,
            PlayerNotificationListener()
        )



        // ExoPlayer will manage the MediaSession for us.
        mediaSessionConnector = MediaSessionConnector(mediaSession)
        mediaSessionConnector.setPlaybackPreparer(MyPlaybackPreparer {
            // This callback will be called every time the player plays a new song
            currentlyPlayingSong = it

            preparePlayer(
                audioFilesMetadata,
                it,
                true
            )
        })
        mediaSessionConnector.setPlayer(exoPlayer)
        mediaSessionConnector.setQueueNavigator(MyQueueNavigator(mediaSession))

        notificationManager.showNotification(exoPlayer)
    }

    private fun preparePlayer(
        audioFilesMetadata: List<MediaMetadataCompat>,
        itemToPlay: MediaMetadataCompat?,
        playNow: Boolean
    ) {
        val currentSongIndex = if (currentlyPlayingSong == null) 0
        else audioFilesMetadata.indexOf(itemToPlay)

        exoPlayer.setMediaSource(asMediaSource(dataSourceFactory))
        exoPlayer.repeatMode = ExoPlayer.REPEAT_MODE_ALL
        exoPlayer.prepare()
        exoPlayer.seekTo(currentSongIndex, 0L)
        exoPlayer.playWhenReady = playNow
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        exoPlayer.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
    }


    private inner class MyQueueNavigator(
        mediaSession: MediaSessionCompat
    ) : TimelineQueueNavigator(mediaSession) {
        override fun getMediaDescription(player: Player, windowIndex: Int): MediaDescriptionCompat =
            audioFilesMetadata[windowIndex].description
    }

    private inner class MyPlaybackPreparer(
        private val playerPrepared: (MediaMetadataCompat?) -> Unit
    ) : MediaSessionConnector.PlaybackPreparer {
        override fun onCommand(
            player: Player,
            command: String,
            extras: Bundle?,
            cb: ResultReceiver?
        ): Boolean = false

        override fun getSupportedPrepareActions(): Long =
            PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID or
                    PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID

        override fun onPrepare(playWhenReady: Boolean) = Unit

        override fun onPrepareFromMediaId(
            mediaId: String,
            playWhenReady: Boolean,
            extras: Bundle?
        ) { whenReady {
            val itemToPlay = audioFilesMetadata.find {
                mediaId == it.description.mediaId
            }
            playerPrepared(itemToPlay)
        }
        }

        override fun onPrepareFromSearch(query: String, playWhenReady: Boolean, extras: Bundle?)
                = Unit

        override fun onPrepareFromUri(uri: Uri, playWhenReady: Boolean, extras: Bundle?) = Unit

    }

    /**
     * Listen for notification events.
     */
    private inner class PlayerNotificationListener :
        PlayerNotificationManager.NotificationListener {
        override fun onNotificationPosted(
            notificationId: Int,
            notification: Notification,
            ongoing: Boolean
        ) {
            if (ongoing && !isForegroundService) {
                ContextCompat.startForegroundService(
                    this@MusicService,
                    Intent(applicationContext, this@MusicService::class.java)
                )

                startForeground(notificationId, notification)
                isForegroundService = true
            }
        }

        override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
            stopForeground(true)
            isForegroundService = false
            stopSelf()
        }
    }

    /**
     * Listen for events from ExoPlayer.
     */
    private inner class PlayerEventListener : Player.Listener {

        @Deprecated("Deprecated in Java")
        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            if(playbackState == Player.STATE_READY && !playWhenReady) {
                stopForeground(false)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val message = getString(R.string.unexpected_error_player)
            Toast.makeText(
                this@MusicService,
                message,
                Toast.LENGTH_LONG
            ).show()
        }

    }
    /*
    To allow clients to connect to your service and browse its media content, onGetRoot()
    must return a non-null BrowserRoot which is a root ID that represents your content hierarchy.
     */
    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        return BrowserRoot(MEDIA_ROOT_ID, null)
    }

    /*
    Called to get information about the children of a media item.
    Implementations must call result.sendResult with the list of children
     */
    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        when (parentId) {
            MEDIA_ROOT_ID -> {
                val resultsSent = whenReady { isInitialized ->
                    if (isInitialized) {
                        result.sendResult(asMediaItems())
                        Timber.d("onLoadChildren: result is sent")
                        if (!isPlayerInitialized && audioFilesMetadata.isNotEmpty()) {
                            preparePlayer(audioFilesMetadata, audioFilesMetadata[0], false)
                            isPlayerInitialized = true
                        }
                    } else {
                        mediaSession.sendSessionEvent(ERROR_SESSION_EVENT, null)
                        result.sendResult(null)
                    }
                }
                if(!resultsSent) {
                    result.detach()
                }
            }
        }
    }
}


