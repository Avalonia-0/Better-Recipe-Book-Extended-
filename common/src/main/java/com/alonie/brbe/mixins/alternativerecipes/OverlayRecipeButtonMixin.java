package com.alonie.brbe.mixins.alternativerecipes;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.interfaces.IOverlayCellTooltip;
import com.alonie.brbe.mixins.accessors.OverlayRecipeButtonPosAccessor;
import com.alonie.brbe.mixins.accessors.OverlayRecipeComponentAccessor;
import com.alonie.brbe.util.BRBTextures;
import com.alonie.brbe.util.CycleLock;
import com.alonie.brbe.util.PartialCraftingUtil;
import com.alonie.brbe.util.RecipeBookKind;
import com.alonie.brbe.util.RecipeCellTooltips;
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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.jetbrains.annotations.Nullable;

import java.util.List;


@Mixin(targets = "net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent$OverlayRecipeButton")
public abstract class OverlayRecipeButtonMixin extends AbstractWidget implements IOverlayCellTooltip {

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
        // 配方书种类（用户 2026-09-27 诉求，与 26.x 对齐）：合成类原样；熔炉类的格子画
        // **材料**当展示物品（同一组里各变体产物相同，只有材料能区分）；熔炉类/合成类
        // 以外的书（模组自建配方书，含调用原版 API 拿到本浮层的那种）只画展示物品、
        // 不画微缩配方——与酿造台 / 锻造台已有行为一致。
        RecipeBookKind kind = RecipeBookKind.current(furnaceBook);
        // 这一格**画出来的**展示物品：格子 tooltip（用户 2026-09-27 诉求）在
        // RecipeBookPage.renderTooltip 里取它——那才是原版配方书 tooltip 的正规出口
        // （槽位 tooltip 早于它注册，会被它盖掉；见 RecipeBookPageOverlayTooltipMixin）。
        // 本分支没有 RecipeDisplay 体系，物品直接来自配方本身 / 本格已解析的输入。
        this.brbe$drawnProduct = this.brbe$alternativesCellStack(kind);
        // 纹理与内容一致（用户 2026-09-25 诉求 2）：
        //  * 显示完整配方预览（悬停，或关闭「仅在悬停时显示替代配方」）→ 原版纹理面，
        //    悬停 = *_highlighted（可合成/残缺 → 启用面，不可合成 → 禁用面）；
        //  * 只画产物图标（开启该配置且未悬停）→ BRBE 自有 crafting_overlay(_disabled)。
        // 熔炉系书（熔炉/鼓风炉/烟熏炉）用原版 furnace_overlay 系，其余书用 BRBE plain_overlay 系。
        boolean fullPreview = kind.showsMicroRecipe()
                && (hovered || !BetterRecipeBook.ctx().config().alternativeRecipes.onHover);
        ResourceLocation resourceLocation;
        if (kind == RecipeBookKind.FURNACE) {
            resourceLocation = BRBTextures.VANILLA_FURNACE_OVERLAY_SPRITE.get(effectiveCraftable, hovered);
        } else if (kind == RecipeBookKind.OTHER) {
            resourceLocation = BRBTextures.RECIPE_BOOK_PLAIN_OVERLAY_SPRITE.get(effectiveCraftable, hovered);
        } else {
            resourceLocation = fullPreview
                    ? BRBTextures.VANILLA_CRAFTING_OVERLAY_SPRITE.get(effectiveCraftable, hovered)
                    : BRBTextures.RECIPE_BOOK_CRAFTING_OVERLAY_SPRITE.get(effectiveCraftable, false);
        }

        gui.blitSprite(resourceLocation, getX(), getY(), this.width, this.height);

        // Red overlay for partially-craftable recipes.  Kept vanilla-style here
        // (craftable sprite + red overlay) — the compat pack's red check is not
        // applied to alternative-recipe groups.
        if (partial) {
            gui.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, 0x60FF3333);
        }

        gui.pose().pushPose();
        if (!kind.showsMicroRecipe()) {
            // 熔炉类 / 其余书：一块底板 + 一件展示物品，不画任何微缩配方
            ItemStack shown = this.brbe$alternativesCellStack(kind);
            if (!shown.isEmpty()) {
                gui.renderItem(shown, getX() + 4, getY() + 4);
            }
        } else if (!fullPreview) { // 只画产物图标（开启「仅在悬停时显示替代配方」且未悬停）
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

    /**
     * 本帧这一格**画出来的**产物（{@link IOverlayCellTooltip}）：逐帧由 {@code renderWidget} 刷新。
     * 查询窗口 / pin 的浮层盖住指针时工具提示通路另有守卫（见 {@code RecipeBookPageOverlayTooltipMixin}）。
     */
    @Unique
    private ItemStack brbe$drawnProduct = ItemStack.EMPTY;

    /**
     * 本格按书种类**画出来的展示物品**（用户 2026-09-27 诉求）：熔炉类 = 配方**材料**
     * （本格已解析的输入，多候选项按时间轮循），其余 = 产物。合成类的完整预览/产物分支
     * 不走它（那两条分支自己取产物）。
     */
    @Unique
    private ItemStack brbe$alternativesCellStack(RecipeBookKind kind) {
        if (kind == RecipeBookKind.FURNACE) {
            int autoIndex = Mth.floor(((OverlayRecipeComponentAccessor) field_3113).getTime() / 30.0f);
            for (Object rawPos : this.ingredientPos) {
                ItemStack[] ingredients = ((OverlayRecipeButtonPosAccessor) rawPos).brbe$getIngredients();
                if (ingredients.length > 0) {
                    return ingredients[Math.floorMod(autoIndex, ingredients.length)];
                }
            }
        }
        return this.recipe.value().getResultItem(field_3113.getRecipeCollection().registryAccess());
    }

    @Nullable
    @Override
    public java.util.List<Component> brbe$cellTooltip() {
        return this.brbe$drawnProduct.isEmpty()
                ? null
                : RecipeCellTooltips.forStack(null, this.brbe$drawnProduct, false);
    }

}
