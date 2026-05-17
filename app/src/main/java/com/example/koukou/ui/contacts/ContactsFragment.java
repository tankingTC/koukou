package com.example.koukou.ui.contacts;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.koukou.R;
import com.example.koukou.data.local.entity.FriendRequestEntity;
import com.example.koukou.data.local.entity.UserEntity;
import com.example.koukou.data.repository.ContactRepository;
import com.example.koukou.data.repository.SettingsRepository;
import com.example.koukou.databinding.FragmentContactsBinding;
import com.example.koukou.theme.ThemePalette;
import com.example.koukou.ui.shared.MainViewModelFactory;
import com.example.koukou.utils.AppearanceManager;
import com.example.koukou.utils.AvatarHelper;
import com.example.koukou.utils.IridescenceAnimator;
import com.example.koukou.utils.UserHelper;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

public class ContactsFragment extends Fragment {
    private FragmentContactsBinding binding;
    private ContactsViewModel viewModel;
    private ContactAdapter adapter;
    private boolean hasPlayedListAnimation = false;
    private SettingsRepository settingsRepository;
    private int pendingRequestCount = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentContactsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MainViewModelFactory factory = new MainViewModelFactory(requireContext());
        viewModel = new ViewModelProvider(this, factory).get(ContactsViewModel.class);
        settingsRepository = SettingsRepository.getInstance(requireContext());

        setupRecyclerView();
        observeViewModel();
        observeAppearance();
        viewModel.refreshRemoteState(null);

        binding.toolbar.setAlpha(0f);
        binding.toolbar.setTranslationY(-50f);
        binding.toolbar.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(350)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f))
                .start();

        binding.ivAddFriend.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            v.animate().cancel();
            v.setScaleX(0.9f);
            v.setScaleY(0.9f);
            v.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(220)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1f))
                    .withEndAction(this::showAddFriendDialog)
                    .start();
        });
    }

    private void setupRecyclerView() {
        adapter = new ContactAdapter();
        binding.rvContacts.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvContacts.setHasFixedSize(true);
        binding.rvContacts.setAdapter(adapter);
        binding.rvContacts.setLayoutAnimation(android.view.animation.AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_stagger_in));
    }

    private void observeViewModel() {
        viewModel.getContacts().observe(getViewLifecycleOwner(), contacts -> {
            adapter.submitList(contacts);
            AppearanceManager.refreshRecyclerAppearance(requireContext(), binding.rvContacts);
            if (contacts != null && !contacts.isEmpty() && !hasPlayedListAnimation) {
                hasPlayedListAnimation = true;
                binding.rvContacts.scheduleLayoutAnimation();
            }
        });
        viewModel.getPendingIncomingCount().observe(getViewLifecycleOwner(), count -> {
            pendingRequestCount = count == null ? 0 : count;
            binding.ivAddFriend.setContentDescription(pendingRequestCount > 0
                    ? "好友申请 " + pendingRequestCount + " 条"
                    : "添加好友");
        });
    }

    private void observeAppearance() {
        settingsRepository.getSettingsLiveData().observe(getViewLifecycleOwner(), state -> {
            AppearanceManager.applyNestedPageAppearance(requireContext(), binding.getRoot(), state);
            ThemePalette palette = AppearanceManager.paletteOf(state);
            binding.toolbar.setTitleTextColor(palette.textPrimary);
            binding.ivAddFriend.setBackgroundResource(palette.bgPanel);
            binding.ivAddFriend.setColorFilter(palette.iconPrimary);
            binding.glowHalo.setVisibility(View.GONE);
            binding.glowSheen.setVisibility(View.GONE);
            AppearanceManager.applyEffectState(binding.glowHalo, binding.glowSheen, binding.ivDecor, state, () -> {
                IridescenceAnimator.startHeroFloat(binding.ivDecor);
                IridescenceAnimator.startDreamscape(binding.ivDecor);
            });
            if (state == null || state.immersiveEffectsEnabled) {
                IridescenceAnimator.startButtonGlow(binding.ivAddFriend);
            } else {
                IridescenceAnimator.stopEffects(binding.ivAddFriend);
            }
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        });
    }

    private void showAddFriendDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_friend, null, false);
        EditText input = dialogView.findViewById(R.id.et_target_id);
        LinearLayout previewLayout = dialogView.findViewById(R.id.layout_user_preview);
        ImageView previewAvatar = dialogView.findViewById(R.id.iv_preview_avatar);
        TextView previewName = dialogView.findViewById(R.id.tv_preview_name);
        TextView previewId = dialogView.findViewById(R.id.tv_preview_id);
        TextView previewSignature = dialogView.findViewById(R.id.tv_preview_signature);
        TextView requestsButton = dialogView.findViewById(R.id.btn_requests);
        TextView searchButton = dialogView.findViewById(R.id.btn_search);
        TextView cancelButton = dialogView.findViewById(R.id.btn_cancel);
        TextView confirmButton = dialogView.findViewById(R.id.btn_confirm);

        final UserEntity[] searchedUser = new UserEntity[1];
        final String currentUserId = UserHelper.getUserId(requireContext());

        requestsButton.setText(pendingRequestCount > 0 ? "申请(" + pendingRequestCount + ")" : "申请");
        previewLayout.setVisibility(View.GONE);
        confirmButton.setEnabled(false);
        confirmButton.setAlpha(0.55f);

        decorateDialog(dialogView, confirmButton);
        applyAddFriendDialogPalette(dialogView);

        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setView(dialogView).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.setOnShowListener(d -> IridescenceAnimator.startPanelBounceIn(dialogView));

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchedUser[0] = null;
                previewLayout.setVisibility(View.GONE);
                confirmButton.setEnabled(false);
                confirmButton.setAlpha(0.55f);
                input.setError(null);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        requestsButton.setOnClickListener(v -> {
            dialog.dismiss();
            showFriendRequestsDialog();
        });
        cancelButton.setOnClickListener(v -> dialog.dismiss());
        searchButton.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            String targetId = input.getText() == null ? "" : input.getText().toString().trim();
            if (!validateTargetId(input, targetId)) {
                return;
            }
            if (targetId.equals(currentUserId)) {
                input.setError("不能添加自己为好友");
                return;
            }

            previewLayout.setVisibility(View.VISIBLE);
            AvatarHelper.loadAvatar(previewAvatar, null);
            previewName.setText("正在查找用户...");
            previewId.setText("扣扣号 " + targetId);
            previewSignature.setText("请稍候");

            viewModel.searchUser(targetId, new ContactRepository.UserLookupCallback() {
                @Override
                public void onSuccess(UserEntity user) {
                    if (!isAdded()) {
                        return;
                    }
                    if (user == null || user.userId == null || user.userId.trim().isEmpty()) {
                        searchedUser[0] = null;
                        previewLayout.setVisibility(View.GONE);
                        confirmButton.setEnabled(false);
                        confirmButton.setAlpha(0.55f);
                        showTip("没有找到对应的扣扣号");
                        return;
                    }
                    if (user.userId.equals(currentUserId)) {
                        searchedUser[0] = null;
                        previewLayout.setVisibility(View.GONE);
                        confirmButton.setEnabled(false);
                        confirmButton.setAlpha(0.55f);
                        showTip("不能添加自己为好友");
                        return;
                    }
                    searchedUser[0] = user;
                    populatePreview(previewAvatar, previewName, previewId, previewSignature, user);
                    confirmButton.setEnabled(true);
                    confirmButton.setAlpha(1f);
                    showTip("已找到用户，可以发送好友申请");
                }

                @Override
                public void onError(String msg) {
                    if (!isAdded()) {
                        return;
                    }
                    searchedUser[0] = null;
                    previewLayout.setVisibility(View.GONE);
                    confirmButton.setEnabled(false);
                    confirmButton.setAlpha(0.55f);
                    showTip(msg);
                }
            });
        });
        confirmButton.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            String targetId = input.getText() == null ? "" : input.getText().toString().trim();
            if (!validateTargetId(input, targetId)) {
                return;
            }
            if (searchedUser[0] == null || !targetId.equals(searchedUser[0].userId)) {
                showTip("请先查找并确认好友资料");
                return;
            }
            viewModel.addFriend(targetId, new ContactRepository.Callback() {
                @Override
                public void onSuccess() {
                    if (!isAdded()) {
                        return;
                    }
                    showTip("好友申请已发送");
                    dialog.dismiss();
                }

                @Override
                public void onError(String msg) {
                    if (!isAdded()) {
                        return;
                    }
                    showTip(msg);
                }
            });
        });

        dialog.show();
    }

    private void populatePreview(ImageView avatar, TextView name, TextView account, TextView signature, UserEntity user) {
        AvatarHelper.loadAvatar(avatar, user.avatarUrl);
        name.setText(user.nickname != null && !user.nickname.trim().isEmpty() ? user.nickname : user.userId);
        account.setText("扣扣号 " + user.userId);
        signature.setText(user.signature != null && !user.signature.trim().isEmpty()
                ? user.signature
                : "这个人很神秘，暂未留下签名");
    }

    private boolean validateTargetId(EditText input, String targetId) {
        if (targetId.isEmpty()) {
            input.setError("请输入对方扣扣号");
            return false;
        }
        if (!targetId.matches("\\d{10}")) {
            input.setError("扣扣号必须是 10 位数字");
            return false;
        }
        return true;
    }

    private void showFriendRequestsDialog() {
        ThemePalette palette = AppearanceManager.currentPalette(requireContext());
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(32, 28, 32, 28);
        content.setBackgroundResource(palette.bgGlassCard);

        TextView title = new TextView(requireContext());
        title.setText("好友申请");
        title.setTextSize(20f);
        title.setTextColor(palette.textPrimary);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        content.addView(title);

        TextView subtitle = new TextView(requireContext());
        subtitle.setText(pendingRequestCount > 0
                ? "你有 " + pendingRequestCount + " 条待处理的好友申请"
                : "处理想要添加你的好友");
        subtitle.setTextSize(13f);
        subtitle.setTextColor(palette.textSecondary);
        subtitle.setPadding(0, 10, 0, 8);
        content.addView(subtitle);

        List<FriendRequestEntity> requests = viewModel.getIncomingRequests().getValue();
        if (requests == null || requests.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText("暂时没有新的好友申请");
            empty.setTextSize(14f);
            empty.setTextColor(palette.textSecondary);
            empty.setPadding(0, 28, 0, 12);
            content.addView(empty);
        } else {
            for (FriendRequestEntity request : requests) {
                content.addView(createRequestItem(request, palette));
            }
        }

        ScrollView scrollView = new ScrollView(requireContext());
        scrollView.setFillViewport(true);
        scrollView.addView(content);
        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setView(scrollView).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
        IridescenceAnimator.startPanelBounceIn(scrollView);
    }

    private View createRequestItem(FriendRequestEntity request, ThemePalette palette) {
        LinearLayout item = new LinearLayout(requireContext());
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(24, 22, 24, 22);
        item.setBackgroundResource(palette.bgSettingsRow);

        LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        itemParams.topMargin = 18;
        item.setLayoutParams(itemParams);

        TextView name = new TextView(requireContext());
        String displayName = request.fromNickname == null || request.fromNickname.trim().isEmpty()
                ? request.fromUserId
                : request.fromNickname;
        name.setText(displayName + " 请求添加你为好友");
        name.setTextSize(15f);
        name.setTextColor(palette.textPrimary);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        item.addView(name);

        TextView message = new TextView(requireContext());
        String content = request.message == null || request.message.trim().isEmpty()
                ? "请求添加你为好友"
                : request.message;
        message.setText(content + " · " + requestStatusLabel(request.status));
        message.setTextSize(13f);
        message.setTextColor(palette.textSecondary);
        message.setPadding(0, 8, 0, 0);
        item.addView(message);

        if ("pending".equals(request.status)) {
            LinearLayout actions = new LinearLayout(requireContext());
            actions.setGravity(Gravity.END);
            actions.setPadding(0, 16, 0, 0);

            TextView reject = buildActionChip("拒绝", palette.bgPanel, palette.textPrimary);
            reject.setOnClickListener(v -> viewModel.rejectFriendRequest(request.requestId, requestCallback("已拒绝好友申请")));
            actions.addView(reject);

            TextView accept = buildActionChip("同意", R.drawable.bg_button_gradient_20, Color.WHITE);
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) accept.getLayoutParams();
            params.setMarginStart(14);
            accept.setLayoutParams(params);
            accept.setOnClickListener(v -> viewModel.acceptFriendRequest(request.requestId, requestCallback("已添加为好友")));
            actions.addView(accept);
            item.addView(actions);
        }
        return item;
    }

    private TextView buildActionChip(String text, int backgroundRes, int textColor) {
        TextView chip = new TextView(requireContext());
        chip.setText(text);
        chip.setGravity(Gravity.CENTER);
        chip.setMinWidth(96);
        chip.setHeight(42);
        chip.setTextColor(textColor);
        chip.setBackgroundResource(backgroundRes);
        chip.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return chip;
    }

    private ContactRepository.Callback requestCallback(String successMessage) {
        return new ContactRepository.Callback() {
            @Override
            public void onSuccess() {
                if (!isAdded()) {
                    return;
                }
                showTip(successMessage);
            }

            @Override
            public void onError(String msg) {
                if (!isAdded()) {
                    return;
                }
                showTip(msg);
            }
        };
    }

    private String requestStatusLabel(String status) {
        if ("accepted".equals(status)) {
            return "已同意";
        }
        if ("rejected".equals(status)) {
            return "已拒绝";
        }
        return "待处理";
    }

    private void decorateDialog(View dialogView, View confirmButton) {
        AppearanceManager.applyEffectState(
                dialogView.findViewById(R.id.dialog_halo),
                dialogView.findViewById(R.id.dialog_sheen),
                null,
                AppearanceManager.currentState(requireContext()),
                () -> {
                    IridescenceAnimator.startHaloPulse(dialogView.findViewById(R.id.dialog_halo));
                    IridescenceAnimator.startSheenDrift(dialogView.findViewById(R.id.dialog_sheen), 0f, -20f, 0f, 12f, 0.14f, 0.28f);
                    if (confirmButton != null) {
                        IridescenceAnimator.startButtonGlow(confirmButton);
                    }
                }
        );
        AppearanceManager.applyItemAppearance(requireContext(), dialogView);
    }

    private void applyAddFriendDialogPalette(View dialogView) {
        ThemePalette palette = AppearanceManager.currentPalette(requireContext());
        dialogView.findViewById(R.id.layout_dialog_card).setBackgroundResource(palette.bgGlassCard);
        dialogView.findViewById(R.id.layout_user_preview).setBackgroundResource(palette.bgListCard);

        TextView title = dialogView.findViewById(R.id.tv_dialog_title);
        TextView subtitle = dialogView.findViewById(R.id.tv_dialog_subtitle);
        TextView previewName = dialogView.findViewById(R.id.tv_preview_name);
        TextView previewId = dialogView.findViewById(R.id.tv_preview_id);
        TextView previewSignature = dialogView.findViewById(R.id.tv_preview_signature);
        TextView requests = dialogView.findViewById(R.id.btn_requests);
        TextView search = dialogView.findViewById(R.id.btn_search);
        TextView cancel = dialogView.findViewById(R.id.btn_cancel);
        EditText input = dialogView.findViewById(R.id.et_target_id);

        title.setTextColor(palette.textPrimary);
        subtitle.setTextColor(palette.textSecondary);
        previewName.setTextColor(palette.textPrimary);
        previewId.setTextColor(palette.textAccent);
        previewSignature.setTextColor(palette.textSecondary);
        input.setBackgroundResource(palette.bgInputBar);
        input.setTextColor(palette.textPrimary);
        input.setHintTextColor(palette.textTertiary);

        requests.setBackgroundResource(palette.bgPanel);
        search.setBackgroundResource(palette.bgPanel);
        cancel.setBackgroundResource(palette.bgPanel);
        requests.setTextColor(palette.textPrimary);
        search.setTextColor(palette.textPrimary);
        cancel.setTextColor(palette.textPrimary);
    }

    private void showTip(String text) {
        if (binding == null || text == null || text.trim().isEmpty()) {
            return;
        }
        Snackbar bar = Snackbar.make(binding.getRoot(), text, Snackbar.LENGTH_SHORT);
        ThemePalette palette = AppearanceManager.currentPalette(requireContext());
        View view = bar.getView();
        view.setBackgroundColor(palette.lightPalette ? 0xF7FFFFFF : 0xE61A2338);
        TextView snackText = view.findViewById(com.google.android.material.R.id.snackbar_text);
        if (snackText != null) {
            snackText.setTextColor(palette.textPrimary);
        }
        bar.show();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel != null) {
            viewModel.refreshRemoteState(null);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
