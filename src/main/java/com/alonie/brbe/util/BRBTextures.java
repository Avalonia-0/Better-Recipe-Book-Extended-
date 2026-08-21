package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.resources.Identifier;

public class BRBTextures {

    private static final String NS = BetterRecipeBook.MOD_ID;

    public static final Identifier RECIPE_BOOK_BACKGROUND_TEXTURE = Identifier.withDefaultNamespace("textures/gui/recipe_book.png");

    public static final Identifier RECIPE_BOOK_BUTTON_SLOT_CRAFTABLE_SPRITE = Identifier.withDefaultNamespace("recipe_book/slot_craftable");
    public static final Identifier RECIPE_BOOK_BUTTON_SLOT_UNCRAFTABLE_SPRITE = Identifier.withDefaultNamespace("recipe_book/slot_uncraftable");
    public static final Identifier RECIPE_BOOK_BUTTON_SLOT_PARTIAL_SPRITE = Identifier.fromNamespaceAndPath(NS, "recipe_book/slot_partial");
    public static final Identifier RECIPE_BOOK_PIN_SPRITE = Identifier.fromNamespaceAndPath(NS, "recipe_book/pin");
    public static final Identifier RECIPE_BOOK_OVERLAY_PIN_SPRITE = Identifier.fromNamespaceAndPath(NS, "recipe_book/overlay_pin");
    
    public static final WidgetSprites RECIPE_BOOK_FILTER_BUTTON_SPRITES = new WidgetSprites(
            Identifier.withDefaultNamespace("recipe_book/filter_enabled"),
            Identifier.withDefaultNamespace("recipe_book/filter_disabled"),
            Identifier.withDefaultNamespace("recipe_book/filter_enabled_highlighted"),
            Identifier.withDefaultNamespace("recipe_book/filter_disabled_highlighted")
    );

    public static final WidgetSprites BREWING_FILTER_BUTTON_SPRITES = new WidgetSprites(
            Identifier.fromNamespaceAndPath(NS, "recipe_book/filter_brewing_enabled"),
            Identifier.fromNamespaceAndPath(NS, "recipe_book/filter_brewing_disabled"),
            Identifier.fromNamespaceAndPath(NS, "recipe_book/filter_brewing_enabled_highlighted"),
            Identifier.fromNamespaceAndPath(NS, "recipe_book/filter_brewing_disabled_highlighted")
    );

    public static final WidgetSprites SMITHING_FILTER_BUTTON_SPRITES = new WidgetSprites(
            Identifier.fromNamespaceAndPath(NS, "recipe_book/filter_smithing_enabled"),
            Identifier.fromNamespaceAndPath(NS, "recipe_book/filter_smithing_disabled"),
            Identifier.fromNamespaceAndPath(NS, "recipe_book/filter_smithing_enabled_highlighted"),
            Identifier.fromNamespaceAndPath(NS, "recipe_book/filter_smithing_disabled_highlighted")
    );

    /** Pick the filter sprites for a recipe book type (brewing/smithing; fallback = vanilla). */
    public static WidgetSprites filterButtonFor(BRBHelper.Book book) {
        if (book == BetterRecipeBook.BREWING) return BREWING_FILTER_BUTTON_SPRITES;
        if (book == BetterRecipeBook.SMITHING) return SMITHING_FILTER_BUTTON_SPRITES;
        return RECIPE_BOOK_FILTER_BUTTON_SPRITES;
    }

    public static final WidgetSprites RECIPE_BOOK_PAGE_FORWARD_SPRITES = new WidgetSprites(
            Identifier.withDefaultNamespace("recipe_book/page_forward"),
            Identifier.withDefaultNamespace("recipe_book/page_forward_highlighted")
    );

    public static final WidgetSprites RECIPE_BOOK_PAGE_BACKWARD_SPRITES = new WidgetSprites(
            Identifier.withDefaultNamespace("recipe_book/page_backward"),
            Identifier.withDefaultNamespace("recipe_book/page_backward_highlighted")
    );

    public static final WidgetSprites RECIPE_BOOK_BUTTON_SPRITES = new WidgetSprites(
            Identifier.withDefaultNamespace("recipe_book/button"),
            Identifier.withDefaultNamespace("recipe_book/button_highlighted")
    );

    public static final WidgetSprites RECIPE_BOOK_TAB_SPRITES = new WidgetSprites(
            Identifier.withDefaultNamespace("recipe_book/tab"),
            Identifier.withDefaultNamespace("recipe_book/tab_selected")
    );

    public static WidgetSprites RECIPE_BOOK_CRAFTING_OVERLAY_SPRITE = new WidgetSprites(
            Identifier.fromNamespaceAndPath(NS, "recipe_book/crafting_overlay"),
            Identifier.fromNamespaceAndPath(NS, "recipe_book/crafting_overlay_disabled"),
            Identifier.fromNamespaceAndPath(NS, "recipe_book/crafting_overlay_highlighted"),
            Identifier.fromNamespaceAndPath(NS, "recipe_book/crafting_overlay_disabled_highlighted")
    );

    public static WidgetSprites RECIPE_BOOK_PLAIN_OVERLAY_SPRITE = new WidgetSprites(
            Identifier.fromNamespaceAndPath(NS, "recipe_book/plain_overlay"),
            Identifier.fromNamespaceAndPath(NS, "recipe_book/plain_overlay_disabled"),
            Identifier.fromNamespaceAndPath(NS, "recipe_book/plain_overlay_highlighted"),
            Identifier.fromNamespaceAndPath(NS, "recipe_book/plain_overlay_disabled_highlighted")
    );

    public static final WidgetSprites SETTINGS_BUTTON_SPRITES = new WidgetSprites(
            Identifier.fromNamespaceAndPath(NS, "recipe_book/button_settings"),
            Identifier.fromNamespaceAndPath(NS, "recipe_book/button_settings_highlighted")
    );

    public static final Identifier FURNACE_FIRE_SPRITE =
            Identifier.fromNamespaceAndPath(NS, "recipe_book/furnace_fire");

    public static final WidgetSprites RECIPE_BOOK_INSTANT_CRAFT_BUTTON_SPRITES = new WidgetSprites(
            Identifier.fromNamespaceAndPath(NS, "recipe_book/button_instantcraft"),
            Identifier.fromNamespaceAndPath(NS, "recipe_book/button_instantcraft_disabled"),
            Identifier.fromNamespaceAndPath(NS, "recipe_book/button_instantcraft_highlighted"),
            Identifier.fromNamespaceAndPath(NS, "recipe_book/button_instantcraft_disabled_highlighted")
    );

}
