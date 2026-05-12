package com.example.koukou.theme;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;

import com.example.koukou.R;
import com.example.koukou.ui.settings.model.SettingsState;

/**
 * 统一主题样式容器：每个"可切换主题"对应一份 {@link ThemePalette}，保存该主题下所有可见组件
 * 的语义化样式（文字色、图标色、背景 drawable 等）。业务代码只读取 palette 字段，不再做
 * {@code "matrix".equals(bg) ? "#A9C8BE" : ...} 的散落分支。
 *
 * <p>新增主题 → 增加一个常量 {@code MY_NEW_THEME} 并在 {@link #forState(SettingsState)} 的
 * switch 中登记；新增组件槽位 → 添加一个字段并在所有常量中补值。</p>
 *
 * <p>字段使用 {@code @ColorInt} / {@code @DrawableRes} 标注，但有意保留为 public 非 final，
 * 以便 palette 工厂用"命名赋值"的方式编写（可读性 &gt; 严格不可变）。palette 常量一旦初始化
 * 不应被修改。</p>
 */
public final class ThemePalette {
    // ---------- 文字 ----------
    @ColorInt public int textPrimary;        // 主标题、条目名、昵称
    @ColorInt public int textSecondary;      // 副文本、签名、最后一条消息、空态
    @ColorInt public int textTertiary;       // 时间、弱化说明
    @ColorInt public int textAccent;         // 账号、强调字段

    // ---------- 图标 ----------
    @ColorInt public int iconPrimary;        // 工具栏图标、返回键等
    @ColorInt public int iconAccent;         // 设置项图标强调色
    @ColorInt public int iconMuted;          // 次级图标

    // ---------- 线条 / 分隔 ----------
    @ColorInt public int cardStroke;         // 信息卡片描边

    // ---------- 背景 drawable ----------
    @DrawableRes public int bgGlassCard;     // 大型信息卡片（个人资料等）
    @DrawableRes public int bgListCard;      // 列表条目（会话、联系人）
    @DrawableRes public int bgSettingsRow;   // 设置项行背景
    @DrawableRes public int bgSettingsActionRow; // 设置项操作（退出/危险）行背景
    @DrawableRes public int bgPanel;         // 小型按钮/图标面板（添加好友图标、返回按钮）
    @DrawableRes public int bgBottomNav;     // 底部导航条背景
    @DrawableRes public int bgUnreadBadge;   // 未读红点
    @DrawableRes public int bgInputBar;      // 聊天输入条常态
    @DrawableRes public int bgInputBarActive;// 聊天输入条激活态（无特殊态的主题可复用 bgInputBar）

    // ---------- 控件 ----------
    @ColorInt public int switchOffTrack;     // Switch 关闭态轨道
    @ColorInt public int switchOffThumb;     // Switch 关闭态滑块
    public int switchActiveAlpha;            // 激活态轨道使用 cyan 的 alpha（0~255）
    public float switchElevation;            // Switch elevation

    // ---------- 导航 ----------
    @ColorInt public int navBadgeColor;      // 主导航 badge 背景色；0 表示使用默认 cyan
    @ColorInt public int navInactive;        // 底部导航未选中项颜色
    @ColorInt public int navSelected;        // 底部导航选中项颜色

    // ---------- 元信息 ----------
    public boolean lightPalette;             // 是否为"浅色"皮（用于状态栏 / 导航栏 icon 反色）

    private ThemePalette() {
    }

    /**
     * 根据设置状态挑选对应 palette。null / 未知主题回退到蝴蝶流光。
     */
    public static ThemePalette forState(SettingsState state) {
        String bg = state == null || state.chatBackground == null ? "butterfly" : state.chatBackground;
        switch (bg) {
            case "pink_bunny":   return BUNNY;
            case "minimal_white":return MINIMAL_WHITE;
            case "matrix":       return MATRIX;
            case "stardust":     return STARDUST;
            case "cyber":        return CYBER;
            case "raindrop":     return RAINDROP;
            case "minimal":      return MINIMAL;
            case "butterfly":
            default:             return BUTTERFLY;
        }
    }

    // =========================================================================================
    // 各主题调色板。字段顺序严格一致，方便新增字段时用多文件搜索逐一补齐。
    // =========================================================================================

    public static final ThemePalette BUTTERFLY = butterfly();
    public static final ThemePalette CYBER = cyber();
    public static final ThemePalette RAINDROP = raindrop();
    public static final ThemePalette MINIMAL = minimalDark();
    public static final ThemePalette MATRIX = matrix();
    public static final ThemePalette STARDUST = stardust();
    public static final ThemePalette MINIMAL_WHITE = minimalWhite();
    public static final ThemePalette BUNNY = bunny();

    private static ThemePalette butterfly() {
        ThemePalette p = new ThemePalette();
        p.textPrimary = 0xFFF3F6FC;
        p.textSecondary = 0xFFB9C2D5;
        p.textTertiary = 0xFF7F8AA3;
        p.textAccent = 0xFF00E6FF;
        p.iconPrimary = 0xFFF3F6FC;
        p.iconAccent = 0xFF00E6FF;
        p.iconMuted = 0xFF7F8AA3;
        p.cardStroke = 0x6C96DFFF;
        p.bgGlassCard = R.drawable.bg_butterfly_glass_card;
        p.bgListCard = R.drawable.bg_butterfly_list_card;
        p.bgSettingsRow = R.drawable.bg_settings_row;
        p.bgSettingsActionRow = R.drawable.bg_settings_action_row;
        p.bgPanel = R.drawable.bg_butterfly_panel;
        p.bgBottomNav = R.drawable.bg_butterfly_bottom_nav;
        p.bgUnreadBadge = R.drawable.bg_button_gradient_20;
        p.bgInputBar = R.drawable.bg_butterfly_input_bar;
        p.bgInputBarActive = R.drawable.bg_butterfly_input_bar;
        p.switchOffTrack = 0xFF202845;
        p.switchOffThumb = 0xFFA6B0C7;
        p.switchActiveAlpha = 104;
        p.switchElevation = 2f;
        p.navBadgeColor = 0;
        p.navInactive = 0xFF7F8AA3;
        p.navSelected = 0xFFF3F6FC;
        p.lightPalette = false;
        return p;
    }

    private static ThemePalette cyber() {
        // 电子科幻：暗底冷色，沿用蝴蝶的组件风格，无特殊 drawable。
        return butterfly();
    }

    private static ThemePalette raindrop() {
        // 互动雨滴：暗玻璃幕，沿用蝴蝶的组件风格。
        return butterfly();
    }

    private static ThemePalette minimalDark() {
        ThemePalette p = butterfly();
        // 极简暗调：整体沿用蝴蝶，但 Switch 关闭态更柔和、elevation 更高。
        ThemePalette q = new ThemePalette();
        q.textPrimary = p.textPrimary;
        q.textSecondary = p.textSecondary;
        q.textTertiary = p.textTertiary;
        q.textAccent = p.textAccent;
        q.iconPrimary = p.iconPrimary;
        q.iconAccent = 0xFF96B5D9; // stroke 风格
        q.iconMuted = p.iconMuted;
        q.cardStroke = p.cardStroke;
        q.bgGlassCard = p.bgGlassCard;
        q.bgListCard = p.bgListCard;
        q.bgSettingsRow = p.bgSettingsRow;
        q.bgSettingsActionRow = p.bgSettingsActionRow;
        q.bgPanel = p.bgPanel;
        q.bgBottomNav = p.bgBottomNav;
        q.bgUnreadBadge = p.bgUnreadBadge;
        q.bgInputBar = p.bgInputBar;
        q.bgInputBarActive = p.bgInputBarActive;
        q.switchOffTrack = 0xFF26334A;
        q.switchOffThumb = 0xFF9EB4C8;
        q.switchActiveAlpha = 132;
        q.switchElevation = 8f;
        q.navBadgeColor = 0;
        q.navInactive = p.navInactive;
        q.navSelected = p.navSelected;
        q.lightPalette = false;
        return q;
    }

    private static ThemePalette matrix() {
        ThemePalette p = new ThemePalette();
        p.textPrimary = 0xFFF3F6FC;
        p.textSecondary = 0xFFA9C8BE;
        p.textTertiary = 0xFF7F8AA3;
        p.textAccent = 0xFF86F7D7;
        p.iconPrimary = 0xFFF3F6FC;
        p.iconAccent = 0xFF86F7D7;
        p.iconMuted = 0xFF7F8AA3;
        p.cardStroke = 0x4F86F7D7;
        p.bgGlassCard = R.drawable.bg_butterfly_glass_card_matrix;
        p.bgListCard = R.drawable.bg_butterfly_list_card;
        p.bgSettingsRow = R.drawable.bg_settings_row_matrix;
        p.bgSettingsActionRow = R.drawable.bg_settings_action_row_matrix;
        p.bgPanel = R.drawable.bg_butterfly_panel_matrix;
        p.bgBottomNav = R.drawable.bg_butterfly_bottom_nav;
        p.bgUnreadBadge = R.drawable.bg_button_gradient_20;
        p.bgInputBar = R.drawable.bg_butterfly_input_bar;
        p.bgInputBarActive = R.drawable.bg_butterfly_input_bar;
        p.switchOffTrack = 0xFF20362F;
        p.switchOffThumb = 0xFF99B8AE;
        p.switchActiveAlpha = 104;
        p.switchElevation = 2f;
        p.navBadgeColor = 0;
        p.navInactive = 0xFF7F8AA3;
        p.navSelected = 0xFFF3F6FC;
        p.lightPalette = false;
        return p;
    }

    private static ThemePalette stardust() {
        ThemePalette p = new ThemePalette();
        p.textPrimary = 0xFFF3F6FC;
        p.textSecondary = 0xFFC6D4EA;
        p.textTertiary = 0xFF7F8AA3;
        p.textAccent = 0xFF8FF4FF;
        p.iconPrimary = 0xFFF3F6FC;
        p.iconAccent = 0xFF00E6FF;
        p.iconMuted = 0xFF7F8AA3;
        p.cardStroke = 0x5ADCF6FF;
        p.bgGlassCard = R.drawable.bg_butterfly_glass_card_stardust;
        p.bgListCard = R.drawable.bg_butterfly_list_card;
        p.bgSettingsRow = R.drawable.bg_settings_row_stardust;
        p.bgSettingsActionRow = R.drawable.bg_settings_action_row_stardust;
        p.bgPanel = R.drawable.bg_butterfly_panel_stardust;
        p.bgBottomNav = R.drawable.bg_butterfly_bottom_nav_stardust;
        p.bgUnreadBadge = R.drawable.bg_button_gradient_20;
        p.bgInputBar = R.drawable.bg_butterfly_input_bar_stardust;
        p.bgInputBarActive = R.drawable.bg_butterfly_input_bar_stardust_active;
        p.switchOffTrack = 0xFF1A2338;
        p.switchOffThumb = 0xFFD5E8F8;
        p.switchActiveAlpha = 138;
        p.switchElevation = 6f;
        p.navBadgeColor = 0;
        p.navInactive = 0xFF7F8AA3;
        p.navSelected = 0xFFF3F6FC;
        p.lightPalette = false;
        return p;
    }

    private static ThemePalette minimalWhite() {
        ThemePalette p = new ThemePalette();
        p.textPrimary = 0xFF162131;
        p.textSecondary = 0xFF6A778C;
        p.textTertiary = 0xFF8A96A8;
        p.textAccent = 0xFF0B9FB5;
        p.iconPrimary = 0xFF162131;
        p.iconAccent = 0xFF0B9FB5;
        p.iconMuted = 0xFF8A96A8;
        p.cardStroke = 0x66C3D3E2;
        p.bgGlassCard = R.drawable.bg_butterfly_glass_card_light;
        p.bgListCard = R.drawable.bg_butterfly_list_card_light;
        p.bgSettingsRow = R.drawable.bg_settings_row_light;
        p.bgSettingsActionRow = R.drawable.bg_settings_action_row_light;
        p.bgPanel = R.drawable.bg_butterfly_panel_light;
        p.bgBottomNav = R.drawable.bg_butterfly_bottom_nav_light;
        p.bgUnreadBadge = R.drawable.bg_button_gradient_20;
        p.bgInputBar = R.drawable.bg_butterfly_input_bar;
        p.bgInputBarActive = R.drawable.bg_butterfly_input_bar;
        p.switchOffTrack = 0xFFC9D8E4;
        p.switchOffThumb = 0xFFEEF4F8;
        p.switchActiveAlpha = 132;
        p.switchElevation = 8f;
        p.navBadgeColor = 0;
        p.navInactive = 0xFF6A778C;
        p.navSelected = 0xFF162131;
        p.lightPalette = true;
        return p;
    }

    private static ThemePalette bunny() {
        ThemePalette p = new ThemePalette();
        p.textPrimary = 0xFF3B2430;
        p.textSecondary = 0xFF8F6578;
        p.textTertiary = 0xFFB8879D;
        p.textAccent = 0xFFD93D82;
        p.iconPrimary = 0xFFD93D82;
        p.iconAccent = 0xFFD93D82;
        p.iconMuted = 0xFFB8879D;
        p.cardStroke = 0x66C3D3E2;
        p.bgGlassCard = R.drawable.bg_butterfly_glass_card_light;
        p.bgListCard = R.drawable.bg_bunny_list_card;
        p.bgSettingsRow = R.drawable.bg_settings_row_light;
        p.bgSettingsActionRow = R.drawable.bg_settings_action_row_light;
        p.bgPanel = R.drawable.bg_bunny_panel;
        p.bgBottomNav = R.drawable.bg_bunny_bottom_nav;
        p.bgUnreadBadge = R.drawable.bg_bunny_badge;
        p.bgInputBar = R.drawable.bg_butterfly_input_bar;
        p.bgInputBarActive = R.drawable.bg_butterfly_input_bar;
        p.switchOffTrack = 0xFFE9D3DD;
        p.switchOffThumb = 0xFFFFF4F8;
        p.switchActiveAlpha = 132;
        p.switchElevation = 8f;
        p.navBadgeColor = 0xFFFF6FAE;
        p.navInactive = 0xFF9B7487;
        p.navSelected = 0xFFD93D82;
        p.lightPalette = true;
        return p;
    }
}
