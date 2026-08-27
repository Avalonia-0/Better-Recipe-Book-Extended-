package com.alonie.brbe;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.alonie.brbe.generic.GenericRecipe;
import com.alonie.brbe.generic.GenericRecipeBookCollection;
import com.alonie.brbe.generic.pins.Pinnable;
import com.alonie.brbe.generic.pins.PinnableRecipeCollection;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.apache.commons.io.IOUtils;

import com.alonie.brbe.pin.PinStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;

public class PinnedRecipeManager {
    public HashSet<Identifier> pinned;

    /** Monotonic version incremented whenever the pin set changes.  Used as
     *  a cheap cache-invalidation signal by the recipe-book pipeline cache
     *  (pins order must be recomputed after any pin change). */
    private int version;

    /** Current pin-set version (see {@link #version}). */
    public int version() {
        return version;
    }

    private PinStore store;

    public void setStore(PinStore store) {
        this.store = store;
    }

    public void read() {
        // Prefer async PinStore when available
        if (store != null) {
            pinned = new HashSet<>(store.load());
            version++;
            return;
        }

        // Fallback legacy synchronous read
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
        // Prefer async PinStore (non-blocking) when available
        if (store != null) {
            store.save(new HashSet<>(pinned));
            return;
        }

        // Fallback legacy synchronous write
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
            version++;
            this.store();
            return;
        }

        this.pinned.addAll(target.identifiers());
        version++;
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

    /** 是否"全 pin 组"（副本替代配方组特征）：组内**每个**配方都被 pin。
     *  仅含部分 pin 配方的原组不返回 true——原组按钮不显示 pin 贴图，
     *  避免"整体变成副本组"的观感（pin 贴图只属于真正的副本组）。 */
    public boolean isFullyPinned(PinnableRecipeCollection target) {
        if (target == null) return false;
        java.util.Collection<Identifier> ids = target.identifiers();
        if (ids.isEmpty()) return false;
        for (Identifier id : ids) {
            if (!this.pinned.contains(id)) return false;
        }
        return true;
    }

    /** 同 {@link #isFullyPinned(PinnableRecipeCollection)}，自研配方书集合版。 */
    public <R extends GenericRecipe, M extends AbstractContainerMenu> boolean isFullyPinned(
            GenericRecipeBookCollection<R, M> target) {
        if (target == null) return false;
        List<R> recipes = target.getRecipes();
        if (recipes.isEmpty()) return false;
        for (R recipe : recipes) {
            if (!this.pinned.contains(recipe.id())) return false;
        }
        return true;
    }

    /** Whether a query-viewer entry is pinned (same stable id derivation as the
     *  recipe book — {@link PinnableRecipeCollection#idFor}).  Lets the query
     *  viewer pin-mark and sort-forward the recipe book's pinned recipes. */
    public boolean isPinnedEntry(net.minecraft.world.item.crafting.display.RecipeDisplayEntry entry) {
        return entry != null && this.pinned.contains(PinnableRecipeCollection.idFor(entry));
    }

    /** 单配方变体 pin 切换（替代配方组规则：组不能直接 pin，只能在打开
     *  替代配方组后按固定键切换组内单个变体；键 = idFor(entry) 的稳定 key）。 */
    public void toggleFavourite(net.minecraft.world.item.crafting.display.RecipeDisplayEntry entry) {
        if (entry == null) return;
        Identifier id = PinnableRecipeCollection.idFor(entry);
        if (this.pinned.remove(id)) {
            version++;
            this.store();
            return;
        }
        this.pinned.add(id);
        version++;
        this.store();
    }
}
