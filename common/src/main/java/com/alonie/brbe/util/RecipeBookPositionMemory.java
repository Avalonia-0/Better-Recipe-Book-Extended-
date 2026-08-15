package com.alonie.brbe.util;

import java.util.HashMap;
import java.util.Map;

/**
 * In-memory memory of where the player left each recipe book: which tab (by
 * its index in the book's tab list) and which page, plus the active search
 * text.  Keyed by the book kind ("crafting" for the vanilla recipe book,
 * "brewing" / "smithing" for the BRBE self-built books).  Not persisted to
 * disk.
 */
public final class RecipeBookPositionMemory {

    private RecipeBookPositionMemory() {}

    public record Pos(int tabIndex, int page, int tabPage, String search) {}

    private static final Map<String, Pos> positions = new HashMap<>();

    public static void save(String book, int tabIndex, int page, int tabPage, String search) {
        if (book == null || tabIndex < 0 || page < 0) return;
        positions.put(book, new Pos(tabIndex, page, tabPage, search != null ? search : ""));
    }

    public static Pos load(String book) {
        return positions.get(book);
    }

    public static void clear(String book) {
        positions.remove(book);
    }
}
