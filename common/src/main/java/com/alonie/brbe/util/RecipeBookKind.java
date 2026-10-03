package com.alonie.brbe.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.AbstractFurnaceRecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.jetbrains.annotations.Nullable;

/**
 * 当前配方书的**种类**——替代配方组浮层格子的渲染规则按它分派（用户 2026-09-27 诉求）。
 *
 * <p>三种书，三条规则：</p>
 *
 * <ul>
 *   <li>{@link #CRAFTING} 合成类（工作台 / 背包的原版合成书）：<b>原样不动</b>
 *       ——微缩配方 + 悬停完整预览的既有设计；</li>
 *   <li>{@link #FURNACE} 熔炉类（熔炉 / 高炉 / 烟熏炉的
 *       {@code AbstractFurnaceRecipeBookComponent}）：格子的展示物品改用配方**材料**（输入），
 *       代替原版的微缩配方——同一组里各变体的产物完全相同，用产物根本分不出谁是谁；</li>
 *   <li>{@link #OTHER} 熔炉类、合成类以外的配方书（模组为自己的功能方块定制的配方书，
 *       含直接调用原版 API 拿到原版 {@code OverlayRecipeComponent} 的那种）：
 *       <b>统一不画微缩配方</b>，只画展示物品——与酿造台 / 锻造台已有的格子行为一致。</li>
 * </ul>
 *
 * <p><b>1.21.1 的判定方式</b>：本分支没有 1.21.5+ 的 {@code CraftingRecipeBookComponent}
 * 类（合成书直接由 {@code RecipeBookComponent} 本体承担），所以熔炉类按基类
 * {@code AbstractFurnaceRecipeBookComponent} 判、合成类按**原版合成界面**
 * （{@code CraftingScreen} / {@code InventoryScreen}）判；其余一律算"模组自建"。
 * 组件取自 {@link HoverGhostRecipe#currentBook()}（浮层自己没有指回组件的引用）。</p>
 */
public enum RecipeBookKind {
    CRAFTING,
    FURNACE,
    OTHER;

    /**
     * 由配方书组件判定种类（{@code null} = 拿不到组件）。
     *
     * <p>兜底顺序：组件类 → 当前界面（原版合成界面 = 合成类；其余带配方书组件的界面 =
     * 模组自建）→ 浮层自带的 {@code isFurnaceMenu} 标记。</p>
     */
    public static RecipeBookKind of(@Nullable RecipeBookComponent book, boolean overlayIsFurnaceMenu) {
        if (book instanceof AbstractFurnaceRecipeBookComponent) {
            return FURNACE;
        }
        // 1.21.1 的当前界面是字段
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof CraftingScreen || screen instanceof InventoryScreen) {
            return CRAFTING;
        }
        if (book != null) {
            return OTHER; // 有配方书组件但不是原版那两种书 = 模组自建
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
