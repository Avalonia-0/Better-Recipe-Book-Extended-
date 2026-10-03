package com.alonie.brbe.brewingstand;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.api.BRBBookCategories;
import com.alonie.brbe.api.BRBBookSettings;
import com.alonie.brbe.generic.GenericRecipeBookComponent;
import com.alonie.brbe.generic.GenericRecipePage;
import com.alonie.brbe.interfaces.IPinningComponent;
import com.alonie.brbe.loaders.PotionLoader;
import com.alonie.brbe.mixins.accessors.BrewingStandMenuAccessor;
import com.alonie.brbe.util.BRBHelper;
import com.alonie.brbe.util.ClientCompat;
import com.alonie.brbe.util.ClientInventoryUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import static com.alonie.brbe.brewingstand.PlatformPotionUtil.getIngredient;

@Environment(EnvType.CLIENT)
public class BrewingRecipeBookComponent extends GenericRecipeBookComponent<BrewingStandMenu, BrewingRecipeCollection, BrewableResult> implements IPinningComponent<BrewingRecipeCollection> {
    private static final Component ONLY_CRAFTABLES_TOOLTIP = Component.translatable("brbe.gui.togglePotions.brewable");

    @Override
    public void init(int parentWidth, int parentHeight, Minecraft client, boolean narrow, BrewingStandMenu menu, Consumer<ItemStack> onGhostRecipeUpdate, RegistryAccess registryAccess) {
        super.init(parentWidth, parentHeight, client, narrow, menu, onGhostRecipeUpdate, registryAccess);

        // BrewingRecipeBookPage：多一个替代配方组浮层（同一瓶药水的多条路线，右键展开）
        this.recipesPage = new BrewingRecipeBookPage(registryAccess, () -> BRBBookSettings.isFiltering(this.getRecipeBookType()));
        // this.cachedInvChangeCount = client.player.getInventory().getChangeCount();

//        if (this.isVisible()) {
        this.initVisuals();
//        }

        ghostRecipe.setRenderingPredicate((type, ingredient) -> {
            ItemStack slot = menu.slots.get(ingredient.getContainerSlot()).getItem();
            switch (type) {
                case ITEM, BACKGROUND -> {
                    // slot 0 is the result so map it to 1
                    ItemStack ghost = ingredient.getContainerSlot() == BrewingStandMenuAccessor.getBOTTLE_SLOT_START() ? ingredient.getOwner().getBySlot(1).getItem() : ingredient.getItem();

                    // slot is result
                    if (ingredient.getContainerSlot() >= BrewingStandMenuAccessor.getBOTTLE_SLOT_START() && ingredient.getContainerSlot() <= BrewingStandMenuAccessor.getBOTTLE_SLOT_END()) {
                        if (!(slot.getItem() instanceof PotionItem)) return true;

                        var slotPotion = slot.get(DataComponents.POTION_CONTENTS);
                        var ghostPotion = ghost.get(DataComponents.POTION_CONTENTS);

                        return !Objects.equals(slotPotion, ghostPotion);
                    } else { // else it's the consumable item
                        return !slot.is(ghost.getItem());
                    }
                }
                case TOOLTIP -> {
                    // render tooltip only if slot is empty
                    return slot.isEmpty();
                }
            }
            return true;
        });

        // still required?
        //client.keyboardHandler.setSendRepeatsToGui(true);
    }

    /**
     * 工作区槽位：三个瓶子槽（0..2）+ 材料槽（3）——**不含燃料槽**（燃料不是配方输入，
     * 与 vanilla {@code slotsToClear} 只清配方格子同义）。
     */
    private boolean brbe$isWorkspaceSlot(Slot slot) {
        int index = slot.index;
        return index == BrewingStandMenuAccessor.getINGREDIENT_SLOT()
                || (index >= BrewingStandMenuAccessor.getBOTTLE_SLOT_START()
                    && index <= BrewingStandMenuAccessor.getBOTTLE_SLOT_END());
    }

    public ItemStack getInputStack(BrewableResult result) {
        BetterRecipeBook.ensureCategories();
        // 输入形态取配方自带的基底物品（26.3：喷溅/滞留配方不能用标签页物品硬套），
        // 旧版数据无形态信息时回退到标签页物品。
        return result.inputAsItemStack(this.selectedTab.getCategory());
    }

    public void setupGhostRecipe(BrewableResult result, List<Slot> slots) {
        this.ghostRecipe.addIngredient(BrewingStandMenuAccessor.getINGREDIENT_SLOT(), ClientCompat.firstIngredientItem(getIngredient(result.recipe)), slots.get(BrewingStandMenuAccessor.getINGREDIENT_SLOT()).x, slots.get(BrewingStandMenuAccessor.getINGREDIENT_SLOT()).y);

        assert selectedTab != null;
        ItemStack inputStack = result.inputAsItemStack(selectedTab.getCategory());

        for (int i = BrewingStandMenuAccessor.getBOTTLE_SLOT_START(); i <= BrewingStandMenuAccessor.getBOTTLE_SLOT_END(); i++) {
            this.ghostRecipe.addIngredient(i, inputStack.copy(), slots.get(i).x, slots.get(i).y);
        }
    }

    /** 悬停预览（用户 2026-09-25）：与点击时的"缺料引导"同一个写入路径，只是不看材料够不够。 */
    @Override
    protected void setupHoverGhost(BrewableResult recipe) {
        if (this.ghostRecipe == null) return;
        this.setupGhostRecipe(recipe, this.menu.slots);
    }

    @Override
    protected List<BrewingRecipeCollection> getCollectionsForCategory() {
        BRBBookCategories.Category category = selectedTab.getCategory();

        // 同一瓶药水的**多条酿造路线**合并成一个替代配方组（用户 2026-09-26 诉求）：
        // 26.3 的配方表里 279 条配方只有 134 个唯一产物，其中 94 个产物有两条路线
        // （例：滞留型治疗药水 = 滞留型粗制 + 闪烁的西瓜片，或 治疗药水 + 龙息）。
        // 一条配方一个集合时配方书里会出现两个**一模一样**的格子、且都不是组（右键无反应）；
        // 合并后折叠时一个格子、右键展开选路线（BrewingRecipeBookPage 的组浮层）。
        Map<Identifier, List<BrewableResult>> groups = new LinkedHashMap<>();
        for (BrewableResult potion : PotionLoader.POTIONS) {
            // 26.3 的配方表含三种物品形态（普通/喷溅/滞留药水）的同一转换，
            // 只列出基底物品与本标签页一致的条目（详见 BrewableResult#belongsToTab）。
            if (!potion.belongsToTab(category)) {
                continue;
            }
            // 酿造自建进度：未解锁（酿造材料未获得过）的配方不显示。
            if (!com.alonie.brbe.brewingstand.RecipeUnlockTracker.isUnlocked(potion)) {
                continue;
            }
            // 组键 = 产物药水 id（BrewableResult#id，与 pin 的标识同源）；同一标签页内
            // 产物物品形态已固定，所以 id 相同的条目就是"同一瓶药水的不同路线"。
            groups.computeIfAbsent(potion.id(), key -> new ArrayList<>()).add(potion);
        }

        List<BrewingRecipeCollection> results = new ArrayList<>(groups.size());
        for (List<BrewableResult> routes : groups.values()) {
            results.add(new BrewingRecipeCollection(routes, menu, registryAccess, category));
        }

        return results;
    }

    @Override
    public Component getRecipeFilterName() {
        return ONLY_CRAFTABLES_TOOLTIP;
    }

    @Override
    public BRBHelper.Book getRecipeBookType() {
        BetterRecipeBook.ensureCategories();
        return BetterRecipeBook.BREWING;
    }

    @Override
    public void handlePlaceRecipe() {
        BrewableResult result = this.recipesPage.getCurrentClickedRecipe();

        if (result == null) return;

        // 放置路径维持只看 slots：材料在鼠标上时显示 ghost 引导放料，
        // 不把 carried 计入放置判定（放置循环只遍历 slots，无法从 carried 取料）。
        if (!result.hasMaterials(this.selectedTab.getCategory(), menu.slots, ItemStack.EMPTY)) {
            this.ghostRecipe.clear();
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

        // 材料齐 → 直接放置，不需要引导。真实物品由服务端放进工作区（客户端只发点击包）：
        // 幽灵**交接保持**到物品到位，避免中间露出一段空工作区（用户 2026-09-27 反馈）
        this.brbe$beginPlacementHandover(result);

        ItemStack inputStack = getInputStack(result);
        Ingredient ingredient = getIngredient(result.recipe);

        int slotIndex = 0;
        int usedInputSlots = 0;
        for (Slot slot : menu.slots) {
            ItemStack itemStack = slot.getItem();

            if (ItemStack.isSameItemSameComponents(inputStack, itemStack)) {
                if (usedInputSlots <= 2) {
                    ClientInventoryUtil.moveItemToSlot(menu, slotIndex, menu.getSlot(usedInputSlots).index);
                    ++usedInputSlots;
                }
            } else if (ClientCompat.firstIngredientItem(ingredient).getItem().equals(slot.getItem().getItem())) {
                ClientInventoryUtil.moveItemToSlot(menu, slotIndex, menu.getSlot(3).index);
            }

            ++slotIndex;
        }

        this.updateCollections(false);
    }

    public void recipesUpdated() {
        updateCollections(false);
    }

}
