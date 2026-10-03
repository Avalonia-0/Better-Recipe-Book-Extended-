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
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class SmithingRecipeBookComponent extends GenericRecipeBookComponent<SmithingMenu, SmithingRecipeCollection, BRBSmithingRecipe> {
    private static final MutableComponent ONLY_CRAFTABLES_TOOLTIP = Component.translatable("brbe.gui.smithable");

    /**
     * 锻造台界面的「盔甲架预览」写入器（原版 {@code SmithingScreen#updateArmorStandPreview} 的方法引用）。
     *
     * <p>留一份引用是为了在**幽灵收起**时把盔甲架复位（用户 2026-09-28 反馈：悬停展示配方后
     * 盔甲架上的装备模型无法移除）——复位值取原版语义的那个值，见
     * {@link #brbe$restoreArmorStandPreview()}。</p>
     */
    @Nullable
    private Consumer<ItemStack> brbe$ghostUpdater;

    public void init(int width, int height, Minecraft minecraft, boolean widthNarrow, SmithingMenu menu, Consumer<ItemStack> onGhostRecipeUpdate, RegistryAccess registryAccess, RecipeManager recipeManager) {
        this.recipeManager = recipeManager;
        // recipesPage MUST be assigned before initVisuals() because initVisuals() calls recipesPage.initialize()
        this.recipesPage = new SmithingRecipeBookPage(registryAccess, () -> BRBBookSettings.isFiltering(getRecipeBookType()));

        super.init(width, height, minecraft, widthNarrow, menu, onGhostRecipeUpdate, registryAccess);

        // ghostRecipe was overwritten by super.init() — re-create it with the correct type
        // and re-attach the predicate (which needs menu to be set first).
        this.brbe$ghostUpdater = onGhostRecipeUpdate;
        this.ghostRecipe = new SmithingGhostRecipe(onGhostRecipeUpdate, registryAccess);
        this.ghostRecipe.setDefaultRenderingPredicate(menu);
        // 幽灵收起（悬停离开 / 点击引导结束 / 收书）→ 盔甲架复位
        this.ghostRecipe.setOnGhostRelease(this::brbe$restoreArmorStandPreview);
        this.initVisuals();
    }

    /**
     * 把盔甲架恢复成**原版语义**的显示：结果槽当前的物品。
     *
     * <p>原版 {@code SmithingScreen#slotChanged(menu, slot, stack)} 只在**结果槽**（
     * {@link SmithingMenu#RESULT_SLOT}）变化时调 {@code updateArmorStandPreview(stack)}，
     * 也就是"盔甲架 = 结果槽物品"（工作区摆好一条配方时显示真实产物，否则空）。BRBE 的幽灵预览
     * 期间每帧把**幽灵产物**推进去，收起时必须把它换回这个值——否则模型一直挂在那里，
     * 直到原版因结果槽变化才复位。</p>
     */
    private void brbe$restoreArmorStandPreview() {
        if (this.brbe$ghostUpdater == null || this.menu == null) {
            return;
        }
        this.brbe$ghostUpdater.accept(this.menu.getSlot(SmithingMenu.RESULT_SLOT).getItem());
    }

    @Override
    public Component getRecipeFilterName() {
        return ONLY_CRAFTABLES_TOOLTIP;
    }

    @Override
    public BRBHelper.Book getRecipeBookType() {
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
            // vanilla 语义（{@code ServerPlaceRecipe#tryPlaceRecipe} 的 PLACE_GHOST_RECIPE 分支）：
            // **先把工作区里的真实物品退回背包**（vanilla {@code clearGrid}），再显示缺料引导——
            // 否则工作区里的旧物品会一直留在那儿、和幽灵叠在一起（用户 2026-09-26 反馈：
            // "点击缺失材料的配方后并不会将工作区里的真实物品放入背包"）。
            // 退回不去（背包塞不下）时 vanilla 直接放弃这次点击（{@code testClearGrid} → NOTHING），
            // 这里同义：宁可什么都不做，也不要把物品挤到光标上/地上。
            if (!ClientInventoryUtil.canReturnSlotsToInventory(this.menu, this::brbe$isWorkspaceSlot)) {
                return;
            }
            ClientInventoryUtil.returnSlotsToInventory(this.menu, this::brbe$isWorkspaceSlot);
            // 点击的"缺料引导"是**持续**的（原版语义）：登记下来，指针移开按钮后由悬停逻辑还原
            // （用户 2026-09-26：此前鼠标一移开引导就被悬停逻辑抹掉，观感是"点击不填充幽灵配方"）
            this.brbe$showPlacedGhost(result);
            return;
        }

        // 材料齐 → 直接放置，不需要引导
        this.brbe$clearPlacedGhost();

        int slotIndex = 0;
        boolean placedBase = false;
        for (Slot slot : menu.slots) {
            ItemStack itemStack = slot.getItem();

            if (result.getTemplate().test(itemStack)) {
                ClientInventoryUtil.moveItemToSlot(menu, slotIndex, SmithingMenu.TEMPLATE_SLOT);
            } else if (!placedBase && !itemStack.has(DataComponents.TRIM) && result.getBase().getItem().equals(itemStack.getItem())) {
                ClientInventoryUtil.moveItemToSlot(menu, slotIndex, SmithingMenu.BASE_SLOT);
                placedBase = true;
            } else if (result.getAddition().test(itemStack)) {
                ClientInventoryUtil.moveItemToSlot(menu, slotIndex, SmithingMenu.ADDITIONAL_SLOT);
            }

            ++slotIndex;
        }

        this.updateCollections(false);
    }

    /**
     * 工作区槽位：模板 / 基底 / 附加材料——**不含结果槽**（结果槽是产出，玩家放不进去，
     * 与 vanilla {@code slotsToClear} 只清配方输入槽同义）。
     */
    private boolean brbe$isWorkspaceSlot(Slot slot) {
        return slot.index == SmithingMenu.TEMPLATE_SLOT
                || slot.index == SmithingMenu.BASE_SLOT
                || slot.index == SmithingMenu.ADDITIONAL_SLOT;
    }

    public void setupGhostRecipe(BRBSmithingRecipe result, List<Slot> list) {
        this.ghostRecipe.setRecipe(result);

        this.ghostRecipe.addIngredient(SmithingMenu.ADDITIONAL_SLOT, result.getAddition(), SmithingMenu.ADDITIONAL_SLOT_X_PLACEMENT, SmithingMenu.SLOT_Y_PLACEMENT);
        this.ghostRecipe.addIngredient(SmithingMenu.TEMPLATE_SLOT, result.getTemplate(), SmithingMenu.TEMPLATE_SLOT_X_PLACEMENT, SmithingMenu.SLOT_Y_PLACEMENT);
        this.ghostRecipe.addIngredient(SmithingMenu.BASE_SLOT, Ingredient.of(result.getBase()), SmithingMenu.BASE_SLOT_X_PLACEMENT, SmithingMenu.SLOT_Y_PLACEMENT);
    }

    /** 悬停预览（用户 2026-09-25）：与点击时的"缺料引导"同一个写入路径，只是不看材料够不够。 */
    @Override
    protected void setupHoverGhost(BRBSmithingRecipe recipe) {
        if (this.ghostRecipe == null) return;
        this.setupGhostRecipe(recipe, this.menu.slots);
    }

    public boolean isShowingGhostRecipe() {
        return this.ghostRecipe != null && this.ghostRecipe.size() > 0;
    }

    @Override
    protected List<SmithingRecipeCollection> getCollectionsForCategory() {
        List<RecipeHolder<SmithingRecipe>> recipes = recipeManager.getAllRecipesFor(RecipeType.SMITHING);
        List<SmithingRecipeCollection> results = new ArrayList<>();
        BRBBookCategories.Category category = selectedTab.getCategory();

        for (RecipeHolder<SmithingRecipe> recipe : recipes) {
            SmithingRecipe value = recipe.value();

            if (category == BetterRecipeBook.SMITHING_SEARCH) {
                if (value instanceof SmithingTransformRecipe) {
                    results.add(new SmithingRecipeCollection(List.of(BRBSmithingTransformRecipe.from((SmithingTransformRecipe) value, registryAccess)), this.menu, registryAccess));
                } else if (value instanceof SmithingTrimRecipe) {
                    results.add(new SmithingRecipeCollection(BRBSmithingTrimRecipe.from((SmithingTrimRecipe) value), this.menu, registryAccess));
                }
            } else if (category == BetterRecipeBook.SMITHING_TRANSFORM) {
                if (value instanceof SmithingTransformRecipe) {
                    results.add(new SmithingRecipeCollection(List.of(BRBSmithingTransformRecipe.from((SmithingTransformRecipe) value, registryAccess)), this.menu, registryAccess));
                }
            } else if (value instanceof SmithingTrimRecipe) {
                results.add(new SmithingRecipeCollection(BRBSmithingTrimRecipe.from((SmithingTrimRecipe) value), this.menu, registryAccess));
            }
        }

        return results;
    }
}
