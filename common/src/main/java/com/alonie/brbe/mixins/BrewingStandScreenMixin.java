package com.alonie.brbe.mixins;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.brewingstand.BrewingRecipeBookComponent;
import com.alonie.brbe.brewingstand.BrewingRecipeBookPage;
import com.alonie.brbe.interfaces.TopLayerOverlayProvider;
import com.alonie.brbe.mixins.accessors.AbstractContainerScreenAccessor;
import com.alonie.brbe.util.BRBTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
// import com.alonie.brbe.interfaces.ExpandedBookScreen; // TEMPORARILY DISABLED
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.BrewingStandScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BrewingStandScreen.class)
public abstract class BrewingStandScreenMixin extends AbstractContainerScreen<BrewingStandMenu> implements TopLayerOverlayProvider {

    @Unique
    public final BrewingRecipeBookComponent _$recipeBookComponent = new BrewingRecipeBookComponent();
    @Unique
    private boolean _$widthNarrow;

    public BrewingStandScreenMixin(BrewingStandMenu handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    protected void init(CallbackInfo ci) {
        if (BetterRecipeBook.ctx().config().enableBook) {
            this._$widthNarrow = this.width < 379;
            assert this.minecraft != null;
            this._$recipeBookComponent.init(this.width, this.height, this.minecraft, _$widthNarrow, this.menu, Minecraft.getInstance().getConnection().registryAccess());

            if (!BetterRecipeBook.ctx().config().keepCentered) {
                this.leftPos = this._$recipeBookComponent.findLeftEdge(this.width, this.imageWidth);
            }

            this.addRenderableWidget(new ImageButton(this.leftPos + 135, this.height / 2 - 50, 20, 18, BRBTextures.RECIPE_BOOK_BUTTON_SPRITES, (button) -> {
                this._$recipeBookComponent.toggleVisibility();
                if (!BetterRecipeBook.ctx().config().keepCentered) {
                    this.leftPos = this._$recipeBookComponent.findLeftEdge(this.width, this.imageWidth);
                }
                button.setPosition(this.leftPos + 135, this.height / 2 - 50);
            }));

            this.addWidget(this._$recipeBookComponent);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 配方书的**替代配方组浮层**优先：浮层打开时点击优先交给配方书组件 →
        // 点到浮层格子 = 选路线、点到别处 = 关掉浮层（用户 2026-09-26 反馈：
        // 此前点到配方书以外的界面无法关闭酿造台的组浮层）。
        if (this.brbe$clickTopLayerOverlay(mouseX, mouseY, button)) {
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ── 替代配方组 = 顶层浮层（与锻造台屏幕同一套语义）────────────────────────────

    @Override
    public boolean brbe$hasTopLayerOverlay() {
        return this._$recipeBookComponent.isVisible()
                && this._$recipeBookComponent.recipesPage instanceof BrewingRecipeBookPage page
                && page.overlayIsVisible();
    }

    @Override
    public void brbe$renderTopLayerOverlay(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (this.brbe$hasTopLayerOverlay()) {
            ((BrewingRecipeBookPage) this._$recipeBookComponent.recipesPage).overlay.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean brbe$clickTopLayerOverlay(double mouseX, double mouseY, int button) {
        return this.brbe$hasTopLayerOverlay()
                && this._$recipeBookComponent.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public ScreenRectangle brbe$getTopLayerOverlayBounds() {
        if (this.brbe$hasTopLayerOverlay()) {
            return ((BrewingRecipeBookPage) this._$recipeBookComponent.recipesPage).overlay.getBounds();
        }

        return null;
    }

    @Override
    public boolean keyPressed(int i, int j, int k) {
        if (_$recipeBookComponent.keyPressed(i, j, k)) {
            return true;
        }
        return super.keyPressed(i, j, k);
    }

    @Override
    public boolean keyReleased(int i, int j, int k) {
        if (_$recipeBookComponent.keyReleased(i, j, k)) {
            return true;
        }
        return super.keyReleased(i, j, k);
    }

    @Override
    public boolean charTyped(char c, int i) {
        if (_$recipeBookComponent.charTyped(c, i)) {
            return true;
        }
        return super.charTyped(c, i);
    }

    @Override
    protected void slotClicked(Slot slot, int x, int y, ClickType clickType) {
        // clear ghost recipe if an empty ingredient slot is clicked with no items
        if (slot != null && slot.index < 4 && menu.slots.get(slot.index).getItem().isEmpty()) {
            // 外部清空 = 点击留下的缺料引导**结束**（否则下次悬停结束会把这份引导又写回来，
            // 玩家看到"来源无规律"的持久幽灵 —— 用户 2026-09-26 反馈）
            _$recipeBookComponent.brbe$endGhostGuide();
        }

        super.slotClicked(slot, x, y, clickType);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0 && this._$recipeBookComponent.isVisible()) {
            this._$recipeBookComponent.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected boolean hasClickedOutside(double d, double e, int i, int j, int k) {
        boolean bl = d < (double) i || e < (double) j || d >= (double) (i + this.imageWidth) || e >= (double) (j + this.imageHeight);
        return this._$recipeBookComponent.hasClickedOutside(d, e, this.leftPos, this.topPos, this.imageWidth, this.imageHeight, k) && bl;
    }

    @Inject(method = "render", at = @At("RETURN"))
    public void render(GuiGraphics guiGraphics, int i, int j, float f, CallbackInfo ci) {
        if (this._$recipeBookComponent.isVisible()) {
            this._$recipeBookComponent.render(guiGraphics, i, j, f);
            this._$recipeBookComponent.renderGhostRecipe(guiGraphics, this.leftPos, this.topPos, false, f);
        }

        // Redraw the carried item so it always draws on top of the recipe book
        // (vanilla draws it before the book's @Inject RETURN, so the book would
        // otherwise cover it).
        if (!this.menu.getCarried().isEmpty()) {
            com.mojang.blaze3d.vertex.PoseStack pose = guiGraphics.pose();
            pose.pushPose();
            pose.translate(this.leftPos, this.topPos, 0.0F);
            ((AbstractContainerScreenAccessor) this).brbe$renderFloatingItem(
                    guiGraphics, this.menu.getCarried(), i - this.leftPos - 8, j - this.topPos - 8, "");
            pose.popPose();
        }

        if (this._$recipeBookComponent.isVisible()) {
            this._$recipeBookComponent.drawTooltip(guiGraphics, this.leftPos, this.topPos, i, j);
        }
    }

    // @Override
    // public boolean brbe$isExpandedBookOpen() { // TEMPORARILY DISABLED
    //     return this._$recipeBookComponent.isVisible()
    //             && this._$recipeBookComponent.isExpanded();
    // }

    // fix brewing progress indicator offset when recipe book is open by modifying the width offset
    @ModifyVariable(
            method = "renderBg",
            index = 5,
            at = @At("STORE")
    )
    public int renderBg_width(int i) {
        if (this._$recipeBookComponent.isVisible() && !BetterRecipeBook.ctx().config().keepCentered) {
            return i + 77;
        } else {
            return i;
        }
    }
}
