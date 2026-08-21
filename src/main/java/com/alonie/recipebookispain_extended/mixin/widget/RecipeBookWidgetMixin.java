package com.alonie.recipebookispain_extended.mixin.widget;

import com.alonie.recipebookispain_extended.RecipeBookIsPain;
import com.alonie.recipebookispain_extended.RecipeBookIsPain.FurnaceVariant;
import com.alonie.recipebookispain_extended.RecipeBookIsPainExtendedConfig;
import com.alonie.recipebookispain_extended.compat.polymer.PolymerCompat;
import com.alonie.recipebookispain_extended.access.RecipeGroupButtonPlacement;
import com.alonie.recipebookispain_extended.access.RecipeGroupButtonPlacementAccess;
import com.alonie.recipebookispain_extended.access.RecipeBookScrollAccess;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.screens.recipebook.CraftingRecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.FurnaceRecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.client.gui.screens.recipebook.SearchRecipeBookCategory;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(RecipeBookComponent.class)
public class RecipeBookWidgetMixin implements RecipeBookScrollAccess {
    @Unique private static final Identifier RBIP_PAGE_BUTTONS = Identifier.fromNamespaceAndPath("zzzbrbe", "textures/rbip/recipe_book_buttons.png");
    @Unique private static final int RBIP_FALLBACK_GROUPS_PER_PAGE = 5;
    @Unique private static final int RBIP_PAGE_BUTTON_WIDTH = 14;
    @Unique private static final int RBIP_PAGE_BUTTON_HEIGHT = 13;
    @Unique private static final int RBIP_BOOK_WIDTH = 147;
    @Unique private static final int RBIP_BOOK_HEIGHT = 166;
    @Unique private static final int RBIP_TAB_WIDTH = 35;
    @Unique private static final int RBIP_TAB_HEIGHT = 27;
    @Unique private static final int RBIP_ROTATED_TAB_WIDTH = 27;
    @Unique private static final int RBIP_ROTATED_TAB_HEIGHT = 35;
    @Unique private static final int RBIP_LEFT_TOTAL_SLOTS = 6;
    @Unique private static final int RBIP_BOTTOM_SLOTS = 5;
    @Unique private static final int RBIP_TOP_SLOTS = 5;
    @Unique private static final int RBIP_EXTENDED_SLOT_STEP = 27;
    @Unique private static final int RBIP_HORIZONTAL_SCROLL_OUTWARD_PADDING = 20;

    @Shadow @Final @Mutable private List<RecipeBookComponent.TabInfo> tabInfos;
    @Shadow @Final private List<RecipeBookTabButton> tabButtons;
    @Shadow protected Minecraft minecraft;
    @Shadow private ClientRecipeBook book;
    @Shadow private int width;
    @Shadow private int height;
    @Shadow private int xOffset;
    @Shadow private RecipeBookTabButton selectedTab;
    @Shadow
    public boolean isVisible() {
        throw new AssertionError();
    }

    @Shadow
    public void updateTabs(boolean filteringCraftable) {
        throw new AssertionError();
    }

    @Unique private List<RecipeBookComponent.TabInfo> rbip$vanillaTabInfos;

    @Unique private RecipeBookTabButton rbip$pinnedTab;
    @Unique private List<RecipeBookTabButton> rbip$pageableTabs = List.of();
    @Unique private int rbip$page;
    @Unique private int rbip$pageCount;
    @Unique private int rbip$pageControlX;
    @Unique private int rbip$pageControlY;

    @Inject(at = @At("TAIL"), method = "<init>")
    private void rbip$addCreativeTabs(RecipeBookMenu handler, List<RecipeBookComponent.TabInfo> tabInfos, CallbackInfo ci) {
        this.rbip$vanillaTabInfos = List.copyOf(this.tabInfos);

        if (!RecipeBookIsPainExtendedConfig.enabled()) return;
        if ((Object) this instanceof CraftingRecipeBookComponent) {
            this.tabInfos = RecipeBookIsPain.withCreativeTabs(tabInfos);
        } else if ((Object) this instanceof FurnaceRecipeBookComponent) {
            FurnaceVariant type = RecipeBookIsPain.detectFurnaceType(tabInfos);
            this.tabInfos = RecipeBookIsPain.withFurnaceCreativeTabs(tabInfos, type);
        }
    }

    @Inject(at = @At("HEAD"), method = "updateTabs")
    private void rbip$syncLateGroups(CallbackInfo ci) {
        if (!RecipeBookIsPainExtendedConfig.enabled()) return;
        if ((Object) this instanceof CraftingRecipeBookComponent) {
            PolymerCompat.refresh();
            this.tabInfos = RecipeBookIsPain.withCreativeTabs(this.tabInfos);
        } else if ((Object) this instanceof FurnaceRecipeBookComponent) {
            FurnaceVariant type = RecipeBookIsPain.detectFurnaceType(this.rbip$vanillaTabInfos);
            this.tabInfos = RecipeBookIsPain.withFurnaceCreativeTabs(this.tabInfos, type);
        }
    }

    @Inject(at = @At("TAIL"), method = "updateTabs")
    private void rbip$paginateTabButtons(boolean filteringCraftable, CallbackInfo ci) {
        List<RecipeBookTabButton> pageableTabs = new ArrayList<>();
        RecipeBookTabButton pinnedTab = null;

        for (RecipeBookTabButton widget : this.tabButtons) {
            if (!widget.visible) continue;

            // Highest priority: hide tabs whose category has no recipe collections.
            // This always applies regardless of any feature toggle state.
            List<RecipeCollection> collections = this.book.getCollection(widget.getCategory());
            if (collections == null || collections.isEmpty()) {
                widget.visible = false;
                continue;
            }

            if (pinnedTab == null && widget.getCategory() instanceof SearchRecipeBookCategory) {
                pinnedTab = widget;
            } else {
                pageableTabs.add(widget);
            }
        }

        this.rbip$pinnedTab = pinnedTab;
        this.rbip$pageableTabs = pageableTabs;
        this.rbip$applyPagination(true);
    }

    @Inject(at = @At("HEAD"), method = "extractRenderState")
    private void rbip$hotReloadOnConfigChange(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!RecipeBookIsPainExtendedConfig.reloadIfChanged()) return;
        if (this.rbip$vanillaTabInfos == null) return;
        if (!((Object) this instanceof CraftingRecipeBookComponent)
                && !((Object) this instanceof FurnaceRecipeBookComponent)) return;

        if (RecipeBookIsPainExtendedConfig.enabled()) {
            // syncLateGroups will replace tabInfos with creative tabs
        } else {
            this.tabInfos = new ArrayList<>(this.rbip$vanillaTabInfos);
        }
        this.updateTabs(false);
    }

    @Inject(at = @At("TAIL"), method = "extractRenderState")
    private void rbip$renderPageControls(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!RecipeBookIsPainExtendedConfig.enabled()) return;
        if (!this.isVisible() || this.rbip$pageCount <= 1) return;

        boolean wrap = com.alonie.brbe.BetterRecipeBook.config.scrolling.scrollAround;
        this.rbip$drawPageControl(context, this.rbip$pageControlX, this.rbip$pageControlY, false, wrap || this.rbip$page > 0, mouseX, mouseY);
        this.rbip$drawPageControl(context, this.rbip$pageControlX + 15, this.rbip$pageControlY, true, wrap || this.rbip$page < this.rbip$pageCount - 1, mouseX, mouseY);

        if (this.minecraft.gui.screen() != null
                && (this.rbip$isInside(mouseX, mouseY, this.rbip$pageControlX, this.rbip$pageControlY, RBIP_PAGE_BUTTON_WIDTH, RBIP_PAGE_BUTTON_HEIGHT)
                || this.rbip$isInside(mouseX, mouseY, this.rbip$pageControlX + 15, this.rbip$pageControlY, RBIP_PAGE_BUTTON_WIDTH, RBIP_PAGE_BUTTON_HEIGHT))) {
            context.setTooltipForNextFrame(this.minecraft.font, Component.literal((this.rbip$page + 1) + "/" + this.rbip$pageCount), mouseX, mouseY);
        }
    }

    @Inject(at = @At("HEAD"), method = "mouseClicked", cancellable = true)
    private void rbip$mouseClickedPageControls(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (!RecipeBookIsPainExtendedConfig.enabled()) return;
        if (!this.isVisible() || this.rbip$pageCount <= 1 || click.button() != 0) return;

        int x = (int) click.x();
        int y = (int) click.y();
        boolean wrap = com.alonie.brbe.BetterRecipeBook.config.scrolling.scrollAround;
        // Ctrl+左键：直接跳转首页/尾页（与配方区翻页箭头一致）。
        boolean ctrl = com.alonie.brbe.util.ClientCompat.isControlDown();

        if (this.rbip$isInside(x, y, this.rbip$pageControlX, this.rbip$pageControlY, RBIP_PAGE_BUTTON_WIDTH, RBIP_PAGE_BUTTON_HEIGHT)) {
            int prev = ctrl ? 0
                    : wrap
                            ? (this.rbip$page - 1 + this.rbip$pageCount) % this.rbip$pageCount
                            : Math.max(0, this.rbip$page - 1);
            if (prev != this.rbip$page) {
                this.rbip$page = prev;
                this.rbip$applyPagination(false);
                AbstractWidget.playButtonClickSound(this.minecraft.getSoundManager());
                cir.setReturnValue(true);
            }
        } else if (this.rbip$isInside(x, y, this.rbip$pageControlX + 15, this.rbip$pageControlY, RBIP_PAGE_BUTTON_WIDTH, RBIP_PAGE_BUTTON_HEIGHT)) {
            int next = ctrl ? this.rbip$pageCount - 1
                    : wrap
                            ? (this.rbip$page + 1) % this.rbip$pageCount
                            : Math.min(this.rbip$pageCount - 1, this.rbip$page + 1);
            if (next != this.rbip$page) {
                this.rbip$page = next;
                this.rbip$applyPagination(false);
                AbstractWidget.playButtonClickSound(this.minecraft.getSoundManager());
                cir.setReturnValue(true);
            }
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return this.rbip$scrollPages(mouseX, mouseY, verticalAmount);
    }

    @Override
    public boolean rbip$scrollPages(double mouseX, double mouseY, double verticalAmount) {
        if (!RecipeBookIsPainExtendedConfig.get().extendedFeatures()
                || this.rbip$pageCount <= 1
                || verticalAmount == 0.0D
                || !this.rbip$isMouseOverAnyVisibleTab(mouseX, mouseY)) {
            return false;
        }

        int nextPage = this.rbip$page + (verticalAmount > 0.0D ? -1 : 1);
        if (com.alonie.brbe.BetterRecipeBook.config.scrolling.scrollAround) {
            // Wrap around: a scroll past the last page returns to the first
            // (and past the first goes to the last), matching scrollAround.
            nextPage = (nextPage % this.rbip$pageCount + this.rbip$pageCount) % this.rbip$pageCount;
        } else {
            nextPage = Math.max(0, Math.min(nextPage, this.rbip$pageCount - 1));
        }
        if (nextPage != this.rbip$page) {
            this.rbip$page = nextPage;
            this.rbip$applyPagination(false);
            if (com.alonie.brbe.BetterRecipeBook.config.scrollPageSound
                    && this.minecraft.getSoundManager() != null) {
                AbstractWidget.playButtonClickSound(this.minecraft.getSoundManager());
            }
        }
        return true;
    }

    @Override
    public int rbip$getPage() {
        return this.rbip$page;
    }

    @Override
    public void rbip$setPage(int page) {
        this.rbip$page = page;
        this.rbip$applyPagination(false);
    }

    @Unique
    private void rbip$applyPagination(boolean followCurrentTab) {
        int slot = 0;
        int groupsPerPage = this.rbip$getGroupsPerPage();

        if (this.rbip$pinnedTab != null) {
            this.rbip$pinnedTab.visible = true;
            this.rbip$placeTab(this.rbip$pinnedTab, slot);
            slot++;
        }

        this.rbip$pageCount = (this.rbip$pageableTabs.size() + groupsPerPage - 1) / groupsPerPage;
        if (this.rbip$pageCount <= 1) {
            this.rbip$page = 0;
            this.rbip$pageControlX = this.rbip$getPageControlX();
            this.rbip$pageControlY = this.rbip$getPageControlY();
            for (RecipeBookTabButton widget : this.rbip$pageableTabs) {
                widget.visible = true;
                this.rbip$placeTab(widget, slot++);
            }
            return;
        }

        if (followCurrentTab && this.selectedTab != null) {
            int currentIndex = this.rbip$pageableTabs.indexOf(this.selectedTab);
            if (currentIndex >= 0) {
                this.rbip$page = currentIndex / groupsPerPage;
            }
        }

        this.rbip$page = Math.max(0, Math.min(this.rbip$page, this.rbip$pageCount - 1));
        int start = this.rbip$page * groupsPerPage;
        int end = Math.min(start + groupsPerPage, this.rbip$pageableTabs.size());

        for (int i = 0; i < this.rbip$pageableTabs.size(); i++) {
            RecipeBookTabButton widget = this.rbip$pageableTabs.get(i);
            boolean onPage = i >= start && i < end;
            widget.visible = onPage;
            if (onPage) {
                this.rbip$placeTab(widget, slot++);
            } else {
                this.rbip$resetTabPlacement(widget);
            }
        }

        this.rbip$pageControlX = this.rbip$getPageControlX();
        this.rbip$pageControlY = this.rbip$getPageControlY();
    }

    @Unique
    private int rbip$getGroupsPerPage() {
        RecipeBookIsPainExtendedConfig config = RecipeBookIsPainExtendedConfig.get();
        if (!config.extendedFeatures()) {
            return RBIP_FALLBACK_GROUPS_PER_PAGE;
        }

        int pinnedCount = this.rbip$pinnedTab == null ? 0 : 1;
        return Math.max(1, config.bottomNumber() - pinnedCount);
    }

    @Unique
    private void rbip$placeTab(RecipeBookTabButton widget, int slot) {
        RecipeBookIsPainExtendedConfig config = RecipeBookIsPainExtendedConfig.get();
        if (!config.extendedFeatures()) {
            this.rbip$placeNormalTab(widget, slot);
            return;
        }

        if (slot < RBIP_LEFT_TOTAL_SLOTS) {
            this.rbip$placeNormalTab(widget, slot);
        } else if (slot < RBIP_LEFT_TOTAL_SLOTS + RBIP_TOP_SLOTS) {
            int topSlot = slot - RBIP_LEFT_TOTAL_SLOTS;
            ((RecipeGroupButtonPlacementAccess) widget).rbip$setPlacement(RecipeGroupButtonPlacement.TOP);
            int x = this.rbip$getTopTabX(topSlot);
            int y = this.rbip$getTopTabY();
            widget.setRectangle(RBIP_ROTATED_TAB_WIDTH, RBIP_ROTATED_TAB_HEIGHT, x, y);
        } else if (slot < RBIP_LEFT_TOTAL_SLOTS + RBIP_TOP_SLOTS + RBIP_BOTTOM_SLOTS) {
            int bottomSlot = slot - RBIP_LEFT_TOTAL_SLOTS - RBIP_TOP_SLOTS;
            ((RecipeGroupButtonPlacementAccess) widget).rbip$setPlacement(RecipeGroupButtonPlacement.BOTTOM);
            int x = this.rbip$getBottomTabX(bottomSlot);
            int y = this.rbip$getBottomTabY();
            widget.setRectangle(RBIP_ROTATED_TAB_WIDTH, RBIP_ROTATED_TAB_HEIGHT, x, y);
        } else {
            this.rbip$placeNormalTab(widget, slot);
        }
    }

    @Unique
    private void rbip$placeNormalTab(RecipeBookTabButton widget, int slot) {
        ((RecipeGroupButtonPlacementAccess) widget).rbip$setPlacement(RecipeGroupButtonPlacement.NORMAL);
        int x = this.rbip$getTabX();
        int y = this.rbip$getTabY() + RBIP_TAB_HEIGHT * slot;
        widget.setRectangle(RBIP_TAB_WIDTH, RBIP_TAB_HEIGHT, x, y);
    }

    @Unique
    private void rbip$resetTabPlacement(RecipeBookTabButton widget) {
        ((RecipeGroupButtonPlacementAccess) widget).rbip$setPlacement(RecipeGroupButtonPlacement.NORMAL);
        widget.setSize(RBIP_TAB_WIDTH, RBIP_TAB_HEIGHT);
    }

    @Unique
    private int rbip$getTabX() {
        return this.rbip$getBookX() - 30;
    }

    @Unique
    private int rbip$getTabY() {
        return this.rbip$getBookY() + 3;
    }

    @Unique
    private int rbip$getPageControlX() {
        if (RecipeBookIsPainExtendedConfig.get().extendedFeatures()) {
            return this.rbip$getBookX() - 28;
        }
        return this.rbip$getBookX() + 5;
    }

    @Unique
    private int rbip$getPageControlY() {
        return this.rbip$getBookY() - 12;
    }

    @Unique
    private int rbip$getBookX() {
        return (this.width - RBIP_BOOK_WIDTH) / 2 - this.xOffset;
    }

    @Unique
    private int rbip$getBookY() {
        return (this.height - RBIP_BOOK_HEIGHT) / 2;
    }

    @Unique
    private int rbip$getBottomTabX(int slot) {
        return this.rbip$getHorizontalTabStartX() + slot * RBIP_EXTENDED_SLOT_STEP;
    }

    @Unique
    private int rbip$getBottomTabY() {
        return this.rbip$getBookY() + RBIP_BOOK_HEIGHT - 5;
    }

    @Unique
    private int rbip$getTopTabX(int slot) {
        return this.rbip$getHorizontalTabStartX() + slot * RBIP_EXTENDED_SLOT_STEP;
    }

    @Unique
    private int rbip$getTopTabY() {
        return this.rbip$getBookY() - RBIP_ROTATED_TAB_HEIGHT + 5;
    }

    @Unique
    private int rbip$getHorizontalTabStartX() {
        return this.rbip$getBookX() + (RBIP_BOOK_WIDTH - RBIP_TOP_SLOTS * RBIP_ROTATED_TAB_WIDTH) / 2;
    }

    @Unique
    private void rbip$drawPageControl(GuiGraphicsExtractor context, int x, int y, boolean next, boolean active, int mouseX, int mouseY) {
        int u = next ? 14 : 0;
        if (active && this.rbip$isInside(mouseX, mouseY, x, y, RBIP_PAGE_BUTTON_WIDTH, RBIP_PAGE_BUTTON_HEIGHT)) {
            u += 28;
        }

        int v = active ? 0 : 13;
        context.blit(RenderPipelines.GUI_TEXTURED, RBIP_PAGE_BUTTONS, x, y, u, v, RBIP_PAGE_BUTTON_WIDTH, RBIP_PAGE_BUTTON_HEIGHT, 256, 256);
    }

    @Unique
    private boolean rbip$isInside(int x, int y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    @Unique
    private boolean rbip$isMouseOverAnyVisibleTab(double mouseX, double mouseY) {
        // 固定滚动区域 = 创造模式标签的完整容纳空间（不依赖实际放置的标签）：
        // 左侧 6 槽整列 + 顶部整行 + 底部整行（含 padding 上下扩展）。
        // 只要鼠标落在这些槽位区域内（即便该处没有标签）即可翻页。
        if (this.rbip$isInside(mouseX, mouseY, this.rbip$getTabX(), this.rbip$getTabY(),
                RBIP_TAB_WIDTH, RBIP_LEFT_TOTAL_SLOTS * RBIP_TAB_HEIGHT)) {
            return true;
        }
        int horizX = this.rbip$getHorizontalTabStartX();
        int horizW = RBIP_TOP_SLOTS * RBIP_EXTENDED_SLOT_STEP;
        int horizH = RBIP_ROTATED_TAB_HEIGHT + 2 * RBIP_HORIZONTAL_SCROLL_OUTWARD_PADDING;
        if (this.rbip$isInside(mouseX, mouseY, horizX, this.rbip$getTopTabY() - RBIP_HORIZONTAL_SCROLL_OUTWARD_PADDING,
                horizW, horizH)) {
            return true;
        }
        if (this.rbip$isInside(mouseX, mouseY, horizX, this.rbip$getBottomTabY() - RBIP_HORIZONTAL_SCROLL_OUTWARD_PADDING,
                horizW, horizH)) {
            return true;
        }
        // The turn-page buttons themselves are also a scroll zone.
        int btnW = RBIP_PAGE_BUTTON_WIDTH * 2 + 15;
        return this.rbip$isInside(mouseX, mouseY, this.rbip$pageControlX, this.rbip$pageControlY,
                btnW, RBIP_PAGE_BUTTON_HEIGHT);
    }

    @Unique
    private boolean rbip$isInside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }
}
