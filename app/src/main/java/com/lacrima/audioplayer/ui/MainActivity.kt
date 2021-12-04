package com.lacrima.audioplayer.ui

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.google.android.material.snackbar.Snackbar
import com.lacrima.audioplayer.BuildConfig
import com.lacrima.audioplayer.R
import com.lacrima.audioplayer.generalutils.Util.checkIsConnectedToWiFi
import com.lacrima.audioplayer.generalutils.Util.setUiWindowInsetsBottom
import com.lacrima.audioplayer.generalutils.Util.setUiWindowInsetsTop
import com.lacrima.audioplayer.generalutils.Util.toPixels
import com.lacrima.audioplayer.data.AudioFilesSource.fetchMediaData
import com.lacrima.audioplayer.data.AudioFilesSource.toSong
import com.lacrima.audioplayer.data.FetchingFirebaseDocumentState
import com.lacrima.audioplayer.data.Song
import com.lacrima.audioplayer.databinding.*
import com.lacrima.audioplayer.exoplayer.currentPlaybackPosition
import com.lacrima.audioplayer.exoplayer.isPlaying
import com.lacrima.audioplayer.generalutils.Status
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

    private val albumArtWidth = 200.toPixels
    private val albumArtHeight = 200.toPixels
    private lateinit var mainViewModel: MainViewModel
    private lateinit var binding: ActivityMainBinding
    private lateinit var errorNoWifiBindingInitial: NoWifiInitialBinding
    private lateinit var generalErrorBindingInitial: GeneralErrorInitialBinding
    private lateinit var progressBinding: ProgressBinding
    private var shouldUpdateSeekbar = true
    private var listOfSongs: List<Song>? = null
    private var nextSongTitle = ""
    private var nextSongArtist = ""
    private lateinit var defaultViews: List<View>
    private lateinit var generalErrorViews: List<View>
    private lateinit var noWiFiErrorViews: List<View>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Debug logs are used only in debug build type
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Change from Splash screen theme to the default one
        setTheme(R.style.Theme_AudioPlayer)

        binding = ActivityMainBinding.inflate(layoutInflater)
        errorNoWifiBindingInitial = NoWifiInitialBinding.bind(binding.root)
        generalErrorBindingInitial = GeneralErrorInitialBinding.bind(binding.root)
        progressBinding = ProgressBinding.bind(binding.root)

        mainViewModel = ViewModelProvider(this)[MainViewModel::class.java]

        setUiWindowInsetsBottom(binding.musicQueue)
        setUiWindowInsetsBottom(binding.nextSong)
        setUiWindowInsetsTop(binding.appbar)

        defaultViews = listOf(
            binding.albumArt,
            binding.seekBar,
            binding.positionTime,
            binding.durationTime,
            binding.skipToPrevious,
            binding.skipToNext,
            binding.playPause,
            binding.nextSong,
            binding.rectangle,
            binding.musicQueue
        )

        noWiFiErrorViews = listOf(
            errorNoWifiBindingInitial.wifiErrorButton,
            errorNoWifiBindingInitial.wifiErrorImage,
            errorNoWifiBindingInitial.wifiErrorText
        )

        generalErrorViews = listOf(
            generalErrorBindingInitial.generalErrorButton,
            generalErrorBindingInitial.generalErrorImage,
            generalErrorBindingInitial.generalErrorText
        )

        errorNoWifiBindingInitial.wifiErrorButton.setOnClickListener {
            lifecycleScope.launch {
                fetchMediaData()
            }
        }

        generalErrorBindingInitial.generalErrorButton.setOnClickListener {
            lifecycleScope.launch {
                fetchMediaData()
            }
        }

        lifecycleScope.launchWhenStarted {
            launch {
                mainViewModel.fetchingFirebaseDocumentState.collectLatest {
                    if (it == FetchingFirebaseDocumentState.Error) {
                        if (!checkIsConnectedToWiFi()) {
                            showNoWifiError()
                        } else {
                            showGeneralError()
                        }
                    }
                }
            }
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

                            showDefaultViews()
                            Timber.d("Items are $listOfSongs")
                            updateNextSongTitleArtist()
                        }
                        Status.LOADING -> {
                            Timber.d("Items are loading")
                            showProgress()

                        }
                        ERROR -> {
                            Timber.d("An error has occurred. Couldn't get any items.")

                            if (!checkIsConnectedToWiFi()) {
                                showNoWifiError()
                            } else {
                                showGeneralError()
                            }
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
                            (songMetadata?.description?.iconUri)
                        )
                        .override(albumArtWidth, albumArtHeight)
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

    private fun showDefaultViews() {
        progressBinding.progressBar.isVisible = false

        for (view in defaultViews) {
            view.isVisible = true
        }
        for (view in noWiFiErrorViews) {
            view.isVisible = false
        }
        for (view in generalErrorViews) {
            view.isVisible = false
        }
    }

    fun showProgress() {
        progressBinding.progressBar.isVisible = true

        for (view in defaultViews) {
            view.isVisible = false
        }
        for (view in noWiFiErrorViews) {
            view.isVisible = false
        }
        for (view in generalErrorViews) {
            view.isVisible = false
        }
    }

    fun showNoWifiError() {
        progressBinding.progressBar.isVisible = false

        for (view in defaultViews) {
            view.isVisible = false
        }
        for (view in noWiFiErrorViews) {
            view.isVisible = true
        }
        for (view in generalErrorViews) {
            view.isVisible = false
        }
    }

    fun showGeneralError() {
        progressBinding.progressBar.isVisible = false

        for (view in defaultViews) {
            view.isVisible = false
        }
        for (view in noWiFiErrorViews) {
            view.isVisible = false
        }
        for (view in generalErrorViews) {
            view.isVisible = true
        }
    }

    private fun showOfflinePlaceholder() {
       if (!checkIsConnectedToWiFi()) {
           binding.seekBar.isVisible = false
           binding.nextSong.isVisible = false
           binding.positionTime.isVisible = false

       }
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