package com.alonie.brbe.jei.plugins.loader;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.cache.RecipeViewerIndex;
import com.alonie.brbe.cache.RecipeViewerIndex.WorkstationSpec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Converts the collected {@code recipeType -> items} map into BRBE
 *  {@link WorkstationSpec}s and injects them into the main mod's registry.
 *  Only the seven vanilla JEI recipe types are mapped (their workstation
 *  categories are known); modded recipe types are skipped with a log line and
 *  can be added manually via {@code config/zzzbrbe_workstations.json}. */
public final class WorkstationExporter {

    private WorkstationExporter() {}

    public static void export(Map<Identifier, Set<Identifier>> collected) {
        List<WorkstationSpec> specs = new ArrayList<>();
        for (Map.Entry<Identifier, Set<Identifier>> entry : collected.entrySet()) {
            WorkstationSpec spec = toSpec(entry.getKey(), entry.getValue());
            if (spec != null) specs.add(spec);
        }
        if (!specs.isEmpty()) {
            RecipeViewerIndex.registerExternalWorkstations(specs);
            BetterRecipeBook.LOGGER.info("[BRBE-JEI-Plugins] injected {} workstations from {} JEI plugin recipe types",
                    specs.size(), collected.size());
            for (WorkstationSpec spec : specs) {
                BetterRecipeBook.LOGGER.info("[BRBE-JEI-Plugins]   station {}: family={} prefixes={} items={}",
                        spec.typeId(), spec.family(), spec.categoryPrefixes(), spec.items());
            }
        }
    }

    private static WorkstationSpec toSpec(Identifier uid, Set<Identifier> items) {
        if (!uid.getNamespace().equals("minecraft")) {
            BetterRecipeBook.LOGGER.info("[BRBE-JEI-Plugins] skipping modded recipe type {} (add manually via config/zzzbrbe_workstations.json)", uid);
            return null;
        }
        String family;
        List<String> prefixes;
        switch (uid.getPath()) {
            case "crafting" -> {
                family = "CRAFTING";
                prefixes = List.of("crafting_");
            }
            case "smelting" -> {
                family = "FURNACE";
                prefixes = List.of("furnace_");
            }
            case "blasting" -> {
                family = "FURNACE";
                prefixes = List.of("blast_furnace_");
            }
            case "smoking" -> {
                family = "FURNACE";
                prefixes = List.of("smoker_");
            }
            case "campfire_cooking" -> {
                family = "FURNACE";
                prefixes = List.of("campfire");
            }
            case "stonecutting" -> {
                family = "STONECUTTING";
                prefixes = List.of("stonecutter");
            }
            case "smithing" -> {
                family = "SMITHING";
                prefixes = List.of("smithing");
            }
            default -> {
                BetterRecipeBook.LOGGER.info("[BRBE-JEI-Plugins] skipping unknown recipe type {} (add manually via config/zzzbrbe_workstations.json)", uid);
                return null;
            }
        }
        List<String> itemStrings = items.stream()
                .filter(BuiltInRegistries.ITEM::containsKey)
                .map(Identifier::toString)
                .sorted()
                .toList();
        if (itemStrings.isEmpty()) return null;
        return new WorkstationSpec(family, uid.toString(), prefixes, itemStrings);
    }
}
