package com.alonie.brbe.api;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.util.BRBHelper;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

public class BRBBookSettings {
    public static Map<Identifier, TypeSettings> states = new HashMap<>();

    public BRBBookSettings() {
        states = new HashMap<>();
    }

    public static void registerBook(BRBHelper.Book book) {
        BetterRecipeBook.LOGGER.info("Registering book {}", book.Identifier);
        states.put(book.Identifier, new TypeSettings(false, false));
    }

    public static boolean isOpen(BRBHelper.Book book) {
        TypeSettings settings = states.get(book.Identifier);

        return settings.open;
    }

    public static void setOpen(BRBHelper.Book book, boolean bl) {
        states.get(book.Identifier).open = bl;
    }

    public static boolean isFiltering(BRBHelper.Book book) {
        return states.get(book.Identifier).filtering;
    }

    public static void setFiltering(BRBHelper.Book book, boolean bl) {
        states.get(book.Identifier).filtering = bl;
    }

    public int hashCode() {
        return states.hashCode();
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
