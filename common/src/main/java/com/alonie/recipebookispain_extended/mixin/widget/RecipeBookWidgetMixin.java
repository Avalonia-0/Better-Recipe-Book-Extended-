package com.alonie.recipebookispain_extended.mixin.widget;

import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.util.RecipeBookDebugLogger;
import com.alonie.recipebookispain_extended.RecipeBookIsPain;
import com.alonie.recipebookispain_extended.RecipeBookIsPainExtendedConfig;
import com.alonie.recipebookispain_extended.access.CreativeTabButtonAccess;
import com.alonie.recipebookispain_extended.access.RecipeBookScrollAccess;
import com.alonie.recipebookispain_extended.access.RecipeGroupButtonPlacement;
import com.alonie.recipebookispain_extended.access.RecipeGroupButtonPlacementAccess;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.RecipeBookCategories;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 1.21.1 RBIP: 16-slot 3-edge tab layout matching 1.21.11 design.
 * <p>
 * Layout: left column (6 NORMAL) + top row (5 TOP rotated) + bottom row (5 BOTTOM rotated) = 16 slots.
 * The search tab is pinned to slot 0; creative tabs fill the remaining slots.
 * Pagination kicks in when tabs exceed slots per page.
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookWidgetMixin implements RecipeBookScrollAccess {

    // ── Shadow fields ──────────────────────────────────────────

    @Shadow @Final @Mutable
    private List<RecipeBookTabButton> tabButtons;

    @Shadow
    private RecipeBookTabButton selectedTab;

    @Shadow
    protected Minecraft minecraft;

    @Shadow
    private int width;

    @Shadow
    private int height;

    @Shadow
    private int xOffset;

    @Shadow
    private boolean widthTooNarrow;

    @Shadow
    private ClientRecipeBook book;

    @Shadow
    protected RecipeBookMenu menu;

    @Shadow
    private boolean visible;

    // ── Layout constants ──────────────────────────────────────────

    @Unique private static final ResourceLocation TEX_PAGE_BTNS =
            ResourceLocation.fromNamespaceAndPath("brbe", "textures/rbip/recipe_book_buttons.png");
    @Unique private static final int VANILLA_BOOK_W = 147;
    @Unique private static final int VANILLA_BOOK_H = 166;
    @Unique private static final int TAB_W = 35;
    @Unique private static final int TAB_H = 27;
    @Unique private static final int ROT_TAB_W = 27;
    @Unique private static final int ROT_TAB_H = 35;
    @Unique private static final int LEFT_SLOTS = 6;
    @Unique private static final int HORIZ_STEP = 27;
    @Unique private static final int PAGE_BTN_W = 14;
    @Unique private static final int PAGE_BTN_H = 13;

    // ── Dynamic helpers (support expanded recipe book) ──────────

    @Unique
    private boolean rbip$isExpanded() {
        return com.alonie.brbe.BetterRecipeBook.ctx().config().expandedRecipeBook
                && !this.widthTooNarrow
                && this.visible;
    }

    @Unique
    private int rbip$getBookW() {
        if (rbip$isExpanded()) {
            int invImageWidth = 176;
            int leftPos = ((com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor) this)
                    .updateScreenPositionInvoker(this.width, invImageWidth);
            int bookLeft = (this.width - VANILLA_BOOK_W) / 2 - this.xOffset;
            return (leftPos + invImageWidth) - bookLeft;
        }
        return VANILLA_BOOK_W;
    }

    @Unique
    private int rbip$getTopSlots() {
        if (!rbip$isExpanded()) return 5;
        int bookW = rbip$getBookW();
        return Math.max(0, (bookW - 12) / HORIZ_STEP);
    }

    @Unique
    private int rbip$getBottomSlots() {
        return rbip$getTopSlots(); // symmetric
    }

    // ── Vanilla sub-categories to exclude (all screen types) ──

    @Unique
    private static boolean rbip$isSearchCategory(RecipeBookCategories cat) {
        return cat == RecipeBookCategories.CRAFTING_SEARCH
                || cat == RecipeBookCategories.FURNACE_SEARCH
                || cat == RecipeBookCategories.SMOKER_SEARCH
                || cat == RecipeBookCategories.BLAST_FURNACE_SEARCH;
    }

    // ── RBIP state ─────────────────────────────────────────────
    // NOTE: All @Unique fields use lazy init via rbip$ensureFields()
    // because Mixin field initializers are unreliable when many mixins
    // target the same class (common in large modpacks like ATM10).

    @Unique private List<RecipeBookTabButton> rbip$creativeButtons;
    @Unique private Map<RecipeBookTabButton, CreativeModeTab> rbip$buttonToTab;
    @Unique private RecipeBookTabButton rbip$pinnedTab;
    @Unique private List<RecipeBookTabButton> rbip$pageableTabs;
    @Unique private int rbip$page;
    @Unique private int rbip$pageCount = 1;
    @Unique private int rbip$pageControlX;
    @Unique private int rbip$pageControlY;

    @Unique
    private void rbip$ensureFields() {
        if (rbip$creativeButtons == null) rbip$creativeButtons = new ArrayList<>();
        if (rbip$buttonToTab == null) rbip$buttonToTab = new HashMap<>();
        if (rbip$pageableTabs == null) rbip$pageableTabs = new ArrayList<>();
    }

    // ── initVisuals TAIL: defer actual creation to first render ──

    @Unique private boolean rbip$tabsNeedBuild = true;

    @Inject(at = @At("TAIL"), method = "initVisuals")
    private void rbip$injectCreativeTabs(CallbackInfo ci) {
        // Defer widget creation to the first render frame.
        rbip$tabsNeedBuild = true;
        // Clear stale RBIP state from previous screen session.
        // Without this, activeCreativeTab can persist across screen
        // close/reopen, causing the pipeline to use a stale variant.
        RecipeBookIsPain.activeCreativeTab = null;
        RecipeBookIsPain.activeFurnaceType = null;
    }

    // ── updateTabs TAIL: rebuild after vanilla refresh ─────────

    @Inject(at = @At("TAIL"), method = "updateTabs")
    private void rbip$afterUpdateTabs(CallbackInfo ci) {
        if (!RecipeBookIsPainExtendedConfig.enabled()) return;
        rbip$ensureFields();
        if (rbip$creativeButtons.isEmpty()) return;
        this.rbip$rebuildTabList();
    }

    // ── render TAIL: scroll + page controls + tooltip ──────────

    @Inject(at = @At("TAIL"), method = "render")
    private void rbip$renderTail(GuiGraphics gui, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!RecipeBookIsPainExtendedConfig.enabled()) return;
        // Don't render page controls or tooltips when the book is collapsed
        if (!this.visible) return;

        // Consume scroll
        int scroll = RecipeBookIsPain.rbip$consumeScroll();
        if (scroll != 0 && rbip$pageCount > 1) {
            this.rbip$scrollPages(mouseX, mouseY, scroll);
        }

        // Render page controls
        if (rbip$pageCount > 1) this.rbip$drawPageControls(gui, mouseX, mouseY);

        // Tooltips
        if (minecraft.screen == null) return;
        for (RecipeBookTabButton btn : this.tabButtons) {
            if (!btn.visible || !btn.isMouseOver(mouseX, mouseY)) continue;
            CreativeModeTab tab = rbip$buttonToTab.get(btn);
            if (tab != null) {
                gui.renderTooltip(minecraft.font, tab.getDisplayName(), mouseX, mouseY);
            } else if (btn.getCategory() == RecipeBookCategories.CRAFTING_SEARCH) {
                gui.renderTooltip(minecraft.font,
                        net.minecraft.world.item.CreativeModeTabs.searchTab().getDisplayName(),
                        mouseX, mouseY);
            }
            break;
        }

        // Page-number tooltip
        if (rbip$pageCount > 1
                && rbip$isInside(mouseX, mouseY, rbip$pageControlX, rbip$pageControlY, PAGE_BTN_W, PAGE_BTN_H)
                || rbip$isInside(mouseX, mouseY, rbip$pageControlX + 15, rbip$pageControlY, PAGE_BTN_W, PAGE_BTN_H)) {
            gui.renderTooltip(minecraft.font,
                    net.minecraft.network.chat.Component.literal((rbip$page + 1) + "/" + rbip$pageCount),
                    mouseX, mouseY);
        }
    }

    // ── render HEAD: hot-reload + deferred tab creation ──────────

    @Inject(at = @At("HEAD"), method = "render")
    private void rbip$hotReload(GuiGraphics g, int mx, int my, float d, CallbackInfo ci) {
        // Guard: when RBIP is disabled, the tabsNeedBuild branch below never
        // runs buildCreativeTabs() (which calls ensureFields()), so the wipe
        // in the reload branch would NPE on the null creativeButtons list.
        rbip$ensureFields();

        // Deferred tab creation: initVisuals sets a flag, we do the
        // expensive widget creation here on the first render frame.
        // This keeps initVisuals fast (0ms instead of 200-300ms).
        if (rbip$tabsNeedBuild) {
            rbip$tabsNeedBuild = false;
            if (RecipeBookIsPainExtendedConfig.enabled()) {
                rbip$buildCreativeTabs();
            }
        }

        if (!RecipeBookIsPainExtendedConfig.reloadIfChanged()) return;
        RecipeBookIsPain.LOGGER.info("[RBIP] Config changed — reload");

        // 1) Wipe old state
        rbip$creativeButtons.clear();
        rbip$buttonToTab.clear();
        rbip$pinnedTab = null;
        rbip$pageableTabs = List.of();
        rbip$page = 0;
        rbip$pageCount = 1;
        RecipeBookIsPain.activeCreativeTab = null;
        RecipeBookIsPain.activeFurnaceType = null;

        // 2) Re-init data (rescans tabs, updates CRAFTING_LIST)
        RecipeBookIsPain.onConfigChanged();
        RecipeBookIsPain.ensureInitialized();

        // 3) Re-create creative buttons from updated tab list
        for (CreativeModeTab tab : RecipeBookIsPain.CRAFTING_LIST) {
            RecipeBookTabButton btn = new RecipeBookTabButton(RecipeBookCategories.UNKNOWN);
            ((CreativeTabButtonAccess) btn).rbip$setCreativeTab(tab);
            rbip$creativeButtons.add(btn);
            rbip$buttonToTab.put(btn, tab);
        }

        // 4) Force UI refresh
        this.rbip$invokeUpdateTabs();
    }

    @SuppressWarnings("unused")
    @Invoker("updateTabs")
    public abstract void rbip$invokeUpdateTabs();

    @Unique
    private void rbip$buildCreativeTabs() {
        rbip$ensureFields();
        RecipeBookIsPain.ensureInitialized();
        List<CreativeModeTab> creativeTabs = RecipeBookIsPain.CRAFTING_LIST;
        if (creativeTabs.isEmpty()) {
            RecipeBookDebugLogger.onRbipTabsBuilt(0, 0);
            return;
        }

        rbip$creativeButtons.clear();
        rbip$buttonToTab.clear();
        for (CreativeModeTab tab : creativeTabs) {
            RecipeBookTabButton btn = new RecipeBookTabButton(RecipeBookCategories.UNKNOWN);
            ((CreativeTabButtonAccess) btn).rbip$setCreativeTab(tab);
            rbip$creativeButtons.add(btn);
            rbip$buttonToTab.put(btn, tab);
        }
        this.rbip$rebuildTabList();
        RecipeBookIsPain.LOGGER.info("[RBIP] {} creative tabs (deferred build)", rbip$creativeButtons.size());
        RecipeBookDebugLogger.onRbipTabsBuilt(rbip$creativeButtons.size(), creativeTabs.size());
    }

    // ── mouseClicked HEAD: creative tabs + page controls ───────

    @Inject(at = @At("HEAD"), method = "mouseClicked", cancellable = true)
    private void rbip$handleClick(double mx, double my, int btn,
                                   CallbackInfoReturnable<Boolean> cir) {
        rbip$ensureFields();
        if (!RecipeBookIsPainExtendedConfig.enabled() || btn != 0) return;
        // Don't process page-control or creative-tab clicks when book is collapsed
        if (!this.visible) return;

        // Page controls
        if (rbip$pageCount > 1) {
            int pcx = rbip$pageControlX, pcy = rbip$pageControlY;
            if (rbip$isInside(mx, my, pcx, pcy, PAGE_BTN_W, PAGE_BTN_H) && rbip$page > 0) {
                rbip$page--;
                this.rbip$applyPagination(false);
                cir.setReturnValue(true);
                return;
            }
            if (rbip$isInside(mx, my, pcx + 15, pcy, PAGE_BTN_W, PAGE_BTN_H)
                    && rbip$page < rbip$pageCount - 1) {
                rbip$page++;
                this.rbip$applyPagination(false);
                cir.setReturnValue(true);
                return;
            }
        }

        // Creative tabs
        for (RecipeBookTabButton b : rbip$creativeButtons) {
            if (!b.visible || !b.isMouseOver(mx, my)) continue;
            CreativeModeTab tab = rbip$buttonToTab.get(b);
            if (tab == null) continue;

            RecipeBookIsPain.LOGGER.info("[RBIP] Selected: {}", tab.getDisplayName().getString());
            if (this.selectedTab != null && this.selectedTab != b) {
                this.selectedTab.setStateTriggered(false);
            }
            b.setStateTriggered(true);
            this.selectedTab = b;
            RecipeBookIsPain.activeCreativeTab = tab;
            // Also track furnace type for the @Redirect in ClientRecipeBookMixin
            String furnaceType = null;
            if (this.menu instanceof AbstractFurnaceMenu furnaceMenu) {
                RecipeBookIsPain.activeFurnaceType = RecipeBookIsPain.detectFurnaceType(furnaceMenu);
                furnaceType = RecipeBookIsPain.activeFurnaceType.name();
            } else {
                RecipeBookIsPain.activeFurnaceType = null;
            }
            RecipeBookDebugLogger.onRbipTabSelected(
                    tab.getDisplayName().getString(), furnaceType);
            this.minecraft.getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            ((RecipeBookComponentAccessor) this).updateCollectionsInvoker(false);
            cir.setReturnValue(true);
            return;
        }
    }

    // ── RecipeBookScrollAccess ─────────────────────────────────

    @Override
    public boolean rbip$scrollPages(double mouseX, double mouseY, double verticalAmount) {
        if (rbip$pageCount <= 1 || verticalAmount == 0) return false;
        if (!this.rbip$isMouseOverAnyVisibleTab(mouseX, mouseY)) return false;

        int next = rbip$page + (verticalAmount > 0 ? -1 : 1);
        next = Math.max(0, Math.min(next, rbip$pageCount - 1));
        if (next != rbip$page) {
            rbip$page = next;
            this.rbip$applyPagination(false);
            return true;
        }
        return false;
    }

    // ── Tab list rebuild ───────────────────────────────────────

    @Unique
    private void rbip$rebuildTabList() {
        rbip$ensureFields();
        // Find the search tab (any screen type) to use as pinned tab
        RecipeBookTabButton search = null;
        for (RecipeBookTabButton btn : this.tabButtons) {
            if (btn instanceof CreativeTabButtonAccess access && access.rbip$getCreativeTab() != null) continue;
            if (rbip$buttonToTab.containsKey(btn)) continue;
            if (rbip$isSearchCategory(btn.getCategory())) {
                if (search == null) search = btn;
            }
        }

        this.rbip$pinnedTab = search;
        this.rbip$pageableTabs = new ArrayList<>();

        // Compute which creative tabs have recipes (cached, single-pass).
        // Uses ITEM_TO_TAB from RecipeBookIsPain for O(1) per-recipe lookup,
        // avoiding the old incremental 2-tabs-per-frame approach entirely.
        Set<CreativeModeTab> tabsWithRecipes = rbip$getTabsWithRecipes();

        for (RecipeBookTabButton btn : rbip$creativeButtons) {
            CreativeModeTab tab = rbip$buttonToTab.get(btn);
            if (tab != null && tabsWithRecipes.contains(tab)) {
                rbip$pageableTabs.add(btn);
            }
        }

        this.rbip$applyPagination(true);
    }

    // ── Cached tab→recipes computation ───────────────────────────
    // Cache is keyed by search category because different screen types
    // (crafting / furnace / smoker / blast-furnace) have disjoint
    // recipe sets.  A tab with crafting recipes may have zero furnace
    // recipes and should be hidden on the furnace screen.

    /** Cache: category → tabs that have ≥1 unlocked recipe. */
    @Unique
    private static final Map<RecipeBookCategories, Set<CreativeModeTab>> brbe$cachedRecipeTabs = new HashMap<>();

    /** Generation at which the cache was built. */
    @Unique
    private static int brbe$cachedGeneration = -1;

    /** unlockAll state at cache-build time. Toggling invalidates. */
    @Unique
    private static boolean brbe$cachedUnlockAll;

    @Unique
    private Set<CreativeModeTab> rbip$getTabsWithRecipes() {
        RecipeBookCategories category = rbip$getSearchCategory();
        // Global invalidation: recipes changed OR unlockAll toggled
        boolean currentUnlockAll = com.alonie.brbe.BetterRecipeBook.ctx().config() != null
                && com.alonie.brbe.BetterRecipeBook.ctx().config().newRecipes.unlockAll;
        if (brbe$cachedGeneration != RecipeBookIsPain.recipeGeneration
                || brbe$cachedUnlockAll != currentUnlockAll) {
            brbe$cachedRecipeTabs.clear();
            brbe$cachedGeneration = RecipeBookIsPain.recipeGeneration;
            brbe$cachedUnlockAll = currentUnlockAll;
        }
        return brbe$cachedRecipeTabs.computeIfAbsent(category,
                k -> rbip$computeTabsWithRecipes());
    }

    @Unique
    private Set<CreativeModeTab> rbip$computeTabsWithRecipes() {
        Set<CreativeModeTab> result = new HashSet<>();
        if (this.book == null) return result;

        List<RecipeCollection> collections = this.book.getCollection(rbip$getSearchCategory());
        if (collections == null) return result;

        // 收集所有配方产出物品
        // Collect all recipe result items for cross-referencing against TAB_ITEMS.
        Set<Item> allRecipeResults = new HashSet<>();
        for (RecipeCollection col : collections) {
            for (RecipeHolder<?> holder : col.getRecipes()) {
                ItemStack resultStack = holder.value().getResultItem(
                        minecraft.level == null
                                ? net.minecraft.client.Minecraft.getInstance().level.registryAccess()
                                : minecraft.level.registryAccess());
                if (!resultStack.isEmpty()) {
                    allRecipeResults.add(resultStack.getItem());
                }
            }
        }

        // 遍历所有已映射的创造标签页，检查是否有任何一个配方产出物品属于该标签页
        // Iterate all mirrored creative tabs — check if ANY recipe result is in that tab.
        // This correctly handles items that belong to multiple tabs (e.g. piston
        // in both building_blocks and redstone_blocks), ensuring all relevant
        // tabs are visible regardless of ITEM_TO_TAB's single-tab limitation.
        for (CreativeModeTab tab : RecipeBookIsPain.CRAFTING_LIST) {
            Set<Item> tabItems = RecipeBookIsPain.getItemsForTab(tab);
            for (Item item : allRecipeResults) {
                if (tabItems.contains(item)) {
                    result.add(tab);
                    break;
                }
            }
        }
        return result;
    }

    @Unique
    private RecipeBookCategories rbip$getSearchCategory() {
        if (this.menu instanceof AbstractFurnaceMenu furnaceMenu) {
            if (furnaceMenu instanceof net.minecraft.world.inventory.SmokerMenu) return RecipeBookCategories.SMOKER_SEARCH;
            if (furnaceMenu instanceof net.minecraft.world.inventory.BlastFurnaceMenu) return RecipeBookCategories.BLAST_FURNACE_SEARCH;
            return RecipeBookCategories.FURNACE_SEARCH;
        }
        return RecipeBookCategories.CRAFTING_SEARCH;
    }

    // ── Pagination ─────────────────────────────────────────────

    @Unique
    private int rbip$getTotalSlots() {
        if (!RecipeBookIsPainExtendedConfig.enabled()) return 6;
        if (rbip$isExpanded()) {
            // Dynamic: left column + full top/bottom rows
            return LEFT_SLOTS + rbip$getTopSlots() + rbip$getBottomSlots();
        }
        return RecipeBookIsPainExtendedConfig.bottomNumber();
    }

    @Unique
    private void rbip$applyPagination(boolean followCurrentTab) {
        int pinnedCount = (rbip$pinnedTab == null) ? 0 : 1;
        int groupsPerPage = Math.max(1, rbip$getTotalSlots() - pinnedCount);

        // Place pinned tab
        int slot = 0;
        if (rbip$pinnedTab != null) {
            rbip$pinnedTab.visible = true;
            this.rbip$placeTab(rbip$pinnedTab, slot);
            slot++;
        }

        rbip$pageCount = Math.max(1,
                (rbip$pageableTabs.size() + groupsPerPage - 1) / groupsPerPage);

        if (rbip$pageCount <= 1) {
            rbip$page = 0;
            rbip$pageControlX = rbip$getPageControlX();
            rbip$pageControlY = rbip$getPageControlY();
            for (RecipeBookTabButton btn : rbip$pageableTabs) {
                btn.visible = true;
                this.rbip$placeTab(btn, slot++);
            }
            this.tabButtons = rbip$buildFinalButtonList();
            return;
        }

        if (followCurrentTab && this.selectedTab != null) {
            int idx = rbip$pageableTabs.indexOf(this.selectedTab);
            if (idx >= 0) rbip$page = idx / groupsPerPage;
        }
        rbip$page = Math.max(0, Math.min(rbip$page, rbip$pageCount - 1));

        int start = rbip$page * groupsPerPage;
        int end = Math.min(start + groupsPerPage, rbip$pageableTabs.size());

        for (int i = 0; i < rbip$pageableTabs.size(); i++) {
            RecipeBookTabButton btn = rbip$pageableTabs.get(i);
            if (i >= start && i < end) {
                btn.visible = true;
                this.rbip$placeTab(btn, slot++);
            } else {
                btn.visible = false;
                ((RecipeGroupButtonPlacementAccess) btn).rbip$setPlacement(RecipeGroupButtonPlacement.NORMAL);
                btn.setWidth(TAB_W);
                btn.setHeight(TAB_H);
            }
        }

        rbip$pageControlX = rbip$getPageControlX();
        rbip$pageControlY = rbip$getPageControlY();
        this.tabButtons = rbip$buildFinalButtonList();
    }

    @Unique
    private List<RecipeBookTabButton> rbip$buildFinalButtonList() {
        List<RecipeBookTabButton> result = new ArrayList<>();
        if (rbip$pinnedTab != null) result.add(rbip$pinnedTab);
        result.addAll(rbip$pageableTabs);
        return result;
    }

    // ── Tab placement (3-edge layout) ──────────────────────────

    @Unique
    private void rbip$placeTab(RecipeBookTabButton btn, int slot) {
        int topSlots = rbip$getTopSlots();
        int bottomSlots = rbip$getBottomSlots();

        if (slot < LEFT_SLOTS) {
            ((RecipeGroupButtonPlacementAccess) btn).rbip$setPlacement(RecipeGroupButtonPlacement.NORMAL);
            btn.setX(rbip$getTabX());
            btn.setY(rbip$getTabY() + TAB_H * slot);
            btn.setWidth(TAB_W);
            btn.setHeight(TAB_H);
        } else if (slot < LEFT_SLOTS + topSlots) {
            int s = slot - LEFT_SLOTS;
            ((RecipeGroupButtonPlacementAccess) btn).rbip$setPlacement(RecipeGroupButtonPlacement.TOP);
            btn.setX(rbip$getTopTabX(s));
            btn.setY(rbip$getTopTabY());
            btn.setWidth(ROT_TAB_W);
            btn.setHeight(ROT_TAB_H);
        } else if (slot < LEFT_SLOTS + topSlots + bottomSlots) {
            int s = slot - LEFT_SLOTS - topSlots;
            ((RecipeGroupButtonPlacementAccess) btn).rbip$setPlacement(RecipeGroupButtonPlacement.BOTTOM);
            btn.setX(rbip$getBottomTabX(s));
            btn.setY(rbip$getBottomTabY());
            btn.setWidth(ROT_TAB_W);
            btn.setHeight(ROT_TAB_H);
        } else {
            ((RecipeGroupButtonPlacementAccess) btn).rbip$setPlacement(RecipeGroupButtonPlacement.NORMAL);
            btn.setX(rbip$getTabX());
            btn.setY(rbip$getTabY() + TAB_H * slot);
            btn.setWidth(TAB_W);
            btn.setHeight(TAB_H);
        }
    }

    // ── Coordinate helpers ─────────────────────────────────────

    @Unique private int rbip$getBookX() { return (width - VANILLA_BOOK_W) / 2 - xOffset; }
    @Unique private int rbip$getBookY() { return (height - VANILLA_BOOK_H) / 2; }
    @Unique private int rbip$getTabX() { return rbip$getBookX() - 30; }
    @Unique private int rbip$getTabY() { return rbip$getBookY() + 3; }
    @Unique private int rbip$getPageControlX() { return rbip$getBookX() - 28; }
    @Unique private int rbip$getPageControlY() { return rbip$getBookY() - 12; }
    @Unique private int rbip$getHorizontalTabStartX() {
        int bookW = rbip$getBookW();
        int topSlots = rbip$getTopSlots();
        return rbip$getBookX() + (bookW - topSlots * ROT_TAB_W) / 2;
    }
    @Unique private int rbip$getTopTabX(int slot) { return rbip$getHorizontalTabStartX() + slot * HORIZ_STEP; }
    @Unique private int rbip$getTopTabY() { return rbip$getBookY() - ROT_TAB_H + 5; }
    @Unique private int rbip$getBottomTabX(int slot) { return rbip$getHorizontalTabStartX() + slot * HORIZ_STEP; }
    @Unique private int rbip$getBottomTabY() { return rbip$getBookY() + VANILLA_BOOK_H - 5; }

    // ── Page control rendering ─────────────────────────────────

    @Unique
    private void rbip$drawPageControls(GuiGraphics gui, int mouseX, int mouseY) {
        int px = rbip$pageControlX;
        int py = rbip$pageControlY;

        // Left arrow
        boolean la = rbip$page > 0;
        boolean lh = la && rbip$isInside(mouseX, mouseY, px, py, PAGE_BTN_W, PAGE_BTN_H);
        int lu = lh ? 28 : 0;
        int lv = la ? 0 : 13;
        gui.blit(TEX_PAGE_BTNS, px, py, lu, lv, PAGE_BTN_W, PAGE_BTN_H, 256, 256);

        // Right arrow
        boolean ra = rbip$page < rbip$pageCount - 1;
        boolean rh = ra && rbip$isInside(mouseX, mouseY, px + 15, py, PAGE_BTN_W, PAGE_BTN_H);
        int ru = 14 + (rh ? 28 : 0);
        int rv = ra ? 0 : 13;
        gui.blit(TEX_PAGE_BTNS, px + 15, py, ru, rv, PAGE_BTN_W, PAGE_BTN_H, 256, 256);
    }

    @Unique
    private static boolean rbip$isInside(double x, double y, int l, int t, int w, int h) {
        return x >= l && x < l + w && y >= t && y < t + h;
    }

    // ── Scroll hit-test (matching 1.21.11) ─────────────────────

    @Unique private static final int SCROLL_PADDING = 20;

    @Unique
    private boolean rbip$isMouseOverAnyVisibleTab(double mouseX, double mouseY) {
        // 固定滚动区域 = 创造模式标签的完整容纳空间（不依赖实际放置的标签）：
        // 左侧 6 槽整列 + 顶部整行 + 底部整行（含 SCROLL_PADDING 上下扩展）。
        // 只要鼠标落在这些槽位区域内（即便该处没有标签）即可翻页。
        if (rbip$isInside(mouseX, mouseY, rbip$getTabX(), rbip$getTabY(),
                TAB_W, LEFT_SLOTS * TAB_H)) {
            return true;
        }
        int horizX = rbip$getHorizontalTabStartX();
        int horizW = rbip$getTopSlots() * HORIZ_STEP;
        int horizH = ROT_TAB_H + 2 * SCROLL_PADDING;
        if (rbip$isInside(mouseX, mouseY, horizX, rbip$getTopTabY() - SCROLL_PADDING,
                horizW, horizH)) {
            return true;
        }
        return rbip$isInside(mouseX, mouseY, horizX, rbip$getBottomTabY() - SCROLL_PADDING,
                horizW, horizH);
    }
}
