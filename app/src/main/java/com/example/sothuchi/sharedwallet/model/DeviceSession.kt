package com.example.sothuchi.sharedwallet.model

data class DeviceSession(
    val deviceId: String = "",
    val nickname: String = "",
    val deviceName: String = "",
    val lastActive: Long = 0L,
)
