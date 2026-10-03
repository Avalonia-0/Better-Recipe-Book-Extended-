package com.alonie.brbe.brewingstand;

import com.google.common.collect.Lists;
import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.api.BRBBookCategories;
import com.alonie.brbe.api.BRBBookSettings;
import com.alonie.brbe.util.AlternativeOverlayLayout;
import com.alonie.brbe.util.BRBTextures;
import com.alonie.brbe.util.ClientCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

import static com.alonie.brbe.brewingstand.PlatformPotionUtil.getIngredient;

/**
 * 酿造台配方书的**替代配方组浮层**：同一瓶药水的多条酿造路线（用户 2026-09-26 诉求）。
 *
 * <p>同一瓶药水常常有两条路线（例：滞留型治疗药水 = 滞留型粗制 + 闪烁的西瓜片，或
 * 治疗药水 + 龙息）。此前一条配方一个集合 → 配方书里出现**两个一模一样的格子**且都不是组；
 * 现在同一产物的路线合并成一个集合：折叠时一个格子、右键展开本浮层选路线。</p>
 *
 * <p>格子画的是该路线的**酿造材料**（产物一组都一样，材料才是区别）；悬停某一格时页面会把
 * 该路线补进 {@code hoveredRecipe}，工作区照常显示这条路线要放什么的幽灵预览。</p>
 */
public class BrewingOverlayRecipeComponent implements Renderable, GuiEventListener {
    private final List<RouteButton> recipeButtons = Lists.newArrayList();
    private BrewableResult lastRecipeClicked;
    private BrewingRecipeCollection collection;
    private boolean isVisible;
    private static final ResourceLocation OVERLAY_RECIPE_SPRITE = ResourceLocation.withDefaultNamespace("recipe_book/overlay_recipe");
    float time;
    private int y;
    private int x;

    /** 本页所属标签页与酿造台菜单：组内格子的 tooltip 要按它们取"产物形态/输入形态"
     *  与"背包里有没有"的白/灰配色（与 {@code BrewableRecipeButton} 同一套）。 */
    private BRBBookCategories.Category category;
    private BrewingStandMenu menu;

    public void init(BrewingRecipeCollection recipeCollection, BRBBookCategories.Category category,
                     BrewingStandMenu menu, int anchorX, int anchorY,
                     int panelLeft, int panelTop, RegistryAccess registryAccess) {
        this.collection = recipeCollection;
        this.category = category;
        this.menu = menu;

        List<BrewableResult> craftableRecipes = recipeCollection.getDisplayRecipes(true);
        List<BrewableResult> otherRecipes = BRBBookSettings.isFiltering(BetterRecipeBook.BREWING)
                ? recipeCollection.getPartiallyCraftableRecipes()
                : recipeCollection.getDisplayRecipes(false);
        int craftableCount = craftableRecipes.size();
        int totalCount = craftableCount + otherRecipes.size();
        int columns = totalCount <= 16 ? 4 : 5;

        // 浮层位置：**贴着被右键点击的那个组按钮**展开（与原版合成书 / 锻造台同一套公式，
        // 见 AlternativeOverlayLayout#placeBox；此前写死"面板 +7,+26" → 永远开在配方书左上角）。
        int boxW = Math.max(1, Math.min(totalCount, columns)) * 25 + 8;
        int boxH = Math.max(1, Mth.ceil((float) totalCount / (float) columns)) * 25 + 8;
        int[] box = AlternativeOverlayLayout.placeBox(anchorX, anchorY,
                Math.max(1, Math.min(totalCount, columns)), columns, boxW, boxH,
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

        List<BrewableResult> partialRecipes = recipeCollection.getPartiallyCraftableRecipes();

        for (int index = 0; index < totalCount; ++index) {
            boolean craftable = index < craftableCount;
            BrewableResult recipe = craftable ? craftableRecipes.get(index) : otherRecipes.get(index - craftableCount);
            boolean partial = partialRecipes.stream().anyMatch(r -> r.id().equals(recipe.id()));
            int buttonX = this.x + 4 + 25 * (index % columns);
            int buttonY = this.y + 5 + 25 * (index / columns);
            this.recipeButtons.add(new RouteButton(buttonX, buttonY, recipe, craftable, registryAccess, partial,
                    category, menu));
        }


        this.lastRecipeClicked = null;
    }

    public boolean mouseClicked(double d, double e, int i) {
        if (i != 0) {
            return false;
        }

        for (RouteButton routeButton : this.recipeButtons) {
            if (!routeButton.mouseClicked(d, e, i)) {
                continue;
            }
            this.lastRecipeClicked = routeButton.recipe;
            return true;
        }

        return false;
    }

    public boolean isMouseOver(double d, double e) {
        return false;
    }

    @Nullable
    public BrewableResult getLastRecipeClicked() {
        return this.lastRecipeClicked;
    }

    public BrewingRecipeCollection getRecipeCollection() {
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

    @Nullable
    public ScreenRectangle getBounds() {
        if (!this.isVisible || this.recipeButtons.isEmpty()) {
            return null;
        }

        int columns = this.recipeButtons.size() <= 16 ? 4 : 5;
        int visibleColumns = Math.min(this.recipeButtons.size(), columns);
        int rows = Mth.ceil((float) this.recipeButtons.size() / (float) columns);
        return new ScreenRectangle(this.x, this.y, visibleColumns * 25 + 8, rows * 25 + 8);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int i, int j, float f) {
        if (!this.isVisible) {
            return;
        }

        this.time += f;
        guiGraphics.pose().pushPose();
        int columns = this.recipeButtons.size() <= 16 ? 4 : 5;
        int visibleColumns = Math.min(this.recipeButtons.size(), columns);
        int rows = Mth.ceil((float) this.recipeButtons.size() / (float) columns);
        guiGraphics.blitSprite(OVERLAY_RECIPE_SPRITE, this.x, this.y, visibleColumns * 25 + 8, rows * 25 + 8);

        for (RouteButton routeButton : this.recipeButtons) {
            // ⚠️ 必须走**外层** render（悬停标记只在它里面刷新）
            routeButton.render(guiGraphics, i, j, f);
        }

        guiGraphics.pose().popPose();
    }

    /** 浮层内指针下的格子（null = 没有）：页面用它把悬停路线补进 {@code hoveredRecipe}。 */
    @Nullable
    public RouteButton hoveredButton() {
        if (!this.isVisible) {
            return null;
        }
        for (RouteButton button : this.recipeButtons) {
            if (button.isHoveredOrFocused()) {
                return button;
            }
        }
        return null;
    }

    /** 浮层里的一个**酿造路线**：画该路线的酿造材料（产物一组都一样，材料才是区别）。 */
    public static class RouteButton extends AbstractWidget {
        final BrewableResult recipe;
        private final boolean isCraftable;
        private final boolean partial;
        private RegistryAccess registryAccess;
        private final BRBBookCategories.Category category;
        @Nullable
        private final BrewingStandMenu menu;

        public RouteButton(int i, int j, BrewableResult recipe, boolean isCraftable, RegistryAccess registryAccess,
                           boolean partial, BRBBookCategories.Category category,
                           @Nullable BrewingStandMenu menu) {
            super(i, j, 200, 20, CommonComponents.EMPTY);
            this.width = 24;
            this.height = 24;
            this.recipe = recipe;
            this.isCraftable = isCraftable;
            this.registryAccess = registryAccess;
            this.partial = partial;
            this.category = category;
            this.menu = menu;
        }

        /**
         * 这一格（= 一条酿造路线）的 tooltip（用户 2026-09-27 诉求：替代配方组里的配方要
         * 像普通配方那样有 tooltip）。
         *
         * <p>行序与 {@code BrewableRecipeButton#getTooltipText()} 完全一致——产物药水名 +
         * 药水效果 + 空行 + 「材料 ↓ 输入药水」（白字 = 背包里已有、灰字 = 缺）；区别只在
         * 这里用的是**本路线自己的**材料与输入药水，所以同一组里每一格的 tooltip 各不相同。</p>
         */
        public java.util.List<Component> getTooltipText() {
            java.util.List<Component> list = Lists.newArrayList();

            ItemStack resultStack = this.recipe.getResult(this.registryAccess, this.category);
            list.add(resultStack.getHoverName());
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null) {
                resultStack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY)
                        .addPotionTooltip(list::add, 1F, minecraft.level.tickRateManager().tickrate());
            }
            list.add(Component.literal(""));

            java.util.List<Slot> slots = this.menu == null ? java.util.List.of() : this.menu.slots;
            ItemStack carried = this.menu == null ? ItemStack.EMPTY : this.menu.getCarried();

            ChatFormatting colour = ChatFormatting.DARK_GRAY;
            if (this.recipe.hasIngredient(slots, carried)) {
                colour = ChatFormatting.WHITE;
            }
            list.add(Component.literal(getIngredient(this.recipe.recipe).getItems()[0].getHoverName().getString())
                    .withStyle(colour));

            list.add(Component.literal("↓").withStyle(ChatFormatting.DARK_GRAY));

            ItemStack inputStack = this.recipe.inputAsItemStack(this.category);
            if (!this.recipe.hasInput(this.category, slots, carried)) {
                colour = ChatFormatting.DARK_GRAY;
            }
            list.add(Component.literal(inputStack.getHoverName().getString()).withStyle(colour));

            return list;
        }

        public void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            this.defaultButtonNarrationText(narrationElementOutput);
        }

        public BrewableResult getRecipe() {
            return this.recipe;
        }

        public void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
            ResourceLocation resourceLocation = BRBTextures.RECIPE_BOOK_PLAIN_OVERLAY_SPRITE.get(this.isCraftable || this.partial, isHoveredOrFocused());

            guiGraphics.blitSprite(resourceLocation, this.getX(), this.getY(), this.width, this.height);
            if (this.partial) {
                guiGraphics.fill(this.getX() + 1, this.getY() + 1, this.getX() + this.width - 1, this.getY() + this.height - 1, 0x60FF3333);
            }
            int offset = 4;
            guiGraphics.renderFakeItem(ClientCompat.firstIngredientItem(getIngredient(this.recipe.recipe)), getX() + offset, getY() + offset);
        }
    }
}
