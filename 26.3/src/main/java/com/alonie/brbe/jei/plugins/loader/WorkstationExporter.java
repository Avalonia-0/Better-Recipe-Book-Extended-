package com.alonie.brbe.jei.plugins.loader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 1.21.11 独立项目版：催化剂 id 集合 → 工作站 ItemStack 解析（BRBE 侧
 *  WorkstationSpec 注入逻辑留在主分支——独立项目只做数据提供）。 */
public final class WorkstationExporter {

    private static final Logger LOGGER = LogManager.getLogger("headless-jei");

    private WorkstationExporter() {}

    /** Resolve catalyst item ids into {@link ItemStack}s. */
    public static List<ItemStack> resolveStations(Set<Identifier> itemIds) {
        List<ItemStack> out = new ArrayList<>();
        if (itemIds == null) return out;
        for (Identifier itemId : itemIds) {
            BuiltInRegistries.ITEM.getOptional(itemId)
                    .ifPresent(item -> out.add(new ItemStack(item)));
        }
        return List.copyOf(new LinkedHashSet<>(out));
    }

    /** Debug helper: log the collected catalyst type count. */
    public static void log(Map<Identifier, Set<Identifier>> collected) {
        LOGGER.info("[headless-jei] catalysts: {} types", collected.size());
    }
}
