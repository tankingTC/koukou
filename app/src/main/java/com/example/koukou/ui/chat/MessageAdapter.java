package com.example.koukou.ui.chat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.koukou.data.local.entity.MessageEntity;
import com.example.koukou.databinding.ItemMsgReceiveBinding;
import com.example.koukou.databinding.ItemMsgSendBinding;
import com.example.koukou.utils.AppearanceManager;
import com.example.koukou.utils.AvatarHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class MessageAdapter extends ListAdapter<MessageEntity, RecyclerView.ViewHolder> {

    private static final int TYPE_RIGHT_SELF = 1;
    private static final int TYPE_LEFT_OTHER = 2;
    private final String currentUserId;
    private final String selfName;
    private final String selfAvatar;
    private final String targetName;
    private final String targetAvatar;
    private int lastAnimatedPosition = -1;
    private final Set<String> pendingHighlightMessageIds = new HashSet<>();

    public MessageAdapter(String currentUserId, String selfName, String selfAvatar, String targetName, String targetAvatar) {
        super(new DiffUtil.ItemCallback<MessageEntity>() {
            @Override
            public boolean areItemsTheSame(@NonNull MessageEntity oldItem, @NonNull MessageEntity newItem) {
                return Objects.equals(oldItem.messageId, newItem.messageId);
            }

            @Override
            public boolean areContentsTheSame(@NonNull MessageEntity oldItem, @NonNull MessageEntity newItem) {
                return Objects.equals(oldItem.content, newItem.content) &&
                       Objects.equals(oldItem.status, newItem.status) &&
                       Objects.equals(oldItem.msgType, newItem.msgType);
            }
        });
        this.currentUserId = currentUserId;
        this.selfName = selfName;
        this.selfAvatar = selfAvatar;
        this.targetName = targetName;
        this.targetAvatar = targetAvatar;
    }

    @Override
    public int getItemViewType(int position) {
        MessageEntity message = getItem(position);
        if (isMyMessage(message)) {
            return TYPE_RIGHT_SELF;
        } else {
            return TYPE_LEFT_OTHER;
        }
    }

    @Override
    public void submitList(List<MessageEntity> list) {
        markNewIncomingMessages(list);
        super.submitList(list);
    }

    private void markNewIncomingMessages(List<MessageEntity> newList) {
        if (newList == null || newList.isEmpty()) {
            return;
        }
        Set<String> oldIds = new HashSet<>();
        List<MessageEntity> oldList = new ArrayList<>(getCurrentList());
        for (MessageEntity old : oldList) {
            if (old != null && old.messageId != null) {
                oldIds.add(old.messageId);
            }
        }

        for (MessageEntity msg : newList) {
            if (msg == null || msg.messageId == null) {
                continue;
            }
            if (!oldIds.contains(msg.messageId) && !isMyMessage(msg)) {
                pendingHighlightMessageIds.add(msg.messageId);
            }
        }
    }

    private boolean isMyMessage(MessageEntity message) {
        return message != null && Objects.equals(message.senderId, currentUserId);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_RIGHT_SELF) {
            ItemMsgSendBinding binding = ItemMsgSendBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
            return new SelfRightViewHolder(binding);
        } else {
            ItemMsgReceiveBinding binding = ItemMsgReceiveBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
            return new OtherLeftViewHolder(binding);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        MessageEntity message = getItem(position);

        if (holder instanceof SelfRightViewHolder) {
            ((SelfRightViewHolder) holder).bind(message);
        } else if (holder instanceof OtherLeftViewHolder) {
            ((OtherLeftViewHolder) holder).bind(message);
        }

        playStaggerIn(holder.itemView, position);

        if (message != null && message.messageId != null && pendingHighlightMessageIds.remove(message.messageId)) {
            View bubbleView;
            if (holder instanceof SelfRightViewHolder) {
                bubbleView = ((SelfRightViewHolder) holder).binding.tvContent;
            } else {
                bubbleView = ((OtherLeftViewHolder) holder).binding.tvContent;
            }
            playHighlightFlash(bubbleView);
        }
        AppearanceManager.applyItemAppearance(holder.itemView.getContext(), holder.itemView);
    }

    private void playStaggerIn(android.view.View itemView, int position) {
        if (position <= lastAnimatedPosition) {
            return;
        }
        itemView.setAlpha(0f);
        itemView.setTranslationY(22f);
        itemView.setScaleX(0.98f);
        itemView.setScaleY(0.98f);
        itemView.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay((position % 6) * 16L)
                .setDuration(240)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
        lastAnimatedPosition = position;
    }

    private void playHighlightFlash(View bubbleView) {
        bubbleView.animate().cancel();
        bubbleView.setAlpha(0.55f);
        bubbleView.animate()
                .alpha(1f)
                .setDuration(300)
                .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                .start();
    }

    class SelfRightViewHolder extends RecyclerView.ViewHolder {
        final ItemMsgSendBinding binding;

        SelfRightViewHolder(ItemMsgSendBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(MessageEntity message) {
            binding.tvName.setText(selfName != null && !selfName.isEmpty() ? selfName : "我");
            AvatarHelper.loadAvatar(binding.ivAvatar, selfAvatar);
            
            if ("image".equals(message.msgType) || "video".equals(message.msgType)) {
                binding.tvContent.setVisibility(View.GONE);
                binding.ivImage.setVisibility(View.VISIBLE);
                
                String path = message.localPath != null ? message.localPath : message.content;
                Glide.with(binding.ivImage.getContext()).load(path).into(binding.ivImage);
                
                binding.ivVideoPlay.setVisibility("video".equals(message.msgType) ? View.VISIBLE : View.GONE);
            } else if ("emoji".equals(message.msgType)) {
                binding.tvContent.setVisibility(View.GONE);
                binding.ivVideoPlay.setVisibility(View.GONE);
                binding.ivImage.setVisibility(View.VISIBLE);
                
                // Content is unicode or res string
                int resId = itemView.getContext().getResources().getIdentifier(message.content, "drawable", itemView.getContext().getPackageName());
                if (resId != 0) {
                    Glide.with(binding.ivImage.getContext()).load(resId).into(binding.ivImage);
                }
            } else {
                binding.tvContent.setVisibility(View.VISIBLE);
                binding.ivImage.setVisibility(View.GONE);
                binding.ivVideoPlay.setVisibility(View.GONE);
                binding.tvContent.setText(message.content);
            }
            
            if ("sending".equals(message.status)) {
                binding.tvStatus.setText("发送中...");
            } else if ("sent".equals(message.status)) {
                binding.tvStatus.setText("已送达");
            } else {
                binding.tvStatus.setText("");
            }
        }
    }

    class OtherLeftViewHolder extends RecyclerView.ViewHolder {
        final ItemMsgReceiveBinding binding;

        OtherLeftViewHolder(ItemMsgReceiveBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(MessageEntity message) {
            binding.tvName.setText(targetName != null && !targetName.isEmpty() ? targetName : "对方");
            AvatarHelper.loadAvatar(binding.ivAvatar, targetAvatar);
            
            if ("image".equals(message.msgType) || "video".equals(message.msgType)) {
                binding.tvContent.setVisibility(View.GONE);
                binding.ivImage.setVisibility(View.VISIBLE);
                
                String path = message.localPath != null ? message.localPath : message.content;
                Glide.with(binding.ivImage.getContext()).load(path).into(binding.ivImage);
                
                binding.ivVideoPlay.setVisibility("video".equals(message.msgType) ? View.VISIBLE : View.GONE);
            } else if ("emoji".equals(message.msgType)) {
                binding.tvContent.setVisibility(View.GONE);
                binding.ivVideoPlay.setVisibility(View.GONE);
                binding.ivImage.setVisibility(View.VISIBLE);
                
                int resId = itemView.getContext().getResources().getIdentifier(message.content, "drawable", itemView.getContext().getPackageName());
                if (resId != 0) {
                    Glide.with(binding.ivImage.getContext()).load(resId).into(binding.ivImage);
                }
            } else {
                binding.tvContent.setVisibility(View.VISIBLE);
                binding.ivImage.setVisibility(View.GONE);
                binding.ivVideoPlay.setVisibility(View.GONE);
                binding.tvContent.setText(message.content);
            }
        }
    }
}
