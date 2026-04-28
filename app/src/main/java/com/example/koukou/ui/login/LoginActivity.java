package com.example.koukou.ui.login;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;
import android.widget.Toast;

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
import com.example.koukou.utils.AppearanceManager;
import com.example.koukou.utils.IridescenceAnimator;
import com.example.koukou.utils.UserHelper;

import java.util.ArrayList;
import java.util.List;

public class LoginActivity extends AppCompatActivity {
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
        setupAnimations();
        setupListeners();
        observeViewModel();
        observeAppearance();
        restoreSavedAccount();
        initQuickLoginDropdown();
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

        binding.cvForm.setTranslationY(-100f);
        binding.cvForm.setAlpha(0f);
        binding.cvForm.animate()
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
            if (state == null || state.immersiveEffectsEnabled) {
                binding.glowSheenBottom.setVisibility(View.GONE);
                IridescenceAnimator.startButtonGlow(binding.btnLogin);
            } else {
                binding.glowSheenBottom.setVisibility(View.GONE);
                IridescenceAnimator.stopEffects(binding.glowSheenBottom);
                IridescenceAnimator.stopEffects(binding.btnLogin);
            }
            AppearanceManager.refreshRecyclerAppearance(this, binding.layoutHistory.rvHistory);
        });
    }

    private void setupListeners() {
        binding.getRoot().setOnClickListener(v -> hideHistoryList());
        binding.layoutHistory.cardRoot.setOnClickListener(v -> {
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

        binding.tilAccount.setEndIconOnClickListener(v -> toggleHistoryList());

        binding.btnLogin.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            playButtonPress(v);
            String account = textOf(binding.etAccount);
            String password = binding.etPassword.getText() == null ? "" : binding.etPassword.getText().toString();
            viewModel.login(account, password);
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
            UserHelper.saveUser(this, account, password, user.userId);
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
        historyAdapter = new HistoryAdapter(savedLogins, new HistoryAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(UserHelper.SavedLogin item) {
                binding.etAccount.setText(item.account);
                binding.etAccount.setSelection(item.account.length());
                binding.etPassword.setText(item.password);
                binding.etPassword.setSelection(item.password.length());
                hideHistoryList();
                showTip("已填充历史账号");
            }

            @Override
            public void onDeleteClick(UserHelper.SavedLogin item, int position) {
                deleteAccount(item, position);
            }
        });

        RecyclerView rv = binding.layoutHistory.rvHistory;
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(historyAdapter);
    }

    private void deleteAccount(UserHelper.SavedLogin item, int position) {
        UserHelper.removeLoginHistory(this, item.account);
        savedLogins.remove(position);
        historyAdapter.notifyItemRemoved(position);
        if (savedLogins.isEmpty()) {
            binding.layoutHistory.cardRoot.setVisibility(View.GONE);
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
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
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
