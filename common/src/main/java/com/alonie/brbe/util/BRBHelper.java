package com.alonie.brbe.util;

import com.mojang.datafixers.util.Pair;
import com.alonie.brbe.api.BRBBookCategories;
import com.alonie.brbe.api.BRBBookSettings;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public class BRBHelper {
    public static Book createBook(String mod_id, String name) {
        Identifier location = Identifier.fromNamespaceAndPath(mod_id, name);

        String hash = location + "#";
        Pair<String, String> pair = new Pair<>(hash + "isGuiOpen", hash + "isFiltering");

        Book book = new Book(
                location,
                pair
        );

        BRBBookSettings.registerBook(book);

        return book;
    }

    static public class Book {
        public Identifier Identifier;
        public Pair<String, String> pair;

        Book(Identifier Identifier, Pair<String, String> pair) {
            this.Identifier = Identifier;
            this.pair = pair;
        }

        public BRBBookCategories.Category createCategory(ItemStack... entries) {
            return BRBBookCategories.createCategory(this, entries);
        }

        public BRBBookCategories.Category createSearch() {
            return BRBBookCategories.createSearch(this);
        }
    }
}
