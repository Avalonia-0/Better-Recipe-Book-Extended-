package com.alonie.brbe.util;

import net.minecraft.client.gui.components.AbstractWidget;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 翻页箭头登记表。
 *
 * <p>配方书（合成台原版书 + BRBE 自研的酿造台/锻造台书）的「上一页 / 下一页」箭头都是
 * 普通 widget，被按下时 vanilla 会在 {@code AbstractWidget.mouseClicked} 里播放**原版
 * 按钮点击声**；而 BRBE 的其它翻页路径（滚轮、查询窗口箭头、RBIP 标签条）播的是
 * <b>「翻页音效」</b>（配置项 {@code pageFlipSound} + 「音效音量」）。两者混在一起时，
 * 同一个"翻页"动作声音不一致（用户 2026-09-25 反馈）。</p>
 *
 * <p>这里登记"哪些 widget 是翻页箭头"，由
 * {@code mixins/scrollablepages/PageTurnArrowSoundMixin} 在 {@code playDownSound}
 * 处把原版点击声换成分页用的翻页音效——<b>单一判定点 + 单一替换点</b>，四处箭头点击
 * 路径（原版 click、BRBE 的 scrollAround 拦截、Ctrl+跳页、BRBE 自研书的翻页）自动统一。</p>
 *
 * <p>键用 {@link WeakHashMap}：书/页面被丢弃后条目自动消失，不需要手动反注册。</p>
 */
public final class PageTurnArrows {

    private static final Set<AbstractWidget> ARROWS =
            Collections.newSetFromMap(new WeakHashMap<>());

    private PageTurnArrows() {
    }

    /** 登记翻页箭头（可传 null，安全忽略；重复登记无副作用）。 */
    public static void register(AbstractWidget... arrows) {
        if (arrows == null) return;
        for (AbstractWidget arrow : arrows) {
            if (arrow != null) ARROWS.add(arrow);
        }
    }

    /** 该 widget 是否是翻页箭头（决定按下时播翻页音效还是原版点击声）。 */
    public static boolean isPageArrow(AbstractWidget widget) {
        return widget != null && ARROWS.contains(widget);
    }
}
