package com.alonie.brbe.util;

/**
 * 管线输入的**单一失效纪元**（单调自增计数器）。
 *
 * <p><b>为什么需要它</b>：管线输出缓存的键必须覆盖管线的全部输入。旧实现是**在读者侧**
 * 手写一串代理量（{@code inventoryUnchanged()}、索引 generation、残缺标记修订号、pin 版本…），
 * 而真正改写状态的是**另一处的写者**（约 25 个写点、两个 tagger、8 个文件）—— 任何
 * "写者动了、代理量恰好没动"的组合都会变成一次静默的错误命中。三轮缓存 Bug
 * （搜索词 / {@code isFiltering} / 残缺标记重算）都是同一个失败模式。
 *
 * <p><b>契约</b>：凡是会改变**管线输入**（集合列表、集合的 craftable/selected、
 * 残缺/不兼容标记、配置、pin、搜索、过滤）的**生产者**代码，改完就 {@link #bump()}。
 * 管线**内部**（Stage 6b 的注入/提升，跑在缓存块之后、且只写每次重建的合成组）**不要**
 * bump —— 否则每次调用都自失效、缓存永不命中。
 *
 * <p>本类只是**快路径**：真正的兜底是
 * {@link PartialCraftingUtil#pipelineStateHash(java.util.List)}（直接哈希输入数据的实际内容）。
 * 漏 bump 时它仍会发现状态不一致；两者都漏才会错，而那种情况已由
 * {@code -Dbrbe.diag=true} 的自检日志当场抓出来。
 */
public final class PipelineEpoch {

    private static int epoch;

    private PipelineEpoch() {
    }

    /** 管线输入变过一次（生产者侧调用）。 */
    public static void bump() {
        epoch++;
    }

    /** 当前纪元（管线输出缓存的键分量之一）。 */
    public static int current() {
        return epoch;
    }
}
