package com.example.koukou.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 互动雨滴特效 View：
 *  - 物理线程固定 16ms 步进
 *  - 重力传感器低通滤波驱动
 *  - 对象池 + 屏幕网格哈希做 O(N) 级碰撞检测
 *  - 融合时质量/动量守恒
 *  - 滑动时沿轨迹生成静态附着小水滴
 *  - 触摸擦拭回收水滴
 *  - 点击产生轻量涟漪
 *  - 用 RadialGradient 高光 + 边缘暗化模拟水滴折射观感
 */
public class RaindropFxView extends View implements SensorEventListener {

    private static final int POOL_SIZE = 220;
    private static final int MAX_ACTIVE = 110;
    private static final int MAX_STATIC = 360;
    private static final int MAX_RIPPLES = 24;
    private static final int MAX_STREAKS = 200;
    private static final long FRAME_DELAY_MS = 16L;
    private static final int MSG_TICK = 1;
    private static final float TIME_STEP = 0.016f;
    private static final float FRICTION = 0.985f;
    private static final float MIN_SPEED_TO_TRAIL = 180f; // px/s
    private static final float WIPE_RADIUS_DP = 28f;
    private static final float SPAWN_INTERVAL_S = 0.18f;
    private static final int DROP_ATLAS_SIZE = 128;

    private final DropParticle[] pool = new DropParticle[POOL_SIZE];
    private final List<DropParticle> activeDrops = new ArrayList<>(POOL_SIZE);
    private final List<DropParticle> staticDrops = new ArrayList<>(MAX_STATIC);
    private final List<Ripple> ripples = new ArrayList<>(MAX_RIPPLES);
    private final List<Streak> streaks = new ArrayList<>(MAX_STREAKS);
    private final Random random = new Random();

    private final Paint ripplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mistPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint streakPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint atlasPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final RectF dropDst = new RectF();

    // 一次性烤制的“理想水滴”贴图：含暗边折射 + 主体渐变 + 顶部镜面高光 + 底部反射月牙。
    private Bitmap dropAtlas;
    private boolean atlasDirty = true;

    private HandlerThread physicsThread;
    private Handler physicsHandler;
    private final Object physicsLock = new Object();

    private SensorManager sensorManager;
    private Sensor gravitySensor;
    private float filteredGx = 0f;
    private float filteredGy = 320f; // 默认有竖直向下重力
    private static final float LOW_PASS_ALPHA = 0.18f;

    private boolean rainEnabled = false;
    private boolean lightPalette = false;
    private boolean attached = false;
    private float density = 1f;
    private long lastSpawnMs = 0L;

    private float lastTouchX = -1f, lastTouchY = -1f;
    private long lastTouchMs = 0L;

    private int colorBody;
    private int colorHighlight;
    private int colorRim;
    private int colorRipple;
    private int colorMist;

    public RaindropFxView(Context context) {
        super(context);
        init();
    }

    public RaindropFxView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RaindropFxView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        for (int i = 0; i < POOL_SIZE; i++) {
            pool[i] = new DropParticle();
        }
        ripplePaint.setStyle(Paint.Style.STROKE);
        mistPaint.setStyle(Paint.Style.FILL);
        streakPaint.setStyle(Paint.Style.STROKE);
        streakPaint.setStrokeCap(Paint.Cap.ROUND);
        applyPalette();
        setWillNotDraw(false);
        // 不使用 setShadowLayer，不需要软件层；保持硬件加速路径
        setAlpha(0.95f);
    }

    public void setLightPalette(boolean light) {
        if (lightPalette == light) {
            return;
        }
        lightPalette = light;
        applyPalette();
        invalidate();
    }

    private void applyPalette() {
        if (lightPalette) {
            colorBody = 0x55B4D8F0;
            colorHighlight = 0xCCFFFFFF;
            colorRim = 0x66FFFFFF;
            colorRipple = 0x66247BE0;
            colorMist = 0x14B4D8F0;
        } else {
            colorBody = 0x55A8C8FF;
            colorHighlight = 0xCCFFFFFF;
            colorRim = 0x66E6F3FF;
            colorRipple = 0x66B4D8FF;
            colorMist = 0x14B4D8FF;
        }
        atlasDirty = true;
    }

    /**
     * 烤制一次性的理想水滴贴图，避免每帧每水滴创建 RadialGradient。
     * 贴图含：
     *  - 外环暗边折射 alpha
     *  - 主体渐变：左上亮 → 左下暗
     *  - 左上尖锐镜面高光
     *  - 右下微小次高光
     *  - 底部 Fresnel 亮月牙（光从背面透过水滴底部）
     */
    private void buildDropAtlas() {
        if (dropAtlas != null) {
            dropAtlas.recycle();
        }
        dropAtlas = Bitmap.createBitmap(DROP_ATLAS_SIZE, DROP_ATLAS_SIZE, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(dropAtlas);
        float cx = DROP_ATLAS_SIZE * 0.5f;
        float cy = DROP_ATLAS_SIZE * 0.5f;
        float R = DROP_ATLAS_SIZE * 0.46f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // 1) 暗外环：模拟玻璃上水滴边缘的折射暗带
        p.setStyle(Paint.Style.FILL);
        RadialGradient rim = new RadialGradient(cx, cy, R * 1.02f,
                new int[]{0x00000000, 0x00000000, 0x40000000, 0x66000000, 0x00000000},
                new float[]{0f, 0.78f, 0.90f, 0.98f, 1f},
                Shader.TileMode.CLAMP);
        p.setShader(rim);
        c.drawCircle(cx, cy, R * 1.02f, p);
        p.setShader(null);

        // 2) 主体渐变：左上亮、右下暗，另加中心背景色。
        p.setStyle(Paint.Style.FILL);
        int body0 = lightPalette ? 0x55FFFFFF : 0x66FFFFFF;
        int body1 = lightPalette ? 0x35B4D8F0 : 0x40A8C8FF;
        int body2 = 0x00000000;
        int body3 = 0x55000000;
        RadialGradient body = new RadialGradient(cx - R * 0.28f, cy - R * 0.32f, R * 1.45f,
                new int[]{body0, body1, body2, body3},
                new float[]{0f, 0.40f, 0.78f, 1f},
                Shader.TileMode.CLAMP);
        p.setShader(body);
        c.drawCircle(cx, cy, R, p);
        p.setShader(null);

        // 3) 底部 Fresnel 亮月牙：在水滴内侧下缘画一条轻薄弧线
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(R * 0.10f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(0xCCFFFFFF);
        RectF arcRect = new RectF(cx - R * 0.74f, cy - R * 0.74f, cx + R * 0.74f, cy + R * 0.74f);
        c.drawArc(arcRect, 32f, 116f, false, p);

        // 4) 左上尖锐镜面高光：外轮廃低 alpha、中心趋近纯白
        p.setStyle(Paint.Style.FILL);
        RadialGradient hl = new RadialGradient(cx - R * 0.36f, cy - R * 0.42f, R * 0.36f,
                new int[]{0xFFFFFFFF, 0xCCFFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.30f, 1f},
                Shader.TileMode.CLAMP);
        p.setShader(hl);
        c.drawCircle(cx - R * 0.36f, cy - R * 0.42f, R * 0.36f, p);
        p.setShader(null);

        // 5) 右下次高光：补一点水体内部反射点
        p.setColor(0x88FFFFFF);
        c.drawCircle(cx + R * 0.22f, cy + R * 0.22f, R * 0.08f, p);

        atlasDirty = false;
    }

    public void setRainEnabled(boolean enabled) {
        if (rainEnabled == enabled) {
            return;
        }
        rainEnabled = enabled;
        if (enabled) {
            startPhysics();
            registerSensor();
            seedInitialDrops();
            invalidate();
        } else {
            stopPhysics();
            unregisterSensor();
            synchronized (physicsLock) {
                recycleAll();
                ripples.clear();
                streaks.clear();
            }
            invalidate();
        }
    }

    private void seedInitialDrops() {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        synchronized (physicsLock) {
            // 微小冷凝点：加强玻璃质感、不抢镜
            int condensation = Math.min(140, MAX_STATIC - staticDrops.size());
            for (int i = 0; i < condensation; i++) {
                DropParticle p = obtain();
                if (p == null) break;
                p.x = random.nextFloat() * width;
                p.y = random.nextFloat() * height;
                p.radius = (0.8f + random.nextFloat() * 1.8f) * density;
                p.mass = p.radius * p.radius * p.radius;
                p.vx = 0f;
                p.vy = 0f;
                p.isStatic = true;
                p.active = true;
                p.bornAt = System.currentTimeMillis();
                staticDrops.add(p);
            }
            // 中等静态水珠：冷凝联合成的珠
            int mediumBeads = Math.min(48, MAX_STATIC - staticDrops.size());
            for (int i = 0; i < mediumBeads; i++) {
                DropParticle p = obtain();
                if (p == null) break;
                p.x = random.nextFloat() * width;
                p.y = random.nextFloat() * height;
                p.radius = (2.2f + random.nextFloat() * 2.6f) * density;
                p.mass = p.radius * p.radius * p.radius;
                p.vx = 0f;
                p.vy = 0f;
                p.isStatic = true;
                p.active = true;
                p.bornAt = System.currentTimeMillis();
                staticDrops.add(p);
            }
            // 少量较大的珠，制造视觉重点
            int largeBeads = Math.min(16, MAX_STATIC - staticDrops.size());
            for (int i = 0; i < largeBeads; i++) {
                DropParticle p = obtain();
                if (p == null) break;
                p.x = random.nextFloat() * width;
                p.y = random.nextFloat() * height;
                p.radius = (4.5f + random.nextFloat() * 3.5f) * density;
                p.mass = p.radius * p.radius * p.radius;
                p.vx = 0f;
                p.vy = 0f;
                p.isStatic = true;
                p.active = true;
                p.bornAt = System.currentTimeMillis();
                staticDrops.add(p);
            }
        }
    }

    private void startPhysics() {
        if (physicsThread != null) {
            return;
        }
        physicsThread = new HandlerThread("raindrop-physics");
        physicsThread.start();
        physicsHandler = new Handler(physicsThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == MSG_TICK) {
                    stepPhysics();
                    if (rainEnabled) {
                        sendEmptyMessageDelayed(MSG_TICK, FRAME_DELAY_MS);
                    }
                }
            }
        };
        physicsHandler.sendEmptyMessage(MSG_TICK);
    }

    private void stopPhysics() {
        if (physicsHandler != null) {
            physicsHandler.removeMessages(MSG_TICK);
        }
        if (physicsThread != null) {
            physicsThread.quitSafely();
            physicsThread = null;
            physicsHandler = null;
        }
    }

    private void registerSensor() {
        if (sensorManager == null) {
            sensorManager = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
        }
        if (sensorManager != null) {
            gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
            if (gravitySensor == null) {
                gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            }
            if (gravitySensor != null) {
                sensorManager.registerListener(this, gravitySensor, SensorManager.SENSOR_DELAY_GAME);
            }
        }
    }

    private void unregisterSensor() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        if (rainEnabled) {
            startPhysics();
            registerSensor();
            invalidate();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attached = false;
        stopPhysics();
        unregisterSensor();
        if (dropAtlas != null) {
            dropAtlas.recycle();
            dropAtlas = null;
            atlasDirty = true;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (rainEnabled && staticDrops.isEmpty() && activeDrops.isEmpty()) {
            seedInitialDrops();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.values.length < 2) return;
        // 横向 X 反向，使得屏幕左侧倾斜时水滴向左流
        float rawX = -event.values[0] * 32f * density;
        float rawY = event.values[1] * 32f * density + 220f;
        filteredGx = LOW_PASS_ALPHA * rawX + (1f - LOW_PASS_ALPHA) * filteredGx;
        filteredGy = LOW_PASS_ALPHA * rawY + (1f - LOW_PASS_ALPHA) * filteredGy;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    /**
     * 由 Activity 的 dispatchTouchEvent 转发原始触摸坐标，避免破坏正常 UI 交互。
     */
    public void dispatchTouchSnapshot(MotionEvent event) {
        if (!rainEnabled) return;
        int action = event.getActionMasked();
        float x = event.getX();
        float y = event.getY();
        // 将屏幕坐标转换为 View 局部坐标
        int[] loc = new int[2];
        getLocationOnScreen(loc);
        x = event.getRawX() - loc[0];
        y = event.getRawY() - loc[1];
        if (x < 0 || y < 0 || x > getWidth() || y > getHeight()) {
            return;
        }
        long now = System.currentTimeMillis();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = x;
                lastTouchY = y;
                lastTouchMs = now;
                spawnRipple(x, y);
                wipeAt(x, y);
                break;
            case MotionEvent.ACTION_MOVE:
                if (lastTouchX >= 0) {
                    long dt = Math.max(1, now - lastTouchMs);
                    float dx = x - lastTouchX;
                    float dy = y - lastTouchY;
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);
                    int steps = Math.min(8, (int) (dist / (8f * density)) + 1);
                    for (int i = 1; i <= steps; i++) {
                        float t = i / (float) steps;
                        wipeAt(lastTouchX + dx * t, lastTouchY + dy * t);
                    }
                    // 给少量水滴一个速度，模拟"被推开"
                    pushNearby(x, y, dx * 6f, dy * 6f);
                }
                lastTouchX = x;
                lastTouchY = y;
                lastTouchMs = now;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                lastTouchX = -1f;
                lastTouchY = -1f;
                break;
        }
    }

    private void spawnRipple(float x, float y) {
        synchronized (physicsLock) {
            if (ripples.size() >= MAX_RIPPLES) {
                ripples.remove(0);
            }
            Ripple r = new Ripple();
            r.x = x;
            r.y = y;
            r.radius = 4f * density;
            r.maxRadius = (40f + random.nextFloat() * 30f) * density;
            r.alpha = 0.55f;
            ripples.add(r);
        }
    }

    private void wipeAt(float x, float y) {
        float radius = WIPE_RADIUS_DP * density;
        float r2 = radius * radius;
        synchronized (physicsLock) {
            for (int i = staticDrops.size() - 1; i >= 0; i--) {
                DropParticle p = staticDrops.get(i);
                float dx = p.x - x;
                float dy = p.y - y;
                if (dx * dx + dy * dy <= r2) {
                    staticDrops.remove(i);
                    recycle(p);
                }
            }
            for (int i = activeDrops.size() - 1; i >= 0; i--) {
                DropParticle p = activeDrops.get(i);
                float dx = p.x - x;
                float dy = p.y - y;
                if (dx * dx + dy * dy <= r2) {
                    activeDrops.remove(i);
                    recycle(p);
                }
            }
        }
    }

    private void pushNearby(float x, float y, float vx, float vy) {
        float radius = 50f * density;
        float r2 = radius * radius;
        synchronized (physicsLock) {
            for (int i = staticDrops.size() - 1; i >= 0; i--) {
                DropParticle p = staticDrops.get(i);
                float dx = p.x - x;
                float dy = p.y - y;
                if (dx * dx + dy * dy <= r2 && p.radius > 3f * density) {
                    p.isStatic = false;
                    p.vx = vx * 0.02f;
                    p.vy = vy * 0.02f + 40f;
                    staticDrops.remove(i);
                    if (activeDrops.size() < MAX_ACTIVE) {
                        activeDrops.add(p);
                    } else {
                        recycle(p);
                    }
                }
            }
        }
    }

    private void stepPhysics() {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        synchronized (physicsLock) {
            // 自动生成新落下的大水滴
            if (now - lastSpawnMs >= (long) (SPAWN_INTERVAL_S * 1000f) && activeDrops.size() < MAX_ACTIVE) {
                lastSpawnMs = now;
                DropParticle p = obtain();
                if (p != null) {
                    p.x = random.nextFloat() * width;
                    p.y = -10f * density;
                    p.radius = (4f + random.nextFloat() * 5.5f) * density;
                    p.mass = p.radius * p.radius * p.radius;
                    p.vx = (random.nextFloat() - 0.5f) * 40f;
                    p.vy = 30f + random.nextFloat() * 80f;
                    p.isStatic = false;
                    p.active = true;
                    p.bornAt = now;
                    activeDrops.add(p);
                }
            }

            // 更新动态水滴
            for (int i = activeDrops.size() - 1; i >= 0; i--) {
                DropParticle p = activeDrops.get(i);
                p.vx += filteredGx * TIME_STEP;
                p.vy += filteredGy * TIME_STEP;
                p.vx *= FRICTION;
                p.vy *= FRICTION;
                p.x += p.vx * TIME_STEP;
                p.y += p.vy * TIME_STEP;

                // 留痕：动态水滴沿“−速度”方向留下密集的微小附着珠，模拟玻璃上被带走后的水迹
                float speed2 = p.vx * p.vx + p.vy * p.vy;
                if (speed2 > MIN_SPEED_TO_TRAIL * MIN_SPEED_TO_TRAIL && p.radius > 2.5f * density) {
                    float speed = (float) Math.sqrt(speed2);
                    float invSpeed = 1f / speed;
                    float dirX = p.vx * invSpeed;
                    float dirY = p.vy * invSpeed;
                    // 按速度强度调节生成概率：越快越多微珠连成珠链
                    float emitProb = Math.min(0.95f, 0.45f + speed / 1200f);
                    if (staticDrops.size() < MAX_STATIC && random.nextFloat() < emitProb) {
                        DropParticle trail = obtain();
                        if (trail != null) {
                            // 沿负速度方向偏移一点点，跟在头滴后面
                            float offset = p.radius * (0.5f + random.nextFloat() * 0.7f);
                            trail.x = p.x - dirX * offset + (random.nextFloat() - 0.5f) * p.radius * 0.35f;
                            trail.y = p.y - dirY * offset + (random.nextFloat() - 0.5f) * p.radius * 0.35f;
                            trail.radius = Math.max(0.9f * density, p.radius * (0.14f + random.nextFloat() * 0.18f));
                            trail.mass = trail.radius * trail.radius * trail.radius;
                            trail.vx = 0f;
                            trail.vy = 0f;
                            trail.isStatic = true;
                            trail.active = true;
                            trail.bornAt = now;
                            staticDrops.add(trail);
                            float newMass = Math.max(p.mass - trail.mass, density * density * density);
                            p.radius = (float) Math.cbrt(newMass);
                            p.mass = newMass;
                        }
                    }
                }

                // 出屏回收
                if (p.y - p.radius > height + 8f * density
                        || p.x + p.radius < -10f * density
                        || p.x - p.radius > width + 10f * density) {
                    activeDrops.remove(i);
                    recycle(p);
                }
            }

            // 网格碰撞融合：动态 vs 动态 / 动态 vs 静态
            collideAndMerge(width, height);

            // 软上限：当静态水滴过多，淘汰最小最旧的
            trimStaticDropsIfNeeded(now);

            // 更新涟漪
            for (int i = ripples.size() - 1; i >= 0; i--) {
                Ripple r = ripples.get(i);
                r.radius += 180f * TIME_STEP;
                r.alpha -= 1.4f * TIME_STEP;
                if (r.alpha <= 0f || r.radius >= r.maxRadius) {
                    ripples.remove(i);
                }
            }

            // streak 系统仍保留以便后期扩展，但不再主动发射
            for (int i = streaks.size() - 1; i >= 0; i--) {
                Streak s = streaks.get(i);
                s.alpha -= 0.9f * TIME_STEP;
                if (s.alpha <= 0f) {
                    streaks.remove(i);
                }
            }
        }

        if (attached) {
            postInvalidateOnAnimation();
        }
    }

    private void collideAndMerge(int width, int height) {
        if (activeDrops.isEmpty()) {
            return;
        }
        float cell = 40f * density;
        int cols = Math.max(1, (int) Math.ceil(width / cell));
        int rows = Math.max(1, (int) Math.ceil(height / cell));
        @SuppressWarnings("unchecked")
        List<DropParticle>[] grid = new List[cols * rows];

        for (DropParticle p : activeDrops) {
            int gx = clamp((int) (p.x / cell), 0, cols - 1);
            int gy = clamp((int) (p.y / cell), 0, rows - 1);
            int idx = gy * cols + gx;
            if (grid[idx] == null) grid[idx] = new ArrayList<>(4);
            grid[idx].add(p);
        }
        for (DropParticle s : staticDrops) {
            int gx = clamp((int) (s.x / cell), 0, cols - 1);
            int gy = clamp((int) (s.y / cell), 0, rows - 1);
            int idx = gy * cols + gx;
            if (grid[idx] == null) grid[idx] = new ArrayList<>(4);
            grid[idx].add(s);
        }

        Set<DropParticle> consumed = new HashSet<>();
        for (int i = 0; i < activeDrops.size(); i++) {
            DropParticle a = activeDrops.get(i);
            if (consumed.contains(a)) continue;
            int gx = clamp((int) (a.x / cell), 0, cols - 1);
            int gy = clamp((int) (a.y / cell), 0, rows - 1);
            for (int oy = -1; oy <= 1; oy++) {
                int yy = gy + oy;
                if (yy < 0 || yy >= rows) continue;
                for (int ox = -1; ox <= 1; ox++) {
                    int xx = gx + ox;
                    if (xx < 0 || xx >= cols) continue;
                    List<DropParticle> bucket = grid[yy * cols + xx];
                    if (bucket == null) continue;
                    for (DropParticle b : bucket) {
                        if (b == a || consumed.contains(b)) continue;
                        float dx = a.x - b.x;
                        float dy = a.y - b.y;
                        float d2 = dx * dx + dy * dy;
                        float rr = a.radius + b.radius;
                        if (d2 <= rr * rr * 0.9f) {
                            // 质量守恒 R = cbrt(R1^3 + R2^3)
                            float newMass = a.mass + b.mass;
                            float newRadius = (float) Math.cbrt(newMass);
                            float newVx = (a.mass * a.vx + b.mass * b.vx) / newMass;
                            float newVy = (a.mass * a.vy + b.mass * b.vy) / newMass;
                            float newX = (a.mass * a.x + b.mass * b.x) / newMass;
                            float newY = (a.mass * a.y + b.mass * b.y) / newMass;
                            a.x = newX;
                            a.y = newY;
                            a.radius = newRadius;
                            a.mass = newMass;
                            a.vx = newVx;
                            a.vy = newVy;
                            a.isStatic = false;
                            consumed.add(b);
                        }
                    }
                }
            }
        }

        if (!consumed.isEmpty()) {
            for (int i = activeDrops.size() - 1; i >= 0; i--) {
                DropParticle p = activeDrops.get(i);
                if (consumed.contains(p)) {
                    activeDrops.remove(i);
                    recycle(p);
                }
            }
            for (int i = staticDrops.size() - 1; i >= 0; i--) {
                DropParticle p = staticDrops.get(i);
                if (consumed.contains(p)) {
                    staticDrops.remove(i);
                    recycle(p);
                }
            }
        }

        // 如果合并后的水滴 radius 大于某阈值或速度足够，确保它在 active 列表
        for (DropParticle a : activeDrops) {
            a.isStatic = false;
        }
    }

    private void trimStaticDropsIfNeeded(long now) {
        if (staticDrops.size() <= MAX_STATIC) return;
        int overflow = staticDrops.size() - MAX_STATIC;
        // 简单选择：淘汰最旧最小者
        for (int k = 0; k < overflow; k++) {
            int idx = 0;
            float worst = Float.MAX_VALUE;
            for (int i = 0; i < staticDrops.size(); i++) {
                DropParticle p = staticDrops.get(i);
                float score = p.radius * 1000f + (now - p.bornAt) * 0.001f;
                if (score < worst) {
                    worst = score;
                    idx = i;
                }
            }
            DropParticle p = staticDrops.remove(idx);
            recycle(p);
        }
    }

    private DropParticle obtain() {
        for (int i = 0; i < pool.length; i++) {
            DropParticle p = pool[i];
            if (!p.active) {
                p.active = true;
                p.isStatic = false;
                p.vx = 0f;
                p.vy = 0f;
                return p;
            }
        }
        return null;
    }

    private void recycle(DropParticle p) {
        if (p == null) return;
        p.active = false;
        p.isStatic = false;
        p.vx = 0f;
        p.vy = 0f;
    }

    private void recycleAll() {
        for (DropParticle p : activeDrops) {
            recycle(p);
        }
        activeDrops.clear();
        for (DropParticle p : staticDrops) {
            recycle(p);
        }
        staticDrops.clear();
    }

    private int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!rainEnabled) return;

        if (atlasDirty || dropAtlas == null) {
            buildDropAtlas();
        }

        List<DropParticle> renderStatic;
        List<DropParticle> renderActive;
        List<Ripple> renderRipples;
        List<Streak> renderStreaks;
        synchronized (physicsLock) {
            renderStatic = new ArrayList<>(staticDrops);
            renderActive = new ArrayList<>(activeDrops);
            renderRipples = new ArrayList<>(ripples);
            renderStreaks = new ArrayList<>(streaks);
        }

        // 1) 轻雾层
        mistPaint.setColor(colorMist);
        canvas.drawRect(0, 0, getWidth(), getHeight(), mistPaint);

        // 2) streak 现不再发射，但保留可选的封装供后期需要时扩展

        // 3) 涟漪
        for (int i = 0, n = renderRipples.size(); i < n; i++) {
            Ripple r = renderRipples.get(i);
            int alpha = (int) (Color.alpha(colorRipple) * Math.max(0f, r.alpha));
            ripplePaint.setColor(colorRipple);
            ripplePaint.setAlpha(alpha);
            ripplePaint.setStrokeWidth(1.4f * density);
            canvas.drawCircle(r.x, r.y, r.radius, ripplePaint);
            ripplePaint.setAlpha(Math.max(0, alpha / 2));
            canvas.drawCircle(r.x, r.y, r.radius * 0.6f, ripplePaint);
        }

        // 4) 静态附着小水滴
        for (int i = 0, n = renderStatic.size(); i < n; i++) {
            drawDrop(canvas, renderStatic.get(i), true);
        }
        // 5) 动态水滴（在最上层）
        for (int i = 0, n = renderActive.size(); i < n; i++) {
            drawDrop(canvas, renderActive.get(i), false);
        }
    }

    private void drawDrop(Canvas canvas, DropParticle p, boolean staticDrop) {
        float r = p.radius;
        if (r <= 0.5f || dropAtlas == null) return;

        if (staticDrop) {
            // 静态水滴：周身圆润，不拉伸
            dropDst.set(p.x - r, p.y - r, p.x + r, p.y + r);
            atlasPaint.setAlpha(220);
            canvas.drawBitmap(dropAtlas, null, dropDst, atlasPaint);
            return;
        }

        float vx = p.vx;
        float vy = p.vy;
        float vsum2 = vx * vx + vy * vy;
        if (vsum2 < 60f * 60f) {
            // 几乎静止：不拉伸
            dropDst.set(p.x - r, p.y - r, p.x + r, p.y + r);
            atlasPaint.setAlpha(240);
            canvas.drawBitmap(dropAtlas, null, dropDst, atlasPaint);
            return;
        }

        float vsum = (float) Math.sqrt(vsum2);
        float invV = 1f / vsum;
        float dirX = vx * invV;
        float dirY = vy * invV;

        // “小彗星”拖尾：头部圆珠 + 沿 −速度方向几个递减的残留珠。
        // 拖尾数量及总长随速度增加，但控制上限防止拖出屏或成本过高。
        int trailCount = vsum > 360f ? 3 : (vsum > 180f ? 2 : 1);
        for (int t = trailCount; t >= 1; t--) {
            float trailDist = r * 0.85f * t;
            float trailR = r * (1f - 0.22f * t);
            if (trailR < 1.2f * density) continue;
            float tx = p.x - dirX * trailDist;
            float ty = p.y - dirY * trailDist;
            dropDst.set(tx - trailR, ty - trailR, tx + trailR, ty + trailR);
            atlasPaint.setAlpha(Math.max(60, 200 - 45 * t));
            canvas.drawBitmap(dropAtlas, null, dropDst, atlasPaint);
        }

        // 头部主珠：沿运动方向极轻微拉伸（低于 1.15x），保持珠状观感
        float stretch = Math.min(0.14f, vsum / 1500f);
        float rx = r * (1f + stretch * Math.abs(dirX));
        float ry = r * (1f + stretch * Math.abs(dirY));
        dropDst.set(p.x - rx, p.y - ry, p.x + rx, p.y + ry);
        atlasPaint.setAlpha(240);
        canvas.drawBitmap(dropAtlas, null, dropDst, atlasPaint);
    }

    /**
     * 由 Activity 的 dispatchTouchEvent 调用：把触摸坐标转发给所有可见的 RaindropFxView。
     * 用遍历而不是 findViewById，能正确支持 ViewPager 中多个 Fragment 共用同一 id 的场景。
     */
    public static void dispatchToVisible(View root, MotionEvent ev) {
        if (root == null || ev == null) return;
        if (root instanceof RaindropFxView) {
            if (root.getVisibility() == View.VISIBLE) {
                ((RaindropFxView) root).dispatchTouchSnapshot(ev);
            }
            return;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                dispatchToVisible(group.getChildAt(i), ev);
            }
        }
    }

    private static final class DropParticle {
        float x, y, radius;
        float vx, vy;
        float mass;
        boolean isStatic;
        boolean active;
        long bornAt;
    }

    private static final class Ripple {
        float x, y;
        float radius;
        float maxRadius;
        float alpha;
    }

    private static final class Streak {
        float x;
        float yStart;
        float yEnd;
        float width;
        float alpha;
    }
}
