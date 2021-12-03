package com.lacrima.audioplayer.ui

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.SeekBar
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.google.android.material.snackbar.Snackbar
import com.lacrima.audioplayer.BuildConfig
import com.lacrima.audioplayer.R
import com.lacrima.audioplayer.Util.setUiWindowInsetsBottom
import com.lacrima.audioplayer.Util.setUiWindowInsetsTop
import com.lacrima.audioplayer.data.AudioFilesSource.toSong
import com.lacrima.audioplayer.data.Song
import com.lacrima.audioplayer.databinding.ActivityMainBinding
import com.lacrima.audioplayer.exoplayer.currentPlaybackPosition
import com.lacrima.audioplayer.exoplayer.isPlaying
import com.lacrima.audioplayer.generalutils.Status.ERROR
import com.lacrima.audioplayer.generalutils.Status.SUCCESS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

const val UPDATE_PLAYER_POSITION_INTERVAL = 100L

class MainActivity : AppCompatActivity(), KoinComponent {

    private lateinit var mainViewModel: MainViewModel
    private lateinit var binding: ActivityMainBinding
    private var shouldUpdateSeekbar = true
    private var listOfSongs: List<Song>? = null
    private var nextSongTitle = ""
    private var nextSongArtist = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Debug logs are used only in debug build type
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        mainViewModel = ViewModelProvider(this)[MainViewModel::class.java]

        setUiWindowInsetsBottom(binding.musicQueue)
        setUiWindowInsetsBottom(binding.nextSong)
        setUiWindowInsetsTop(binding.appbar)

        lifecycleScope.launchWhenStarted {
            launch(Dispatchers.IO) {
                updateCurrentPlayerPosition()
            }

            launch {
                mainViewModel.currentlyPlayingSongDuration.collectLatest { duration ->
                    // Initially duration is -1, which is displayed in text view as 59:59
                    val durationLong = if (duration == -1L) 0 else duration
                    val durationInt = durationLong.toInt()
                    binding.seekBar.max = durationInt

                    setTimeToTextView(binding.durationTime, durationLong)
                }
            }
            launch {
                mainViewModel.isConnected.collectLatest {
                    it.getContentIfNotHandled()?.let { result ->
                        when (result.status) {
                            ERROR -> Snackbar.make(
                                binding.root,
                                result.message ?: getString(R.string.unknown_error),
                                Snackbar.LENGTH_SHORT
                            ).show()
                            else -> Unit
                        }
                    }
                }
            }
            launch {
                // It's called every time we paused/start playing
                mainViewModel.playbackState.collectLatest { playbackState ->
                    binding.playPause.setImageResource(
                        if (playbackState?.isPlaying == true)
                            R.drawable.ic_round_pause_circle_outline_48
                        else R.drawable.ic_round_play_circle_outline_48
                    )
                    binding.seekBar.progress = playbackState?.position?.toInt() ?: 0

                }
            }
            launch {
                mainViewModel.mediaItems.collectLatest { result ->
                    when (result.status) {
                        SUCCESS -> {
                            listOfSongs = result.data
                            updateNextSongTitleArtist()
                        }
                        else -> Unit
                    }

                }
            }
            launch {
                mainViewModel.currentlyPlayingSong.collectLatest { songMetadata ->

                    updateNextSongTitleArtist()

                    binding.playPause.setOnClickListener {
                        songMetadata?.toSong()
                            ?.let { song -> mainViewModel.playOrToggleSong(song, true) }
                    }
                    binding.artist.text = songMetadata?.description?.subtitle ?: ""
                    binding.songTitle.text = songMetadata?.description?.title ?: ""

                    Glide.with(this@MainActivity).asBitmap()
                        .load(
                            (songMetadata?.description?.iconBitmap)
                        )
                        .placeholder(R.drawable.ic_music_note_album_art)
                        .transform(RoundedCorners(30))
                        .into(binding.albumArt)
                }
            }
            launch {
                mainViewModel.currentPlayerPosition.collectLatest { position ->
                    if (shouldUpdateSeekbar) {
                        binding.seekBar.progress = position.toInt()
                        setTimeToTextView(binding.positionTime, position)
                    }

                }
            }
            launch {
                mainViewModel.sessionError.collectLatest {
                    it.getContentIfNotHandled()?.let { result ->
                        when (result.status) {
                            ERROR -> Snackbar.make(
                                binding.root,
                                result.message ?: getString(R.string.unknown_error),
                                Snackbar.LENGTH_LONG
                            ).show()
                            else -> Unit
                        }
                    }
                }
            }

        }

        binding.skipToNext.setOnClickListener {
            mainViewModel.skipToNextSong()
        }

        binding.skipToPrevious.setOnClickListener {
            mainViewModel.skipToPreviousSong()
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    setTimeToTextView(binding.positionTime, progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                shouldUpdateSeekbar = false
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.let {
                    mainViewModel.seekTo(it.progress.toLong())
                    shouldUpdateSeekbar = true
                }
            }

        })

        setContentView(binding.root)
    }

    private fun setTimeToTextView(view: TextView, ms: Long) {
        val dateFormat = SimpleDateFormat("mm:ss", Locale.getDefault())
        view.text = dateFormat.format(ms)
    }

    private fun updateNextSongTitleArtist() {
        // Find the position of currently playing song in a list of songs
        val currentlyPlayingSongPosition = listOfSongs?.indexOf(listOfSongs?.find {
            it.mediaId == mainViewModel.currentlyPlayingSong.value?.description?.mediaId })

        if (currentlyPlayingSongPosition != listOfSongs?.size?.minus(1)) {
            nextSongTitle = listOfSongs?.get(currentlyPlayingSongPosition?.plus(1) ?: 0)
                ?.title ?: getString(R.string.unknown)
            nextSongArtist = listOfSongs?.get(currentlyPlayingSongPosition?.plus(1) ?: 0)
                ?.artist ?: getString(R.string.unknown)
        } else {
            nextSongTitle = listOfSongs?.get(0)?.title ?: getString(R.string.unknown)
            nextSongArtist = listOfSongs?.get(0)?.artist ?: getString(R.string.unknown)
        }

        binding.nextSong.text = getString(R.string.next_song, nextSongArtist, nextSongTitle)
    }

    private suspend fun updateCurrentPlayerPosition() {
        while (true) {
            if (mainViewModel.playbackState.value?.currentPlaybackPosition != null) {
                val pos = mainViewModel.playbackState.value!!.currentPlaybackPosition
                if (mainViewModel.currentPlayerPosition.value != pos) {
                    mainViewModel.updateCurrentPlayerPosition(pos)
                }
                delay(UPDATE_PLAYER_POSITION_INTERVAL)
            }
        }
    }
}