package com.alonie.brbe.pin;

import com.alonie.brbe.BetterRecipeBook;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * RBIP 配方书标签固定管理器：有序固定列表（pin 顺序）+ 磁盘持久化。
 *
 * <p>固定身份 = 创造模式标签的注册 id（{@link Identifier}，跨会话稳定；RBIP 为每个
 * 创造标签现造的 {@code RecipeBookCategory} 是会话内的，不能持久化）。固定标签排在
 * 配方书首页（搜索标签下），与查询对象的 pin 界面同一目录（{@code zzzbrbe.tabpins.json}，
 * 与 {@code zzzbrbe.pinoverlays.json} / {@code zzzbrbe.pins.json} 并排）。</p>
 *
 * <p>读取同步（启动后首次使用），写入异步（渲染线程不阻塞磁盘 I/O）。</p>
 */
public final class TabPinManager {

    private static final Gson GSON = new Gson();
    private static final Type LIST_TYPE = new TypeToken<ArrayList<String>>() {}.getType();

    private static Path path;
    private static List<String> pinnedIds = new ArrayList<>();
    private static boolean loaded;

    private TabPinManager() {}

    /** Point the manager at the game directory (same level as the query pins).
     *  Called from {@code BetterRecipeBook.init()}; the file is read lazily. */
    public static void init(Path gameDir) {
        path = gameDir.resolve("zzzbrbe.tabpins.json");
        loaded = false;
    }

    private static void ensureLoaded() {
        if (loaded || path == null) return;
        loaded = true;
        try {
            if (Files.exists(path)) {
                String json = Files.readString(path, StandardCharsets.UTF_8);
                List<String> loadedIds = GSON.fromJson(json, LIST_TYPE);
                if (loadedIds != null) {
                    pinnedIds = new ArrayList<>(loadedIds);
                }
            }
        } catch (IOException | RuntimeException e) {
            BetterRecipeBook.LOGGER.warn("[BRBE] Failed to read tab pins file: {}", e.toString());
        }
    }

    /** Whether the creative tab is pinned. */
    public static boolean isPinned(Identifier tabId) {
        ensureLoaded();
        return tabId != null && pinnedIds.contains(tabId.toString());
    }

    /** Toggle the creative tab's pin (new pins append to the end = pin order);
     *  returns the new pinned state. */
    public static boolean toggle(Identifier tabId) {
        ensureLoaded();
        if (tabId == null) return false;
        String key = tabId.toString();
        boolean pinned = pinnedIds.contains(key);
        if (pinned) {
            pinnedIds.remove(key);
        } else {
            pinnedIds.add(key);
        }
        save();
        return !pinned;
    }

    /** Pinned creative-tab ids in pin order. */
    public static List<String> pinnedIds() {
        ensureLoaded();
        return List.copyOf(pinnedIds);
    }

    /** The pinned {@link CreativeModeTab}s in pin order; unknown / unregistered
     *  ids are skipped (mod removed, etc.). */
    public static List<CreativeModeTab> pinnedTabs() {
        ensureLoaded();
        List<CreativeModeTab> out = new ArrayList<>();
        for (String id : pinnedIds) {
            Identifier key = Identifier.tryParse(id);
            if (key == null) continue;
            try {
                BuiltInRegistries.CREATIVE_MODE_TAB.getOptional(key)
                        .ifPresent(out::add);
            } catch (RuntimeException e) {
                // registry not ready — skip
            }
        }
        return out;
    }

    private static void save() {
        if (path == null) return;
        List<String> snapshot = new ArrayList<>(pinnedIds);
        CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(path.getParent());
                Files.writeString(path, GSON.toJson(snapshot), StandardCharsets.UTF_8);
            } catch (IOException e) {
                BetterRecipeBook.LOGGER.warn("[BRBE] Failed to write tab pins file: {}", e.toString());
            }
        });
    }
}
