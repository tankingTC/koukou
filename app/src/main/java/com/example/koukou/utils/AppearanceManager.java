package com.example.koukou.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.koukou.R;
import com.example.koukou.data.repository.SettingsRepository;
import com.example.koukou.ui.settings.model.SettingsState;
import com.example.koukou.widget.CodeRainView;
import com.example.koukou.widget.ThemeAtmosphereView;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class AppearanceManager {
    private static final Set<String> EFFECT_TAGS = new HashSet<>(Arrays.asList(
            "particle",
            "spray_particle",
            "trail_particle",
            "butterfly_glow",
            "light_beam",
            "chroma_cyan",
            "chroma_pearl",
            "core_glow"
    ));

    private AppearanceManager() {
    }

    public static SettingsState currentState(Context context) {
        SettingsState state = SettingsRepository.getInstance(context).getSettingsLiveData().getValue();
        return state == null ? new SettingsState() : state;
    }

    public static void applyPageAppearance(Context context, Window window, View root, SettingsState state) {
        if (root == null) {
            return;
        }
        SettingsState safeState = state == null ? new SettingsState() : state;
        applyThemeMode(safeState.themeMode, safeState.chatBackground);
        boolean lightPalette = usesLightPalette(context, safeState.themeMode, safeState.chatBackground);
        root.setBackgroundResource(resolveBackgroundRes(safeState.chatBackground, lightPalette));
        applyWindowStyle(window, root, lightPalette);
        applyTextScale(root, fontScale(safeState.fontSize));
        View codeRainView = root.findViewById(R.id.code_rain_view);
        View themeEffectView = root.findViewById(R.id.theme_effect_view);
        boolean enableRain = "matrix".equals(safeState.chatBackground) && safeState.immersiveEffectsEnabled;
        boolean enableThemeEffect = safeState.immersiveEffectsEnabled && !"matrix".equals(safeState.chatBackground);
        if (codeRainView instanceof CodeRainView) {
            CodeRainView rainView = (CodeRainView) codeRainView;
            rainView.setVisibility(enableRain ? View.VISIBLE : View.GONE);
            rainView.setLightPalette(lightPalette);
            rainView.setRainEnabled(enableRain);
        }
        if (themeEffectView instanceof ThemeAtmosphereView) {
            ThemeAtmosphereView atmosphereView = (ThemeAtmosphereView) themeEffectView;
            atmosphereView.setVisibility(enableThemeEffect ? View.VISIBLE : View.GONE);
            atmosphereView.setLightPalette(lightPalette);
            atmosphereView.setMode(mapThemeEffectMode(safeState.chatBackground));
            atmosphereView.setEffectEnabled(enableThemeEffect);
        }
    }

    public static void applyItemAppearance(Context context, View root) {
        if (root == null) {
            return;
        }
        applyTextScale(root, fontScale(currentState(context).fontSize));
    }

    public static void applyNestedPageAppearance(Context context, View root, SettingsState state) {
        if (root == null) {
            return;
        }
        SettingsState safeState = state == null ? new SettingsState() : state;
        applyTextScale(root, fontScale(safeState.fontSize));
        root.setBackgroundColor(Color.TRANSPARENT);
        boolean lightPalette = usesLightPalette(context, safeState.themeMode, safeState.chatBackground);
        View codeRainView = root.findViewById(R.id.code_rain_view);
        View themeEffectView = root.findViewById(R.id.theme_effect_view);
        boolean enableRain = "matrix".equals(safeState.chatBackground) && safeState.immersiveEffectsEnabled;
        boolean enableThemeEffect = safeState.immersiveEffectsEnabled && !"matrix".equals(safeState.chatBackground);
        if (codeRainView instanceof CodeRainView) {
            CodeRainView rainView = (CodeRainView) codeRainView;
            rainView.setVisibility(enableRain ? View.VISIBLE : View.GONE);
            rainView.setLightPalette(lightPalette);
            rainView.setRainEnabled(enableRain);
        }
        if (themeEffectView instanceof ThemeAtmosphereView) {
            ThemeAtmosphereView atmosphereView = (ThemeAtmosphereView) themeEffectView;
            atmosphereView.setVisibility(enableThemeEffect ? View.VISIBLE : View.GONE);
            atmosphereView.setLightPalette(lightPalette);
            atmosphereView.setMode(mapThemeEffectMode(safeState.chatBackground));
            atmosphereView.setEffectEnabled(enableThemeEffect);
        }
    }

    public static void refreshRecyclerAppearance(Context context, RecyclerView recyclerView) {
        if (recyclerView == null) {
            return;
        }
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            applyItemAppearance(context, recyclerView.getChildAt(i));
        }
    }

    public static void applyEffectState(View haloView,
                                        View sheenView,
                                        View butterflyRoot,
                                        SettingsState state,
                                        Runnable starter) {
        SettingsState safeState = state == null ? new SettingsState() : state;
        boolean enabled = safeState.immersiveEffectsEnabled;
        boolean dialogMode = butterflyRoot == null;
        if (enabled) {
            setEffectViewVisible(haloView, dialogMode);
            setEffectViewVisible(sheenView, dialogMode);
            setTaggedEffectVisibility(butterflyRoot, false);
            if (butterflyRoot != null) {
                butterflyRoot.setVisibility(View.GONE);
                butterflyRoot.setAlpha(0f);
                butterflyRoot.setTranslationX(0f);
                butterflyRoot.setTranslationY(0f);
                butterflyRoot.setScaleX(1f);
                butterflyRoot.setScaleY(1f);
            }
            if (starter != null && dialogMode) {
                starter.run();
            }
            return;
        }

        IridescenceAnimator.stopEffects(haloView);
        IridescenceAnimator.stopEffects(sheenView);
        IridescenceAnimator.stopEffects(butterflyRoot);
        setEffectViewVisible(haloView, false);
        setEffectViewVisible(sheenView, false);
        setTaggedEffectVisibility(butterflyRoot, false);
        if (butterflyRoot != null) {
            butterflyRoot.setVisibility(View.GONE);
            butterflyRoot.setAlpha(0f);
            butterflyRoot.setTranslationX(0f);
            butterflyRoot.setTranslationY(0f);
            butterflyRoot.setScaleX(1f);
            butterflyRoot.setScaleY(1f);
        }
    }

    public static void applyThemeMode(String mode, String chatBackground) {
        int targetMode;
        if (usesLightPalette(null, mode, chatBackground)) {
            targetMode = AppCompatDelegate.MODE_NIGHT_NO;
        } else if ("light".equals(mode)) {
            targetMode = AppCompatDelegate.MODE_NIGHT_NO;
        } else if ("system".equals(mode)) {
            targetMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        } else {
            targetMode = AppCompatDelegate.MODE_NIGHT_YES;
        }
        if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
            AppCompatDelegate.setDefaultNightMode(targetMode);
        }
    }

    public static float fontScale(String fontSize) {
        if ("small".equals(fontSize)) {
            return 0.9f;
        }
        if ("large".equals(fontSize)) {
            return 1.12f;
        }
        return 1f;
    }

    private static boolean usesLightPalette(Context context, String mode, String background) {
        if ("minimal".equals(background)) {
            return false;
        }
        if ("minimal_white".equals(background)) {
            return true;
        }
        if ("stardust".equals(background)) {
            return false;
        }
        return "light".equals(mode);
    }

    private static int resolveBackgroundRes(String background, boolean lightPalette) {
        if ("minimal".equals(background)) {
            return R.drawable.bg_scene_minimal;
        }
        if ("minimal_white".equals(background)) {
            return R.drawable.bg_scene_minimal_white;
        }
        if ("cyber".equals(background)) {
            return lightPalette ? R.drawable.bg_scene_cyber_light : R.drawable.bg_scene_cyber;
        }
        if ("matrix".equals(background)) {
            return lightPalette ? R.drawable.bg_scene_matrix_light : R.drawable.bg_scene_matrix;
        }
        if ("stardust".equals(background)) {
            return R.drawable.bg_scene_stardust;
        }
        return lightPalette ? R.drawable.bg_butterfly_scene_light : R.drawable.bg_butterfly_scene;
    }

    private static String mapThemeEffectMode(String background) {
        if ("minimal".equals(background) || "minimal_white".equals(background)) {
            return ThemeAtmosphereView.MODE_MINIMAL;
        }
        if ("cyber".equals(background)) {
            return ThemeAtmosphereView.MODE_CYBER;
        }
        if ("stardust".equals(background)) {
            return ThemeAtmosphereView.MODE_STARDUST;
        }
        return ThemeAtmosphereView.MODE_BUTTERFLY;
    }

    private static void applyWindowStyle(Window window, View root, boolean lightPalette) {
        if (window == null || root == null) {
            return;
        }
        window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
        window.setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        WindowInsetsControllerCompat controller = ViewCompat.getWindowInsetsController(root);
        if (controller != null) {
            controller.setAppearanceLightStatusBars(lightPalette);
            controller.setAppearanceLightNavigationBars(lightPalette);
        }
    }

    private static void applyTextScale(View view, float scale) {
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            Object tag = textView.getTag(R.id.tag_base_text_size);
            float baseSize = tag instanceof Float ? (Float) tag : textView.getTextSize();
            if (!(tag instanceof Float)) {
                textView.setTag(R.id.tag_base_text_size, baseSize);
            }
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, baseSize * scale);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyTextScale(group.getChildAt(i), scale);
            }
        }
    }

    private static void setEffectViewVisible(View view, boolean visible) {
        if (view == null) {
            return;
        }
        view.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        if (visible) {
            view.setTranslationX(0f);
            view.setTranslationY(0f);
            view.setScaleX(1f);
            view.setScaleY(1f);
        }
    }

    private static void setTaggedEffectVisibility(View root, boolean visible) {
        if (!(root instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            Object tag = child.getTag();
            if (tag instanceof String && EFFECT_TAGS.contains(tag)) {
                child.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
            }
            if (child instanceof ViewGroup) {
                setTaggedEffectVisibility(child, visible);
            }
        }
    }
}
