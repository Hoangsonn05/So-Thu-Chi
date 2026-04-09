package com.example.sothuchi.search

import com.example.sothuchi.SearchListItem
import com.example.sothuchi.Transaction

data class SearchScreenUiState(
    val query: String = "",
    val selectedUser: String = ALL_USERS,
    val users: List<String> = listOf(ALL_USERS),
    val items: List<SearchListItem> = emptyList(),
    val filteredTransactions: List<Transaction> = emptyList(),
    val totalIncome: Long = 0L,
    val totalExpense: Long = 0L,
    val totalNet: Long = 0L,
) {
    companion object {
        const val ALL_USERS = "Tất cả"
    }
}
