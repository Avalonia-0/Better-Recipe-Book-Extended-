package com.alonie.brbe.brewingstand;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.cache.RecipeViewerIndex;
import com.alonie.brbe.loaders.PotionLoader;
import com.alonie.brbe.util.ClientCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.RecipeToast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.alonie.brbe.brewingstand.PlatformPotionUtil.getIngredient;
import static com.alonie.brbe.brewingstand.PlatformPotionUtil.getFrom;
import static com.alonie.brbe.brewingstand.PlatformPotionUtil.getTo;
import com.alonie.brbe.util.BrbeLogger;

/**
 * 酿造/锻造配方书进度解锁——全部数据运行时推导，零硬编码；
 * 解锁状态<b>只存于原版进度（advancement）系统</b>（世界内完成状态 = 天然
 * 按存档隔离），不设任何自建持久化。
 *
 * <p><b>酿造</b>：材料全集 ← 运行时 {@code PotionBrewing} 混合表（
 * {@link PotionLoader} 重建，含模组材料，版本改名自动跟随）。BRBE 在<b>服务器
 * 启动</b>（{@link #writeProgressPacksForServer}，
 * {@code ServerLifecycleEvents.SERVER_STARTED}）时把触发器数据包（每个材料一条
 * {@code brbe:brew/material/<ns>-<path>} advancement，
 * {@code inventory_changed} 获得材料即完成，无 display 静默——不进进度页、
 * 也不弹成就）写入 <b>当前世界 datapacks 文件夹</b>（
 * {@code <世界>/datapacks/brbe_progress/}——路径经
 * {@code server.getWorldPath(DATAPACK_DIR)} 决定论正确：客户端 JOIN 时机的
 * {@code getWorldData().getLevelName()} 在切换世界进程中实测不可靠（曾把包写进
 * 上一个世界）；服务器侧写入同时覆盖 LAN/多机——服务器装 BRBE 即 per-save 进度）。
 * 内容指纹去重，注册表变化自动重写。静态生成的一切脆性（版本改名/模组差异）
 * 因源自运行时注册表而不存在。解锁显示：观察 advancement 完成 → 药水在酿造书
 * 显示 + 原版配方解锁弹窗（{@link RecipeToast} 复刻）。数据包在服务器启动的
 * datapack 加载完成后写入 → 次局起生效；首局观察解锁为会话级（不持久化）。</p>
 *
 * <p><b>会话隔离</b>：会话级状态（观察集/注入集）在世界断开（{@link #resetSession()}，
 * {@code ClientPlayConnectionEvents.DISCONNECT}）时清空——同一次运行切换存档
 * 不会继承上一个存档的解锁轨迹；解锁权威始终是原版 advancement（按世界存储），
 * 观察仅作为该世界内的即时回填。
 *
 * <p><b>锻造</b>：解锁由<b>运行时生成</b>的 {@code brbe:recipe/<配方id>}
 * advancement 驱动（与酿造同 {@code brbe_progress} 包、同写入时机——服务器启动
 * 时枚举服务器配方管理器的<b>模组</b>锻造配方；<b>原版锻造配方走原生存解锁</b>：
 * 数据生成器 {@code minecraft:advancement/recipes/*}——升级系 = 获得下界合金锭、
 * 纹饰系 = 获得纹饰模板，原版条件即权威，BRBE 不干预不覆盖）。模组判定<b>镜像
 * 原版语义</b>（transform = 附加材料、trim = 模板）；{@code rewards.recipes}
 * 走原版奖励链路；完成状态存世界内。BRBE 观察完成 → {@code ClientRecipeBook.add(display)}
 * 注入本地配方书（原版 API，unlock-all 同款）——锻造书/查询立即显示。
 * 构建期静态包与生成脚本已退役。</p>
 *
 * <p><b>unlockAll 兼容</b>：开启时酿造书全量显示（{@link #isUnlocked} 短路）。
 * 锻造书由 unlockAll 的已知集注入自然覆盖。</p>
 */
public final class RecipeUnlockTracker {

    private RecipeUnlockTracker() {}

    /** 酿造材料：物品 id → 以其为 ingredient 的产物药水 id 集。 */
    private static volatile Map<Identifier, Set<Identifier>> MATERIAL_RESULTS = Map.of();

    /** 本会话内已解锁的酿造材料（首局观察 + advancement 完成回填）。 */
    private static final Set<Identifier> KNOWN_MATERIALS = new HashSet<>();

    /** 本会话已注入的锻造配方（advancement 完成观察去重）。 */
    private static final Set<String> INJECTED_RECIPES = new HashSet<>();

    private static int tickCounter;

    /** {@link PotionLoader#load} 后调用：运行时重建会话侧"材料 → 产物"映射
     *  （进度门控用）。触发器数据包的写入已在服务器侧（
     *  {@link #writeProgressPacksForServer}，{@code ServerLifecycleEvents.SERVER_STARTED}）。 */
    public static void refreshIngredients() {
        MATERIAL_RESULTS = deriveMaterialResults(
                net.minecraft.client.Minecraft.getInstance().level);
        BrbeLogger.log("BRBE-RECIPE-PROGRESS", "tracked {} brewing materials ({} results)",
                MATERIAL_RESULTS.size(),
                MATERIAL_RESULTS.values().stream().mapToInt(Set::size).sum());
    }

    /** 运行时推导"材料 → 产物"映射（PotionBrewing 混合表，客户端/服务器线程
     *  皆可调用——材料表为注册表静态内容，任何 level 传入均可）。 */
    public static Map<Identifier, Set<Identifier>> deriveMaterialResults(net.minecraft.world.level.Level level) {
        Map<Identifier, Set<Identifier>> mapping = new java.util.HashMap<>();
        try {
            for (Object raw : PlatformPotionUtil.getPotionMixes(level)) {
                BrewableResult potion = new BrewableResult(raw);
                Identifier result = null;
                try {
                    result = net.minecraft.core.registries.BuiltInRegistries.POTION
                            .getKey(getTo(potion.recipe));
                } catch (Exception | LinkageError ignored) {
                }
                if (result == null) continue;
                try {
                    for (ItemStack stack : ClientCompat.ingredientItems(getIngredient(potion.recipe))) {
                        if (stack == null || stack.isEmpty()) continue;
                        Identifier material = net.minecraft.core.registries.BuiltInRegistries.ITEM
                                .getKey(stack.getItem());
                        if (material != null) {
                            mapping.computeIfAbsent(material, k -> new HashSet<>()).add(result);
                        }
                    }
                } catch (Exception | LinkageError ignored) {
                }
            }
        } catch (Exception | LinkageError ignored) {
        }
        Map<Identifier, Set<Identifier>> frozen = new java.util.HashMap<>();
        for (Map.Entry<Identifier, Set<Identifier>> e : mapping.entrySet()) {
            frozen.put(e.getKey(), Set.copyOf(e.getValue()));
        }
        return Map.copyOf(frozen);
    }

    /** 酿造配方是否已解锁（材料获得过——advancement 完成或本会话观察；
     *  unlockAll 时恒真）。 */
    public static boolean isUnlocked(BrewableResult potion) {
        if (BetterRecipeBook.config.unlockAll) return true;
        if (potion == null || potion.recipe == null) return true;
        try {
            boolean tracked = false;
            for (ItemStack stack : ClientCompat.ingredientItems(getIngredient(potion.recipe))) {
                if (stack == null || stack.isEmpty()) continue;
                Identifier material = net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(stack.getItem());
                if (material == null) continue;
                if (MATERIAL_RESULTS.containsKey(material)) tracked = true;
                if (KNOWN_MATERIALS.contains(material)) return true;
            }
            return !tracked;
        } catch (Exception | LinkageError e) {
            return true;
        }
    }

    /** 查询侧门控：产物药水是否已解锁（其材料进度；unlockAll 时恒真）。 */
    public static boolean isPotionResultUnlocked(Identifier potionId) {
        if (potionId == null) return false;
        if (BetterRecipeBook.config.unlockAll) return true;
        for (Map.Entry<Identifier, Set<Identifier>> e : MATERIAL_RESULTS.entrySet()) {
            if (KNOWN_MATERIALS.contains(e.getKey()) && e.getValue().contains(potionId)) return true;
        }
        return false;
    }

    /** 会话级进度状态清空（世界断开时调用）：同一会话切换存档不得继承
     *  上一个存档的解锁状态（权威 = 原版 advancement，按世界存储）——
     *  KNOWN_MATERIALS/INJECTED_RECIPES 若不清理，下一个存档会继承本会话
     *  此前观察到的全部解锁/注入轨迹。下一世界 JOIN 后由 advancement 观察
     *  （{@link #applyCompletedProgress}）重新回填该世界的完成状态（世界内
     *  权威）。触发器数据包写入在服务器侧按服务器生命周期执行（每世界一次），
     *  与客户端会话状态无关。 */
    public static void resetSession() {
        KNOWN_MATERIALS.clear();
        INJECTED_RECIPES.clear();
        tickCounter = 0;
    }

    /** 主循环（ClientTick 调用）：物品栏观察（即时解锁 + 弹窗；首局引导）
     *  + advancement 完成观察（酿造材料/锻造配方；进度存储回填 + 注入）。
     *  触发器数据包写入见 {@link #writeProgressPacksForServer}。 */
    public static void tick(Minecraft mc) {
        if (mc.player == null) return;
        if (++tickCounter % 5 != 0) return;
        scanInventory(mc);
        if (tickCounter % 20 == 0) {
            applyCompletedProgress(mc);
        }
    }

    // ------------------------------------------------------------------
    // 触发器数据包写入：服务器侧（SERVER_STARTED）——世界路径经
    // server.getWorldPath(DATAPACK_DIR) 决定论正确（客户端 JOIN 时机的
    // getWorldData().getLevelName() 在切换世界进程中实测不可靠，曾把包写进
    // 上一个世界）。
    // ------------------------------------------------------------------

    /** 服务器启动回调（{@code ServerLifecycleEvents.SERVER_STARTED}，主入口
     *  注册）：把酿造/锻造触发器数据包写入<b>当前世界</b> datapacks 文件夹
     *  （单机集成服务器与 LAN/多机服务器同——服务器侧权威路径；内容指纹去重）。
     *  数据包在服务器启动的 datapack 加载完成后写入 → 次局生效（与既有引导
     *  语义一致）。 */
    public static void writeProgressPacksForServer(net.minecraft.server.MinecraftServer server) {
        try {
            if (server == null || server.overworld() == null) return;
            // 平台触发器初始化（客户端入口已 init；独立服务端场景补一次，幂等）。
            com.alonie.brbe.brewingstand.fabric.PlatformPotionUtilImpl.init();
            java.nio.file.Path packRoot = server.getWorldPath(
                            net.minecraft.world.level.storage.LevelResource.DATAPACK_DIR)
                    .resolve("brbe_progress");
            writeBrewProgressPack(packRoot, deriveMaterialResults(server.overworld()));
            writeSmithingProgressPack(server, packRoot);
        } catch (Exception | LinkageError e) {
            BetterRecipeBook.LOGGER.warn("[BRBE-RECIPE-PROGRESS] progress pack write failed: {}",
                    e.toString());
        }
    }

    /** 酿造数据包（触发器部分见方法注释）。 */
    private static void writeBrewProgressPack(java.nio.file.Path packRoot,
                                              Map<Identifier, Set<Identifier>> results) {
        if (results == null || results.isEmpty()) return;
        try {
            String fingerprint = results.keySet().stream()
                    .map(Object::toString).sorted()
                    .collect(java.util.stream.Collectors.joining(","));
            java.nio.file.Path fp = packRoot.resolve(".materials.fingerprint");
            if (java.nio.file.Files.exists(fp)
                    && fingerprint.equals(java.nio.file.Files.readString(fp))) {
                return; // 内容未变——无需重写
            }
            String mcmeta = "{\"pack\":{\"pack_format\":" + currentDataPackFormat()
                    + ",\"description\":\"BRBE progress triggers\"}}";
            java.nio.file.Files.createDirectories(packRoot);
            java.nio.file.Files.writeString(packRoot.resolve("pack.mcmeta"), mcmeta);
            java.nio.file.Path advDir = packRoot.resolve("data/brbe/advancement/brew");
            writeBrewJson(advDir.resolve("root.json"),
                    "{\"criteria\":{\"base\":{\"trigger\":\"minecraft:tick\"}}}");
            for (Identifier material : results.keySet()) {
                String ns = material.getNamespace();
                String path = material.getPath().replace('/', '_');
                String adv = "{\"parent\":\"brbe:brew/root\","
                        + "\"criteria\":{\"have_material\":{\"trigger\":"
                        + "\"minecraft:inventory_changed\",\"conditions\":"
                        + "{\"items\":[{\"items\":[\"" + material
                        + "\"]}]}}}}";
                writeBrewJson(advDir.resolve(ns + "-" + path + ".json"), adv);
            }
            java.nio.file.Files.writeString(fp, fingerprint);
            BrbeLogger.log("BRBE-RECIPE-PROGRESS", "wrote brew progress pack to {} ({} materials)",
                    packRoot, results.size());
        } catch (Exception | LinkageError e) {
            BetterRecipeBook.LOGGER.warn("[BRBE-RECIPE-PROGRESS] brew pack write failed: {}",
                    e.toString());
        }
    }

    private static void writeBrewJson(java.nio.file.Path file, String content) throws Exception {
        java.nio.file.Files.createDirectories(file.getParent());
        java.nio.file.Files.writeString(file, content);
    }

    private static int currentDataPackFormat() {
        try {
            return net.minecraft.SharedConstants.getCurrentVersion()
                    .packVersion(net.minecraft.server.packs.PackType.SERVER_DATA)
                    .major();
        } catch (Exception | LinkageError e) {
            return -1;
        }
    }

    // ------------------------------------------------------------------
    // 锻造数据包：运行时生成（服务器侧写入；与酿造同包）
    // ------------------------------------------------------------------

    /** 锻造：运行时生成触发器数据包（与酿造同 {@code brbe_progress} 包、
     *  <b>服务器侧</b>写入——{@link #writeProgressPacksForServer}）。
     *  <b>只生成模组锻造配方</b>：原版锻造配方有<b>原生存解锁条件</b>（数据
     *  生成器 {@code minecraft:advancement/recipes/*}：升级系 = 获得下界合金锭、
     *  纹饰系 = 获得纹饰模板）——原版条件即权威，BRBE 不干预（也不再覆盖）。
     *  模组配方没有原生存解锁（生成器只跑原版数据）——BRBE 包是唯一机制，
     *  <b>判定镜像原版语义</b>：transform = 获得附加材料、trim = 获得纹饰模板；
     *  {@code has_the_recipe} 任一满足；{@code rewards.recipes} 原版奖励链路。
     *  指纹 = 模组配方 id + 判定内容；变化自动重写（重写时清理遗留的旧覆盖
     *  目录）；次局起生效（与酿造同引导语义）。 */
    private static void writeSmithingProgressPack(
            net.minecraft.server.MinecraftServer server, java.nio.file.Path packRoot) {
        try {
            java.util.List<net.minecraft.world.item.crafting.RecipeHolder<?>> smithing =
                    new java.util.ArrayList<>();
            for (net.minecraft.world.item.crafting.RecipeHolder<?> holder
                    : server.getRecipeManager().getRecipes()) {
                if (holder != null
                        && holder.value() instanceof net.minecraft.world.item.crafting.SmithingRecipe
                        && !holder.id().identifier().getNamespace().equals("minecraft")) {
                    smithing.add(holder);
                }
            }
            // 清理旧版本的遗留覆盖文件（data/minecraft/... 的 impossible 覆盖，
            // 曾用于"只认模板"——现按用户决定回归原版条件）：无论模组配方有无
            // 都执行，否则旧覆盖会残留在包目录中继续压制原版解锁。
            java.nio.file.Path staleOverride = packRoot.resolve("data/minecraft");
            if (java.nio.file.Files.exists(staleOverride)) {
                try (java.util.stream.Stream<java.nio.file.Path> walk =
                             java.nio.file.Files.walk(staleOverride)) {
                    walk.sorted(java.util.Comparator.reverseOrder())
                            .forEach(p -> {
                                try {
                                    java.nio.file.Files.delete(p);
                                } catch (Exception ignored) {
                                }
                            });
                } catch (Exception ignored) {
                }
            }
            if (smithing.isEmpty()) return;
            // 指纹 = 模组配方 id + 判定内容（物品集）：判定规则/物品集变化
            // （含已写包的世界）触发重写。
            String fingerprint = smithing.stream()
                    .map(h -> h.id().identifier() + "|" + String.join("+",
                            criterionItemsOf((net.minecraft.world.item.crafting.SmithingRecipe) h.value())))
                    .sorted()
                    .collect(java.util.stream.Collectors.joining(",")) + "|vanilla-native";
            java.nio.file.Path fp = packRoot.resolve(".recipes.fingerprint");
            if (java.nio.file.Files.exists(fp)
                    && fingerprint.equals(java.nio.file.Files.readString(fp))) {
                return; // 内容未变——无需重写
            }
            java.nio.file.Files.createDirectories(packRoot);
            java.nio.file.Files.writeString(packRoot.resolve("pack.mcmeta"),
                    "{\"pack\":{\"pack_format\":" + currentDataPackFormat()
                            + ",\"description\":\"BRBE progress triggers\"}}");
            java.nio.file.Path advDir = packRoot.resolve("data/brbe/advancement/recipe");
            writeBrewJson(advDir.resolve("root.json"),
                    "{\"criteria\":{\"base\":{\"trigger\":\"minecraft:tick\"}}}");
            int written = 0;
            for (net.minecraft.world.item.crafting.RecipeHolder<?> holder : smithing) {
                Identifier id = holder.id().identifier();
                net.minecraft.world.item.crafting.SmithingRecipe recipe =
                        (net.minecraft.world.item.crafting.SmithingRecipe) holder.value();
                // 判定镜像原版语义：transform = 获得附加材料（如下界合金锭之于
                // 原版升级系）；trim = 获得纹饰模板（原版纹饰系同款）；模板/
                // 附加任一为空时防御性回退另一侧。
                boolean trim = recipe instanceof net.minecraft.world.item.crafting.SmithingTrimRecipe;
                java.util.List<String> criterionItems = new java.util.ArrayList<>();
                if (trim) {
                    collectCriterionItems(recipe.templateIngredient().orElse(null), criterionItems);
                    if (criterionItems.isEmpty()) {
                        collectCriterionItems(recipe.additionIngredient().orElse(null), criterionItems);
                    }
                } else {
                    collectCriterionItems(recipe.additionIngredient().orElse(null), criterionItems);
                    if (criterionItems.isEmpty()) {
                        collectCriterionItems(recipe.templateIngredient().orElse(null), criterionItems);
                    }
                }
                if (criterionItems.isEmpty()) continue;
                String criteriaName = trim ? "has_template" : "has_addition";
                String itemsJson = String.join("\",\"", criterionItems);
                String adv = "{\"parent\":\"brbe:recipe/root\","
                        + "\"criteria\":{\"" + criteriaName + "\":{\"trigger\":"
                        + "\"minecraft:inventory_changed\",\"conditions\":"
                        + "{\"items\":[{\"items\":[\"" + itemsJson + "\"]}]}},"
                        + "\"has_the_recipe\":{\"trigger\":\"minecraft:recipe_unlocked\","
                        + "\"conditions\":{\"recipe\":\"" + id + "\"}}},"
                        + "\"requirements\":[[\"has_the_recipe\"],[\"" + criteriaName + "\"]],"
                        + "\"rewards\":{\"recipes\":[\"" + id + "\"]}}";
                writeBrewJson(advDir.resolve(id.getNamespace())
                        .resolve(id.getPath() + ".json"), adv);
                written++;
            }
            if (written == 0) return;
            java.nio.file.Files.writeString(fp, fingerprint);
            BrbeLogger.log("BRBE-RECIPE-PROGRESS", "wrote smithing progress pack to {} ({} mod recipes)",
                    packRoot, written);
        } catch (Exception | LinkageError e) {
            BetterRecipeBook.LOGGER.warn("[BRBE-RECIPE-PROGRESS] smithing pack write failed: {}",
                    e.toString());
        }
    }

    /** 配方解锁判定物品集：镜像原版语义（transform = 附加材料、trim = 模板）。 */
    private static java.util.List<String> criterionItemsOf(
            net.minecraft.world.item.crafting.SmithingRecipe recipe) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (recipe instanceof net.minecraft.world.item.crafting.SmithingTrimRecipe) {
            collectCriterionItems(recipe.templateIngredient().orElse(null), out);
            if (out.isEmpty()) {
                collectCriterionItems(recipe.additionIngredient().orElse(null), out);
            }
        } else {
            collectCriterionItems(recipe.additionIngredient().orElse(null), out);
            if (out.isEmpty()) {
                collectCriterionItems(recipe.templateIngredient().orElse(null), out);
            }
        }
        return out;
    }

    /** 收集 ingredient 的全部物品 id（标签已展开；判定 = 获得其中任一）。
     *  条目注入 criterion 的 {@code items} 数组。 */
    private static void collectCriterionItems(
            net.minecraft.world.item.crafting.Ingredient ingredient, java.util.List<String> out) {
        if (ingredient == null) return;
        try {
            ingredient.items().forEach(holder -> {
                Identifier key = net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(holder.value());
                if (key != null) out.add(key.toString());
            });
        } catch (Exception | LinkageError ignored) {
            // unresolvable ingredient — skip
        }
    }

    // ------------------------------------------------------------------
    // 酿造：物品栏观察（即时会话级解锁 + 原版配方解锁弹窗）
    // ------------------------------------------------------------------

    private static void scanInventory(Minecraft mc) {
        if (MATERIAL_RESULTS.isEmpty()) return;
        boolean changed = false;
        net.minecraft.world.entity.player.Inventory inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.isEmpty()) continue;
            Identifier material = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getKey(stack.getItem());
            if (material == null || !MATERIAL_RESULTS.containsKey(material)) continue;
            if (KNOWN_MATERIALS.add(material)) {
                changed = true;
                for (Identifier resultPotion : MATERIAL_RESULTS.get(material)) {
                    toastBrewingUnlock(mc, resultPotion);
                }
            }
        }
        if (changed) {
            BrbeLogger.log("BRBE-RECIPE-PROGRESS", "observed brewing materials: {}",
                    KNOWN_MATERIALS.size());
        }
    }

    private static void toastBrewingUnlock(Minecraft mc, Identifier resultPotionId) {
        try {
            net.minecraft.world.item.alchemy.Potion potion =
                    net.minecraft.core.registries.BuiltInRegistries.POTION
                            .getOptional(resultPotionId).orElse(null);
            if (potion == null) return;
            ItemStack potionStack = net.minecraft.world.item.alchemy.PotionContents
                    .createItemStack(net.minecraft.world.item.Items.POTION,
                            net.minecraft.core.Holder.direct(potion));
            net.minecraft.world.item.crafting.display.SlotDisplay.ItemStackSlotDisplay slot =
                    new net.minecraft.world.item.crafting.display.SlotDisplay.ItemStackSlotDisplay(
                            potionStack);
            net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay display =
                    new net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay(
                            java.util.List.of(slot), slot,
                            new net.minecraft.world.item.crafting.display.SlotDisplay.ItemStackSlotDisplay(
                                    new ItemStack(net.minecraft.world.item.Items.BREWING_STAND)));
            ToastManager manager = mc.getToastManager();
            if (manager != null) {
                RecipeToast.addOrUpdate(manager, display);
            }
        } catch (Exception | LinkageError e) {
            BrbeLogger.log("BRBE-RECIPE-PROGRESS", "toast failed: {}", e.toString());
        }
    }

    // ------------------------------------------------------------------
    // 进度完成观察：brbe:brew/material/*（酿造）+ brbe:recipe/*（锻造）
    // ------------------------------------------------------------------

    private static final String BREW_ADVANCEMENT_PREFIX = "brbe:brew/material/";
    private static final String RECIPE_ADVANCEMENT_PREFIX = "brbe:recipe/";

    private static void applyCompletedProgress(Minecraft mc) {
        try {
            Object clientAdvancements = mc.getConnection().getAdvancements();
            if (clientAdvancements == null) return;
            java.lang.reflect.Field progressField = findProgressField(clientAdvancements);
            if (progressField == null) return;
            Object progressMap = progressField.get(clientAdvancements);
            if (!(progressMap instanceof Map<?, ?> map)) return;
            Object tree = clientAdvancements.getClass()
                    .getMethod("getTree").invoke(clientAdvancements);
            for (Object node : (Iterable<?>) tree.getClass().getMethod("nodes").invoke(tree)) {
                Object holder = node.getClass().getMethod("holder").invoke(node);
                Object id = holder.getClass().getMethod("id").invoke(holder);
                if (!(id instanceof Identifier advancementId)) continue;
                String text = advancementId.toString();
                Object progress = map.get(holder);
                if (progress == null) continue;
                if (!(Boolean) progress.getClass().getMethod("isDone").invoke(progress)) continue;
                if (text.startsWith(BREW_ADVANCEMENT_PREFIX)) {
                    applyBrewUnlock(mc, text.substring(BREW_ADVANCEMENT_PREFIX.length()));
                } else if (text.startsWith(RECIPE_ADVANCEMENT_PREFIX)) {
                    String recipeId = text.substring(RECIPE_ADVANCEMENT_PREFIX.length());
                    if (INJECTED_RECIPES.add(recipeId)) {
                        injectRecipeDisplays(mc, recipeId);
                    }
                }
            }
        } catch (Exception | LinkageError e) {
            BrbeLogger.log("BRBE-RECIPE-PROGRESS", "advancement poll failed: {}",
                    e.toString());
        }
    }

    /** advancement 后的部分 = {@code <ns>-<path>}（生成时路径压平）。 */
    private static void applyBrewUnlock(Minecraft mc, String suffix) {
        int dash = suffix.indexOf('-');
        if (dash <= 0) return;
        Identifier material = Identifier.fromNamespaceAndPath(
                suffix.substring(0, dash), suffix.substring(dash + 1));
        if (!MATERIAL_RESULTS.containsKey(material)) return;
        if (KNOWN_MATERIALS.add(material)) {
            for (Identifier resultPotion : MATERIAL_RESULTS.get(material)) {
                toastBrewingUnlock(mc, resultPotion);
            }
            BrbeLogger.log("BRBE-RECIPE-PROGRESS", "advancement-unlocked brewing material: {}", material);
        }
    }

    /** 把锻造配方 display 注入本地配方书（原版 API，与 unlock-all 同款：
     *  单机远程枚举经 server.submit().join()，book.add + rebuildCollections
     *  在渲染线程执行——锻造书/引擎立即可见）。 */
    private static void injectRecipeDisplays(Minecraft mc, String recipeId) {
        try {
            if (mc.getSingleplayerServer() == null) return;
            net.minecraft.resources.ResourceKey<net.minecraft.world.item.crafting.Recipe<?>> key =
                    net.minecraft.resources.ResourceKey.create(
                            net.minecraft.core.registries.Registries.RECIPE,
                            Identifier.parse(recipeId));
            java.util.List<net.minecraft.world.item.crafting.display.RecipeDisplayEntry> displays =
                    new java.util.ArrayList<>();
            mc.getSingleplayerServer().submit(() -> {
                mc.getSingleplayerServer().getRecipeManager()
                        .listDisplaysForRecipe(key, displays::add);
            }).join();
            if (displays.isEmpty()) return;
            net.minecraft.client.ClientRecipeBook book = mc.player.getRecipeBook();
            for (net.minecraft.world.item.crafting.display.RecipeDisplayEntry entry : displays) {
                book.add(entry);
            }
            book.rebuildCollections();
            RecipeViewerIndex.forceNextRebuild();
            BrbeLogger.log("BRBE-RECIPE-PROGRESS", "injected smithing unlock {} ({} displays)",
                    recipeId, displays.size());
        } catch (Exception | LinkageError e) {
            BetterRecipeBook.LOGGER.warn("[BRBE-RECIPE-PROGRESS] inject failed: {}", e.toString());
        }
    }

    private static java.lang.reflect.Field progressFieldCache;
    private static Class<?> progressFieldOwner;

    private static java.lang.reflect.Field findProgressField(Object clientAdvancements) {
        if (progressFieldCache != null && progressFieldOwner == clientAdvancements.getClass()) {
            return progressFieldCache;
        }
        synchronized (RecipeUnlockTracker.class) {
            if (progressFieldCache == null || progressFieldOwner != clientAdvancements.getClass()) {
                try {
                    progressFieldCache = clientAdvancements.getClass()
                            .getDeclaredField("progress");
                    progressFieldCache.setAccessible(true);
                    progressFieldOwner = clientAdvancements.getClass();
                } catch (Exception | LinkageError e) {
                    progressFieldCache = null;
                }
            }
        }
        return progressFieldCache;
    }
}
