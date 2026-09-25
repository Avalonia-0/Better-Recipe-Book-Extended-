package com.alonie.brbe.generic;

import com.google.common.collect.Lists;
import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.api.BRBBookCategories;
import com.alonie.brbe.util.ClientCompat;
import com.alonie.brbe.util.CycleLock;
import com.alonie.brbe.util.ModNameUtil;
import com.alonie.brbe.util.PartialCraftingUtil;
import com.alonie.brbe.util.PartialGhostOverlayUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    }

    public void addIngredient(int containerSlot, Ingredient ingredient, int i, int j) {
        this.ingredients.add(new GenericGhostIngredient(containerSlot, ingredient, i, j));
    }

    public void addIngredient(int containerSlot, ItemStack itemStack, int i, int j) {
        this.ingredients.add(new GenericGhostIngredient(containerSlot, itemStack, i, j));
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

    /** 原版缺料红底（{@code 0x30FF0000}，alpha ≈19%）。 */
    private static final int GHOST_RED = 0x30FF0000;
    /** 加深后的缺料红底（与工作台 CRAFTING 路径的 {@code GhostSlotsMixin} 同值）。 */
    private static final int GHOST_RED_STRONG = 0x66FF0000;
    /** 原版半透明白罩（已有材料槽位在工作台里会被跳过）。 */
    private static final int GHOST_WHITE = 0x30FFFFFF;

    public void render(GuiGraphicsExtractor guiGraphics, Minecraft minecraft, int i, int j, boolean bl, float f, BRBBookCategories.Category category) {
        if (!ClientCompat.isControlDown()) {
            this.time += f;
            if (this.onGhostUpdate != null && this.recipe != null) this.onGhostUpdate.accept(this.getCurrentResult(category));
        }

        // 材料是否已在检索空间（真实物品栏 + 手持 + 副手 + 打开的合成网格）：
        // 与工作台 CRAFTING 路径（GhostSlotsMixin + PartialGhostOverlayUtil）**同一套判定**，
        // 逐槽按 (y, x) 顺序扣减数量（2026-09-22 用户反馈：锻造台/酿造台的幽灵物品
        // 没有这套调整——已有材料仍被红/白遮罩盖住、缺料红罩也没加深）。
        boolean[] missing = brbe$missingSlots();

        for (int k = 0; k < this.ingredients.size(); ++k) {
            GenericGhostIngredient ghostIngredient = this.ingredients.get(k);
            boolean shouldRenderBackground = renderingPredicate != null && renderingPredicate.test(GhostRenderType.BACKGROUND, ghostIngredient);
            boolean shouldRenderItem = renderingPredicate != null && renderingPredicate.test(GhostRenderType.ITEM, ghostIngredient);

            // 已有材料的槽位：红底与白罩都不画，物品以完整不透明度显示。
            boolean materialOwned = k < missing.length && !missing[k];

            int l = ghostIngredient.getX() + i;
            int m = ghostIngredient.getY() + j;
            int background = -1;
            if (shouldRenderBackground && !materialOwned) {
                background = missing.length > k && missing[k] ? GHOST_RED_STRONG : GHOST_RED;
                if (k == 0 && bl) {
                    guiGraphics.fill(l - 4, m - 4, l + 20, m + 20, background);
                } else {
                    guiGraphics.fill(l, m, l + 16, m + 16, background);
                }
            }

            ItemStack itemStack = ghostIngredient.getDisplayStack(l, m);
            if (shouldRenderItem) {
                guiGraphics.fakeItem(itemStack, l, m);
            }

            if (shouldRenderBackground && !materialOwned) {
                guiGraphics.fill(l, m, l + 16, m + 16, GHOST_WHITE);
            }

            if (k == 0) {
                guiGraphics.itemDecorations(minecraft.font, itemStack, l, m);
            }
        }
    }

    /**
     * 每个幽灵槽位的"缺料"标记：{@code true} = 检索空间里没有这件材料。
     *
     * <p>判定与查询预览/pin 的逐槽红罩同源（{@link PartialGhostOverlayUtil#computeMissing} +
     * {@link PartialCraftingUtil#searchSpaceItemCounts()}）：候选变体里任意一个拥有即视为
     * 不缺料，并按 (y, x) 顺序扣减数量（同一种材料占多个槽位时只有够数的槽位算"已有"）。</p>
     */
    private boolean[] brbe$missingSlots() {
        if (this.ingredients.isEmpty()) return new boolean[0];
        List<PartialGhostOverlayUtil.GhostSlotSample> samples = new ArrayList<>(this.ingredients.size());
        for (GenericGhostIngredient ingredient : this.ingredients) {
            samples.add(new PartialGhostOverlayUtil.GhostSlotSample(
                    ingredient.getX(), ingredient.getY(), ingredient.getVariants()));
        }
        Map<net.minecraft.world.item.Item, Integer> counts = PartialCraftingUtil.searchSpaceItemCounts();
        return PartialGhostOverlayUtil.computeMissing(samples, counts);
    }

    public GenericGhostIngredient getBySlot(int i) {
        for (GenericGhostIngredient ingredient : ingredients) {
            if (ingredient.getContainerSlot() == i) return ingredient;
        }
        return null;
    }

    public void drawTooltip(GuiGraphicsExtractor gui, int x, int y, int mouseX, int mouseY) {
        ItemStack itemStack = null;

        for (GenericGhostIngredient ingredient : ingredients) {
            int j = ingredient.getX() + x;
            int k = ingredient.getY() + y;

            // don't render tooltip if cursor is not over item or predicate returns false
            if (mouseX >= j && mouseY >= k && mouseX < j + 16 && mouseY < k + 16 && (renderingPredicate == null || renderingPredicate.test(GhostRenderType.TOOLTIP, ingredient))) {
                // 用显示体（而非自动轮换体）：锁定期间鼠标提示必须与画出来的那一件一致
                itemStack = ingredient.getDisplayStack(j, k);
            }
        }

        this.lastHoveredItem = itemStack;

        if (itemStack != null && Minecraft.getInstance().gui.screen() != null) {
            List<Component> tooltip = Screen.getTooltipFromItem(Minecraft.getInstance(), itemStack);
            if (BetterRecipeBook.config != null && BetterRecipeBook.config.showModName) {
                Component modName = ModNameUtil.getFormattedModName(itemStack);
                if (modName != null && !modName.getString().isEmpty()) {
                    tooltip.add(Component.empty());
                    tooltip.add(modName);
                }
            }

            ClientCompat.setComponentTooltipForNextFrame(gui, tooltip, mouseX, mouseY);
        }
    }

    @Nullable
    public ItemStack getLastHoveredItem() {
        return lastHoveredItem;
    }

    public class GenericGhostIngredient {
        @Nullable
        private final Ingredient ingredient;
        @Nullable
        private final ItemStack[] itemStacks;
        private final int x;
        private final int y;
        private final int containerSlot;

        public GenericGhostIngredient(int containerSlot, Ingredient ingredient, int i, int j) {
            this.containerSlot = containerSlot;
            this.ingredient = ingredient;
            this.itemStacks = null;
            this.x = i;
            this.y = j;
        }

        public GenericGhostIngredient(int containerSlot, ItemStack itemStack, int i, int j) {
            this.containerSlot = containerSlot;
            this.ingredient = null;
            this.itemStacks = new ItemStack[]{itemStack};
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
            ItemStack[] displayStacks = this.itemStacks != null ? this.itemStacks : ClientCompat.ingredientItems(this.ingredient);
            return displayStacks.length == 0 ? ItemStack.EMPTY : displayStacks[Mth.floor(GenericGhostRecipe.this.time / 30.0F) % displayStacks.length];
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
            ItemStack[] displayStacks = this.itemStacks != null ? this.itemStacks : ClientCompat.ingredientItems(this.ingredient);
            if (displayStacks.length <= 1) {
                return displayStacks.length == 0 ? ItemStack.EMPTY : displayStacks[0];
            }
            Object key = this;
            if (!CycleLock.claimScreen(key, screenX, screenY, 16, 16)) {
                CycleLock.release(key);
                return displayStacks[Mth.floor(GenericGhostRecipe.this.time / 30.0F) % displayStacks.length];
            }
            int auto = Math.floorMod(Mth.floor(GenericGhostRecipe.this.time / 30.0F), displayStacks.length);
            int index = CycleLock.indexFor(key, auto);
            return displayStacks[Math.floorMod(index, displayStacks.length)];
        }

        /** 该槽位的全部候选物品（轮循显示的变体；缺料判定用——"拥有任意一个即不缺料"）。 */
        public List<ItemStack> getVariants() {
            ItemStack[] displayStacks = this.itemStacks != null ? this.itemStacks : ClientCompat.ingredientItems(this.ingredient);
            return displayStacks.length == 0 ? List.of() : List.of(displayStacks);
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
