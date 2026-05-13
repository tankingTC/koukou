package com.example.koukou.ui.login;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.koukou.MainActivity;
import com.example.koukou.data.repository.SettingsRepository;
import com.example.koukou.databinding.ActivityLoginBinding;
import com.example.koukou.theme.ThemePalette;
import com.example.koukou.utils.AppearanceManager;
import com.example.koukou.utils.IridescenceAnimator;
import com.example.koukou.utils.UserHelper;
import com.example.koukou.widget.RaindropFxView;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

public class LoginActivity extends AppCompatActivity {

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        try {
            RaindropFxView.dispatchToVisible(findViewById(android.R.id.content), ev);
        } catch (Throwable ignored) {
        }
        return super.dispatchTouchEvent(ev);
    }

    private ActivityLoginBinding binding;
    private LoginViewModel viewModel;
    private final List<UserHelper.SavedLogin> savedLogins = new ArrayList<>();
    private HistoryAdapter historyAdapter;
    private ActivityResultLauncher<Intent> registerLauncher;
    private SettingsRepository settingsRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        settingsRepository = SettingsRepository.getInstance(this);

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        LoginViewModelFactory factory = new LoginViewModelFactory(this);
        viewModel = new ViewModelProvider(this, factory).get(LoginViewModel.class);

        setupRegisterLauncher();
        setupListeners();
        observeViewModel();
        observeAppearance();
        restoreSavedAccount();
        initQuickLoginDropdown();
        // setupAnimations 依赖 cvHistory/cvForm 可见性，必须在 initQuickLoginDropdown 之后执行
        setupAnimations();
    }

    private void setupRegisterLauncher() {
        registerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                String account = result.getData().getStringExtra(RegisterActivity.EXTRA_REGISTERED_ACCOUNT);
                String password = result.getData().getStringExtra(RegisterActivity.EXTRA_REGISTERED_PASSWORD);
                if (account != null) {
                    binding.etAccount.setText(account);
                    binding.etAccount.setSelection(account.length());
                }
                if (password != null) {
                    binding.etPassword.setText(password);
                    binding.etPassword.setSelection(password.length());
                }
                refreshSavedLogins();
                showTip("新账号已创建，可直接登录");
            }
        });
    }

    private void setupAnimations() {
        binding.ivLogo.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(300)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        // 首屏被选中的卡片（历史 or 表单）从上方轻轻滑入
        View introCard = binding.cvHistory.getVisibility() == View.VISIBLE ? binding.cvHistory : binding.cvForm;
        introCard.setTranslationY(-100f);
        introCard.setAlpha(0f);
        introCard.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(400)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void observeAppearance() {
        settingsRepository.getSettingsLiveData().observe(this, state -> {
            AppearanceManager.applyPageAppearance(this, getWindow(), binding.getRoot(), state);
            binding.glowHalo.setVisibility(View.GONE);
            binding.glowSheenTop.setVisibility(View.GONE);
            AppearanceManager.applyEffectState(binding.glowHalo, binding.glowSheenTop, binding.ivHero, state, () -> {
                IridescenceAnimator.startHeroFloat(binding.ivHero);
                IridescenceAnimator.startDreamscape(binding.ivHero);
            });
            // glow_sheen_bottom 在新布局中是 0×0 GONE 占位 view，不再参与动画或可见性控制
            if (state == null || state.immersiveEffectsEnabled) {
                IridescenceAnimator.startButtonGlow(binding.btnLogin);
            } else {
                IridescenceAnimator.stopEffects(binding.btnLogin);
            }
            AppearanceManager.refreshRecyclerAppearance(this, binding.layoutHistory.rvHistory);
            applyPalette(AppearanceManager.paletteOf(state));
        });
    }

    /**
     * 将当前主题 palette 应用到登录页所有静态文本 / 表单控件 / 卡片背景，避免浅色主题下
     * 出现“白底白字”对比度问题。
     */
    private void applyPalette(ThemePalette palette) {
        if (palette == null) {
            return;
        }
        ColorStateList primary = ColorStateList.valueOf(palette.textPrimary);
        ColorStateList tertiary = ColorStateList.valueOf(palette.textTertiary);
        ColorStateList stroke = ColorStateList.valueOf(palette.cardStroke);

        // 品牌区文本
        binding.tvTitle.setTextColor(palette.textPrimary);
        binding.tvSubtitle.setTextColor(palette.textSecondary);
        binding.tvRegister.setTextColor(palette.textSecondary);
        binding.btnUseOther.setTextColor(palette.textPrimary);
        binding.ivLogo.setBackgroundResource(palette.bgPanel);

        // 历史卡片 / 表单卡片描边
        binding.cvHistory.setStrokeColor(palette.cardStroke);
        binding.cvForm.setStrokeColor(palette.cardStroke);

        // 历史卡片内部背景 / 文本
        applyCardInnerSurface(binding.cvHistory, palette);
        applyCardInnerSurface(binding.cvForm, palette);

        // 输入框：描边 / hint / 提示色
        applyTextInputLayout(binding.tilAccount, palette, primary, tertiary, stroke);
        applyTextInputLayout(binding.tilPassword, palette, primary, tertiary, stroke);

        // ProgressBar
        binding.pbLoading.setIndeterminateTintList(primary);
    }

    private void applyCardInnerSurface(View card, ThemePalette palette) {
        if (!(card instanceof com.google.android.material.card.MaterialCardView)) {
            return;
        }
        com.google.android.material.card.MaterialCardView cv = (com.google.android.material.card.MaterialCardView) card;
        if (cv.getChildCount() == 0) {
            return;
        }
        View inner = cv.getChildAt(0);
        inner.setBackgroundResource(palette.bgGlassCard);
        applyTextColors(inner, palette);
    }

    private void applyTextColors(View root, ThemePalette palette) {
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup vg = (android.view.ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                applyTextColors(vg.getChildAt(i), palette);
            }
        }
        if (!(root instanceof TextView) || root instanceof com.google.android.material.button.MaterialButton) {
            return;
        }
        TextView tv = (TextView) root;
        // 根据字号区分主、副、提示三类，避免为每个 TextView 加 tag
        float sp = tv.getTextSize() / tv.getResources().getDisplayMetrics().scaledDensity;
        if (sp >= 16f) {
            tv.setTextColor(palette.textPrimary);
        } else if (sp >= 13f) {
            tv.setTextColor(palette.textSecondary);
        } else {
            tv.setTextColor(palette.textTertiary);
        }
    }

    private void applyTextInputLayout(TextInputLayout til,
                                      ThemePalette palette,
                                      ColorStateList primary,
                                      ColorStateList tertiary,
                                      ColorStateList stroke) {
        if (til == null) {
            return;
        }
        til.setBoxStrokeColor(palette.cardStroke);
        til.setHintTextColor(tertiary);
        til.setDefaultHintTextColor(tertiary);
        til.setBoxBackgroundColor(Color.TRANSPARENT);
        til.setBoxStrokeColorStateList(stroke);
        if (til.getEditText() != null) {
            til.getEditText().setTextColor(palette.textPrimary);
            til.getEditText().setHintTextColor(palette.textTertiary);
        }
        if (til.isErrorEnabled()) {
            // 错误色保留 Material 默认 (贤色系)，不使用 palette
        }
    }

    private void setupListeners() {
        // 点击空白处隐藏老下拉式历史列表
        binding.getRoot().setOnClickListener(v -> hideHistoryList());
        binding.layoutHistory.cardRoot.setOnClickListener(v -> {
            // 吞掉点击，避免穿透到根布局
        });

        View.OnFocusChangeListener focusListener = (v, hasFocus) -> v.animate()
                .translationY(hasFocus ? -6f : 0f)
                .setDuration(150)
                .start();
        binding.etAccount.setOnFocusChangeListener(focusListener);
        binding.etPassword.setOnFocusChangeListener(focusListener);

        binding.etAccount.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterChanged(String value) {
                String password = UserHelper.getPasswordByAccount(LoginActivity.this, value.trim());
                if (password != null) {
                    binding.etPassword.setText(password);
                    binding.etPassword.setSelection(password.length());
                }
            }
        });

        // 表单账号右侧下拉仍可弹出老式历史下拉（作为 fallback）
        binding.tilAccount.setEndIconOnClickListener(v -> toggleHistoryList());

        // B+C: "使用其他账号" 切换为表单输入模式
        binding.btnUseOther.setOnClickListener(v -> showFormMode());

        binding.btnLogin.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            playButtonPress(v);
            String account = textOf(binding.etAccount);
            String password = binding.etPassword.getText() == null ? "" : binding.etPassword.getText().toString();
            // 内联验证错误优先，不再靠 Toast
            binding.tilAccount.setError(null);
            binding.tilPassword.setError(null);
            if (account.isEmpty()) {
                binding.tilAccount.setError("请输入扣扣号");
                binding.etAccount.requestFocus();
                return;
            }
            if (password.isEmpty()) {
                binding.tilPassword.setError("请输入密码");
                binding.etPassword.requestFocus();
                return;
            }
            viewModel.login(account, password);
        });

        // 输入变化时清除错误提示
        binding.etAccount.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterChanged(String value) {
                if (binding.tilAccount.getError() != null) {
                    binding.tilAccount.setError(null);
                }
            }
        });
        binding.etPassword.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterChanged(String value) {
                if (binding.tilPassword.getError() != null) {
                    binding.tilPassword.setError(null);
                }
            }
        });

        binding.tvRegister.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            registerLauncher.launch(new Intent(LoginActivity.this, RegisterActivity.class));
            overridePendingTransition(com.example.koukou.R.anim.chat_open_enter, com.example.koukou.R.anim.chat_open_exit);
        });
    }

    private void observeViewModel() {
        viewModel.getLoading().observe(this, isLoading -> {
            boolean loading = Boolean.TRUE.equals(isLoading);
            binding.btnLogin.setText(loading ? "登录中..." : "登录");
            binding.btnLogin.setEnabled(!loading);
            binding.pbLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        });

        viewModel.getError().observe(this, errorMsg -> {
            if (errorMsg != null && !errorMsg.trim().isEmpty()) {
                showTip(errorMsg);
            }
        });

        viewModel.getLoginSuccess().observe(this, user -> {
            if (user == null) {
                return;
            }
            String account = user.account != null ? user.account : "";
            String password = binding.etPassword.getText() == null ? "" : binding.etPassword.getText().toString();
            UserHelper.saveLoginHistory(this, account, password, user.nickname, user.avatarUrl);
            UserHelper.saveUser(this, account, password, user.userId, user.authToken);
            UserHelper.saveProfile(this, user.nickname, user.avatarUrl, user.signature);

            showTip("登录成功，欢迎回来");
            binding.getRoot().postDelayed(() -> {
                Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                startActivity(intent);
                overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
                finish();
            }, 180);
        });
    }

    private void restoreSavedAccount() {
        String savedAccount = UserHelper.getAccount(this);
        String savedPassword = UserHelper.getPassword(this);
        if (savedAccount != null) {
            binding.etAccount.setText(savedAccount);
        }
        if (savedPassword != null) {
            binding.etPassword.setText(savedPassword);
        }
    }

    private void initQuickLoginDropdown() {
        refreshSavedLogins();
        // B+C: 点击历史账号直接登录（不再填表单）。同一个 adapter 同时服务主卸入式历史与老下拉 fallback。
        HistoryAdapter.OnItemClickListener listener = new HistoryAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(UserHelper.SavedLogin item) {
                binding.etAccount.setText(item.account);
                binding.etPassword.setText(item.password);
                hideHistoryList();
                viewModel.login(item.account, item.password);
            }

            @Override
            public void onDeleteClick(UserHelper.SavedLogin item, int position) {
                deleteAccount(item, position);
            }
        };
        historyAdapter = new HistoryAdapter(savedLogins, listener);

        // 主卸入式历史列表（首屏卡片）
        RecyclerView inline = binding.rvHistoryInline;
        inline.setLayoutManager(new LinearLayoutManager(this));
        inline.setAdapter(historyAdapter);

        // 老下拉式（fallback）依然可用，但默认 hidden
        RecyclerView fallback = binding.layoutHistory.rvHistory;
        fallback.setLayoutManager(new LinearLayoutManager(this));
        fallback.setAdapter(new HistoryAdapter(savedLogins, listener));

        applyEntryMode();
    }

    /** 根据是否有历史账号选择首屏是历史卡片 / 表单卡片。 */
    private void applyEntryMode() {
        boolean hasHistory = !savedLogins.isEmpty();
        binding.cvHistory.setVisibility(hasHistory ? View.VISIBLE : View.GONE);
        binding.btnUseOther.setVisibility(hasHistory ? View.VISIBLE : View.GONE);
        binding.cvForm.setVisibility(hasHistory ? View.GONE : View.VISIBLE);
        binding.btnLogin.setVisibility(hasHistory ? View.GONE : View.VISIBLE);
    }

    /** 点 "使用其他账号" 后进入表单模式。 */
    private void showFormMode() {
        binding.cvHistory.setVisibility(View.GONE);
        binding.btnUseOther.setVisibility(View.GONE);
        binding.cvForm.setVisibility(View.VISIBLE);
        binding.btnLogin.setVisibility(View.VISIBLE);
        binding.cvForm.setAlpha(0f);
        binding.cvForm.setTranslationY(-24f);
        binding.cvForm.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(220)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void deleteAccount(UserHelper.SavedLogin item, int position) {
        UserHelper.removeLoginHistory(this, item.account);
        savedLogins.remove(position);
        historyAdapter.notifyItemRemoved(position);
        if (savedLogins.isEmpty()) {
            binding.layoutHistory.cardRoot.setVisibility(View.GONE);
            // 历史清空后自动切表单模式
            showFormMode();
        }
        showTip("已删除历史账号");
    }

    private void toggleHistoryList() {
        if (binding.layoutHistory.cardRoot.getVisibility() == View.VISIBLE) {
            hideHistoryList();
            return;
        }
        refreshSavedLogins();
        if (savedLogins.isEmpty()) {
            showTip("暂无历史扣扣号");
            return;
        }
        binding.layoutHistory.cardRoot.setAlpha(0f);
        binding.layoutHistory.cardRoot.setVisibility(View.VISIBLE);
        binding.layoutHistory.cardRoot.animate().alpha(1f).setDuration(250).start();
    }

    private void hideHistoryList() {
        View card = binding.layoutHistory.cardRoot;
        if (card.getVisibility() != View.VISIBLE) {
            return;
        }
        card.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction(() -> card.setVisibility(View.GONE))
                .start();
    }

    private void refreshSavedLogins() {
        savedLogins.clear();
        savedLogins.addAll(UserHelper.getSavedLogins(this));
        if (historyAdapter != null) {
            historyAdapter.notifyDataSetChanged();
            applyEntryMode();
        }
    }

    private void playButtonPress(@NonNull View view) {
        ObjectAnimator scaleDownX = ObjectAnimator.ofFloat(view, View.SCALE_X, 0.95f);
        ObjectAnimator scaleDownY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.95f);
        scaleDownX.setDuration(75);
        scaleDownY.setDuration(75);
        scaleDownX.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                view.animate().scaleX(1f).scaleY(1f).setDuration(75).start();
            }
        });
        scaleDownX.start();
        scaleDownY.start();
    }

    private String textOf(TextView view) {
        return view.getText() == null ? "" : view.getText().toString().trim();
    }

    private void showTip(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Snackbar bar = Snackbar.make(binding.getRoot(), text, Snackbar.LENGTH_SHORT);
        ThemePalette palette = AppearanceManager.currentPalette(this);
        View view = bar.getView();
        view.setBackgroundColor(palette.lightPalette ? 0xEEFFFFFF : 0xCC152443);
        TextView snackText = view.findViewById(com.google.android.material.R.id.snackbar_text);
        if (snackText != null) {
            snackText.setTextColor(palette.textPrimary);
        }
        bar.show();
    }

    private abstract static class SimpleTextWatcher implements android.text.TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(android.text.Editable s) {
            afterChanged(s == null ? "" : s.toString());
        }

        public abstract void afterChanged(String value);
    }
}
