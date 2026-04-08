package com.example.sothuchi;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<HistoryItem> items;

    public HistoryAdapter(List<HistoryItem> items) {
        this.items = items;
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == HistoryItem.TYPE_HEADER) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_date_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transaction, parent, false);
            return new TransactionViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        HistoryItem item = items.get(position);
        if (holder instanceof HeaderViewHolder) {
            HeaderViewHolder h = (HeaderViewHolder) holder;
            h.tvDate.setText(item.date);
            String totalStr = (item.total >= 0 ? "+" : "") + item.total + "đ";
            h.tvTotal.setText(totalStr);
            h.tvTotal.setTextColor(item.total >= 0 ? 0xFF4CAF50 : 0xFFF44336); // Green/Red
        } else if (holder instanceof TransactionViewHolder) {
            TransactionViewHolder t = (TransactionViewHolder) holder;
            Transaction trans = item.transaction;
            t.tvCategory.setText(trans.getCategory());
            t.tvNote.setText(trans.getNote());
            
            String amountStr = (trans.getType() == 1 ? "+" : "-") + trans.getAmount() + "đ";
            t.tvAmount.setText(amountStr);
            t.tvAmount.setTextColor(trans.getType() == 1 ? 0xFF4CAF50 : 0xFFF44336);
            
            // Set icon placeholder (you can map category to icon here)
            t.ivIcon.setImageResource(android.R.drawable.ic_menu_edit);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvDate, tvTotal;
        public HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tv_header_date);
            tvTotal = itemView.findViewById(R.id.tv_header_total);
        }
    }

    public static class TransactionViewHolder extends RecyclerView.ViewHolder {
        TextView tvCategory, tvNote, tvAmount;
        ImageView ivIcon;
        public TransactionViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCategory = itemView.findViewById(R.id.tv_category);
            tvNote = itemView.findViewById(R.id.tv_note);
            tvAmount = itemView.findViewById(R.id.tv_amount);
            ivIcon = itemView.findViewById(R.id.iv_cat_icon);
        }
    }

    public static class HistoryItem {
        public static final int TYPE_HEADER = 0;
        public static final int TYPE_TRANSACTION = 1;

        public int type;
        public String date;
        public long total;
        public Transaction transaction;

        public HistoryItem(String date, long total) {
            this.type = TYPE_HEADER;
            this.date = date;
            this.total = total;
        }

        public HistoryItem(Transaction transaction) {
            this.type = TYPE_TRANSACTION;
            this.transaction = transaction;
        }
    }
}
