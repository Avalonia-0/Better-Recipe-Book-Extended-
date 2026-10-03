package com.alonie.brbe.smithingtable;

import com.alonie.brbe.generic.GenericRecipeButton;
import com.alonie.brbe.generic.GenericRecipePage;
import com.alonie.brbe.recipe.BRBSmithingRecipe;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.inventory.SmithingMenu;

import java.util.function.Supplier;

public class SmithingRecipeBookPage extends GenericRecipePage<SmithingMenu, SmithingRecipeCollection, BRBSmithingRecipe> {
    public final SmithingOverlayRecipeComponent overlay = new SmithingOverlayRecipeComponent();

    public SmithingRecipeBookPage(RegistryAccess registryAccess, Supplier<Boolean> filteringSupplier) {
        // SmithingRecipeButton：纹饰组的折叠单元格显示该组的纹饰模板（而不是轮循各件装备）
        super(registryAccess, () -> new SmithingRecipeButton(registryAccess, filteringSupplier));
    }

    @Override
    protected boolean overlayMouseClicked(double mouseX, double mouseY, int button, int j, int k, int l, int m) {
        if (this.overlay.mouseClicked(mouseX, mouseY, button)) {
            this.lastClickedRecipe = this.overlay.getLastRecipeClicked();
            this.lastClickedRecipeCollection = this.overlay.getRecipeCollection();
        } else {
            this.overlay.setVisible(false);
        }
        return true;
    }

    @Override
    protected void initOverlay(SmithingRecipeCollection recipeCollection, int anchorX, int anchorY,
                               int x, int y, RegistryAccess registryAccess) {
        // 位置按**被点击的组按钮** + 书面板原点算（原版规则，见 AlternativeOverlayLayout#placeBox）
        this.overlay.init(recipeCollection, anchorX, anchorY, this.parentLeft, this.parentTop, registryAccess);
    }

    /**
     * pin 切换后结果集重建：就地刷新浮层内容，**位置与页码保持**（用户 2026-09-27 三次反馈：
     * 初版直接重新 init → 浮层跳回第 1 页、并跟着被重排的格子乱跑）。
     */
    @Override
    protected void refreshOverlay(SmithingRecipeCollection recipeCollection, int anchorX, int anchorY) {
        this.overlay.refresh(recipeCollection, anchorX, anchorY, this.parentLeft, this.parentTop, this.registryAccess);
    }

    @Override
    public void render(GuiGraphics gui, int x, int y, int mouseX, int mouseY, float delta) {
        super.render(gui, x, y, mouseX, mouseY, delta);

        gui.nextStratum();
        this.overlay.render(gui, mouseX, mouseY, delta);

        // 组浮层内的悬停变体补进页面的 hovered* 状态：浮层盖在网格上，网格按钮不再
        // 报告悬停，于是「自动填充幽灵配方」在组内配方上失效（用户 2026-09-25 反馈：
        // 锻造台替代配方组里的配方无法触发自动填充幽灵配方）。组件在其后读
        // hoveredRecipe 决定是否写幽灵。
        SmithingOverlayRecipeComponent.OverlayRecipeButton overlayHovered = this.overlay.hoveredButton();
        if (overlayHovered != null) {
            this.hoveredRecipe = overlayHovered.getRecipe();
            this.hoveredCategory = this.category;
            this.hoverGhostRecipe = this.hoveredRecipe;
        }
    }

    @Override
    public boolean overlayIsVisible() {
        return this.overlay.isVisible();
    }

    /**
     * 浮层里被悬停的那一格的 tooltip（用户 2026-09-27 诉求）：格子由浮层自己画，
     * 网格的 tooltip 通路在浮层打开时被关掉，所以这里单独给出。
     */
    @Override
    public java.util.List<net.minecraft.network.chat.Component> overlayTooltip() {
        SmithingOverlayRecipeComponent.OverlayRecipeButton hovered = this.overlay.hoveredButton();
        return hovered == null ? null : hovered.getTooltipText();
    }

    /** 浮层打开期间网格不参与悬停判定（否则会透过浮层命中底下那格 → 幽灵预览/tooltip 穿透）。 */
    @Override
    protected boolean suppressGridHover() {
        return this.overlay.isVisible();
    }
}
