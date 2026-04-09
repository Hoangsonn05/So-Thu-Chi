package com.example.sothuchi.realtime.ui

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.sothuchi.realtime.model.FirestoreTransaction
import kotlinx.coroutines.launch

fun interface OnTransactionsUpdateListener {
    fun onUpdate(transactions: List<FirestoreTransaction>)
}

object TransactionRealtimeCollector {

    @JvmStatic
    fun collect(
        activity: AppCompatActivity,
        viewModel: TransactionRealtimeViewModel,
        listener: OnTransactionsUpdateListener,
    ) {
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.transactions.collect { items ->
                    listener.onUpdate(items)
                }
            }
        }
    }
}
