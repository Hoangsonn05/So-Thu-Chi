package com.example.sothuchi.realtime.config

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

object FirestoreOfflineConfig {

    @Volatile
    private var initialized = false

    @Synchronized
    fun enableOfflinePersistence(firestore: FirebaseFirestore = FirebaseFirestore.getInstance()) {
        if (initialized) return

        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()

        firestore.firestoreSettings = settings
        initialized = true
    }
}
