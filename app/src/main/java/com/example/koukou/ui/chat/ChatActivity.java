package com.example.koukou.ui.chat;

import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.koukou.R;
import com.example.koukou.data.repository.SettingsRepository;
import com.example.koukou.databinding.ActivityChatBinding;
import com.example.koukou.utils.AppearanceManager;
import com.example.koukou.utils.IridescenceAnimator;
import com.example.koukou.utils.UserHelper;
import com.example.koukou.ui.settings.model.SettingsState;

public class ChatActivity extends AppCompatActivity {
    public static final String EXTRA_TARGET_ID = "target_id";
    public static final String EXTRA_TARGET_NAME = "target_name";
    public static final String EXTRA_TARGET_AVATAR = "target_avatar";

    private ActivityChatBinding binding;
    private ChatViewModel viewModel;
    private MessageAdapter adapter;
    private String currentUserId;
    private String targetId;
    private String targetName;
    private String targetAvatar;
    private boolean hasPlayedListAnimation = false;
    private ActivityResultLauncher<String> pickMediaLauncher;
    private SettingsRepository settingsRepository;
    private SettingsState currentAppearanceState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        settingsRepository = SettingsRepository.getInstance(this);

        currentUserId = UserHelper.getUserId(this);
        if (currentUserId == null) {
            showTip("未登录，请重新登录");
            finishWithGestureTransition();
            return;
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.chatRoot, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            if (insets.isVisible(WindowInsetsCompat.Type.ime()) && adapter != null && adapter.getItemCount() > 0) {
                binding.rvMessages.post(() -> {
                    if (adapter.getItemCount() > 0) {
                        binding.rvMessages.smoothScrollToPosition(adapter.getItemCount() - 1);
                    }
                });
            }
            return WindowInsetsCompat.CONSUMED;
        });

        targetId = getIntent().getStringExtra(EXTRA_TARGET_ID);
        targetName = getIntent().getStringExtra(EXTRA_TARGET_NAME);
        targetAvatar = getIntent().getStringExtra(EXTRA_TARGET_AVATAR);

        if (targetId == null || targetId.trim().isEmpty()) {
            showTip("无效的聊天对象");
            finishWithGestureTransition();
            return;
        }

        binding.toolbar.setTitle(targetName != null ? targetName : targetId);
        setupBackLinkedTransition();

        binding.toolbar.setAlpha(0f);
        binding.toolbar.setTranslationY(-20f);
        binding.toolbar.animate().alpha(1f).translationY(0f)
                .setDuration(280)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.2f))
                .start();

        binding.layoutBottomBar.setAlpha(0f);
        binding.layoutBottomBar.setTranslationY(24f);
        binding.layoutBottomBar.animate().alpha(1f).translationY(0f)
                .setDuration(300)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.2f))
                .start();

        ChatViewModelFactory factory = new ChatViewModelFactory(this);
        viewModel = new ViewModelProvider(this, factory).get(ChatViewModel.class);
        viewModel.setTargetId(targetId);

        setupRecyclerView();
        setupInputArea();
        setupExtraButtons();
        observeViewModel();
        observeAppearance();
    }

    private void setupBackLinkedTransition() {
        binding.toolbar.setNavigationOnClickListener(v -> finishWithGestureTransition());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishWithGestureTransition();
            }
        });
    }

    private void finishWithGestureTransition() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        finish();
        overridePendingTransition(R.anim.chat_back_enter, R.anim.chat_back_exit);
    }

    private void setupRecyclerView() {
        adapter = new MessageAdapter(
                currentUserId,
                UserHelper.getNickname(this),
                UserHelper.getAvatar(this),
                targetName,
                targetAvatar
        );
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        binding.rvMessages.setLayoutManager(layoutManager);
        binding.rvMessages.setAdapter(adapter);
        binding.rvMessages.setLayoutAnimation(android.view.animation.AnimationUtils.loadLayoutAnimation(this, R.anim.layout_stagger_in));

        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                if (adapter.getItemCount() > 0) {
                    binding.rvMessages.post(() -> {
                        if (adapter.getItemCount() > 0) {
                            layoutManager.smoothScrollToPosition(binding.rvMessages, null, adapter.getItemCount() - 1);
                        }
                    });
                }
            }
        });
    }

    private void setupInputArea() {
        binding.etInput.setOnFocusChangeListener((v, hasFocus) -> updateInputSurface(hasFocus));
        IridescenceAnimator.setupClickFeedback(binding.btnSend);
        binding.btnSend.setOnClickListener(v -> {
            String text = binding.etInput.getText().toString();
            if (!text.trim().isEmpty()) {
                viewModel.sendMessage(text);
                binding.etInput.setText("");
            } else {
                showTip("不能发送空消息");
            }
        });
    }

    private void setupExtraButtons() {
        pickMediaLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                String path = uri.toString();
                String type = getContentResolver().getType(uri);
                if (type != null && type.startsWith("video/")) {
                    viewModel.sendMessage("[视频]", "video", path);
                    showTip("视频已发送");
                } else {
                    viewModel.sendMessage("[图片]", "image", path);
                    showTip("图片已发送");
                }
            }
        });

        binding.btnMore.setOnClickListener(v -> pickMediaLauncher.launch("*/*"));
    }

    private void observeViewModel() {
        viewModel.getMessages().observe(this, messages -> {
            adapter.submitList(messages);
            if (messages != null && !messages.isEmpty()) {
                if (!hasPlayedListAnimation) {
                    hasPlayedListAnimation = true;
                    binding.rvMessages.scheduleLayoutAnimation();
                }
                binding.rvMessages.post(() -> {
                    if (adapter.getItemCount() > 0) {
                        binding.rvMessages.scrollToPosition(adapter.getItemCount() - 1);
                    }
                });
            }
        });
    }

    private void observeAppearance() {
        settingsRepository.getSettingsLiveData().observe(this, state -> {
            currentAppearanceState = state;
            AppearanceManager.applyPageAppearance(this, getWindow(), binding.getRoot(), state);
            AppearanceManager.applyEffectState(null, null, binding.ivHero, state, () -> {
                IridescenceAnimator.startHeroFloat(binding.ivHero);
                IridescenceAnimator.startDreamscape(binding.ivHero);
            });
            if (state == null || state.immersiveEffectsEnabled) {
                IridescenceAnimator.startButtonGlow(binding.btnSend);
            } else {
                IridescenceAnimator.stopEffects(binding.btnSend);
            }
            updateInputSurface(binding.etInput.hasFocus());
            binding.layoutEmojiPanel.setBackgroundResource(state != null && "stardust".equals(state.chatBackground)
                    ? R.drawable.bg_butterfly_panel_stardust
                    : R.drawable.bg_butterfly_panel);
            AppearanceManager.refreshRecyclerAppearance(this, binding.rvMessages);
            AppearanceManager.refreshRecyclerAppearance(this, binding.rvEmojis);
        });
    }

    private void updateInputSurface(boolean focused) {
        boolean stardust = currentAppearanceState != null && "stardust".equals(currentAppearanceState.chatBackground);
        boolean active = stardust && currentAppearanceState.immersiveEffectsEnabled && focused;
        binding.layoutInput.setBackgroundResource(active
                ? R.drawable.bg_butterfly_input_bar_stardust_active
                : (stardust ? R.drawable.bg_butterfly_input_bar_stardust : R.drawable.bg_butterfly_input_bar));
        
        if (focused && active) {
            IridescenceAnimator.startInputEdgeTrace(binding.layoutInput);
        }
        
        binding.layoutInput.animate().cancel();
        binding.layoutInput.animate()
                .scaleX(active ? 1.01f : 1f)
                .scaleY(active ? 1.01f : 1f)
                .alpha(active ? 1f : 0.98f)
                .setDuration(180)
                .start();
    }

    private void showTip(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }
}
