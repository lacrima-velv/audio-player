package com.lacrima.audioplayer.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.lacrima.audioplayer.data.AudioFilesSource
import com.lacrima.audioplayer.data.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import timber.log.Timber

// The name of collection in firestore
const val SONG_COLLECTION = "songs"

class MusicDatabase {

    private val firestore = FirebaseFirestore.getInstance()
    private val songCollection = firestore.collection(SONG_COLLECTION)
    private val settings: FirebaseFirestoreSettings = FirebaseFirestoreSettings.Builder()
        .setPersistenceEnabled(true)
        .build()

    suspend fun getAllSongs(): List<Song> {
        return try {
            this.songCollection.get().await().toObjects(Song::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setFirestoreSettings() {
        firestore.firestoreSettings = settings
    }

    private val _fetchingFirebaseDocumentState =
        MutableStateFlow<FetchingFirebaseDocumentState>(FetchingFirebaseDocumentState.NotStarted)
    val fetchingFirebaseDocumentState: StateFlow<FetchingFirebaseDocumentState>
        get() = _fetchingFirebaseDocumentState

    fun setListenerToFirebaseCollection() {
        songCollection.document().get()
            .addOnFailureListener { exception ->
                Timber.d("Firebase get collection exception: ${exception.message} " +
                        "State is ${AudioFilesSource.fetchingMetadataState}")
                _fetchingFirebaseDocumentState.value = FetchingFirebaseDocumentState.Error
            }
            .addOnSuccessListener { document ->
                if (document != null) {
                    Timber.d("DocumentSnapshot data: ${document.data}")
                    _fetchingFirebaseDocumentState.value = FetchingFirebaseDocumentState.Success
                } else {
                    Timber.d("No such document")
                    _fetchingFirebaseDocumentState.value = FetchingFirebaseDocumentState.Error
                }
            }
    }

}

sealed class FetchingFirebaseDocumentState {
    object NotStarted: FetchingFirebaseDocumentState()
    object Success: FetchingFirebaseDocumentState()
    object Error: FetchingFirebaseDocumentState()
}