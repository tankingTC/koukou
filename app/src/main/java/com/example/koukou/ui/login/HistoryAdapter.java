package com.example.koukou.ui.login;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.koukou.R;
import com.example.koukou.utils.AppearanceManager;
import com.example.koukou.utils.AvatarHelper;
import com.example.koukou.utils.UserHelper;

import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
    private final List<UserHelper.SavedLogin> list;
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(UserHelper.SavedLogin item);
        void onDeleteClick(UserHelper.SavedLogin item, int position);
    }

    public HistoryAdapter(List<UserHelper.SavedLogin> list, OnItemClickListener listener) {
        this.list = list;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_account_history, parent, false);
        ViewHolder holder = new ViewHolder(view);
        // Force the foreground to be exactly as wide as the RecyclerView width
        int width = parent.getMeasuredWidth();
        ViewGroup.LayoutParams lp = holder.foreground.getLayoutParams();
        lp.width = width;
        holder.foreground.setLayoutParams(lp);
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        UserHelper.SavedLogin item = list.get(position);
        holder.scrollView.scrollTo(0, 0); // Reset scroll position for reused views
        holder.tvNickname.setText(item.nickname == null || item.nickname.trim().isEmpty() ? item.account : item.nickname);
        holder.tvAccount.setText(item.account);
        AvatarHelper.loadAvatar(holder.ivAvatar, item.avatar);
        holder.foreground.setOnClickListener(v -> listener.onItemClick(item));
        holder.tvDelete.setOnClickListener(v -> listener.onDeleteClick(item, holder.getAdapterPosition()));
        AppearanceManager.applyItemAppearance(holder.itemView.getContext(), holder.itemView);
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        HorizontalScrollView scrollView;
        ImageView ivAvatar;
        TextView tvNickname;
        TextView tvAccount;
        TextView tvDelete;
        View foreground;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            scrollView = (HorizontalScrollView) itemView;
            ivAvatar = itemView.findViewById(R.id.iv_avatar);
            tvNickname = itemView.findViewById(R.id.tv_nickname);
            tvAccount = itemView.findViewById(R.id.tv_account);
            tvDelete = itemView.findViewById(R.id.tv_delete);
            foreground = itemView.findViewById(R.id.ll_foreground);
        }
    }
}
