package com.alonie.brbe.generic;

import com.google.common.collect.Lists;
import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.api.BRBBookCategories;
import com.alonie.brbe.util.ClientCompat;
import com.alonie.brbe.util.ModNameUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

    private static final Logger LOGGER = LogManager.getLogger("BRBE-GhostSlot");

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

    public void render(GuiGraphicsExtractor guiGraphics, Minecraft minecraft, int i, int j, boolean bl, float f, BRBBookCategories.Category category) {
        if (!ClientCompat.isControlDown()) {
            this.time += f;
            if (this.onGhostUpdate != null && this.recipe != null) this.onGhostUpdate.accept(this.getCurrentResult(category));
        }

        for (int k = 0; k < this.ingredients.size(); ++k) {
            GenericGhostIngredient ghostIngredient = this.ingredients.get(k);
            boolean shouldRenderBackground = renderingPredicate != null && renderingPredicate.test(GhostRenderType.BACKGROUND, ghostIngredient);
            boolean shouldRenderItem = renderingPredicate != null && renderingPredicate.test(GhostRenderType.ITEM, ghostIngredient);

            int l = ghostIngredient.getX() + i;
            int m = ghostIngredient.getY() + j;
            if (shouldRenderBackground) {
                if (k == 0 && bl) {
                    guiGraphics.fill(l - 4, m - 4, l + 20, m + 20, 822018048);
                } else {
                    guiGraphics.fill(l, m, l + 16, m + 16, 822018048);
                }
            }

            ItemStack itemStack = ghostIngredient.getItem();
            if (shouldRenderItem) {
                guiGraphics.fakeItem(itemStack, l, m);
            }

            if (shouldRenderBackground) {
                guiGraphics.fill(l, m, l + 16, m + 16, 822083583);
            }

            if (k == 0) {
                guiGraphics.itemDecorations(minecraft.font, itemStack, l, m);
            }
        }
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
                itemStack = ingredient.getItem();
            }
        }

        this.lastHoveredItem = itemStack;

        if (itemStack != null && Minecraft.getInstance().gui.screen() != null) {
            List<Component> tooltip = Screen.getTooltipFromItem(Minecraft.getInstance(), itemStack);
            LOGGER.info("drawTooltip: item={}, config={}, showModName={}",
                    itemStack.getItem(), BetterRecipeBook.config,
                    BetterRecipeBook.config != null && BetterRecipeBook.config.showModName);

            if (BetterRecipeBook.config != null && BetterRecipeBook.config.showModName) {
                Component modName = ModNameUtil.getFormattedModName(itemStack);
                LOGGER.info("Mod name resolved: '{}' for item {}", modName.getString(), itemStack.getItem());
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
