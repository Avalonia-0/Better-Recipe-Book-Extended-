package com.alonie.brbe.generic;

import com.google.common.collect.Lists;
import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.api.BRBBookCategories;
import com.alonie.brbe.layout.BookLayout;
import com.alonie.brbe.util.BRBTextures;
import com.alonie.brbe.util.RecipeCellTooltips;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.function.Supplier;

public class GenericRecipeButton<C extends GenericRecipeBookCollection<R, M>, R extends GenericRecipe, M extends AbstractContainerMenu> extends AbstractWidget {
    private final Supplier<Boolean> filteringSupplier;
    protected C collection;
    protected M menu;
    protected float time;
    protected int currentIndex;
    protected RegistryAccess registryAccess;
    protected BRBBookCategories.Category category;

    public GenericRecipeButton(RegistryAccess registryAccess, Supplier<Boolean> filteringSupplier) {
        super(0, 0, BookLayout.BUTTON_SIZE, BookLayout.BUTTON_SIZE, CommonComponents.EMPTY);
        this.registryAccess = registryAccess;
        this.filteringSupplier = filteringSupplier;
    }

    public void showCollection(C collection, M smithingMenu, BRBBookCategories.Category category) {
        this.collection = collection;
        this.menu = smithingMenu;
        this.category = category;
    }

    public void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float delta) {
        if (this.collection == null) return;

        if (!Screen.hasControlDown()) {
            this.time += delta;
        }

        List<R> list = getOrderedRecipes();

        if (list.isEmpty()) {
            return;
        }

        this.currentIndex = Mth.floor(this.time / 30.0F) % list.size();

        R current = getCurrentDisplayedRecipe();
        boolean isPartial = current != null
                && this.collection.getPartiallyCraftableRecipes().stream()
                        .anyMatch(r -> r.id().equals(current.id()));

        // blit outline texture — use craftable sprite for partial recipes
        // so they get the light-coloured border (red fill is drawn below)
        boolean effectiveCraftable = collection.atleastOneCraftable(menu.slots) || isPartial;
        ResourceLocation outlineTexture = effectiveCraftable ?
                BRBTextures.RECIPE_BOOK_BUTTON_SLOT_CRAFTABLE_SPRITE : BRBTextures.RECIPE_BOOK_BUTTON_SLOT_UNCRAFTABLE_SPRITE;

        // partial recipes: use the red-check sprite from the compat pack when available,
        // otherwise fall back to the vanilla sprite + red overlay
        boolean redCheck = isPartial && BRBTextures.hasPartialSprite();
        gui.blitSprite(redCheck ? BRBTextures.RECIPE_BOOK_BUTTON_SLOT_PARTIAL_SPRITE : outlineTexture,
                getX(), getY(), this.width, this.height);

        // red overlay for partially craftable recipes (drawn before item so item shows on top)
        if (isPartial && !redCheck) {
            gui.fill(getX() + 1, getY() + 1, getX() + this.width - 1, getY() + this.height - 1, 0x60FF3333);
        }

        // 展示物品走 getDisplayedStack：默认 = 当前轮循配方的产物，子类可整格换（纹饰组 → 模板）
        ItemStack result = this.getDisplayedStack(category);

        // render ingredient item (on top of red overlay)
        int offset = BookLayout.PIN_SPRITE_OFFSET;
        gui.renderFakeItem(result, getX() + offset, getY() + offset);

        // if pinned recipe, blit the pin texture at top-right outer corner
        // 仅"全 pin 组"（含 Stage 6 生成的副本组）显示 pin 贴图
        if (BetterRecipeBook.pinnedRecipeManager.isFullyPinned(collection)) {
            gui.blitSprite(BRBTextures.RECIPE_BOOK_PIN_SPRITE,
                    getX() + getWidth() - BookLayout.PIN_SPRITE_OFFSET,
                    getY() - BookLayout.PIN_SPRITE_OFFSET,
                    BookLayout.PIN_SPRITE_SIZE, BookLayout.PIN_SPRITE_SIZE);
        }
    }

    public R getCurrentDisplayedRecipe() {
        List<R> list = getOrderedRecipes();

        return list.get(currentIndex);
    }

    public boolean isOnlyOption() {
        return this.getOrderedRecipes().size() == 1;
    }

    /**
     * 本格是否参与「悬停即预览幽灵配方」：默认参与。子类可以声明不参与
     * （锻造台纹饰组——那一格展示的是模板、内部不轮循，悬停它不该往工作区写任何东西）。
     */
    public boolean providesHoverPreview() {
        return true;
    }

    public List<R> getOrderedRecipes() {
        C coll = this.getCollection();
        if (coll == null) return List.of();

        List<R> list = coll.getDisplayRecipes(true);

        if (!this.filteringSupplier.get()) {
            list.addAll(this.collection.getDisplayRecipes(false));
        } else {
            list.addAll(this.collection.getPartiallyCraftableRecipes());
        }

        return list;
    }

    public C getCollection() {
        return this.collection;
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput builder) {
//        ItemStack inputStack = this.getCollection().getFirst().inputAsItemStack(group);
//
//        builder.add(NarratedElementType.TITLE, Component.translatable("narration.recipe", inputStack.getHoverName()));
//        builder.add(NarratedElementType.USAGE, Component.translatable("narration.button.usage.hovered"));
    }

    public int getWidth() {
        return BookLayout.BUTTON_SIZE;
    }

    protected boolean isValidClickButton(int i) {
        return i == 0 || i == 1;
    }

    public List<Component> getTooltipText() {
        return this.getTooltipFor(this.getDisplayedStack(this.category));
    }

    /** 由**展示物品**构建单元格 tooltip（子类可整格换展示，见 {@link #getDisplayedStack}）。 */
    protected List<Component> getTooltipFor(ItemStack result) {
        // 与原版一致（`RecipeButton#getTooltipText`：hasMultipleRecipes() → MORE_RECIPES_TOOLTIP）：
        // 这一格是"多个替代配方"的组、右键能展开更多时补一行提示。用户 2026-09-26 反馈：
        // 酿造台/锻造台的自研配方书漏了这一行。
        // 行内容与**替代配方组浮层**里的格子共用（RecipeCellTooltips），保证两边格式一致；
        // 该 helper 同时补齐模组名行——与另三个分支的同名方法一致（原先只有本分支缺）。
        return RecipeCellTooltips.forStack(this.registryAccess, result, this.getOrderedRecipes().size() > 1);
    }

    /**
     * 单元格**画出来**的那件物品（默认 = 当前轮循配方的产物）。
     *
     * <p>锻造台的**纹饰组**覆写它：原版一条 {@code smithing_trim} 配方的 base 是
     * {@code #minecraft:trimmable_armor} 标签，展开成"每种可纹饰装备一件"的一整组，
     * 折叠时轮循各件装备没有信息量——整组共用的**纹饰模板**才是这一组的 identity
     * （用户 2026-09-26 诉求）。</p>
     */
    protected ItemStack getDisplayedStack(BRBBookCategories.Category category) {
        R current = this.getCurrentDisplayedRecipe();
        return current == null ? ItemStack.EMPTY : current.getResult(this.registryAccess, category);
    }
}
