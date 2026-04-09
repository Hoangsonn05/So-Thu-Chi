package com.example.sothuchi.sharedwallet.data

import android.os.Build
import com.example.sothuchi.realtime.model.FirestoreTransaction
import com.example.sothuchi.sharedwallet.firestore.awaitResult
import com.example.sothuchi.sharedwallet.identity.DeviceIdentityStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SharedWalletTransactionRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val identityStore: DeviceIdentityStore,
) {

    suspend fun addTransaction(transaction: FirestoreTransaction) {
        val uid = auth.currentUser?.uid ?: return

        val payload = transaction.copy(
            createdBy = identityStore.getNickname().ifBlank { "Unknown" },
            deviceName = Build.MODEL ?: "Android Device",
            deviceId = identityStore.getDeviceId(),
        )

        firestore.collection("users")
            .document(uid)
            .collection("transactions")
            .add(payload)
            .awaitResult()
    }
}
