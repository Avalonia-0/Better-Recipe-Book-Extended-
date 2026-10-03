package com.alonie.brbe.cache;

import com.alonie.brbe.util.BrbeLogger;
import com.alonie.brbe.util.ModNameUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * 客户端「这条配方来自哪个**数据包**」索引：配方资源 id → 提供它的包（{@code packId()}）。
 *
 * <p>RBIP 的「数据包」标签模式用它把配方按**来源包**归组（原版 / 每个模组自带的数据包 /
 * 每个启用的数据包各一个标签），而不是按配方 id 的命名空间。</p>
 *
 * <h3>为什么只能是单机</h3>
 * <p>包列表只存在于**服务端**（{@code MinecraftServer.getResourceManager()}）。联机时客户端拿不到
 * 服务端装了哪些数据包（网络协议里根本没有这条信息），所以联机一律返回"查不到"——
 * 调用方按单一「服务器」标签兜底（见 {@link #UNKNOWN_KEY} 与 {@link #label(String)}）。</p>
 *
 * <h3>判定方式</h3>
 * <p>一次 {@code ResourceManager.listResources("recipe", …)} 拿到**每个配方资源 → 生效的那份 Resource**，
 * 再取 {@link Resource#sourcePackId()}——覆盖关系（数据包覆盖原版配方）由游戏自己的资源栈判定，
 * 天然正确：被覆盖的那条配方归**覆盖它的包**，不归原版。</p>
 *
 * <p>资源路径规则：配方 id {@code ns:path} → {@code data/<ns>/recipe/<path>.json}
 * （三分支实测目录名都是单数 {@code recipe}）；{@code listResources} 给的键是
 * {@code ns:recipe/<path>.json} —— **带 {@code .json} 后缀**（见 {@link #recipeResource}）。</p>
 */
public final class RecipePackIndex {

    /** 无法判定来源时的兜底分组键（真实包 id 不会以 {@code #} 开头）。 */
    public static final String UNKNOWN_KEY = "#unknown";

    /** 「当前没有可用来源」的哨兵身份（用于节流重试）。 */
    private static final Object NO_SOURCE = new Object();
    private static final long RETRY_INTERVAL_MS = 1_000L;
    private static final Object LOCK = new Object();

    /** 配方资源键（{@code ns:recipe/path.json}，见 {@link #recipeResource}）→ 提供它的包 id。 */
    private static final Map<Identifier, String> PACK_BY_RESOURCE = new HashMap<>();
    /** 同键 → **全部**提供者（资源栈顺序，胜者在最前）。用户 2026-10-02 要求"每个提供者都显示一份"：
     *  同一条配方被多个包覆盖时（实测 Stellarity 与 Incendium 都覆盖 5 条原版红石配方），
     *  数据包档把它**同时**放进每个提供者的标签；展示内容仍以资源栈胜者为准（同一条条目）。 */
    private static final Map<Identifier, java.util.List<String>> PACKS_BY_RESOURCE = new HashMap<>();
    /** 包 id → 显示名（pack.mcmeta 的 name/description；模组数据包 = 模组名）。 */
    private static final Map<String, Component> LABELS = new HashMap<>();
    /** 包 id → 在资源栈里的序号（排序用；栈序 = 原版 → 模组 → 数据包）。 */
    private static final Map<String, Integer> ORDER = new HashMap<>();
    /** 命名空间 → 主要提供者（该命名空间下配方最多的包）；pin 迁移与回退用。 */
    private static final Map<String, String> NAMESPACE_OWNER = new HashMap<>();

    private static Object builtIdentity = NO_SOURCE;
    private static long nextRetryAt;

    private RecipePackIndex() {}

    // ================================================================
    //  查询
    // ================================================================

    /** 该配方来自哪个包；单机之外（或查不到）返回 {@code null}。 */
    public static String packKeyOf(Identifier recipeId) {
        if (recipeId == null) return null;
        ensureFresh();
        synchronized (LOCK) {
            return PACK_BY_RESOURCE.get(recipeResource(recipeId));
        }
    }

    /** 该配方的**全部**提供者（资源栈顺序，胜者在最前）；查不到返回空列表（单机之外亦然）。 */
    public static java.util.List<String> packKeysOf(Identifier recipeId) {
        if (recipeId == null) return java.util.List.of();
        ensureFresh();
        synchronized (LOCK) {
            java.util.List<String> providers = PACKS_BY_RESOURCE.get(recipeResource(recipeId));
            if (providers != null) return providers;
            String single = PACK_BY_RESOURCE.get(recipeResource(recipeId));
            return single == null ? java.util.List.of() : java.util.List.of(single);
        }
    }

    /** 该命名空间下配方最多的包（pin 迁移 / 回退用）；未知返回 {@code null}。 */
    public static String namespaceOwner(String namespace) {
        if (namespace == null) return null;
        ensureFresh();
        synchronized (LOCK) {
            return NAMESPACE_OWNER.get(namespace);
        }
    }

    /** 该字符串是不是当前已知的数据包 id（pin 键迁移用）。 */
    public static boolean isPackKey(String key) {
        if (key == null) return false;
        ensureFresh();
        synchronized (LOCK) {
            return ORDER.containsKey(key) || LABELS.containsKey(key);
        }
    }

    /** 包在资源栈里的序号（越小越靠前）；未知返回 {@link Integer#MAX_VALUE}。 */
    public static int orderOf(String packKey) {
        if (packKey == null || UNKNOWN_KEY.equals(packKey)) return Integer.MAX_VALUE;
        synchronized (LOCK) {
            Integer i = ORDER.get(packKey);
            return i == null ? Integer.MAX_VALUE : i;
        }
    }

    /** 索引是否可用（= 当前是单机且已建成）；联机恒 false。 */
    public static boolean available() {
        ensureFresh();
        synchronized (LOCK) {
            return builtIdentity != NO_SOURCE;
        }
    }

    /**
     * 分组键的显示名（tooltip）：
     * <ul>
     *   <li>{@link #UNKNOWN_KEY}：联机 = 服务器名（取不到就用「服务器」），单机未解析 = 「未知来源」；</li>
     *   <li>原版包：{@code Minecraft}（与「显示物品来源模组」对 minecraft 的叫法一致）；</li>
     *   <li>**模组自带数据包**：**模组声明里的名字优先**（{@code fabric.mod.json} 的 {@code name}，
     *       用户 2026-10-02 修订）；只有查不到该模组时才退回数据包自己的声明。既不再打印 Fabric 的
     *       「Fabric 模组 "XXX"」包装名（2026-10-01），也不会把 Fabric 合成元数据里的裸模组 id
     *       当名字（2026-10-02，实测 {@code naturescompass}）；</li>
     *   <li>**文件型数据包**（{@code file/…}）：作者写在 {@code pack.mcmeta} 的 {@code description}
     *       —— 元数据表没有 name 字段，包列表里的"名字"只是文件名（同样用户 2026-10-01）；</li>
     *   <li>再兜底：包 title / 包 id。详见 {@link #computeLabel(String, Pack)}。</li>
     * </ul>
     */
    public static Component label(String packKey) {
        if (packKey == null || UNKNOWN_KEY.equals(packKey)) {
            Minecraft minecraft = Minecraft.getInstance();
            ServerData server = minecraft == null ? null : minecraft.getCurrentServer();
            if (server != null) {
                String name = server.name;
                return name == null || name.isBlank()
                        ? Component.translatable("brbe.tab.datapack.server")
                        : Component.literal(name);
            }
            return Component.translatable("brbe.tab.datapack.unknown");
        }
        // 原版包**不**取游戏包列表里的标题 —— 那里它叫「默认」/ Default（那是"默认资源包 /
        // 默认数据包"的语境），当标签 tooltip 极易误解。固定叫 Minecraft，与「命名空间」档的
        // minecraft 标签（{@code ModNameUtil.resolveModName("minecraft")} 的兜底）以及
        // 「显示物品来源模组」对 minecraft 的叫法一致（用户 2026-10-01 反馈）。
        if (isVanillaPack(packKey)) return Component.literal("Minecraft");
        ensureFresh();
        synchronized (LOCK) {
            Component label = LABELS.get(packKey);
            if (label != null) return label;
        }
        // 兜底（用户 2026-10-02）：索引里没有这一条时**现场查一次模组声明**，
        // 别把裸包 id 当名字显示（Fabric 合成元数据里就是这么写的）。
        String declaredMod = ModNameUtil.metadataModName(packKey);
        return Component.literal(declaredMod != null ? declaredMod : packKey);
    }

    /** 原版数据包的包 id（就是 {@code vanilla}）；兼容带前缀写法。 */
    private static boolean isVanillaPack(String packKey) {
        return "vanilla".equals(packKey) || packKey.endsWith("/vanilla");
    }

    /** 文件型数据包（放在 {@code datapacks/} 下的 zip / 文件夹）——这些包的 title 只是文件名。 */
    private static boolean isFilePackId(String packKey) {
        return packKey != null && packKey.startsWith("file/");
    }

    private static Component safeTitle(Pack pack) {
        try {
            return pack.getTitle();
        } catch (Exception | LinkageError ignored) {
            return null;        // 个别包元数据异常：忽略
        }
    }

    private static Component safeDescription(Pack pack) {
        try {
            return pack.getDescription();
        } catch (Exception | LinkageError ignored) {
            return null;
        }
    }

    /**
     * 一个包的显示名（tooltip 用）——按用户 2026-10-01 定的优先级：
     *
     * <ol>
     *   <li><b>模组自带数据包</b>（包 id 就是模组 id，Fabric 的 {@code ModNioPackResources} 用
     *       {@code ModMetadata.getId()} 当 PackLocationInfo id）：**数据包自己的声明优先**
     *       —— {@code pack.mcmeta} 的 {@code description}；没有声明才退回**模组声明里的标题**
     *       （{@code fabric.mod.json} 的 {@code name}，即模组菜单里显示的名字）。</li>
     *   <li><b>文件型数据包</b>（{@code file/*.zip} / 文件夹）：{@code title} 只是**文件名**
     *       （{@code PackLocationInfo.title()} = {@code furnicraft-v7.5.zip}），作者真正署名的
     *       项目名写在 {@code pack.mcmeta} 的 {@code description} 里 → 优先用它。</li>
     *   <li>其余（原版除外，原版在 {@link #label(String)} 里已被拦截）：包 {@code title}，
     *       但要是 Fabric 的包装名就掏回裸模组名（见 {@link #unwrapModPackTitle(Component)}）。</li>
     * </ol>
     *
     * <p>为什么必须解包：Fabric 的 {@code ModNioPackResources} 给每个模组数据包起的 title 是
     * {@code Component.translatable("pack.name.fabricMod", 模组名)} —— 中文渲染成
     * 「Fabric 模组 "XXX"」（{@code fabric-resource-loader-v1} 的 lang 实锤）。用户明确要求
     * **直接打印标题**，不要这层包装。</p>
     */
    private static Component computeLabel(String packId, Pack pack) {
        Component title = safeTitle(pack);
        Component description = safeDescription(pack);
        // ① **模组声明优先**（用户 2026-10-02 修订；此前是"数据包自己的声明优先"）。
        //    为什么必须翻过来：Fabric 会给**没有 pack.mcmeta 的模组数据包合成一份 pack.mcmeta**，
        //    其中的名字取自 {@code ModPackResourcesUtil.getName(ModMetadata)} —— 那个方法的字节码是
        //    `if (getId() != null) return Component.literal(getId())`（getMetadataPackJson /
        //    serializeMetadata 就是拿它去合成元数据的），于是这种包的 `description` **就是裸模组 id**
        //    （实测 Nature's Compass 显示成 `naturescompass`）；而有 pack.mcmeta 的模组又常把
        //    宣传语写进 description（"Naturalist Resources"）。两者都不如模组声明里的名字。
        String declaredMod = ModNameUtil.metadataModName(packId);
        if (declaredMod != null) return Component.literal(declaredMod);
        // ② 非模组包（文件型数据包 / 第三方包）：数据包自己的声明（作者署名的项目名）
        if (!isBlank(description) && !packId.equals(description.getString())) return description;
        // ③ 兜底：title（Fabric 包装名 → 掏回裸模组名；与包 id 相同的"占位名"视为无效）
        Component unwrapped = unwrapModPackTitle(title);
        if (unwrapped != null) return unwrapped;
        if (!isBlank(title) && !packId.equals(title.getString())) return title;
        if (!isBlank(description) && !packId.equals(description.getString())) return description;
        return null;    // 全是"包 id 形态"的占位 → 交给 label() 的兜底（那里会再查一次模组声明）
    }

    /** 索引日志用：包 id → 标签文本（TreeMap 保证同一份索引的日志可比对）。 */
    private static java.util.Map<String, String> labelTexts(Map<String, Component> labels) {
        java.util.Map<String, String> out = new java.util.TreeMap<>();
        labels.forEach((key, value) -> out.put(key, value == null ? "<null>" : value.getString()));
        return out;
    }

    /**
     * Fabric 给模组自带（子）数据包起的 title 是 {@code pack.name.fabricMod} /
     * {@code pack.name.fabricMod.subPack} 的翻译组件 —— 渲染出来是「Fabric 模组 "XXX"」。
     * 这两个组件的**第一个参数就是模组名**（{@code ModNioPackResources} 字节码实锤），
     * 直接掏出来用，避免把包装名当标题打印。
     */
    private static Component unwrapModPackTitle(Component title) {
        if (title == null) return null;
        try {
            if (title.getContents() instanceof TranslatableContents contents) {
                String key = contents.getKey();
                if (!"pack.name.fabricMod".equals(key) && !"pack.name.fabricMod.subPack".equals(key)) {
                    return null;
                }
                Object[] args = contents.getArgs();
                if (args != null && args.length > 0 && args[0] != null) {
                    Component name = args[0] instanceof Component c
                            ? c
                            : Component.literal(String.valueOf(args[0]));
                    return isBlank(name) ? null : name;
                }
            }
        } catch (Exception | LinkageError ignored) {
            // 组件结构变了：退回调用方的兜底
        }
        return null;
    }

    private static boolean isBlank(Component component) {
        return component == null || component.getString().isBlank();
    }

    // ================================================================
    //  建索引（单机：集成服务端）
    // ================================================================

    private static void ensureFresh() {
        Minecraft minecraft = Minecraft.getInstance();
        MinecraftServer server = minecraft == null ? null : minecraft.getSingleplayerServer();
        if (server == null) {
            // 联机 / 标题界面：没有包列表可用 → 清空 + 节流重试。
            if (builtIdentity == NO_SOURCE && System.currentTimeMillis() < nextRetryAt) return;
            clear();
            return;
        }
        ResourceManager manager;
        try {
            manager = server.getResourceManager();
        } catch (Exception | LinkageError e) {
            manager = null;
        }
        if (manager == null) return;
        if (Objects.equals(manager, builtIdentity)) return;
        build(server, manager);
    }

    private static void clear() {
        synchronized (LOCK) {
            PACK_BY_RESOURCE.clear();
            PACKS_BY_RESOURCE.clear();
            LABELS.clear();
            ORDER.clear();
            NAMESPACE_OWNER.clear();
        }
        builtIdentity = NO_SOURCE;
        nextRetryAt = System.currentTimeMillis() + RETRY_INTERVAL_MS;
    }

    private static void build(MinecraftServer server, ResourceManager manager) {
        Map<Identifier, String> byResource = new HashMap<>();
        Map<Identifier, java.util.List<String>> packsByResource = new HashMap<>();
        Map<String, Integer> ownerCounts = new HashMap<>();
        Map<String, String> owner = new HashMap<>();
        Map<String, Component> labels = new HashMap<>();
        Map<String, Integer> order = new HashMap<>();
        int recipes = 0;

        // ① 配方资源 → 生效的包（覆盖关系由资源栈判定）。
        try {
            Map<Identifier, Resource> resources = manager.listResources("recipe", id -> true);
            // 全部提供者：Fabric 的 listResourceStacks（同键的整条资源栈，胜者在最前）。
            // 拿不到就退化为"只有胜者"，行为与改动前一致。
            Map<Identifier, java.util.List<Resource>> stacks = null;
            try {
                stacks = manager.listResourceStacks("recipe", id -> true);
            } catch (Throwable ignored) {
                stacks = null;
            }
            for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
                Identifier resourceId = entry.getKey();
                Resource resource = entry.getValue();
                if (resourceId == null || resource == null) continue;
                String packId;
                try {
                    packId = resource.sourcePackId();
                } catch (Exception | LinkageError e) {
                    continue;
                }
                if (packId == null || packId.isEmpty()) continue;
                byResource.put(resourceId, packId);
                java.util.List<String> providers = new java.util.ArrayList<>();
                java.util.List<Resource> stack = stacks == null ? null : stacks.get(resourceId);
                if (stack != null) {
                    for (Resource r : stack) {
                        if (r == null) continue;
                        String pid;
                        try {
                            pid = r.sourcePackId();
                        } catch (Exception | LinkageError e) {
                            continue;
                        }
                        if (pid != null && !pid.isEmpty() && !providers.contains(pid)) providers.add(pid);
                    }
                }
                if (providers.isEmpty()) providers.add(packId);      // 退化路径
                else if (!providers.get(0).equals(packId)) {          // 保证胜者在前
                    providers.remove(packId);
                    providers.add(0, packId);
                }
                packsByResource.put(resourceId, java.util.List.copyOf(providers));
                recipes++;
                String namespace = resourceId.getNamespace();
                int count = ownerCounts.merge(namespace + "|" + packId, 1, Integer::sum);
                String current = owner.get(namespace);
                if (current == null || count > ownerCounts.getOrDefault(namespace + "|" + current, 0)) {
                    owner.put(namespace, packId);
                }
            }
        } catch (Exception | LinkageError e) {
            BrbeLogger.log("BRBE-PACK-INDEX", "listResources(recipe) failed: {}", e.toString());
        }

        // ② 资源栈顺序（排序用）+ 包自带的名字（PackResources.location().title()）。
        try {
            if (manager instanceof MultiPackResourceManager multi) {
                Stream<PackResources> packs = multi.listPacks();
                if (packs != null) {
                    int[] index = {0};
                    packs.forEach(pack -> {
                        if (pack == null) return;
                        String id = pack.packId();
                        if (id != null && !id.isEmpty()) {
                            order.putIfAbsent(id, index[0]);
                            try {
                                if (pack.location() != null && pack.location().title() != null) {
                                    labels.putIfAbsent(id, pack.location().title());
                                }
                            } catch (Exception | LinkageError ignored) {
                                // 元数据缺失：保留 id 兜底
                            }
                        }
                        index[0]++;
                    });
                }
            }
        } catch (Exception | LinkageError e) {
            BrbeLogger.log("BRBE-PACK-INDEX", "listPacks() failed: {}", e.toString());
        }

        // ③ 包仓库的名字（pack.mcmeta 的 description / 模组声明 / 包 title）优先于 ② 的兜底。
        try {
            for (Pack pack : server.getPackRepository().getAvailablePacks()) {
                if (pack == null) continue;
                String id = pack.getId();
                if (id == null || id.isEmpty()) continue;
                Component label = computeLabel(id, pack);
                if (label != null) labels.put(id, label);
            }
        } catch (Exception | LinkageError e) {
            BrbeLogger.log("BRBE-PACK-INDEX", "getAvailablePacks() failed: {}", e.toString());
        }

        Map<Identifier, String> frozenByResource = Map.copyOf(byResource);
        Map<Identifier, java.util.List<String>> frozenPacksByResource = Map.copyOf(packsByResource);
        Map<String, Component> frozenLabels = Map.copyOf(labels);
        Map<String, Integer> frozenOrder = Map.copyOf(order);
        Map<String, String> frozenOwner = Map.copyOf(owner);
        synchronized (LOCK) {
            PACK_BY_RESOURCE.clear();
            PACK_BY_RESOURCE.putAll(frozenByResource);
            PACKS_BY_RESOURCE.clear();
            PACKS_BY_RESOURCE.putAll(frozenPacksByResource);
            LABELS.clear();
            LABELS.putAll(frozenLabels);
            ORDER.clear();
            ORDER.putAll(frozenOrder);
            NAMESPACE_OWNER.clear();
            NAMESPACE_OWNER.putAll(frozenOwner);
        }
        builtIdentity = manager;
        BrbeLogger.log("BRBE-PACK-INDEX",
                "index built: recipe resources={} packs={} namespaces={} labels={}",
                recipes, frozenOrder.size(), frozenOwner.size(), frozenLabels.size());
        BrbeLogger.log("BRBE-PACK-INDEX", "pack order: {}", frozenOrder);
        BrbeLogger.log("BRBE-PACK-INDEX", "labels(text): {}", labelTexts(frozenLabels));
    }

    /**
     * 配方 id → {@code listResources("recipe", …)} 返回映射里的键。
     *
     * <p>键的形态是 {@code ns:<目录>/<路径><扩展名>} —— 由 {@code FileToIdConverter.fileToId}
     * 反编译证实（它做的正是 {@code path.substring(prefix.length() + 1, path.length() - extension.length())}），
     * 即 **带目录前缀、带 {@code .json} 后缀**。少写 {@code .json} 会让每一次查询都 miss，
     * 于是归组静默退化成"命名空间的主要归属包"（2026-10-01 实机 Furnicraft 现象：
     * 数据包放在 {@code minecraft} 命名空间的配方落进原版标签）。</p>
     */
    private static Identifier recipeResource(Identifier recipeId) {
        return Identifier.fromNamespaceAndPath(recipeId.getNamespace(),
                "recipe/" + recipeId.getPath() + ".json");
    }
}
