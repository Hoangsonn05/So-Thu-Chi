package com.example.sothuchi;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class CalendarAdapter extends RecyclerView.Adapter<CalendarAdapter.CalendarViewHolder> {

    private final List<DayModel> days;
    private OnDayClickListener listener;
    private int selectedPosition = -1;
    private long lastClickTime = 0;

    public interface OnDayClickListener {
        void onDayClick(DayModel day);
        void onDayDoubleClick(DayModel day);
    }

    public CalendarAdapter(List<DayModel> days, OnDayClickListener listener) {
        this.days = days;
        this.listener = listener;
    }

    @NonNull
    @Override
    public CalendarViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_calendar_day, parent, false);
        return new CalendarViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CalendarViewHolder holder, int position) {
        DayModel day = days.get(position);
        
        holder.tvDay.setText(day.day);
        
        if (day.income > 0) {
            holder.tvIncome.setText("+" + day.income);
            holder.tvIncome.setVisibility(View.VISIBLE);
        } else {
            holder.tvIncome.setVisibility(View.GONE);
        }

        if (day.expense > 0) {
            holder.tvExpense.setText("-" + day.expense);
            holder.tvExpense.setVisibility(View.VISIBLE);
        } else {
            holder.tvExpense.setVisibility(View.GONE);
        }

        if (!day.isCurrentMonth) {
            holder.tvDay.setTextColor(Color.LTGRAY);
        } else {
            holder.tvDay.setTextColor(Color.BLACK);
        }

        // Hiện thị trạng thái chọn (Highlight)
        if (selectedPosition == position) {
            holder.itemView.setBackgroundResource(R.color.selected_day_bg);
        } else {
            holder.itemView.setBackgroundColor(Color.TRANSPARENT);
        }

        holder.itemView.setOnClickListener(v -> {
            long clickTime = System.currentTimeMillis();
            if (clickTime - lastClickTime < 300) {
                // Hành động Double click
                if (listener != null) listener.onDayDoubleClick(day);
            } else {
                // Hành động Single click
                int previousSelected = selectedPosition;
                selectedPosition = position;
                
                // Cập nhật lại UI cho ô cũ và ô mới chọn
                notifyItemChanged(previousSelected);
                notifyItemChanged(selectedPosition);

                if (listener != null) listener.onDayClick(day);
            }
            lastClickTime = clickTime;
        });
    }

    @Override
    public int getItemCount() {
        return days.size();
    }

    public static class CalendarViewHolder extends RecyclerView.ViewHolder {
        TextView tvDay, tvIncome, tvExpense;

        public CalendarViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDay = itemView.findViewById(R.id.tv_day);
            tvIncome = itemView.findViewById(R.id.tv_income);
            tvExpense = itemView.findViewById(R.id.tv_expense);
        }
    }

    public static class DayModel {
        public String day;
        public long income;
        public long expense;
        public boolean isCurrentMonth;

        public DayModel(String day, long income, long expense, boolean isCurrentMonth) {
            this.day = day;
            this.income = income;
            this.expense = expense;
            this.isCurrentMonth = isCurrentMonth;
        }
    }
}
