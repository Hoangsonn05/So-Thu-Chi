package com.example.sothuchi.realtime.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sothuchi.realtime.data.TransactionRealtimeRepository
import com.example.sothuchi.realtime.model.FirestoreTransaction
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class TransactionRealtimeViewModel(
    private val repository: TransactionRealtimeRepository = TransactionRealtimeRepository(),
) : ViewModel() {

    constructor() : this(TransactionRealtimeRepository())

    val transactions: StateFlow<List<FirestoreTransaction>> =
        repository.observeTransactions()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )
}
