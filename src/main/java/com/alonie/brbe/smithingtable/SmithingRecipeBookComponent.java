package com.alonie.brbe.smithingtable;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.api.BRBBookCategories;
import com.alonie.brbe.api.BRBBookSettings;
import com.alonie.brbe.generic.GenericRecipeBookComponent;
import com.alonie.brbe.recipe.BRBSmithingRecipe;
import com.alonie.brbe.recipe.smithing.BRBSmithingTransformRecipe;
import com.alonie.brbe.recipe.smithing.BRBSmithingTrimRecipe;
import com.alonie.brbe.util.BRBHelper;
import com.alonie.brbe.util.ClientInventoryUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class SmithingRecipeBookComponent extends GenericRecipeBookComponent<SmithingMenu, SmithingRecipeCollection, BRBSmithingRecipe> {
    private static final MutableComponent ONLY_CRAFTABLES_TOOLTIP = Component.translatable("brbe.gui.smithable");

    public void init(int width, int height, Minecraft minecraft, boolean widthNarrow, SmithingMenu menu, Consumer<ItemStack> onGhostRecipeUpdate, RegistryAccess registryAccess) {
        super.init(width, height, minecraft, widthNarrow, menu, onGhostRecipeUpdate, registryAccess);

        this.ghostRecipe = new SmithingGhostRecipe(onGhostRecipeUpdate, registryAccess);
        this.ghostRecipe.setDefaultRenderingPredicate(this.menu);
        this.recipesPage = new SmithingRecipeBookPage(registryAccess, () -> BRBBookSettings.isFiltering(getRecipeBookType()));

//        if (this.isVisible()) {
        this.initVisuals();
//        }
    }

    @Override
    public Component getRecipeFilterName() {
        return ONLY_CRAFTABLES_TOOLTIP;
    }

    @Override
    public BRBHelper.Book getRecipeBookType() {
        BetterRecipeBook.ensureCategories();
        return BetterRecipeBook.SMITHING;
    }

    @Override
    public void handlePlaceRecipe() {
        BRBSmithingRecipe result = this.recipesPage.getCurrentClickedRecipe();
        SmithingRecipeCollection recipeCollection = this.recipesPage.getLastClickedRecipeCollection();

        if (result == null || recipeCollection == null) return;

        this.ghostRecipe.clear();

        // 放置路径维持只看 slots：材料在鼠标上时显示 ghost 引导放料，
        // 不把 carried 计入放置判定（放置循环只遍历 slots，无法从 carried 取料）。
        if (!result.hasMaterials(this.menu.slots, this.registryAccess, ItemStack.EMPTY)) {
            this.setupGhostRecipe(result, this.menu.slots);
            return;
        }

        int slotIndex = 0;
        boolean placedBase = false;
        for (Slot slot : menu.slots) {
            ItemStack itemStack = slot.getItem();

            if (result.requiresTemplate() && result.getTemplate().test(itemStack)) {
                ClientInventoryUtil.moveItemToSlot(menu, slotIndex, SmithingMenu.TEMPLATE_SLOT);
            } else if (!placedBase && !itemStack.has(DataComponents.TRIM) && result.getBase().getItem().equals(itemStack.getItem())) {
                ClientInventoryUtil.moveItemToSlot(menu, slotIndex, SmithingMenu.BASE_SLOT);
                placedBase = true;
            } else if (result.requiresAddition() && result.getAddition().test(itemStack)) {
                ClientInventoryUtil.moveItemToSlot(menu, slotIndex, SmithingMenu.ADDITIONAL_SLOT);
            }

            ++slotIndex;
        }

        this.updateCollections(false);
    }

    public void setupGhostRecipe(BRBSmithingRecipe result, List<Slot> list) {
        this.ghostRecipe.setRecipe(result);

        if (result.requiresAddition()) {
            this.ghostRecipe.addIngredient(SmithingMenu.ADDITIONAL_SLOT, result.getAddition(), SmithingMenu.ADDITIONAL_SLOT_X_PLACEMENT, SmithingMenu.SLOT_Y_PLACEMENT);
        }
        if (result.requiresTemplate()) {
            this.ghostRecipe.addIngredient(SmithingMenu.TEMPLATE_SLOT, result.getTemplate(), SmithingMenu.TEMPLATE_SLOT_X_PLACEMENT, SmithingMenu.SLOT_Y_PLACEMENT);
        }
        this.ghostRecipe.addIngredient(SmithingMenu.BASE_SLOT, result.getBase().copy(), SmithingMenu.BASE_SLOT_X_PLACEMENT, SmithingMenu.SLOT_Y_PLACEMENT);
    }

    public boolean isShowingGhostRecipe() {
        return this.ghostRecipe != null && this.ghostRecipe.size() > 0;
    }

    @Override
    protected List<SmithingRecipeCollection> getCollectionsForCategory() {
        if (this.minecraft.player == null || this.minecraft.level == null) {
            return Collections.emptyList();
        }

        List<SmithingRecipeCollection> results = new ArrayList<>();
        BRBBookCategories.Category category = selectedTab.getCategory();
        ContextMap displayContext = SlotDisplayContext.fromLevel(this.minecraft.level);
        List<RecipeCollection> collections = this.minecraft.player.getRecipeBook().getCollection(RecipeBookCategories.SMITHING);

        for (RecipeCollection collection : collections) {
            List<BRBSmithingRecipe> smithingRecipes = new ArrayList<>();

            for (RecipeDisplayEntry entry : collection.getRecipes()) {
                if (!(entry.display() instanceof SmithingRecipeDisplay smithingDisplay)) {
                    continue;
                }

                boolean isTrimRecipe = smithingDisplay.result() instanceof SlotDisplay.SmithingTrimDemoSlotDisplay;
                if (!shouldInclude(category, isTrimRecipe)) {
                    continue;
                }

                if (isTrimRecipe) {
                    smithingRecipes.addAll(BRBSmithingTrimRecipe.from(smithingDisplay, displayContext));
                } else {
                    smithingRecipes.add(BRBSmithingTransformRecipe.from(entry, smithingDisplay, displayContext));
                }
            }

            if (!smithingRecipes.isEmpty()) {
                results.add(new SmithingRecipeCollection(smithingRecipes, this.menu, registryAccess));
            }
        }

        return results;
    }

    private static boolean shouldInclude(BRBBookCategories.Category category, boolean isTrimRecipe) {
        BetterRecipeBook.ensureCategories();
        if (category == BetterRecipeBook.SMITHING_SEARCH) {
            return true;
        }

        if (category == BetterRecipeBook.SMITHING_TRANSFORM) {
            return !isTrimRecipe;
        }

        return isTrimRecipe;
    }
}
