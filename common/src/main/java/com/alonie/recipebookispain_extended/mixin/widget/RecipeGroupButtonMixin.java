package com.alonie.recipebookispain_extended.mixin.widget;

import com.alonie.brbe.util.BRBTextures;
import com.alonie.brbe.util.ClientCompat;
import com.alonie.recipebookispain_extended.RecipeBookIsPain;
import com.alonie.recipebookispain_extended.access.RecipeGroupButtonPlacement;
import com.alonie.recipebookispain_extended.access.RecipeGroupButtonPlacementAccess;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.client.renderer.RenderPipelines;
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

    @Inject(at = @At("HEAD"), method = "renderContents", cancellable = true)
    private void rbip$drawRotatedButton(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
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

        if (pushed) {
            context.pose().popMatrix();
            this.animationTime -= delta;
        }

        ci.cancel();
    }

    @Inject(at = @At("HEAD"), method = "renderIcon", cancellable = true)
    private void rbip$render(GuiGraphics context, CallbackInfo ci) {
        CreativeModeTab group = RecipeBookIsPain.toItemGroup(this.getCategory());
        if (group == null) return;

        int i = this.selected ? -2 : 0;

        if (RecipeBookIsPain.isOwOLoaded) {
            if (RecipeBookIsPain.rbip$renderOwo(context, i, (RecipeBookTabButton) (Object) this, group)) {
                ci.cancel();
            }
        }
    }

    @Unique
    private void rbip$drawRotatedBackground(GuiGraphics context) {
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
    private void rbip$renderIconsAt(GuiGraphics context, int x, int y) {
        CreativeModeTab group = RecipeBookIsPain.toItemGroup(this.getCategory());
        if (group != null && RecipeBookIsPain.isOwOLoaded && RecipeBookIsPain.rbip$renderOwo(context, x, y, group)) {
            return;
        }

        if (this.tabInfo.secondaryIcon().isPresent()) {
            context.renderFakeItem(this.tabInfo.primaryIcon(), this.getX(), y);
            context.renderFakeItem(this.tabInfo.secondaryIcon().get(), this.getX() + 11, y);
        } else {
            context.renderFakeItem(this.tabInfo.primaryIcon(), x, y);
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
