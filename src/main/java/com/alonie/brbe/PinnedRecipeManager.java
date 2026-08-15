package com.alonie.brbe;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.alonie.brbe.generic.GenericRecipe;
import com.alonie.brbe.generic.GenericRecipeBookCollection;
import com.alonie.brbe.generic.pins.Pinnable;
import com.alonie.brbe.generic.pins.PinnableRecipeCollection;
import com.alonie.brbe.pin.PinStore;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public class PinnedRecipeManager {
    public HashSet<Identifier> pinned;

    /** Async pin store — when non-null, reads and writes delegate here. */
    private PinStore store;

    /** Wire an async PinStore.  Called from BetterRecipeBook.init(). */
    public void setStore(PinStore store) {
        this.store = store;
    }

    public void read() {
        // Prefer async store for loading
        if (store != null) {
            Set<Identifier> loaded = store.load();
            pinned = (loaded instanceof HashSet) ? (HashSet<Identifier>) loaded : new HashSet<>(loaded);
            return;
        }

        // Fallback: legacy synchronous read
        Gson gson = new Gson();
        JsonReader reader = null;

        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.gameDirectory == null) return;
            File pinsFile = new File(mc.gameDirectory, BetterRecipeBook.MOD_ID + ".pins");

            if (pinsFile.exists()) {
                reader = new JsonReader(new FileReader(pinsFile.getAbsolutePath()));
                Type type = new TypeToken<HashSet<Identifier>>() {
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
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.gameDirectory == null) return;
            File pinsFile = new File(mc.gameDirectory, BetterRecipeBook.MOD_ID + ".pins");
            writer = new OutputStreamWriter(new FileOutputStream(pinsFile), StandardCharsets.UTF_8);
            writer.write(gson.toJson(this.pinned));
        } catch (Throwable var8) {
            BetterRecipeBook.LOGGER.error(BetterRecipeBook.MOD_ID + ".pins could not be saved.");
        } finally {
            IOUtils.closeQuietly(writer);
        }
    }

    public <R extends GenericRecipe, M extends AbstractContainerMenu> void addOrRemoveFavourite(GenericRecipeBookCollection<R, M> target) {
        for (Identifier identifier : this.pinned) {
            for (R recipe : target.getRecipes()) {
                if (recipe.id().equals(identifier)) {
                    this.pinned.remove(identifier);
                    this.store();
                    return;
                }
            }
        }

        this.pinned.addAll(target.getRecipes().stream().map(R::id).toList());
        this.store();
    }

    public void addOrRemoveFavourite(PinnableRecipeCollection target) {
        if (this.pinned.removeIf(target::has)) {
            this.store();
            return;
        }

        this.pinned.addAll(target.identifiers());
        this.store();
    }

    public boolean has(Pinnable target) {
        for (Identifier identifier : this.pinned) {
            if (target.has(identifier)) {
                return true;
            }
        }

        return false;
    }
}
