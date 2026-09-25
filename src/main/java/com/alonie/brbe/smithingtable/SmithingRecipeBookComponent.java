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
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

    /** 悬停预览（用户 2026-09-25）：与点击时的"缺料引导"同一个写入路径，只是不看材料够不够。 */
    @Override
    protected void setupHoverGhost(BRBSmithingRecipe recipe) {
        if (this.ghostRecipe == null) return;
        this.setupGhostRecipe(recipe, this.menu.slots);
    }

    @Override
    protected List<SmithingRecipeCollection> getCollectionsForCategory() {
        if (this.minecraft.player == null || this.minecraft.level == null) {
            return Collections.emptyList();
        }

        List<SmithingRecipeCollection> results = new ArrayList<>();
        BRBBookCategories.Category category = selectedTab.getCategory();
        ContextMap displayContext = SlotDisplayContext.fromLevel(this.minecraft.level);
        // 直接读配方书 known 集（RecipeViewerIndex.knownEntries = 客户端 known 地图
        // 视图，与 BRBE 的解锁注入/引擎重建同源）——绕过 vanilla RecipeCollection
        // 构建层（该层对 BRBE 注入的解锁条目不总是即时反映）。
        // 集合粒度与原版 categorizeAndGroupRecipes 一致：同一
        // entry.group()（recipe 的变体组）合并为一个集合（一个按钮），
        // 无组的条目各自成集合——若把所有条目塞进一个集合，页面只会渲染
        // 一个按钮（每集合一按钮，20 集合/页），解锁的配方"看起来没显示"。
        Map<Object, List<RecipeDisplayEntry>> groups = new java.util.LinkedHashMap<>();
        for (RecipeDisplayEntry entry : com.alonie.brbe.cache.RecipeViewerIndex.knownEntries()) {
            if (!(entry.display() instanceof SmithingRecipeDisplay smithingDisplay)) {
                continue;
            }
            boolean isTrimRecipe = smithingDisplay.result() instanceof SlotDisplay.SmithingTrimDemoSlotDisplay;
            if (!shouldInclude(category, isTrimRecipe)) {
                continue;
            }
            Object groupKey = entry.group().isEmpty()
                    ? entry.id()
                    : "grp:" + entry.group().getAsInt();
            groups.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(entry);
        }
        for (List<RecipeDisplayEntry> group : groups.values()) {
            List<BRBSmithingRecipe> smithingRecipes = new ArrayList<>();
            for (RecipeDisplayEntry entry : group) {
                SmithingRecipeDisplay smithingDisplay =
                        (SmithingRecipeDisplay) entry.display();
                boolean isTrimRecipe =
                        smithingDisplay.result() instanceof SlotDisplay.SmithingTrimDemoSlotDisplay;
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
