package com.alonie.brbe.brewingstand;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.api.BRBBookCategories;
import com.alonie.brbe.api.BRBBookSettings;
import com.alonie.brbe.util.AlternativeOverlayLayout;
import com.alonie.brbe.util.BRBTextures;
import com.alonie.brbe.util.ClientCompat;
import com.google.common.collect.Lists;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.alonie.brbe.brewingstand.PlatformPotionUtil.getIngredient;

/**
 * 酿造台配方书的**替代配方组浮层**：同一瓶药水的多条酿造路线（用户 2026-09-26 诉求）。
 *
 * <p>26.3 的 {@code minecraft:brewing} 配方表里，**同一瓶药水常常有两条路线**——例：滞留型治疗药水
 * 既能"滞留型粗制药水 + 闪烁的西瓜片"，也能"治疗药水 + 龙息"（全数据 279 条配方 → 134 个唯一产物，
 * 其中 94 个产物有两条路线）。此前 {@code getCollectionsForCategory} 一条配方一个集合，于是配方书里
 * 出现**两个一模一样的格子**、且都单配方（没有替代配方组、右键无反应）。现在同一产物的路线合并成
 * 一个集合：折叠时一个格子、右键展开本浮层选路线。</p>
 *
 * <p>浮层格子画的是**该路线的酿造材料**（reagent，如闪烁的西瓜片 / 龙息）——一组的产物都一样，
 * 材料才是路线的区别；悬停某一格时 {@code BrewingRecipeBookPage} 会把该路线补进页面的
 * {@code hoveredRecipe}，于是工作区照常显示这条路线要放什么的幽灵预览（含输入药水形态）。</p>
 *
 * <p>结构与 {@code SmithingOverlayRecipeComponent} 同源，但路线数极少（原版最多 2 条），
 * 因此没有分页/滚轮翻页区。</p>
 */
public class BrewingOverlayRecipeComponent implements net.minecraft.client.gui.components.Renderable,
        net.minecraft.client.gui.components.events.GuiEventListener {
    private static final Identifier OVERLAY_RECIPE_SPRITE = Identifier.withDefaultNamespace("recipe_book/overlay_recipe");
    /** 列上限 4（与配方格同宽），行数按条目数。 */
    private static final int COLUMNS = 4;
    /** 配方格步距（既有布局：25px）。 */
    private static final int CELL = 25;

    private final List<RouteButton> recipeButtons = Lists.newArrayList();
    private BrewableResult lastRecipeClicked;
    private BrewingRecipeCollection collection;
    private boolean visible;
    private int x;
    private int y;

    /** 全量路线 + 各自的"可合成/残缺"标记（{@code init} 时算一次）。 */
    private final List<BrewableResult> allRecipes = Lists.newArrayList();
    private final List<Boolean> allCraftable = Lists.newArrayList();
    private final List<Boolean> allPartial = Lists.newArrayList();
    private RegistryAccess registryAccess;
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
        List<BrewableResult> otherRecipes;
        if (!BRBBookSettings.isFiltering(BetterRecipeBook.BREWING)) {
            otherRecipes = recipeCollection.getDisplayRecipes(false);
        } else {
            otherRecipes = recipeCollection.getPartiallyCraftableRecipes();
        }

        int craftableCount = craftableRecipes.size();
        int totalCount = craftableCount + otherRecipes.size();

        this.visible = true;
        this.registryAccess = registryAccess;

        // 残缺标记走集合的槽位状态缓存（O(1)，且与「残缺配方标记」开关同语义）
        this.allRecipes.clear();
        this.allCraftable.clear();
        this.allPartial.clear();
        for (int index = 0; index < totalCount; ++index) {
            boolean craftable = index < craftableCount;
            BrewableResult recipe = craftable
                    ? craftableRecipes.get(index)
                    : otherRecipes.get(index - craftableCount);
            this.allRecipes.add(recipe);
            this.allCraftable.add(craftable);
            this.allPartial.add(recipeCollection.isPartiallyMarked(recipe));
        }

        // 先建一次按钮只为拿到盒尺寸，位置算好后按新 x/y 重排（与锻造台浮层同一套写法）
        this.brbe$rebuildButtons();
        this.brbe$placeBox(anchorX, anchorY, panelLeft, panelTop);
        this.brbe$rebuildButtons();

        this.lastRecipeClicked = null;
    }

    /** 浮层位置：贴着被右键点击的那个组按钮展开（与原版合成书 / 锻造台同一套公式）。 */
    private void brbe$placeBox(int anchorX, int anchorY, int panelLeft, int panelTop) {
        Minecraft minecraft = Minecraft.getInstance();
        int[] box = AlternativeOverlayLayout.placeBox(anchorX, anchorY,
                Math.min(COLUMNS, Math.max(1, this.allRecipes.size())), COLUMNS,
                this.brbe$boxWidth(), this.brbe$boxHeight(),
                AlternativeOverlayLayout.pageCenterX(panelLeft), AlternativeOverlayLayout.pageCenterY(panelTop),
                CELL, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
        // 再夹进配方书面板（用户 2026-09-26：酿造台组浮层此前会超出书体）
        int[] clamped = AlternativeOverlayLayout.clampToBook(box[0], box[1], this.brbe$boxWidth(), this.brbe$boxHeight(), panelLeft, panelTop);
        this.x = clamped[0];
        this.y = clamped[1];
    }

    private void brbe$rebuildButtons() {
        this.recipeButtons.clear();
        for (int index = 0; index < this.allRecipes.size(); ++index) {
            int buttonX = this.x + 4 + CELL * (index % COLUMNS);
            int buttonY = this.y + 5 + CELL * (index / COLUMNS);
            this.recipeButtons.add(new RouteButton(buttonX, buttonY, this.allRecipes.get(index),
                    this.allCraftable.get(index), this.allPartial.get(index), this.registryAccess,
                    this.category, this.menu));
        }
    }

    private int brbe$boxWidth() {
        int columns = Math.max(1, Math.min(COLUMNS, this.recipeButtons.size()));
        return columns * CELL + 8;
    }

    private int brbe$boxHeight() {
        int rows = Math.max(1, Mth.ceil((float) this.recipeButtons.size() / (float) COLUMNS));
        return rows * CELL + 8;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != ClientCompat.MOUSE_LEFT) {
            return false;
        }

        for (RouteButton routeButton : this.recipeButtons) {
            if (!routeButton.mouseClicked(mouseX, mouseY, button)) {
                continue;
            }
            this.lastRecipeClicked = routeButton.getRecipe();
            return true;
        }

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
        return this.visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    @Override
    public void setFocused(boolean bl) {
    }

    @Override
    public boolean isFocused() {
        return false;
    }

    @Nullable
    public net.minecraft.client.gui.navigation.ScreenRectangle getBounds() {
        if (!this.visible || this.recipeButtons.isEmpty()) {
            return null;
        }
        return new net.minecraft.client.gui.navigation.ScreenRectangle(this.x, this.y,
                this.brbe$boxWidth(), this.brbe$boxHeight());
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return false;
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        if (!this.visible) {
            return;
        }

        int boxWidth = this.brbe$boxWidth();
        int boxHeight = this.brbe$boxHeight();
        ClientCompat.blitSprite(guiGraphics, OVERLAY_RECIPE_SPRITE, this.x, this.y, boxWidth, boxHeight);

        for (RouteButton routeButton : this.recipeButtons) {
            // ⚠️ 必须走**外层** render（26.x 的悬停标记只在它里面刷新）
            routeButton.render(guiGraphics, mouseX, mouseY, delta);
        }
    }

    /** 浮层内指针下的格子（null = 没有）：页面用它把悬停路线补进 {@code hoveredRecipe}。 */
    @Nullable
    public RouteButton hoveredButton() {
        if (!this.visible) {
            return null;
        }
        for (RouteButton button : this.recipeButtons) {
            if (button.isHovered()) {
                return button;
            }
        }
        return null;
    }

    /** 浮层里的一个**酿造路线**：画该路线的酿造材料（产物一组都一样，材料才是区别）。 */
    public static class RouteButton extends AbstractWidget {
        private final BrewableResult recipe;
        private final boolean craftable;
        private final boolean partial;
        private final RegistryAccess registryAccess;
        private final BRBBookCategories.Category category;
        @Nullable
        private final BrewingStandMenu menu;

        public RouteButton(int x, int y, BrewableResult recipe, boolean craftable, boolean partial,
                           RegistryAccess registryAccess, BRBBookCategories.Category category,
                           @Nullable BrewingStandMenu menu) {
            super(x, y, 24, 24, CommonComponents.EMPTY);
            this.recipe = recipe;
            this.craftable = craftable;
            this.partial = partial;
            this.registryAccess = registryAccess;
            this.category = category;
            this.menu = menu;
        }

        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return ClientCompat.mouseClicked(this, mouseX, mouseY, button);
        }

        public BrewableResult getRecipe() {
            return this.recipe;
        }

        /**
         * 这一格（= 一条酿造路线）的 tooltip（用户 2026-09-27 诉求：替代配方组里的配方要
         * 像普通配方那样有 tooltip）。
         *
         * <p>行序与 {@link BrewableRecipeButton#getTooltipText()} 完全一致——产物药水名 +
         * 药水效果 + 空行 + 「材料 → 输入药水」（白字 = 背包里已有、灰字 = 缺）；区别只在
         * 这里用的是**本路线自己的**材料与输入药水，所以同一组里每一格的 tooltip 各不相同。</p>
         */
        public List<Component> getTooltipText() {
            List<Component> list = Lists.newArrayList();

            ItemStack resultStack = this.recipe.getResult(this.registryAccess, this.category);
            list.add(resultStack.getHoverName());
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null) {
                PotionContents.addPotionTooltip(
                        resultStack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).getAllEffects(),
                        list::add,
                        1F,
                        minecraft.level.tickRateManager().tickrate()
                );
            }
            list.add(Component.empty());

            List<Slot> slots = this.menu == null ? List.of() : this.menu.slots;
            ItemStack carried = this.menu == null ? ItemStack.EMPTY : this.menu.getCarried();

            ChatFormatting colour = this.recipe.hasIngredient(slots, carried)
                    ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY;
            list.add(Component.literal(ClientCompat.firstIngredientItem(getIngredient(this.recipe.recipe))
                    .getHoverName().getString()).withStyle(colour));
            list.add(Component.literal("->").withStyle(ChatFormatting.DARK_GRAY));

            ItemStack inputStack = this.recipe.inputAsItemStack(this.category);
            if (!this.recipe.hasInput(this.category, slots, carried)) {
                colour = ChatFormatting.DARK_GRAY;
            }
            list.add(Component.literal(inputStack.getHoverName().getString()).withStyle(colour));

            return list;
        }

        @Override
        protected boolean isValidClickButton(MouseButtonInfo button) {
            return button.button() == ClientCompat.MOUSE_LEFT;
        }

        @Override
        public void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            this.defaultButtonNarrationText(narrationElementOutput);
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
            BetterRecipeBook.ensureCategories();
            Identifier sprite = BRBTextures.RECIPE_BOOK_PLAIN_OVERLAY_SPRITE.get(this.craftable || this.partial, this.isHoveredOrFocused());
            ClientCompat.blitSprite(guiGraphics, sprite, this.getX(), this.getY(), this.width, this.height);
            if (this.partial) {
                guiGraphics.fill(this.getX() + 1, this.getY() + 1, this.getX() + this.width - 1, this.getY() + this.height - 1, 0x60FF3333);
            }
            guiGraphics.renderFakeItem(this.brbe$reagentStack(), this.getX() + 4, this.getY() + 4);
            // 已固定的路线画图钉（与网格按钮 / 原版书的组浮层同款）——组浮层里 pin 的反馈
            // （用户 2026-09-27 反馈：自研书此前只有"组能 pin"，变体 pin 没有任何标记）
            if (this.recipe != null && BetterRecipeBook.pinnedRecipeManager.pinned.contains(this.recipe.id())) {
                ClientCompat.blitSprite(guiGraphics, BRBTextures.RECIPE_BOOK_PIN_SPRITE,
                        this.getX() - 4, this.getY() - 4, 32, 32);
            }
        }

        /** 该路线的酿造材料（tag 取第一个变体；与幽灵预览取的是同一个）。 */
        private ItemStack brbe$reagentStack() {
            return ClientCompat.firstIngredientItem(getIngredient(this.recipe.recipe));
        }
    }
}
