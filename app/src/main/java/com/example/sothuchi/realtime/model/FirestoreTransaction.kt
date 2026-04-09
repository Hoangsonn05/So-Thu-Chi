package com.example.sothuchi.realtime.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

/**
 * Firestore model generated from current transaction write keys:
 * amount, note, category, type, timestamp, yearMonth, year, lastUpdated
 */
data class FirestoreTransaction(
    val firebaseDocId: String = "",
    val amount: Long = 0L,
    val note: String = "",
    val category: String = "",
    val type: Int = 0,
    @get:PropertyName("createdBy")
    @set:PropertyName("createdBy")
    var createdBy: String = "Người dùng ẩn danh",
    @get:PropertyName("devices")
    @set:PropertyName("devices")
    var deviceName: String = "Không rõ thiết bị",
    @get:PropertyName("deviceId")
    @set:PropertyName("deviceId")
    var deviceId: String = "",
    val timestamp: Timestamp? = null,
    val yearMonth: String = "",
    val year: Int = 0,
    val lastUpdated: Timestamp? = null,
)