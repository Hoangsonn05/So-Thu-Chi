package com.example.sothuchi;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {

    private final List<CategoryModel> categories;
    private final int selectionColor;
    private int selectedPosition = -1;
    private OnCategorySelectedListener listener;

    public interface OnCategorySelectedListener {
        void onCategorySelected(CategoryModel category);
    }

    public CategoryAdapter(List<CategoryModel> categories, int selectionColor, OnCategorySelectedListener listener) {
        this.categories = categories;
        this.selectionColor = selectionColor;
        this.listener = listener;
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Có thể lồng một logic kiểm tra layout ở đây nếu cần icon red/green riêng, nhưng tạm thời dùng chung
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        CategoryModel category = categories.get(position);
        holder.tvName.setText(category.name);
        holder.ivIcon.setImageResource(category.iconRes);
        holder.ivIcon.setColorFilter(selectionColor);

        // Hiệu ứng trạng thái chọn (Selection State) linh hoạt màu
        if (selectedPosition == position) {
            holder.cardCategory.setStrokeWidth(6); // 2dp approx
            holder.cardCategory.setStrokeColor(selectionColor);
        } else {
            holder.cardCategory.setStrokeWidth(2); // 0.7dp approx/default
            holder.cardCategory.setStrokeColor(Color.parseColor("#F0F0F0"));
        }

        holder.itemView.setOnClickListener(v -> {
            int previousPosition = selectedPosition;
            selectedPosition = holder.getAdapterPosition();
            
            notifyItemChanged(previousPosition);
            notifyItemChanged(selectedPosition);

            if (listener != null) {
                listener.onCategorySelected(category);
            }
        });
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    public static class CategoryViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardCategory;
        ImageView ivIcon;
        TextView tvName;

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            cardCategory = itemView.findViewById(R.id.card_category);
            ivIcon = itemView.findViewById(R.id.iv_icon);
            tvName = itemView.findViewById(R.id.tv_name);
        }
    }

    public static class CategoryModel {
        public String name;
        public int iconRes;

        public CategoryModel(String name, int iconRes) {
            this.name = name;
            this.iconRes = iconRes;
        }
    }
}
