package com.example.koukou.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CodeRainView extends View {
    private static final char[] GLYPHS = "01ABCDEF0123456789数据矩阵流光0101".toCharArray();

    private static final int LAYER_FAR = 0;
    private static final int LAYER_MID = 1;
    private static final int LAYER_NEAR = 2;

    private final Paint glyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint headPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint groundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ripplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scanlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final List<Drop> drops = new ArrayList<>();
    private final List<ImpactParticle> particles = new ArrayList<>();
    private final List<GroundRipple> ripples = new ArrayList<>();

    private boolean rainEnabled = false;
    private boolean lightPalette = false;
    private long lastFrameTime = 0L;
    private float spawnFarAccumulator = 0f;
    private float spawnMidAccumulator = 0f;
    private float spawnNearAccumulator = 0f;
    private float density;

    public CodeRainView(Context context) {
        super(context);
        init();
    }

    public CodeRainView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CodeRainView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        glyphPaint.setTypeface(Typeface.MONOSPACE);
        glyphPaint.setTextAlign(Paint.Align.LEFT);
        glyphPaint.setStyle(Paint.Style.FILL);
        headPaint.setTypeface(Typeface.MONOSPACE);
        headPaint.setTextAlign(Paint.Align.LEFT);
        headPaint.setStyle(Paint.Style.FILL);
        glowPaint.setStyle(Paint.Style.FILL);
        glowPaint.setTypeface(Typeface.MONOSPACE);
        glowPaint.setTextAlign(Paint.Align.LEFT);
        glowPaint.setAntiAlias(true);
        particlePaint.setStyle(Paint.Style.FILL);
        groundPaint.setStyle(Paint.Style.STROKE);
        groundPaint.setStrokeCap(Paint.Cap.ROUND);
        ripplePaint.setStyle(Paint.Style.STROKE);
        ripplePaint.setStrokeCap(Paint.Cap.ROUND);
        scanlinePaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStyle(Paint.Style.STROKE);
        setWillNotDraw(false);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        setAlpha(0.9f);
        applyPalette();
    }

    public void setRainEnabled(boolean enabled) {
        if (rainEnabled == enabled) {
            return;
        }
        rainEnabled = enabled;
        if (!enabled) {
            drops.clear();
            particles.clear();
            ripples.clear();
            invalidate();
            return;
        }
        lastFrameTime = 0L;
        spawnFarAccumulator = 0f;
        spawnMidAccumulator = 0f;
        spawnNearAccumulator = 0f;
        invalidate();
    }

    public void setLightPalette(boolean lightPalette) {
        if (this.lightPalette == lightPalette) {
            return;
        }
        this.lightPalette = lightPalette;
        applyPalette();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!rainEnabled || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }

        long now = System.nanoTime();
        float dt = lastFrameTime == 0L ? 0.016f : Math.min((now - lastFrameTime) / 1_000_000_000f, 0.033f);
        lastFrameTime = now;

        updateSimulation(dt);
        drawGroundPlane(canvas);
        drawDropsForLayer(canvas, LAYER_FAR);
        drawDropsForLayer(canvas, LAYER_MID);
        drawDropsForLayer(canvas, LAYER_NEAR);
        drawGroundRipples(canvas);
        drawImpactParticles(canvas);
        drawScanlines(canvas);
        postInvalidateOnAnimation();
    }

    private void updateSimulation(float dt) {
        spawnFarAccumulator += dt;
        spawnMidAccumulator += dt;
        spawnNearAccumulator += dt;

        int farTarget = Math.max(18, (int) (getWidth() / dp(22f)));
        int midTarget = Math.max(22, (int) (getWidth() / dp(20f)));
        int nearTarget = Math.max(10, (int) (getWidth() / dp(42f)));

        spawnDropsForLayer(LAYER_FAR, farTarget, 0.10f, spawnFarAccumulator);
        spawnFarAccumulator = consumeAccumulator(LAYER_FAR, spawnFarAccumulator);
        spawnDropsForLayer(LAYER_MID, midTarget, 0.06f, spawnMidAccumulator);
        spawnMidAccumulator = consumeAccumulator(LAYER_MID, spawnMidAccumulator);
        spawnDropsForLayer(LAYER_NEAR, nearTarget, 0.085f, spawnNearAccumulator);
        spawnNearAccumulator = consumeAccumulator(LAYER_NEAR, spawnNearAccumulator);

        float impactY = getHeight() - dp(30f);
        for (int i = drops.size() - 1; i >= 0; i--) {
            Drop drop = drops.get(i);
            drop.phase += dt * drop.phaseSpeed;
            drop.y += drop.speed * dt;
            drop.x += (float) Math.sin(drop.phase) * drop.driftAmplitude * dt;
            if (drop.y >= impactY) {
                spawnImpact(drop.x, impactY, drop.layer);
                drops.remove(i);
            }
        }

        for (int i = particles.size() - 1; i >= 0; i--) {
            ImpactParticle particle = particles.get(i);
            particle.x += particle.vx * dt;
            particle.y += particle.vy * dt;
            particle.vy += dp(36f) * dt;
            particle.alpha -= dt * particle.fadeSpeed;
            if (particle.alpha <= 0f) {
                particles.remove(i);
            }
        }

        for (int i = ripples.size() - 1; i >= 0; i--) {
            GroundRipple ripple = ripples.get(i);
            ripple.radius += ripple.speed * dt;
            ripple.alpha -= dt * ripple.fadeSpeed;
            ripple.thickness = Math.max(dp(0.6f), ripple.thickness - dt * dp(0.8f));
            if (ripple.alpha <= 0f) {
                ripples.remove(i);
            }
        }
    }

    private void spawnDropsForLayer(int layer, int targetCount, float interval, float accumulator) {
        int current = countDrops(layer);
        while (accumulator >= interval && current < targetCount) {
            drops.add(createDrop(layer));
            accumulator -= interval;
            current++;
        }
    }

    private float consumeAccumulator(int layer, float accumulator) {
        float interval = layer == LAYER_FAR ? 0.10f : (layer == LAYER_MID ? 0.06f : 0.085f);
        while (accumulator >= interval) {
            accumulator -= interval;
        }
        return accumulator;
    }

    private int countDrops(int layer) {
        int count = 0;
        for (Drop drop : drops) {
            if (drop.layer == layer) {
                count++;
            }
        }
        return count;
    }

    private void drawGroundPlane(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float groundY = h - dp(24f);
        float vanishX = w * 0.5f;
        float horizonY = h - dp(64f);

        groundPaint.setStrokeWidth(dp(0.85f));
        groundPaint.setColor(withAlpha(lightPalette ? "#85CFC9" : "#66FFE0", lightPalette ? 80 : 96));
        canvas.drawLine(0f, groundY, w, groundY, groundPaint);

        gridPaint.setStrokeWidth(dp(0.5f));
        for (int i = 0; i < 8; i++) {
            float progress = i / 7f;
            float startX = w * (-0.08f + progress * 1.16f);
            gridPaint.setColor(withAlpha(lightPalette ? "#74B7AF" : "#4AE1CA", i % 2 == 0 ? 44 : 26));
            canvas.drawLine(startX, groundY, vanishX, horizonY, gridPaint);
        }
        for (int i = 0; i < 4; i++) {
            float p = i / 3f;
            float y = lerp(groundY, horizonY, p * p);
            float leftX = lerp(-w * 0.06f, vanishX, p * p);
            float rightX = lerp(w * 1.06f, vanishX, p * p);
            gridPaint.setColor(withAlpha(lightPalette ? "#78BDB5" : "#56E4D0", i == 0 ? 42 : 22));
            canvas.drawLine(leftX, y, rightX, y, gridPaint);
        }
    }

    private void drawDropsForLayer(Canvas canvas, int targetLayer) {
        for (Drop drop : drops) {
            if (drop.layer != targetLayer) {
                continue;
            }
            glyphPaint.setTextSize(drop.textSize);
            headPaint.setTextSize(drop.textSize);
            glowPaint.setTextSize(drop.textSize);

            if (drop.layer == LAYER_NEAR) {
                glowPaint.setShadowLayer(dp(10f), 0f, 0f, withAlpha(lightPalette ? "#9AFFF4" : "#62FFD7", 110));
            } else if (drop.layer == LAYER_MID) {
                glowPaint.setShadowLayer(dp(6f), 0f, 0f, withAlpha(lightPalette ? "#AAFFF8" : "#53FFCE", 90));
            } else {
                glowPaint.setShadowLayer(dp(3f), 0f, 0f, withAlpha(lightPalette ? "#9BFFF8" : "#45F9C7", 44));
            }

            float y = drop.y;
            for (int i = 0; i < drop.trailLength; i++) {
                float charY = y - i * drop.charStep;
                if (charY < -drop.charStep || charY > getHeight() + drop.charStep) {
                    continue;
                }
                char glyph = drop.glyphs[i % drop.glyphs.length];
                if (i == 0) {
                    int glowColor = withAlpha(lightPalette ? "#F7FFFF" : "#B8FFF2", drop.layer == LAYER_NEAR ? 156 : 126);
                    glowPaint.setColor(glowColor);
                    canvas.drawText(String.valueOf(glyph), drop.x, charY, glowPaint);

                    int headColor = Color.parseColor(lightPalette ? "#FFFFFF" : "#F2FFF8");
                    headPaint.setColor(headColor);
                    headPaint.setShadowLayer(dp(drop.layer == LAYER_NEAR ? 10f : 7f), 0f, 0f,
                            withAlpha(lightPalette ? "#D1FFF8" : "#61FFD3", drop.layer == LAYER_NEAR ? 166 : 132));
                    canvas.drawText(String.valueOf(glyph), drop.x, charY, headPaint);
                    headPaint.clearShadowLayer();
                } else {
                    double decay = Math.exp(-i * (drop.layer == LAYER_FAR ? 0.62 : drop.layer == LAYER_MID ? 0.48 : 0.36));
                    int alpha = (int) (drop.baseAlpha * decay);
                    glyphPaint.setColor(withAlpha(drop.layer == LAYER_FAR
                            ? (lightPalette ? "#B4FFF8" : "#4CF4C5")
                            : (drop.layer == LAYER_MID ? (lightPalette ? "#B9FFF9" : "#66FFD2") : (lightPalette ? "#D4FFFB" : "#8FFFE2")), alpha));
                    glyphPaint.setShadowLayer(dp(drop.layer == LAYER_NEAR ? 6f : drop.layer == LAYER_MID ? 4f : 1.5f), 0f, 0f,
                            withAlpha(lightPalette ? "#BAFFF8" : "#34F1BE", Math.min(130, alpha)));
                    canvas.drawText(String.valueOf(glyph), drop.x, charY, glyphPaint);
                    glyphPaint.clearShadowLayer();
                }
            }
        }
    }

    private void drawImpactParticles(Canvas canvas) {
        for (ImpactParticle particle : particles) {
            int alpha = (int) (255 * particle.alpha);
            particlePaint.setColor(withAlpha(particle.color, alpha));
            canvas.drawRect(
                    particle.x - particle.size * 0.5f,
                    particle.y - particle.size * 0.5f,
                    particle.x + particle.size * 0.5f,
                    particle.y + particle.size * 0.5f,
                    particlePaint
            );
        }
    }

    private void drawGroundRipples(Canvas canvas) {
        for (GroundRipple ripple : ripples) {
            int outerAlpha = (int) (255 * ripple.alpha);
            float width = ripple.radius * ripple.aspectX;
            float height = ripple.radius * ripple.aspectY;

            ripplePaint.setStrokeWidth(ripple.thickness);
            ripplePaint.setColor(withAlpha(lightPalette ? "#CFFFFC" : "#8EFFE7", Math.min(180, outerAlpha)));
            ripplePaint.setShadowLayer(dp(10f), 0f, 0f,
                    withAlpha(lightPalette ? "#AFFFF9" : "#46FFD2", Math.min(120, outerAlpha)));
            canvas.drawOval(
                    ripple.x - width,
                    ripple.y - height,
                    ripple.x + width,
                    ripple.y + height,
                    ripplePaint
            );

            ripplePaint.setShadowLayer(dp(5f), 0f, 0f,
                    withAlpha(lightPalette ? "#D7FFFD" : "#74FFE0", Math.min(100, (int) (outerAlpha * 0.72f))));
            ripplePaint.setStrokeWidth(Math.max(dp(0.8f), ripple.thickness * 0.55f));
            ripplePaint.setColor(withAlpha(lightPalette ? "#F8FFFF" : "#CEFFF4",
                    Math.min(132, (int) (outerAlpha * 0.58f))));
            canvas.drawArc(
                    ripple.x - width * 0.66f,
                    ripple.y - height * 0.64f,
                    ripple.x + width * 0.66f,
                    ripple.y + height * 0.64f,
                    186f,
                    168f,
                    false,
                    ripplePaint
            );
            ripplePaint.clearShadowLayer();
        }
    }

    private void drawScanlines(Canvas canvas) {
        float step = dp(7f);
        scanlinePaint.setStrokeWidth(dp(0.45f));
        scanlinePaint.setColor(withAlpha(lightPalette ? "#8FA7B3" : "#D7FFF0", lightPalette ? 6 : 5));
        for (float y = 0f; y < getHeight(); y += step) {
            canvas.drawLine(0f, y, getWidth(), y, scanlinePaint);
        }
    }

    private void spawnImpact(float x, float y, int layer) {
        int count = layer == LAYER_NEAR ? 10 : (layer == LAYER_MID ? 7 : 5);
        for (int i = 0; i < count; i++) {
            float angle = (float) (random.nextFloat() * Math.PI - Math.PI / 1.7f);
            float speed = dp(layer == LAYER_NEAR ? 82f + random.nextInt(42) : 48f + random.nextInt(34));
            float vx = (float) Math.cos(angle) * speed;
            float vy = (float) Math.sin(angle) * speed * 0.58f - dp(12f);
            float size = dp(layer == LAYER_NEAR ? 3.2f : layer == LAYER_MID ? 2.4f : 1.8f);
            float fade = layer == LAYER_NEAR ? 2.6f : 2.1f;
            String color = i % 3 == 0 ? "#F3FFF9" : (lightPalette ? "#C9FFF7" : "#5DFFD1");
            particles.add(new ImpactParticle(x, y, vx, vy, size, fade, color));
        }
        spawnGroundRipple(x, y, layer);
    }

    private void spawnGroundRipple(float x, float y, int layer) {
        float radius = layer == LAYER_NEAR ? dp(8f) : (layer == LAYER_MID ? dp(6f) : dp(5f));
        float speed = layer == LAYER_NEAR ? dp(88f) : (layer == LAYER_MID ? dp(74f) : dp(58f));
        float fadeSpeed = layer == LAYER_NEAR ? 2.1f : 1.8f;
        float thickness = layer == LAYER_NEAR ? dp(1.45f) : dp(1.05f);
        float aspectX = 1.95f + random.nextFloat() * 0.45f;
        float aspectY = 0.22f + random.nextFloat() * 0.05f;
        ripples.add(new GroundRipple(x, y + dp(1f), radius, speed, fadeSpeed, thickness, aspectX, aspectY));

        if (layer != LAYER_FAR) {
            ripples.add(new GroundRipple(
                    x + dp(random.nextBoolean() ? 2f : -2f),
                    y + dp(1.8f),
                    radius * 0.72f,
                    speed * 0.82f,
                    fadeSpeed * 1.18f,
                    thickness * 0.78f,
                    aspectX * 0.9f,
                    aspectY * 0.86f
            ));
        }
    }

    private Drop createDrop(int layer) {
        float x = dp(8f) + random.nextFloat() * Math.max(dp(20f), getWidth() - dp(24f));
        float y = -dp(36f) - random.nextFloat() * dp(160f);

        if (layer == LAYER_FAR) {
            return buildDrop(layer, x, y, dp(150f + random.nextInt(80)), dp(9.5f), dp(11f),
                    7 + random.nextInt(5), lightPalette ? 38 : 34, dp(4f));
        }
        if (layer == LAYER_NEAR) {
            return buildDrop(layer, x, y, dp(340f + random.nextInt(160)), dp(17f), dp(17f),
                    9 + random.nextInt(6), lightPalette ? 132 : 158, dp(10f));
        }
        return buildDrop(layer, x, y, dp(240f + random.nextInt(120)), dp(13f), dp(14f),
                8 + random.nextInt(5), lightPalette ? 96 : 118, dp(7f));
    }

    private Drop buildDrop(int layer, float x, float y, float speed, float textSize, float charStep,
                           int trailLength, int baseAlpha, float driftAmplitude) {
        char[] glyphs = new char[trailLength];
        for (int i = 0; i < trailLength; i++) {
            glyphs[i] = GLYPHS[random.nextInt(GLYPHS.length)];
        }
        return new Drop(
                x,
                y,
                speed,
                trailLength,
                glyphs,
                layer,
                textSize,
                charStep,
                baseAlpha,
                driftAmplitude,
                random.nextFloat() * 6.28f,
                0.8f + random.nextFloat() * 1.8f
        );
    }

    private void applyPalette() {
        glyphPaint.setColor(lightPalette ? Color.parseColor("#84FFF4") : Color.parseColor("#5EFFD1"));
        headPaint.setColor(lightPalette ? Color.parseColor("#FFFFFF") : Color.parseColor("#F0FFF8"));
        glowPaint.setColor(lightPalette ? Color.parseColor("#D3FFFB") : Color.parseColor("#68FFD8"));
        particlePaint.setColor(lightPalette ? Color.parseColor("#A9FFF6") : Color.parseColor("#53FFD0"));
        groundPaint.setColor(lightPalette ? Color.parseColor("#9EDFD7") : Color.parseColor("#4CF1C9"));
        scanlinePaint.setColor(lightPalette ? Color.parseColor("#12A3B8C4") : Color.parseColor("#08D7FFF0"));
        gridPaint.setColor(lightPalette ? Color.parseColor("#7EBEB7") : Color.parseColor("#45DEC8"));
    }

    private float lerp(float start, float end, float amount) {
        return start + (end - start) * amount;
    }

    private float dp(float value) {
        return value * density;
    }

    private int withAlpha(String colorString, int alpha) {
        int color = Color.parseColor(colorString);
        return Color.argb(
                Math.max(0, Math.min(255, alpha)),
                Color.red(color),
                Color.green(color),
                Color.blue(color)
        );
    }

    private static final class Drop {
        float x;
        float y;
        final float speed;
        final int trailLength;
        final char[] glyphs;
        final int layer;
        final float textSize;
        final float charStep;
        final int baseAlpha;
        final float driftAmplitude;
        float phase;
        final float phaseSpeed;

        private Drop(float x,
                     float y,
                     float speed,
                     int trailLength,
                     char[] glyphs,
                     int layer,
                     float textSize,
                     float charStep,
                     int baseAlpha,
                     float driftAmplitude,
                     float phase,
                     float phaseSpeed) {
            this.x = x;
            this.y = y;
            this.speed = speed;
            this.trailLength = trailLength;
            this.glyphs = glyphs;
            this.layer = layer;
            this.textSize = textSize;
            this.charStep = charStep;
            this.baseAlpha = baseAlpha;
            this.driftAmplitude = driftAmplitude;
            this.phase = phase;
            this.phaseSpeed = phaseSpeed;
        }
    }

    private static final class ImpactParticle {
        float x;
        float y;
        final float vx;
        float vy;
        final float size;
        final float fadeSpeed;
        final String color;
        float alpha = 1f;

        private ImpactParticle(float x, float y, float vx, float vy, float size, float fadeSpeed, String color) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.size = size;
            this.fadeSpeed = fadeSpeed;
            this.color = color;
        }
    }

    private static final class GroundRipple {
        final float x;
        final float y;
        float radius;
        final float speed;
        final float fadeSpeed;
        float thickness;
        final float aspectX;
        final float aspectY;
        float alpha = 1f;

        private GroundRipple(float x,
                             float y,
                             float radius,
                             float speed,
                             float fadeSpeed,
                             float thickness,
                             float aspectX,
                             float aspectY) {
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.speed = speed;
            this.fadeSpeed = fadeSpeed;
            this.thickness = thickness;
            this.aspectX = aspectX;
            this.aspectY = aspectY;
        }
    }
}
