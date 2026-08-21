package com.alonie.brbe.api;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.util.BRBHelper;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

public class BRBBookSettings {
    private static final Map<Identifier, TypeSettings> states = new HashMap<>();


    public static void registerBook(BRBHelper.Book book) {
        if (book == null) return;
        BetterRecipeBook.LOGGER.info("Registering book {}", book.Identifier);
        states.put(book.Identifier, new TypeSettings(false, false));
    }

    public static boolean isOpen(BRBHelper.Book book) {
        if (book == null) return false;
        TypeSettings settings = states.get(book.Identifier);
        if (settings == null) return false;
        return settings.open;
    }

    public static void setOpen(BRBHelper.Book book, boolean bl) {
        if (book == null) return;
        TypeSettings settings = states.get(book.Identifier);
        if (settings == null) return;
        settings.open = bl;
    }

    public static boolean isFiltering(BRBHelper.Book book) {
        if (book == null) return false;
        TypeSettings settings = states.get(book.Identifier);
        if (settings == null) return false;
        return settings.filtering;
    }

    public static void setFiltering(BRBHelper.Book book, boolean bl) {
        if (book == null) return;
        TypeSettings settings = states.get(book.Identifier);
        if (settings == null) return;
        settings.filtering = bl;
    }


    static class TypeSettings {
        boolean open;
        boolean filtering;

        public TypeSettings(boolean bl, boolean bl2) {
            this.open = bl;
            this.filtering = bl2;
        }
    }
}
