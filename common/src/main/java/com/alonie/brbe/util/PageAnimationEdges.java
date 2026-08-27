package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedReader;
import java.util.Optional;

/**
 * 翻页动画"左右边界宽度"的资源包配置源。
 *
 * <p>值存于 {@code assets/zzzbrbe/animation/edge_width.json}：本体默认左2右3，
 * unique dark 兼容包（内置资源包 {@code zzzbrbe_unique_dark}）以同路径文件覆盖为左0右0。
 * 每次调用重新读取：26.2 的 {@code Minecraft.resourceManager} 是 final 字段，
 * 资源包热重载不替换实例，按实例缓存会读到旧值。</p>
 */
public final class PageAnimationEdges {

    private static final ResourceLocation EDGE_WIDTH_ID =
            ResourceLocation.fromNamespaceAndPath("zzzbrbe", "animation/edge_width.json");
    private static final int DEFAULT_LEFT = 2;
    private static final int DEFAULT_RIGHT = 3;

    private static int lastLoggedLeft = Integer.MIN_VALUE;
    private static int lastLoggedRight = Integer.MIN_VALUE;
    private static String lastLoggedSource = "";

    private PageAnimationEdges() {
    }

    public static int left() {
        return read().left();
    }

    public static int right() {
        return read().right();
    }

    private static Edges read() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getResourceManager() == null) {
            return new Edges(DEFAULT_LEFT, DEFAULT_RIGHT);
        }
        ResourceManager manager = minecraft.getResourceManager();
        Optional<Resource> resource = manager.getResource(EDGE_WIDTH_ID);
        if (resource.isEmpty()) {
            logChange(DEFAULT_LEFT, DEFAULT_RIGHT, "<missing>");
            return new Edges(DEFAULT_LEFT, DEFAULT_RIGHT);
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
            int l = obj.has("left") ? Math.max(0, obj.get("left").getAsInt()) : DEFAULT_LEFT;
            int r = obj.has("right") ? Math.max(0, obj.get("right").getAsInt()) : DEFAULT_RIGHT;
            logChange(l, r, resource.get().sourcePackId());
            return new Edges(l, r);
        } catch (Exception e) {
            logChange(DEFAULT_LEFT, DEFAULT_RIGHT, "<error>");
            return new Edges(DEFAULT_LEFT, DEFAULT_RIGHT);
        }
    }

    private static void logChange(int l, int r, String source) {
        if (l == lastLoggedLeft && r == lastLoggedRight && source.equals(lastLoggedSource)) return;
        lastLoggedLeft = l;
        lastLoggedRight = r;
        lastLoggedSource = source;
        BetterRecipeBook.LOGGER.info("[BRBE-EDGE] {} left={} right={} source={}", EDGE_WIDTH_ID, l, r, source);
    }

    private record Edges(int left, int right) {
    }
}
