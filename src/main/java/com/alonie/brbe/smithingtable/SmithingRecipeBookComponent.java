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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

    public void init(int width, int height, Minecraft minecraft, boolean widthNarrow, SmithingMenu menu, Consumer<ItemStack> onGhostRecipeUpdate, RegistryAccess registryAccess) {
        super.init(width, height, minecraft, widthNarrow, menu, onGhostRecipeUpdate, registryAccess);

        this.brbe$ghostUpdater = onGhostRecipeUpdate;
        this.ghostRecipe = new SmithingGhostRecipe(onGhostRecipeUpdate, registryAccess);
        this.ghostRecipe.setDefaultRenderingPredicate(this.menu);
        // 幽灵收起（悬停离开 / 点击引导结束 / 收书）→ 盔甲架复位
        this.ghostRecipe.setOnGhostRelease(this::brbe$restoreArmorStandPreview);
        this.recipesPage = new SmithingRecipeBookPage(registryAccess, () -> BRBBookSettings.isFiltering(getRecipeBookType()));

//        if (this.isVisible()) {
        this.initVisuals();
//        }
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
        BetterRecipeBook.ensureCategories();
        return BetterRecipeBook.SMITHING;
    }

    @Override
    public void handlePlaceRecipe() {
        BRBSmithingRecipe result = this.recipesPage.getCurrentClickedRecipe();
        SmithingRecipeCollection recipeCollection = this.recipesPage.getLastClickedRecipeCollection();

        if (result == null || recipeCollection == null) return;

        // 放置路径维持只看 slots：材料在鼠标上时显示 ghost 引导放料，
        // 不把 carried 计入放置判定（放置循环只遍历 slots，无法从 carried 取料）。
        if (!result.hasMaterials(this.menu.slots, this.registryAccess, ItemStack.EMPTY)) {
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
                    ? brbe$groupKey(entry, smithingDisplay, displayContext)
                    : "grp:" + entry.group().getAsInt();
            groups.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(entry);
        }
        for (Map.Entry<Object, List<RecipeDisplayEntry>> groupEntry : groups.entrySet()) {
            List<RecipeDisplayEntry> group = groupEntry.getValue();
            // 折叠的升级组：组内顺序按「产物物品 id」字典序。known 集是 Map，迭代顺序跨会话不稳定，
            // 不排的话右键展开的选择列表每次开机顺序都可能不一样（玩家按位置找部位）。
            if (groupEntry.getKey() instanceof String key && key.startsWith("upgrade:")) {
                group.sort(java.util.Comparator.comparing(entry -> brbe$resultKey(entry, displayContext)));
            }
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

    /**
     * 未组队条目的分组键（用户 2026-09-27 诉求）：**升级配方**（{@code smithing_transform}）
     * 按**加成材料**折叠成一个替代配方组。
     *
     * <p>本实例的 12 条下界合金升级（斧/靴/胸甲/头盔/锄/马铠/护腿/鹦鹉螺铠/镐/锹/矛/剑）用同一份
     * {@code #minecraft:netherite_tool_materials}（= 下界合金锭）→ 折叠成一格、右键展开选具体部位；
     * 代表物品见 {@link SmithingRecipeButton}（锭，不是模板）。</p>
     *
     * <p>⚠️ <b>升级模板不参与分组</b>（用户 2026-09-27 二次反馈）：概念上"用同一份材料升级"
     * 就是同一组，模板只是配方书的解锁条件——BetterEnd 的下界合金锻锤用自家升级模板 + 下界合金锭，
     * 早先按「模板 + 材料」分组时它单独成一格，玩家看着像"漏了一组"。现在只要锭相同就同组。</p>
     *
     * <p>纹饰配方不在这里折叠：原版一条纹饰配方本身就展开成整组
     * （{@link BRBSmithingTrimRecipe#from} 把 {@code #minecraft:trimmable_armor} 展开成每件装备一条）。</p>
     */
    private static Object brbe$groupKey(RecipeDisplayEntry entry, SmithingRecipeDisplay display, ContextMap context) {
        if (display.result() instanceof SlotDisplay.SmithingTrimDemoSlotDisplay) {
            return entry.id();
        }
        String addition = brbe$displayItemsKey(display.addition(), context);
        if (addition == null) {
            return entry.id();
        }
        return "upgrade:" + addition;
    }

    /** 条目产物的物品 id（升级组内排序用；取不到时回退空串，排在最后）。 */
    private static String brbe$resultKey(RecipeDisplayEntry entry, ContextMap context) {
        try {
            for (ItemStack stack : entry.resultItems(context)) {
                if (!stack.isEmpty()) {
                    return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                }
            }
        } catch (Exception ignored) {
            // 解析失败不参与排序
        }
        return "";
    }

    /** SlotDisplay 解析出的物品 id 串（顺序稳定）当分组键；解析不出物品时返回 {@code null}（不折叠）。 */
    @Nullable
    private static String brbe$displayItemsKey(SlotDisplay display, ContextMap context) {
        List<ItemStack> stacks = display.resolveForStacks(context);
        StringBuilder key = new StringBuilder();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            key.append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())).append(',');
        }
        return key.isEmpty() ? null : key.toString();
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
