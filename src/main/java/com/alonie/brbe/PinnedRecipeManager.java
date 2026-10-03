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

    /** 清空全部配方固定（{@code /brbe clear recipepin}）。返回清理前的数量。 */
    public int clearAll() {
        int cleared = pinned == null ? 0 : pinned.size();
        if (pinned == null) {
            pinned = new HashSet<>();
        }
        pinned.clear();
        version++;
        store();
        return cleared;
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

    /**
     * 固定 / 取消固定一个**自研配方书集合**（酿造台、锻造台）。
     *
     * <p>⚠️ 必须**整组一起**切换：一个集合可能包含多条配方——锻造台的纹饰组是
     * "每种可纹饰装备一条"、酿造台的一个产物是"同产物的多条酿造路线"。旧实现对每个
     * 已 pin 的 id 只删掉**第一个命中**就 {@code return}：组内其余 id 仍是 pin 状态 →
     * 集合仍被判为"已固定"（{@code has()} → 排序继续把它顶到最前），而且这个"半 pin"
     * 状态会写进 {@code brbe.pins}（用户 2026-09-26 反馈：固定后取消固定，排序不恢复，
     * 且重启后依然如此）。取消时删**组内全部** id；固定时加**组内全部** id（与
     * {@link #addOrRemoveFavourite(PinnableRecipeCollection)} 的 {@code removeIf} 同义）。</p>
     *
     * <p>⚠️ <b>固定键不再走这里</b>（用户 2026-09-27 规则：替代配方组**不能**直接固定，
     * 只能打开组浮层逐个固定变体 → {@link #toggleFavourite(GenericRecipe)}）。</p>
     */
    public <R extends GenericRecipe, M extends AbstractContainerMenu> void addOrRemoveFavourite(GenericRecipeBookCollection<R, M> target) {
        List<Identifier> ids = target.getRecipes().stream().map(R::id).distinct().toList();

        if (ids.stream().anyMatch(this.pinned::contains)) {
            this.pinned.removeAll(ids);
        } else {
            this.pinned.addAll(ids);
        }

        version++;
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
        if (entry == null) {
            return false;
        }
        Identifier id = PinnableRecipeCollection.idFor(entry);
        if (this.pinned.contains(id)) {
            return true;
        }
        // 同产物同形融合条目：pin 状态跟随成员（用户 2026-09-29 定）——成员里有 pin 就算被 pin
        List<Identifier> memberPins = com.alonie.brbe.util.FusedRecipeVariants.memberPins(id);
        if (memberPins != null) {
            for (Identifier memberPin : memberPins) {
                if (this.pinned.contains(memberPin)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 单配方变体 pin 切换（替代配方组规则：组不能直接 pin，只能在打开
     *  替代配方组后按固定键切换组内单个变体；键 = idFor(entry) 的稳定 key）。 */
    public void toggleFavourite(net.minecraft.world.item.crafting.display.RecipeDisplayEntry entry) {
        if (entry == null) return;
        Identifier id = PinnableRecipeCollection.idFor(entry);
        List<Identifier> memberPins = com.alonie.brbe.util.FusedRecipeVariants.memberPins(id);
        if (memberPins != null) {
            // 融合条目：pin 落到**成员键**上（关掉合并功能后 pin 依然有效）；取消时连同一起清掉
            boolean pinned = this.pinned.contains(id);
            for (Identifier memberPin : memberPins) {
                if (this.pinned.contains(memberPin)) {
                    pinned = true;
                    break;
                }
            }
            if (pinned) {
                this.pinned.remove(id);
                this.pinned.removeAll(memberPins);
            } else {
                this.pinned.addAll(memberPins);
            }
            version++;
            this.store();
            return;
        }
        if (this.pinned.remove(id)) {
            version++;
            this.store();
            return;
        }
        this.pinned.add(id);
        version++;
        this.store();
    }

    /**
     * 单条**自研书配方**（BRB 包装对象）的固定切换：键 = {@link GenericRecipe#id()}。
     *
     * <p>与 {@link #toggleFavourite(net.minecraft.world.item.crafting.display.RecipeDisplayEntry)}
     * 同义（替代配方组规则：组不能直接固定，只能打开组浮层后逐个固定变体）。用户 2026-09-27 反馈：
     * 此前固定键在自研书里直接作用在**集合**上 → 组被整体固定，而组内变体固定不了、
     * 还会穿透浮层固定到下层那个组。</p>
     */
    public void toggleFavourite(GenericRecipe recipe) {
        if (recipe == null || recipe.id() == null) return;
        Identifier id = recipe.id();
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
