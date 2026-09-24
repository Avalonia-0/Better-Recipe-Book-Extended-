package com.alonie.brbe.config;

import net.minecraft.client.Minecraft;

/**
 * 拼音搜索的「语言相关默认值」。
 *
 * <p>该配置项只在中文（zh_*）语言的配置界面里显示（见
 * {@link PinyinSearchGuiRegistrar}）：</p>
 * <ul>
 *   <li><b>显示时</b>（中文）默认<b>开启</b>；</li>
 *   <li><b>隐藏时</b>（其他语言）默认<b>关闭</b>（隐藏期间值无意义，启动时强制关闭）。</li>
 * </ul>
 *
 * <p>{@code BrbeConfig.pinyinSearch} 的字段默认值只能是常量（{@code false}），语言相关的
 * 默认值只能在运行时收敛。本类是这条语义的<b>唯一决策点</b>，两个调用方：</p>
 * <ol>
 *   <li>启动钩子（各分支 {@code BetterRecipeBookClientFabric} 的 CLIENT_STARTED）；</li>
 *   <li>{@code /brbe clear configchange}（{@link com.alonie.brbe.command.BrbeCommandActions}）——
 *       Cloth 的"恢复默认"用的是 POJO 字段默认值，若不在恢复后按语言收敛，中文会话下
 *       "恢复默认"会把拼音搜索关掉（与配置界面里该开关的默认值 {@code true} 不一致）。</li>
 * </ol>
 */
public final class PinyinSearchDefaults {

    private PinyinSearchDefaults() {
    }

    /** 语言是否为中文（zh_*）。{@code minecraft}/{@code options} 为 null 时按非中文处理。 */
    public static boolean isChineseLanguage(Minecraft minecraft) {
        if (minecraft == null || minecraft.options == null) return false;
        String code = minecraft.options.languageCode;
        return code != null && code.startsWith("zh");
    }

    /** 当前游戏语言（{@code Minecraft.getInstance()}）是否为中文。 */
    public static boolean isChineseLanguage() {
        return isChineseLanguage(Minecraft.getInstance());
    }

    /**
     * 把 {@code pinyinSearch} 收敛到当前语言下的默认值：中文 = 开，其他语言 = 关。
     *
     * <p>语言未知（{@code minecraft} 或 {@code options} 为 null）时<b>不改动</b>——宁可保留
     * 现值，也不要把"读不到语言"误判成非中文而关掉功能。</p>
     *
     * @return 是否发生了改动（调用方据此决定是否需要 {@code save()}）
     */
    public static boolean applyLanguageDefault(BrbeConfig config, Minecraft minecraft) {
        if (config == null || minecraft == null || minecraft.options == null) return false;
        boolean target = isChineseLanguage(minecraft);
        if (config.pinyinSearch == target) return false;
        config.pinyinSearch = target;
        return true;
    }
}
