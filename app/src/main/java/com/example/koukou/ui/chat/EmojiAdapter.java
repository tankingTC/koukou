package com.example.koukou.ui.chat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.koukou.R;
import java.util.List;

public class EmojiAdapter extends RecyclerView.Adapter<EmojiAdapter.EmojiViewHolder> {

    private final List<Integer> emojiIds;
    private final OnEmojiClickListener clickListener;

    public EmojiAdapter(List<Integer> emojiIds, OnEmojiClickListener clickListener) {
        this.emojiIds = emojiIds;
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public EmojiViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ImageView imageView = new ImageView(parent.getContext());
        imageView.setLayoutParams(new ViewGroup.LayoutParams(120, 120));
        imageView.setPadding(16, 16, 16, 16);
        return new EmojiViewHolder(imageView);
    }

    @Override
    public void onBindViewHolder(@NonNull EmojiViewHolder holder, int position) {
        int emojiResId = emojiIds.get(position);
        holder.imageView.setImageResource(emojiResId);
        holder.imageView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onEmojiClick(emojiResId);
            }
        });
    }

    @Override
    public int getItemCount() {
        return emojiIds == null ? 0 : emojiIds.size();
    }

    public static class EmojiViewHolder extends RecyclerView.ViewHolder {
        final ImageView imageView;

        public EmojiViewHolder(@NonNull View itemView) {
            super(itemView);
            this.imageView = (ImageView) itemView;
        }
    }

    public interface OnEmojiClickListener {
        void onEmojiClick(int emojiResId);
    }
}