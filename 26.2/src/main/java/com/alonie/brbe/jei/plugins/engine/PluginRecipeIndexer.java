package com.alonie.brbe.jei.plugins.engine;


import com.alonie.brbe.jei.plugins.engine.DataOnlyLayoutBuilder;
import com.alonie.brbe.jei.plugins.engine.EmptyFocusGroup;
import com.alonie.brbe.jei.plugins.engine.SlotData;
import com.alonie.brbe.jei.api.JeiRecipeRegistry;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.vanilla.IJeiAnvilRecipe;
import mezz.jei.api.recipe.vanilla.IJeiBrewingRecipe;
import mezz.jei.api.recipe.vanilla.IJeiCompostingRecipe;
import mezz.jei.api.recipe.vanilla.IJeiFuelingRecipe;
import mezz.jei.api.recipe.vanilla.IJeiGrindstoneRecipe;
import mezz.jei.api.recipe.vanilla.IJeiIngredientInfoRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.alonie.brbe.jei.plugins.HeadlessJeiLog;

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


    private PluginRecipeIndexer() {}

    /** Vanilla JEI types indexed from the JEI runtime.  anvil/brewing/grindstone
     *  are runtime-built (no datapack holders) — full entries.  stonecutting/
     *  smithing have datapack holders indexed by the consumer; here they only
     *  provide native layouts for the popup delegate (entries carry layout,
     *  the consumer attaches them instead of re-registering). */
    private static final List<String> VANILLA_PLUGIN_TYPES =
            List.of("minecraft:anvil", "minecraft:brewing", "minecraft:grindstone",
                    "minecraft:stonecutting", "minecraft:smithing");

    private static final Map<Identifier, IRecipeCategory<?>> CATEGORIES = new HashMap<>();
    /** uid → JEI 类型（渲染委托用：createRecipeLayoutDrawable 需要 manager 的
     *  真实类别实例——收集器实例的 background/arrow 来自 stub，绘制为空）。 */
    private static final Map<Identifier, IRecipeType<?>> UID_TO_TYPE = new HashMap<>();

    /** Rebuild mod-plugin entries and register them into the query engine. */
    public static synchronized void indexModData(
            List<IRecipeCategory<?>> categories,
            Map<IRecipeType<?>, List<Object>> recipesByType,
            Map<Identifier, Set<Identifier>> catalysts) {
        CATEGORIES.clear();
        Map<Identifier, List<JeiRecipeRegistry.Entry>> entries = new HashMap<>();
        Map<Identifier, List<ItemStack>> stations = new HashMap<>();

        if (categories != null) {
            for (IRecipeCategory<?> category : categories) {
                if (category == null) continue;
                IRecipeType<?> type = category.getRecipeType();
                if (type != null && type.getUid() != null) {
                    CATEGORIES.put(type.getUid(), category);
                    UID_TO_TYPE.put(type.getUid(), type);
                }
            }
        }

        if (recipesByType != null) {
            for (Map.Entry<IRecipeType<?>, List<Object>> entry : recipesByType.entrySet()) {
                IRecipeType<?> type = entry.getKey();
                if (type == null || type.getUid() == null) continue;
                Identifier uid = type.getUid();
                if (SKIP_VANILLA.contains(uid.toString())) continue;
                IRecipeCategory<?> category = CATEGORIES.get(uid);
                int done = 0;
                for (Object recipe : entry.getValue()) {
                    if (recipe == null) continue;
                    JeiRecipeRegistry.Entry jeiEntry = buildEntry(uid, category, recipe);
                    if (jeiEntry == null) continue;
                    entries.computeIfAbsent(uid, k -> new ArrayList<>()).add(jeiEntry);
                    done++;
                }
                HeadlessJeiLog.log("BRBE-JEI-PLUGINS", "mod type {}: {} recipes -> {} indexed (category={})",
                        uid, entry.getValue().size(), done, category != null);
            }
        }

        if (catalysts != null) {
            for (Map.Entry<Identifier, Set<Identifier>> e : catalysts.entrySet()) {
                if (e.getKey() == null) continue;
                // 不跳过 vanilla 类型：mod 工作站注册到原版类型（如 BetterEnd
                // 末地石冶炼炉 → minecraft:blasting、FD 煎锅 → campfire）同样
                // 写入 registry 的 stations——消费者（BRBE 主侧）据此把 mod 站
                // 注册进工作站表（烧炼行图标/查询命中）。
                List<ItemStack> stacks = resolveStations(e.getValue());
                if (!stacks.isEmpty()) {
                    stations.put(e.getKey(), stacks);
                }
            }
        }
        for (Identifier uid : entries.keySet()) {
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
        Map<Identifier, List<JeiRecipeRegistry.Entry>> entries = new HashMap<>();
        Map<Identifier, List<ItemStack>> stations = new HashMap<>();

        Map<Identifier, IRecipeCategory<?>> categoriesByUid = new HashMap<>();
        for (IRecipeCategory<?> category : manager.createRecipeCategoryLookup().get().toList()) {
            IRecipeType<?> type = category.getRecipeType();
            if (type != null && type.getUid() != null) {
                categoriesByUid.put(type.getUid(), category);
                CATEGORIES.put(type.getUid(), category);
                UID_TO_TYPE.put(type.getUid(), type);
            }
        }
        for (String uid : VANILLA_PLUGIN_TYPES) {
            try {
                IRecipeCategory<?> category = categoriesByUid.get(
                        Identifier.parse(uid));
                if (category == null) continue;
                List<?> recipes;
                try {
                    recipes = manager.createRecipeLookup(category.getRecipeType()).get().toList();
                } catch (Exception | LinkageError e) {
                    HeadlessJeiLog.log("BRBE-JEI-PLUGINS", "vanilla {} recipe lookup failed: {}",
                            uid, e.toString());
                    continue;
                }
                if (recipes.isEmpty()) continue;
                List<JeiRecipeRegistry.Entry> indexed = new ArrayList<>();
                Set<String> seen = new HashSet<>();
                for (Object recipe : recipes) {
                    JeiRecipeRegistry.Entry entry = buildEntry(
                            Identifier.parse(uid), category, recipe);
                    if (entry == null) continue;
                    String fingerprint = fingerprint(entry);
                    if (!seen.add(fingerprint)) continue;
                    indexed.add(entry);
                }
                if (indexed.isEmpty()) continue;
                entries.put(Identifier.parse(uid), indexed);
                stations.put(Identifier.parse(uid), vanillaStationsFor(uid));
                HeadlessJeiLog.log("BRBE-JEI-PLUGINS", "vanilla runtime type {}: {} recipes indexed",
                        uid, indexed.size());
            } catch (Exception | LinkageError e) {
                HeadlessJeiLog.log("BRBE-JEI-PLUGINS", "vanilla runtime type {} pass failed: {}",
                        uid, e.toString());
            }
        }
        register(entries, stations, "vanilla runtime");
    }

    /** Merge {@code entries} into the bridge registry (per-uid replace, other
     *  uids preserved — mod plugins and vanilla runtime are separate passes). */
    private static void register(Map<Identifier, List<JeiRecipeRegistry.Entry>> entries,
                                 Map<Identifier, List<ItemStack>> stations,
                                 String source) {
        Map<Identifier, String> titles = new HashMap<>();
        for (Identifier uid : entries.keySet()) {
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
            HeadlessJeiLog.log("BRBE-JEI-PLUGINS", "indexed {} JEI types ({} entries, {})",
                    entries.size(), entries.values().stream().mapToInt(List::size).sum(), source);
        }
    }

    /** The category for a JEI type uid (popup rendering).  Prefers the JEI
     *  manager's real registered instance (its background/arrow drawables are
     *  real); the collected instance (stub drawables) is only a fallback. */
    public static synchronized IRecipeCategory<?> categoryFor(Identifier typeUid) {
        IRecipeType<?> type = UID_TO_TYPE.get(typeUid);
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
            Identifier uid, IRecipeCategory<?> category, Object recipe) {
        try {
            if (recipe instanceof IJeiAnvilRecipe anvil) {
                return withLayout(uid, recipe,
                        merge(anvil.getLeftInputs(), anvil.getRightInputs()), anvil.getOutputs(),
                        category);
            }
            if (recipe instanceof IJeiGrindstoneRecipe grindstone) {
                return withLayout(uid, recipe,
                        merge(grindstone.getTopInputs(), grindstone.getBottomInputs()),
                        grindstone.getOutputs(), category);
            }
            if (recipe instanceof IJeiBrewingRecipe brewing) {
                return withLayout(uid, recipe,
                        merge(brewing.getPotionInputs(), brewing.getIngredients()),
                        List.of(brewing.getPotionOutput()), category);
            }
            if (recipe instanceof IJeiCompostingRecipe composting) {
                return withLayout(uid, recipe,
                        composting.getInputs(), List.of(), category);
            }
            if (recipe instanceof IJeiFuelingRecipe fueling) {
                return withLayout(uid, recipe,
                        fueling.getInputs(), List.of(), category);
            }
            if (recipe instanceof IJeiIngredientInfoRecipe info) {
                return withLayout(uid, recipe,
                        new ArrayList<>(), new ArrayList<>(), category);
            }
            return buildGenericEntry(uid, category, recipe);
        } catch (Exception | LinkageError e) {
            HeadlessJeiLog.log("BRBE-JEI-PLUGINS", "entry build failed for {}: {}",
                    uid, e.toString());
            return null;
        }
    }

    /** Interface-accessed vanilla recipes: keep the interface-extracted item
     *  lists, but also run the category's setRecipe to capture the slot layout
     *  (width/height/slots) — the JEI-delegated popup renderer needs it. */
    private static JeiRecipeRegistry.Entry withLayout(
            Identifier uid, Object recipe, List<ItemStack> inputs,
            List<ItemStack> outputs, IRecipeCategory<?> category) {
        LayoutData layout = null;
        if (category != null) {
            layout = captureLayout(category, recipe);
        }
        if (layout == null) {
            return new JeiRecipeRegistry.Entry(uid, recipe, inputs, outputs);
        }
        return new JeiRecipeRegistry.Entry(uid, recipe, inputs, outputs,
                layout.slots, layout.width, layout.height);
    }

    /** Run the category's setRecipe against a data-only builder and return
     *  the recorded slot layout (null on any failure). */
    private static LayoutData captureLayout(IRecipeCategory<?> category, Object recipe) {
        try {
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
            ((IRecipeCategory) category).setRecipe(builder, recipe, EmptyFocusGroup.INSTANCE);
            List<SlotData> slots = builder.slotData();
            List<JeiRecipeRegistry.Entry.Slot> layoutSlots = new ArrayList<>();
            for (SlotData slot : slots) {
                if (slot.stacks() == null || slot.stacks().isEmpty()) continue;
                layoutSlots.add(new JeiRecipeRegistry.Entry.Slot(
                        slot.x(), slot.y(), slot.role().ordinal(), slot.stacks()));
            }
            if (layoutSlots.isEmpty() && width <= 0 && height <= 0) return null;
            return new LayoutData(layoutSlots, Math.max(width, 0), Math.max(height, 0));
        } catch (Exception | LinkageError e) {
            HeadlessJeiLog.log("BRBE-JEI-PLUGINS", "layout capture failed for {}: {}",
                    recipe, e.toString());
            return null;
        }
    }

    /** setRecipe 失败时的最小条目：仅 item 列表（无 layout），弹窗走 vanilla
     *  兜底渲染，保证配方仍可查询。 */
    private static JeiRecipeRegistry.Entry minimalEntry(Object recipe) {
        try {
            if (recipe instanceof net.minecraft.world.item.crafting.RecipeHolder<?> holder
                    && holder.value() instanceof net.minecraft.world.item.crafting.SmithingRecipe smithing) {
                // 锻造：template/base/addition（Ingredient → 代表物品）+ 产物
                List<ItemStack> inputs = new ArrayList<>();
                for (int i = 0; i < 3; i++) {
                    net.minecraft.world.item.crafting.Ingredient ing = switch (i) {
                        case 0 -> smithing.templateIngredient()
                                .orElse(net.minecraft.world.item.crafting.Ingredient.of());
                        case 1 -> smithing.baseIngredient();
                        default -> smithing.additionIngredient()
                                .orElse(net.minecraft.world.item.crafting.Ingredient.of());
                    };
                    ing.items().forEach(itemHolder -> {
                        ItemStack st = new ItemStack(itemHolder.value());
                        if (!st.isEmpty()) inputs.add(st);
                    });
                }
                List<ItemStack> outputs = new ArrayList<>();
                try {
                    net.minecraft.world.item.crafting.display.SlotDisplay result = smithing.display().getFirst().result();
                    List<ItemStack> stacks = result.resolveForStacks(
                            net.minecraft.world.item.crafting.display.SlotDisplayContext.fromLevel(
                                    net.minecraft.client.Minecraft.getInstance().level));
                    if (stacks != null) {
                        for (ItemStack item : stacks) {
                            if (item != null && !item.isEmpty()) outputs.add(item);
                        }
                    }
                } catch (Exception | LinkageError ignored2) {
                }
                if (!inputs.isEmpty() || !outputs.isEmpty()) {
                    return new JeiRecipeRegistry.Entry(
                            null, recipe, inputs, outputs);
                }
            }
        } catch (Exception | LinkageError ignored) {
        }
        return null;
    }

    private record LayoutData(List<JeiRecipeRegistry.Entry.Slot> slots,
                              int width, int height) {}

    /** Generic (mod recipe) path: run the category's setRecipe with a
     *  data-only layout builder and extract Input/Output stacks. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static JeiRecipeRegistry.Entry buildGenericEntry(
            Identifier uid, IRecipeCategory<?> category, Object recipe) {
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
            HeadlessJeiLog.log("BRBE-JEI-PLUGINS", "setRecipe failed for {}: {}",
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
                        || slot.role() == RecipeIngredientRole.CRAFTING_STATION)) {
                inputs.addAll(slot.stacks());
            }
            if (slot.role() == RecipeIngredientRole.RENDER_ONLY) {
                // RENDER_ONLY 槽位语义 = "仅渲染"（JEI 数据模型里的上下文物品，
                // 如熔炉类类别左下角的燃料槽——aerial hell 冷却器/振荡器的
                // 岩浆凝胶/氟石即此角色）：不是产物的声明——产物只信 OUTPUT 角色
                // （原版研磨的 RENDER_ONLY 输出走 IJeiGrindstoneRecipe 接口路径，
                // 产物由接口显式提供，不受这里影响）。但 RENDER_ONLY 物品仍是
                // 查询对象（JEI 对该槽位同样支持 R/U 聚焦），计入 inputs 让
                // usage（U）查询命中本配方；绝不进产物列表（否则燃料会在对象
                // 按钮上轮循/被误当作该站的产物）。
                if (!slot.visible()) continue;
                for (ItemStack stack : slot.stacks()) {
                    if (stack != null && !stack.isEmpty()) {
                        inputs.add(stack);
                    }
                }
            }
            if (slot.role() == RecipeIngredientRole.OUTPUT) {
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

    /** The dedup key of a recipe entry: every input and product stack keyed by
     *  REGISTRY ID + FULL COMPONENTS (enchantments, potion contents, damage…).
     *  The former item-only key collapsed stacks differing only in components —
     *  anvil book-enchant recipes of the same base item (Sharpness vs
     *  Unbreaking books) and brewing recipes of the same potion item all shared
     *  one fingerprint, so only the FIRST variant survived the {@code seen}
     *  set and every other enchantment was silently dropped. */
    private static String fingerprint(JeiRecipeRegistry.Entry entry) {
        StringBuilder sb = new StringBuilder();
        if (entry.inputs() != null) {
            for (ItemStack stack : entry.inputs()) {
                sb.append(stackKey(stack)).append(',');
            }
        }
        sb.append('=');
        if (entry.outputs() != null) {
            for (ItemStack stack : entry.outputs()) {
                sb.append(stackKey(stack)).append(',');
            }
        }
        return sb.toString();
    }

    /** {@code stack} 的稳定键：注册 id + 逐组件序列化（组件 {@code toString}
     *  只要求同一轮收集内一致——dedup 只在单次收集内生效）。 */
    private static String stackKey(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(
                String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem())));
        for (TypedDataComponent<?> tc : stack.getComponents()) {
            sb.append('/').append(tc.type()).append('=').append(tc.value());
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
        BuiltInRegistries.ITEM.getOptional(Identifier.parse(id))
                .ifPresent(item -> out.add(new ItemStack(item)));
    }

    private static List<ItemStack> resolveStations(Set<Identifier> itemIds) {
        List<ItemStack> out = new ArrayList<>();
        if (itemIds == null) return out;
        for (Identifier itemId : itemIds) {
            BuiltInRegistries.ITEM.getOptional(itemId)
                    .ifPresent(item -> out.add(new ItemStack(item)));
        }
        return List.copyOf(new LinkedHashSet<>(out));
    }
}
