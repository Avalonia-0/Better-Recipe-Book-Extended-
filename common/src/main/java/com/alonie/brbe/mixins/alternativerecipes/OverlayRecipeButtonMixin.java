package com.alonie.brbe.mixins.alternativerecipes;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.OverlayRecipeButtonPosAccessor;
import com.alonie.brbe.mixins.accessors.OverlayRecipeComponentAccessor;
import com.alonie.brbe.util.BRBTextures;
import com.alonie.brbe.util.CycleLock;
import com.alonie.brbe.util.PartialCraftingUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;


@Mixin(targets = "net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent$OverlayRecipeButton")
public abstract class OverlayRecipeButtonMixin extends AbstractWidget {

    @Final
    @Shadow
    private boolean isCraftable;
    @Final
    @Shadow
    RecipeHolder<?> recipe;

    @Shadow
    public abstract void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float delta);

    @Shadow
    @Final
    protected List<?> ingredientPos;
    @Shadow
    @Final
    OverlayRecipeComponent field_3113;

    public OverlayRecipeButtonMixin(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    @Inject(at = @At("HEAD"), method = "renderWidget", cancellable = true)
    public void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        OverlayRecipeComponentAccessor overlay = (OverlayRecipeComponentAccessor) field_3113;
        boolean furnaceBook = overlay.isFurnaceMenu();
        boolean partial = PartialCraftingUtil.isPartiallyCraftable(field_3113.getRecipeCollection(), this.recipe);
        if (furnaceBook) {
            // Furnace books (furnace / blast furnace / smoker) have no partial state.
            partial = false;
        }
        boolean hovered = this.isHoveredOrFocused();
        boolean effectiveCraftable = this.isCraftable || partial;
        // 纹理与内容一致（用户 2026-09-25 诉求 2）：
        //  * 显示完整配方预览（悬停，或关闭「仅在悬停时显示替代配方」）→ 原版纹理面，
        //    悬停 = *_highlighted（可合成/残缺 → 启用面，不可合成 → 禁用面）；
        //  * 只画产物图标（开启该配置且未悬停）→ BRBE 自有 crafting_overlay(_disabled)。
        // 熔炉系书（熔炉/鼓风炉/烟熏炉）同规则：原版面换成 furnace_overlay 系、
        // BRBE 面换成 plain_overlay 系。
        boolean fullPreview = hovered
                || !BetterRecipeBook.ctx().config().alternativeRecipes.onHover;
        ResourceLocation resourceLocation = fullPreview
                ? (furnaceBook
                        ? BRBTextures.VANILLA_FURNACE_OVERLAY_SPRITE
                        : BRBTextures.VANILLA_CRAFTING_OVERLAY_SPRITE)
                        .get(effectiveCraftable, hovered)
                : (furnaceBook
                        ? BRBTextures.RECIPE_BOOK_PLAIN_OVERLAY_SPRITE
                        : BRBTextures.RECIPE_BOOK_CRAFTING_OVERLAY_SPRITE)
                        .get(effectiveCraftable, false);

        gui.blitSprite(resourceLocation, getX(), getY(), this.width, this.height);

        // Red overlay for partially-craftable recipes.  Kept vanilla-style here
        // (craftable sprite + red overlay) — the compat pack's red check is not
        // applied to alternative-recipe groups.
        if (partial) {
            gui.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, 0x60FF3333);
        }

        gui.pose().pushPose();
        if (!fullPreview) { // 只画产物图标（开启「仅在悬停时显示替代配方」且未悬停）
            ItemStack recipeOutput = this.recipe.value().getResultItem(field_3113.getRecipeCollection().registryAccess());
            gui.renderItem(recipeOutput, getX() + 4, getY() + 4);
        } else { // otherwise display the crafting recipe
            // 逐物品折叠锁（用户 2026-09-13 诉求 2）：整块按钮当作**一件**折叠物品
            // ——只有指针下这一个按钮会冻结（首次冻结时 latch 住当时显示的变体），
            // 锁定键+滚轮也只翻动它；其余按钮照常自动轮换。查询浮层里的按钮直接
            // 判定，配方书自己的替代配方按钮走屏幕层判定（LEI 浮层挡住指针时不判定）。
            int autoIndex = Mth.floor(((OverlayRecipeComponentAccessor) field_3113).getTime() / 30.0f);
            Object cycleKey = this;
            boolean onViewer = com.alonie.brbe.util.RecipeViewerOverlay.isActive();
            boolean claimed = onViewer
                    ? CycleLock.claim(cycleKey, getX(), getY(), width, height)
                    : CycleLock.claimScreen(cycleKey, getX(), getY(), width, height);
            final int selIdx;
            if (claimed) {
                selIdx = CycleLock.indexFor(cycleKey, autoIndex);
            } else {
                CycleLock.release(cycleKey);
                selIdx = autoIndex;
            }
            gui.pose().translate(this.getX() + 2, this.getY() + 2, 150.0);
            for (Object rawPos : this.ingredientPos) {
                OverlayRecipeButtonPosAccessor pos = (OverlayRecipeButtonPosAccessor) rawPos;
                gui.pose().pushPose();
                gui.pose().translate(pos.brbe$getX(), pos.brbe$getY(), 0.0);
                // if furnace menu, keep items at default scale, so it isn't tiny
                if (!((OverlayRecipeComponentAccessor) field_3113).isFurnaceMenu()) {
                    gui.pose().scale(0.375f, 0.375f, 1.0f);
                }
                gui.pose().translate(-8.0, -8.0, 0.0);
                ItemStack[] ingredients = pos.brbe$getIngredients();
                if (ingredients.length > 0) {
                    gui.renderItem(ingredients[Math.floorMod(selIdx, ingredients.length)], 0, 0);
                }
                gui.pose().popPose();
            }
        }
        gui.pose().popPose();

        // blit pin for pinned recipes
        if (BetterRecipeBook.pinnedRecipeManager.pinned.contains(recipe.id())) {
            gui.pose().pushPose();
            // make sure pin is drawn over the crafting items
            gui.pose().mulPose(gui.pose().last().pose());
            gui.blitSprite(BRBTextures.RECIPE_BOOK_OVERLAY_PIN_SPRITE, getX() - 4, getY() - 4, this.width + 8, this.height + 8);
            gui.pose().popPose();
        }

        ci.cancel();
    }

}
