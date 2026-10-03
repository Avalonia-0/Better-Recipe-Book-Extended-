package com.alonie.brbe.generic;

import com.google.common.collect.Lists;
import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.api.BRBBookCategories;
import com.alonie.brbe.util.ClientCompat;
import com.alonie.brbe.util.BRBTextures;
import com.alonie.brbe.util.PageAnimationEdges;
import com.alonie.brbe.util.RecipeCellTooltips;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

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
        super(0, 0, 25, 25, CommonComponents.EMPTY);
        this.registryAccess = registryAccess;
        this.filteringSupplier = filteringSupplier;
    }

    public void showCollection(C collection, M smithingMenu, BRBBookCategories.Category category) {
        this.collection = collection;
        this.menu = smithingMenu;
        this.category = category;
    }

    public void extractWidgetRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        if (this.collection == null) return;

        if (!ClientCompat.isControlDown()) {
            this.time += delta;
        }

        List<R> list = getOrderedRecipes();

        if (list.isEmpty()) {
            return;
        }

        this.currentIndex = Mth.floor(this.time / 30.0F) % list.size();

        R current = getCurrentDisplayedRecipe();
        // 残缺判定走集合的槽位状态缓存（同 id 视为同一配方，与既有语义一致）
        boolean isPartial = current != null && this.collection.isPartiallyMarked(current);

        // blit outline texture — use craftable sprite for partial recipes
        // so they get the light-coloured border (red fill is drawn below)
        boolean effectiveCraftable = current != null
                && (collection.isCraftable(current, menu.slots) || isPartial);
        Identifier outlineTexture = effectiveCraftable ?
                BRBTextures.RECIPE_BOOK_BUTTON_SLOT_CRAFTABLE_SPRITE : BRBTextures.RECIPE_BOOK_BUTTON_SLOT_UNCRAFTABLE_SPRITE;

        // partial recipes: use the red-check sprite from the compat pack when available,
        // otherwise fall back to the vanilla sprite + red overlay
        boolean redCheck = isPartial && ClientCompat.hasSpriteResource(BRBTextures.RECIPE_BOOK_BUTTON_SLOT_PARTIAL_SPRITE);
        ClientCompat.blitSprite(gui, redCheck ? BRBTextures.RECIPE_BOOK_BUTTON_SLOT_PARTIAL_SPRITE : outlineTexture,
                getX(), getY(), this.width, this.height);

        // red overlay for partially craftable recipes (drawn before item so item shows on top)
        if (isPartial && !redCheck) {
            gui.fill(getX() + 1, getY() + 1, getX() + this.width - 1, getY() + this.height - 1, 0x60FF3333);
        }

        // 展示物品走 getDisplayedStack：默认 = 当前轮循配方的产物，子类可整格换（纹饰组 → 模板）
        ItemStack result = this.getDisplayedStack(category);

        // render ingredient item (on top of red overlay)
        int offset = 4;
        gui.fakeItem(result, getX() + offset, getY() + offset);

        // if pinned recipe, blit the pin texture over it
        if (BetterRecipeBook.pinnedRecipeManager.isFullyPinned(collection)) {
            ClientCompat.blitSprite(gui, BRBTextures.RECIPE_BOOK_PIN_SPRITE, getX() - 4, getY() - 4, 32, 32);
        }
    }

    public R getCurrentDisplayedRecipe() {
        List<R> list = getOrderedRecipes();
        if (list.isEmpty()) {
            return null;
        }
        if (this.currentIndex >= list.size()) {
            // 动画挤压路径（renderSquashed）可能在 showCollection 换集合后仍带着上一个
            // 集合的 currentIndex 直接取数；按当前列表长度取模兜底，避免越界崩溃。
            this.currentIndex = this.currentIndex % list.size();
        }
        return list.get(this.currentIndex);
    }

    /**
     * 挤压离场渲染：配方格子以给定宽度渲染（blit 贴图被横向压缩），用于配方
     * 滑出视窗边界时被挤压消失的效果。右/左边缘由 slotX/width 决定（边缘钳制在
     * 视窗边界），图标在格子宽度不足以容纳时（&lt;=20）消失。
     */
    public void renderSquashed(GuiGraphicsExtractor gui, int slotX, int width, int bx, int slotY) {
        if (this.collection == null || width <= 0) {
            return;
        }
        List<R> list = getOrderedRecipes();
        if (list.isEmpty()) {
            return;
        }
        R current = getCurrentDisplayedRecipe();
        if (current == null) {
            return;
        }
        boolean isPartial = this.collection.isPartiallyMarked(current);
        boolean effectiveCraftable = collection.isCraftable(current, menu.slots) || isPartial;
        Identifier outlineTexture = effectiveCraftable ?
                BRBTextures.RECIPE_BOOK_BUTTON_SLOT_CRAFTABLE_SPRITE : BRBTextures.RECIPE_BOOK_BUTTON_SLOT_UNCRAFTABLE_SPRITE;
        boolean redCheck = isPartial && ClientCompat.hasSpriteResource(BRBTextures.RECIPE_BOOK_BUTTON_SLOT_PARTIAL_SPRITE);
        Identifier sprite = redCheck ? BRBTextures.RECIPE_BOOK_BUTTON_SLOT_PARTIAL_SPRITE : outlineTexture;
        int rightBound = slotX + width;
        if (width < 25) {
            // 伪压缩：内容在 [slotX, rightBound] 内裁剪（中间随滑动变短）。图标完整跟随
            // 配方位置滑出视窗；左右边界 2px 最后渲染（上层），盖住经过边界的图标。
            // 内容 scissor 右边界收窄 1px：原版 sprite 最右列（col24）顶部 1px 是
            // 透明的，若内容画到该列，滚动时下层配方会从缺口漏出。收窄后缺口处
            // 不绘制任何下层内容，仅显示背景（保持透明效果）。
            gui.enableScissor(slotX, slotY, rightBound - 1, slotY + this.height);
            ClientCompat.blitSprite(gui, sprite, bx, slotY, this.width, this.height);
            if (isPartial && !redCheck) {
                gui.fill(slotX + 1, slotY + 1, rightBound - 1, slotY + this.height - 1, 0x60FF3333);
            }
            gui.disableScissor();
            ItemStack result = this.getDisplayedStack(category);
            gui.fakeItem(result, bx + 4, slotY + 4);
            // 移动方向的前方边缘盖住后方边缘：配方左移（左端被边界压扁）时左边界
            // 最后渲染（在上层），右移时右边界在上层。
            boolean movingLeft = bx < slotX;
            if (movingLeft) {
                // 右边界先画（下层）
                gui.enableScissor(Math.max(rightBound - PageAnimationEdges.right(), slotX), slotY, rightBound, slotY + this.height);
                ClientCompat.blitSprite(gui, sprite, rightBound - this.width, slotY, this.width, this.height);
                gui.disableScissor();
                // 左边界最后（上层，盖住右边界）
                gui.enableScissor(slotX, slotY, Math.min(slotX + PageAnimationEdges.left(), rightBound), slotY + this.height);
                ClientCompat.blitSprite(gui, sprite, slotX, slotY, this.width, this.height);
                gui.disableScissor();
            } else {
                // 左边界先画（下层）
                gui.enableScissor(slotX, slotY, Math.min(slotX + PageAnimationEdges.left(), rightBound), slotY + this.height);
                ClientCompat.blitSprite(gui, sprite, slotX, slotY, this.width, this.height);
                gui.disableScissor();
                // 右边界最后（上层，盖住左边界）
                gui.enableScissor(Math.max(rightBound - PageAnimationEdges.right(), slotX), slotY, rightBound, slotY + this.height);
                ClientCompat.blitSprite(gui, sprite, rightBound - this.width, slotY, this.width, this.height);
                gui.disableScissor();
            }
        } else {
            ClientCompat.blitSprite(gui, sprite, slotX, slotY, this.width, this.height);
            if (isPartial && !redCheck) {
                gui.fill(slotX + 1, slotY + 1, slotX + this.width - 1, slotY + this.height - 1, 0x60FF3333);
            }
            ItemStack result = this.getDisplayedStack(category);
            gui.fakeItem(result, bx + 4, slotY + 4);
        }
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
        // getDisplayRecipes 返回**缓存本体**（只读），这里复制一份再拼装；
        // 三个子列表都来自集合的槽位状态缓存，同一帧内不重复扫描配方材料。
        List<R> list = Lists.newArrayList(this.getCollection().getDisplayRecipes(true));

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
        return 25;
    }

    protected boolean isValidClickButton(int i) {
        return i == 0 || i == 1;
    }

    @Override
    protected boolean isValidClickButton(MouseButtonInfo button) {
        return this.isValidClickButton(button.button());
    }

    public List<Component> getTooltipText() {
        return getTooltipText(getCurrentDisplayedRecipe(), this.category);
    }

    /**
     * 由指定配方与类别构建 tooltip。动画中两页共用按钮、内容会被下一页覆盖，
     * 悬停瞬间须捕获配方后调用本重载，避免读到错页内容。
     */
    public List<Component> getTooltipText(R recipe, BRBBookCategories.Category category) {
        if (recipe == null) {
            return Lists.newArrayList();
        }
        return this.getTooltipFor(recipe.getResult(registryAccess, category));
    }

    /**
     * 由**展示物品**构建单元格 tooltip。
     *
     * <p>物品行的来源与 {@link #getDisplayedStack} 一致（子类可以把整格换成别的东西展示，
     * tooltip 跟着换，例如锻造台纹饰组显示模板）；行序与原版 `RecipeButton#getTooltipText`
     * 相同：物品行 → 「单击鼠标右键获取更多信息」→ 空行 + 模组名。</p>
     */
    protected List<Component> getTooltipFor(ItemStack result) {
        // 与原版一致（javap `RecipeButton#getTooltipText`：hasMultipleRecipes() → MORE_RECIPES_TOOLTIP）：
        // 这一格是"多个替代配方"的组、右键能展开更多时补一行提示。用户 2026-09-26 反馈：
        // 酿造台/锻造台的自研配方书漏了这一行。
        // 行内容与**替代配方组浮层**里的格子共用（RecipeCellTooltips），保证两边格式一致。
        return RecipeCellTooltips.forStack(this.registryAccess, result, this.getOrderedRecipes().size() > 1);
    }

    /**
     * 单元格**画出来**的那件物品（默认 = 当前轮循配方的产物）。
     *
     * <p>锻造台的**纹饰组**覆写它：原版一条 {@code smithing_trim} 配方的 base 是
     * {@code #minecraft:trimmable_armor} 标签，展开成"每种可纹饰装备一件"的一整组，
     * 折叠时轮循各件装备没有信息量——整组共用的**纹饰模板**才是这一组的身份
     * （用户 2026-09-26 诉求）。</p>
     */
    protected ItemStack getDisplayedStack(BRBBookCategories.Category category) {
        R current = this.getCurrentDisplayedRecipe();
        return current == null ? ItemStack.EMPTY : current.getResult(this.registryAccess, category);
    }
}
