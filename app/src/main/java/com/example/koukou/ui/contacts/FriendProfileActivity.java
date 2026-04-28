package com.example.koukou.ui.contacts;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.koukou.R;
import com.example.koukou.data.local.AppDatabase;
import com.example.koukou.data.repository.ContactRepository;
import com.example.koukou.data.repository.SettingsRepository;
import com.example.koukou.databinding.ActivityFriendProfileBinding;
import com.example.koukou.ui.chat.ChatActivity;
import com.example.koukou.utils.AppExecutors;
import com.example.koukou.utils.AppearanceManager;
import com.example.koukou.utils.AvatarHelper;
import com.example.koukou.utils.IridescenceAnimator;
import com.example.koukou.utils.UserHelper;

public class FriendProfileActivity extends AppCompatActivity {
    public static final String EXTRA_USER_ID = "extra_user_id";
    public static final String EXTRA_NICKNAME = "extra_nickname";
    public static final String EXTRA_AVATAR = "extra_avatar";
    public static final String EXTRA_SIGNATURE = "extra_signature";

    private ActivityFriendProfileBinding binding;
    private String targetId;
    private String targetName;
    private String currentUserId;
    private String targetAvatar;
    private SettingsRepository settingsRepository;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFriendProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        settingsRepository = SettingsRepository.getInstance(this);

        currentUserId = UserHelper.getUserId(this);
        targetId = getIntent().getStringExtra(EXTRA_USER_ID);
        targetName = getIntent().getStringExtra(EXTRA_NICKNAME);
        targetAvatar = getIntent().getStringExtra(EXTRA_AVATAR);
        String signature = getIntent().getStringExtra(EXTRA_SIGNATURE);

        binding.tvProfileNickname.setText(targetName);
        binding.tvProfileQQ.setText("扣扣号 " + targetId);
        binding.tvSignatureDetail.setText(signature != null && !signature.isEmpty() ? signature : "这个人很神秘，暂未留下签名");
        AvatarHelper.loadAvatar(binding.ivBigAvatar, targetAvatar);

        setupBackHandling();
        setupListeners();
        observeAppearance();
    }

    private void setupBackHandling() {
        binding.ivBack.setOnClickListener(v -> finishWithBackTransition());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishWithBackTransition();
            }
        });
    }

    private void setupListeners() {
        binding.tvShare.setOnClickListener(v -> Toast.makeText(this, "分享功能开发中", Toast.LENGTH_SHORT).show());

        binding.tvSendMessage.setOnClickListener(v -> {
            Intent intent = new Intent(this, ChatActivity.class);
            intent.putExtra(ChatActivity.EXTRA_TARGET_ID, targetId);
            intent.putExtra(ChatActivity.EXTRA_TARGET_NAME, targetName);
            intent.putExtra(ChatActivity.EXTRA_TARGET_AVATAR, targetAvatar);
            startActivity(intent);
            overridePendingTransition(R.anim.chat_open_enter, R.anim.chat_open_exit);
            finish();
        });

        binding.tvDeleteFriend.setOnClickListener(v -> showDeleteConfirm());
    }

    private void showDeleteConfirm() {
        new AlertDialog.Builder(this)
                .setTitle("删除好友")
                .setMessage("确定要删除这位好友吗？")
                .setPositiveButton("删除", (dialog, which) -> performDelete())
                .setNegativeButton("取消", null)
                .show();
    }

    private void performDelete() {
        AppDatabase db = AppDatabase.getInstance(this);
        ContactRepository repo = ContactRepository.getInstance(db.friendDao(), db.userDao(), AppExecutors.getInstance());
        repo.deleteFriend(currentUserId, targetId, new ContactRepository.Callback() {
            @Override
            public void onSuccess() {
                Toast.makeText(FriendProfileActivity.this, "已删除好友", Toast.LENGTH_SHORT).show();
                finishWithBackTransition();
            }

            @Override
            public void onError(String msg) {
                Toast.makeText(FriendProfileActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void finishWithBackTransition() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        finish();
        overridePendingTransition(R.anim.chat_back_enter, R.anim.chat_back_exit);
    }

    private void observeAppearance() {
        settingsRepository.getSettingsLiveData().observe(this, state -> {
            AppearanceManager.applyPageAppearance(this, getWindow(), binding.getRoot(), state);
            AppearanceManager.applyEffectState(null, null, binding.ivDecor, state, () -> {
                IridescenceAnimator.startHeroFloat(binding.ivDecor);
                IridescenceAnimator.startDreamscape(binding.ivDecor);
            });
        });
    }
}
