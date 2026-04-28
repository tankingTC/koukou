package com.example.koukou.ui.settings;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.koukou.R;
import com.example.koukou.data.repository.SettingsRepository;
import com.example.koukou.databinding.FragmentSettingsBinding;
import com.example.koukou.ui.login.LoginActivity;
import com.example.koukou.ui.settings.model.SettingsItem;
import com.example.koukou.ui.settings.model.SettingsPage;
import com.example.koukou.utils.AppearanceManager;
import com.example.koukou.utils.AvatarHelper;
import com.example.koukou.utils.IridescenceAnimator;
import com.example.koukou.utils.UserHelper;
import com.yalantis.ucrop.UCrop;

import java.io.File;

public class SettingsFragment extends Fragment {
    private FragmentSettingsBinding binding;
    private SettingsRepository settingsRepository;
    private SettingsViewModel viewModel;
    private ActivityResultLauncher<String> pickImageLauncher;
    private ActivityResultLauncher<Intent> cropImageLauncher;

    private interface TextDialogAction {
        void onConfirm(String value);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        settingsRepository = SettingsRepository.getInstance(requireContext());
        viewModel = new ViewModelProvider(this, new SettingsViewModelFactory(requireContext(), SettingsPage.ROOT)).get(SettingsViewModel.class);

        setupPickers();
        setupViews();
        setupRecyclerView();
        setupClicks();
        observeViewModel();
        observeAppearance();
    }

    private void setupPickers() {
        pickImageLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri == null) {
                return;
            }
            String userId = UserHelper.getUserId(requireContext());
            File destFile = new File(requireContext().getFilesDir(), userId + "_avatar_" + System.currentTimeMillis() + ".jpg");
            Intent intent = UCrop.of(uri, android.net.Uri.fromFile(destFile))
                    .withAspectRatio(1, 1)
                    .withMaxResultSize(500, 500)
                    .getIntent(requireContext());
            cropImageLauncher.launch(intent);
        });

        cropImageLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                android.net.Uri resultUri = UCrop.getOutput(result.getData());
                if (resultUri != null) {
                    updateProfile(UserHelper.getNickname(requireContext()), resultUri.toString(), UserHelper.getSignature(requireContext()));
                }
            } else if (result.getResultCode() == UCrop.RESULT_ERROR && result.getData() != null) {
                Throwable error = UCrop.getError(result.getData());
                if (error != null) {
                    Toast.makeText(requireContext(), "裁剪失败: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void setupViews() {
        binding.toolbar.setTitle("设置");
        refreshProfileViews();
    }

    private void refreshProfileViews() {
        String userId = UserHelper.getUserId(requireContext());
        String nickname = UserHelper.getNickname(requireContext());
        String avatar = UserHelper.getAvatar(requireContext());
        String signature = UserHelper.getSignature(requireContext());

        binding.tvNickname.setText(nickname);
        binding.tvSignature.setText(signature);
        binding.tvUserid.setText("扣扣号 " + (userId == null ? "未知" : userId));
        AvatarHelper.loadAvatar(binding.ivAvatar, avatar);
    }

    private void setupRecyclerView() {
        SettingsAdapter adapter = new SettingsAdapter(this::handleItemClick, (item, isChecked) -> viewModel.onSwitchChanged(item.key, isChecked));
        binding.rvSettings.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvSettings.setAdapter(adapter);
        viewModel.getItemsLiveData().observe(getViewLifecycleOwner(), adapter::submitList);
    }

    private void observeViewModel() {
        viewModel.getSettingsState().observe(getViewLifecycleOwner(), state -> {
            if (binding == null) {
                return;
            }
            if (!binding.tvNickname.getText().toString().equals(UserHelper.getNickname(requireContext()))) {
                refreshProfileViews();
            }
        });
    }

    private void setupClicks() {
        binding.tvNickname.setOnClickListener(v -> showNicknameDialog());
        binding.ivAvatar.setOnClickListener(v -> showAvatarDialog());
        binding.tvSignature.setOnClickListener(v -> showSignatureDialog());
    }

    private void handleItemClick(SettingsItem item) {
        switch (item.key) {
            case SettingsViewModel.KEY_ACCOUNT_SECURITY:
                openDetail(SettingsPage.ACCOUNT_SECURITY);
                break;
            case SettingsViewModel.KEY_PRIVACY:
                openDetail(SettingsPage.PRIVACY_CONTACTS);
                break;
            case SettingsViewModel.KEY_NOTIFICATIONS:
                openDetail(SettingsPage.NOTIFICATIONS);
                break;
            case SettingsViewModel.KEY_APPEARANCE:
                openDetail(SettingsPage.APPEARANCE);
                break;
            case SettingsViewModel.KEY_GENERAL_STORAGE:
                openDetail(SettingsPage.GENERAL_STORAGE);
                break;
            case SettingsViewModel.KEY_ABOUT:
                openDetail(SettingsPage.ABOUT);
                break;
            case SettingsViewModel.KEY_LOGOUT:
                performLogout();
                break;
            default:
                break;
        }
    }

    private void openDetail(String page) {
        Intent intent = new Intent(requireContext(), SettingsDetailActivity.class);
        intent.putExtra(SettingsDetailActivity.EXTRA_PAGE, page);
        startActivity(intent);
        requireActivity().overridePendingTransition(R.anim.chat_open_enter, R.anim.chat_open_exit);
    }

    private void showNicknameDialog() {
        showTextDialog(
                "编辑昵称",
                "换一个更有辨识度的名字，让朋友一眼认出你。",
                "请输入昵称",
                UserHelper.getNickname(requireContext()),
                false,
                value -> updateProfile(value, UserHelper.getAvatar(requireContext()), UserHelper.getSignature(requireContext()))
        );
    }

    private void showAvatarDialog() {
        String[] avatars = {"ic_avatar_1", "ic_avatar_2", "ic_avatar_3", "ic_avatar_4", "ic_avatar_5", "ic_avatar_6"};
        String[] names = {"从相册上传", "炽焰橙红", "森林绿意", "天际蔚蓝", "神秘紫金", "活力明黄", "玫瑰粉红"};

        new AlertDialog.Builder(requireContext())
                .setTitle("选择头像")
                .setItems(names, (dialog, which) -> {
                    if (which == 0) {
                        pickImageLauncher.launch("image/*");
                        return;
                    }
                    updateProfile(UserHelper.getNickname(requireContext()), avatars[which - 1], UserHelper.getSignature(requireContext()));
                })
                .show();
    }

    private void showSignatureDialog() {
        showTextDialog(
                "编辑个性签名",
                "写下一句属于你的流光注释，让资料页更有氛围。",
                "输入个性签名",
                UserHelper.getSignature(requireContext()),
                true,
                value -> updateProfile(UserHelper.getNickname(requireContext()), UserHelper.getAvatar(requireContext()), value)
        );
    }

    private void showTextDialog(String title, String subtitle, String hint, String currentValue, boolean multiline, TextDialogAction action) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_profile, null, false);
        TextView titleView = dialogView.findViewById(R.id.tv_dialog_title);
        TextView subtitleView = dialogView.findViewById(R.id.tv_dialog_subtitle);
        EditText input = dialogView.findViewById(R.id.et_content);
        View cancelButton = dialogView.findViewById(R.id.btn_cancel);
        View confirmButton = dialogView.findViewById(R.id.btn_confirm);

        titleView.setText(title);
        subtitleView.setText(subtitle);
        input.setHint(hint);
        input.setText(currentValue);
        input.setSelection(input.getText().length());
        input.setSingleLine(!multiline);
        input.setMinLines(multiline ? 3 : 1);
        input.setMaxLines(multiline ? 3 : 1);
        input.setInputType(multiline
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);

        AppearanceManager.applyEffectState(
                dialogView.findViewById(R.id.dialog_halo),
                dialogView.findViewById(R.id.dialog_sheen),
                null,
                AppearanceManager.currentState(requireContext()),
                () -> {
                    IridescenceAnimator.startHaloPulse(dialogView.findViewById(R.id.dialog_halo));
                    IridescenceAnimator.startSheenDrift(dialogView.findViewById(R.id.dialog_sheen), 0f, -20f, 0f, 12f, 0.14f, 0.28f);
                    IridescenceAnimator.startButtonGlow(confirmButton);
                }
        );
        AppearanceManager.applyItemAppearance(requireContext(), dialogView);

        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setView(dialogView).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        cancelButton.setOnClickListener(v -> dialog.dismiss());
        confirmButton.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            String value = input.getText().toString().trim();
            if (value.isEmpty()) {
                if (multiline) {
                    value = "这个人很神秘，暂未留下签名";
                } else {
                    input.setError("请输入昵称");
                    return;
                }
            }
            action.onConfirm(value);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void updateProfile(String nickname, String avatarUrl, String signature) {
        String userId = UserHelper.getUserId(requireContext());
        settingsRepository.updateProfile(userId, nickname, avatarUrl, signature, new com.example.koukou.data.repository.UserRepository.Callback() {
            @Override
            public void onSuccess(com.example.koukou.data.local.entity.UserEntity user) {
                refreshProfileViews();
                Toast.makeText(requireContext(), "资料更新成功", Toast.LENGTH_SHORT).show();

                com.example.koukou.network.model.WebSocketMessage msg = new com.example.koukou.network.model.WebSocketMessage();
                msg.type = "profile_update";
                msg.from = userId;
                msg.senderNickname = nickname;
                msg.senderAvatar = avatarUrl;
                com.example.koukou.network.websocket.WebSocketManager.getInstance().sendMessage(msg);
            }

            @Override
            public void onError(String msg) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void performLogout() {
        UserHelper.clearSavedUser(requireContext());
        Intent intent = new Intent(requireActivity(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        requireActivity().finish();
    }

    private void observeAppearance() {
        settingsRepository.getSettingsLiveData().observe(getViewLifecycleOwner(), state -> {
            AppearanceManager.applyNestedPageAppearance(requireContext(), binding.getRoot(), state);
            boolean minimalWhite = state != null && "minimal_white".equals(state.chatBackground);
            boolean matrix = state != null && "matrix".equals(state.chatBackground);
            boolean stardust = state != null && "stardust".equals(state.chatBackground);
            binding.toolbar.setTitleTextColor(Color.parseColor(minimalWhite ? "#162131" : "#F3F6FC"));
            binding.layoutInfoSurface.setBackgroundResource(minimalWhite
                    ? R.drawable.bg_butterfly_glass_card_light
                    : (matrix ? R.drawable.bg_butterfly_glass_card_matrix
                    : (stardust ? R.drawable.bg_butterfly_glass_card_stardust : R.drawable.bg_butterfly_glass_card)));
            binding.cvInfo.setStrokeColor(Color.parseColor(minimalWhite ? "#66C3D3E2" : (matrix ? "#4F86F7D7" : (stardust ? "#5ADCF6FF" : "#6C96DFFF"))));
            binding.tvNickname.setTextColor(Color.parseColor(minimalWhite ? "#162131" : "#F3F6FC"));
            binding.tvSignature.setTextColor(Color.parseColor(minimalWhite ? "#6A778C" : (matrix ? "#A9C8BE" : (stardust ? "#C6D4EA" : "#B9C2D5"))));
            binding.tvUserid.setTextColor(Color.parseColor(minimalWhite ? "#0B9FB5" : (stardust ? "#8FF4FF" : "#00E6FF")));
            binding.glowHalo.setVisibility(View.GONE);
            binding.glowSheen.setVisibility(View.GONE);
            AppearanceManager.applyEffectState(binding.glowHalo, binding.glowSheen, binding.ivDecor, state, () -> {
                IridescenceAnimator.startHeroFloat(binding.ivDecor);
                IridescenceAnimator.startDreamscape(binding.ivDecor);
            });
            AppearanceManager.refreshRecyclerAppearance(requireContext(), binding.rvSettings);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
