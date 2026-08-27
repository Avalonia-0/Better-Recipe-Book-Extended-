package com.alonie.brbe.jei.plugins.loader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 1.21.1 版：催化剂 → 引擎工作站物品（1.21.11 的 export 走 RBIP 工作站表，
 *  1.21.1 无此系统——此处直接把类型→工作站物品清单交给调用方）。 */
public final class WorkstationExporter {

    private static final Logger LOGGER = LogManager.getLogger("headless-jei");

    private WorkstationExporter() {}

    /** Resolve catalyst item ids into {@link ItemStack}s. */
    public static List<ItemStack> resolveStations(ResourceLocation uid,
                                                  Set<ResourceLocation> itemIds) {
        List<ItemStack> out = new ArrayList<>();
        if (itemIds == null) return out;
        for (ResourceLocation itemId : itemIds) {
            BuiltInRegistries.ITEM.getOptional(itemId)
                    .ifPresent(item -> out.add(new ItemStack(item)));
        }
        return List.copyOf(new LinkedHashSet<>(out));
    }

    /** Debug helper: log the collected catalyst map. */
    public static void log(Map<ResourceLocation, Set<ResourceLocation>> collected) {
        LOGGER.info("[BRBE-JEI-Plugins] catalysts: {} types", collected.size());
    }
}
