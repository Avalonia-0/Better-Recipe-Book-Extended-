package com.alonie.brbe.mixins.incompletecrafting;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.mixins.accessors.RecipeCollectionAccessor;
import com.alonie.brbe.util.CollectionCategory;
import com.alonie.brbe.util.IncompatibleCraftingUtil;
import com.alonie.brbe.util.PartialCraftingUtil;
import com.alonie.brbe.util.RecipeBookState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Predicate;

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {
    @Shadow @Final
    protected RecipeBookMenu menu;

    @Shadow @Final
    protected Minecraft minecraft;

    @Shadow
    private net.minecraft.client.gui.components.EditBox searchBox;

    @Shadow
    private net.minecraft.client.ClientRecipeBook book;

    @Shadow
    private List<net.minecraft.client.gui.screens.recipebook.RecipeBookComponent.TabInfo> tabInfos;

    @Unique
    private long brbe$lastSlotHash;

    // -- diagnostic: store processed list to ensure same objects as rendering --
    @Unique
    private List<RecipeCollection> brbe$lastProcessedCollections;

    @Unique
    private net.minecraft.world.item.ItemStack brbe$lastCarried = net.minecraft.world.item.ItemStack.EMPTY;

    /**
     * 鼠标拿起物品 = 放入一个特殊槽位（carried）。槽位变化应触发配方书刷新，
     * 让配方书基于 slots+carried 重新计算状态（拿起新材料 → 新配方可合成；
     * 拿起已有材料 → 材料集合不变 → 状态不变）。
     *
     * <p>必须在触发前重置 brbe$lastSlotHash：vanilla tick 已因槽位变化跑过
     * 一轮 selectMatchingRecipes（用不含 carried 的 stackedContents 清空
     * craftable 集合），若 lastSlotHash 保持相同，第二轮 updateCollections
     * 会走 vanilla removeIf 提前分支，BRBE 的 elevate 不重跑 → 材料齐全的
     * 配方掉出可合成。重置后 inventoryChanged=true → 走完整标记 + carried 提升。
     */
    @Inject(method = "tick", at = @At("RETURN"))
    private void brbe$detectCarriedChange(CallbackInfo ci) {
        if (!((net.minecraft.client.gui.screens.recipebook.RecipeBookComponent) (Object) this).isVisible()) {
            return;
        }
        ItemStack carried = this.menu.getCarried();
        if (!ItemStack.matches(carried, this.brbe$lastCarried)) {
            this.brbe$lastCarried = carried.copy();
            this.brbe$lastSlotHash = 0; // 强制下一轮走完整标记路径
            ((RecipeBookComponentAccessor) this).updateStackedContentsInvoker();
        }
    }


    @Inject(method = "updateCollections", at = @At("HEAD"))
    private void brbe$trackPartialFilteringUpdate(boolean resetPageNumber, boolean isFiltering, CallbackInfo ci) {
        RecipeBookState.beginCollectionProcessing();
        // When the player switches tabs or reopens the recipe book
        // (resetPageNumber=true), the collections list contains entirely new
        // RecipeCollection objects that have never been through
        // markPartialMaterials.  Reset the slot hash so the removeIf gate
        // below doesn't skip partial evaluation for these new collections.
        if (resetPageNumber) {
            this.brbe$lastSlotHash = 0;
        }
        boolean retainIncompatible = BetterRecipeBook.config.showAllRecipesInSurvival
                && !isFiltering
                && this.minecraft != null
                && this.minecraft.screen instanceof InventoryScreen;
        IncompatibleCraftingUtil.beginFiltering(retainIncompatible);
    }

    /**
     * After vanilla clears and repopulates craftable sets for all
     * collections (via the abstract selectMatchingRecipes per-collection
     * method), re-inject partially-craftable recipes that were previously
     * marked.  Without this, tick() → updateStackedContents() →
     * selectMatchingRecipes() wipes the injection that was done during
     * the previous updateCollections() call, causing partial recipes to
     * appear for one frame then disappear.
     */
    @Inject(method = "selectMatchingRecipes", at = @At("RETURN"))
    private void brbe$reinjectAfterSelectMatching(CallbackInfo ci) {
        if (!BetterRecipeBook.config.partialMarkingEnabled) return;

        for (net.minecraft.client.gui.screens.recipebook.RecipeBookComponent.TabInfo tabInfo : this.tabInfos) {
            for (RecipeCollection collection : this.book.getCollection(tabInfo.category())) {
                if (PartialCraftingUtil.hasPartialMaterialsEvenIfStale(collection)) {
                    RecipeCollectionAccessor accessor = (RecipeCollectionAccessor) collection;
                    for (RecipeDisplayEntry entry : collection.getRecipes()) {
                        if (PartialCraftingUtil.isPartiallyCraftableEvenIfStale(collection, entry.id())) {
                            accessor.brbe$getCraftable().add(entry.id());
                        }
                    }
                }
            }
        }
    }

    // ordinal = 0: 26.1.2 has three removeIf(Predicate) calls inside
    // updateCollections.  Only intercept the first one (the main craftability
    // filter) so that the search filter and the crafting-table filter still
    // run vanilla's own predicate with our already-modified craftable set.
    @Redirect(method = "updateCollections", at = @At(value = "INVOKE", target = "Ljava/util/List;removeIf(Ljava/util/function/Predicate;)Z", ordinal = 0))
    private boolean brbe$keepPartiallyCraftable(List<RecipeCollection> collections, Predicate<? super RecipeCollection> predicate) {
        this.brbe$lastProcessedCollections = collections;

        // ── Gate variables: single point of truth for each concern ──
        boolean onInventoryScreen = this.minecraft != null
                && this.minecraft.screen instanceof InventoryScreen;
        boolean retainPartial = BetterRecipeBook.config.partialMarkingEnabled;
        boolean retainIncompatible = onInventoryScreen
                && BetterRecipeBook.config.showAllRecipesInSurvival;
        // When showAllRecipesInSurvival is off, 3×3 recipes must never
        // be in PARTIAL_RECIPES — regardless of which screen we're on.
        // (The screen might not be InventoryScreen yet if updateCollections
        // fires during screen transition.)

        // ── Slot cache: skip when inventory unchanged ──
        // 鼠标拿起物（carried）也算作物品栏一部分，纳入哈希以触发重标记。
        net.minecraft.world.item.ItemStack carried = this.menu.getCarried();
        long slotHash = PartialCraftingUtil.slotHash(this.menu.slots, carried);
        boolean inventoryChanged = (slotHash != this.brbe$lastSlotHash);
        // Config changes also force a full re-marking pass.
        // Consumed here (inside the normal tick→updateCollections path)
        // so the page number is NOT reset.
        boolean configChanged = BetterRecipeBook.ctx() != null
                && BetterRecipeBook.ctx().events().consumeConfigChange();
        if (!inventoryChanged && !retainIncompatible && !configChanged) {
            return collections.removeIf(predicate);
        }

        // ── Cleanup when partialMarkingEnabled is toggled OFF ──
        // Step 0 uses EvenIfStale queries which are gated by enabled().
        // When the feature is disabled, enabled() returns false and EvenIfStale
        // queries skip cleanup, leaving stale partial recipes permanently
        // injected into the craftable set.  Use Raw queries (no enabled()
        // guard) to purge them unconditionally when the feature is off.
        if (!retainPartial) {
            for (RecipeCollection collection : collections) {
                if (PartialCraftingUtil.hasPartialMaterialsRaw(collection)) {
                    RecipeCollectionAccessor accessor = (RecipeCollectionAccessor) collection;
                    for (RecipeDisplayEntry entry : collection.getRecipes()) {
                        if (PartialCraftingUtil.isPartiallyCraftableRaw(collection, entry.id())) {
                            accessor.brbe$getCraftable().remove(entry.id());
                        }
                    }
                }
            }
            PartialCraftingUtil.invalidateCaches();
        }

        // Only skip everything when BOTH features are off.
        if (!retainPartial && !retainIncompatible) {
            return collections.removeIf(predicate);
        }

        // ── Partial material marking (gated inside PartialCraftingUtil) ──
        this.brbe$lastSlotHash = slotHash;

        // Step 0: Clear previously-injected partial IDs from craftable set.
        // Skip 3×3 recipes when showAllRecipesInSurvival is off — they were
        // never injected (see injection guard below), so removing them would
        // only destroy vanilla's own craftable marking and cause
        // markPartialMaterials to see isCraftable()==false, re-tagging them
        // as partial.  That creates an infinite cycle where a fully-craftable
        // 3×3 recipe permanently shows the "partial" overlay.
        //
        // Uses EvenIfStale queries intentionally: Step 0 needs to see what
        // was injected in the PREVIOUS generation so it can undo those
        // injections before re-evaluating.
        for (RecipeCollection collection : collections) {
            if (PartialCraftingUtil.hasPartialMaterialsEvenIfStale(collection)) {
                RecipeCollectionAccessor accessor = (RecipeCollectionAccessor) collection;
                for (RecipeDisplayEntry entry : collection.getRecipes()) {
                    RecipeDisplayId id = entry.id();
                    if (PartialCraftingUtil.isPartiallyCraftableEvenIfStale(collection, id)) {
                        accessor.brbe$getCraftable().remove(id);
                    }
                }
            }
        }

        PartialCraftingUtil.beginFilteringUpdate(true);
        java.util.Set<net.minecraft.world.item.Item> inventoryItems = PartialCraftingUtil.hashInventory(this.menu.slots, -1, carried);
        // partialOnlyWhenCarrying：残缺配方只在拿起物品时显示，且只显示与
        // carried 相关的配方 → 匹配集仅含 carried 类型（carried 为空则空集，
        // 完全不标 partial）。
        boolean partialOnlyWhenCarrying = BetterRecipeBook.config.partialOnlyWhenCarrying;
        java.util.Set<net.minecraft.world.item.Item> markItems = partialOnlyWhenCarrying
                ? (carried.isEmpty() ? java.util.Set.of() : java.util.Set.of(carried.getItem()))
                : inventoryItems;
        // Item → 总数量。数量感知的材料齐全判定（3×3）需要它区分
        // "类型齐全但数量不足"（铁斧 3 铁锭 2 木棍，库存各 1 → 材料不足）。
        java.util.Map<net.minecraft.world.item.Item, Integer> inventoryCounts = new java.util.HashMap<>();
        for (net.minecraft.world.inventory.Slot slot : this.menu.slots) {
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                inventoryCounts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        if (!carried.isEmpty()) {
            inventoryCounts.merge(carried.getItem(), carried.getCount(), Integer::sum);
        }

        for (RecipeCollection collection : collections) {
            PartialCraftingUtil.markPartialMaterials(collection, inventoryItems, inventoryCounts, markItems);
        }

        // ── Carried-as-special-slot: when holding an item, elevate ALL
        // material-complete recipes (slots + carried) so picking up an item
        // never changes the recipe book.  Vanilla isCraftable ignores carried,
        // so a recipe whose material moved to the hand would otherwise drop
        // to partial/uncraftable.  MUST run BEFORE the partial injection below:
        // once a partial recipe is injected into the craftable set,
        // isCraftable() becomes true and the elevation would skip it.
        if (!carried.isEmpty()) {
            for (RecipeCollection collection : collections) {
                PartialCraftingUtil.elevateFullyCraftableWithCarried(collection, inventoryItems, inventoryCounts);
            }
        }

        // Inject partial recipes into craftable set
        for (RecipeCollection collection : collections) {
            if (PartialCraftingUtil.hasPartialMaterials(collection)) {
                RecipeCollectionAccessor accessor = (RecipeCollectionAccessor) collection;
                for (RecipeDisplayEntry entry : collection.getRecipes()) {
                    RecipeDisplayId id = entry.id();
                    if (PartialCraftingUtil.isPartiallyCraftable(collection, id)) {
                        accessor.brbe$getCraftable().add(id);
                    }
                }
            }
        }

        // ── Pre-check (parity with 1.21.1): on inventory screen with showAll,
        // elevate fully-craftable 3×3 recipes to craftable so they behave the
        // same as 2×2 recipes (vanilla canCraft rejects them on the 2×2 grid).
        if (retainIncompatible) {
            for (RecipeCollection collection : collections) {
                PartialCraftingUtil.elevateFullyCraftable3x3(collection, inventoryItems, inventoryCounts);
            }
        }

        PartialCraftingUtil.beginFilteringUpdate(false);

        // ── Incompatible recipe marking ──
        if (retainIncompatible) {
            for (RecipeCollection collection : collections) {
                IncompatibleCraftingUtil.markIncompatibleRecipes(collection);
            }
        }

        // ── Retention flags for the removeIf predicate ──
        boolean hasSearchActive = searchBox != null && !searchBox.getValue().isEmpty();
        boolean keepPartial = retainPartial && !hasSearchActive;
        boolean keepIncompatible = retainIncompatible
                && IncompatibleCraftingUtil.isActive()
                && !hasSearchActive;

        if (!keepPartial && !keepIncompatible) {
            return collections.removeIf(predicate);
        }

        boolean removed = collections.removeIf(collection -> {
            if (!predicate.test(collection)) return false;
            if (keepPartial && PartialCraftingUtil.hasPartialMaterials(collection)) {
                // 3×3 partial recipes are material-deficient and were injected
                // into craftable, so they survive the vanilla filter normally.
                return false;
            }
            if (keepIncompatible && IncompatibleCraftingUtil.hasIncompatibleRecipes(collection)) return false;
            return true;
        });

        return removed;
    }

    // ═══════════ 诊断：每次物品栏刷新后检查配方状态 ═══════════

    @Inject(method = "updateCollections", at = @At("TAIL"))
    private void brbe$diagnostic(boolean resetPageNumber, boolean isFiltering, CallbackInfo ci) {
        com.alonie.brbe.util.RecipeStateDiagnostic.run(brbe$lastProcessedCollections, menu.slots, this.menu.getCarried());
    }

    /** True if a crafting display needs more than a 2×2 grid. */
    @Unique
    private static boolean brbe$needsLargerGrid(RecipeDisplay display) {
        return PartialCraftingUtil.needsLargerGrid(display);
    }

}
