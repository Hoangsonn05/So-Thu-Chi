package com.example.sothuchi.sharedwallet.data

import com.example.sothuchi.sharedwallet.firestore.awaitResult
import com.example.sothuchi.sharedwallet.model.DeviceSession
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

class DeviceSessionRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {

    suspend fun isNicknameTakenByAnotherDevice(deviceId: String, nickname: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val uid = auth.currentUser?.uid ?: return@withContext false

                val snapshot = firestore.collection("users")
                    .document(uid)
                    .collection("devices")
                    .whereEqualTo("nickname", nickname)
                    .get()
                    .awaitResult()

                snapshot.documents.any { it.id != deviceId }
            } catch (_: Exception) {
                false
            }
        }
    }

    suspend fun upsertSession(session: DeviceSession) {
        withContext(Dispatchers.IO) {
            try {
                val uid = auth.currentUser?.uid ?: return@withContext
                firestore.collection("users")
                    .document(uid)
                    .collection("devices")
                    .document(session.deviceId)
                    .set(session)
                    .awaitResult()
            } catch (_: Exception) {
                // Prevent fatal crashes from bubbling to UI scope.
            }
        }
    }

    fun observeDeviceSessions(): Flow<List<DeviceSession>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val registration: ListenerRegistration = firestore.collection("users")
            .document(uid)
            .collection("devices")
            .orderBy("lastActive", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList()).isSuccess
                    return@addSnapshotListener
                }

                val list = try {
                    snapshot?.documents
                        ?.map { doc ->
                            val nickname = doc.getString("nickname").orEmpty()
                            val deviceName = doc.getString("deviceName").orEmpty()
                            val lastActiveRaw = doc.get("lastActive")
                            val lastActive = when (lastActiveRaw) {
                                is Long -> lastActiveRaw
                                is Timestamp -> lastActiveRaw.toDate().time
                                else -> 0L
                            }
                            DeviceSession(
                                deviceId = doc.id,
                                nickname = nickname,
                                deviceName = deviceName,
                                lastActive = lastActive,
                            )
                        }
                        ?: emptyList()
                } catch (_: Exception) {
                    emptyList()
                }

                trySend(list).isSuccess
            }

        awaitClose { registration.remove() }
    }
}
