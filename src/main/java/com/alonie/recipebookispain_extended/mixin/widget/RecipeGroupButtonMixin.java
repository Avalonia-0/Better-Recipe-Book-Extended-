package com.alonie.recipebookispain_extended.mixin.widget;

import com.alonie.brbe.pin.TabPinManager;
import com.alonie.brbe.util.BRBTextures;
import com.alonie.brbe.util.ClientCompat;
import com.alonie.recipebookispain_extended.RecipeBookIsPain;
import com.alonie.recipebookispain_extended.access.RecipeGroupButtonPlacement;
import com.alonie.recipebookispain_extended.access.RecipeGroupButtonPlacementAccess;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.crafting.ExtendedRecipeBookCategory;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RecipeBookTabButton.class)
public abstract class RecipeGroupButtonMixin extends ImageButton implements RecipeGroupButtonPlacementAccess {
    @Unique private static final int RBIP_TAB_WIDTH = 35;
    @Unique private static final int RBIP_TAB_HEIGHT = 27;
    @Unique private static final int RBIP_ROTATED_TAB_WIDTH = 27;
    @Unique private static final int RBIP_ROTATED_TAB_HEIGHT = 35;
    @Unique private static final Identifier RBIP_BOTTOM_TAB = Identifier.fromNamespaceAndPath("brbe", "textures/rbip/bottom_tab.png");
    @Unique private static final Identifier RBIP_BOTTOM_TAB_SELECTED = Identifier.fromNamespaceAndPath("brbe", "textures/rbip/bottom_tab_selected.png");
    @Unique private static final Identifier RBIP_TOP_TAB = Identifier.fromNamespaceAndPath("brbe", "textures/rbip/top_tab.png");
    @Unique private static final Identifier RBIP_TOP_TAB_SELECTED = Identifier.fromNamespaceAndPath("brbe", "textures/rbip/top_tab_selected.png");

    @Unique private RecipeGroupButtonPlacement rbip$placement = RecipeGroupButtonPlacement.NORMAL;

    @Shadow @Final private RecipeBookComponent.TabInfo tabInfo;
    @Shadow private float animationTime;
    @Shadow private boolean selected;

    @Shadow public abstract ExtendedRecipeBookCategory getCategory();

    public RecipeGroupButtonMixin(int x, int y, int width, int height, WidgetSprites sprites, Button.OnPress onPress) {
        super(x, y, width, height, sprites, onPress);
    }

    @Override
    public void rbip$setPlacement(RecipeGroupButtonPlacement placement) {
        this.rbip$placement = placement;
    }

    @Override
    public RecipeGroupButtonPlacement rbip$getPlacement() {
        return this.rbip$placement;
    }

    @Inject(at = @At("HEAD"), method = "startAnimation", cancellable = true)
    private void rbip$skipCreativeTabUnlockBounce(ClientRecipeBook recipeBook, boolean filteringCraftable, CallbackInfo ci) {
        if (RecipeBookIsPain.toItemGroup(this.getCategory()) != null) {
            ci.cancel();
        }
    }

    @Inject(at = @At("HEAD"), method = "extractContents", cancellable = true)
    private void rbip$drawRotatedButton(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this.rbip$placement == RecipeGroupButtonPlacement.NORMAL) return;

        boolean pushed = false;
        if (this.animationTime > 0.0F) {
            float scale = 1.0F + 0.1F * (float) Math.sin(this.animationTime / 15.0F * (float) Math.PI);
            context.pose().pushMatrix();
            context.pose().translate(this.getX() + 8, this.getY() + 12);
            context.pose().scale(1.0F, scale);
            context.pose().translate(-(this.getX() + 8), -(this.getY() + 12));
            pushed = true;
        }

        this.rbip$drawRotatedBackground(context);
        this.rbip$renderIconsAt(context, this.rbip$getRotatedIconX(), this.rbip$getRotatedIconY());
        this.rbip$drawTabPin(context);

        if (pushed) {
            context.pose().popMatrix();
            this.animationTime -= delta;
        }

        ci.cancel();
    }

    @Inject(at = @At("HEAD"), method = "extractIcon", cancellable = true)
    private void rbip$render(GuiGraphicsExtractor context, CallbackInfo ci) {
        CreativeModeTab group = RecipeBookIsPain.toItemGroup(this.getCategory());
        if (group == null) return;

        int i = this.selected ? -2 : 0;

        if (RecipeBookIsPain.isOwOLoaded) {
            if (RecipeBookIsPain.rbip$renderOwo(context, i, (RecipeBookTabButton) (Object) this, group)) {
                ci.cancel();
            }
        }
    }

    /** 固定标签标记：固定了的创造标签画 pin 图标（最上层，图标之后，
     *  32x32 与配方按钮的 pin 一致，图形悬出标签边缘）。正常朝向与上侧标签
     *  的 pin 悬在标签左上角，下侧标签的 pin 悬在左下角；旋转条带在 90°
     *  旋转矩阵之外按最终屏幕坐标绘制，位置即最终呈现位置。正常朝向的标签
     *  借 extractIcon RETURN 绘制；旋转条带上的标签由 extractContents HEAD
     *  注入接管（extractIcon 不会执行），在 rbip$drawRotatedButton 内补画。
     *  注入目标必须是 RecipeBookTabButton 自身声明的方法（extractIcon）——
     *  继承自 AbstractWidget 的 extractWidgetRenderState 不会被本版本 Mixin
     *  解析（与 RecipeBookTabHoverMixin 的 @Shadow 字段问题同类）。 */
    @Inject(at = @At("RETURN"), method = "extractIcon")
    private void rbip$drawPinMarker(GuiGraphicsExtractor context, CallbackInfo ci) {
        if (this.rbip$placement != RecipeGroupButtonPlacement.NORMAL) return;
        this.rbip$drawTabPin(context);
    }

    @Unique
    private void rbip$drawTabPin(GuiGraphicsExtractor context) {
        CreativeModeTab group = RecipeBookIsPain.toItemGroup(this.getCategory());
        if (group == null) return;
        Identifier tabId = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(group);
        if (tabId == null || !TabPinManager.isPinned(tabId)) return;
        // pin 图形位于 32x32 精灵图左上 (6,2)-(12,7)。上侧 pin 锚点 (x-1, y-4)
        // （左上角，悬出标签顶边 2px，x-4 基础上右移 3px）；下侧 pin 同 x 但
        // y+6（左上角下移 6px，用户要求）；正常朝向锚点 (x-4, y-4)。
        // 均为最终屏幕位置（不随旋转矩阵变换）。
        // 选中时 pin 随标签选中偏移移动：左/上/下侧分别向左/上/下移动 1px。
        int pinX = this.getX() - 4;
        int pinY = this.getY() - 4;
        if (this.rbip$placement == RecipeGroupButtonPlacement.TOP
                || this.rbip$placement == RecipeGroupButtonPlacement.BOTTOM) {
            pinX += 3;
        }
        if (this.rbip$placement == RecipeGroupButtonPlacement.BOTTOM) {
            pinY += 6;
        }
        if (this.selected) {
            if (this.rbip$placement == RecipeGroupButtonPlacement.NORMAL) {
                pinX--;
            } else if (this.rbip$placement == RecipeGroupButtonPlacement.TOP) {
                pinY--;
            } else {
                pinY++;
            }
        }
        ClientCompat.blitSprite(context, BRBTextures.RECIPE_BOOK_PIN_SPRITE,
                pinX, pinY, 32, 32);
    }

    @Unique
    private void rbip$drawRotatedBackground(GuiGraphicsExtractor context) {
        int localX = this.selected ? -2 : 0;
        context.pose().pushMatrix();
        if (this.rbip$placement == RecipeGroupButtonPlacement.BOTTOM) {
            context.pose().translate(this.getX(), this.getY() + RBIP_ROTATED_TAB_HEIGHT);
            context.pose().rotate(-(float) Math.PI / 2.0F);
        } else {
            context.pose().translate(this.getX() + RBIP_ROTATED_TAB_WIDTH, this.getY());
            context.pose().rotate((float) Math.PI / 2.0F);
        }
        Identifier texture = this.rbip$getRotatedTexture();
        if (texture.getPath().startsWith("textures/")) {
            context.blit(RenderPipelines.GUI_TEXTURED, texture, localX, 0, 0.0F, 0.0F, RBIP_TAB_WIDTH, RBIP_TAB_HEIGHT, RBIP_TAB_WIDTH, RBIP_TAB_HEIGHT);
        } else {
            context.blitSprite(RenderPipelines.GUI_TEXTURED, texture, localX, 0, RBIP_TAB_WIDTH, RBIP_TAB_HEIGHT);
        }
        context.pose().popMatrix();
    }

    @Unique
    private Identifier rbip$getRotatedTexture() {
        if (this.rbip$placement == RecipeGroupButtonPlacement.TOP) {
            return this.selected ? RBIP_TOP_TAB_SELECTED : RBIP_TOP_TAB;
        }
        if (this.rbip$placement == RecipeGroupButtonPlacement.BOTTOM) {
            return this.selected ? RBIP_BOTTOM_TAB_SELECTED : RBIP_BOTTOM_TAB;
        }
        return this.sprites.get(true, this.selected);
    }

    @Unique
    private void rbip$renderIconsAt(GuiGraphicsExtractor context, int x, int y) {
        CreativeModeTab group = RecipeBookIsPain.toItemGroup(this.getCategory());
        if (group != null && RecipeBookIsPain.isOwOLoaded && RecipeBookIsPain.rbip$renderOwo(context, x, y, group)) {
            return;
        }

        if (this.tabInfo.secondaryIcon().isPresent()) {
            context.fakeItem(this.tabInfo.primaryIcon(), this.getX(), y);
            context.fakeItem(this.tabInfo.secondaryIcon().get(), this.getX() + 11, y);
        } else {
            context.fakeItem(this.tabInfo.primaryIcon(), x, y);
        }
    }

    @Unique
    private int rbip$getRotatedIconX() {
        boolean top = this.rbip$placement == RecipeGroupButtonPlacement.TOP;
        int offset = top ? 1 : 0;
        if (top && ClientCompat.hasSpriteResource(BRBTextures.RECIPE_BOOK_BUTTON_SLOT_PARTIAL_SPRITE)) {
            offset -= 1; // unique dark 兼容包：顶部 tab 图标左移 1px
        }
        return this.getX() + (RBIP_ROTATED_TAB_WIDTH - 16) / 2 + offset;
    }

    @Unique
    private int rbip$getRotatedIconY() {
        int y = this.getY() + (RBIP_ROTATED_TAB_HEIGHT - 16) / 2;
        if (this.rbip$placement == RecipeGroupButtonPlacement.TOP) {
            return y - 1 + (this.selected ? -2 : 0);
        }
        if (this.rbip$placement == RecipeGroupButtonPlacement.BOTTOM) {
            return y + 1 + (this.selected ? 2 : 0);
        }
        return y;
    }
}
