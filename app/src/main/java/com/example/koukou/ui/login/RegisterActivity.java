package com.example.koukou.ui.login;

import android.content.Intent;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.koukou.R;
import com.example.koukou.data.repository.SettingsRepository;
import com.example.koukou.databinding.ActivityRegisterBinding;
import com.example.koukou.utils.AppearanceManager;
import com.example.koukou.utils.IridescenceAnimator;
import com.example.koukou.utils.UserHelper;

public class RegisterActivity extends AppCompatActivity {
    public static final String EXTRA_REGISTERED_ACCOUNT = "extra_registered_account";
    public static final String EXTRA_REGISTERED_PASSWORD = "extra_registered_password";

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
            binding.glowHalo.setVisibility(android.view.View.GONE);
            binding.glowSheenTop.setVisibility(android.view.View.GONE);
            AppearanceManager.applyEffectState(binding.glowHalo, binding.glowSheenTop, binding.ivHero, state, () -> {
                IridescenceAnimator.startHeroFloat(binding.ivHero);
                IridescenceAnimator.startDreamscape(binding.ivHero);
            });
            if (state == null || state.immersiveEffectsEnabled) {
                IridescenceAnimator.startButtonGlow(binding.btnRegister);
            } else {
                IridescenceAnimator.stopEffects(binding.btnRegister);
            }
        });
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

            if (nickname.isEmpty()) {
                showTip("请输入昵称");
                return;
            }
            if (koukouId.isEmpty()) {
                showTip("请先生成或输入扣扣号");
                return;
            }
            if (password.isEmpty()) {
                showTip("请输入密码");
                return;
            }
            if (confirmPassword.isEmpty()) {
                showTip("请再次输入密码");
                return;
            }
            if (!password.equals(confirmPassword)) {
                showTip("两次密码输入不一致");
                return;
            }

            viewModel.register(nickname, koukouId, password);
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
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }
}
