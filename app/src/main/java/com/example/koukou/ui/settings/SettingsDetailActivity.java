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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.koukou.R;
import com.example.koukou.data.repository.SettingsRepository;
import com.example.koukou.databinding.ActivitySettingsDetailBinding;
import com.example.koukou.ui.login.LoginActivity;
import com.example.koukou.ui.settings.model.SettingsItem;
import com.example.koukou.ui.settings.model.SettingsPage;
import com.example.koukou.ui.settings.model.SettingsState;
import com.example.koukou.utils.AppearanceManager;
import com.example.koukou.utils.IridescenceAnimator;
import com.example.koukou.utils.UserHelper;

public class SettingsDetailActivity extends AppCompatActivity {
    public static final String EXTRA_PAGE = "settings_page";

    private ActivitySettingsDetailBinding binding;
    private SettingsViewModel viewModel;
    private String page;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        page = getIntent().getStringExtra(EXTRA_PAGE);
        if (page == null || page.trim().isEmpty()) {
            page = SettingsPage.ROOT;
        }

        viewModel = new ViewModelProvider(this, new SettingsViewModelFactory(this, page)).get(SettingsViewModel.class);

        setupHeader();
        setupRecyclerView();
        observeViewModel();
    }

    private void setupHeader() {
        binding.tvPageTitle.setText(viewModel.getPageTitle());
        binding.tvPageSubtitle.setText(viewModel.getPageSubtitle());
        binding.ivBack.setOnClickListener(v -> finishWithBackTransition());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishWithBackTransition();
            }
        });
    }

    private void setupRecyclerView() {
        SettingsAdapter adapter = new SettingsAdapter(this::handleItemClick, (item, isChecked) -> {
            if (SettingsViewModel.KEY_LOCAL_PASSCODE.equals(item.key)) {
                return;
            }
            viewModel.onSwitchChanged(item.key, isChecked);
            showTip(isChecked ? "已开启" : "已关闭");
        });
        binding.rvSettings.setLayoutManager(new LinearLayoutManager(this));
        binding.rvSettings.setAdapter(adapter);
        viewModel.getItemsLiveData().observe(this, items -> {
            adapter.submitList(items);
            AppearanceManager.refreshRecyclerAppearance(this, binding.rvSettings);
        });
    }

    private void observeViewModel() {
        viewModel.getSettingsState().observe(this, state -> {
            if (SettingsPage.GENERAL_STORAGE.equals(page)) {
                viewModel.refreshStorageStats();
            }
            AppearanceManager.applyPageAppearance(this, getWindow(), binding.getRoot(), state);
            boolean minimalWhite = state != null && "minimal_white".equals(state.chatBackground);
            boolean matrix = state != null && "matrix".equals(state.chatBackground);
            boolean stardust = state != null && "stardust".equals(state.chatBackground);
            binding.ivBack.setBackgroundResource(minimalWhite
                    ? R.drawable.bg_butterfly_panel_light
                    : (matrix ? R.drawable.bg_butterfly_panel_matrix
                    : (stardust ? R.drawable.bg_butterfly_panel_stardust : R.drawable.bg_butterfly_panel)));
            binding.ivBack.setColorFilter(Color.parseColor(minimalWhite ? "#162131" : "#F3F6FC"));
            binding.tvPageTitle.setTextColor(Color.parseColor(minimalWhite ? "#162131" : "#F3F6FC"));
            binding.tvPageSubtitle.setTextColor(Color.parseColor(minimalWhite ? "#6A778C" : (matrix ? "#A9C8BE" : (stardust ? "#C6D4EA" : "#B9C2D5"))));
            binding.glowHalo.setVisibility(View.GONE);
            binding.glowSheen.setVisibility(View.GONE);
            AppearanceManager.applyEffectState(binding.glowHalo, binding.glowSheen, binding.ivDecor, state, () -> {
                IridescenceAnimator.startHeroFloat(binding.ivDecor);
                IridescenceAnimator.startDreamscape(binding.ivDecor);
            });
            updateHeaderSubtitle(state);
        });
    }

    private void updateHeaderSubtitle(SettingsState state) {
        if (state == null) {
            return;
        }
        if (SettingsPage.NOTIFICATIONS.equals(page)) {
            binding.tvPageSubtitle.setText(state.notificationsEnabled ? "当前全局通知已开启，可继续细化提醒方式。" : "当前全局通知已关闭，必要时可以重新开启。");
        } else if (SettingsPage.APPEARANCE.equals(page)) {
            binding.tvPageSubtitle.setText("当前主题：" + viewModel.themeLabel(state.themeMode) + " · 背景：" + viewModel.backgroundLabel(state.chatBackground));
        } else if (SettingsPage.PRIVACY_CONTACTS.equals(page)) {
            binding.tvPageSubtitle.setText("好友验证：" + viewModel.verifyModeLabel(state.friendVerificationMode) + " · 黑名单 " + state.blacklistUserIds.size() + " 人");
        }
    }

    private void handleItemClick(SettingsItem item) {
        switch (item.key) {
            case SettingsViewModel.KEY_CHANGE_PASSWORD:
                showChangePasswordDialog();
                break;
            case SettingsViewModel.KEY_LOCAL_PASSCODE:
                handleLocalPasscode();
                break;
            case SettingsViewModel.KEY_DEVICE_MANAGEMENT:
                openPage(SettingsPage.DEVICE_MANAGEMENT);
                break;
            case SettingsViewModel.KEY_CURRENT_ACCOUNT:
                showTip("当前登录扣扣号：" + UserHelper.getUserId(this));
                break;
            case SettingsViewModel.KEY_VERIFY_MODE:
                showChoiceDialog(
                        "加我为好友时",
                        "决定别人加你时的验证方式。",
                        new String[]{"允许任何人", "需要验证", "拒绝所有人"},
                        new String[]{"allow_all", "need_verify", "deny_all"},
                        viewModel.getSettingsState().getValue() == null ? "need_verify" : viewModel.getSettingsState().getValue().friendVerificationMode,
                        value -> {
                            viewModel.setFriendVerificationMode(value);
                            showTip("好友验证方式已更新");
                        }
                );
                break;
            case SettingsViewModel.KEY_BLACKLIST:
                openPage(SettingsPage.BLACKLIST);
                break;
            case SettingsViewModel.KEY_THEME_MODE:
                showChoiceDialog(
                        "主题模式",
                        "切换整体界面明暗基调。",
                        new String[]{"跟随系统", "深色", "浅色"},
                        new String[]{"system", "dark", "light"},
                        viewModel.getSettingsState().getValue() == null ? "system" : viewModel.getSettingsState().getValue().themeMode,
                        value -> {
                            viewModel.setThemeMode(value);
                            showTip("主题模式已切换为" + viewModel.themeLabel(value));
                        }
                );
                break;
            case SettingsViewModel.KEY_CHAT_BACKGROUND:
                showChoiceDialog(
                        "聊天背景",
                        "选择你喜欢的聊天空间氛围。",
                        new String[]{"蝴蝶流光", "全息晶尘", "极简暗调", "极简白色", "电子科幻", "代码雨"},
                        new String[]{"butterfly", "stardust", "minimal", "minimal_white", "cyber", "matrix"},
                        viewModel.getSettingsState().getValue() == null ? "butterfly" : viewModel.getSettingsState().getValue().chatBackground,
                        value -> {
                            viewModel.setChatBackground(value);
                            showTip("聊天背景已切换为" + viewModel.backgroundLabel(value));
                        }
                );
                break;
            case SettingsViewModel.KEY_FONT_SIZE:
                showChoiceDialog(
                        "字体大小",
                        "调节聊天和设置页的阅读尺度。",
                        new String[]{"小", "中", "大"},
                        new String[]{"small", "medium", "large"},
                        viewModel.getSettingsState().getValue() == null ? "medium" : viewModel.getSettingsState().getValue().fontSize,
                        value -> {
                            viewModel.setFontSize(value);
                            showTip("字体大小已切换为" + viewModel.fontLabel(value));
                        }
                );
                break;
            case SettingsViewModel.KEY_STORAGE_USAGE:
                viewModel.refreshStorageStats();
                showTip("已刷新存储统计");
                break;
            case SettingsViewModel.KEY_STORAGE_DB:
            case SettingsViewModel.KEY_STORAGE_CACHE:
            case SettingsViewModel.KEY_STORAGE_FILES:
                showTip(item.title + "：" + item.value);
                break;
            case SettingsViewModel.KEY_CLEAR_CACHE:
                showConfirmDialog("清理图片缓存", "确认清理缓存内容吗？", "清理", false, () -> viewModel.clearCache(simpleCallback()));
                break;
            case SettingsViewModel.KEY_CLEAR_CHATS:
                showConfirmDialog("清空聊天记录", "此操作不可恢复，确认继续吗？", "清空", true, () -> viewModel.clearChats(simpleCallback()));
                break;
            case SettingsViewModel.KEY_VERSION_INFO:
                startActivity(new Intent(this, VersionInfoActivity.class));
                overridePendingTransition(R.anim.chat_open_enter, R.anim.chat_open_exit);
                break;
            case SettingsViewModel.KEY_CURRENT_DEVICE:
                showTip(viewModel.getCurrentDeviceName());
                break;
            case SettingsViewModel.KEY_SYSTEM_VERSION:
                showTip(viewModel.getCurrentSystemVersion());
                break;
            case SettingsViewModel.KEY_APP_VERSION:
                showTip(item.value);
                break;
            case SettingsViewModel.KEY_ADD_BLACKLIST:
                showSingleInputDialog("添加黑名单", "输入需要拦截的扣扣号。", "10位扣扣号", "", InputType.TYPE_CLASS_NUMBER, value -> {
                    if (value.length() != 10) {
                        showTip("扣扣号必须为 10 位数字");
                        return;
                    }
                    viewModel.addToBlacklist(value);
                    showTip("已加入黑名单");
                });
                break;
            case SettingsViewModel.KEY_LOGOUT:
                performLogout();
                break;
            default:
                if (item.key.startsWith("blacklist_user:")) {
                    String userId = item.key.substring("blacklist_user:".length());
                    showConfirmDialog("移出黑名单", "确认将 " + userId + " 移出黑名单吗？", "移出", true, () -> {
                        viewModel.removeFromBlacklist(userId);
                        showTip("已移出黑名单");
                    });
                }
                break;
        }
    }

    private void handleLocalPasscode() {
        SettingsState state = viewModel.getSettingsState().getValue();
        boolean enabled = state != null && state.localPasscodeEnabled;
        if (enabled) {
            showSingleInputDialog("关闭本地密码", "输入当前本地密码以关闭保护。", "当前本地密码", "", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD, value ->
                    viewModel.clearLocalPasscode(value, simpleCallback()));
        } else {
            showSingleInputDialog("开启本地密码", "设置一个 4 位以上的本地密码。", "输入本地密码", "", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD, value ->
                    viewModel.saveLocalPasscode(value, simpleCallback()));
        }
    }

    private SettingsRepository.ResultCallback simpleCallback() {
        return new SettingsRepository.ResultCallback() {
            @Override
            public void onSuccess(String message) {
                showTip(message);
                viewModel.refreshStorageStats();
            }

            @Override
            public void onError(String message) {
                showTip(message);
            }
        };
    }

    private void showSingleInputDialog(String title, String subtitle, String hint, String currentValue, int inputType, OnValueConfirmed callback) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_profile, null, false);
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
        input.setSingleLine(true);
        input.setInputType(inputType);

        decorateDialog(dialogView, confirmButton);

        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        cancelButton.setOnClickListener(v -> dialog.dismiss());
        confirmButton.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            String value = input.getText().toString().trim();
            if (value.isEmpty()) {
                input.setError("请输入内容");
                return;
            }
            callback.onConfirm(value);
            dialog.dismiss();
        });
        dialog.show();
    }

    private void showChangePasswordDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_password, null, false);
        EditText oldInput = dialogView.findViewById(R.id.et_old_password);
        EditText newInput = dialogView.findViewById(R.id.et_new_password);
        View cancelButton = dialogView.findViewById(R.id.btn_cancel);
        View confirmButton = dialogView.findViewById(R.id.btn_confirm);

        decorateDialog(dialogView, confirmButton);

        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        cancelButton.setOnClickListener(v -> dialog.dismiss());
        confirmButton.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            String oldPassword = oldInput.getText().toString().trim();
            String newPassword = newInput.getText().toString().trim();
            if (oldPassword.isEmpty()) {
                oldInput.setError("请输入原密码");
                return;
            }
            if (newPassword.length() < 6) {
                newInput.setError("新密码至少需要 6 位");
                return;
            }
            viewModel.updatePassword(oldPassword, newPassword, new SettingsRepository.ResultCallback() {
                @Override
                public void onSuccess(String message) {
                    showTip(message);
                    dialog.dismiss();
                }

                @Override
                public void onError(String message) {
                    showTip(message);
                }
            });
        });
        dialog.show();
    }

    private void showChoiceDialog(String title,
                                  String subtitle,
                                  String[] labels,
                                  String[] values,
                                  String currentValue,
                                  OnValueConfirmed callback) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings_choice, null, false);
        TextView titleView = dialogView.findViewById(R.id.tv_dialog_title);
        TextView subtitleView = dialogView.findViewById(R.id.tv_dialog_subtitle);
        LinearLayout optionsLayout = dialogView.findViewById(R.id.layout_options);
        View cancelButton = dialogView.findViewById(R.id.btn_cancel);

        titleView.setText(title);
        subtitleView.setText(subtitle);

        for (int i = 0; i < labels.length; i++) {
            View optionView = LayoutInflater.from(this).inflate(R.layout.item_dialog_option, optionsLayout, false);
            TextView optionTitle = optionView.findViewById(R.id.tv_option_title);
            ImageView selectedView = optionView.findViewById(R.id.iv_selected);
            String label = labels[i];
            String value = values[i];
            optionTitle.setText(label);
            selectedView.setVisibility(value.equals(currentValue) ? View.VISIBLE : View.GONE);
            optionView.setOnClickListener(v -> {
                callback.onConfirm(value);
                if (dialogView.getTag() instanceof AlertDialog) {
                    ((AlertDialog) dialogView.getTag()).dismiss();
                }
            });
            optionsLayout.addView(optionView);
        }

        decorateDialog(dialogView, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();
        dialogView.setTag(dialog);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        cancelButton.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showConfirmDialog(String title, String message, String confirmText, boolean danger, Runnable action) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings_confirm, null, false);
        TextView titleView = dialogView.findViewById(R.id.tv_dialog_title);
        TextView messageView = dialogView.findViewById(R.id.tv_dialog_message);
        TextView cancelButton = dialogView.findViewById(R.id.btn_cancel);
        TextView confirmButton = dialogView.findViewById(R.id.btn_confirm);

        titleView.setText(title);
        messageView.setText(message);
        confirmButton.setText(confirmText);
        if (danger) {
            confirmButton.setTextColor(getResources().getColor(R.color.butterfly_danger));
        }

        decorateDialog(dialogView, confirmButton);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        cancelButton.setOnClickListener(v -> dialog.dismiss());
        confirmButton.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            action.run();
            dialog.dismiss();
        });
        dialog.show();
    }

    private void decorateDialog(View dialogView, View confirmButton) {
        AppearanceManager.applyEffectState(
                dialogView.findViewById(R.id.dialog_halo),
                dialogView.findViewById(R.id.dialog_sheen),
                null,
                AppearanceManager.currentState(this),
                () -> {
                    IridescenceAnimator.startHaloPulse(dialogView.findViewById(R.id.dialog_halo));
                    IridescenceAnimator.startSheenDrift(dialogView.findViewById(R.id.dialog_sheen), 0f, -20f, 0f, 12f, 0.14f, 0.28f);
                    if (confirmButton != null) {
                        IridescenceAnimator.startButtonGlow(confirmButton);
                    }
                }
        );
        AppearanceManager.applyItemAppearance(this, dialogView);
    }

    private void openPage(String targetPage) {
        Intent intent = new Intent(this, SettingsDetailActivity.class);
        intent.putExtra(EXTRA_PAGE, targetPage);
        startActivity(intent);
        overridePendingTransition(R.anim.chat_open_enter, R.anim.chat_open_exit);
    }

    private void performLogout() {
        UserHelper.clearSavedUser(this);
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    private void finishWithBackTransition() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        finish();
        overridePendingTransition(R.anim.chat_back_enter, R.anim.chat_back_exit);
    }

    private void showTip(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private interface OnValueConfirmed {
        void onConfirm(String value);
    }
}
