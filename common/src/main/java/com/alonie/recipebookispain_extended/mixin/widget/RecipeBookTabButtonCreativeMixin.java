package com.alonie.recipebookispain_extended.mixin.widget;

import com.alonie.brbe.pin.TabPinManager;
import com.alonie.brbe.util.BRBTextures;
import com.alonie.recipebookispain_extended.RecipeBookIsPain;
import com.alonie.recipebookispain_extended.access.CreativeTabButtonAccess;
import com.alonie.recipebookispain_extended.access.RecipeGroupButtonPlacement;
import com.alonie.recipebookispain_extended.access.RecipeGroupButtonPlacementAccess;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.1 RBIP: Creative tab button rendering.
 * <p>
 * Implements {@link CreativeTabButtonAccess} (icon override) plus
 * {@link RecipeGroupButtonPlacementAccess} (rotated rendering for top/bottom rows).
 * Mirrors the 1.21.11 {@code RecipeGroupButtonMixin}.
 */
@Mixin(RecipeBookTabButton.class)
public abstract class RecipeBookTabButtonCreativeMixin
        implements CreativeTabButtonAccess, RecipeGroupButtonPlacementAccess {

    @Unique
    private CreativeModeTab rbip$creativeTab;

    @Unique
    private RecipeGroupButtonPlacement rbip$placement = RecipeGroupButtonPlacement.NORMAL;

    // ── CreativeTabButtonAccess ────────────────────────────────

    @Override
    public void rbip$setCreativeTab(CreativeModeTab tab) { this.rbip$creativeTab = tab; }

    @Override
    public CreativeModeTab rbip$getCreativeTab() { return this.rbip$creativeTab; }

    // ── RecipeGroupButtonPlacementAccess ───────────────────────

    @Override
    public void rbip$setPlacement(RecipeGroupButtonPlacement p) { this.rbip$placement = p; }

    @Override
    public RecipeGroupButtonPlacement rbip$getPlacement() { return this.rbip$placement; }

    // ── Texture assets ─────────────────────────────────────────

    @Unique
    private static final ResourceLocation TEX_BOTTOM =
            ResourceLocation.fromNamespaceAndPath("zzzbrbe", "textures/rbip/bottom_tab.png");
    @Unique
    private static final ResourceLocation TEX_BOTTOM_SEL =
            ResourceLocation.fromNamespaceAndPath("zzzbrbe", "textures/rbip/bottom_tab_selected.png");
    @Unique
    private static final ResourceLocation TEX_TOP =
            ResourceLocation.fromNamespaceAndPath("zzzbrbe", "textures/rbip/top_tab.png");
    @Unique
    private static final ResourceLocation TEX_TOP_SEL =
            ResourceLocation.fromNamespaceAndPath("zzzbrbe", "textures/rbip/top_tab_selected.png");

    // ── Cancel unlock bounce for creative tabs ─────────────────

    @Inject(method = "startAnimation", at = @At("HEAD"), cancellable = true)
    private void rbip$cancelCreativeTabBounce(CallbackInfo ci) {
        if (this.rbip$creativeTab != null) ci.cancel();
    }

    // ── Override icon for creative tabs ────────────────────────

    @Inject(method = "renderIcon", at = @At("HEAD"), cancellable = true)
    private void rbip$renderCreativeIcon(GuiGraphics gui, ItemRenderer itemRenderer, CallbackInfo ci) {
        if (this.rbip$creativeTab == null) return;

        ci.cancel();
        ItemStack icon = this.rbip$creativeTab.getIconItem();
        if (icon.isEmpty()) return;

        RecipeBookTabButton self = (RecipeBookTabButton) (Object) this;
        int xOff = self.isStateTriggered() ? -2 : 0;
        gui.renderFakeItem(icon, self.getX() + 9 + xOff, self.getY() + 5);
    }

    // ── 固定标签 pin 贴图 ────────────────────────────────────────

    /** 固定了的创造标签画 pin 图标（最上层，32x32 与配方按钮 pin 一致）。
     *  位置规则（1.21.11 最终版）：正常朝向 anchor (x-4, y-4)（左上角）；
     *  上/下侧绕转条带 pinX+3（右移 3px），下侧 pinY+6；选中时 pin 随标签
     *  偏移 1px（正常向左、上侧向上、下侧向下）。均为最终屏幕位置。 */
    @Unique
    private void rbip$drawTabPin(GuiGraphics gui) {
        if (this.rbip$creativeTab == null) return;
        ResourceLocation tabId = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(this.rbip$creativeTab);
        if (tabId == null || !TabPinManager.isPinned(tabId)) return;
        RecipeBookTabButton self = (RecipeBookTabButton) (Object) this;
        int pinX = self.getX() - 4;
        int pinY = self.getY() - 4;
        if (this.rbip$placement == RecipeGroupButtonPlacement.TOP
                || this.rbip$placement == RecipeGroupButtonPlacement.BOTTOM) {
            pinX += 3;
        }
        if (this.rbip$placement == RecipeGroupButtonPlacement.BOTTOM) {
            pinY += 6;
        }
        if (self.isStateTriggered()) {
            if (this.rbip$placement == RecipeGroupButtonPlacement.NORMAL) {
                pinX--;
            } else if (this.rbip$placement == RecipeGroupButtonPlacement.TOP) {
                pinY--;
            } else {
                pinY++;
            }
        }
        gui.blitSprite(BRBTextures.RECIPE_BOOK_PIN_SPRITE,
                pinX, pinY, 32, 32);
    }

    // ── Normal placement: pin marker after default render ──────

    @Inject(method = "renderWidget", at = @At("RETURN"))
    private void rbip$drawPinMarkerNormal(GuiGraphics gui, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this.rbip$placement != RecipeGroupButtonPlacement.NORMAL) return;
        this.rbip$drawTabPin(gui);
    }

    // ── Rotated rendering for TOP / BOTTOM placements ──────────

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void rbip$renderRotated(GuiGraphics gui, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this.rbip$placement == RecipeGroupButtonPlacement.NORMAL) return;
        if (this.rbip$creativeTab == null) return;

        ci.cancel();

        // 1) Draw rotated background (own push/pop)
        this.rbip$drawRotatedBackground(gui);

        // 2) Draw icon at ABSOLUTE screen coords (outside matrix)
        //    — matches 1.21.11's renderIconsAt approach
        ItemStack icon = this.rbip$creativeTab.getIconItem();
        if (!icon.isEmpty()) {
            gui.renderFakeItem(icon, this.rbip$getRotatedIconX(), this.rbip$getRotatedIconY());
        }

        // 3) 固定标签 pin 贴图（旋转条带在 90° 旋转矩阵之外按最终屏幕坐标绘制）
        this.rbip$drawTabPin(gui);
    }

    @Unique
    private void rbip$drawRotatedBackground(GuiGraphics gui) {
        RecipeBookTabButton self = (RecipeBookTabButton) (Object) this;
        boolean selected = self.isStateTriggered();
        int localX = selected ? -2 : 0;
        PoseStack pose = gui.pose();
        pose.pushPose();

        if (this.rbip$placement == RecipeGroupButtonPlacement.BOTTOM) {
            pose.translate(self.getX(), self.getY() + ROT_TAB_H, 0);
            pose.mulPose(Axis.ZP.rotationDegrees(-90.0F));
        } else {
            pose.translate(self.getX() + ROT_TAB_W, self.getY(), 0);
            pose.mulPose(Axis.ZP.rotationDegrees(90.0F));
        }

        ResourceLocation tex = rbip$getRotatedTexture();
        gui.blit(tex, localX, 0, 0, 0, TAB_W, TAB_H, TAB_W, TAB_H);

        pose.popPose();
    }

    @Unique
    private int rbip$getRotatedIconX() {
        RecipeBookTabButton self = (RecipeBookTabButton) (Object) this;
        int off = this.rbip$placement == RecipeGroupButtonPlacement.TOP ? 1 : 0;
        if (this.rbip$placement == RecipeGroupButtonPlacement.TOP && BRBTextures.hasPartialSprite()) {
            off -= 1; // unique dark 兼容包：顶部 tab 图标左移 1px
        }
        return self.getX() + (ROT_TAB_W - 16) / 2 + off;
    }

    @Unique
    private int rbip$getRotatedIconY() {
        RecipeBookTabButton self = (RecipeBookTabButton) (Object) this;
        int y = self.getY() + (ROT_TAB_H - 16) / 2;
        boolean sel = self.isStateTriggered();
        if (this.rbip$placement == RecipeGroupButtonPlacement.TOP) return y - 1 + (sel ? -2 : 0);
        if (this.rbip$placement == RecipeGroupButtonPlacement.BOTTOM) return y + 1 + (sel ? 2 : 0);
        return y;
    }

    @Unique private static final int TAB_W = 35;
    @Unique private static final int TAB_H = 27;
    @Unique private static final int ROT_TAB_W = 27;
    @Unique private static final int ROT_TAB_H = 35;

    @Unique
    private ResourceLocation rbip$getRotatedTexture() {
        RecipeBookTabButton self = (RecipeBookTabButton) (Object) this;
        boolean selected = self.isStateTriggered();
        if (this.rbip$placement == RecipeGroupButtonPlacement.BOTTOM) {
            return selected ? TEX_BOTTOM_SEL : TEX_BOTTOM;
        } else {
            return selected ? TEX_TOP_SEL : TEX_TOP;
        }
    }
}
