package com.example.koukou.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ThemeAtmosphereView extends View {
    public static final String MODE_BUTTERFLY = "butterfly";
    public static final String MODE_MINIMAL = "minimal";
    public static final String MODE_CYBER = "cyber";
    public static final String MODE_STARDUST = "stardust";

    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nodePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path ribbonPath = new Path();
    private final Path shardPath = new Path();
    private final Path prismPath = new Path();
    private final Random random = new Random();
    private final List<Float> seeds = new ArrayList<>();
    private final List<StardustParticle> stardustParticles = new ArrayList<>();

    private String mode = MODE_BUTTERFLY;
    private boolean effectEnabled = false;
    private boolean lightPalette = false;
    private long startTimeMs;
    private float density;

    public ThemeAtmosphereView(Context context) {
        super(context);
        init();
    }

    public ThemeAtmosphereView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ThemeAtmosphereView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        fillPaint.setStyle(Paint.Style.FILL);
        dotPaint.setStyle(Paint.Style.FILL);
        shardPaint.setStyle(Paint.Style.STROKE);
        shardPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setStyle(Paint.Style.FILL);
        gridPaint.setStyle(Paint.Style.STROKE);
        nodePaint.setStyle(Paint.Style.FILL);
        startTimeMs = System.currentTimeMillis();
        for (int i = 0; i < 36; i++) {
            seeds.add(random.nextFloat());
        }
        generateStardustField();
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        setAlpha(0.9f);
    }

    public void setMode(String mode) {
        String next = mode == null ? MODE_BUTTERFLY : mode;
        if (next.equals(this.mode)) {
            return;
        }
        this.mode = next;
        invalidate();
    }

    public void setEffectEnabled(boolean enabled) {
        if (effectEnabled == enabled) {
            return;
        }
        effectEnabled = enabled;
        if (enabled) {
            startTimeMs = System.currentTimeMillis();
            invalidate();
        }
    }

    public void setLightPalette(boolean lightPalette) {
        if (this.lightPalette == lightPalette) {
            return;
        }
        this.lightPalette = lightPalette;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!effectEnabled || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }

        float t = (System.currentTimeMillis() - startTimeMs) / 1000f;
        if (MODE_MINIMAL.equals(mode)) {
            drawMinimal(canvas, t);
        } else if (MODE_CYBER.equals(mode)) {
            drawCyber(canvas, t);
        } else if (MODE_STARDUST.equals(mode)) {
            drawStardust(canvas, t);
        } else {
            drawButterfly(canvas, t);
        }
        postInvalidateOnAnimation();
    }

    private void drawButterfly(Canvas canvas, float t) {
        strokePaint.setStrokeWidth(dp(2f));
        for (int i = 0; i < 4; i++) {
            float phase = t * (0.34f + i * 0.05f) + i;
            float startX = getWidth() * (0.64f + i * 0.05f);
            float startY = getHeight() * (0.14f + i * 0.08f);
            float endX = getWidth() * (0.08f + i * 0.04f);
            float endY = getHeight() * (0.72f + i * 0.08f);
            float wave = (float) Math.sin(phase) * dp(24f);
            ribbonPath.reset();
            ribbonPath.moveTo(startX, startY + wave);
            ribbonPath.cubicTo(
                    getWidth() * 0.58f, startY - dp(20f),
                    getWidth() * 0.26f, endY - dp(44f),
                    endX, endY - wave
            );
            strokePaint.setColor(withAlpha(lightPalette ? "#C9F4FF" : "#95F5FF", 44 - i * 6));
            canvas.drawPath(ribbonPath, strokePaint);
        }

        shardPaint.setStrokeWidth(dp(1.05f));
        glowPaint.setColor(withAlpha(lightPalette ? "#FFFFFF" : "#D3FFFF", 42));
        for (int i = 0; i < 16; i++) {
            float seed = seeds.get(i);
            float cx = getWidth() * (0.58f - (i % 6) * 0.06f + seed * 0.18f);
            float cy = getHeight() * (0.2f + (i % 8) * 0.07f);
            float driftX = (float) Math.sin(t * 0.9f + i) * dp(18f) - i * dp(5f);
            float driftY = (float) Math.cos(t * 0.75f + i * 0.6f) * dp(11f) + i * dp(3f);
            float length = dp(10f + (i % 4) * 4f);
            float tilt = (float) Math.sin(t * 0.6f + i) * 0.7f;
            shardPath.reset();
            shardPath.moveTo(cx + driftX - length * 0.6f, cy + driftY);
            shardPath.lineTo(cx + driftX, cy + driftY - length * 0.34f);
            shardPath.lineTo(cx + driftX + length * 0.55f, cy + driftY + tilt * dp(4f));
            shardPath.lineTo(cx + driftX - length * 0.16f, cy + driftY + length * 0.42f);
            shardPath.close();
            shardPaint.setColor(withAlpha(i % 2 == 0 ? "#EFFFFF" : "#AEEBFF", 78));
            canvas.drawPath(shardPath, shardPaint);
            canvas.drawCircle(cx + driftX, cy + driftY, dp(1.4f), glowPaint);
        }

        for (int i = 0; i < 12; i++) {
            float seed = seeds.get(i);
            float x = (getWidth() * (0.56f - seed * 0.42f)) - t * dp(18f) - i * dp(7f);
            while (x < -dp(28f)) {
                x += getWidth() * 0.6f + dp(60f);
            }
            float y = getHeight() * (0.18f + 0.62f * ((i + 1) / 13f)) + i * dp(6f)
                    + (float) Math.cos(t * 0.7f + i) * dp(10f);
            dotPaint.setColor(withAlpha(i % 3 == 0 ? "#C8FFFF" : "#7FE7FF", 130));
            canvas.drawCircle(x, y, dp(i % 3 == 0 ? 2.2f : 1.5f), dotPaint);
            if (i % 4 == 0) {
                strokePaint.setStrokeWidth(dp(1f));
                strokePaint.setColor(withAlpha("#B6EEFF", 74));
                canvas.drawLine(x + dp(4f), y - dp(2f), x + dp(18f), y - dp(10f), strokePaint);
            }
        }
    }

    private void drawMinimal(Canvas canvas, float t) {
        gridPaint.setStrokeWidth(dp(0.55f));
        for (int i = 0; i < 11; i++) {
            float y = getHeight() * (0.08f + i * 0.09f);
            gridPaint.setColor(withAlpha(lightPalette ? "#DDF6FF" : "#9EC9E6", i % 3 == 0 ? 12 : 8));
            canvas.drawLine(0f, y, getWidth(), y, gridPaint);
        }
        for (int i = 0; i < 8; i++) {
            float x = getWidth() * (0.08f + i * 0.12f);
            gridPaint.setColor(withAlpha(lightPalette ? "#DDF6FF" : "#9EC9E6", i % 2 == 0 ? 10 : 6));
            canvas.drawLine(x, 0f, x, getHeight(), gridPaint);
        }

        strokePaint.setStrokeWidth(dp(0.85f));
        strokePaint.setColor(withAlpha(lightPalette ? "#F4FBFF" : "#BFE3FF", 16));
        for (int i = 0; i < 4; i++) {
            float y = getHeight() * (0.18f + i * 0.18f);
            float xOffset = (float) Math.sin(t * 0.16f + i) * dp(8f);
            canvas.drawLine(getWidth() * 0.14f + xOffset, y, getWidth() * 0.86f + xOffset, y, strokePaint);
        }

        for (int i = 0; i < 14; i++) {
            float seed = seeds.get(10 + (i % 14));
            float x = (seed * getWidth() + t * dp(3.2f) * (i % 2 == 0 ? 1f : -1f)) % getWidth();
            if (x < 0f) {
                x += getWidth();
            }
            float y = getHeight() * (0.1f + (i * 0.065f))
                    + (float) Math.sin(t * 0.24f + i * 0.8f) * dp(4f);
            float radius = i % 4 == 0 ? dp(2.2f) : (i % 3 == 0 ? dp(1.6f) : dp(1.1f));
            int alpha = i % 4 == 0 ? 48 : 26;
            dotPaint.setColor(withAlpha(lightPalette ? "#FFFFFF" : "#D8EEFF", alpha));
            canvas.drawCircle(x, y, radius, dotPaint);
        }
    }

    private void drawCyber(Canvas canvas, float t) {
        float w = getWidth();
        float h = getHeight();
        float vanishX = w * 0.7f;
        float horizonY = h * 0.23f;

        fillPaint.setColor(withAlpha("#7A5CFF", lightPalette ? 18 : 26));
        canvas.drawCircle(w * 0.86f, h * 0.74f, w * 0.32f, fillPaint);
        fillPaint.setColor(withAlpha("#00E6FF", lightPalette ? 10 : 18));
        canvas.drawCircle(w * 0.18f, h * 0.16f, w * 0.22f, fillPaint);

        gridPaint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 12; i++) {
            float progress = i / 11f;
            float startX = w * (-0.08f + progress * 1.18f);
            boolean major = i % 3 == 0;
            gridPaint.setStrokeWidth(dp(major ? 1.1f : 0.55f));
            gridPaint.setColor(withAlpha(major ? "#96EFFF" : "#5B8EE7FF", major ? 40 : 18));
            canvas.drawLine(startX, h, vanishX, horizonY, gridPaint);
            if (major) {
                drawChromaticLine(canvas, startX, h, vanishX, horizonY, "#7A5CFF", "#00E6FF", 18);
            }
        }

        for (int i = 0; i < 9; i++) {
            float p = i / 8f;
            float depth = p * p;
            float y = lerp(h * 0.94f, horizonY, depth);
            float leftX = lerp(-w * 0.1f, vanishX, depth);
            float rightX = lerp(w * 1.08f, vanishX, depth);
            boolean major = i % 2 == 0;
            gridPaint.setStrokeWidth(dp(major ? 1f : 0.5f));
            gridPaint.setColor(withAlpha(major ? "#90E9FF" : "#6797D0", major ? 30 : 12));
            canvas.drawLine(leftX, y, rightX, y, gridPaint);
        }

        for (int i = 0; i < 6; i++) {
            float p = (i + 1) / 7f;
            float y = lerp(h * 0.88f, horizonY + dp(16f), p * p);
            float leftX = lerp(w * 0.08f, vanishX, p * 0.72f);
            float rightX = lerp(w * 0.92f, vanishX, p * 0.72f);
            drawCrosshair(canvas, leftX, y, i % 2 == 0);
            drawCrosshair(canvas, rightX, y, i % 2 != 0);
        }

        float[][] nodes = new float[][]{
                {w * 0.12f, h * 0.68f}, {w * 0.24f, h * 0.58f}, {w * 0.38f, h * 0.62f},
                {w * 0.54f, h * 0.48f}, {w * 0.68f, h * 0.56f}, {w * 0.82f, h * 0.42f},
                {w * 0.28f, h * 0.34f}, {w * 0.48f, h * 0.28f}, {w * 0.72f, h * 0.3f}
        };
        int[][] links = new int[][]{
                {0, 1}, {1, 2}, {2, 4}, {4, 5}, {1, 6}, {6, 7}, {7, 8}, {8, 5}, {2, 3}, {3, 7}
        };

        strokePaint.setStrokeWidth(dp(1f));
        for (int i = 0; i < links.length; i++) {
            float[] a = nodes[links[i][0]];
            float[] b = nodes[links[i][1]];
            strokePaint.setColor(withAlpha(i % 2 == 0 ? "#89EFFF" : "#9174FF", 52));
            canvas.drawLine(a[0], a[1], b[0], b[1], strokePaint);
            drawPulseOnLink(canvas, a[0], a[1], b[0], b[1], (t * (0.55f + i * 0.06f)) % 1f);
        }

        for (int i = 0; i < nodes.length; i++) {
            float[] node = nodes[i];
            float pulse = (float) (0.5f + 0.5f * Math.sin(t * 2.2f + i));
            fillPaint.setColor(withAlpha("#00E6FF", 28));
            canvas.drawCircle(node[0], node[1], dp(9f + pulse * 3f), fillPaint);
            nodePaint.setColor(withAlpha(i % 3 == 0 ? "#FFFFFF" : "#B9F8FF", 190));
            canvas.drawCircle(node[0], node[1], dp(i % 3 == 0 ? 2.4f : 1.9f), nodePaint);
            drawChromaticNode(canvas, node[0], node[1]);
        }

        for (int i = 0; i < 5; i++) {
            float seed = seeds.get(i);
            float x = (seed * w + t * dp(42f) * (i % 2 == 0 ? 1f : -0.8f)) % (w + dp(80f));
            if (x < -dp(40f)) {
                x += w + dp(80f);
            }
            float y = h * (0.2f + i * 0.1f);
            fillPaint.setColor(withAlpha("#00E6FF", 72));
            canvas.drawRoundRect(x, y, x + dp(38f), y + dp(8f), dp(4f), dp(4f), fillPaint);
            strokePaint.setStrokeWidth(dp(1.4f));
            strokePaint.setColor(withAlpha("#D5FBFF", 86));
            canvas.drawRoundRect(x + dp(46f), y, x + dp(92f), y + dp(8f), dp(4f), dp(4f), strokePaint);
        }

        float sweepY = (t * dp(44f)) % (h + dp(110f));
        fillPaint.setColor(withAlpha("#CFFFFF", 20));
        canvas.drawRect(0f, sweepY - dp(18f), w, sweepY, fillPaint);

        gridPaint.setStrokeWidth(dp(0.5f));
        for (int i = 0; i < 80; i++) {
            float y = i * dp(9f);
            gridPaint.setColor(withAlpha("#FFFFFF", 5));
            canvas.drawLine(0f, y, w, y, gridPaint);
        }
    }

    private void drawStardust(Canvas canvas, float t) {
        float w = getWidth();
        float h = getHeight();

        fillPaint.setShadowLayer(dp(46f), 0f, 0f, withAlpha("#7A5CFF", 22));
        fillPaint.setColor(withAlpha("#7A5CFF", 18));
        canvas.drawCircle(w * 0.84f, h * 0.18f, w * 0.18f, fillPaint);

        fillPaint.setShadowLayer(dp(54f), 0f, 0f, withAlpha("#00E6FF", 18));
        fillPaint.setColor(withAlpha("#00E6FF", 12));
        canvas.drawCircle(w * 0.16f, h * 0.76f, w * 0.24f, fillPaint);

        fillPaint.setShadowLayer(dp(30f), 0f, 0f, withAlpha("#F3F6FC", 14));
        fillPaint.setColor(withAlpha("#F3F6FC", 8));
        canvas.drawCircle(w * 0.52f, h * 0.42f, w * 0.12f, fillPaint);
        fillPaint.clearShadowLayer();

        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 5; i++) {
            float seed = seeds.get(i);
            float baseX = w * (0.62f + seed * 0.28f);
            float baseY = h * (0.12f + i * 0.16f);
            float drift = (float) Math.sin(t * (0.18f + i * 0.05f) + i) * dp(18f);
            strokePaint.setStrokeWidth(dp(i == 0 ? 1.2f : 0.8f));
            strokePaint.setColor(withAlpha(i % 2 == 0 ? "#CFFBFF" : "#9DE9FF", 18 - i * 2));
            canvas.drawLine(baseX + drift, baseY, baseX - dp(220f), baseY + dp(110f), strokePaint);
        }

        for (int layer = 0; layer < 3; layer++) {
            float minDepth = layer == 0 ? 0f : (layer == 1 ? 0.34f : 0.68f);
            float maxDepth = layer == 0 ? 0.34f : (layer == 1 ? 0.68f : 1.01f);
            for (StardustParticle particle : stardustParticles) {
                if (particle.depth < minDepth || particle.depth >= maxDepth) {
                    continue;
                }
                float px = particle.xRatio * w
                        + (float) Math.sin(t * particle.driftSpeedX + particle.phase) * dp(particle.driftX) * (0.45f + particle.depth)
                        + (float) Math.cos(t * 0.12f + particle.phase) * dp(5f) * particle.depth;
                float py = particle.yRatio * h
                        + (float) Math.cos(t * particle.driftSpeedY + particle.phase * 1.2f) * dp(particle.driftY) * (0.42f + particle.depth);
                float twinkle = 0.42f + 0.58f * (0.5f + 0.5f * (float) Math.sin(t * particle.twinkleSpeed + particle.phase));
                float radius = dp(particle.baseSize) * (0.72f + particle.depth * 0.9f) * (0.9f + twinkle * 0.22f);
                int coreAlpha = (int) ((layer == 0 ? 42 : (layer == 1 ? 92 : 156)) * twinkle);
                int glowAlpha = (int) ((layer == 0 ? 24 : (layer == 1 ? 64 : 112)) * twinkle);
                drawStardustParticle(canvas, particle, px, py, radius, coreAlpha, glowAlpha);
            }
        }

        for (int i = 0; i < 11; i++) {
            float seed = seeds.get(10 + i);
            float px = w * seed;
            float py = h * (0.1f + i * 0.072f);
            float radius = dp(i % 4 == 0 ? 7f : 4.5f);
            int alpha = i % 3 == 0 ? 18 : 10;
            fillPaint.setShadowLayer(radius * 2.6f, 0f, 0f, withAlpha(i % 2 == 0 ? "#00E6FF" : "#7A5CFF", alpha));
            fillPaint.setColor(withAlpha("#FFFFFF", alpha / 2));
            canvas.drawCircle(px, py, radius, fillPaint);
        }
        fillPaint.clearShadowLayer();
    }

    private void drawStardustParticle(Canvas canvas,
                                      StardustParticle particle,
                                      float px,
                                      float py,
                                      float radius,
                                      int coreAlpha,
                                      int glowAlpha) {
        String edgeColor = particle.colorSeed > 0.84f
                ? "#FFFFFF"
                : (particle.colorSeed > 0.42f ? "#00E6FF" : "#7A5CFF");
        String glowColor = particle.colorSeed > 0.84f
                ? "#E8FFFF"
                : (particle.colorSeed > 0.42f ? "#5BFFF1" : "#A892FF");

        if (particle.shape == 2) {
            float length = radius * (3.2f + particle.depth);
            float tilt = radius * (0.5f + particle.colorSeed);
            strokePaint.setStrokeWidth(Math.max(dp(0.8f), radius * 0.42f));
            strokePaint.setColor(withAlpha(edgeColor, coreAlpha));
            strokePaint.setShadowLayer(radius * 2.8f, 0f, 0f, withAlpha(glowColor, glowAlpha));
            canvas.drawLine(px - length, py + tilt, px + length, py - tilt, strokePaint);
            strokePaint.clearShadowLayer();
            return;
        }

        if (particle.shape == 1) {
            prismPath.reset();
            prismPath.moveTo(px, py - radius * 1.35f);
            prismPath.lineTo(px + radius * 1.1f, py);
            prismPath.lineTo(px, py + radius * 1.35f);
            prismPath.lineTo(px - radius * 1.1f, py);
            prismPath.close();

            fillPaint.setColor(withAlpha(edgeColor, Math.max(18, coreAlpha / 2)));
            fillPaint.setShadowLayer(radius * 3.2f, 0f, 0f, withAlpha(glowColor, glowAlpha));
            canvas.drawPath(prismPath, fillPaint);

            strokePaint.setStrokeWidth(Math.max(dp(0.65f), radius * 0.2f));
            strokePaint.setColor(withAlpha("#F3F6FC", Math.min(180, coreAlpha + 20)));
            strokePaint.setShadowLayer(radius * 2f, 0f, 0f, withAlpha(glowColor, glowAlpha / 2));
            canvas.drawPath(prismPath, strokePaint);

            fillPaint.setColor(withAlpha("#FFFFFF", Math.min(220, coreAlpha + 36)));
            canvas.drawCircle(px, py, radius * 0.25f, fillPaint);
            fillPaint.clearShadowLayer();
            strokePaint.clearShadowLayer();
            return;
        }

        fillPaint.setColor(withAlpha(edgeColor, Math.min(210, coreAlpha)));
        fillPaint.setShadowLayer(radius * 3.4f, 0f, 0f, withAlpha(glowColor, glowAlpha));
        canvas.drawCircle(px, py, radius, fillPaint);
        fillPaint.setColor(withAlpha("#FFFFFF", Math.min(240, coreAlpha + 28)));
        canvas.drawCircle(px, py, radius * 0.34f, fillPaint);
        fillPaint.clearShadowLayer();
    }

    private void generateStardustField() {
        stardustParticles.clear();
        for (int i = 0; i < 92; i++) {
            float depth = random.nextFloat();
            stardustParticles.add(new StardustParticle(
                    0.02f + random.nextFloat() * 0.96f,
                    0.04f + random.nextFloat() * 0.92f,
                    depth,
                    0.8f + random.nextFloat() * (depth > 0.66f ? 2.2f : 1.4f),
                    random.nextFloat() * 6.28f,
                    0.22f + random.nextFloat() * 1.2f,
                    2.4f + random.nextFloat() * 11f,
                    2f + random.nextFloat() * 9f,
                    0.12f + random.nextFloat() * 0.46f,
                    0.1f + random.nextFloat() * 0.44f,
                    i % 7 == 0 ? 2 : (i % 4 == 0 ? 1 : 0),
                    random.nextFloat()
            ));
        }
    }

    private void drawCrosshair(Canvas canvas, float cx, float cy, boolean major) {
        strokePaint.setStrokeWidth(dp(major ? 1.05f : 0.7f));
        strokePaint.setColor(withAlpha(major ? "#B8F7FF" : "#7DBAD6", major ? 58 : 28));
        float arm = dp(major ? 7f : 4f);
        canvas.drawLine(cx - arm, cy, cx + arm, cy, strokePaint);
        canvas.drawLine(cx, cy - arm, cx, cy + arm, strokePaint);
    }

    private void drawPulseOnLink(Canvas canvas, float x1, float y1, float x2, float y2, float progress) {
        float px = lerp(x1, x2, progress);
        float py = lerp(y1, y2, progress);
        fillPaint.setColor(withAlpha("#00E6FF", 34));
        canvas.drawCircle(px, py, dp(8f), fillPaint);
        nodePaint.setColor(withAlpha("#E8FFFF", 220));
        canvas.drawCircle(px, py, dp(2.3f), nodePaint);
    }

    private void drawChromaticLine(Canvas canvas, float x1, float y1, float x2, float y2, String a, String b, int alpha) {
        strokePaint.setStrokeWidth(dp(0.7f));
        strokePaint.setColor(withAlpha(a, alpha));
        canvas.drawLine(x1 - dp(0.8f), y1, x2 - dp(0.8f), y2, strokePaint);
        strokePaint.setColor(withAlpha(b, alpha));
        canvas.drawLine(x1 + dp(0.8f), y1, x2 + dp(0.8f), y2, strokePaint);
    }

    private void drawChromaticNode(Canvas canvas, float cx, float cy) {
        fillPaint.setColor(withAlpha("#7A5CFF", 44));
        canvas.drawCircle(cx - dp(1f), cy, dp(2.6f), fillPaint);
        fillPaint.setColor(withAlpha("#00E6FF", 44));
        canvas.drawCircle(cx + dp(1f), cy, dp(2.6f), fillPaint);
    }

    private float lerp(float start, float end, float amount) {
        return start + (end - start) * amount;
    }

    private int withAlpha(String colorString, int alpha) {
        int base = Color.parseColor(colorString);
        return Color.argb(
                Math.max(0, Math.min(255, alpha)),
                Color.red(base),
                Color.green(base),
                Color.blue(base)
        );
    }

    private float dp(float value) {
        return value * density;
    }

    private static final class StardustParticle {
        final float xRatio;
        final float yRatio;
        final float depth;
        final float baseSize;
        final float phase;
        final float twinkleSpeed;
        final float driftX;
        final float driftY;
        final float driftSpeedX;
        final float driftSpeedY;
        final int shape;
        final float colorSeed;

        private StardustParticle(float xRatio,
                                 float yRatio,
                                 float depth,
                                 float baseSize,
                                 float phase,
                                 float twinkleSpeed,
                                 float driftX,
                                 float driftY,
                                 float driftSpeedX,
                                 float driftSpeedY,
                                 int shape,
                                 float colorSeed) {
            this.xRatio = xRatio;
            this.yRatio = yRatio;
            this.depth = depth;
            this.baseSize = baseSize;
            this.phase = phase;
            this.twinkleSpeed = twinkleSpeed;
            this.driftX = driftX;
            this.driftY = driftY;
            this.driftSpeedX = driftSpeedX;
            this.driftSpeedY = driftSpeedY;
            this.shape = shape;
            this.colorSeed = colorSeed;
        }
    }
}
