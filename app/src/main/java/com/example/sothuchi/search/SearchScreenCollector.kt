package com.example.sothuchi.search

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

fun interface SearchScreenStateListener {
    fun onState(state: SearchScreenUiState)
}

object SearchScreenCollector {
    @JvmStatic
    fun collect(
        activity: AppCompatActivity,
        viewModel: SharedWalletSearchViewModel,
        listener: SearchScreenStateListener,
    ) {
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    listener.onState(state)
                }
            }
        }
    }
}
