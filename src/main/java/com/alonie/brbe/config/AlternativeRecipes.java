package com.alonie.brbe.config;

import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "alternativeRecipes")
public class AlternativeRecipes {
    /**
     * **「只在悬停时显示微缩配方」**（用户 2026-09-30 定的显示名）：
     * 开启时替代配方按钮平时只画产物图标，**悬停才展开**完整（微缩）配方预览。
     */
    @ConfigEntry.Gui.Tooltip()
    public boolean onHover = true;

    /**
     * **「拆散替代配方组」**（用户 2026-10-03：显示名由「完全拆散替代配方组」改来，类型由布尔改为枚举）：
     * {@link SplitMode#FULL} 完全（原 {@code noGrouped=true}）· {@link SplitMode#SELECTIVE} 选择性
     * （原 {@code false}，默认）· {@link SplitMode#OFF} 关闭（新增）。**默认「选择性」**。
     *
     * <ul>
     *   <li><b>完全</b>：把每个替代配方组拆成单配方格（见 {@code CollectionPipeline#applyUngroup} 与
     *       {@code mixins/ungroup/ClientRecipeBookMixin}），并且**优先于** {@link #mergeSameResult}
     *       ——两者同时启用时本档生效、收纳不执行。</li>
     *   <li><b>选择性</b>（2026-09-27 定稿的「按排序原因智能拆分」）：组内某个变体的状态
     *       （pin / 可合成 / 残缺）改变、或该变体被搜索命中时，把不同状态的变体剥出去参与排序，
     *       同状态变体仍留在一个组里（{@code CollectionPipeline#applySortExtraction}）。</li>
     *   <li><b>关闭</b>（用户 2026-10-03 定）：**连选择性拆散也不做** —— 替代配方组整体保持原样，
     *       组内变体在同一个按钮上轮循。代价：组内变体被 pin / 搜索命中时不再被单独拎出来。</li>
     * </ul>
     *
     * <p>旧 TOML 布尔键 {@code noGrouped} 由配置加载前的
     * {@code migrateLegacyConfigValuesInToml()} 迁移成 {@code splitMode = "FULL"|"SELECTIVE"}。</p>
     */
    @ConfigEntry.Gui.Tooltip(count = 3)
    // 三行说明（排版对齐「标签模式」）：…@Tooltip[0] / [1] / [2]（不是换行符）。
    // ⚠️ 必需（理由同 pageFlipDirection）：没有它 AutoConfig 生成的是候选只剩当前档位的下拉框。
    @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
    public SplitMode splitMode = SplitMode.SELECTIVE;

    /** 「拆散替代配方组」的**三档**。档位名走
     *  {@code text.autoconfig.brbe.option.alternativeRecipes.splitMode.<常量名>}。 */
    public enum SplitMode implements me.shedaniel.clothconfig2.gui.entries.SelectionListEntry.Translatable {
        /** 完全：所有替代配方组拆成单配方格（原布尔 {@code noGrouped=true}）。 */
        FULL,
        /** 选择性：按变体状态（pin / 可合成 / 残缺 / 搜索命中）拆散（原布尔 {@code =false}，默认）。 */
        SELECTIVE,
        /** 关闭：不做任何拆散，替代配方组保持原样。 */
        OFF;

        @Override
        public String getKey() {
            return "text.autoconfig.brbe.option.alternativeRecipes.splitMode." + name();
        }

        /** 完全拆散（原 {@code noGrouped=true}）。 */
        public boolean ungroupAll() {
            return this == FULL;
        }
    }

    /** 旧布尔 {@code noGrouped} 的读取点统一改用它（= 「完全」档）。 */
    public boolean noGrouped() {
        return splitMode == SplitMode.FULL;
    }

    /** 选择性拆散（Stage 2.5「按排序原因剥离」）是否启用 —— 「关闭」档不做任何拆散。 */
    public boolean selectiveSplitEnabled() {
        return splitMode == SplitMode.SELECTIVE;
    }

    /**
     * **「自动收纳同产物配方」**（用户 2026-09-30 定的显示名与默认值；原名「非常智能地收纳配方」）：
     * **默认开启**，tooltip「将相同产物的配方整理到一起。」。
     *
     * <p>⏸️ 开发状态：2026-09-30 起**暂停继续开发**（细节、7 条缺陷、实测数据与重启路线图见
     * {@code docs/同产物配方合并-开发过程记录.md}），但功能本身按用户指令**默认开启**。</p>
     *
     * 强制把**产物相同**的配方集中到一个**专用配方组**（用户 2026-09-28 定稿）。
     *
     * <p>有的模组给同一件物品写了好几套配方却没有共用 {@code group}，配方书里就会各占一格。
     * 开启后按"产物 = 物品 + 组件（**忽略数量**）"归并：</p>
     * <ul>
     *   <li><b>非混合格</b>（独立格 / 全同产物组）里的同产物配方 → **搬进**专用组
     *       （它就是唯一非混合格时原地当专用组，多个则并成一格）；</li>
     *   <li><b>混合配方组</b>（一个组多种产物，如木板/栅栏）里的同产物配方 → **复制**一份进专用组，
     *       **原组一条不动**（保留原有秩序）；</li>
     *   <li>产物只出现在一个格子里 → 不做处理。</li>
     * </ul>
     *
     * <p>复制出来的两份在配方**脱离父组**（pin / 可合成 / 残缺剥离）时会合二为一，搜索时也不会
     * 重复显示。见 {@code CollectionPipeline#applyResultMerge}。</p>
     *
     * <p><b>收纳格不特殊</b>（用户 2026-09-30 指令）：格内某个变体的状态改变时，照常按 09-27 定稿的
     * 「按排序原因智能拆分」被剥出去参与排序——不做"整格绑在一起"的特例。</p>
     *
     * <p>「拆散替代配方组」（{@link #splitMode}）与本项语义相反：**「完全」档优先**（该档开启时
     *  本项不执行；「选择性 / 关闭」两档不影响本项）。</p>
     */
    @ConfigEntry.Gui.Tooltip()
    public boolean mergeSameResult = true;
}
