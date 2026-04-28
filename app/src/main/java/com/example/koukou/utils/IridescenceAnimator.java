package com.example.koukou.utils;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.example.koukou.R;

import java.util.ArrayList;
import java.util.List;

public final class IridescenceAnimator {
    private IridescenceAnimator() {
    }

    public static void startHaloPulse(View view) {
        if (view == null) {
            return;
        }
        cancelRegisteredAnimators(view);
        AnimatorSet set = new AnimatorSet();
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 0.96f, 1.08f, 0.98f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.96f, 1.08f, 0.98f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(view, View.ALPHA, view.getAlpha(), view.getAlpha() + 0.14f, view.getAlpha());
        set.playTogether(scaleX, scaleY, alpha);
        set.setDuration(5200);
        set.setInterpolator(new AccelerateDecelerateInterpolator());
        set.start();
        repeatInfinitely(set);
        registerAnimator(view, set);
    }

    public static void startSheenDrift(View view, float fromX, float toX, float fromY, float toY, float fromAlpha, float toAlpha) {
        if (view == null) {
            return;
        }
        cancelRegisteredAnimators(view);
        view.setTranslationX(fromX);
        view.setTranslationY(fromY);
        view.setAlpha(fromAlpha);

        AnimatorSet set = new AnimatorSet();
        ObjectAnimator moveX = ObjectAnimator.ofFloat(view, View.TRANSLATION_X, fromX, toX, fromX);
        ObjectAnimator moveY = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, fromY, toY, fromY);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(view, View.ALPHA, fromAlpha, toAlpha, fromAlpha);
        set.playTogether(moveX, moveY, alpha);
        set.setDuration(6200);
        set.setInterpolator(new AccelerateDecelerateInterpolator());
        set.start();
        repeatInfinitely(set);
        registerAnimator(view, set);
    }

    public static void startHeroFloat(View view) {
        if (view == null) {
            return;
        }
        cancelRegisteredAnimators(view);
        AnimatorSet set = new AnimatorSet();
        ObjectAnimator moveY = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 0f, -18f, 0f);
        ObjectAnimator moveX = ObjectAnimator.ofFloat(view, View.TRANSLATION_X, 0f, -8f, 0f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(view, View.ALPHA, view.getAlpha(), Math.min(0.56f, view.getAlpha() + 0.12f), view.getAlpha());
        set.playTogether(moveY, moveX, alpha);
        set.setDuration(5400);
        set.setInterpolator(new AccelerateDecelerateInterpolator());
        set.start();
        repeatInfinitely(set);
        registerAnimator(view, set);
    }

    public static void startButtonGlow(View view) {
        if (view == null) {
            return;
        }
        cancelRegisteredAnimators(view);
        AnimatorSet set = new AnimatorSet();
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.018f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 1.018f, 1f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(view, View.ALPHA, 0.96f, 1f, 0.96f);
        set.playTogether(scaleX, scaleY, alpha);
        set.setDuration(2600);
        set.setInterpolator(new AccelerateDecelerateInterpolator());
        set.start();
        repeatInfinitely(set);
        registerAnimator(view, set);
    }

    public static void startDreamscape(View root) {
        if (!(root instanceof ViewGroup)) {
            return;
        }
        List<View> particles = new ArrayList<>();
        List<View> sprayParticles = new ArrayList<>();
        List<View> glows = new ArrayList<>();
        List<View> trails = new ArrayList<>();
        List<View> beams = new ArrayList<>();
        List<View> chromaCyan = new ArrayList<>();
        List<View> chromaPearl = new ArrayList<>();
        List<View> coreGlows = new ArrayList<>();
        collectTaggedViews((ViewGroup) root, "particle", particles);
        collectTaggedViews((ViewGroup) root, "spray_particle", sprayParticles);
        collectTaggedViews((ViewGroup) root, "butterfly_glow", glows);
        collectTaggedViews((ViewGroup) root, "trail_particle", trails);
        collectTaggedViews((ViewGroup) root, "light_beam", beams);
        collectTaggedViews((ViewGroup) root, "chroma_cyan", chromaCyan);
        collectTaggedViews((ViewGroup) root, "chroma_pearl", chromaPearl);
        collectTaggedViews((ViewGroup) root, "core_glow", coreGlows);

        for (int i = 0; i < particles.size(); i++) {
            startParticleFloat(particles.get(i), i);
            startTwinkle(particles.get(i), i);
        }
        for (int i = 0; i < sprayParticles.size(); i++) {
            startSprayParticleFlow(sprayParticles.get(i), i);
            startTwinkle(sprayParticles.get(i), i + 2);
        }
        for (int i = 0; i < trails.size(); i++) {
            startTrailFloat(trails.get(i), i);
            startTrailGlow(trails.get(i), i);
        }
        for (int i = 0; i < glows.size(); i++) {
            startSoftGlow(glows.get(i), i);
        }
        for (int i = 0; i < beams.size(); i++) {
            startBeamFlow(beams.get(i), i);
        }
        for (int i = 0; i < chromaCyan.size(); i++) {
            startChromaticShift(chromaCyan.get(i), i, -1f);
        }
        for (int i = 0; i < chromaPearl.size(); i++) {
            startChromaticShift(chromaPearl.get(i), i, 1f);
        }
        for (int i = 0; i < coreGlows.size(); i++) {
            startCorePulse(coreGlows.get(i), i);
        }
    }

    public static void stopEffects(View root) {
        if (root == null) {
            return;
        }
        cancelRegisteredAnimators(root);
        root.animate().cancel();
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                stopEffects(group.getChildAt(i));
            }
        }
    }

    public static void startPanelBounceIn(View panel) {
        if (panel == null) return;
        panel.setAlpha(0f);
        panel.setScaleX(0.7f);
        panel.setScaleY(0.7f);
        panel.setTranslationY(80f);

        // 流体空间与弹簧物理定律 (Fluid & Spring Dynamics)
        SpringAnimation springY = new SpringAnimation(panel, DynamicAnimation.TRANSLATION_Y, 0f);
        springY.getSpring().setStiffness(SpringForce.STIFFNESS_LOW);
        springY.getSpring().setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY);
        springY.start();

        SpringAnimation springScaleX = new SpringAnimation(panel, DynamicAnimation.SCALE_X, 1f);
        springScaleX.getSpring().setStiffness(SpringForce.STIFFNESS_LOW);
        springScaleX.getSpring().setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY);
        springScaleX.start();

        SpringAnimation springScaleY = new SpringAnimation(panel, DynamicAnimation.SCALE_Y, 1f);
        springScaleY.getSpring().setStiffness(SpringForce.STIFFNESS_LOW);
        springScaleY.getSpring().setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY);
        springScaleY.start();

        panel.animate()
                .alpha(1f)
                .setDuration(240)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.2f))
                .start();
    }

    public static void setupClickFeedback(View view) {
        if (view == null) return;
        
        // 三维通感反馈系统 (Sensory Resonance)
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    // 触点微缩与引力坍缩 (Anticipation)
                    SpringAnimation pressScaleX = new SpringAnimation(v, DynamicAnimation.SCALE_X, 0.95f);
                    pressScaleX.getSpring().setStiffness(SpringForce.STIFFNESS_HIGH).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY);
                    pressScaleX.start();
                    SpringAnimation pressScaleY = new SpringAnimation(v, DynamicAnimation.SCALE_Y, 0.95f);
                    pressScaleY.getSpring().setStiffness(SpringForce.STIFFNESS_HIGH).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY);
                    pressScaleY.start();
                    
                    // 锐利的线性马达 Tick
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                    }
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    // 粒子激荡与释放波纹 (Follow-through)
                    SpringAnimation releaseScaleX = new SpringAnimation(v, DynamicAnimation.SCALE_X, 1f);
                    releaseScaleX.getSpring().setStiffness(SpringForce.STIFFNESS_LOW).setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY);
                    releaseScaleX.start();
                    SpringAnimation releaseScaleY = new SpringAnimation(v, DynamicAnimation.SCALE_Y, 1f);
                    releaseScaleY.getSpring().setStiffness(SpringForce.STIFFNESS_LOW).setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY);
                    releaseScaleY.start();
                    
                    // 松手伴随 Thud 震感
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.REJECT);
                    }
                    break;
            }
            return false;
        });
    }

    public static void startInputEdgeTrace(View inputBar) {
        if (inputBar == null) return;
        // 数据呼吸与状态重塑：输入框脉冲边带
        ObjectAnimator rotate = ObjectAnimator.ofFloat(inputBar, View.ROTATION, 0f, 0.8f, -0.8f, 0f);
        rotate.setDuration(400);
        rotate.setInterpolator(new android.view.animation.OvershootInterpolator(2.5f));
        rotate.start();
        
        ObjectAnimator glow = ObjectAnimator.ofFloat(inputBar, "elevation", 0f, 15f, 0f);
        glow.setDuration(800);
        glow.start();
    }

    public static void triggerRefreshGlitch(View background) {
        if (background == null) return;
        
        // 信息流加载 (Glitch & Matrix Sort) - 高强度但控制在底层的 RGB Glitch 色散剥离特效
        AnimatorSet set = new AnimatorSet();
        ObjectAnimator transX1 = ObjectAnimator.ofFloat(background, View.TRANSLATION_X, 0f, 25f, -20f, 15f, -10f, 8f, -4f, 0f);
        transX1.setDuration(300);
        transX1.setInterpolator(new android.view.animation.LinearInterpolator());
        
        ObjectAnimator alpha1 = ObjectAnimator.ofFloat(background, View.ALPHA, background.getAlpha(), 0.1f, 0.9f, 0.2f, 0.8f, 1f);
        alpha1.setDuration(300);
        
        set.playTogether(transX1, alpha1);
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            // 实时高斯模糊景深体系
            background.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(15f, 15f, android.graphics.Shader.TileMode.CLAMP));
            background.postDelayed(() -> background.setRenderEffect(null), 300);
        }
        
        set.start();
    }
    
    private static float dpToPx(View v, float dp) {
        return dp * v.getResources().getDisplayMetrics().density;
    }

    private static void startParticleFloat(View view, int index) {
        float offsetX = 16f + (index % 3) * 6f;
        float offsetY = 14f + (index % 4) * 4f;
        AnimatorSet set = new AnimatorSet();
        ObjectAnimator moveX = ObjectAnimator.ofFloat(view, View.TRANSLATION_X, 0f, -offsetX, offsetX * 0.24f, 0f);
        ObjectAnimator moveY = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 0f, offsetY, -offsetY * 0.35f, 0f);
        set.playTogether(moveX, moveY);
        set.setDuration(4200L + index * 380L);
        set.setInterpolator(new AccelerateDecelerateInterpolator());
        set.start();
        repeatInfinitely(set);
        registerAnimator(view, set);
    }

    private static void startSprayParticleFlow(View view, int index) {
        float offsetX = 28f + index * 10f;
        float offsetY = 20f + index * 8f;
        AnimatorSet set = new AnimatorSet();
        ObjectAnimator moveX = ObjectAnimator.ofFloat(view, View.TRANSLATION_X, 0f, -offsetX, -offsetX * 1.18f, 0f);
        ObjectAnimator moveY = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 0f, -offsetY * 0.25f, offsetY, 0f);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 0.9f, 1.14f, 0.92f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.9f, 1.14f, 0.92f);
        set.playTogether(moveX, moveY, scaleX, scaleY);
        set.setDuration(3800L + index * 320L);
        set.setInterpolator(new AccelerateDecelerateInterpolator());
        set.start();
        repeatInfinitely(set);
        registerAnimator(view, set);
    }

    private static void startTwinkle(View view, int index) {
        float base = Math.max(0.16f, view.getAlpha());
        float peak = Math.min(0.72f, base + 0.22f + (index % 2) * 0.08f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(view, View.ALPHA, base, peak, base * 0.9f, base);
        alpha.setDuration(2600L + index * 180L);
        alpha.setInterpolator(new LinearInterpolator());
        alpha.setRepeatCount(ObjectAnimator.INFINITE);
        alpha.setRepeatMode(ObjectAnimator.RESTART);
        alpha.start();
        registerAnimator(view, alpha);
    }

    private static void startSoftGlow(View view, int index) {
        float base = Math.max(0.16f, view.getAlpha());
        float peak = Math.min(0.95f, base + 0.12f);
        AnimatorSet set = new AnimatorSet();
        ObjectAnimator alpha = ObjectAnimator.ofFloat(view, View.ALPHA, base, peak, base);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.05f + (index % 2) * 0.03f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 1.05f + (index % 2) * 0.03f, 1f);
        set.playTogether(alpha, scaleX, scaleY);
        set.setDuration(3600L + index * 260L);
        set.setInterpolator(new AccelerateDecelerateInterpolator());
        set.start();
        repeatInfinitely(set);
        registerAnimator(view, set);
    }

    private static void startTrailFloat(View view, int index) {
        AnimatorSet set = new AnimatorSet();
        ObjectAnimator moveY = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 0f, -10f - index * 3f, 12f + index * 6f, 0f);
        ObjectAnimator moveX = ObjectAnimator.ofFloat(view, View.TRANSLATION_X, 0f, -20f - index * 10f, -42f - index * 16f, 0f);
        set.playTogether(moveX, moveY);
        set.setDuration(4600L + index * 320L);
        set.setInterpolator(new AccelerateDecelerateInterpolator());
        set.start();
        repeatInfinitely(set);
        registerAnimator(view, set);
    }

    private static void startTrailGlow(View view, int index) {
        float base = Math.max(0.18f, view.getAlpha());
        ObjectAnimator alpha = ObjectAnimator.ofFloat(view, View.ALPHA, base, Math.min(0.62f, base + 0.16f), base);
        alpha.setDuration(2800L + index * 180L);
        alpha.setInterpolator(new LinearInterpolator());
        alpha.setRepeatCount(ObjectAnimator.INFINITE);
        alpha.setRepeatMode(ObjectAnimator.RESTART);
        alpha.start();
        registerAnimator(view, alpha);
    }

    private static void startBeamFlow(View view, int index) {
        AnimatorSet set = new AnimatorSet();
        ObjectAnimator alpha = ObjectAnimator.ofFloat(view, View.ALPHA, 0.34f, 0.68f, 0.34f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.94f, 1.12f, 0.94f);
        ObjectAnimator translationY = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 0f, -18f, 12f, 0f);
        set.playTogether(alpha, scaleY, translationY);
        set.setDuration(4200L + index * 220L);
        set.setInterpolator(new AccelerateDecelerateInterpolator());
        set.start();
        repeatInfinitely(set);
        registerAnimator(view, set);
    }

    private static void startChromaticShift(View view, int index, float direction) {
        AnimatorSet set = new AnimatorSet();
        float shiftX = (3.5f + index % 2) * direction;
        float shiftY = (2f + (index % 3) * 0.6f) * direction;
        ObjectAnimator moveX = ObjectAnimator.ofFloat(view, View.TRANSLATION_X, 0f, shiftX, -shiftX * 0.45f, 0f);
        ObjectAnimator moveY = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 0f, -shiftY, shiftY * 0.4f, 0f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(view, View.ALPHA, view.getAlpha(), Math.min(1f, view.getAlpha() + 0.16f), view.getAlpha());
        set.playTogether(moveX, moveY, alpha);
        set.setDuration(2800L + index * 180L);
        set.setInterpolator(new AccelerateDecelerateInterpolator());
        set.start();
        repeatInfinitely(set);
        registerAnimator(view, set);
    }

    private static void startCorePulse(View view, int index) {
        AnimatorSet set = new AnimatorSet();
        ObjectAnimator alpha = ObjectAnimator.ofFloat(view, View.ALPHA, view.getAlpha(), Math.min(1f, view.getAlpha() + 0.12f), view.getAlpha());
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 0.96f, 1.18f, 0.98f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.96f, 1.18f, 0.98f);
        set.playTogether(alpha, scaleX, scaleY);
        set.setDuration(2400L + index * 140L);
        set.setInterpolator(new AccelerateDecelerateInterpolator());
        set.start();
        repeatInfinitely(set);
        registerAnimator(view, set);
    }

    private static void collectTaggedViews(ViewGroup parent, String tag, List<View> out) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            Object childTag = child.getTag();
            if (tag.equals(childTag)) {
                out.add(child);
            }
            if (child instanceof ViewGroup) {
                collectTaggedViews((ViewGroup) child, tag, out);
            }
        }
    }

    private static void repeatInfinitely(AnimatorSet set) {
        for (android.animation.Animator animator : set.getChildAnimations()) {
            if (animator instanceof ObjectAnimator) {
                ((ObjectAnimator) animator).setRepeatCount(ObjectAnimator.INFINITE);
                ((ObjectAnimator) animator).setRepeatMode(ObjectAnimator.RESTART);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void cancelRegisteredAnimators(View view) {
        Object tag = view.getTag(R.id.tag_iridescence_animators);
        if (!(tag instanceof List)) {
            return;
        }
        List<android.animation.Animator> animators = (List<android.animation.Animator>) tag;
        for (android.animation.Animator animator : animators) {
            if (animator != null) {
                animator.cancel();
            }
        }
        animators.clear();
    }

    @SuppressWarnings("unchecked")
    private static void registerAnimator(View view, android.animation.Animator animator) {
        if (view == null || animator == null) {
            return;
        }
        Object tag = view.getTag(R.id.tag_iridescence_animators);
        List<android.animation.Animator> animators;
        if (tag instanceof List) {
            animators = (List<android.animation.Animator>) tag;
        } else {
            animators = new ArrayList<>();
            view.setTag(R.id.tag_iridescence_animators, animators);
        }
        animators.add(animator);
    }
}
