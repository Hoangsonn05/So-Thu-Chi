package com.example.sothuchi.sharedwallet.data

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.sothuchi.sharedwallet.firestore.awaitResult
import com.example.sothuchi.sharedwallet.identity.DeviceIdentityStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object UsernameSyncManager {

    interface Callback {
        fun onSuccess(updatedCount: Int)
        fun onError(message: String)
    }

    @JvmStatic
    fun changeUsername(
        activity: AppCompatActivity,
        newNickname: String,
        callback: Callback,
    ) {
        activity.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    performUsernameChange(
                        activity = activity,
                        newNickname = newNickname.trim(),
                    )
                }
            }

            result.onSuccess { updatedCount ->
                callback.onSuccess(updatedCount)
            }.onFailure { throwable ->
                callback.onError(throwable.message ?: "Không thể cập nhật username")
            }
        }
    }

    private suspend fun performUsernameChange(
        activity: AppCompatActivity,
        newNickname: String,
    ): Int {
        if (newNickname.isBlank()) {
            throw IllegalArgumentException("Vui lòng nhập username")
        }

        val auth = FirebaseAuth.getInstance()
        val firestore = FirebaseFirestore.getInstance()
        val user = auth.currentUser ?: throw IllegalStateException("Chưa đăng nhập")

        val identityStore = DeviceIdentityStore(activity)
        val currentDeviceId = identityStore.getDeviceId()
        val deviceModel = identityStore.getDeviceName()

        identityStore.saveNickname(newNickname)

        val userDoc = firestore.collection("users").document(user.uid)

        val deviceData = hashMapOf<String, Any>(
            "deviceId" to currentDeviceId,
            "nickname" to newNickname,
            "deviceName" to deviceModel,
            "lastActive" to FieldValue.serverTimestamp(),
        )

        userDoc.collection("devices")
            .document(currentDeviceId)
            .set(deviceData, SetOptions.merge())
            .awaitResult()

        // Keep scope aligned with existing realtime listeners: users/{uid}/transactions
        val transactionsRef = userDoc.collection("transactions")
        val querySnapshot = transactionsRef
            .whereEqualTo("deviceId", currentDeviceId)
            .get()
            .awaitResult()

        val batch = firestore.batch()
        var updatedCount = 0

        for (doc in querySnapshot.documents) {
            batch.update(doc.reference, "createdBy", newNickname)
            updatedCount++
        }

        if (updatedCount > 0) {
            batch.commit().awaitResult()
        }

        return updatedCount
    }
}
