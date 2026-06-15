package com.alonie.recipebookispain_extended.mixin.widget;

import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.recipebookispain_extended.RbipScrollArea;
import com.alonie.recipebookispain_extended.RecipeBookIsPain;
import com.alonie.recipebookispain_extended.RecipeBookIsPainExtendedConfig;
import com.alonie.recipebookispain_extended.access.CreativeTabButtonAccess;
import com.alonie.recipebookispain_extended.access.RecipeBookScrollAccess;
import com.alonie.recipebookispain_extended.access.RecipeGroupButtonPlacement;
import com.alonie.recipebookispain_extended.access.RecipeGroupButtonPlacementAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.RecipeBookCategories;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
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
import java.util.List;
import java.util.Map;

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

    // ── Layout constants (matching 1.21.11) ────────────────────

    @Unique private static final ResourceLocation TEX_PAGE_BTNS =
            ResourceLocation.fromNamespaceAndPath("recipe-book-is-pain-extended", "textures/rbip/recipe_book_buttons.png");
    @Unique private static final int BOOK_W = 147;
    @Unique private static final int BOOK_H = 166;
    @Unique private static final int TAB_W = 35;
    @Unique private static final int TAB_H = 27;
    @Unique private static final int ROT_TAB_W = 27;
    @Unique private static final int ROT_TAB_H = 35;
    @Unique private static final int LEFT_SLOTS = 6;
    @Unique private static final int TOP_SLOTS = 5;
    @Unique private static final int BOTTOM_SLOTS = 5;
    @Unique private static final int HORIZ_STEP = 27;
    @Unique private static final int PAGE_BTN_W = 14;
    @Unique private static final int PAGE_BTN_H = 13;

    // ── Vanilla sub-categories to exclude ──────────────────────

    @Unique
    private static final RecipeBookCategories[] EXCLUDED_VANILLA = {
            RecipeBookCategories.CRAFTING_BUILDING_BLOCKS,
            RecipeBookCategories.CRAFTING_REDSTONE,
            RecipeBookCategories.CRAFTING_EQUIPMENT,
            RecipeBookCategories.CRAFTING_MISC
    };

    // ── RBIP state ─────────────────────────────────────────────

    @Unique private final List<RecipeBookTabButton> rbip$creativeButtons = new ArrayList<>();
    @Unique private final Map<RecipeBookTabButton, CreativeModeTab> rbip$buttonToTab = new HashMap<>();
    @Unique private RecipeBookTabButton rbip$pinnedTab;
    @Unique private List<RecipeBookTabButton> rbip$pageableTabs = List.of();
    @Unique private int rbip$page;
    @Unique private int rbip$pageCount = 1;
    @Unique private int rbip$pageControlX;
    @Unique private int rbip$pageControlY;

    // ── initVisuals TAIL: create creative buttons ─────────────

    @Inject(at = @At("TAIL"), method = "initVisuals")
    private void rbip$injectCreativeTabs(CallbackInfo ci) {
        if (!RecipeBookIsPainExtendedConfig.enabled()) return;

        RecipeBookIsPain.ensureInitialized();
        List<CreativeModeTab> creativeTabs = RecipeBookIsPain.CRAFTING_LIST;
        if (creativeTabs.isEmpty()) return;

        rbip$creativeButtons.clear();
        rbip$buttonToTab.clear();
        for (CreativeModeTab tab : creativeTabs) {
            RecipeBookTabButton btn = new RecipeBookTabButton(RecipeBookCategories.UNKNOWN);
            ((CreativeTabButtonAccess) btn).rbip$setCreativeTab(tab);
            rbip$creativeButtons.add(btn);
            rbip$buttonToTab.put(btn, tab);
        }
        this.rbip$rebuildTabList();
        RecipeBookIsPain.LOGGER.info("[RBIP] {} creative tabs", rbip$creativeButtons.size());
    }

    // ── updateTabs TAIL: rebuild after vanilla refresh ─────────

    @Inject(at = @At("TAIL"), method = "updateTabs")
    private void rbip$afterUpdateTabs(CallbackInfo ci) {
        if (!RecipeBookIsPainExtendedConfig.enabled() || rbip$creativeButtons.isEmpty()) return;
        this.rbip$rebuildTabList();
    }

    // ── render TAIL: scroll + page controls + tooltip ──────────

    @Inject(at = @At("TAIL"), method = "render")
    private void rbip$renderTail(GuiGraphics gui, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!RecipeBookIsPainExtendedConfig.enabled()) return;

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

    // ── render HEAD: hot-reload ────────────────────────────────

    @Inject(at = @At("HEAD"), method = "render")
    private void rbip$hotReload(GuiGraphics g, int mx, int my, float d, CallbackInfo ci) {
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

    // ── mouseClicked HEAD: creative tabs + page controls ───────

    @Inject(at = @At("HEAD"), method = "mouseClicked", cancellable = true)
    private void rbip$handleClick(double mx, double my, int btn,
                                   CallbackInfoReturnable<Boolean> cir) {
        if (!RecipeBookIsPainExtendedConfig.enabled() || btn != 0) return;

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
        // Separate search tab (pin it), exclude vanilla sub-cats and creative buttons
        List<RecipeBookTabButton> keep = new ArrayList<>();
        RecipeBookTabButton search = null;

        for (RecipeBookTabButton btn : this.tabButtons) {
            // Detect creative buttons via the access interface (robust even if map was cleared)
            if (btn instanceof CreativeTabButtonAccess access && access.rbip$getCreativeTab() != null) {
                continue;
            }
            if (rbip$buttonToTab.containsKey(btn)) continue;

            RecipeBookCategories cat = btn.getCategory();
            boolean excluded = false;
            for (RecipeBookCategories ex : EXCLUDED_VANILLA) {
                if (cat == ex) { excluded = true; break; }
            }
            if (excluded) continue;

            if (cat == RecipeBookCategories.CRAFTING_SEARCH && search == null) {
                search = btn;
            } else {
                keep.add(btn);
            }
        }

        this.rbip$pinnedTab = search;
        this.rbip$pageableTabs = keep;
        this.rbip$pageableTabs.addAll(rbip$creativeButtons);
        this.rbip$applyPagination(true);
    }

    // ── Pagination ─────────────────────────────────────────────

    @Unique
    private void rbip$applyPagination(boolean followCurrentTab) {
        int pinnedCount = (rbip$pinnedTab == null) ? 0 : 1;
        int groupsPerPage = Math.max(1,
                RecipeBookIsPainExtendedConfig.bottomNumber() - pinnedCount);

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
        if (slot < LEFT_SLOTS) {
            ((RecipeGroupButtonPlacementAccess) btn).rbip$setPlacement(RecipeGroupButtonPlacement.NORMAL);
            btn.setX(rbip$getTabX());
            btn.setY(rbip$getTabY() + TAB_H * slot);
            btn.setWidth(TAB_W);
            btn.setHeight(TAB_H);
        } else if (slot < LEFT_SLOTS + TOP_SLOTS) {
            int s = slot - LEFT_SLOTS;
            ((RecipeGroupButtonPlacementAccess) btn).rbip$setPlacement(RecipeGroupButtonPlacement.TOP);
            btn.setX(rbip$getTopTabX(s));
            btn.setY(rbip$getTopTabY());
            btn.setWidth(ROT_TAB_W);
            btn.setHeight(ROT_TAB_H);
        } else if (slot < LEFT_SLOTS + TOP_SLOTS + BOTTOM_SLOTS) {
            int s = slot - LEFT_SLOTS - TOP_SLOTS;
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

    @Unique private int rbip$getBookX() { return (width - BOOK_W) / 2 - xOffset; }
    @Unique private int rbip$getBookY() { return (height - BOOK_H) / 2; }
    @Unique private int rbip$getTabX() { return rbip$getBookX() - 30; }
    @Unique private int rbip$getTabY() { return rbip$getBookY() + 3; }
    @Unique private int rbip$getPageControlX() { return rbip$getBookX() - 28; }
    @Unique private int rbip$getPageControlY() { return rbip$getBookY() - 12; }
    @Unique private int rbip$getHorizontalTabStartX() {
        return rbip$getBookX() + (BOOK_W - TOP_SLOTS * ROT_TAB_W) / 2;
    }
    @Unique private int rbip$getTopTabX(int slot) { return rbip$getHorizontalTabStartX() + slot * HORIZ_STEP; }
    @Unique private int rbip$getTopTabY() { return rbip$getBookY() - ROT_TAB_H + 5; }
    @Unique private int rbip$getBottomTabX(int slot) { return rbip$getHorizontalTabStartX() + slot * HORIZ_STEP; }
    @Unique private int rbip$getBottomTabY() { return rbip$getBookY() + BOOK_H - 5; }

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
        RbipScrollArea topArea = null;
        RbipScrollArea bottomArea = null;

        for (RecipeBookTabButton btn : this.tabButtons) {
            if (!btn.visible) continue;
            RecipeGroupButtonPlacement p = ((RecipeGroupButtonPlacementAccess) btn).rbip$getPlacement();
            if (p == RecipeGroupButtonPlacement.TOP) {
                topArea = rbip$mergeScrollArea(topArea, rbip$expandedArea(btn));
            } else if (p == RecipeGroupButtonPlacement.BOTTOM) {
                bottomArea = rbip$mergeScrollArea(bottomArea, rbip$expandedArea(btn));
            } else if (rbip$isInside(mouseX, mouseY, btn.getX(), btn.getY(), btn.getWidth(), btn.getHeight())) {
                return true;
            }
        }
        if (topArea != null && rbip$isInside(mouseX, mouseY, topArea.left(), topArea.top(),
                topArea.width(), topArea.height())) return true;
        if (bottomArea != null && rbip$isInside(mouseX, mouseY, bottomArea.left(), bottomArea.top(),
                bottomArea.width(), bottomArea.height())) return true;
        return false;
    }

    @Unique
    private RbipScrollArea rbip$expandedArea(RecipeBookTabButton btn) {
        return new RbipScrollArea(
                btn.getX(), btn.getY() - SCROLL_PADDING,
                btn.getX() + btn.getWidth(), btn.getY() + btn.getHeight() + SCROLL_PADDING);
    }

    @Unique
    private RbipScrollArea rbip$mergeScrollArea(RbipScrollArea a, RbipScrollArea b) {
        if (a == null) return b;
        return new RbipScrollArea(
                Math.min(a.left(), b.left()), Math.min(a.top(), b.top()),
                Math.max(a.right(), b.right()), Math.max(a.bottom(), b.bottom()));
    }
}
