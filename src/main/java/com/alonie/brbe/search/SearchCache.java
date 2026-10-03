package com.alonie.brbe.search;

import com.alonie.brbe.util.ModNameUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Extracts and caches search-relevant data from ItemStacks.
 * <p>
 * Created fresh each search pass to avoid stale data or memory leaks.
 * Uses ItemStack.copy() as cache keys for stable hash/equality.
 */
public class SearchCache {

    // namespace → mod display name
    private final Map<String, String> modNameCache = new HashMap<>();

    // item stack (by copy) → set of tag strings
    private final Map<ItemStack, Set<String>> tagsCache = new HashMap<>();

    // item stack (by copy) → concatenated tooltip text
    private final Map<ItemStack, String> tooltipCache = new HashMap<>();

    // item → cached namespace (immutable, so safe to cache permanently)
    private final Map<Item, String> namespaceCache = new HashMap<>();

    private Item.TooltipContext tooltipContext;

    /**
     * 纯文本搜索是否也匹配 **tooltip 全文**（原版语料对齐，用户 2026-09-27 诉求）。
     *
     * <p>原版配方书搜索走 {@code SessionSearchTrees.recipes()}——索引的是每条配方
     * <b>产物物品的全部 tooltip 行</b>，所以只出现在 tooltip 里的文字（例：锻造台纹饰产物
     * 的 {@code ArmorTrim.addToTooltip} 行"海岸盔甲纹饰"）在原版能查到所属的替代配方组，
     * 而 BRBE 先前只匹配物品名 → 查不到。</p>
     *
     * <p>默认关：tooltip 生成有开销，只在语料小的自研书（锻造台 / 酿造台）打开；
     * 命中名字就不建 tooltip（tooltip 按 stack 缓存）。</p>
     */
    private boolean tooltipFallback;

    public void setTooltipFallback(boolean enabled) {
        this.tooltipFallback = enabled;
    }

    public boolean tooltipFallback() {
        return this.tooltipFallback;
    }

    private void ensureTooltipContext() {
        if (tooltipContext == null) {
            var level = Minecraft.getInstance().level;
            if (level != null) {
                tooltipContext = Item.TooltipContext.of(level);
            }
        }
    }

    /**
     * Returns the mod namespace for the given stack's item.
     * e.g., "minecraft", "brbe"
     */
    public String getModNamespace(ItemStack stack) {
        return namespaceCache.computeIfAbsent(stack.getItem(), item ->
                BuiltInRegistries.ITEM.getKey(item).getNamespace());
    }

    /**
     * Returns the human-readable mod name for the given stack's item.
     * e.g., "Minecraft", "Better Recipe Book"
     */
    public String getModName(ItemStack stack) {
        String namespace = getModNamespace(stack);
        return modNameCache.computeIfAbsent(namespace, ModNameUtil::resolveModName);
    }

    /**
     * Returns all tag identifiers for the given stack's item.
     * e.g., ["minecraft:logs", "minecraft:oak_logs"]
     */
    public Set<String> getTags(ItemStack stack) {
        return tagsCache.computeIfAbsent(stack.copy(), key -> {
            Set<String> tags = new HashSet<>();
            key.getItem().builtInRegistryHolder().tags()
                    .map(tagKey -> tagKey.location().toString())
                    .forEach(tags::add);
            return tags;
        });
    }

    /**
     * Returns the concatenated tooltip text for the given stack.
     * Lines are joined with newline. Returns empty string if tooltip cannot be generated.
     */
    public String getTooltipText(ItemStack stack) {
        return tooltipCache.computeIfAbsent(stack.copy(), key -> {
            ensureTooltipContext();
            if (tooltipContext == null) return "";

            var player = Minecraft.getInstance().player;
            if (player == null) return "";

            try {
                List<Component> lines = key.getTooltipLines(tooltipContext, player, TooltipFlag.NORMAL);
                return lines.stream()
                        .map(Component::getString)
                        .collect(Collectors.joining("\n"));
            } catch (Exception e) {
                return "";
            }
        });
    }

    /**
     * Clears all cached data. Called between search passes.
     */
    public void clear() {
        modNameCache.clear();
        tagsCache.clear();
        tooltipCache.clear();
        // namespaceCache is item-based and safe to keep; tooltipContext is session-based
    }
}
