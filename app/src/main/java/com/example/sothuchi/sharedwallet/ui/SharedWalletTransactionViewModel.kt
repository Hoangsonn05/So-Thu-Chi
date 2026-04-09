package com.example.sothuchi.sharedwallet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sothuchi.realtime.model.FirestoreTransaction
import com.example.sothuchi.sharedwallet.data.SharedWalletTransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SharedWalletTransactionViewModel(
    private val repository: SharedWalletTransactionRepository,
) : ViewModel() {

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    fun addTransaction(transaction: FirestoreTransaction) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                repository.addTransaction(transaction)
            } finally {
                _isSaving.value = false
            }
        }
    }
}
