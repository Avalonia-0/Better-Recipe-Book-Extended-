package com.alonie.brbe.mixins;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.brewingstand.BrewingRecipeBookComponent;
import com.alonie.brbe.brewingstand.BrewingRecipeBookPage;
import com.alonie.brbe.interfaces.TopLayerOverlayProvider;
import com.alonie.brbe.pinoverlay.PinOverlayManager;
import com.alonie.brbe.util.RecipeViewerOverlay;
import com.alonie.brbe.util.ClientCompat;
import com.alonie.brbe.util.BRBTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.BrewingStandScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ContainerInput;
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
        if (BetterRecipeBook.config.enableBook) {
            this._$widthNarrow = this.width < 379;
            assert this.minecraft != null;
            this._$recipeBookComponent.init(this.width, this.height, this.minecraft, _$widthNarrow, this.menu, Minecraft.getInstance().getConnection().registryAccess());

            if (!BetterRecipeBook.config.keepCentered) {
                this.leftPos = this._$recipeBookComponent.findLeftEdge(this.width, this.imageWidth);
            }

            this.addRenderableWidget(new ImageButton(this.leftPos + 135, this.height / 2 - 50, 20, 18, BRBTextures.RECIPE_BOOK_BUTTON_SPRITES, this::brbe$onRecipeBookButton));

            this.addWidget(this._$recipeBookComponent);
        }
    }

    /**
     * 配方书开关按钮的回调。
     *
     * <p><b>为什么拆成方法 + 方法引用</b>：mixin 类里的 lambda 会被编译成合成方法，
     * Mixin 必须重命名它们（否则与目标类同名合成方法冲突）并在 latest.log 打一行
     * {@code Renaming synthetic method ...}；方法引用走 invokedynamic 的
     * {@code MethodHandle}，与直接调用走同一套重映射（{@code transformMethodRef}），
     * 不产生合成方法、不刷日志。</p>
     */
    @Unique
    private void brbe$onRecipeBookButton(Button button) {
        this._$recipeBookComponent.toggleVisibility();
        if (!BetterRecipeBook.config.keepCentered) {
            this.leftPos = this._$recipeBookComponent.findLeftEdge(this.width, this.imageWidth);
        }
        button.setPosition(this.leftPos + 135, this.height / 2 - 50);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // 查询窗口 / pin 浮层优先，随后是配方书的**替代配方组浮层**：浮层打开时点击优先交给
        // 配方书组件 → 点到浮层格子 = 选路线、点到别处 = 关掉浮层（用户 2026-09-26 反馈：
        // 此前点到配方书以外的界面无法关闭酿造台的组浮层）。
        if (RecipeViewerOverlay.mouseClicked(event, doubleClick, this)) {
            return true;
        }

        if (PinOverlayManager.handleMouseClicked(event, doubleClick, this)) {
            return true;
        }

        if (this.brbe$clickTopLayerOverlay(event, doubleClick)) {
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    // ── 替代配方组 = 顶层浮层（与锻造台屏幕同一套语义）────────────────────────────

    @Override
    public boolean brbe$hasTopLayerOverlay() {
        return this._$recipeBookComponent.isVisible()
                && this._$recipeBookComponent.recipesPage instanceof BrewingRecipeBookPage page
                && page.overlayIsVisible();
    }

    @Override
    public void brbe$renderTopLayerOverlay(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (this.brbe$hasTopLayerOverlay()) {
            ((BrewingRecipeBookPage) this._$recipeBookComponent.recipesPage).overlay.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    /**
     * 顶层浮层这一遍的格子 tooltip（用户 2026-09-27 反馈：组内配方的 tooltip 被浮层压住）。
     *
     * <p>这一遍跑在帧末 tooltip 刷新之后，所以只能就地画；延迟注册的那一份在
     * {@link com.alonie.brbe.generic.GenericRecipeBookComponent#drawTooltip} 里已按
     * {@code TopLayerOverlayRenderer#redrawsOverlayOnTop} 让位，不会重复绘制。</p>
     */
    @Override
    public void brbe$renderTopLayerTooltip(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        if (!this.brbe$hasTopLayerOverlay()) {
            return;
        }

        BrewingRecipeBookPage page = (BrewingRecipeBookPage) this._$recipeBookComponent.recipesPage;
        com.alonie.brbe.util.ClientCompat.drawComponentTooltipNow(guiGraphics, page.overlayTooltip(), mouseX, mouseY);
    }

    @Override
    public boolean brbe$clickTopLayerOverlay(MouseButtonEvent event, boolean doubleClick) {
        return this.brbe$hasTopLayerOverlay()
                && this._$recipeBookComponent.mouseClicked(event, doubleClick);
    }

    @Override
    public ScreenRectangle brbe$getTopLayerOverlayBounds() {
        if (this.brbe$hasTopLayerOverlay()) {
            return ((BrewingRecipeBookPage) this._$recipeBookComponent.recipesPage).overlay.getBounds();
        }

        return null;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (_$recipeBookComponent.keyPressed(event)) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (_$recipeBookComponent.keyReleased(event)) {
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (_$recipeBookComponent.charTyped(event)) {
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    protected void slotClicked(Slot slot, int x, int y, ContainerInput clickType) {
        // clear ghost recipe if an empty ingredient slot is clicked with no items
        if (slot != null && slot.index < 4 && menu.slots.get(slot.index).getItem().isEmpty()) {
            // 外部清空 = 点击留下的缺料引导**结束**（否则下次悬停结束会把这份引导又写回来，
            // 玩家看到"来源无规律"的持久幽灵 —— 用户 2026-09-26 反馈）
            _$recipeBookComponent.brbe$endGhostGuide();
        }

        super.slotClicked(slot, x, y, clickType);
    }

    @Override
    protected boolean hasClickedOutside(double d, double e, int i, int j) {
        boolean bl = d < (double) i || e < (double) j || d >= (double) (i + this.imageWidth) || e >= (double) (j + this.imageHeight);
        return this._$recipeBookComponent.hasClickedOutside(d, e, this.leftPos, this.topPos, this.imageWidth, this.imageHeight, 0) && bl;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int i, int j, float f) {
        this.extractBackground(guiGraphics, i, j, f);
        this.extractContents(guiGraphics, i, j, f);

        guiGraphics.nextStratum();
        if (this._$recipeBookComponent.isVisible()) {
            this._$recipeBookComponent.extractRenderState(guiGraphics, i, j, f);
            this._$recipeBookComponent.renderGhostRecipe(guiGraphics, this.leftPos, this.topPos, false, f);
        }

        guiGraphics.nextStratum();
        this.extractCarriedItem(guiGraphics, i, j);
        this.extractTooltip(guiGraphics, i, j);
        if (this._$recipeBookComponent.isVisible()) {
            this._$recipeBookComponent.drawTooltip(guiGraphics, this.leftPos, this.topPos, i, j);
        }

        // This screen overrides extractRenderState without delegating to
        // AbstractContainerScreen, so the viewer overlay (hooked on the base
        // class) must be drawn here explicitly, merged with any pin overlays.
        PinOverlayManager.render(guiGraphics, i, j, f);
    }

    // fix brewing progress indicator offset when recipe book is open by modifying the width offset
    @ModifyVariable(
            method = "extractBackground",
            index = 5,
            at = @At("STORE")
    )
    public int renderBg_width(int i) {
        if (this._$recipeBookComponent.isVisible() && !BetterRecipeBook.config.keepCentered) {
            return i + 77;
        } else {
            return i;
        }
    }
}
