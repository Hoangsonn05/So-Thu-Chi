package com.example.sothuchi.sharedwallet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sothuchi.sharedwallet.data.DeviceSessionRepository
import com.example.sothuchi.sharedwallet.model.DeviceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

class DeviceManagementViewModel(
    repository: DeviceSessionRepository = DeviceSessionRepository(),
) : ViewModel() {

    constructor() : this(DeviceSessionRepository())

    val devices: StateFlow<List<DeviceSession>> = repository.observeDeviceSessions()
        .flowOn(Dispatchers.IO)
        .catch { emit(emptyList()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )
}
