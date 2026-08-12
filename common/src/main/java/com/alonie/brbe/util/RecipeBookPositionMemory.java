package com.alonie.brbe.util;

import java.util.HashMap;
import java.util.Map;

/**
 * In-memory memory of where the player left each recipe book: which tab and
 * which page.  Keyed by the book kind ("crafting" for the vanilla recipe book,
 * "brewing" / "smithing" for the BRBE self-built books).  Not persisted to disk.
 */
public final class RecipeBookPositionMemory {

    private RecipeBookPositionMemory() {}

    public record Pos(String category, int page, int tabPage) {}

    private static final Map<String, Pos> positions = new HashMap<>();

    public static void save(String book, String category, int page) {
        save(book, category, page, -1);
    }

    public static void save(String book, String category, int page, int tabPage) {
        if (book == null || category == null || page < 0) return;
        positions.put(book, new Pos(category, page, tabPage));
    }

    public static Pos load(String book) {
        return positions.get(book);
    }

    public static void clear(String book) {
        positions.remove(book);
    }
}
