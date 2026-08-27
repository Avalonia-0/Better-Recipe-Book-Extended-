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

    /** 是否"全 pin"（副本替代配方组特征）：组内**每个**配方都被 pin。
     *  仅含部分 pin 配方的原组不返回 true——原组按钮不显示 pin 贴图，
     *  避免"整体变成副本组"的观感（pin 贴图只属于真正的副本组）。
     *  1.21.1 版：基于 RecipeHolder.id()（RecipeCollection.getRecipes()）。 */
    public boolean isFullyPinned(RecipeCollection target) {
        if (target == null) return false;
        java.util.List<RecipeHolder<?>> recipes = target.getRecipes();
        if (recipes.isEmpty()) return false;
        for (RecipeHolder<?> recipe : recipes) {
            if (!this.pinned.contains(recipe.id())) return false;
        }
        return true;
    }

    /** 同 {@link #isFullyPinned(RecipeCollection)}，自研配方书集合版。 */
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

    /** Whether a single recipe holder is pinned（同一 key 语义：holder.id()）。 */
    public boolean isPinnedEntry(RecipeHolder<?> holder) {
        return holder != null && this.pinned.contains(holder.id());
    }

    /** 单配方 pin 切换（替代配方组规则：组不能直接 pin，只能在打开
     *  替代配方组后按固定键切换组内单个变体）。 */
    public void toggleFavourite(RecipeHolder<?> holder) {
        if (holder == null) return;
        ResourceLocation id = holder.id();
        if (this.pinned.remove(id)) {
            version++;
            this.store();
            return;
        }
        this.pinned.add(id);
        version++;
        this.store();
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
