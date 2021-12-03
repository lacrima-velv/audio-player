package com.lacrima.audioplayer.exoplayer

import android.os.SystemClock
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING
import android.support.v4.media.session.PlaybackStateCompat.STATE_BUFFERING
import android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED
import android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY
import android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY_PAUSE

/*
When using inline functions, the compiler inlines the function body.
That is, it substitutes the body directly into places where the function gets called
 */
inline val PlaybackStateCompat.isPrepared
    get() = state == STATE_BUFFERING ||
            state == STATE_PLAYING ||
            state == STATE_PAUSED

inline val PlaybackStateCompat.isPlaying
    get() = state == STATE_BUFFERING ||
            state == STATE_PLAYING

/*
Unlike the && operator, this function does not perform short-circuit evaluation.
Both this and other will always be evaluated
 */
inline val PlaybackStateCompat.isPlayEnabled
    get() = actions and ACTION_PLAY != 0L ||
            (actions and ACTION_PLAY_PAUSE != 0L &&
                    state == STATE_PAUSED)

inline val PlaybackStateCompat.currentPlaybackPosition: Long
    get() = if (state == STATE_PLAYING) {
        // Get the difference between system boot time and lastPositionUpdateTime
        val timeDelta = SystemClock.elapsedRealtime() - lastPositionUpdateTime
        (position + (timeDelta * playbackSpeed)).toLong()
    } else position

