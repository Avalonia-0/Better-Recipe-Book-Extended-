package com.alonie.brbe;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.alonie.brbe.generic.GenericRecipe;
import com.alonie.brbe.generic.GenericRecipeBookCollection;
import com.alonie.brbe.generic.pins.Pinnable;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.pin.PinStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PinnedRecipeManager {
    public HashSet<ResourceLocation> pinned;

    /** Monotonic version incremented whenever the pin set changes.  Used as
     *  a cheap cache-invalidation signal by the recipe-book pipeline cache
     *  (pins order must be recomputed after any pin change). */
    private int version;

    /** Current pin-set version (see {@link #version}). */
    public int version() {
        return version;
    }

    /** Async pin store — when non-null, reads and writes delegate here. */
    private PinStore store;

    /** Wire an async PinStore.  Called from AppContext after construction. */
    public void setStore(PinStore store) {
        this.store = store;
    }

    public void read() {
        // Prefer async store for loading
        if (store != null) {
            Set<ResourceLocation> loaded = store.load();
            pinned = (loaded instanceof HashSet) ? (HashSet<ResourceLocation>) loaded : new HashSet<>(loaded);
            version++;
            return;
        }

        // Fallback: legacy synchronous read
        Gson gson = new Gson();
        JsonReader reader = null;

        try {
            File pinsFile = new File(Minecraft.getInstance().gameDirectory, BetterRecipeBook.MOD_ID + ".pins");

            if (pinsFile.exists()) {
                reader = new JsonReader(new FileReader(pinsFile.getAbsolutePath()));
                Type type = new TypeToken<HashSet<ResourceLocation>>() {
                }.getType();
                pinned = gson.fromJson(reader, type);
            }
        } catch (Throwable var8) {
            BetterRecipeBook.LOGGER.error(BetterRecipeBook.MOD_ID + ".pins could not be read.");
        } finally {
            if (pinned == null) {
                pinned = new HashSet<>();
            }
            IOUtils.closeQuietly(reader);
        }
    }

    private void store() {
        // Prefer async store — never block the render thread on disk I/O
        if (store != null) {
            store.save(new HashSet<>(pinned));
            return;
        }

        // Fallback: legacy synchronous write
        Gson gson = new Gson();
        OutputStreamWriter writer = null;

        try {
            File pinsFile = new File(Minecraft.getInstance().gameDirectory, BetterRecipeBook.MOD_ID + ".pins");
            writer = new OutputStreamWriter(new FileOutputStream(pinsFile), StandardCharsets.UTF_8);
            writer.write(gson.toJson(this.pinned));
        } catch (Throwable var8) {
            BetterRecipeBook.LOGGER.error(BetterRecipeBook.MOD_ID + ".pins could not be saved.");
        } finally {
            IOUtils.closeQuietly(writer);
        }
    }

    public void addOrRemoveFavourite(RecipeCollection target) {
        for (ResourceLocation identifier : this.pinned) {
            for (RecipeHolder<?> recipe : target.getRecipes()) {
                if (recipe.id().equals(identifier)) {
                    this.pinned.remove(identifier);
                    version++;
                    this.store();
                    return;
                }
            }
        }

        this.pinned.addAll(target.getRecipes().stream().map(RecipeHolder::id).toList());
        version++;
        this.store();
    }

    public <R extends GenericRecipe, M extends AbstractContainerMenu> void addOrRemoveFavourite(GenericRecipeBookCollection<R, M> target) {
        for (ResourceLocation identifier : this.pinned) {
            for (R recipe : target.getRecipes()) {
                if (recipe.id().equals(identifier)) {
                    this.pinned.remove(identifier);
                    version++;
                    this.store();
                    return;
                }
            }
        }

        this.pinned.addAll(target.getRecipes().stream().map(R::id).toList());
        version++;
        this.store();
    }

    public boolean has(Pinnable target) {
        for (ResourceLocation identifier : this.pinned) {
            if (target.has(identifier)) {
                return true;
            }
        }

        return false;
    }

    public static void handlePinRecipe(RecipeBookComponent book, RecipeBookPage page, RecipeHolder<?> recipe) {
        RecipeCollection collection = new RecipeCollection(Minecraft.getInstance().level.registryAccess(), List.of(recipe));
        collection.updateKnownRecipes(page.getRecipeBook());
        BetterRecipeBook.pinnedRecipeManager.addOrRemoveFavourite(collection);
        ((RecipeBookComponentAccessor) book).updateCollectionsInvoker(false);
        if (Minecraft.getInstance().screen instanceof RecipeUpdateListener rul) {
            rul.recipesUpdated();
        }
    }

}
