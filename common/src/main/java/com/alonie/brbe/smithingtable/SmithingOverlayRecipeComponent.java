package com.alonie.brbe.smithingtable;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.systems.RenderSystem;
import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.api.BRBBookSettings;
import com.alonie.brbe.recipe.BRBSmithingRecipe;
import com.alonie.brbe.util.AlternativeOverlayLayout;
import com.alonie.brbe.util.BRBTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class SmithingOverlayRecipeComponent implements Renderable, GuiEventListener {
    private final List<OverlayRecipeButton> recipeButtons = Lists.newArrayList();
    private BRBSmithingRecipe lastRecipeClicked;
    private SmithingRecipeCollection collection;
    private boolean isVisible;
    private static final ResourceLocation OVERLAY_RECIPE_SPRITE = ResourceLocation.withDefaultNamespace("recipe_book/overlay_recipe");
    float time;
    private int y;
    private int x;

    public void init(SmithingRecipeCollection recipeCollection, int anchorX, int anchorY,
                     int panelLeft, int panelTop, RegistryAccess registryAccess) {
        this.collection = recipeCollection;

        List<BRBSmithingRecipe> lockedRecipes = recipeCollection.getDisplayRecipes(true);
        List<BRBSmithingRecipe> unlockedRecipes = BRBBookSettings.isFiltering(BetterRecipeBook.SMITHING) ? Collections.emptyList() : recipeCollection.getDisplayRecipes(false);
        int lockedRecipeCount = lockedRecipes.size();
        int totalRecipeCount = lockedRecipeCount + unlockedRecipes.size();
        int columns = totalRecipeCount <= 16 ? 4 : 5;
        // 浮层位置：**贴着被右键点击的那个组按钮**展开（与原版合成书 / 锻造台同一套公式，
        // 见 AlternativeOverlayLayout#placeBox；此前写死"面板 +7,+26" → 永远开在配方书左上角）。
        int boxW = Math.max(1, Math.min(totalRecipeCount, columns)) * 25 + 8;
        int boxH = Math.max(1, Mth.ceil((float) totalRecipeCount / (float) columns)) * 25 + 8;
        int[] box = AlternativeOverlayLayout.placeBox(anchorX, anchorY,
                Math.max(1, Math.min(totalRecipeCount, columns)), columns, boxW, boxH,
                AlternativeOverlayLayout.pageCenterX(panelLeft), AlternativeOverlayLayout.pageCenterY(panelTop),
                AlternativeOverlayLayout.CELL,
                Minecraft.getInstance().getWindow().getGuiScaledWidth(),
                Minecraft.getInstance().getWindow().getGuiScaledHeight());
        // 再夹进配方书面板（用户 2026-09-26：酿造台组浮层此前会超出书体）
        int[] clamped = AlternativeOverlayLayout.clampToBook(box[0], box[1], boxW, boxH, panelLeft, panelTop);
        this.x = clamped[0];
        this.y = clamped[1];

        this.isVisible = true;
        this.recipeButtons.clear();

        List<BRBSmithingRecipe> partialRecipes = recipeCollection.getPartiallyCraftableRecipes();

        for (int index = 0; index < totalRecipeCount; ++index) {
            boolean isCraftable = index < lockedRecipeCount;
            BRBSmithingRecipe recipeHolder = isCraftable ? lockedRecipes.get(index) : unlockedRecipes.get(index - lockedRecipeCount);
            boolean partial = partialRecipes.stream().anyMatch(r -> r.id().equals(recipeHolder.id()));
            int buttonX = this.x + 4 + 25 * (index % columns);
            int buttonY = this.y + 5 + 25 * (index / columns);
            this.recipeButtons.add(new OverlayRecipeButton(buttonX, buttonY, recipeHolder, isCraftable, registryAccess, partial));
        }

        this.lastRecipeClicked = null;
    }

    public boolean mouseClicked(double d, double e, int i) {
        if (i != 0) {
            return false;
        }
        for (OverlayRecipeButton overlayRecipeButton : this.recipeButtons) {
            if (!overlayRecipeButton.mouseClicked(d, e, i)) continue;
            this.lastRecipeClicked = overlayRecipeButton.recipe;
            return true;
        }
        return false;
    }

    public boolean isMouseOver(double d, double e) {
        return false;
    }

    @Nullable
    public BRBSmithingRecipe getLastRecipeClicked() {
        return this.lastRecipeClicked;
    }

    public SmithingRecipeCollection getRecipeCollection() {
        return this.collection;
    }

    public boolean isVisible() {
        return this.isVisible;
    }

    public void setVisible(boolean b) {
        this.isVisible = b;
    }

    public void setFocused(boolean bl) {
    }

    public boolean isFocused() {
        return false;
    }

    public ScreenRectangle getBounds() {
        if (this.recipeButtons.isEmpty()) {
            return null;
        }

        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int bottom = Integer.MIN_VALUE;

        for (OverlayRecipeButton button : this.recipeButtons) {
            left = Math.min(left, button.getX());
            top = Math.min(top, button.getY());
            right = Math.max(right, button.getX() + button.getWidth());
            bottom = Math.max(bottom, button.getY() + button.getHeight());
        }

        left -= 4;
        top -= 5;
        right += 5;
        bottom += 4;
        return new ScreenRectangle(left, top, right - left, bottom - top);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int i, int j, float f) {
        if (this.isVisible) {
            this.time += f;
            RenderSystem.enableBlend();
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0.0F, 0.0F, 1000.0F);
            int k = this.recipeButtons.size() <= 16 ? 4 : 5;
            int l = Math.min(this.recipeButtons.size(), k);
            int m = Mth.ceil((float) this.recipeButtons.size() / (float) k);
            guiGraphics.blitSprite(OVERLAY_RECIPE_SPRITE, this.x, this.y, l * 25 + 8, m * 25 + 8);
            RenderSystem.disableBlend();

            for (OverlayRecipeButton overlayRecipeButton : this.recipeButtons) {
                // 走外层 render：AbstractWidget 在那里用 isMouseOver 刷新悬停标记。
                overlayRecipeButton.render(guiGraphics, i, j, f);
            }

            guiGraphics.pose().popPose();
        }
    }

    /** 组浮层内指针下的按钮（null = 没有）：{@code SmithingRecipeBookPage} 用它把
     *  悬停变体补进页面的 {@code hoveredRecipe}，悬停预览便能在组内配方上生效
     *  （用户 2026-09-25 反馈：锻造台替代配方组里的配方无法触发自动填充幽灵配方）。 */
    @Nullable
    public OverlayRecipeButton hoveredButton() {
        if (!this.isVisible) {
            return null;
        }
        for (OverlayRecipeButton button : this.recipeButtons) {
            if (button.isHovered()) {
                return button;
            }
        }
        return null;
    }

    public static class OverlayRecipeButton extends AbstractWidget {
        final BRBSmithingRecipe recipe;
        private final boolean isCraftable;
        private final boolean partial;
        private RegistryAccess registryAccess;

        public OverlayRecipeButton(int i, int j, BRBSmithingRecipe smithableResult, boolean isCraftable, RegistryAccess registryAccess, boolean partial) {
            super(i, j, 200, 20, CommonComponents.EMPTY);
            this.width = 24;
            this.height = 24;
            this.recipe = smithableResult;
            this.isCraftable = isCraftable;
            this.registryAccess = registryAccess;
            this.partial = partial;
        }

        public void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            this.defaultButtonNarrationText(narrationElementOutput);
        }

        public BRBSmithingRecipe getRecipe() {
            return this.recipe;
        }

        /**
         * 这一格的 tooltip（用户 2026-09-27 诉求：替代配方组里的配方要像普通配方那样有 tooltip）。
         *
         * <p>行内容 = 该格**画出来的那件产物**的 tooltip（+ 空行 + 模组名），与该书普通配方格
         * 完全同款。没有「单击鼠标右键获取更多信息」那行：格子本身就是展开后的单个变体，
         * 右键不再展开任何东西。</p>
         */
        public java.util.List<net.minecraft.network.chat.Component> getTooltipText() {
            return com.alonie.brbe.util.RecipeCellTooltips.forStack(this.registryAccess,
                    this.recipe.getResult(this.registryAccess, BetterRecipeBook.SMITHING_SEARCH), false);
        }

        public void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
            ResourceLocation resourceLocation;

            // Partial recipes keep the craftable sprite (light border); the red overlay marks them.
            resourceLocation = BRBTextures.RECIPE_BOOK_PLAIN_OVERLAY_SPRITE.get(this.isCraftable || this.partial, isHoveredOrFocused());

            guiGraphics.blitSprite(resourceLocation, this.getX(), this.getY(), this.width, this.height);
            if (this.partial) {
                guiGraphics.fill(this.getX() + 1, this.getY() + 1, this.getX() + this.width - 1, this.getY() + this.height - 1, 0x60FF3333);
            }
            guiGraphics.pose().pushPose();
//            guiGraphics.pose().translate(this.getX() + 2, this.getY() + 2, 150.0);

            int offset = 4;
            // Currently, the trim does not use the category passed at all. Only brewing uses the category. soooo its
            // fineeeee to not use the current category. I hate OOP.
            guiGraphics.renderFakeItem(recipe.getResult(registryAccess, BetterRecipeBook.SMITHING_SEARCH), getX() + offset, getY() + offset);

            guiGraphics.pose().popPose();
        }
    }
}
