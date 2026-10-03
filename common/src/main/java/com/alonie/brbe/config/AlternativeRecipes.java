package com.alonie.brbe.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "alternativeRecipes")
public class AlternativeRecipes implements ConfigData {
    @ConfigEntry.Gui.Tooltip()
    public boolean onHover = true;
    /**
     * **「拆散替代配方组」**（用户 2026-10-03：类型由布尔改为枚举）：
     * {@link SplitMode#FULL} 完全（原 {@code noGrouped=true}，把所有替代配方组拆成单配方格）·
     * {@link SplitMode#SELECTIVE} 选择性（原 {@code false}，默认）· {@link SplitMode#OFF} 关闭。
     *
     * <p>⚠️ 1.21.1 **没有** 26.x / 1.21.11 的「按排序原因剥离」（Stage 2.5）——组内变体从来不按状态
     * 拆出去，所以本分支上「选择性」与「关闭」行为相同；保留 OFF 档只为跨分支配置与界面一致。</p>
     *
     * <p>旧 TOML 布尔键 {@code noGrouped} 由配置加载前的
     * {@code migrateLegacyConfigValuesInToml()} 迁移成 {@code splitMode = "FULL"|"SELECTIVE"}。</p>
     */
    @ConfigEntry.Gui.Tooltip(count = 3)
    // 三行说明（排版对齐「标签模式」）：…@Tooltip[0] / [1] / [2]（不是换行符）。
    // ⚠️ 必需：没有它 AutoConfig 生成的是候选只剩当前档位的下拉框。
    @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
    public SplitMode splitMode = SplitMode.SELECTIVE;

    /** 「拆散替代配方组」的**三档**。档位名走
     *  {@code text.autoconfig.brbe.option.alternativeRecipes.splitMode.<常量名>}。 */
    public enum SplitMode implements me.shedaniel.clothconfig2.gui.entries.SelectionListEntry.Translatable {
        /** 完全：所有替代配方组拆成单配方格（原布尔 {@code noGrouped=true}）。 */
        FULL,
        /** 选择性：原布尔 {@code =false}（本分支与「关闭」行为相同，见类注释）。 */
        SELECTIVE,
        /** 关闭：不做任何拆散（本分支与「选择性」相同）。 */
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

    /** 选择性拆散是否启用（1.21.1 无 Stage 2.5，「关闭」档在本分支上没有额外效果）。 */
    public boolean selectiveSplitEnabled() {
        return splitMode == SplitMode.SELECTIVE;
    }

    /**
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
     * <p>「拆散替代配方组」（{@link #splitMode}）与本项语义相反：**「完全」档优先**（该档开启时
     *  本项不执行；「选择性 / 关闭」两档不影响本项）。</p>
     */
    @ConfigEntry.Gui.Tooltip()
    public boolean mergeSameResult = false;
}
