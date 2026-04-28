package com.example.koukou;

import android.graphics.Color;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.animation.ObjectAnimator;
import android.animation.AnimatorSet;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.example.koukou.data.local.AppDatabase;
import com.example.koukou.data.repository.MessageRepository;
import com.example.koukou.data.repository.SettingsRepository;
import com.example.koukou.databinding.ActivityMainBinding;
import com.example.koukou.ui.settings.model.SettingsState;
import com.example.koukou.network.websocket.WebSocketManager;
import com.example.koukou.utils.AppExecutors;
import com.example.koukou.utils.AppearanceManager;
import com.example.koukou.utils.UserHelper;
import com.google.android.material.badge.BadgeDrawable;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private com.example.koukou.data.local.dao.ConversationDao convDao;
    private String currentUserId;
    private int selectedNavItemId = R.id.nav_conversations;
    private SettingsRepository settingsRepository;
    private ObjectAnimator navAmbientAnimator;
    private SettingsState currentAppearanceState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.navAmbientGlow.setVisibility(View.GONE);

        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        WindowInsetsControllerCompat controller = ViewCompat.getWindowInsetsController(binding.main);
        if (controller != null) {
            controller.setAppearanceLightStatusBars(false);
            controller.setAppearanceLightNavigationBars(false);
        }

        currentUserId = UserHelper.getUserId(this);
        settingsRepository = SettingsRepository.getInstance(this);

        AppDatabase db = AppDatabase.getInstance(this);
        convDao = db.conversationDao();

        MessageRepository repo = MessageRepository.getInstance(
                db.messageDao(),
                db.conversationDao(),
                AppExecutors.getInstance(),
                WebSocketManager.getInstance()
        );
        repo.setCurrentUser(
                currentUserId,
                UserHelper.getNickname(this),
                UserHelper.getAvatar(this)
        );

        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        setupViewPagerAndNav();
        binding.bottomNav.setItemActiveIndicatorEnabled(false);
        observeAppearance();

        // 序列化入场 (Staging)
        binding.bottomNav.setTranslationY(dp(40));
        binding.bottomNav.setAlpha(0f);
        binding.bottomNav.postDelayed(() -> {
            binding.bottomNav.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(350)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.2f))
                    .start();
        }, 150);
    }

    private void observeAppearance() {
        settingsRepository.getSettingsLiveData().observe(this, state -> {
            currentAppearanceState = state;
            AppearanceManager.applyPageAppearance(this, getWindow(), binding.main, state);
            boolean stardust = state != null && "stardust".equals(state.chatBackground);
            binding.bottomNav.setBackgroundResource(stardust
                    ? R.drawable.bg_butterfly_bottom_nav_stardust
                    : R.drawable.bg_butterfly_bottom_nav);
            binding.navAmbientGlow.setBackgroundResource(stardust
                    ? R.drawable.bg_bottom_nav_stardust_pulse
                    : R.drawable.bg_bottom_nav_ambient_glow);
            updateBottomNavAmbientEffects(stardust && state.immersiveEffectsEnabled);
        });
    }

    private void setupViewPagerAndNav() {
        MainPagerAdapter pagerAdapter = new MainPagerAdapter(this);
        binding.viewPager.setAdapter(pagerAdapter);
        binding.viewPager.setOffscreenPageLimit(3);
        binding.viewPager.setPageTransformer(new ZoomFadePageTransformer());

        binding.bottomNav.setOnItemSelectedListener(item -> {
            binding.bottomNav.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            int id = item.getItemId();
            if (id == R.id.nav_conversations) {
                binding.viewPager.setCurrentItem(0, true);
                animateNavSelection(id, true);
                return true;
            } else if (id == R.id.nav_contacts) {
                binding.viewPager.setCurrentItem(1, true);
                animateNavSelection(id, true);
                return true;
            } else if (id == R.id.nav_settings) {
                binding.viewPager.setCurrentItem(2, true);
                animateNavSelection(id, true);
                return true;
            }
            return false;
        });

        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                switch (position) {
                    case 0:
                        binding.bottomNav.setSelectedItemId(R.id.nav_conversations);
                        animateNavSelection(R.id.nav_conversations, false);
                        break;
                    case 1:
                        binding.bottomNav.setSelectedItemId(R.id.nav_contacts);
                        animateNavSelection(R.id.nav_contacts, false);
                        break;
                    case 2:
                        binding.bottomNav.setSelectedItemId(R.id.nav_settings);
                        animateNavSelection(R.id.nav_settings, false);
                        break;
                    default:
                        break;
                }
            }
        });

        binding.bottomNav.post(() -> {
            selectedNavItemId = binding.bottomNav.getSelectedItemId();
            applyIdleNavState(selectedNavItemId);
        });

        BadgeDrawable badge = binding.bottomNav.getOrCreateBadge(R.id.nav_conversations);
        badge.setBackgroundColor(ContextCompat.getColor(this, R.color.butterfly_cyan));
        badge.setBadgeTextColor(ContextCompat.getColor(this, R.color.butterfly_bg));
        if (currentUserId != null && !currentUserId.isEmpty()) {
            convDao.getTotalUnreadCount(currentUserId).observe(this, count -> {
                if (count != null && count > 0) {
                    badge.setVisible(true);
                    badge.setNumber(count);
                } else {
                    badge.setVisible(false);
                    badge.clearNumber();
                }
            });
        }
    }

    private static class ZoomFadePageTransformer implements ViewPager2.PageTransformer {
        private static final float MIN_SCALE = 0.90f;
        private static final float MIN_ALPHA = 0.42f;

        public void transformPage(View view, float position) {
            if (position < -1) {
                view.setAlpha(0f);
            } else if (position <= 1) {
                float scaleFactor = Math.max(MIN_SCALE, 1 - Math.abs(position));
                view.setScaleX(scaleFactor);
                view.setScaleY(scaleFactor);
                view.setTranslationX(-position * view.getWidth() * 0.08f);
                view.setRotation(position * -1.4f);
                view.setAlpha(MIN_ALPHA + (scaleFactor - MIN_SCALE) / (1 - MIN_SCALE) * (1 - MIN_ALPHA));
            } else {
                view.setAlpha(0f);
            }
        }
    }

    private void animateNavSelection(int itemId, boolean fromTap) {
        View itemView = binding.bottomNav.findViewById(itemId);
        View previousItemView = binding.bottomNav.findViewById(selectedNavItemId);
        if (previousItemView != null && selectedNavItemId != itemId) {
            previousItemView.animate().cancel();
            previousItemView.animate()
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .rotation(0f)
                    .alpha(0.72f)
                    .setDuration(220)
                    .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                    .start();
        }

        if (itemView == null) {
            selectedNavItemId = itemId;
            return;
        }

        itemView.animate().cancel();
        itemView.setPivotY(itemView.getHeight() * 0.5f);

        float lift = 0f;
        float settleScale = 1.04f;
        float popScale = fromTap ? 1.12f : 1.07f;
        float twist = fromTap ? 3.2f : 1.8f;

        ObjectAnimator liftAnim;
        ObjectAnimator scaleX;
        ObjectAnimator scaleY;
        
        if (fromTap) {
            // Anticipation & Follow-through
            liftAnim = ObjectAnimator.ofFloat(itemView, View.TRANSLATION_Y, itemView.getTranslationY(), dp(2f), -dp(1.5f), lift);
            scaleX = ObjectAnimator.ofFloat(itemView, View.SCALE_X, itemView.getScaleX(), 0.94f, popScale, settleScale);
            scaleY = ObjectAnimator.ofFloat(itemView, View.SCALE_Y, itemView.getScaleY(), 0.94f, popScale, settleScale);
        } else {
            liftAnim = ObjectAnimator.ofFloat(itemView, View.TRANSLATION_Y, itemView.getTranslationY(), lift);
            scaleX = ObjectAnimator.ofFloat(itemView, View.SCALE_X, itemView.getScaleX(), popScale, settleScale);
            scaleY = ObjectAnimator.ofFloat(itemView, View.SCALE_Y, itemView.getScaleY(), popScale, settleScale);
        }

        liftAnim.setDuration(fromTap ? 340 : 180);
        liftAnim.setInterpolator(new android.view.animation.DecelerateInterpolator());

        scaleX.setDuration(fromTap ? 340 : 240);
        scaleX.setInterpolator(new android.view.animation.OvershootInterpolator(1.8f));

        scaleY.setDuration(fromTap ? 340 : 240);
        scaleY.setInterpolator(new android.view.animation.OvershootInterpolator(1.8f));

        ObjectAnimator rotate = ObjectAnimator.ofFloat(itemView, View.ROTATION, 0f, twist, -twist * 0.45f, 0f);
        rotate.setDuration(fromTap ? 360 : 260);
        rotate.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());

        AnimatorSet set = new AnimatorSet();
        set.playTogether(liftAnim, scaleX, scaleY, rotate);
        set.start();

        itemView.setAlpha(1f);
        playNavSelectionBurst(itemView, fromTap);

        selectedNavItemId = itemId;
    }

    private void applyIdleNavState(int selectedItemId) {
        int[] itemIds = {
                R.id.nav_conversations,
                R.id.nav_contacts,
                R.id.nav_settings
        };
        for (int itemId : itemIds) {
            View itemView = binding.bottomNav.findViewById(itemId);
            if (itemView == null) {
                continue;
            }
            boolean selected = itemId == selectedItemId;
            itemView.animate().cancel();
            itemView.setTranslationY(0f);
            itemView.setScaleX(selected ? 1.04f : 1f);
            itemView.setScaleY(selected ? 1.04f : 1f);
            itemView.setRotation(0f);
            itemView.setAlpha(selected ? 1f : 0.72f);
        }
    }

    private void updateBottomNavAmbientEffects(boolean enabled) {
        if (navAmbientAnimator != null) {
            navAmbientAnimator.cancel();
            navAmbientAnimator = null;
        }
        binding.navAmbientGlow.setVisibility(View.GONE);
    }

    private void playNavSelectionBurst(View itemView, boolean fromTap) {
        if (currentAppearanceState == null
                || !currentAppearanceState.immersiveEffectsEnabled
                || !"stardust".equals(currentAppearanceState.chatBackground)
                || itemView == null) {
            binding.navAmbientGlow.animate().cancel();
            binding.navAmbientGlow.setVisibility(View.GONE);
            return;
        }

        binding.navAmbientGlow.animate().cancel();
        binding.navAmbientGlow.post(() -> {
            float centerX = resolveCenterXInMain(itemView);
            float centerY = resolveCenterYInMain(itemView) + dp(2f);
            float glowX = centerX - binding.navAmbientGlow.getWidth() * 0.5f;
            float glowY = centerY - binding.navAmbientGlow.getHeight() * 0.5f;

            binding.navAmbientGlow.setX(glowX);
            binding.navAmbientGlow.setY(glowY);
            binding.navAmbientGlow.setPivotX(binding.navAmbientGlow.getWidth() * 0.5f);
            binding.navAmbientGlow.setPivotY(binding.navAmbientGlow.getHeight() * 0.5f);
            binding.navAmbientGlow.setVisibility(View.VISIBLE);
            binding.navAmbientGlow.setScaleX(fromTap ? 0.72f : 0.84f);
            binding.navAmbientGlow.setScaleY(fromTap ? 0.54f : 0.62f);
            binding.navAmbientGlow.setAlpha(0f);
            binding.navAmbientGlow.animate()
                    .alpha(fromTap ? 0.96f : 0.8f)
                    .scaleX(1f)
                    .scaleY(0.96f)
                    .setDuration(fromTap ? 180 : 140)
                    .withEndAction(() -> binding.navAmbientGlow.animate()
                            .alpha(0f)
                            .scaleX(1.28f)
                            .scaleY(1.08f)
                            .setDuration(360)
                            .withEndAction(() -> binding.navAmbientGlow.setVisibility(View.GONE))
                            .start())
                    .start();
        });
    }

    private float resolveCenterXInMain(View target) {
        int[] parentLocation = new int[2];
        int[] targetLocation = new int[2];
        binding.main.getLocationOnScreen(parentLocation);
        target.getLocationOnScreen(targetLocation);
        return (targetLocation[0] - parentLocation[0]) + target.getWidth() * 0.5f;
    }

    private float resolveCenterYInMain(View target) {
        int[] parentLocation = new int[2];
        int[] targetLocation = new int[2];
        binding.main.getLocationOnScreen(parentLocation);
        target.getLocationOnScreen(targetLocation);
        return (targetLocation[1] - parentLocation[1]) + target.getHeight() * 0.5f;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
