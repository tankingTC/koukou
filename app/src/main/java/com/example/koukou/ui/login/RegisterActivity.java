package com.example.koukou.ui.login;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.koukou.R;
import com.example.koukou.data.repository.SettingsRepository;
import com.example.koukou.databinding.ActivityRegisterBinding;
import com.example.koukou.theme.ThemePalette;
import com.example.koukou.utils.AppearanceManager;
import com.example.koukou.utils.IridescenceAnimator;
import com.example.koukou.utils.UserHelper;
import com.example.koukou.widget.RaindropFxView;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;

public class RegisterActivity extends AppCompatActivity {
    public static final String EXTRA_REGISTERED_ACCOUNT = "extra_registered_account";
    public static final String EXTRA_REGISTERED_PASSWORD = "extra_registered_password";

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        try {
            RaindropFxView.dispatchToVisible(findViewById(android.R.id.content), ev);
        } catch (Throwable ignored) {
        }
        return super.dispatchTouchEvent(ev);
    }

    private ActivityRegisterBinding binding;
    private LoginViewModel viewModel;
    private SettingsRepository settingsRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        settingsRepository = SettingsRepository.getInstance(this);

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        LoginViewModelFactory factory = new LoginViewModelFactory(this);
        viewModel = new ViewModelProvider(this, factory).get(LoginViewModel.class);

        setupBackHandling();
        setupListeners();
        observeViewModel();
        observeAppearance();
        viewModel.refreshKoukouId();
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
            if (state == null || state.immersiveEffectsEnabled) {
                IridescenceAnimator.startButtonGlow(binding.btnRegister);
            } else {
                IridescenceAnimator.stopEffects(binding.btnRegister);
            }
            applyPalette(AppearanceManager.paletteOf(state));
        });
    }

    /** 应用当前主题 palette 到所有静态文本 / 表单控件。 */
    private void applyPalette(ThemePalette palette) {
        if (palette == null) {
            return;
        }
        ColorStateList primary = ColorStateList.valueOf(palette.textPrimary);
        ColorStateList tertiary = ColorStateList.valueOf(palette.textTertiary);
        ColorStateList stroke = ColorStateList.valueOf(palette.cardStroke);

        binding.tvOverline.setTextColor(palette.textSecondary);
        binding.tvTitle.setTextColor(palette.textPrimary);
        binding.tvDesc.setTextColor(palette.textSecondary);
        binding.btnBack.setColorFilter(palette.iconPrimary);
        binding.btnBack.setBackgroundResource(palette.bgPanel);
        binding.ivLogo.setBackgroundResource(palette.bgPanel);

        binding.cvForm.setStrokeColor(palette.cardStroke);
        if (binding.cvForm.getChildCount() > 0) {
            View inner = binding.cvForm.getChildAt(0);
            inner.setBackgroundResource(palette.bgGlassCard);
            applyTextColors(inner, palette);
        }

        applyTextInputLayout(binding.tilAccount, palette, primary, tertiary, stroke);
        applyTextInputLayout(binding.tilKoukouId, palette, primary, tertiary, stroke);
        applyTextInputLayout(binding.tilPassword, palette, primary, tertiary, stroke);
        applyTextInputLayout(binding.tilConfirmPassword, palette, primary, tertiary, stroke);
    }

    private void applyTextColors(View root, ThemePalette palette) {
        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                applyTextColors(vg.getChildAt(i), palette);
            }
        }
        if (!(root instanceof TextView) || root instanceof com.google.android.material.button.MaterialButton) {
            return;
        }
        TextView tv = (TextView) root;
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
        til.setHelperTextColor(tertiary);
        til.setBoxBackgroundColor(Color.TRANSPARENT);
        til.setBoxStrokeColorStateList(stroke);
        if (til.getEditText() != null) {
            til.getEditText().setTextColor(palette.textPrimary);
            til.getEditText().setHintTextColor(palette.textTertiary);
        }
    }

    private void setupBackHandling() {
        binding.btnBack.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            finishWithBackTransition();
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishWithBackTransition();
            }
        });
    }

    private void setupListeners() {
        binding.tilKoukouId.setEndIconOnClickListener(v -> {
            binding.etKoukouId.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            viewModel.refreshKoukouId();
            showTip("正在刷新扣扣号");
        });

        binding.btnRegister.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            String nickname = getText(binding.etAccount);
            String koukouId = getText(binding.etKoukouId);
            String password = getText(binding.etPassword);
            String confirmPassword = getText(binding.etConfirmPassword);

            // 清除之前的错误
            binding.tilAccount.setError(null);
            binding.tilKoukouId.setError(null);
            binding.tilPassword.setError(null);
            binding.tilConfirmPassword.setError(null);

            if (nickname.isEmpty()) {
                binding.tilAccount.setError("请输入昵称");
                binding.etAccount.requestFocus();
                return;
            }
            if (koukouId.isEmpty()) {
                binding.tilKoukouId.setError("请先生成或输入扣扣号");
                binding.etKoukouId.requestFocus();
                return;
            }
            if (password.isEmpty()) {
                binding.tilPassword.setError("请输入密码");
                binding.etPassword.requestFocus();
                return;
            }
            if (confirmPassword.isEmpty()) {
                binding.tilConfirmPassword.setError("请再次输入密码");
                binding.etConfirmPassword.requestFocus();
                return;
            }
            if (!password.equals(confirmPassword)) {
                binding.tilConfirmPassword.setError("两次密码输入不一致");
                binding.etConfirmPassword.requestFocus();
                return;
            }

            viewModel.register(nickname, koukouId, password);
        });

        // 输入变化时清除错误提示
        attachErrorClearer(binding.tilAccount);
        attachErrorClearer(binding.tilKoukouId);
        attachErrorClearer(binding.tilPassword);
        attachErrorClearer(binding.tilConfirmPassword);
    }

    private void attachErrorClearer(TextInputLayout til) {
        if (til == null || til.getEditText() == null) {
            return;
        }
        til.getEditText().addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (til.getError() != null) {
                    til.setError(null);
                }
            }
        });
    }

    private void observeViewModel() {
        viewModel.getLoading().observe(this, isLoading -> {
            boolean loading = Boolean.TRUE.equals(isLoading);
            binding.btnRegister.setEnabled(!loading);
            binding.btnRegister.setText(loading ? "注册中..." : "注册账号");
        });

        viewModel.getError().observe(this, errorMsg -> {
            if (errorMsg != null && !errorMsg.trim().isEmpty()) {
                showTip(errorMsg);
            }
        });

        viewModel.getGeneratedKoukouId().observe(this, koukouId -> {
            if (koukouId != null && !koukouId.isEmpty()) {
                binding.etKoukouId.setText(koukouId);
                binding.etKoukouId.setSelection(koukouId.length());
            }
        });

        viewModel.getRegisterSuccess().observe(this, user -> {
            if (user == null) {
                return;
            }
            UserHelper.saveLoginHistory(this, user.userId, user.password, user.nickname, user.avatarUrl);
            Intent data = new Intent();
            data.putExtra(EXTRA_REGISTERED_ACCOUNT, user.userId);
            data.putExtra(EXTRA_REGISTERED_PASSWORD, user.password);
            setResult(RESULT_OK, data);
            showTip("注册成功，扣扣号：" + user.userId);
            binding.getRoot().postDelayed(this::finishWithBackTransition, 200);
        });
    }

    private void finishWithBackTransition() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        finish();
        overridePendingTransition(R.anim.chat_back_enter, R.anim.chat_back_exit);
    }

    private String getText(TextView textView) {
        return textView.getText() == null ? "" : textView.getText().toString().trim();
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
}
