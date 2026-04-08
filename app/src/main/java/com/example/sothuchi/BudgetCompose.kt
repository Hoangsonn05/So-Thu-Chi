package com.example.sothuchi

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import java.util.*
import kotlin.jvm.JvmStatic

data class BudgetItem(
    val categoryName: String,
    val budgetAmount: Long,
    val spentAmount: Long,
    val colorArgb: Long = 0xFF1EBE5DL
) {
    val remaining: Long get() = budgetAmount - spentAmount
    val percent: Float get() = if (budgetAmount > 0) spentAmount.toFloat() / budgetAmount else 0f
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    monthYear: String,
    dateRange: String,
    budgetItems: List<BudgetItem>,
    onPrevClick: () -> Unit,
    onNextClick: () -> Unit,
    onMonthClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF8F9FA)
    ) {
        Column {
            // Header Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    painter = painterResource(id = android.R.drawable.ic_menu_camera),
                    contentDescription = "Camera",
                    tint = Color(0xFFFFA500),
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = "Ngân sách",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Icon(
                    painter = painterResource(id = android.R.drawable.ic_menu_preferences),
                    contentDescription = "Settings",
                    tint = Color(0xFF1EBE5D),
                    modifier = Modifier.size(28.dp)
                )
            }

            // Date Selection Area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevClick) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_media_previous),
                        contentDescription = "Prev",
                        tint = Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Surface(
                    onClick = onMonthClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF1F3F4)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = monthYear,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = dateRange,
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }

                IconButton(onClick = onNextClick) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_media_next),
                        contentDescription = "Next",
                        tint = Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Dynamic Budget List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(budgetItems) { item ->
                    BudgetCardItem(item)
                }
            }
        }
    }
}

@Composable
fun BudgetCardItem(item: BudgetItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = item.categoryName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                
                val remainingColor = if (item.remaining < 0) Color.Red else Color.Black
                Text(
                    text = "Còn lại: ${String.format("%,d", item.remaining)}đ",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = remainingColor
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Specialized Progress Bar handling > 100%
            val progress = item.percent.coerceIn(0f, 1f)
            val barColor = if (item.percent >= 1.0f) Color(0xFFFF5252) else Color(item.colorArgb)
            
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
                color = barColor,
                trackColor = Color(0xFFF0F0F0),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Ngân sách: ${String.format("%,d", item.budgetAmount)}đ",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                
                Text(
                    text = "${(item.percent * 100).toInt()}%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )

                Text(
                    text = "Chi tiêu: ${String.format("%,d", item.spentAmount)}đ",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

object BudgetBridge {
    @JvmStatic
    fun setupBudgetScreen(
        composeView: ComposeView,
        monthYear: String,
        dateRange: String,
        budgetItems: List<BudgetItem>,
        onPrev: Runnable,
        onNext: Runnable,
        onMonth: Runnable
    ) {
        composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                BudgetScreen(
                    monthYear = monthYear,
                    dateRange = dateRange,
                    budgetItems = budgetItems,
                    onPrevClick = { onPrev.run() },
                    onNextClick = { onNext.run() },
                    onMonthClick = { onMonth.run() }
                )
            }
        }
    }
}
