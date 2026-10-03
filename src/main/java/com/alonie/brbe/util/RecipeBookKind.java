package com.alonie.brbe.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.recipebook.CraftingRecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.FurnaceRecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import org.jetbrains.annotations.Nullable;

/**
 * 当前配方书的**种类**——替代配方组浮层格子的渲染规则按它分派（用户 2026-09-27 诉求）。
 *
 * <p>三种书，三条规则：</p>
 *
 * <ul>
 *   <li>{@link #CRAFTING} 合成类（工作台 / 背包的 {@code CraftingRecipeBookComponent}）：
 *       <b>原样不动</b>——微缩配方 + 悬停完整预览的既有设计；</li>
 *   <li>{@link #FURNACE} 熔炉类（熔炉 / 高炉 / 烟熏炉的 {@code FurnaceRecipeBookComponent}）：
 *       格子的展示物品改用配方**材料**（输入），代替原版的微缩配方——同一组里各变体的
 *       产物完全相同，用产物根本分不出谁是谁，材料才是这一格的身份；</li>
 *   <li>{@link #OTHER} 熔炉类、合成类以外的配方书（模组为自己的功能方块定制的
 *       {@code RecipeBookComponent} 子类，含直接调用原版 API 拿到原版
 *       {@code OverlayRecipeComponent} 的那种）：<b>统一不画微缩配方</b>，只画展示物品
 *       ——与酿造台 / 锻造台已有的格子行为一致。</li>
 * </ul>
 *
 * <p><b>为什么按组件类判定</b>：26.3 原版只有 {@code CraftingRecipeBookComponent} 与
 * {@code FurnaceRecipeBookComponent} 两个具体子类，{@code RecipeBookComponent} 本身是
 * abstract——模组要自建配方书只能继承它，于是"既不是合成类也不是熔炉类"就等于"模组自建"。
 * 组件取自 {@link HoverGhostRecipe#currentBook()}（浮层自己没有指回组件的引用）。</p>
 */
public enum RecipeBookKind {
    CRAFTING,
    FURNACE,
    OTHER;

    /**
     * 由配方书组件判定种类（{@code null} = 拿不到组件）。
     *
     * <p>兜底顺序：组件类 → 界面所属菜单（模组把配方书嵌在非
     * {@code AbstractRecipeBookScreen} 的自定义界面里时拿不到组件）→ 浮层自带的
     * {@code isFurnaceMenu} 标记。</p>
     */
    public static RecipeBookKind of(@Nullable RecipeBookComponent<?> book, boolean overlayIsFurnaceMenu) {
        if (book instanceof FurnaceRecipeBookComponent) {
            return FURNACE;
        }
        if (book instanceof CraftingRecipeBookComponent) {
            return CRAFTING;
        }
        if (book != null) {
            return OTHER;
        }
        Screen screen = Minecraft.getInstance().gui.screen();
        if (screen instanceof AbstractContainerScreen<?> containerScreen) {
            if (containerScreen.getMenu() instanceof AbstractFurnaceMenu) {
                return FURNACE;
            }
            if (containerScreen.getMenu() instanceof AbstractCraftingMenu) {
                return CRAFTING;
            }
            return OTHER; // 模组机器界面：有配方书浮层但不是原版那两种书
        }
        return overlayIsFurnaceMenu ? FURNACE : CRAFTING;
    }

    /** 当前界面所属配方书的种类（{@code overlayIsFurnaceMenu} = 浮层的原版熔炉标记）。 */
    public static RecipeBookKind current(boolean overlayIsFurnaceMenu) {
        return of(HoverGhostRecipe.currentBook(), overlayIsFurnaceMenu);
    }

    /** 这一种书是否还画微缩配方（只有合成类保留）。 */
    public boolean showsMicroRecipe() {
        return this == CRAFTING;
    }
}
