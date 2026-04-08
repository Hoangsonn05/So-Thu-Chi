package com.example.sothuchi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

interface CategorySelectedListener {
    fun onSelected(category: String)
}

object CategoryBridge {
    @OptIn(ExperimentalMaterial3Api::class)
    @JvmStatic
    fun setupCategorySelector(
        composeView: ComposeView,
        categories: List<String>,
        listener: CategorySelectedListener
    ) {
        composeView.setContent {
            val selectedCategoryName = remember { mutableStateOf("") }
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { category ->
                    val isSelected = selectedCategoryName.value == category
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedCategoryName.value = category
                            listener.onSelected(category)
                        },
                        label = { Text(text = category, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFE8F5E9),
                            selectedLabelColor = Color(0xFF1B5E20)
                        )
                    )
                }
            }
        }
    }
}
