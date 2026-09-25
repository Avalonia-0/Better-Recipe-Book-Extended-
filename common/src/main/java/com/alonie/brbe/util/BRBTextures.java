package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.resources.ResourceLocation;

public class BRBTextures {

    private static final String NS = BetterRecipeBook.MOD_ID;

    /** True if the red-check sprite is present (provided by the compat pack). */
    public static boolean hasPartialSprite() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getResourceManager() == null) return false;
        ResourceLocation fileId = ResourceLocation.fromNamespaceAndPath(
                RECIPE_BOOK_BUTTON_SLOT_PARTIAL_SPRITE.getNamespace(),
                "textures/gui/sprites/" + RECIPE_BOOK_BUTTON_SLOT_PARTIAL_SPRITE.getPath() + ".png");
        return minecraft.getResourceManager().getResource(fileId).isPresent();
    }

    public static final ResourceLocation RECIPE_BOOK_BACKGROUND_TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/recipe_book.png");

    public static final ResourceLocation RECIPE_BOOK_BUTTON_SLOT_CRAFTABLE_SPRITE = ResourceLocation.withDefaultNamespace("recipe_book/slot_craftable");
    public static final ResourceLocation RECIPE_BOOK_BUTTON_SLOT_UNCRAFTABLE_SPRITE = ResourceLocation.withDefaultNamespace("recipe_book/slot_uncraftable");
    public static final ResourceLocation RECIPE_BOOK_BUTTON_SLOT_PARTIAL_SPRITE = ResourceLocation.fromNamespaceAndPath(NS, "recipe_book/slot_partial");
    public static final ResourceLocation RECIPE_BOOK_PIN_SPRITE = ResourceLocation.fromNamespaceAndPath(NS, "recipe_book/pin");
    public static final ResourceLocation FURNACE_FIRE_SPRITE = ResourceLocation.fromNamespaceAndPath(NS, "recipe_book/furnace_fire");
    public static final ResourceLocation RECIPE_BOOK_OVERLAY_PIN_SPRITE = ResourceLocation.fromNamespaceAndPath(NS, "recipe_book/overlay_pin");
    
    public static final WidgetSprites RECIPE_BOOK_FILTER_BUTTON_SPRITES = new WidgetSprites(
            ResourceLocation.withDefaultNamespace("recipe_book/filter_enabled"),
            ResourceLocation.withDefaultNamespace("recipe_book/filter_disabled"),
            ResourceLocation.withDefaultNamespace("recipe_book/filter_enabled_highlighted"),
            ResourceLocation.withDefaultNamespace("recipe_book/filter_disabled_highlighted")
    );

    public static final WidgetSprites BREWING_FILTER_BUTTON_SPRITES = new WidgetSprites(
            ResourceLocation.fromNamespaceAndPath(NS, "recipe_book/filter_brewing_enabled"),
            ResourceLocation.fromNamespaceAndPath(NS, "recipe_book/filter_brewing_disabled"),
            ResourceLocation.fromNamespaceAndPath(NS, "recipe_book/filter_brewing_enabled_highlighted"),
            ResourceLocation.fromNamespaceAndPath(NS, "recipe_book/filter_brewing_disabled_highlighted")
    );

    public static final WidgetSprites SMITHING_FILTER_BUTTON_SPRITES = new WidgetSprites(
            ResourceLocation.fromNamespaceAndPath(NS, "recipe_book/filter_smithing_enabled"),
            ResourceLocation.fromNamespaceAndPath(NS, "recipe_book/filter_smithing_disabled"),
            ResourceLocation.fromNamespaceAndPath(NS, "recipe_book/filter_smithing_enabled_highlighted"),
            ResourceLocation.fromNamespaceAndPath(NS, "recipe_book/filter_smithing_disabled_highlighted")
    );

    /** Pick the filter sprites for a recipe book type (brewing/smithing; fallback = vanilla). */
    public static WidgetSprites filterButtonFor(BRBHelper.Book book) {
        if (book == BetterRecipeBook.BREWING) return BREWING_FILTER_BUTTON_SPRITES;
        if (book == BetterRecipeBook.SMITHING) return SMITHING_FILTER_BUTTON_SPRITES;
        return RECIPE_BOOK_FILTER_BUTTON_SPRITES;
    }

    public static final WidgetSprites RECIPE_BOOK_PAGE_FORWARD_SPRITES = new WidgetSprites(
            ResourceLocation.withDefaultNamespace("recipe_book/page_forward"),
            ResourceLocation.withDefaultNamespace("recipe_book/page_forward_highlighted")
    );

    public static final WidgetSprites RECIPE_BOOK_PAGE_BACKWARD_SPRITES = new WidgetSprites(
            ResourceLocation.withDefaultNamespace("recipe_book/page_backward"),
            ResourceLocation.withDefaultNamespace("recipe_book/page_backward_highlighted")
    );

    public static final WidgetSprites RECIPE_BOOK_BUTTON_SPRITES = new WidgetSprites(
            ResourceLocation.withDefaultNamespace("recipe_book/button"),
            ResourceLocation.withDefaultNamespace("recipe_book/button_highlighted")
    );

    public static final WidgetSprites RECIPE_BOOK_TAB_SPRITES = new WidgetSprites(
            ResourceLocation.withDefaultNamespace("recipe_book/tab"),
            ResourceLocation.withDefaultNamespace("recipe_book/tab_selected")
    );

    public static WidgetSprites RECIPE_BOOK_CRAFTING_OVERLAY_SPRITE = new WidgetSprites(
            ResourceLocation.fromNamespaceAndPath(NS, "recipe_book/crafting_overlay"),
            ResourceLocation.fromNamespaceAndPath(NS, "recipe_book/crafting_overlay_disabled"),
            ResourceLocation.fromNamespaceAndPath(NS, "recipe_book/crafting_overlay_highlighted"),
            ResourceLocation.fromNamespaceAndPath(NS, "recipe_book/crafting_overlay_disabled_highlighted")
    );

    public static WidgetSprites RECIPE_BOOK_PLAIN_OVERLAY_SPRITE = new WidgetSprites(
            ResourceLocation.fromNamespaceAndPath(NS, "recipe_book/plain_overlay"),
            ResourceLocation.fromNamespaceAndPath(NS, "recipe_book/plain_overlay_disabled"),
            ResourceLocation.fromNamespaceAndPath(NS, "recipe_book/plain_overlay_highlighted"),
            ResourceLocation.fromNamespaceAndPath(NS, "recipe_book/plain_overlay_disabled_highlighted")
    );

    // ── 原版替代配方按钮纹理（用户 2026-09-25 诉求 2）───────────────────────────
    // 配方书「替代配方组」浮层的按钮**纹理跟随内容**：显示完整配方预览时用原版纹理面
    // （悬停 → *_highlighted；可合成/残缺 → 启用面，不可合成 → 禁用面），只画产物图标
    // （开启「仅在悬停时显示替代配方」且未悬停）时才用 BRBE 自有 crafting_overlay 面。
    public static final WidgetSprites VANILLA_CRAFTING_OVERLAY_SPRITE = new WidgetSprites(
            ResourceLocation.withDefaultNamespace("recipe_book/crafting_overlay"),
            ResourceLocation.withDefaultNamespace("recipe_book/crafting_overlay_disabled"),
            ResourceLocation.withDefaultNamespace("recipe_book/crafting_overlay_highlighted"),
            ResourceLocation.withDefaultNamespace("recipe_book/crafting_overlay_disabled_highlighted")
    );

    public static final WidgetSprites VANILLA_FURNACE_OVERLAY_SPRITE = new WidgetSprites(
            ResourceLocation.withDefaultNamespace("recipe_book/furnace_overlay"),
            ResourceLocation.withDefaultNamespace("recipe_book/furnace_overlay_disabled"),
            ResourceLocation.withDefaultNamespace("recipe_book/furnace_overlay_highlighted"),
            ResourceLocation.withDefaultNamespace("recipe_book/furnace_overlay_disabled_highlighted")
    );

    public static final WidgetSprites SETTINGS_BUTTON_SPRITES = new WidgetSprites(
            ResourceLocation.fromNamespaceAndPath(NS, "recipe_book/button_settings"),
            ResourceLocation.fromNamespaceAndPath(NS, "recipe_book/button_settings_highlighted")
    );

    public static final WidgetSprites RECIPE_BOOK_EXPAND_BUTTON_SPRITES = new WidgetSprites(
            ResourceLocation.fromNamespaceAndPath(NS, "recipe_book/button_expand"),
            ResourceLocation.fromNamespaceAndPath(NS, "recipe_book/button_expand_disabled"),
            ResourceLocation.fromNamespaceAndPath(NS, "recipe_book/button_expand_highlighted"),
            ResourceLocation.fromNamespaceAndPath(NS, "recipe_book/button_expand_disabled_highlighted")
    );

    public static final WidgetSprites RECIPE_BOOK_INSTANT_CRAFT_BUTTON_SPRITES = new WidgetSprites(
            ResourceLocation.fromNamespaceAndPath(NS, "recipe_book/button_instantcraft"),
            ResourceLocation.fromNamespaceAndPath(NS, "recipe_book/button_instantcraft_disabled"),
            ResourceLocation.fromNamespaceAndPath(NS, "recipe_book/button_instantcraft_highlighted"),
            ResourceLocation.fromNamespaceAndPath(NS, "recipe_book/button_instantcraft_disabled_highlighted")
    );

}
