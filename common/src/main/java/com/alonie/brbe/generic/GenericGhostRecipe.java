package com.alonie.brbe.generic;

import com.google.common.collect.Lists;
import com.alonie.brbe.api.BRBBookCategories;
import net.minecraft.client.Minecraft;
import com.alonie.brbe.util.CycleLock;
import com.alonie.brbe.util.HoverGhostRecipe;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.RegistryAccess;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

public class GenericGhostRecipe<R extends GenericRecipe> {
    @Nullable
    protected Consumer<ItemStack> onGhostUpdate;
    @Nullable
    protected R recipe;
    protected final List<GenericGhostIngredient> ingredients = Lists.newArrayList();
    protected float time;
    protected RegistryAccess registryAccess;
    @Nullable
    private BiPredicate<GhostRenderType, GenericGhostIngredient> renderingPredicate;

    /** The ItemStack that was under the mouse during the most recent {@link #drawTooltip} call. */
    @Nullable
    private ItemStack lastHoveredItem;

    public GenericGhostRecipe(@Nullable Consumer<ItemStack> onGhostUpdate, RegistryAccess registryAccess) {
        this.onGhostUpdate = onGhostUpdate;
        this.registryAccess = registryAccess;
    }

    /**
     * 幽灵**结束**（{@link #clear()} / 不再显示）时把「跟着幽灵走的外部预览」复位的回调。
     *
     * <p><b>为什么需要它</b>（用户 2026-09-28 反馈："悬停展示锻造台配方后，工作区那个盔甲架上的
     * 装备模型无法移除"）：锻造台界面的盔甲架由原版 {@code SmithingScreen#updateArmorStandPreview}
     * 驱动，原版只在**结果槽**变化时用它刷新（{@code slotChanged(menu, RESULT_SLOT, stack)}）。
     * BRBE 的幽灵预览每帧把幽灵产物推进去（见 {@link #render}），但幽灵收起时**没有人复位**它 →
     * 盔甲架一直挂着那件装备的模型，直到玩家动一下工作区让结果槽变化为止。</p>
     *
     * <p>复位值由调用方决定：锻造台传的是"原版语义的那个值"= 结果槽当前的物品
     * （见 {@code SmithingRecipeBookComponent#brbe$restoreArmorStandPreview}）。</p>
     */
    @Nullable
    private Runnable onGhostRelease;

    /** 外部预览当前是否被本幽灵占着（用来让 {@link #releaseExternalPreview()} 幂等）。 */
    private boolean externalPreviewHeld;

    public void setOnGhostRelease(@Nullable Runnable onGhostRelease) {
        this.onGhostRelease = onGhostRelease;
    }

    /**
     * 幽灵不再显示时复位外部预览。**幂等**：没有对外写过就什么都不做，可以放心每帧调用
     * （收起配方书那条路径就是这么用的——幽灵本身按原设计保留，只是不再"占着"外部预览）。
     */
    public void releaseExternalPreview() {
        if (!this.externalPreviewHeld) {
            return;
        }
        this.externalPreviewHeld = false;
        if (this.onGhostRelease != null) {
            this.onGhostRelease.run();
        }
    }

    /**
     * @param renderingPredicate Returns true if {@link GhostRenderType} should be rendered
     */
    public void setRenderingPredicate(@Nullable BiPredicate<GhostRenderType, GenericGhostIngredient> renderingPredicate) {
        this.renderingPredicate = renderingPredicate;
    }

    public <T extends AbstractContainerMenu> void setDefaultRenderingPredicate(T menu) {
        this.setRenderingPredicate((type, ingredient) -> {
            ItemStack slot = menu.slots.get(ingredient.getContainerSlot()).getItem();
            switch (type) {
                case ITEM, BACKGROUND, TOOLTIP -> {
                    return slot.isEmpty();
                }
            }
            return true;
        });
    }

    public ItemStack getCurrentResult(BRBBookCategories.Category category) {
        if (this.recipe == null) {
            return ItemStack.EMPTY;
        }

        ItemStack itemStack = this.recipe.getResult(registryAccess, category);

        return itemStack.copy();
    }

    public void clear() {
        this.recipe = null;
        this.ingredients.clear();
        this.time = 0.0F;
        // 幽灵收起 = 外部预览（锻造台盔甲架）不再由我们驱动 → 复位成原版的显示
        this.releaseExternalPreview();
    }

    public void addIngredient(int containerSlot, Ingredient ingredient, int i, int j) {
        this.ingredients.add(new GenericGhostIngredient(containerSlot, ingredient, i, j));
    }

    public GenericGhostIngredient get(int i) {
        return this.ingredients.get(i);
    }

    public int size() {
        return this.ingredients.size();
    }

    @Nullable
    public R getRecipe() {
        return this.recipe;
    }

    public void setRecipe(@Nullable R recipe) {
        this.recipe = recipe;
    }

    public void render(GuiGraphics guiGraphics, Minecraft minecraft, int i, int j, boolean bl, float f, BRBBookCategories.Category category) {
        if (!Screen.hasControlDown()) {
            this.time += f;
            if (this.onGhostUpdate != null && this.recipe != null) {
                // 记住了"外部预览被我们占着"：幽灵收起时要复位（见 #releaseExternalPreview）
                this.externalPreviewHeld = true;
                this.onGhostUpdate.accept(this.getCurrentResult(category));
            }
        }

        // 悬停预览期间**忽略渲染谓词**（锻造台的"只画空槽位"、酿造台的"槽内与目标不同才画"）：
        // 工作区里的真实物品此时已被「暂隐工作区真实物品」藏起来（见
        // {@link HoverGhostRecipe#hidesRealItemIn}），幽灵必须照样画——否则那些槽位会整格
        // 空白（用户 2026-09-25 反馈：锻造台工作区有物品时悬停配方 → 整格清空、连幽灵物品
        // 都不显示）。点击放置的缺料引导不受影响：那时没有悬停预览，谓词照常生效。
        boolean hoverPreview = HoverGhostRecipe.isPreviewing();

        for (int k = 0; k < this.ingredients.size(); ++k) {
            GenericGhostIngredient ghostIngredient = this.ingredients.get(k);
            boolean shouldRenderBackground = hoverPreview
                    || (renderingPredicate != null && renderingPredicate.test(GhostRenderType.BACKGROUND, ghostIngredient));
            boolean shouldRenderItem = hoverPreview
                    || (renderingPredicate != null && renderingPredicate.test(GhostRenderType.ITEM, ghostIngredient));

            int l = ghostIngredient.getX() + i;
            int m = ghostIngredient.getY() + j;
            if (shouldRenderBackground) {
                if (k == 0 && bl) {
                    guiGraphics.fill(l - 4, m - 4, l + 20, m + 20, 822018048);
                } else {
                    guiGraphics.fill(l, m, l + 16, m + 16, 822018048);
                }
            }

            ItemStack itemStack = ghostIngredient.getDisplayStack(l, m);
            if (shouldRenderItem) {
                guiGraphics.renderFakeItem(itemStack, l, m);
            }

            if (shouldRenderBackground) {
                guiGraphics.fill(RenderType.guiGhostRecipeOverlay(), l, m, l + 16, m + 16, 822083583);
            }

            if (k == 0) {
                guiGraphics.renderItemDecorations(minecraft.font, itemStack, l, m);
            }
        }
    }


    public GenericGhostIngredient getBySlot(int i) {
        for (GenericGhostIngredient ingredient : ingredients) {
            if (ingredient.getContainerSlot() == i) return ingredient;
        }
        return null;
    }

    /**
     * 工作区**已经摆好这条配方**——幽灵的每个条目在对应槽位里都已经是候选物品之一
     * （用户 2026-09-27 收尾诉求：已摆好就别再预览，否则"暂隐工作区真实物品"会把摆好的材料
     * 整片藏掉，而自研书的幽灵渲染谓词只画空槽位 → 工作区看起来是空的）。
     *
     * <p>判定与工作台路径（{@link HoverGhostRecipe} 的"幽灵一格也补不进去"）同一套
     * {@link HoverGhostRecipe#satisfies}：物品相同、数量够，候选带组件时组件也要一致。</p>
     */
    public boolean isLaidOutInWorkspace(@Nullable AbstractContainerMenu menu) {
        if (menu == null || this.ingredients.isEmpty()) return false;
        for (GenericGhostIngredient ingredient : this.ingredients) {
            int index = ingredient.getContainerSlot();
            if (index < 0 || index >= menu.slots.size()) return false;
            ItemStack real = menu.slots.get(index).getItem();
            if (real.isEmpty()) return false;
            boolean matched = false;
            for (ItemStack candidate : ingredient.getVariants()) {
                if (HoverGhostRecipe.satisfies(real, candidate)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) return false;
        }
        return true;
    }

    public void drawTooltip(GuiGraphics gui, int x, int y, int mouseX, int mouseY) {
        ItemStack itemStack = null;

        for (GenericGhostIngredient ingredient : ingredients) {
            int j = ingredient.getX() + x;
            int k = ingredient.getY() + y;

            // don't render tooltip if cursor is not over item or predicate returns false
            if (mouseX >= j && mouseY >= k && mouseX < j + 16 && mouseY < k + 16
                    && (HoverGhostRecipe.isPreviewing()
                        || renderingPredicate == null
                        || renderingPredicate.test(GhostRenderType.TOOLTIP, ingredient))) {
                // 用显示体（而非自动轮换体）：锁定期间鼠标提示必须与画出来的那一件一致
                itemStack = ingredient.getDisplayStack(j, k);
            }
        }

        this.lastHoveredItem = itemStack;

        if (itemStack != null && Minecraft.getInstance().screen != null) {
            gui.renderComponentTooltip(Minecraft.getInstance().font, Screen.getTooltipFromItem(Minecraft.getInstance(), itemStack), mouseX, mouseY);
        }
    }

    @Nullable
    public ItemStack getLastHoveredItem() {
        return lastHoveredItem;
    }

    public class GenericGhostIngredient {
        private final Ingredient ingredient;
        private final int x;
        private final int y;
        private final int containerSlot;

        public GenericGhostIngredient(int containerSlot, Ingredient ingredient, int i, int j) {
            this.containerSlot = containerSlot;
            this.ingredient = ingredient;
            this.x = i;
            this.y = j;
        }

        public int getX() {
            return this.x;
        }

        public int getY() {
            return this.y;
        }

        public ItemStack getItem() {
            ItemStack[] itemStacks = this.ingredient.getItems();
            return itemStacks.length == 0 ? ItemStack.EMPTY : itemStacks[Mth.floor(GenericGhostRecipe.this.time / 30.0F) % itemStacks.length];
        }

        /** 该槽位的全部候选物品（缺料 / "已摆好"判定用——拥有任意一个即视为满足）。 */
        public List<ItemStack> getVariants() {
            ItemStack[] itemStacks = this.ingredient.getItems();
            return itemStacks.length == 0 ? List.of() : List.of(itemStacks);
        }

        /**
         * 该槽位**这一帧应显示**的物品：逐物品折叠锁（用户 2026-09-26 诉求）。
         *
         * <p>{@code screenX/screenY} 是它在屏幕上的位置（渲染原点 + 本槽位相对坐标，
         * 与 {@code render}/{@code drawTooltip} 画它的坐标是同一组）。指针停在这件
         * 幽灵物品上、且锁定键（默认 Alt）按住 → 冻结在**当下看到的**那一个变体上，
         * 锁定键+滚轮逐格翻动（{@link CycleLock#consumeQueuedScroll()} 在幽灵物品的
         * 绘制路径里消费排队的那次滚轮）；没被指着 → 照常按 {@code time} 自动轮换。</p>
         *
         * <p>单变体槽位不是折叠物品，直接原样返回（不去 claim，免得占着"指针下的物品"
         * 让滚轮翻不动它旁边真正的折叠物品）。</p>
         */
        public ItemStack getDisplayStack(int screenX, int screenY) {
            ItemStack[] itemStacks = this.ingredient.getItems();
            if (itemStacks.length <= 1) {
                return itemStacks.length == 0 ? ItemStack.EMPTY : itemStacks[0];
            }
            Object key = this;
            if (!CycleLock.claimScreen(key, screenX, screenY, 16, 16)) {
                CycleLock.release(key);
                return itemStacks[Mth.floor(GenericGhostRecipe.this.time / 30.0F) % itemStacks.length];
            }
            int auto = Math.floorMod(Mth.floor(GenericGhostRecipe.this.time / 30.0F), itemStacks.length);
            int index = CycleLock.indexFor(key, auto);
            return itemStacks[Math.floorMod(index, itemStacks.length)];
        }

        public int getContainerSlot() {
            return this.containerSlot;
        }

        public GenericGhostRecipe<R> getOwner() {
            return GenericGhostRecipe.this;
        }
    }

    public enum GhostRenderType {
        /**
         * When rendering the fake item model
         */
        ITEM,
        /**
         * When rendering the background color
         */
        BACKGROUND,
        /**
         * When rendering the fake item tooltip
         */
        TOOLTIP
    }
}
