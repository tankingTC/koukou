package com.example.koukou.ui.settings;

import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.koukou.R;
import com.example.koukou.data.repository.SettingsRepository;
import com.example.koukou.databinding.ActivityVersionInfoBinding;
import com.example.koukou.utils.AppearanceManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class VersionInfoActivity extends AppCompatActivity {
    private ActivityVersionInfoBinding binding;
    private SettingsRepository settingsRepository;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityVersionInfoBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        settingsRepository = SettingsRepository.getInstance(this);

        binding.toolbar.setNavigationOnClickListener(v -> finishWithBackTransition());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishWithBackTransition();
            }
        });

        binding.tvChangelog.setText(loadChangelogFromJson());
        observeAppearance();
    }

    private String loadChangelogFromJson() {
        StringBuilder text = new StringBuilder();

        try {
            InputStream inputStream = getResources().openRawResource(R.raw.version_changelog);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }
            reader.close();

            JSONArray versions = new JSONArray(jsonBuilder.toString());
            for (int i = 0; i < versions.length(); i++) {
                JSONObject item = versions.optJSONObject(i);
                if (item == null) {
                    continue;
                }

                String version = item.optString("version", "");
                String date = item.optString("date", "");
                text.append(version).append("  ").append(date).append("\n");

                JSONArray changes = item.optJSONArray("changes");
                if (changes != null) {
                    for (int j = 0; j < changes.length(); j++) {
                        String change = changes.optString(j, "");
                        if (!change.isEmpty()) {
                            text.append("- ").append(change).append("\n");
                        }
                    }
                }

                if (i < versions.length() - 1) {
                    text.append("\n");
                }
            }
        } catch (Exception e) {
            text.append("版本日志加载失败，请稍后重试。");
        }

        return text.toString();
    }

    private void finishWithBackTransition() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        finish();
        overridePendingTransition(R.anim.chat_back_enter, R.anim.chat_back_exit);
    }

    private void observeAppearance() {
        settingsRepository.getSettingsLiveData().observe(this,
                state -> AppearanceManager.applyPageAppearance(this, getWindow(), binding.getRoot(), state));
    }
}
