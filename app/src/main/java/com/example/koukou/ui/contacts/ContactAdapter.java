package com.example.koukou.ui.contacts;

import android.app.Activity;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.koukou.R;
import com.example.koukou.data.local.entity.UserEntity;
import com.example.koukou.databinding.ItemContactBinding;
import com.example.koukou.utils.AppearanceManager;

public class ContactAdapter extends ListAdapter<UserEntity, ContactAdapter.ViewHolder> {

    public ContactAdapter() {
        super(new DiffUtil.ItemCallback<UserEntity>() {
            @Override
            public boolean areItemsTheSame(@NonNull UserEntity oldItem, @NonNull UserEntity newItem) {
                return oldItem.userId.equals(newItem.userId);
            }

            @Override
            public boolean areContentsTheSame(@NonNull UserEntity oldItem, @NonNull UserEntity newItem) {
                return java.util.Objects.equals(oldItem.account, newItem.account)
                        && java.util.Objects.equals(oldItem.nickname, newItem.nickname)
                        && java.util.Objects.equals(oldItem.avatarUrl, newItem.avatarUrl)
                        && java.util.Objects.equals(oldItem.signature, newItem.signature);
            }
        });
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemContactBinding binding = ItemContactBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemContactBinding binding;

        ViewHolder(ItemContactBinding binding) {
            super(binding.getRoot());
            this.binding = binding;

            binding.getRoot().setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) {
                    return;
                }
                UserEntity user = getItem(pos);
                Intent intent = new Intent(v.getContext(), FriendProfileActivity.class);
                intent.putExtra(FriendProfileActivity.EXTRA_USER_ID, user.userId);
                intent.putExtra(FriendProfileActivity.EXTRA_NICKNAME, user.nickname != null && !user.nickname.isEmpty() ? user.nickname : user.userId);
                intent.putExtra(FriendProfileActivity.EXTRA_AVATAR, user.avatarUrl);
                intent.putExtra(FriendProfileActivity.EXTRA_SIGNATURE, user.signature);
                v.getContext().startActivity(intent);
                if (v.getContext() instanceof Activity) {
                    ((Activity) v.getContext()).overridePendingTransition(R.anim.chat_open_enter, R.anim.chat_open_exit);
                }
            });
        }

        void bind(UserEntity entity) {
            binding.tvName.setText(entity.nickname != null && !entity.nickname.isEmpty() ? entity.nickname : entity.userId);
            binding.tvSignature.setText(entity.signature != null && !entity.signature.isEmpty() ? entity.signature : "这个人很神秘，暂未留下签名");

            if (entity.avatarUrl != null && !entity.avatarUrl.isEmpty()) {
                int resId = binding.getRoot().getContext().getResources().getIdentifier(
                        entity.avatarUrl, "drawable", binding.getRoot().getContext().getPackageName());
                if (resId == 0) {
                    resId = binding.getRoot().getContext().getResources().getIdentifier(
                            entity.avatarUrl, "mipmap", binding.getRoot().getContext().getPackageName());
                }
                if (resId != 0) {
                    binding.ivAvatar.setImageResource(resId);
                    return;
                }
            }
            binding.ivAvatar.setImageResource(R.mipmap.tubiao);
            AppearanceManager.applyItemAppearance(binding.getRoot().getContext(), binding.getRoot());
        }
    }
}
