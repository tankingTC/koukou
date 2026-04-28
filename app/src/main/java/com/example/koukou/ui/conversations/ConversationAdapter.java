package com.example.koukou.ui.conversations;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.example.koukou.data.local.entity.ConversationEntity;
import com.example.koukou.databinding.ItemConversationBinding;
import com.example.koukou.utils.AppearanceManager;
import com.example.koukou.utils.AvatarHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ConversationAdapter extends ListAdapter<ConversationEntity, ConversationAdapter.ViewHolder> {

    public interface OnConversationClickListener {
        void onClick(ConversationEntity conversation);
    }

    private final OnConversationClickListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public ConversationAdapter(OnConversationClickListener listener) {
        super(new DiffUtil.ItemCallback<ConversationEntity>() {
            @Override
            public boolean areItemsTheSame(@NonNull ConversationEntity oldItem, @NonNull ConversationEntity newItem) {
                return oldItem.conversationId.equals(newItem.conversationId);   
            }

            @Override
            public boolean areContentsTheSame(@NonNull ConversationEntity oldItem, @NonNull ConversationEntity newItem) {
                return oldItem.lastMessageTime == newItem.lastMessageTime &&    
                       oldItem.unreadCount == newItem.unreadCount &&
                       java.util.Objects.equals(oldItem.lastMessage, newItem.lastMessage) &&
                       java.util.Objects.equals(oldItem.targetName, newItem.targetName) &&
                       java.util.Objects.equals(oldItem.targetAvatarUrl, newItem.targetAvatarUrl);
            }
        });
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemConversationBinding binding = ItemConversationBinding.inflate(      
                LayoutInflater.from(parent.getContext()), parent, false);       
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {    
        ConversationEntity entity = getItem(position);
        holder.bind(entity);
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemConversationBinding binding;

        ViewHolder(ItemConversationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.getRoot().setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) { 
                    v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100)    
                            .withEndAction(() -> {
                                v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                                listener.onClick(getItem(position));
                            }).start();
                }
            });
        }

        void bind(ConversationEntity entity) {
            AvatarHelper.loadAvatar(binding.ivAvatar, entity.targetAvatarUrl);
            binding.tvName.setText(entity.targetName != null ? entity.targetName : entity.targetId);
            binding.tvLastMessage.setText(entity.lastMessage != null && !entity.lastMessage.isEmpty() ? entity.lastMessage : "点击开始聊天");
            
            if (entity.lastMessageTime > 0) {
                binding.tvTime.setText(dateFormat.format(new Date(entity.lastMessageTime)));
            } else {
                binding.tvTime.setText("");
            }

            if (entity.unreadCount > 0) {
                binding.tvUnread.setVisibility(View.VISIBLE);
                binding.tvUnread.setText(String.valueOf(entity.unreadCount));   
            } else {
                binding.tvUnread.setVisibility(View.GONE);
            }

            if (entity.targetAvatarUrl != null) {
                int resId = binding.getRoot().getContext().getResources().getIdentifier(
                        entity.targetAvatarUrl, "drawable", binding.getRoot().getContext().getPackageName());
                if (resId == 0) {
                    resId = binding.getRoot().getContext().getResources().getIdentifier(
                            entity.targetAvatarUrl, "mipmap", binding.getRoot().getContext().getPackageName());
                }
                if (resId != 0) {
                    binding.ivAvatar.setImageResource(resId);
                } else {
                    binding.ivAvatar.setImageResource(com.example.koukou.R.mipmap.tubiao);
                }
            } else {
                binding.ivAvatar.setImageResource(com.example.koukou.R.mipmap.tubiao);
            }
            AppearanceManager.applyItemAppearance(binding.getRoot().getContext(), binding.getRoot());
        }
    }
}
