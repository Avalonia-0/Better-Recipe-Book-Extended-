package com.alonie.brbe.brewingstand;

import com.alonie.brbe.generic.GenericRecipePage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.inventory.BrewingStandMenu;

import java.util.function.Supplier;

/**
 * 酿造台配方书的页面：比通用页面多一个**替代配方组浮层**
 * （{@link BrewingOverlayRecipeComponent}，用户 2026-09-26 诉求）。
 */
public class BrewingRecipeBookPage extends GenericRecipePage<BrewingStandMenu, BrewingRecipeCollection, BrewableResult> {
    public final BrewingOverlayRecipeComponent overlay = new BrewingOverlayRecipeComponent();

    public BrewingRecipeBookPage(RegistryAccess registryAccess, Supplier<Boolean> filteringSupplier) {
        super(registryAccess, () -> new BrewableRecipeButton(registryAccess, filteringSupplier));
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
    protected void initOverlay(BrewingRecipeCollection recipeCollection, int anchorX, int anchorY,
                               int x, int y, RegistryAccess registryAccess) {
        // 位置按**被点击的组按钮** + 书面板原点算（原版规则，见 AlternativeOverlayLayout#placeBox）
        // category/menu 一并传进浮层：组内格子的 tooltip 要按它们取产物形态与"背包里有没有"的配色
        this.overlay.init(recipeCollection, this.category, this.menu, anchorX, anchorY,
                this.parentLeft, this.parentTop, registryAccess);
    }

    @Override
    public void render(GuiGraphicsExtractor gui, int x, int y, int mouseX, int mouseY, float delta) {
        super.render(gui, x, y, mouseX, mouseY, delta);

        gui.nextStratum();
        this.overlay.extractRenderState(gui, mouseX, mouseY, delta);

        // 浮层内的悬停路线补进页面的 hovered* 状态：浮层盖在网格上，网格按钮不再报告悬停，
        // 于是「自动填充幽灵配方」在组内路线上失效——这里补回来（与锻造台浮层同一处理）。
        BrewingOverlayRecipeComponent.RouteButton overlayHovered = this.overlay.hoveredButton();
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
        BrewingOverlayRecipeComponent.RouteButton hovered = this.overlay.hoveredButton();
        return hovered == null ? null : hovered.getTooltipText();
    }

    /** 浮层打开期间网格不参与悬停判定（否则会透过浮层命中底下那格 → 幽灵预览/tooltip 穿透）。 */
    @Override
    protected boolean suppressGridHover() {
        return this.overlay.isVisible();
    }

    /** 收起配方书时关闭组浮层（与 {@link #overlayMouseClicked} 的"点到组外"同一动作）。 */
    @Override
    protected void hideOverlay() {
        this.overlay.setVisible(false);
    }
}
