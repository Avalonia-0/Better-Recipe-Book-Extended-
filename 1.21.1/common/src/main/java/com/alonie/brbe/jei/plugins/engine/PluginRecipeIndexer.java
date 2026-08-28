package com.alonie.brbe.jei.plugins.engine;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.alonie.brbe.jei.plugins.engine.DataOnlyLayoutBuilder;
import com.alonie.brbe.jei.plugins.engine.EmptyFocusGroup;
import com.alonie.brbe.jei.plugins.engine.SlotData;
import com.alonie.brbe.jei.api.JeiRecipeRegistry;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.vanilla.IJeiAnvilRecipe;
import mezz.jei.api.recipe.vanilla.IJeiBrewingRecipe;
import mezz.jei.api.recipe.vanilla.IJeiCompostingRecipe;
import mezz.jei.api.recipe.vanilla.IJeiFuelingRecipe;
import mezz.jei.api.recipe.vanilla.IJeiGrindstoneRecipe;
import mezz.jei.api.recipe.vanilla.IJeiIngredientInfoRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 1.21.1 版插件配方索引器：把 JEI 插件采集的（类别 x 配方 x 催化剂）数据
 * 转成查询引擎可查的 {@link JeiRecipeRegistry.Entry}（物品输入/输出 + 原始
 * JEI 配方对象）。对应 1.21.11 的 PluginRecipeIndexer——1.21.1 无
 * RecipeDisplayEntry，条目直接以 (typeUid, recipe) 进引擎，弹窗渲染走
 * IRecipeManager#createRecipeLayoutDrawable。
 *
 * <p>数据来源：
 * <ul>
 *   <li>mod 插件（registerRecipes 采集）→ {@link #indexModData}</li>
 *   <li>原版 JEI 类型（anvil/brewing/grindstone，来自 JEI 运行时的
 *       VanillaPlugin 数据）→ {@link #indexVanillaRuntimeTypes}（需要嵌入式
 *       无头核心已启动；否则类别保持空）</li>
 * </ul>
 * 条目提取：JEI 原生的 anvil/grindstone/brewing/compost/fuel/info 配方走接口
 * 访问器；其他（mod）配方把类别 {@code setRecipe} 跑一遍
 * {@link DataOnlyLayoutBuilder} 记录槽位（仅数据，不渲染）。</p>
 */
public final class PluginRecipeIndexer {

    private static final Logger LOGGER = LogManager.getLogger("headless-jei");

    private PluginRecipeIndexer() {}

    /** No-recipe-book vanilla JEI workstation types indexed from the JEI
     *  runtime (anvil/brewing/grindstone). */
    private static final List<String> VANILLA_PLUGIN_TYPES =
            List.of("minecraft:anvil", "minecraft:brewing", "minecraft:grindstone");

    private static final Map<ResourceLocation, IRecipeCategory<?>> CATEGORIES = new HashMap<>();
    /** uid → JEI 类型（渲染委托用：createRecipeLayoutDrawable 需要 manager 的
     *  真实类别实例——收集器实例的 background/arrow 来自 stub，绘制为空）。 */
    private static final Map<ResourceLocation, RecipeType<?>> UID_TO_TYPE = new HashMap<>();

    /** Rebuild mod-plugin entries and register them into the query engine. */
    public static synchronized void indexModData(
            List<IRecipeCategory<?>> categories,
            Map<RecipeType<?>, List<Object>> recipesByType,
            Map<ResourceLocation, Set<ResourceLocation>> catalysts) {
        CATEGORIES.clear();
        Map<ResourceLocation, List<JeiRecipeRegistry.Entry>> entries = new HashMap<>();
        Map<ResourceLocation, List<ItemStack>> stations = new HashMap<>();

        if (categories != null) {
            for (IRecipeCategory<?> category : categories) {
                if (category == null) continue;
                RecipeType<?> type = category.getRecipeType();
                if (type != null && type.getUid() != null) {
                    CATEGORIES.put(type.getUid(), category);
                    UID_TO_TYPE.put(type.getUid(), type);
                }
            }
        }

        if (recipesByType != null) {
            for (Map.Entry<RecipeType<?>, List<Object>> entry : recipesByType.entrySet()) {
                RecipeType<?> type = entry.getKey();
                if (type == null || type.getUid() == null) continue;
                ResourceLocation uid = type.getUid();
                if (SKIP_VANILLA.contains(uid.toString())) continue;
                IRecipeCategory<?> category = CATEGORIES.get(uid);
                for (Object recipe : entry.getValue()) {
                    if (recipe == null) continue;
                    JeiRecipeRegistry.Entry jeiEntry = buildEntry(uid, category, recipe);
                    if (jeiEntry == null) continue;
                    entries.computeIfAbsent(uid, k -> new ArrayList<>()).add(jeiEntry);
                }
            }
        }

        if (catalysts != null) {
            for (Map.Entry<ResourceLocation, Set<ResourceLocation>> e : catalysts.entrySet()) {
                if (e.getKey() == null || SKIP_VANILLA.contains(e.getKey().toString())) continue;
                List<ItemStack> stacks = resolveStations(e.getValue());
                if (!stacks.isEmpty()) {
                    stations.put(e.getKey(), stacks);
                }
            }
        }
        for (ResourceLocation uid : entries.keySet()) {
            stations.computeIfAbsent(uid, k -> List.of());
        }

        register(entries, stations, "mod plugins");
    }

    /** Index the vanilla JEI plugin types (anvil / brewing / grindstone) from
     *  the running JEI runtime (embedded headless core or real JEI).  Types
     *  whose runtime is absent stay empty (categories degrade to info-only). */
    public static synchronized void indexVanillaRuntimeTypes() {
        IRecipeManager manager = JeiRuntimeBridge.recipeManager();
        if (manager == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Map<ResourceLocation, List<JeiRecipeRegistry.Entry>> entries = new HashMap<>();
        Map<ResourceLocation, List<ItemStack>> stations = new HashMap<>();

        Map<ResourceLocation, IRecipeCategory<?>> categoriesByUid = new HashMap<>();
        for (IRecipeCategory<?> category : manager.createRecipeCategoryLookup().get().toList()) {
            RecipeType<?> type = category.getRecipeType();
            if (type != null && type.getUid() != null) {
                categoriesByUid.put(type.getUid(), category);
                CATEGORIES.put(type.getUid(), category);
                UID_TO_TYPE.put(type.getUid(), type);
            }
        }
        for (String uid : VANILLA_PLUGIN_TYPES) {
            try {
                IRecipeCategory<?> category = categoriesByUid.get(
                        ResourceLocation.parse(uid));
                if (category == null) continue;
                List<?> recipes;
                try {
                    recipes = manager.createRecipeLookup(category.getRecipeType()).get().toList();
                } catch (Exception | LinkageError e) {
                    LOGGER.warn("[BRBE-JEI-Plugins] vanilla {} recipe lookup failed: {}",
                            uid, e.toString());
                    continue;
                }
                if (recipes.isEmpty()) continue;
                List<JeiRecipeRegistry.Entry> indexed = new ArrayList<>();
                Set<String> seen = new HashSet<>();
                for (Object recipe : recipes) {
                    JeiRecipeRegistry.Entry entry = buildEntry(
                            ResourceLocation.parse(uid), category, recipe);
                    if (entry == null) continue;
                    String fingerprint = fingerprint(entry);
                    if (!seen.add(fingerprint)) continue;
                    indexed.add(entry);
                }
                if (indexed.isEmpty()) continue;
                entries.put(ResourceLocation.parse(uid), indexed);
                stations.put(ResourceLocation.parse(uid), vanillaStationsFor(uid));
                LOGGER.info("[BRBE-JEI-Plugins] vanilla runtime type {}: {} recipes indexed",
                        uid, indexed.size());
            } catch (Exception | LinkageError e) {
                LOGGER.warn("[BRBE-JEI-Plugins] vanilla runtime type {} pass failed: {}",
                        uid, e.toString());
            }
        }
        register(entries, stations, "vanilla runtime");
    }

    /** Replace the bridge registry with {@code entries}. */
    private static void register(Map<ResourceLocation, List<JeiRecipeRegistry.Entry>> entries,
                                 Map<ResourceLocation, List<ItemStack>> stations,
                                 String source) {
        Map<ResourceLocation, String> titles = new HashMap<>();
        for (ResourceLocation uid : entries.keySet()) {
            IRecipeCategory<?> category = CATEGORIES.get(uid);
            if (category != null) {
                try {
                    titles.put(uid, category.getTitle().getString());
                } catch (Exception | LinkageError ignored) {
                }
            }
        }
        JeiRecipeRegistry.putAll(entries, stations, titles);
        com.alonie.brbe.jei.api.JeiPopupRenderer.invalidate();
        if (!entries.isEmpty()) {
            LOGGER.info("[BRBE-JEI-Plugins] indexed {} JEI types ({} entries, {})",
                    entries.size(), entries.values().stream().mapToInt(List::size).sum(), source);
        }
    }

    /** The category for a JEI type uid (popup rendering).  Prefers the JEI
     *  manager's real registered instance (its background/arrow drawables are
     *  real); the collected instance (stub drawables) is only a fallback. */
    public static synchronized IRecipeCategory<?> categoryFor(ResourceLocation typeUid) {
        RecipeType<?> type = UID_TO_TYPE.get(typeUid);
        if (type != null) {
            IRecipeManager manager = JeiRuntimeBridge.recipeManager();
            if (manager != null) {
                try {
                    IRecipeCategory<?> real = manager.getRecipeCategory(type);
                    if (real != null) return real;
                } catch (Exception | LinkageError ignored) {
                }
            }
        }
        return CATEGORIES.get(typeUid);
    }

    public static synchronized void clear() {
        CATEGORIES.clear();
        UID_TO_TYPE.clear();
        JeiRecipeRegistry.clear();
    }

    /** The seven vanilla JEI holder-based types whose data the engine already
     *  indexes from RecipeManager (do not duplicate them here). */
    private static final Set<String> SKIP_VANILLA = Set.of(
            "minecraft:crafting", "minecraft:smelting", "minecraft:blasting",
            "minecraft:smoking", "minecraft:campfire_cooking",
            "minecraft:stonecutting", "minecraft:smithing");

    // ------------------------------------------------------------------

    private static JeiRecipeRegistry.Entry buildEntry(
            ResourceLocation uid, IRecipeCategory<?> category, Object recipe) {
        try {
            if (recipe instanceof IJeiAnvilRecipe anvil) {
                return new JeiRecipeRegistry.Entry(uid, recipe,
                        merge(anvil.getLeftInputs(), anvil.getRightInputs()), anvil.getOutputs());
            }
            if (recipe instanceof IJeiGrindstoneRecipe grindstone) {
                return new JeiRecipeRegistry.Entry(uid, recipe,
                        merge(grindstone.getTopInputs(), grindstone.getBottomInputs()),
                        grindstone.getOutputs());
            }
            if (recipe instanceof IJeiBrewingRecipe brewing) {
                return new JeiRecipeRegistry.Entry(uid, recipe,
                        merge(brewing.getPotionInputs(), brewing.getIngredients()),
                        List.of(brewing.getPotionOutput()));
            }
            if (recipe instanceof IJeiCompostingRecipe composting) {
                return new JeiRecipeRegistry.Entry(uid, recipe,
                        composting.getInputs(), List.of());
            }
            if (recipe instanceof IJeiFuelingRecipe fueling) {
                return new JeiRecipeRegistry.Entry(uid, recipe,
                        fueling.getInputs(), List.of());
            }
            if (recipe instanceof IJeiIngredientInfoRecipe info) {
                return new JeiRecipeRegistry.Entry(uid, recipe,
                        new ArrayList<>(), new ArrayList<>());
            }
            return buildGenericEntry(uid, category, recipe);
        } catch (Exception | LinkageError e) {
            LOGGER.debug("[BRBE-JEI-Plugins] entry build failed for {}: {}",
                    uid, e.toString());
            return null;
        }
    }

    /** setRecipe 失败时的最小条目：仅 item 列表（无 layout），弹窗走 vanilla
     *  兜底渲染，保证配方仍可查询。 */
    private static JeiRecipeRegistry.Entry minimalEntry(Object recipe) {
        try {
            if (recipe instanceof net.minecraft.world.item.crafting.RecipeHolder<?> holder
                    && holder.value() instanceof net.minecraft.world.item.crafting.Recipe<?> r) {
                List<ItemStack> inputs = new ArrayList<>();
                for (net.minecraft.world.item.crafting.Ingredient ing : r.getIngredients()) {
                    for (ItemStack st : ing.getItems()) {
                        if (st != null && !st.isEmpty()) inputs.add(st);
                    }
                }
                List<ItemStack> outputs = new ArrayList<>();
                net.minecraft.core.HolderLookup.Provider registries =
                        net.minecraft.client.Minecraft.getInstance().level != null
                                ? net.minecraft.client.Minecraft.getInstance().level.registryAccess()
                                : net.minecraft.core.RegistryAccess.EMPTY;
                ItemStack result = r.getResultItem(registries);
                if (result != null && !result.isEmpty()) outputs.add(result);
                if (!inputs.isEmpty() || !outputs.isEmpty()) {
                    return new JeiRecipeRegistry.Entry(
                            null, recipe, inputs, outputs);
                }
            }
        } catch (Exception | LinkageError ignored) {
        }
        return null;
    }

    /** Generic (mod recipe) path: run the category's setRecipe with a
     *  data-only layout builder and extract Input/Output stacks. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static JeiRecipeRegistry.Entry buildGenericEntry(
            ResourceLocation uid, IRecipeCategory<?> category, Object recipe) {
        if (category == null) return null;
        int width;
        int height;
        try {
            width = category.getWidth();
            height = category.getHeight();
        } catch (Exception | LinkageError e) {
            width = 0;
            height = 0;
        }
        DataOnlyLayoutBuilder builder = new DataOnlyLayoutBuilder(width, height);
        try {
            ((IRecipeCategory) category).setRecipe(builder, recipe, EmptyFocusGroup.INSTANCE);
        } catch (Exception | LinkageError e) {
            LOGGER.debug("[BRBE-JEI-Plugins] setRecipe failed for {}: {}",
                    uid, e.toString());
            // setRecipe 失败（如 smithing trim 的 tag 依赖未绑定）时保留配方
            // 条目本身（仅缺 layout）——弹窗回退 vanilla 布局，不整类丢失。
            return minimalEntry(recipe);
        }
        List<SlotData> slots = builder.slotData();
        List<ItemStack> inputs = new ArrayList<>();
        Map<net.minecraft.world.item.Item, ItemStack> products = new LinkedHashMap<>();
        List<JeiRecipeRegistry.Entry.Slot> layoutSlots = new ArrayList<>();
        for (SlotData slot : slots) {
            if (slot.stacks() != null && !slot.stacks().isEmpty()
                    && (slot.role() == RecipeIngredientRole.INPUT
                        || slot.role() == RecipeIngredientRole.CATALYST)) {
                inputs.addAll(slot.stacks());
            }
            if (slot.role() == RecipeIngredientRole.OUTPUT
                    || slot.role() == RecipeIngredientRole.RENDER_ONLY) {
                // visible output slots only (invisible slots are data-only)
                if (!slot.visible()) continue;
                for (ItemStack stack : slot.stacks()) {
                    if (stack != null && !stack.isEmpty()) {
                        products.putIfAbsent(stack.getItem(), stack);
                    }
                }
            }
            if (slot.stacks() != null && !slot.stacks().isEmpty()) {
                layoutSlots.add(new JeiRecipeRegistry.Entry.Slot(
                        slot.x(), slot.y(), slot.role().ordinal(), slot.stacks()));
            }
        }
        if (inputs.isEmpty() && products.isEmpty()) return null;
        return new JeiRecipeRegistry.Entry(uid, recipe, inputs,
                new ArrayList<>(products.values()), layoutSlots,
                Math.max(width, 0), Math.max(height, 0));
    }

    /** Renderer-visible output enumeration: anvil/grindstone products are the
     *  recipe's declared outputs (grindstone declares RENDER_ONLY slots). */
    private static String fingerprint(JeiRecipeRegistry.Entry entry) {
        StringBuilder sb = new StringBuilder();
        if (entry.inputs() != null) {
            for (ItemStack stack : entry.inputs()) {
                if (stack != null && !stack.isEmpty()) sb.append(stack.getItem()).append(',');
            }
        }
        sb.append('=');
        if (entry.outputs() != null) {
            for (ItemStack stack : entry.outputs()) {
                if (stack != null && !stack.isEmpty()) sb.append(stack.getItem()).append(',');
            }
        }
        return sb.toString();
    }

    private static List<ItemStack> merge(List<ItemStack> a, List<ItemStack> b) {
        List<ItemStack> out = new ArrayList<>();
        if (a != null) out.addAll(a);
        if (b != null) out.addAll(b);
        return out;
    }

    /** Workstation block items of a vanilla JEI plugin type (the same stations
     *  JEI's {@code registerRecipeCatalysts} declares; all anvil variants). */
    private static List<ItemStack> vanillaStationsFor(String uid) {
        List<ItemStack> out = new ArrayList<>();
        switch (uid) {
            case "minecraft:anvil" -> {
                addStation(out, "minecraft:anvil");
                addStation(out, "minecraft:chipped_anvil");
                addStation(out, "minecraft:damaged_anvil");
            }
            case "minecraft:brewing" -> addStation(out, "minecraft:brewing_stand");
            case "minecraft:grindstone" -> addStation(out, "minecraft:grindstone");
            default -> { }
        }
        return out;
    }

    private static void addStation(List<ItemStack> out, String id) {
        BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id))
                .ifPresent(item -> out.add(new ItemStack(item)));
    }

    private static List<ItemStack> resolveStations(Set<ResourceLocation> itemIds) {
        List<ItemStack> out = new ArrayList<>();
        if (itemIds == null) return out;
        for (ResourceLocation itemId : itemIds) {
            BuiltInRegistries.ITEM.getOptional(itemId)
                    .ifPresent(item -> out.add(new ItemStack(item)));
        }
        return List.copyOf(new LinkedHashSet<>(out));
    }
}
