package com.alonie.brbe.interfaces;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 配方书**替代配方组浮层格子**的 tooltip 提供者（用户 2026-09-27 诉求：组里的配方要"像普通
 * 配方那样"有 tooltip）。
 *
 * <p>由 {@code alternativerecipes/OverlayRecipeButtonMixin} 实现在原版格子类上（合成台 /
 * 熔炉系的浮层）；{@code RecipeBookPage.extractTooltip} 里查询被悬停的格子并取出行内容——
 * 那里是原版配方书 tooltip 的正规出口（容器槽位 tooltip 早于它注册，会被它盖掉）。
 * 锻造台/酿造台的自研浮层不走这里，它们在自己的页面里实现 {@code overlayTooltip()}。</p>
 */
public interface IOverlayCellTooltip {

    /**
     * 这一格的 tooltip 行；{@code null} = 这一格当前没有可显示的内容
     * （查询窗口 / pin 的浮层格子、或产物解不出来的格子）。
     */
    @Nullable
    List<Component> brbe$cellTooltip();
}
