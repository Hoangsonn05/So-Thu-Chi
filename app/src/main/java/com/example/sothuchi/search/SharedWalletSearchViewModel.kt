package com.example.sothuchi.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sothuchi.SearchListItem
import com.example.sothuchi.Transaction
import com.example.sothuchi.VietUtils
import com.example.sothuchi.realtime.data.TransactionRealtimeRepository
import com.example.sothuchi.realtime.model.FirestoreTransaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SharedWalletSearchViewModel(
    private val realtimeRepository: TransactionRealtimeRepository = TransactionRealtimeRepository(),
) : ViewModel() {

    constructor() : this(TransactionRealtimeRepository())

    private val queryFlow = MutableStateFlow("")
    private val selectedUserFlow = MutableStateFlow(SearchScreenUiState.ALL_USERS)

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val headerDateFormat = SimpleDateFormat("dd/MM/yyyy (E)", Locale("vi", "VN"))

    private val allTransactions: StateFlow<List<Transaction>> = realtimeRepository.observeTransactions()
        .map { list -> list.map { it.toTransaction(dateFormat) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<SearchScreenUiState> = combine(
        allTransactions,
        queryFlow,
        selectedUserFlow,
    ) { all, query, selectedUser ->
        val users = buildUserList(all)
        val activeUser = if (users.contains(selectedUser)) selectedUser else SearchScreenUiState.ALL_USERS
        val filtered = applyFilter(all, query, activeUser)

        val income = filtered.filter { it.type == 1 }.sumOf { it.amount }
        val expense = filtered.filter { it.type != 1 }.sumOf { it.amount }

        SearchScreenUiState(
            query = query,
            selectedUser = activeUser,
            users = users,
            items = buildGroupedItems(filtered),
            filteredTransactions = filtered,
            totalIncome = income,
            totalExpense = expense,
            totalNet = income - expense,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        SearchScreenUiState(),
    )

    fun setQuery(query: String) {
        queryFlow.value = query.trim()
    }

    fun setSelectedUser(user: String) {
        selectedUserFlow.value = if (user.isBlank()) SearchScreenUiState.ALL_USERS else user
    }

    private fun buildUserList(all: List<Transaction>): List<String> {
        val names = all.asSequence()
            .map { it.createdBy?.trim().orEmpty() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
            .toList()
        return listOf(SearchScreenUiState.ALL_USERS) + names
    }

    private fun applyFilter(all: List<Transaction>, query: String, selectedUser: String): List<Transaction> {
        val result = all.filter { t ->
            val matchQuery = query.isBlank() ||
                VietUtils.containsIgnoreAccent(t.category, query) ||
                VietUtils.containsIgnoreAccent(t.note, query) ||
                VietUtils.containsIgnoreAccent(t.date, query)

            val matchUser = selectedUser == SearchScreenUiState.ALL_USERS ||
                t.createdBy?.trim().orEmpty() == selectedUser

            matchQuery && matchUser
        }

        return result.sortedByDescending { parseDateSafe(it.date) ?: Date(0L) }
    }

    private fun buildGroupedItems(filtered: List<Transaction>): List<SearchListItem> {
        val items = mutableListOf<SearchListItem>()
        val grouped = linkedMapOf<String, MutableList<Transaction>>()

        filtered.forEach { t ->
            val date = t.date ?: ""
            val bucket = grouped.getOrPut(date) { mutableListOf() }
            bucket.add(t)
        }

        grouped.forEach { (dateStr, dayTransactions) ->
            val dayTotal = dayTransactions.sumOf { if (it.type == 1) it.amount else -it.amount }

            val headerText = parseDateSafe(dateStr)?.let { headerDateFormat.format(it) } ?: dateStr
            val dayTotalPrefix = if (dayTotal >= 0) "+" else ""
            val dayTotalText = "($dayTotalPrefix${dayTotal}đ)"
            val fullHeader = "$headerText $dayTotalText"

            items.add(SearchListItem.Header(fullHeader, dayTotal))
            dayTransactions.forEach { items.add(SearchListItem.TransactionRow(it)) }
        }

        return items
    }

    private fun parseDateSafe(value: String?): Date? {
        if (value.isNullOrBlank()) return null
        return runCatching { dateFormat.parse(value) }.getOrNull()
    }
}

private fun FirestoreTransaction.toTransaction(dateFormat: SimpleDateFormat): Transaction {
    val dateValue = timestamp?.toDate()?.let { dateFormat.format(it) } ?: ""
    return Transaction(amount, note, category, dateValue, type).apply {
        createdBy = this@toTransaction.createdBy
        deviceName = this@toTransaction.deviceName
    }
}
