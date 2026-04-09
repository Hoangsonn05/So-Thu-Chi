package com.example.sothuchi

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat
import java.util.Locale

/**
 * RecyclerView Adapter for the Transaction Search screen.
 *
 * Handles two view types:
 * - Date headers (grouped by day)
 * - Transaction items (individual entries under each day)
 *
 * Reuses existing layouts: item_date_header.xml & item_transaction.xml
 */
class SearchAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_TRANSACTION = 1

        // Category → icon mapping (same as setupTienChiRecyclerView & setupTienThuRecyclerView)
        private val CATEGORY_ICONS = mapOf(
            // Expense categories
            "Ăn uống" to android.R.drawable.ic_menu_today,
            "Chi tiêu" to android.R.drawable.ic_menu_manage,
            "Chi tiêu hàng ngày" to android.R.drawable.ic_menu_manage,
            "Quần áo" to android.R.drawable.ic_menu_gallery,
            "Mỹ phẩm" to android.R.drawable.ic_menu_camera,
            "Giao lưu" to android.R.drawable.ic_menu_share,
            "Phí giao lưu" to android.R.drawable.ic_menu_share,
            "Y tế" to android.R.drawable.ic_menu_call,
            "Giáo dục" to android.R.drawable.ic_menu_sort_alphabetically,
            "Tiền điện" to android.R.drawable.ic_menu_info_details,
            "Du lịch" to android.R.drawable.ic_menu_directions,
            "Liên lạc" to android.R.drawable.ic_menu_send,
            "Phí liên lạc" to android.R.drawable.ic_menu_send,
            "Tiền nhà" to android.R.drawable.ic_menu_view,
            "Khác" to android.R.drawable.ic_menu_help,
            // Income categories
            "Tiền lương" to android.R.drawable.ic_menu_my_calendar,
            "Tiền phụ cấp" to android.R.drawable.ic_menu_save,
            "Tiền thưởng" to android.R.drawable.ic_menu_send,
            "Thu nhập phụ" to android.R.drawable.ic_input_add,
            "Đầu tư" to android.R.drawable.ic_menu_gallery,
            "Thu nhập tạm" to android.R.drawable.ic_menu_manage,
            "Thu nhập tạm thời" to android.R.drawable.ic_menu_manage
        )
    }

    /** Items list (headers + transactions), set externally */
    private var items: List<SearchListItem> = emptyList()

    /** Update the displayed data */
    fun submitList(newItems: List<SearchListItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is SearchListItem.Header -> TYPE_HEADER
            is SearchListItem.TransactionRow -> TYPE_TRANSACTION
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(inflater.inflate(R.layout.item_date_header, parent, false))
        } else {
            TransactionViewHolder(inflater.inflate(R.layout.item_transaction, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        when (holder) {
            is HeaderViewHolder -> {
                val header = item as SearchListItem.Header
                holder.tvDate.text = header.displayText
                val fmt = NumberFormat.getInstance(Locale("vi", "VN"))
                val totalStr = (if (header.dayTotal >= 0) "+" else "") +
                        fmt.format(header.dayTotal) + "đ"
                holder.tvTotal.text = totalStr
                holder.tvTotal.setTextColor(
                    if (header.dayTotal >= 0) 0xFF2196F3.toInt() else 0xFFF44336.toInt()
                )
            }
            is TransactionViewHolder -> {
                val row = item as SearchListItem.TransactionRow
                val t = row.transaction

                holder.tvCategory.text = t.category ?: ""
                holder.tvNote.text = t.note ?: ""
                holder.tvNote.visibility = if (t.note.isNullOrBlank()) View.GONE else View.VISIBLE

                val createdBy = t.createdBy?.takeIf { it.isNotBlank() } ?: "Không rõ"
                val deviceName = t.deviceName?.takeIf { it.isNotBlank() } ?: "Thiết bị"
                holder.tvMeta.text = "Nhập bởi: $createdBy • Thiết bị: $deviceName"

                // Amount formatting
                val fmt = NumberFormat.getInstance(Locale("vi", "VN"))
                val prefix = if (t.type == 1) "+" else "-"
                val amountStr = "$prefix${fmt.format(t.amount)}đ"
                holder.tvAmount.text = amountStr

                val context = holder.itemView.context
                if (t.type == 1) {
                    // Income → Blue
                    holder.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.saturday_blue))
                } else {
                    // Expense → Red
                    holder.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.sunday_red))
                }

                // Category icon
                val iconRes = CATEGORY_ICONS[t.category] ?: android.R.drawable.ic_menu_edit
                holder.ivIcon.setImageResource(iconRes)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    // ── ViewHolders ──

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDate: TextView = view.findViewById(R.id.tv_header_date)
        val tvTotal: TextView = view.findViewById(R.id.tv_header_total)
    }

    class TransactionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvCategory: TextView = view.findViewById(R.id.tv_category)
        val tvMeta: TextView = view.findViewById(R.id.tv_meta_user_device)
        val tvNote: TextView = view.findViewById(R.id.tv_note)
        val tvAmount: TextView = view.findViewById(R.id.tv_amount)
        val ivIcon: ImageView = view.findViewById(R.id.iv_cat_icon)
    }
}

/**
 * Sealed class representing items in the search results list.
 */
sealed class SearchListItem {
    /**
     * Date group header.
     * @param displayText Full display: "28/03/2026 (Th 7)"
     * @param dayTotal Net total for this date (income - expense)
     */
    data class Header(
        val displayText: String,
        val dayTotal: Long
    ) : SearchListItem()

    /**
     * Single transaction row.
     */
    data class TransactionRow(
        val transaction: Transaction
    ) : SearchListItem()
}
