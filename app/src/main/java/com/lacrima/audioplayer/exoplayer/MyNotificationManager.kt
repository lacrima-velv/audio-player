package com.lacrima.audioplayer.exoplayer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat.BADGE_ICON_NONE
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import com.lacrima.audioplayer.R
import com.lacrima.audioplayer.Util.toPixels
import timber.log.Timber

const val NOW_PLAYING_CHANNEL_ID = "Music"
// Arbitrary number used to identify our notification
const val NOW_PLAYING_NOTIFICATION_ID = 1

/**
 * A wrapper class for ExoPlayer's PlayerNotificationManager. It sets up the notification shown to
 * the user during audio playback and provides track metadata, such as track title and icon image.
 */

class MyNotificationManager(
    private val context: Context,
    sessionToken: MediaSessionCompat.Token,
    notificationListener: PlayerNotificationManager.NotificationListener
) {
    private val notificationManager: PlayerNotificationManager

    // These are used, when loading large notification icon
    val notificationBitmapWidth = 100.toPixels
    val notificationBitmapHeight = 100.toPixels

    val systemNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        //Allows an app to interact with an ongoing media session.
        val mediaController = MediaControllerCompat(context, sessionToken)




        createNotificationChannel()

        notificationManager = PlayerNotificationManager.Builder(
            context,
            NOW_PLAYING_NOTIFICATION_ID,
            NOW_PLAYING_CHANNEL_ID
        ).apply {
            setMediaDescriptionAdapter(DescriptionAdapter(mediaController))
            setNotificationListener(notificationListener)
        }.build().apply {
            setUseRewindAction(false)
            setUseFastForwardAction(false)
            setBadgeIconType(BADGE_ICON_NONE)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(NOW_PLAYING_CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW).apply {
                    description = context.getString(R.string.notification_channel_description)
                    setShowBadge(false)
            }
            systemNotificationManager.createNotificationChannel(
                notificationChannel
            )
        }
    }

    fun showNotification(player: Player){
        notificationManager.setPlayer(player)
    }

    private inner class DescriptionAdapter(private val controller: MediaControllerCompat) :
            PlayerNotificationManager.MediaDescriptionAdapter {

        override fun getCurrentContentTitle(player: Player): CharSequence {
            return controller.metadata.description.title.toString()
        }

        override fun getCurrentSubText(player: Player): CharSequence {
            return controller.metadata.description.subtitle.toString()
        }

        override fun createCurrentContentIntent(player: Player): PendingIntent? =
            controller.sessionActivity


        override fun getCurrentContentText(player: Player): CharSequence =
            controller.metadata.description.subtitle.toString()

        override fun getCurrentLargeIcon(
            player: Player,
            callback: PlayerNotificationManager.BitmapCallback
        ): Bitmap? {
            Timber.d("getCurrentLargeIcon() with Glide is called")

            Glide.with(context).asBitmap()
                .load(controller.metadata.description.iconBitmap ?: R.drawable.ic_music_note_notification)
                .centerCrop()
                .override(notificationBitmapWidth, notificationBitmapHeight)
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(
                        resource: Bitmap,
                        transition: Transition<in Bitmap>?
                    ) {
                        callback.onBitmap(resource)
                    }

                    override fun onLoadCleared(placeholder: Drawable?) = Unit
                })
            return null
        }

    }
}

