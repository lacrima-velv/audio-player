package com.lacrima.audioplayer.remote

import com.google.android.gms.tasks.OnFailureListener
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.lacrima.audioplayer.data.Song
import kotlinx.coroutines.tasks.await

// The name of collection in firestore
const val SONG_COLLECTION = "songs"

class MusicDatabase {

    private val firestore = FirebaseFirestore.getInstance()
    val songCollection = firestore.collection(SONG_COLLECTION)
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

}