package com.alonie.brbe.generic.pins;

import com.alonie.brbe.BetterRecipeBook;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.List;

public final class PinnableRecipeCollection implements Pinnable {
    private final List<Identifier> identifiers;

    private PinnableRecipeCollection(List<Identifier> identifiers) {
        this.identifiers = identifiers;
    }

    public static PinnableRecipeCollection of(RecipeCollection collection) {
        return new PinnableRecipeCollection(collection.getRecipes().stream()
                .map(PinnableRecipeCollection::idFor)
                .toList());
    }

    public Collection<Identifier> identifiers() {
        return this.identifiers;
    }

    @Override
    public boolean has(Identifier identifier) {
        return this.identifiers.contains(identifier);
    }

    private static Identifier idFor(RecipeDisplayEntry entry) {
        String stableKey = entry.category() + "|" + entry.group().orElse(-1) + "|" + entry.display();
        return Identifier.fromNamespaceAndPath(BetterRecipeBook.MOD_ID, "pin/" + sha1Hex(stableKey));
    }

    private static String sha1Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte element : hash) {
                builder.append(Character.forDigit((element >> 4) & 15, 16));
                builder.append(Character.forDigit(element & 15, 16));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Missing SHA-1 support", exception);
        }
    }
}
