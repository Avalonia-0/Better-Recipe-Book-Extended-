# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Multi-branch architecture

Each git branch targets a **different Minecraft version** and is built independently:

| Branch    | Minecraft | Java | Loom                              | Mod Loaders      |
|-----------|-----------|------|-----------------------------------|------------------|
| `1.21.1`  | 1.21.1    | 21   | Architectury Loom                 | Fabric + NeoForge |
| `1.21.11` | 1.21.11   | 21   | fabric-loom-remap (`net.fabricmc.fabric-loom-remap` 1.14.6, 单模块) | Fabric |
| `26.2`    | 26.2      | 25   | fabric-loom (`net.fabricmc.fabric-loom`, no-remap) | Fabric |
| `26.3` ← 本分支 | 26.3 | 25 | fabric-loom (`net.fabricmc.fabric-loom` 1.17.18, no-remap) | Fabric |

## 26.3 升级要点（本分支，2026-09-22）

从 26.2 分支完整复制后升级；**实测 API 差异见 [`docs/26.3-api-changes.md`](docs/26.3-api-changes.md)**，
迁移过程记录见 [`docs/26.3-migration-plan.md`](docs/26.3-migration-plan.md)。

**版本基线**：MC 26.3（release 2026-09-15，Java 25）· Fabric Loader 0.19.5 ·
Fabric API 0.161.0+26.3 · Cloth Config 26.3.158 · Fabric Loom 1.17.18（**无需升级**）·
mod_version 2.3 · 真实 JEI 参考版本 31.3.0.17。

**26.3 是大改动版本**，移植时必须注意：
- **GLFW → SDL3**：`org.lwjgl.glfw` 整包消失（改用 `org.lwjgl:lwjgl-sdl`）；
  `InputConstants.isKeyDown(int)` 单参、`Type.KEYSYM`→`KEYBOARD`、`KeyEvent.scancode()`→`keycode()`；
  **MC 不再提供图像光标 API**（只有 `CursorTypes` 标准光标），`ViewerCursor` 的"抓取拳头"
  光标因此失效（回退 `CursorTypes.RESIZE_ALL`）。
- **渲染管线搬进 `renderpearl`**：`RenderPipeline` 在 `com.mojang.renderpearl.api.pipeline`，
  `brbe.common.accesswidener` 里 3 条 blit 描述已同步。
- **`tooltip(...)` 末尾多一个 `boolean`**。
- **`PotionBrewing` 被删除**：酿造改成常规配方 `RecipeType.BREWING`；
  `Level.getRecipeManager()` → `Level.recipeAccess()`。
- **燃料/堆肥从表改成数据组件 + `context_int_provider` 数据包注册表**：
  `util/LootIntResolver` 求结构化期望值，复现旧的燃烧时长与堆肥概率数字。
  ⚠️ 该注册表只在 `RegistryDataLoader.RELOADABLE_REGISTRIES`、**不在 SYNCHRONIZED_REGISTRIES**
  → 客户端/集成服务端都拿不到，实际数值来自生成的内置兜底表 `util/ContextIntProviderFallbacks`
  （详见 2026-09-22（五））。
- **JEI 26.3 把配置系统抽成独立 mod `mezz_config`**（官方 JEI 也是 jar-in-jar 内嵌它）。

**JEI 集成（26.3）**：无头 fork 位于**独立工程 `headless-jei/26.3`**（`headless-jei` 分支），
基线是**官方 JEI 26.3 源码**（不是 26.2 fork 逐文件打补丁），产物内嵌进主 jar。
⚠️ **dev 运行注意**：Fabric Loader **不在 dev 下展开 jar-in-jar**，因此 `build.gradle` 用
`runtimeOnly` 把 headless-jei 与 mezz_config 挂到 dev 运行时；它们同时也在
`src/main/resources/META-INF/jars/` 里供成品 jar 内嵌。

**⚠️ 26.3 移植必查：mixin `@At` 的调用点描述符**（2026-09-22 踩坑）
`@Inject/@Redirect` 的 `at = @At(value="INVOKE", target="Lowner;name(desc)ret")` 里的
描述符是**字符串**，编译期不校验；26.3 换了包/改了名（如 `RenderPipeline` 搬进
`com.mojang.renderpearl.api.pipeline`、`RecipeButton.extractWidgetRenderState` 等）时，
一个都匹配不上 → **该类 class load 时崩**（`Scanned 0 target(s)`），冒烟测试停在标题界面
根本碰不到。移植后用 `tools/verify_mixin_targets.py <src> <mc jar>` 过一遍
（描述符级 + 字节码级，当前 26.3 = 0 问题）；要验某个界面能否构造，用
`tools/brbe-screen-selftest/`（进世界自动开物品栏）。详见
`docs/26.3-mixin-at-descriptor-crash.md`。

**已知降级（26.3）**：
1. `ViewerCursor` 自造光标失效（原因见上，需 `java.lang.foreign` downcall SDL 才能恢复）；
2. `BrbeJeiMinecraftMixin` 已成空操作（26.3 的 MC 自带 `AtlasManager`，JEI 侧
   `JeiAtlasManager`/`Textures.getAtlasManager()` 已删除），注入点保留仅为不动 mixin 注册表，
   可随时连同 `mixins.brbe.json` 条目一起清理；
3. 26.3 无 REI（同 26.2）。

**The root `build.gradle` validates `minecraft_version` against the branch name at configure time** — it will fail with a clear error if they differ. After switching branches, always run `git checkout -- gradle.properties` to restore the correct version.

## Module layout

Single-module fabric project — all sources under `src/main/java` + `src/main/resources` (no Architectury multi-module split).

```
src/main/java/com/alonie/brbe/
  api/                                ←   public interfaces (HudHider, ConfigScreenProvider, book categories)
  impl/hud/                           ←   HudHider implementations (JeiHudHider, ReiHudHider)
  compat/                             ←   cross-mod bridges (OverlayHider, CompatMixinPlugin, ItemViewCompat)
  config/                             ←   Cloth Config data classes (Config, AlternativeRecipes, InstantCraft, Scrolling, NewRecipes)
  generic/                            ←   abstract recipe book component hierarchy (GenericRecipeBookComponent, GenericRecipeButton, GenericRecipePage, etc.)
    pins/                             ←     pinnable recipe collection abstraction (Pinnable, PinnableRecipeCollection)
  search/                             ←   search query parser (SearchQuery, SearchArgument variants: Mod, Tag, Tooltip, Regex, Text, Negated, Compound, Alternative)
  mixins/                             ←   mixins grouped by feature subdirectory
    accessors/                        ←     interface injectors (RecipeBookComponentAccessor, GhostSlotsAccessor, etc.)
      smithing/                       ←       smithing recipe accessors
    pins/                             ←     pinning feature mixins
    instantcraft/                     ←     instant craft mixins
    incompletecrafting/               ←     partial-craftable display mixins
    hideoverlay/                      ←     JEI/REI overlay hiding mixins
    unlockrecipes/                    ←     recipe unlock mixins
    search/                           ←     search mixins
    settings/                         ←     settings button mixins
    centered/                         ←     centered recipe book mixins
    jei/                              ←     JEI integration mixins
    modname/                          ←     mod name tooltip mixins
    ungroup/                          ←     recipe variant ungrouping mixins
    alternativerecipes/               ←     alt recipe overlay button mixins
    incompatibleenvironment/          ←     guard mixins for missing mod compat
    scrollablepages/                  ←     scrollable recipe book pages
    toasts/                           ←     toast suppression mixins
  brewingstand/                       ←   brewing stand recipe book (BrewingRecipeBookComponent, BrewingRecipeCollection, etc.)
  smithingtable/                      ←   smithing table recipe book (SmithingRecipeBookComponent, SmithingRecipeCollection, etc.)
  fabric/                             ←   Fabric entrypoints + platform init
    BetterRecipeBookFabric, BetterRecipeBookClientFabric
    ModMenuReflectiveBridge           ←   reflection-based ModMenu integration
    Mixins/Accessors/                 ←   Fabric-specific accessor mixins (FabricPotionBrewingAccessor)
    compat/jei, compat/rei            ←   JEI/REI integration
  brewingstand/fabric/                ←   PlatformPotionUtilImpl (fabric)
  interfaces/                         ←   cross-cutting interfaces (IPinningComponent, ISettingsButton, TopLayerOverlayProvider, RecipeBookTabButtonIconOffset)
  recipe/                             ←   custom recipe wrappers (BRBSmithingRecipe, etc.)
    smithing/                         ←     smithing transform/trim recipes
  util/                               ←   utility classes (BRBHelper, BRBTextures, ModNameUtil, PartialCraftingUtil, RecipeUnlockUtil, TopLayerOverlayRenderer, etc.)
  widget/                             ←   custom widgets (StateSwitchingButton)
  loaders/                            ←   PotionLoader (registers potion recipes from data packs)

src/main/resources/
  fabric.mod.json                     ←   mod metadata (entrypoints, mixins)
  mixins.brbe.json                    ←   platform mixins (FabricPotionBrewingAccessor)
  mixins.brbe-common.json             ←   all cross-cutting BRBE mixins
  mixins.brbe-common-compat.json      ←   conditional compat mixins
  mixins.brbe-jei-common.json         ←   JEI overlay mixins
  mixins.brbe-rei-common.json         ←   REI overlay mixins
  recipe-book-is-pain-extended.mixins.json ← RBIP mixins
  brbe.common.accesswidener           ←   access widener (not declared in fabric.mod.json — inert resource)
  assets/brbe/                        ←   lang, textures, icon.png
  resourcepacks/brbe_unique_dark/     ←   built-in resource pack (registered in BetterRecipeBookClientFabric)
```

## Core architecture patterns

### No Architectury API dependency
No Architectury API anywhere. The single-module build uses official `net.fabricmc.fabric-loom` (1.17.18, LoomNoRemap — Minecraft 26.1+ is unobfuscated, Mojang mappings are final and no remap is needed). Platform code uses native Fabric API directly.

### Mixin configuration split
There are **six** mixin config files, all in `src/main/resources`:
- `mixins.brbe.json` — platform-mixin configs (Fabric's `FabricPotionBrewingAccessor`).
- `mixins.brbe-common.json` — all cross-cutting BRBE mixins (required: true).
- `mixins.brbe-common-compat.json` — conditional compat mixins (required: false, with `CompatMixinPlugin` that checks FabricLoader.isModLoaded). Current compat: mousewheelie.
- `mixins.brbe-jei-common.json` / `mixins.brbe-rei-common.json` — JEI / REI overlay mixins (required: false).
- `recipe-book-is-pain-extended.mixins.json` — RBIP mixins.

### Config system
Uses **Cloth Config** (`me.shedaniel.autoconfig`) with TOML serialization. Config is gated by runtime availability — the `AutoConfig.register()` call is wrapped in try-catch. The config POJO lives at `com.alonie.brbe.config.Config` with nested sub-configs for feature groups (AlternativeRecipes, InstantCraft, Scrolling, NewRecipes, RecipeBookIsPain).

### Search query system (`com.alonie.brbe.search`)
Implements a mini query language with `|` (OR), space (AND), `-` (negation), `@mod` (mod search), `$tag` (tag search), `#tooltip` (tooltip search), `r/regex/` (regex), and quoted strings. `SearchQuery.parse()` builds an `AlternativeArgument` tree of `SearchArgument` nodes.

### Brewing & Smithing recipe books
The mod adds **non-vanilla** recipe book screens for brewing stands and smithing tables. Each has its own component/collection/recipe classes under `brewingstand/` and `smithingtable/`. These are separate from the generic recipe book base classes.

### Platform potion utilities
Potion brewing is platform-dependent (`PotionBrewing.Mix` is package-private). Fabric implements `PlatformPotionUtil` via reflection-based accessors in `src/main/java/com/alonie/brbe/brewingstand/fabric/PlatformPotionUtilImpl.java`.

## Build commands

### JEI 插件收集代码（已并入本目录，无需外部工程）

原独立工程 `jei-plugins/`（独立 git 分支/worktree）已并入本目录源码树，直接编译，**不再需要先构建任何外部工程**：

- `src/main/java/com/alonie/brbe/jei/` —— 插件收集逻辑（`plugins/` `engine/` `loader/` `stub/`），入口 `BrbeJeiPluginsClientFabric`
- `src/main/java/mezz/jei/api/` —— vendored JEI API fork（主 jar 内嵌，`fabric.mod.json` 无 `breaks: jei`，与真实 JEI 共存）；运行时若真实 JEI 存在则直接依赖它

```bash
./gradlew build        # 默认构建：内嵌 vendored mezz.jei.api
```

### 常规构建

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk sh gradlew build   # full build (single module)
./gradlew compileJava                 # compile-only check
./gradlew runClient                   # launch Fabric dev client
./gradlew clean build                 # full clean rebuild

# Cache corruption recovery (after branch switches)
./gradlew cleanLoomCache && rm -rf .gradle && ./gradlew build

# Deploy (build JAR → copy to test instance)
cp build/libs/brbe-ava-fabric-26.3-2.3.1.jar /home/avalonia/data/MinecraftLib/versions/26.3-Fabric/mods/
```

Test instance path rule: `/home/avalonia/data/MinecraftLib/versions/{GAME_VERSION}-{MOD_LOADER}/mods/` (`MOD_LOADER` capitalized: `Fabric`). 构建完必须部署；部署前将实例内同版本 JAR 备份为 `*.jar.bak.YYYYMMDD`。

⚠️ **部署用原子替换，实例运行中也可安全部署**：先 `cp` 到 `mods/` 下的临时文件，再 `mv` 改名到目标（同目录 rename 是原子操作）——正在运行的会话其 zip 句柄指向旧 inode、内容完好，新启动的会话加载新 jar。**禁止 `cp` 直写覆盖目标 jar**：同 inode 截断重写会让正在运行的会话 zip 读取损坏（典型症状 `java.util.zip.ZipException: ZipFile invalid LOC header (bad signature)`，启动后运行途中随机崩溃——2026-08-25 20:47 部署时实例正开，20:35 启动的会话在 20:47 渲染时读 brbe jar 的类失败即此因）。

```bash
# 原子替换部署（实例运行中也安全）
cp build/libs/brbe-ava-fabric-26.3-2.3.1.jar /home/avalonia/data/MinecraftLib/versions/26.3-Fabric/mods/.brbe-deploy.tmp && mv /home/avalonia/data/MinecraftLib/versions/26.3-Fabric/mods/.brbe-deploy.tmp /home/avalonia/data/MinecraftLib/versions/26.3-Fabric/mods/brbe-ava-fabric-26.3-2.3.1.jar
```

## Config features and their gates

| Config field | Effect | Gate location |
|-------------|--------|---------------|
| `hideReiJeiOverlay` | Hides JEI/REI overlays | `OverlayHider.setOverlaysHidden()` → iterates `HudHider` registry |
| `showAllRecipesInSurvival` | When **false**, skips ALL partial-material injection (vanilla-only) | `incompletecrafting/RecipeBookComponentMixin.keepPartiallyCraftable` |
| `enableRecipeBookIsPain` | Enables RBIP creative-mode tabs | Hidden from GUI (`@ConfigEntry.Gui.Excluded`), edited in `brbe.toml` |
| `partialCraftingEnabled` | Shows partially craftable recipes when "Show Craftable Only" is on | `incompletecrafting/` mixins |
| `partialMarkingEnabled` | Visually marks partial recipes | `incompletecrafting/RecipeButtonMixin` |
| `enablePinning` | Pin/favourite recipes | `PinnedRecipeManager` + `pins/` mixins |
| `instantCraft.enabled` | Shift-click instant craft (auto-move result to inventory) | `InstantCraftingManager` + `instantcraft/` mixins |
| `alternativeRecipes.noGrouped` | Ungroup recipe variants into separate buttons | `ungroup/RecipeBookComponentMixin` |
| `keepCentered` | Keep recipe book centered on screen | `centered/RecipeBookComponentMixin` |
| `showModName` | Display mod namespace on recipe tooltips | `modname/RecipeButtonMixin` + `modname/GhostRecipeTooltipMixin` |
| `scrolling.enabled` | Confine scroll to recipe book area | `MouseScrollHandler` + `scrollablepages/RecipeBookPageMixin` |
| `pageFlipVolume` | Page-flip sound volume slider added to vanilla Sound Options screen (0.0–1.0, default 1.0 = vanilla). Value in `brbe.toml`, not options.txt | `soundoptions/SoundOptionsScreenMixin` + `scrollablepages/RecipeBookPageMixin` |
| `newRecipes` | Badge/indicator for newly unlocked recipes | `NewRecipes` config class |
| `pinyinSearch` | 拼音搜索：搜索栏输入拼音匹配中文物品名（如 `mutou`→木头）。默认关；游戏语言为中文时每次启动自动开启。数据为打包的 Unihan 字表（`assets/zzzbrbe/search/pinyin.txt`），算法移植自 REI（MIT） | `search/TextArgument` + `search/PinyinMatcher`；启动自动开启在 `fabric/BetterRecipeBookClientFabric` 的 `ClientLifecycleEvents.CLIENT_STARTED`（entrypoint 阶段 `options` 为 null） |

## RBIP (Recipe Book is Pain) module

- Lives in `src/main/java/com/alonie/recipebookispain_extended/` in the single-module source tree. Uses its own package (`com.alonie.recipebookispain_extended`).
- Own mixin config: `recipe-book-is-pain-extended.mixins.json` (Fabric)
- Platform init: Fabric → `RBIPFabricEntrypoint`
- Config bridged through `RecipeBookIsPainExtendedConfig.enabled()` → reads `brbe.toml [rbip]`
- KeyMappings and key events: Fabric → `RBIPFabricEntrypoint`
- If `enableRecipeBookIsPain` is off, RBIP is a no-op (constructor saves `vanillaTabInfos`, all methods guard on `enabled()`)
- Polymer compat: optional support for Polymer virtual items via `PolymerCompat` (checks `isModLoaded("polymer")`)

## HudHider API (refactored from OverlayHider)

`OverlayHider` is now a thin registry. New implementations implement `api/hud/HudHider`:

```java
OverlayHider.register(new JeiHudHider());  // JEI IClientToggleState bridge (reflection)
OverlayHider.register(new ReiHudHider());  // REI ConfigObject bridge (reflection)
```

Each hider owns its own state (snapshot, guard flags). Adding a new HUD mod only requires implementing the interface + one registration call. Overlay hide state is enforced on every client tick when a screen is open and the config toggle is active.

## Important gotchas

- **26.1+ is unobfuscated** — Mojang official mappings are the final names, no remap needed. The build uses `net.fabricmc.fabric-loom` 1.17.18 (`LoomNoRemapGradlePlugin`). Intermediary-based mods (like ModMenu) cannot be directly included as compile dependencies. ModMenuFabric integration is done reflectively via `ModMenuReflectiveBridge`.
- **JEI 已可用（headless fork，26.3 重建）**：编译参考 `libs/headless-jei-fabric-26.3-1.0.0.jar`（独立工程 `headless-jei/26.3`，基线为官方 JEI 26.3 源码），jar-in-jar 内嵌 headless-jei + mezz_config；与真实 JEI 31.3.0.17 共存（检测到 `jei` 已加载即跳过内嵌核心）。`libs/jei-26.3-fabric-31.3.0.17.jar` 保留作为 API 对照。**REI 仍不可用**。
- **Cloth Config for 26.3 is bundled as a separate mod** (not jar-in-jar). The config registration is wrapped in try-catch; if Cloth Config is absent, the mod still runs with default values.
- **No test suite.** Validation is manual via `runClient` tasks or deploying to a test instance.
- **Pinned recipes are stored in a JSON file** (`brbe.pins` in the game directory), not in NBT or config.
- **BrewingRecipeBookComponent and SmithingRecipeBookComponent** are concrete implementations that sit alongside (not as subclasses of) GenericRecipeBookComponent — they share some interfaces but have their own rendering and event handling.

## 2026-08-21 二轮同步（26.2 ↔ 1.21.11）

与 1.21.11 分支的零碎特性互相同步（编译验证通过）：

- `CollectionPipeline` 改用 `hasPartialMaterialsEvenIfStale` / `isPartiallyCraftableEvenIfStale`（分代推进后不误过滤部分可合成集合）
- RBIP `ClientRecipeBookMixin` 补 `RecipeBookIsPainExtendedConfig.enabled()` 守卫
- 清理调试日志：`GenericGhostRecipe`（保留 `showModName` 功能）、`incompletecrafting/RecipeButtonMixin` 的 [BRBE-DIAG] 日志块
- 删除无引用死代码：`config/Config.java`（配置已统一到 `BrbeConfig`）、`book/`（RecipeBook 门面从未接线）
- `mixins.brbe.json` 移除非法尾逗号

**2026-08-21 深夜（二）：pin/viewer 配方状态基于真实物品栏**——`PartialCraftingUtil.realInventorySlots()` 替代屏幕容器槽位（创造模式虚拟物品不再算材料），涉及 `PinOverlayManager.refreshRecipeStates`、`PinOverlay.create/refreshRecipeState`、`RecipeViewerOverlay` 两处 `prepareForViewer`。与 1.21.11 同步。
- **常规检索空间统一**（2026-08-21，与 1.21.11 同步）：`PartialCraftingUtil.searchSpaceSlots()` 为配方状态判定唯一槽位来源（真实物品栏 + 合成网格，排除结果栏），carried 参数计入、offhand 内部计入；craftable 走 `fillSearchSpaceStackedContents`。配方书/pin/viewer/幽灵浮层/诊断全部统一。
- **预览/pin 残缺红罩**（2026-08-21，与 1.21.11 同步）：残缺配方状态下界面本体盖整块红罩（`0x60FF3333`）。曾两度尝试按槽位标记/挖洞后按用户要求回退，保持整块红罩。

**2026-08-22 早间（两分支同步，4 项）**：
- **燃料 tooltip 补齐图标**（`RecipeViewerOverlay`）：标题行加燃料物品图标（复用 `TitleWithIconTooltipComponent`，与其他类别一致）；三行子类别（熔炉/鼓风炉/烟熏炉）的工作站图标改用 `workstationsIconsForPrefix(stationCategoryPrefix(i))` 聚合查询——JEI 插件注册的 mod 工作站（如 BetterEnd 末地石冶炼炉注册为 blasting）现在显示在对应行上（原 `stationIcons(i)` 只取内建代表，已删除 `stationIcons`/`furnaceWorkstation` 死代码）
- **ESC 不关闭 pin 界面**（`PinOverlayManager.handleEscape`）：ESC 只关闭查询 viewer，pin 只能按预览键（默认 A）关闭或随宿主界面关闭；`topmostPin` 死代码删除
- **工作站 usage 查询架构修复**（`RecipeViewerIndex.rebuildEngine`）：工作站 items 按 typeId 聚合（builtin+config+external 全部条目的 fallbackIcons 合并进引擎 stationItems），此前 external 与 builtin 共享 typeId（如 `minecraft:blasting`）时引擎索引只含 builtin 条目 → 查询 mod 工作站（BetterEnd `end_stone_smelter` 注册为 blasting catalyst）usage 时 viewer 打开但 0 对象。修复后：任何注册工作站块的 usage 查询都返回整个 type（JEI 语义）
- **合成器（crafter）加入合成类别工作站**（`BUILTIN_WORKSTATIONS` CRAFTING 条目 items 加 `minecraft:crafter`）：usage 查询合成器显示全部合成配方；`recipeFitsScreen` 的 `crafting_` → `AbstractCraftingMenu` 路径不受影响（CrafterMenu 不继承 AbstractCraftingMenu，crafter 界面无配方书放置，仅查询语义生效）

**2026-08-22 早间（二）：JEI 插件类别数据源配方书驱动（两分支同步）**——带配方书的 mod（如 Farmer's Delight 厨锅）的 JEI 插件类别，其配方数据源**自动跟随配方书解锁状态**，不写死任何 mod 路径。初版按 RecipeBookCategory 判定（recipeBookCategoryIds），实机发现 JEI `registerRecipes` 收集在此环境不可靠（FD 用 Fabric `SynchronizedRecipes` 传 RecipeHolder，且配方同步晚于收集时机）→ **重构为 known craftingStation 归属**（最终实现）：
- 归属：`PluginRecipeIndexer` 遍历 `RecipeViewerIndex.knownEntries()`（配方书已解锁条目），仅取 **mod 配方书类别**（category 的 id namespace ≠ minecraft）的条目，解析其 display 声明的 `craftingStation()`（FD cooking 配方的 display 自带厨锅 `ItemSlotDisplay(COOKING_POT)`），用 catalysts 反查（`typeUidForStation`）归属到 JEI type，注册 type（数据源 = known 解锁子集，stations = catalysts）
- 时序：在 JEI 全量注册之后执行（配方书数据优先）；known 重建 → rebuildEngine → 重建监听器 → collectAndInject 重新收集，解锁变化动态跟随；mod 自动解锁 → known 全量 → 全部显示
- 无匹配（纯 JEI 类别如 BetterEnd infusion）→ 保持 JEI 全量路径；vanilla 类别条目被排除（归 rebuildEngine 管，且防止经 mod 的 crafting_table catalyst 误归属）
- **无配方书体系的工作站按原路径显示**（2026-08-22 修正）：曾加"零解锁隐藏"（RecipeBookCategory namespace 级判定），实机发现 **bclib 注册了 RecipeBookCategory（AlloyingRecipe 返回 ALLOYING_CATEGORY）但无配方书 UI** → bclib anvils/alloying、betterend infusion 全部被误隐藏。已移除该逻辑：**RecipeBookCategory 注册 ≠ 有配方书体系**；唯一权威信号是 known 本身（bclib anvils 的条目从不进 known）。无配方书类型走 JEI 全量原路径；配方书驱动（known 归属）在解锁后覆盖引擎数据
- 已知环境问题（未修）：26.2 实例 FD `registerRecipes` 的 recipes 为空（fabric 配方同步晚于收集时机），JEI 全量路径对 mod type 全部 0 可索引——配方书驱动路径不受影响
- `RecipeViewerIndex` 新增 public `knownEntries()` / `resolveCraftingStation(RecipeDisplayEntry)` / `toIndexed(RecipeDisplayEntry)`；`RecipeViewerEngine` 新增 `isVanillaType(String)`

**2026-08-22 凌晨：隐藏无配方书工作站所属的对象（两分支同步）**——新配置项 `hideNoRecipeBookStationObjects`（默认关、无 tooltip、GUI 标题"隐藏无配方书工作站所属的对象"，位于"启用BRBE的查询功能"下方；7 语言翻译键 `text.autoconfig.zzzbrbe.option.hideNoRecipeBookStationObjects`）：
- 语义：开启后，查询结果中**所有工作站都没有配方书体系**的对象被隐藏；若对象还包含有配方书体系的工作站则保留对象本身，仅 **tooltip 隐藏非法工作站图标**
- 判定数据：`RecipeViewerEngine.RECIPE_BOOK_STATION_ITEMS`——每次 JEI 收集重建 = vanilla 类型全部工作站（`RecipeViewerIndex.vanillaWorkstationItems()`，含注册到 vanilla type 的 external 站如 end_stone_smelter）+ 配方书驱动 mod 类型的 stations（bookDriven）；`isRecipeBookStation(ItemStack)` 查询
- 过滤点（`RecipeViewerOverlay`）：`open`/`switchCategory` 的查询结果过 `filterByRecipeBookStations`（内置类别 furnace/crafting/stonecutting/smithing/fuel 的对象恒合法——`isBuiltinCategory`）；`stationIconsTooltipComponents` 图标过滤（过滤后空则省略图标行）
- 隐藏模式下 **fallback 到 JEI 也被抑制**（BRBE 无法判定的对象不泄漏给外部 viewer）；过滤后空 → viewer 不打开

**2026-08-22 凌晨（五）：类别级隐藏（对象全隐藏则标签隐藏，两分支同步）**——开启"隐藏无配方书工作站所属的对象"后，若某类别的**全部对象**都被过滤（如 bclib anvils 类别对象全属非法站），其类别标签（tab）也隐藏：
- `RecipeViewerOverlay.computeHiddenCategoryIds`：遍历 RecipeViewerCategories.all() 的 PluginRecipeViewerCategory（内置类别/燃料类别豁免），逐对象 `entryHasRecipeBookStation`（stationIconsFor 优先、display craftingStation 兜底）→ 全非法 → 隐藏
- `visibleCategories` 过滤 hidden 类别（与既有 hasContent 过滤叠加）；结果缓存（cachedHiddenCategoryIds），失效时机：插件重收集（`RecipeViewerCategories.markVisibilityDirty`，PluginRecipeIndexer 调用）或开关状态变化
- `PluginRecipeViewerCategory.uids()` getter 新增；`hasRecipeBookStation` 重构复用 `entryHasRecipeBookStation`

**2026-08-22 下午：杂项配置类别 + 隐藏配置界面 Tips（两分支同步）**——新增 Cloth 配置类别 `miscellaneous`（翻译"杂项"），下含开关 `hideConfigTips`（标题"隐藏配置界面的Tips"，tooltip"就是'实用功能'页面那个每次打开配置界面都会变化的Tips。"，默认关）：
- `ConfigTipsHelper.addCarousels` 开头守卫 `BetterRecipeBook.config.hideConfigTips`（开启则不再向"实用功能"类别注入轮循提示行）
- 翻译键：`text.autoconfig.zzzbrbe.category.miscellaneous` / `option.hideConfigTips` / `option.hideConfigTips.@Tooltip`（7 语言）
- 已部署两实例（备份 20260822-143214）

**2026-08-22 傍晚：FD cooking shift 预览空白修复（两分支同步）**——用户反馈（1.21.11）U 查询厨锅后按 Shift 预览显示的是 crafting_overlay_highlighted.png 放大背景且无任何物品。根因：FD 的 cooking 配方是自定义 display（CookingPotRecipeDisplay），vanilla 按钮槽位为空 → PopupRenderer/PopupGeometry 的 crafting 分支无槽位可渲染。
修复：PopupRenderer.renderSlotItems / PopupGeometry.vanilla 的 crafting 分支在 slots 为空时回退**通用条目布局**（`renderGenericCrafting`/`genericCraftingSlots`）——`entry.craftingRequirements()` 输入铺 3x2 网格（间距 5）+ 结果右上（17,2），按 selIdx 循环变体；命中区域与渲染一致。

**2026-08-22 深夜：bookDriven 条目挂接 JEI 完整渲染（两分支同步）**——用户反馈"只显示物品没用，JEI界面没法显示"：开发"隐藏无配方书工作站"前 cooking 数据是 JEI 全量 synthetic 条目（弹窗走 SyntheticRecipeRenderer 委托真实 JEI 渲染完整 UI）；bookDriven 覆盖后变成 known 条目（无 layout/RenderEntry）→ 弹窗只剩物品网格。
修复：`PluginRecipeIndexer` JEI 全量循环收集 `RenderCandidate`（layout+products+category+recipe），bookDriven 注册后**按结果物品匹配**给 known 条目 `registerLayout` + `RENDER_ENTRIES`；`PopupGeometry.of` 的 adapted 判定去掉 isSynthetic（canRender 已够）；`PopupGeometry.vanilla()`/`PopupRenderer.renderSlotItems` 的 synthetic 分支放宽为 `getLayout(id) != null`（无 JEI 时也按 native 槽位渲染，背景回退 vanilla sprite）。
效果：U 查询厨锅 → Shift 预览/pin 显示 FD 完整 JEI UI（cooking_pot.png 背景 + 原生布局 + 厨锅槽），由真实 JEI 的 createRecipeLayoutDrawable 绘制。

**2026-08-22 深夜（二）：cooking 弹窗/pin 尺寸损坏修复（两分支同步）**——用户反馈厨锅预览/pin 界面"尺寸异常"（截图：巨大面板占屏 59%）。根因：上一轮把 RenderEntry/layout 挂到 known 条目后，`PopupGeometry.of` 已对这些条目走 adapted 几何（约 105x53 面板），但 `PopupRenderer.renderRecipePopup` 的 **JEI 委托分支仍带 `isSynthetic(id)` 守卫** → known 条目（非 synthetic）被挡在委托外 → 走 renderVanillaPopup（按钮 24x24 矩形）→ FD 背景纹理与 layout 槽位坐标在 24x24 内绘制 → 尺寸/位置错乱（pin 同路径）。
修复：委托条件去掉 `isSynthetic(id)`（`canRender` 已检查 renderEntry+layout，known 匹配条目同样委托真实 JEI 完整 UI，几何与渲染一致）。

**2026-08-22 深夜（三）：点击 RBIP 标签自动翻页修复（两分支同步）**——用户反馈：点击某些配方书标签时 RBIP 标签栏会自己翻页，且不是每个标签都这样。根因：`RecipeBookComponentMixin.brbe$restoreTabPosition`（注入原版 `onTabButtonPress` 尾部，原版点标签只走 replaceSelected+updateCollections、不调 updateTabs）会恢复该标签"记住"的 RBIP 标签栏页码（`rbip$setPage`）。被点击的标签必然在当前页可见，而记忆在标签选中期间每帧跟随当前页码——选中某标签后翻过页再点回它，标签栏就会翻到旧页码，甚至把刚点击的标签翻出可视区；只有"记住页码 ≠ 当前页码"的标签才触发，故时有时无。
修复：点击标签时不再恢复 RBIP 标签栏页码（仅保留重开配方书 `brbe$restorePosition` 的页码恢复与每标签配方区页码记忆）；标签栏页码只由翻页按钮/滚轮与重开配方书改变。涉及文件：`mixins/recipebookposition/RecipeBookComponentMixin.java`（javadoc 同步更新）。已编译、已部署两实例（备份 20260822-194457）。

**2026-08-22 深夜（四）：无 JEI 时 cooking 预览错乱修复（两分支同步，7206e4d3）**——用户反馈禁用 JEI（BRBE 依赖内嵌 mezz fork）后厨锅预览重现"尺寸异常"。根因：无 JEI 时 `SyntheticRecipeRenderers` 为 NONE（`isModLoaded("jei")` 守卫不注册）→ canRender 恒 false → 委托分支不可达；但 known/synthetic 条目的 layout（收集时由 `DataOnlyLayoutBuilder` 注册，不依赖真实 JEI）仍存在 → `PopupGeometry.vanilla()` 的 layout-attached 分支（背景纹理几何）与 `PopupRenderer.renderSlotItems` 的 renderSynthetic、`resolveBackdrop` 的 JEI 背景纹理仍被启用 → 几何与渲染错位（纹理渲染中心 x+12 vs 几何中心 x-12，差 24px）→ 厨锅预览面板错乱（同 2026-08-22 深夜二症状，当时有 JEI、委托被 isSynthetic 守卫挡住）。
修复：三处 layout-attached 分支（`PopupGeometry.vanilla` / `PopupRenderer.renderSlotItems` / `resolveBackdrop`）加 `SyntheticRecipeRenderers.get() != SyntheticRecipeRenderer.NONE` 条件——无 JEI 时 cooking 回退通用条目布局（3x2 输入 + 结果，48x48 面板，背景回退 vanilla sprite，与 2026-08-22 傍晚修复一致）；有 JEI 行为不变。已部署两实例（26.2 备份 20260822-201808、1.21.11 备份 20260822-201813）。

**2026-08-25：配置保存后残缺配方变可合成纹理修复（两分支同步）**——用户反馈：开关"隐藏无配方书工作站所属的对象"（实际是**任意配置保存**）后，配方书里残缺配方丢失红罩、显示成可合成配方纹理，拿起物品后才恢复；R/U 查询系统（viewer）正常。
- 根因三环相扣：① 每次配置保存无条件发布 `PartialCraftingChanged` → `PartialCraftingUtil.invalidateCaches()` 用 `tagger.clearAll()` **清空全部残缺标记**（tags + checked 记录）；② 配置保存触发的配方书刷新中，物品栏没变 → `RecipeCraftingIndex.shouldSkip` 跳过 `selectRecipes` → craftable 集合**残留上一轮注入的 partial ID**；③ FULL-PASS 的 Step0（撤销旧注入）依赖 tagger 的 EvenIfStale 查询——tags 已被清空 → 无法撤销残留注入 → `markPartialMaterials` 把这些 ID 当可合成跳过（不重标记）→ 无红罩。渲染时 isCraftable=true + partial=false → 与可合成配方完全同纹理。
- "拿起物品修复"机制：槽位变化 → `Inventory.getTimesChanged` 变化 → `selectRecipes` 重算（changedItems 非空不跳过）→ 残留注入清除 → 重新标记。viewer 正常是因为每次打开创建新集合（无残留注入）且 partial 用打开时快照。
- 修复：`invalidateCaches()` 改用 `tagger.clearCheckedGenerations()`（**保留 tags**，只清 checked 标记）→ Step0 能用 EvenIfStale 撤销旧注入 → 重标记正常。与 `RecipeCollectionTagger.clearCheckedGenerations` 的文档语义（保留 tag 供 cleanup 用）一致。涉及文件：`util/PartialCraftingUtil.java`。已部署两实例、两分支验证通过。

**2026-08-25（二）：红色幽灵物品红底加强 + 轮循槽位红罩判定修复（两分支同步）**——用户反馈合成台缺材料幽灵物品红色不明显；另外轮循幽灵槽位（如"任意颜色羊毛"）只按 items 列表第一个物品判定红罩是否移除，导致玩家拥有其他颜色时红罩没除、且随显示物品轮循闪烁。
- 红底加强：`incompletecrafting/GhostSlotsMixin` 拦到的缺材料槽位红底 `0x30FF0000`（alpha 0x30≈19%）调高为 `0x66FF0000`（≈40%），红色更明显；白罩 `0x30FFFFFF` 不动。
- 轮循槽位判定：`util/PartialGhostOverlayUtil` 的 `resolveGhostItem`（只取 items 列表第一个）改为 `findOwnedItem`（遍历列表，玩家拥有**任意一个**可放置物品即视为拥有 → 整格移除红罩并扣减一个对应物品）。修正"中心任一种羊毛 + 八个木棍"这类配方：居中槽只要玩家拥有任一种颜色的羊毛，整格就移除红罩。
- 两分支同步（26.2 / 1.21.11），均已构建部署（备份 20260825）。**1.21.1 不改**：其使用旧版 `GhostRecipe`/`GhostIngredient`（`getItem()` 也按时间轮循、会闪烁），因需另加 accessor（运行时 Yarn 字段名），按用户要求暂缓。

**2026-08-25（三）：RBIP 标签 pin 标记补画上下旋转条带（两分支同步）**——用户反馈：pin 住的创造标签排到配方书首页，但落到上侧/下侧旋转条带上的标签不显示 pin 贴图。
- 根因（两处）：① `RecipeGroupButtonMixin.rbip$drawPinMarker` 带 `placement != NORMAL return` 守卫（当初认为旋转条带坐标是旋转锚点、不适用）；② 旋转条带的 `extractContents` 被 HEAD 注入接管并整体取消，RETURN 注入的 pin 绘制对旋转标签根本不会执行（并非仅位置错了）。
- 修复：抽出 `rbip$drawTabPin`（正常朝向标签仍借 extractIcon RETURN 绘制）；旋转条带在 `rbip$drawRotatedButton` 图标之后补画。位置按用户规定取**最终屏幕位置**——pin 在 90° 旋转矩阵之外以绝对坐标 blit（x/y 即最终呈现位置）：上侧标签 pin 悬在标签**左上角**（锚点 x-4, y-4，与正常朝向一致）；下侧标签 pin 悬在标签**左下角**（锚点 x-4, y+RBIP_ROTATED_TAB_HEIGHT-5 = y+30，pin 图形（精灵图左上 (6,2)-(12,7)）悬出标签底边 2px，与顶边悬出 2px 镜像对称）。
- 1.21.1 无 TabPinManager（无标签 pin 功能），不改。已编译、已部署两实例（备份 20260825-164338）。

**2026-08-25（四）：RBIP 标签 pin 位置微调（两分支同步）**——用户实测反馈两处微调（`rbip$drawTabPin`）：
- 下侧 pin 上移 5px：锚点从 `y+ROTATED_H-5`（pin 悬出底边 2px）改为 `y+ROTATED_H-10`（pin 底部距标签底边 3px）。
- 选中偏移：选中 pin 标签时 pin 随标签选中方向移动 1px——左/上/下侧分别为向左/上/下（依据已有 `selected` 字段，与图标选中偏移同源）。
- 已编译、已部署两实例（备份 20260825-165114）。

**2026-08-25（五）：RBIP 上下侧 pin 贴图右移 3px（26.2 / 1.21.11）**——用户最初要求"上侧与下侧标签整体右移 3px"，实机确认后澄清为**只移动 pin 贴图**（标签本体不动）：
- 最终实现：`rbip$drawTabPin` 对 TOP / BOTTOM 放置的 pinX 再 +3（锚点 x-4 → x-1），NORMAL 不变；`getHorizontalTabStartX` 保持原样（曾临时 +3 实现"标签右移"，已回退）。
- 1.21.1 无 pin 功能：只回退条带布局，不新增偏移（已重新构建部署原布局）。已编译、已部署四实例（备份 20260825-165946）。

**2026-08-25（六）：下侧 pin 贴图改放左上角（26.2 / 1.21.11）**——用户要求下侧标签 pin 与上侧统一放左上角：
- `rbip$drawTabPin`：BOTTOM 的 pinY 由 `y+ROTATED_H-10`（左下角）改为 `y-4`（左上角），与 TOP 同锚点 (x-1, y-4)（含右移 3px）；选中偏移方向不变（下侧仍随标签向下 1px）。
- 已编译、已部署两实例（备份 20260825-170402）。

**2026-08-25（七）：下侧 pin 贴图下移 6px（26.2 / 1.21.11）**——用户要求下侧 pin 在上侧左上角锚点基础上再下移 6px：
- `rbip$drawTabPin`：BOTTOM 的 pinY 由 `y-4` 改为 `y+2`（锚点 (x-1, y+2)）；TOP / NORMAL 不变；选中偏移方向不变。
- 已编译、已部署两实例（备份 20260825-170712）。

**2026-08-25（八）：残缺配方 tip 文案追加"灵感源自基岩版"（三分支同步）**——用户要求 `zzzbrbe.gui.tip.3`（配置界面"实用功能"提示）中文文案末尾追加"，灵感源自基岩版"，其他语言同步追加各自译法（en/ja/pl/ru/tr/zh_tw）：
- 三个维护分支的 lang 文件（26.2 / 1.21.11：`assets/zzzbrbe/lang/*.json` 键 `zzzbrbe.gui.tip.3`；1.21.1：`assets/brbe/lang/*.json` 键 `brb.gui.tip.3`）共 21 个文件，各自追加：en "Inspired by Bedrock Edition."、ja "Bedrock Edition から着想を得ています。"、pl "Inspirowane edycją Bedrock."、ru "Вдохновлено Bedrock Edition."、tr "Bedrock Edition'dan ilham alınmıştır."、zh_cn "，灵感源自基岩版。"、zh_tw "，靈感源自基岩版。"。26.1.2 停维不改。
- 已编译、已部署四实例（备份 20260825-171439）。

**2026-08-25（九）：拼音搜索 tooltip 移除 REI 出处（26.2 / 1.21.11）**——用户要求 `text.autoconfig.zzzbrbe.option.pinyinSearch.@Tooltip` 中文文案移除"，灵感来自REI"，其他语言同步移除各自 REI 出处（en "Inspired by REI."、zh_tw "，靈感來自REI。"）：
- 每分支 3 个文件（zh_cn / zh_tw / en_us）共 6 个文件；ja/pl/ru/tr 无该键（回退 en_us），1.21.1 无拼音功能，26.1.2 停维，均不改。
- 已编译、已部署两实例（备份 20260825-171911）。

**2026-08-25（十）：内置资源包 Unique Dark Lite 修复（26.2 / 1.21.11）**——用户反馈"unique dark lite 兼容材质包在游戏中找不到了"。根因（两处叠加，为 efffb7b8 全量移植（mod id brbe→zzzbrbe）时的遗漏）：
- ① 注册路径不匹配：`ResourceLoader.registerBuiltinPack("zzzbrbe:zzzbrbe_unique_dark",...)` 但 JAR 内目录仍是 `resourcepacks/brbe_unique_dark` → Fabric 找不到包 → 资源包列表无 "Unique Dark Lite ✕ BRBE"；（cd9c7e1d 单模块化时注册名/目录名均为 brbe 匹配，efffb7b8 只改了注册名，目录未同步改名——回归点）
- ② 包内容命名空间未迁移：包内文件仍为 `assets/brbe/...`（老命名空间），而 26.2/1.21.11 的 mod 资源全部在 `assets/zzzbrbe/...`（sprite id `zzzbrbe:recipe_book/*`、`PageAnimationEdges` 读 `zzzbrbe:animation/edge_width.json`）→ 即使包加载也无任何覆盖效果（1.21.11 上即此状态：目录名匹配能列出但无效果）。
- 修复：26.2 目录改名 `brbe_unique_dark`→`zzzbrbe_unique_dark`（git mv）；两分支包内 `assets/brbe`→`assets/zzzbrbe`；1.21.11 包补 `animation/edge_width.json`（左0右0，与 26.2 一致）。1.21.1 全链路本就正确（brbe 命名空间、目录名、pack_format 34），未改。pack.mcmeta 格式值（26.2 [88,0] / 1.21.11 [75,0]）未变（版本未升级，此前可用）。
- 已编译、已部署两实例（备份 20260825-172941）。如进游戏后包仍显示"不兼容"警告，需重取两版本的 RESOURCE_PACK_FORMAT 数值。

**2026-08-25（十一）：内置资源包显示名改为 "Unique Dark - Lite ✕ BRBE"（三分支同步）**——用户要求在资源包名的 Lite 前加 "- "：
- 四处注册（26.2 / 1.21.11 的 `BetterRecipeBookClientFabric`、1.21.1 的 fabric + neoforge）显示名 `"Unique Dark Lite "` → `"Unique Dark - Lite "`；包 id / 目录名不变（激活状态不失效）。
- 已编译、已部署四实例（备份 20260825-173541）。

**2026-08-25（十二）：查询 viewer 切石/锻造类别预览换成完整 JEI 界面（两分支同步）**——用户要求"查询合成/用途"（R/U viewer）中切石机与锻造台类别的预览弹窗换成 JEI 样式（此前是原版固定双槽布局：弹窗内只有输入+产物两个 0.6 缩放小图标，锻造台连模板/附加材料都不显示）：
- 实现路径（与 bookDriven/mod 类别同一套"委托真实 JEI 渲染"机制）：`PluginRecipeIndexer` 新增 `attachVanillaCategoryLayouts` pass——取 JEI manager 已注册的原版 `minecraft:stonecutting` / `minecraft:smithing` 类别（headless 内嵌核心与真实 JEI 都会注册），把引擎中这两类条目按 **display 值相等**匹配回服务器同步的 `RecipeHolder`（数据源 `mezz.jei.common.Internal.getClientSyncedRecipes()`，即 headless 核心/真实 JEI 共用的同步配方 map），用原版 JEI 类别的 `setRecipe` 跑 `DataOnlyLayoutBuilder` 注册 native layout + `RENDER_ENTRIES` → 弹窗/pin 走 `SyntheticRecipeRendererImpl` 委托 `createRecipeLayoutDrawable` 绘制完整 JEI UI（JEI 槽位底 + 箭头 + 单配方背景；切石 82x34 输入槽→箭头→输出槽；锻造 108x28 模板/基底/附加槽→箭头→输出槽）
- 退化保护：无 JEI runtime / 同步配方 map 为空 / display 无匹配 holder 的条目保持原版固定双槽预览（不崩溃、不空白）；1.21.11 用 `Internal.getClientSyncedRecipes`（fabric-recipe-api 8.x 无 `FabricRecipeAccess`），26.2 同步改用同一数据源（原 FabricRecipeAccess 版已弃用重写）
- `PopupRenderer.renderRecipePopup` 委托分支成功后补画残缺配方红罩（`0x60FF3333`，仅非 crafting 模式，即切石/锻造；cooking 等 crafting 委托不变）——与原版弹窗的红罩视觉一致
- 已编译、已部署两实例（备份 20260825-180052，两实例同秒）。验证：R/U 查询某物品 → 切石/锻造类别 → Shift 悬停配方按钮/pin 应为完整 JEI 配方 UI

**2026-08-25（十三）：查询 viewer 修正：切石机按无配方书工作站处理 + 空站类别不再吞掉燃料对象（两分支同步）**——用户反馈两项：
- **切石机 = 无配方书工作站**（vanilla 的 StonecutterMenu/Screen 均非配方书体系，BRBE 也未添加），开启"隐藏无配方书工作站所属的对象"后查询引擎应屏蔽切石机：
  - `RecipeViewerIndex.BUILTIN_WORKSTATIONS`：切石机工作站 `recipeBook` true→false（注释说明缘由——此前当作有配方书的内置站，过滤无法生效）
  - `RecipeViewerOverlay.isBuiltinCategory`：豁免列表移除 `stonecutting`（内置类别豁免的假设"内置类 = 配方书体系"对切石机不成立）
  - **1.21.11 补上 26.2 已有的源级排除**（`workstations()` 在过滤开启时按 recipeBook 过滤——1.21.11 此前缺失，其 PluginRecipeViewerCategory 注释还声称存在该过滤）：26.2 / 1.21.11 现在行为一致——过滤开启时切石机从整个查询系统源级移除：U 查询切石机不打开 viewer（也不走 JEI 回退），R 查询石头等材料时切石类别被过滤、落到有内容的其他类别
- **锻造台无配方时燃料对象消失**：U 查询锻造台（可作熔炉燃料）且已知锻造配方为空时，旧逻辑 `defaultFor` 在站类别循环里落到"空 firstMatch"直接返回 → `open()` 空命中 → 回退到外部 viewer（JEI），燃料类别不再显示。修复（`RecipeViewerCategories.defaultFor`）：站类别全部无内容时不立即返回空 firstMatch，改先取"按 priority 最高的有内容类别"（燃料类别 priority 2，如锻造台是燃料时胜出）；`RecipeViewerOverlay.open()` 另加防御性 `bestContentCategory` 重选（分类命中被过滤清空时改开有内容的其他类别，而非直接回退/关闭）
- 已编译、已部署两实例（备份 20260825-183612（两实例同秒））。验证：① 开"隐藏无配方书工作站所属的对象"→ U 查询切石机应打不开（R 查询石头应只显示有配方书的工作站类别）；② 新存档/锻造配方未解锁时 U 查询锻造台 → 打开 BRBE viewer 且显示"烧炼燃料"类别（锻造台作为燃料）

**2026-08-25（十四）：查询 viewer 锻造/切石配方中的空占位符修复（两分支同步）**——用户反馈"查询锻造台用途时，许多锻造台配方中还夹杂着很多空的占位符（没有任何信息）"：
- 根因：本地缓存（`VanillaRecipeCache` + `CacheableRecipeDisplayEntry`，`unlockAll=true` 时注入 known）对锻造/切石配方只存了"仅结果"的兜底 shapeless display（`fromJson` 注释原为"smithing: ingredients not needed, just result"；stonecutter 走 default 分支）→ 注入后引擎把它们当锻造/切石条目，但 `asSmithing`/`asStonecutter`（instanceof 判定）为 null → 按钮/弹窗渲染出"无任何信息"的空白占位。服务器同步的真实锻造条目显示正常，故现象是"夹杂"空占位符
- 修复一（数据正确性）：`CacheableRecipeDisplayEntry` 新增 template/base/addition 字段（`VanillaRecipeLoader.extractSmithingSlot` 解析 `template`/`base`/`addition` JSON 字段），toEntry 为锻造配方重建真实 `SmithingRecipeDisplay(template, base, addition, result, station)`；切石配方重建 `StonecutterRecipeDisplay(input, result, station)`。附带收益：这些条目与服务器同步 holder 的 display 值相等 → 自动被 `attachVanillaCategoryLayouts` 匹配 → 预览走完整 JEI UI
- 修复二（渲染兜底）：`PopupRenderer.renderFixedPair` / `PopupGeometry.fixedPairSlots` 对 display 类型不匹配的条目回退到通用条目布局（`renderGenericCrafting`/`genericCraftingSlots`），按钮/弹窗至少显示产物与材料网格，不再空白
- 已编译、已部署两实例（备份 20260825-184251）。验证：U 查询锻造台/切石机 → 列表中的配方按钮应全部有图标（模板/基底/附加/产物），Shift 预览/pin 为完整 JEI UI；不再有空白占位

**2026-08-25（十五）：空占位符根因修复——纹饰配方缓存产物为空 + 锻造 layout 收集 NPE（两分支同步，十四的补完）**——上条修复后用户反馈问题一依旧（查询 viewer 与锻造台配方书都有空气占位符，点击能加载幽灵物品，怀疑是错误重复对象）。结合实例最新日志（`smithing=91` 几乎全为服务器条目；`vanilla minecraft:smithing: 38 switched` 但 70 条 `failed to attach ... NPE: ContextMap.getOptional ... context is null`；`injected(complement): 18 cached, 0 filtered`）实锤两个根因：
- **根因一（重复空占位符）**：26.2 原版纹饰配方（`minecraft:smithing_trim`）JSON **没有 `result` 字段**（产物由 `pattern` 字段派生，display 结果是 `SlotDisplay.SmithingTrimDemoSlotDisplay`）。旧缓存逻辑：`extractResultItem` 读不到 result → 纹饰缓存条目 resultItem=null → ① complement 按结果物品去重**永远不生效**（每 16 个原版纹饰条目被重复注入）；② 注入校验按 `resultItem != null` 门控 → 不校验直接注入 → 上一轮修复把条目标成带 `SmithingRecipeDisplay` 的"真"条目后，**锻造台配方书不再跳过它们**（isTrimRecipe 判定 `result() instanceof SmithingTrimDemoSlotDisplay` 失败 → 按 transform 处理）→ 产物空 → 按钮空白 + 点击仍加载模板/基底/附加幽灵物品（"错误的重复对象"）——完全吻合用户现象
- **根因二（NPE）**：`SmithingCategoryExtension.setOutput` 调 `ingredientAcceptor.getContextMap()` 解析输出；`DataOnlyLayoutBuilder`/`DataOnlySlotBuilder` 的 `getContextMap()` 恒返 null → 70 条锻造条目布局收集 NPE → 无 JEI UI
- 修复：
  - `CacheableRecipeDisplayEntry` 新增 `pattern`（trimPattern）字段；`fromJson` 读 `pattern`；`toEntry` 的 smithing_trim 分支构建与 `SmithingTrimRecipe.display()` 一致的结构：`SmithingRecipeDisplay(template, base, addition, SmithingTrimDemoSlotDisplay(base, addition, patternHolder), station)`（registry `Registries.TRIM_PATTERN` 解析 holder，解析失败返回 null → 条目过滤）
  - `VanillaRecipeCache.collectServerResultItems` 增加 demo display 的 pattern 去重键（`trim:<pattern-id>`），`injectEntries` 对 trimPattern 命中跳过 → **纹饰条目不再重复注入**；"服务器无配方"模式下仍全量注入（数据完整）
  - `DataOnlyLayoutBuilder` 构造时构建 level-backed `SlotDisplayContext`，`DataOnlySlotBuilder` 透传该 context → 锻造扩展 setOutput 正常解析 → 全部锻造条目挂接 JEI layout（日志应为 `vanilla minecraft:smithing: 91-ish switched`，不再有 failed）
- 已编译、已部署两实例（备份 20260825-190302）。验证：U 查询锻造台 → 列表无空白占位、无重复纹饰条目；锻造台配方书 → 纹饰/升级标签页均正常（纹饰按钮显示带纹饰的护甲样本，点击放置幽灵正常）

**2026-08-25（十六）：查询 viewer 补齐剩余 JEI 类别——铁砧/酿造/研磨完整 JEI UI + 堆肥/信息纯信息行（两分支同步）**——用户要求剩余 JEI 类别接入查询 viewer：
- **铁砧/酿造/研磨（anvil/brewing/grindstone）**：这三个类别是 JEI vanilla 插件的**运行时构建配方**（无 datapack holder、无配方书条目），数据源 = JEI manager（`manager.createRecipeLookup(type).get()`，headless 内嵌核心与真实 JEI 均注册）。`PluginRecipeIndexer` 新增 `indexVanillaPluginTypes()` pass：抽出原全量循环的逐配方索引为共享 `indexPluginRecipe()`（多出 `renderOnlyAsOutput` 开关——原版研磨的输出槽声明为 RENDER_ONLY，需计入产物），三种类别的配方跑原版 JEI 类别的 `setRecipe` → synthetic 条目 + native layout + `RENDER_ENTRIES` → 弹窗/pin 由 `SyntheticRecipeRendererImpl` 委托完整 JEI UI（铁砧 125x38 槽位背景+加号+箭头+经验消耗文本；酿造 114x61 酿造台背景+气泡+箭头+酿造步数；研磨 73x52 双输入+箭头+XP 奖励文本），**不是** vanilla 固定双槽回退
- **新内置 viewer 类别**（`recipeviewer/`）：`AnvilRecipeCategory`("anvil")/`BrewingRecipeCategory`("brewing")/`GrindstoneRecipeCategory`("grindstone")（engine 类型 `minecraft:anvil`/`brewing`/`grindstone`，priority 1，`appliesToMenu` Anvil/BrewingStand/GrindstoneMenu 各归位）+ `CompostRecipeCategory`("compost") + `InfoRecipeCategory`("info")
- **堆肥/信息 = 纯信息行类别**（与燃料同款网格）：`RecipeViewerCategory` 新增 `isGridCategory()`/`gridItems()`，燃料类别也标记为 grid；`RecipeViewerOverlay` 泛化 `isFuelMode` → `isGridMode`（drawItemGrid/rebuildGrid/computeGridBoxSize/gridHoverStack），网格 tooltip 按类别分派 `gridTooltipComponents`：燃料烧炼行 / 堆肥"概率：25%"（`floor(chance*100)`，数据源 `ComposterBlock.COMPOSTABLES`——JEI CompostingRecipeMaker 同源，无 JEI 也工作）/ 信息页文案行（`jei:information` recipes 的 `IJeiIngredientInfoRecipe.description`，经 `Language.getVisualOrder` 渲染，无 JEI 时类别自动缺席）。堆肥/信息 priority 2/0（信息最后兜底，不抢配方类别的默认 tab）
- **工作站注册**：`RecipeViewerIndex.BUILTIN_WORKSTATIONS` 新增 anvil（三变体）/brewing_stand/grindstone/composter 条目（`recipeBook=false`，与切石机同列——都是无配方书工作站）；Family 枚举加 ANVIL/BREWING/GRINDSTONE/COMPOSTING
- **"隐藏无配方书工作站所属的对象"语义**：anvil/brewing/grindstone 与切石机完全一致——过滤开启时 `indexVanillaPluginTypes` 直接 `RecipeViewerEngine.clearType()` 源级移除（否则 defaultFor 会绕过过滤打开类别）；堆肥/信息与燃料一致豁免（信息表，非工作站对象）。`PinOverlay`/`PinButtonRenderOverride` 新增 `MODE_ANVIL/BREWING/GRINDSTONE`（弹窗/pin 残缺红罩与非 crafting 模式一致），`viewerMode()`/`RecipePopupLayer.computeMode`/`PinOverlayManager.modeFor`/createPin 统一走 `RecipeViewerOverlay.viewerMode()`
- 语言键（en/zh_cn/zh_tw；ja/pl/ru/tr 回退 en_us）：`zzzbrbe.category.anvil`(铁砧)/`brewing`(酿造)/`grindstone`(研磨)/`compost`(堆肥)/`info`(信息) + `zzzbrbe.category.compost.chance`("概率：%s%%"/"Chance: %s%%"/"機率：%s%%")
- 已编译、已部署两实例（备份 20260825-202550）。验证：① U 查询铁砧/酿造台/研磨石 → 各类别打开，Shift 预览与 A 键 pin 为完整 JEI UI；② U 查询可堆肥物品（如小麦）→ "堆肥"类别，悬停单元格 tooltip 显示"概率：25%"；③ U 查询有 JEI 信息文案的物品 → "信息"类别显示文案；④ 开"隐藏无配方书工作站所属的对象"→ U 查询铁砧/酿造台/研磨石/堆肥桶不应打开（查询材料时这些类别被过滤，与切石机一致）

**2026-08-25（十七）：预览/pin 完整以原始 1:1 大小显示（两分支同步）**——用户要求"预览界面能完整在 tooltip 中以原始大小显示"。此前委托渲染的预览是把类别**塞进 24px 按钮再放大**（`PopupGeometry.adaptedSynthetic` 的 `min(24/w, 24/h) * CONTENT_ZOOM`）：铁砧 125x38 这种宽布局反而被缩到 ~85%（文字模糊、不像 JEI 原界面），窄布局则被放大。
- 修复（`PopupGeometry.adaptedSynthetic` + 新 `originalSizeFit`）：改以类别布局**原始 1:1 像素尺寸**显示（铁砧就是 125x38、酿造 114x61、研磨 73x52、烧炼 82x54…，与 JEI 自己配方页面完全一致），面板 = 内容 + 9-slice padding；仅当布局超出屏幕（实践中是极端大的 mod 类别）才缩到窗口 80% 以内，保证**完整**显示
- 另加面板**屏幕钳位**：预览以 24px 按钮居中展开，按钮贴近屏幕边缘时面板将越界——现随面板平移内容原点（渲染坐标与命中体积同步平移，不会漂移）
- 命中/排除/工具提示全部跟随同一几何（itemAt/itemUnderMouse/JEI 排除区域自动 1:1）；pin 走同一 PopupGeometry → pin 也 1:1。无 JEI 的 native-layout 兜底（`vanilla()` 分支）保持原按钮适配缩放（仅 JEI runtime 缺席时生效），`CONTENT_ZOOM` 注释同步更新
- 已编译、已部署两实例（备份 20260825-204424）。验证：Shift 悬停任意配方按钮 → 预览面板 = JEI 原始尺寸的完整界面；A 键 pin 大小一致；铁砧预览应显著大于之前（133x46 面板，含经验消耗文字）

**2026-08-25（十八）：查询对象 tooltip 内嵌完整预览界面（不按 Shift，两分支同步）**——用户澄清需求：将预览界面嵌入查询对象的 **tooltip**（不按 Shift 时），放在**模组名行的上面**（此前理解成了 Shift 弹窗的尺寸问题）。
- 新增 `render/RecipePreviewTooltipComponent`（26.2 用 `extractText/extractImage`，1.21.11 用 `renderText/renderImage`——两分支 ClientTooltipComponent 接口名不同）：一个 tooltip 行组件，尺寸 = 预览面板（layout 原始尺寸 + 9-slice padding），`extractImage`/`renderImage` 居中后直接委托 `SyntheticRecipeRenderers.render`（与 Shift 弹窗同一绘制：JEI drawable 1:1 + 面板背景），残缺配方时补非 crafting 模式红罩；不经过 PopupGeometry 的屏幕钳位（tooltip 自身有位置管理）
- `RecipeViewerOverlay.renderDetailedRecipeTooltip` 重构：原 public 方法（pin tooltip 用）保留（无内嵌预览——pin 本身就是预览界面）；新增 viewer 按钮 hover 专用重载传 `slots/craftable/partial`，在**工作站图标行之后、模组名行之前**插入：空行 → 预览面板 → 空行；仅 `canRender(id)`（有 JEI 委托 + 布局）的条目嵌入，vanilla 无布局条目（合成/烧炼等）维持原文本 tooltip
- craftable/partial 取自视图集合（`overlay.getRecipeCollection()`，`isCraftable` + `isViewerPartial || isPartiallyCraftableEvenIfStale`，与 RecipePopupLayer 判定一致）
- 已编译、已部署两实例（备份 20260825-210237，部署前已确认无游戏实例运行）。验证：RvU 查询任意对象（铁砧/酿造/研磨/切石/锻造/厨锅…）→ **不按 Shift** 悬停配方按钮 → tooltip 中"物品名 + 工作站行 + **完整 JEI 预览界面（原始大小）** + 模组名"；Shift 弹窗行为不变；pin tooltip 不嵌预览

**2026-08-25（十九）：tooltip 内嵌预览位置修正 + 合成/烧炼接入 + 顺序调整（两分支同步）**——用户反馈：① 预览位置太靠下超出 tooltip 背景；② 合成/烧炼（BRBE 定制界面）没接入；③ 决定把预览放在**工作站列表的上面**。
- **位置根因**（反编译 26.2 `GuiGraphicsExtractor.tooltip` 证实）：`extractImage/renderImage` 的 `width`/`height` 参数是**整个 tooltip 的尺寸**（宽=所有行最大宽、高=各行高之和），不是当前行——此前按"行高"垂直居中把预览推下去了。修复：垂直直接以 `y`（行起点）为顶，仅水平居中
- **合成/烧炼接入**：`RecipePreviewTooltipComponent` 不再限 `canRender`——有 JEI 委托 + 布局 → JEI UI 1:1（layout+2*PADDING 尺寸）；否则走 `PopupRenderer.renderRecipePopup` vanilla 弹窗渲染（48×48，合成 3×3 网格 / 烧炼料槽+火焰+结果，与 Shift 弹窗完全一致）；嵌入条件放宽为所有 viewer 对象
- **顺序调整**（用户指令：预览放工作站列表上）：tooltip 行序 = 物品名+图标 → 空行 → **预览界面** → 空行 → 工作站图标行（烧炼为料槽/经验行）→ 模组名（预览仍在模组名上方）
- 已编译、已部署两实例（备份 20260825-212141；部署前确认无游戏实例运行）。验证：重进游戏后悬停任意对象（不按 Shift）→ 预览完整位于 tooltip 背景内、在工作站图标行上方；合成/烧炼显示 48×48 BRBE 定制预览

**2026-08-25（二十）：tooltip 内嵌预览尺寸修正——合成/烧炼 96px 超界 + JEI 界面缩小 40%（两分支同步）**——用户反馈：① 合成/烧炼预览 UI 尺寸太大超出 tooltip；② tooltip 里的 JEI 界面要缩小 40%。
- **根因一（合成/烧炼 96×96）**：`renderVanillaPopup` 的 hover 路径自带 2× 缩放变换（作用于传入的"按钮矩形"），此前把 48×48 组件矩形当按钮传入 → 48 的 sprite 再放大 2× → 超出。修复：vanilla 兜底调用改传**居中的 24×24 按钮矩形**（`px+12, py+12, 24, 24`），由弹窗自身的 2× 变换放大到恰好 48×48 组件区域（几何原点同步验证：ox/oy = 组件左上角，面板居中）
- **根因二（JEI 40% 缩小）**：`RecipePreviewTooltipComponent` 新增 `TOOLTIP_SCALE = 0.6f`（仅 tooltip 内嵌场景；Shift 弹窗/pin 仍 1:1）——delegated 内容按 `layout × 0.6` 绘制（render 内部 fit 沿用），面板尺寸同步 = `round(layout×0.6) + 2×PADDING`（铁砧 125×38 → 83×31 面板、酿造 114×61 → 76×45、研磨 73×52 → 52×39…）
- 已编译、已部署两实例（备份 20260825-213045；部署前确认无游戏实例运行）。验证：悬停合成/烧炼 → 48×48 预览完整在 tooltip 内；悬停 JEI 类别 → 预览为原尺寸 60%（约为之前 6 成大小），仍在工作站图标行上方

**2026-08-25（二十一）：tooltip 内嵌预览改居左放置（两分支同步）**——用户要求预览在 tooltip 里靠左而不是居中：`RecipePreviewTooltipComponent` 的 `px = x + (width - this.width)/2`（水平居中）改为 `px = x`（左缘与 tooltip 内容左缘对齐），垂直仍为行顶。已编译、已部署两实例（备份 20260825-214xxx；部署前确认无游戏实例运行）。验证：悬停任意对象（不按 Shift）→ 预览紧贴 tooltip 左侧，尺寸不变（JEI 60%、合成/烧炼 48×48），仍在工作站图标行上方

**2026-08-25（二十二）：移除 tooltip 内嵌预览邻近的空行（两分支同步）**——用户要求删掉预览上下的两个空行：`RecipeViewerOverlay` 的 `embedPreview` 块只保留预览组件（原为 空行+预览+空行）。行序变 = 物品名+图标 → 预览界面 → 工作站图标行（烧炼为料槽/经验行）→ 模组名。已编译、已部署两实例（备份 20260825-220xxx；部署前确认无游戏实例运行）

**2026-08-25（二十三）：Shift 预览时 JEI 界面物品停止轮循修复（两分支同步）**——用户反馈：JEI 插件通用实现中按住 Shift 进行预览时，JEI 界面里的物品不轮循了（pin 住后可以轮循）。
- 根因：**不是 BRBE 代码 bug，而是与 JEI 键位冲突**——JEI 的"暂停配方轮循"快捷键（`key.jei.pauseRecipeCycling`）默认绑定 **LEFT_SHIFT**（vendored `mezz/jei/gui/config/InternalKeyMappings`；实例 options.txt 实测 `key_key.jei.pauseRecipeCycling:key.keyboard.left.shift`）。BRBE 委托渲染的 JEI drawable 由 `SyntheticRecipeRendererImpl` 以 20Hz 调 `drawable.tick()` → `RecipeLayout.tick()` → `CycleTicker.tick()`；后者检测到暂停键按下（用户正好按住 Shift 预览）直接返回 false → 变体索引不再推进 → 物品不轮循；pin（A 键）时不按 Shift → 轮循正常
- 修复：vendored（BRBE fork）`mezz/jei/library/gui/ingredients/CycleTicker.tick()` 与 `CycleTimer.getCycled()` 移除暂停键检查（fork 注释标注 [BRBE fork] 及原因，未来更新 fork 勿恢复）——BRBE 的 Shift 是预览键，预览期间必须持续轮循；JEI 原生"按 Shift 暂停轮循"为冷门功能且与 BRBE 键位冲突。26.2 / 1.21.11 同步修改（4 文件）。`mezz/jei/common/input/IInternalKeyMappings` 与 `InternalKeyMappings` 的键位定义未动（真实 JEI GUI 键位提示仍显示）
- 已编译、已部署两实例（备份 20260825-215958；部署前确认无游戏实例运行）。验证：R/U 查询 → Shift 悬停预览（铁砧/酿造/研磨/切石/锻造等 JEI 类别）→ 界面物品应持续轮循（约 1s 一变体）；A 键 pin、tooltip 内嵌预览行为不变

**2026-08-25（二十四）：预览展开只认左 Shift（右 Shift 无反应，两分支同步）**——用户要求"只有左 Shift 能展开预览界面，右 Shift 无反应"：`ClientCompat.isShiftDown()`（`KEY_LSHIFT || KEY_RSHIFT`）改为 `isLeftShiftDown()`（仅 `KEY_LSHIFT`），调用点同步改名：
- `RecipeViewerOverlay.render`（查询 viewer 的 Shift 弹窗触发/关闭信号）
- `OverlayRecipeButtonMixin.extractWidgetRenderState`（配方书 hover 时 Shift → 4× 放大预览；右 Shift 现在保持普通 2× hover 放大）
- `PinOverlayManager`（pin 内物品 tooltip 的 Shift 门控，随之只认左 Shift）
- 不动：`event.hasShiftDown()`（放置配方的 shift+点击/即时合成语义，非预览）与 vanilla shift-click；`ItemViewCompat`/instantcraft 等非预览路径未改
- 已编译、已部署两实例（备份 20260825-22xxxx；部署前确认无游戏实例运行）。验证：① 按左 Shift 悬停配方按钮 → 预览展开（viewer 弹窗/配方书 4×）；② 按右 Shift 悬停 → 不展开（无弹窗，配方书保持普通 hover 放大）；③ 右 Shift + 点击放置配方等原版语义不受影响

**2026-08-25（二十五）：JEI 暂停轮循改由右 Shift 触发（两分支同步）**——用户反馈"JEI 的暂停轮循应该可以由右 Shift 触发"（延续（二十三）的暂停键冲突与（二十四）的左右 Shift 分工）：恢复暂停功能但绑定到**右 Shift**——左 Shift = BRBE 预览展开（轮循继续），右 Shift = 暂停轮循：
- `mezz/jei/library/gui/ingredients/CycleTicker.tick()` / `CycleTimer.getCycled()`：恢复暂停检查，但改为**直接读取 GLFW 的 `KEY_RSHIFT`**（`InputConstants.isKeyDown`）——不依赖 JEI 的 KeyMapping 状态/options.txt 绑定，因此有/无真实 JEI 运行时、任何已存键位绑定下行为一致（左 Shift 预览永不停帧；右 Shift 按下即冻结变体）
- `mezz/jei/gui/config/InternalKeyMappings.pauseRecipeCycling`：默认键 `GLFW_KEY_LEFT_SHIFT` → `GLFW_KEY_RIGHT_SHIFT`（键位列表/tooltip 显示与行为一致）；26.2 实例 `options.txt` 的旧绑定 `key_key.jei.pauseRecipeCycling:key.keyboard.left.shift` 已同步改为 `key.keyboard.right.shift`（游戏未运行时编辑；1.21.11 headless 无该键行，无需）
- 效果：按住右 Shift → JEI 界面物品轮循冻结（暂停）；松开恢复；按住左 Shift 预览 → 轮循照常进行
- 已编译、已部署两实例（备份 20260825-23xxxx；部署前确认无游戏实例运行）。验证：① 左 Shift 悬停预览 → 物品持续轮循；② 右 Shift 悬停（同时不展开预览）→ 物品定格；③ 松开右 Shift → 恢复轮循

**2026-08-25（二十六）：左右 Shift 都可展开预览，仅左 Shift 不锁定轮循（两分支同步）**——用户修正（二十四/二十五）的方向："希望左右 shift 都能展开预览界面，只是左 shift 不锁定轮循物品"：
- 预览展开判定恢复为**任一 Shift**：`ClientCompat.isLeftShiftDown()`（仅 KEY_LSHIFT）改回 `isShiftDown()`（`KEY_LSHIFT || KEY_RSHIFT`），调用点（`RecipeViewerOverlay.render` / `OverlayRecipeButtonMixin` / `PinOverlayManager`）改回原名——左/右 Shift 悬停均展开预览（viewer 弹窗 / 配方书 4× 放大）
- 轮循暂停保持（二十五）语义：vendored `CycleTicker.tick()` / `CycleTimer.getCycled()` 仍只读 GLFW `KEY_RSHIFT`——**右 Shift 按下 = 物品轮循冻结**，左 Shift 预览不冻结；`InternalKeyMappings.pauseRecipeCycling`（默认 RIGHT_SHIFT）与 26.2 实例 options.txt 的 right.shift 绑定不变
- 已编译、已部署两实例（备份 20260825-24xxxx；部署前确认无游戏实例运行）。验证：① 左 Shift 悬停 → 预览展开、物品持续轮循；② 右 Shift 悬停 → 预览展开、物品定格（暂停轮循）；③ 松开 → 恢复轮循

**2026-08-25（二十七）：烧炼等自研前端弹窗物品命中区域偏移修复（两分支同步）**——用户反馈"烧炼类别（自研前端）的物品鼠标判定区域不准确，有偏移"：
- 根因：`PopupRenderer.scaledItem`（烧炼/切石/锻造固定双槽与 generic 通用条目布局的小图标绘制）以 `translate(tx,ty)` 为**图标左上角**、0.6× 缩放 16px 图标（无中心平移，对照 crafting 网格分支有 `translate(-8,-8)`）；但 `PopupGeometry.fixedPairSlots`/`genericCraftingSlots` 的命中 Slot 直接把 (2,2)/(12,7)/grid 坐标当作**图标中心** → 命中区域整体偏左上 **4.8 内容像素**（2× 弹窗 = ~9.6 屏幕像素）。命中圆（±5）覆盖图标左上象限，其余部分悬停无响应
- 修复：`PopupGeometry` 新增 `ICON_HALF = 0.6f * 16f / 2f`（=4.8），`addSlot` 与 `genericCraftingSlots` 的所有 Slot 坐标加 `ICON_HALF` → 命中圆心 = 渲染图标真实中心（6.8,6.8）/(16.8,11.8)/grid 中心；半径 5 > 图标半长 4.8，命中略大且完全覆盖图标。渲染视觉不变
- 生效场景：Shift 预览弹窗（RecipePopupLayer.itemAt）与 pin（PinOverlay.itemAt）的物品 tooltip/查询命中（烧炼/切石/锻造/无按钮槽位条目如 FD cooking）；crafting 网格分支不受影响（本就中心对齐）
- 已编译、已部署两实例（备份 20260825-25xxxx；1.21.11 先部署，26.2 因实例运行待用户退出后补部署，均已交付，md5 一致）。验证：U 查询燃料类（如煤炭/木板）→ 烧炼类别 → Shift 悬停料槽/结果图标 → tooltip 即时响应；pin 同样的悬停命中

**2026-08-25（二十八）：查询界面翻页按钮移入容器底部页脚（两分支同步）**——用户要求：容器 UI 将翻页按钮包裹在内（延展界面，判定区域同步拓展）；翻页按钮在底边靠右（右侧与盒子齐平）；延展区域左侧显示当前打开类别的标题：
- 新增 `PAGE_BAR_HEIGHT = 17`（页脚条高）+ `PAGE_BAR_MARGIN = 4`（按钮右内边距，与左侧 4px 内容内边距对称）；页脚条位于盒内底部，盒子向下延展包裹它（原来翻页按钮画在盒子上方、判定区也只在盒外按钮条）
- `computeBoxSize` 末尾 `boxH += PAGE_BAR_HEIGHT`（容器高含页脚：盒子背景 blit、`contains`/`exclusionArea`/`overScrollZone`、`bottomAnchor` 锚定、屏幕钳位自动跟随）；`showPage` 按钮网格布局高改用 `boxH - PAGE_BAR_HEIGHT`（内容区，行数不变）
- 新坐标 helper：`footerTop()`（页脚顶）/`pageBtnX()`（右对齐盒子右缘-4）/`pageBtnY()`（页脚垂直居中）；`drawPageControls` 现在 = 页脚左侧当前类别标题（`zzzbrbe.category.<id>` 键，26.2 `gui.text` / 1.21.11 `gui.drawString`，0xFFC0C0C0）+ 右侧翻页按钮（仅分页时）；单页也显示标题（页脚常驻）
- 判定区同步：`handlePageButtonClick` / `drawPageButton` hover / 页码 tooltip 均用新按钮矩形；`overScrollZone` 简化为整个盒子（含页脚）；类别的标题对 grid 类别（燃料/堆肥/信息）同样生效
- 已编译、已部署两实例（备份 20260825-26xxxx；部署前确认无游戏实例运行）。验证：R/U 查询任意对象 → 盒子底部出现页脚条（左=类别名如"烧炼"/"铁砧"，右=翻页按钮，仅多页时显示），按钮与盒子右缘对齐、在容器背景内；悬停/点击按钮翻页正常，页码 tooltip 出现；单页时仅显示标题

**2026-08-25（二十九）：回退（二十八）查询界面页脚改动（两分支同步）**——用户看后不满意，要求"回退吧"：`RecipeViewerOverlay` 还原（二十八）前的布局（翻页按钮回到盒子上方左侧、盒高/判定区/滚轮区恢复原样、无页脚标题），涉及文件同（二十八）全部 7 处（常量 PAGE_BAR_*、computeBoxSize、showPage、helper boxRight/footerTop/pageBtnX/pageBtnY、overScrollZone、handlePageButtonClick、drawPageControls）。已验证：两分支回退产物 md5 与（二十七）版本完全一致（26.2 51c8ec…、1.21.11 d53cb67c…），两实例均已部署（备份 20260825-27xxxx）

**2026-08-25（三十）：查询标签切换/翻页重做——REI 滚动窗口（两分支同步）**——用户要求：① 鼠标滚轮快速切换标签；② 标签数 >10 用滚动窗口机制（同 REI）；③ 选中标签位于窗口最左/右端时继续向前/后滚 → 窗口左/右滚动，无动画直接切换：
- 字段 `tabPage`（分页索引）→ `tabWindowStart`（滑动窗口起始 index，窗口大小 = MAX_TABS=10）；`drawCategoryTabs`/`handleCategoryTabClick`/`overTabStrip` 全部改按窗口绘制/命中；`drawTabTooltip` 删除分页页码行（无分页概念了）
- `mouseScrolledTabs`（标签条上滚轮）：每次滚动 = **切换选中标签**（上滚 = 上一个/左，下滚 = 下一个/右，首尾 clamp 不循环）；当新选中标签跑到窗口外 → 窗口滑动（新选中贴窗口边缘），无动画立即切换；切换走 `switchCategory`（与点击一致）
- `repaginateToSelected` 语义改为"窗口 clamp + 选中必可见"（窗口滑动而非翻页）；`close()` 重置 tabWindowStart；`ensureTabWidth` 不变（盒子仍按最多 10 个标签加宽）
- 已编译、已部署两实例（备份 20260825-28xxxx；部署前确认无游戏实例运行）。验证：R/U 查询 → 悬停底部标签条滚轮 → 快速逐个切换标签（首尾不循环）；当标签超过 10 个（如 bclib/BetterEnd 多类别整合）→ 窗口滑动显示新标签、选中贴边缘；点击标签仍切换且选中保证可见

**2026-08-25（三十一）：查询界面左侧工作站对象列 + tooltip 工作站行限制（两分支同步）**——用户四项要求：① 除烧炼/烧炼燃料外 tooltip 不显示工作站；② 所有类别的工作站统一放查询界面左侧另起一列（从下往上、不随主区翻页、超过 5 个滑动窗口、行数不够也滑窗不建空行）；③ 左列空余裁切、边界紧致；④ 左列对象 = 普通对象（同烧炼燃料格）、无特殊信息行、用于查询合成/用途：
- **tooltip（#1）**：`stationIconsTooltipComponents` 开头守卫——category 非 furnace/fuel 直接返回空（其余类别的工作站行移除）；furnace 的料槽/经验行（workstationsIconsForPrefix）保留
- **左列（#2/#3/#4）**：
  - 布局：盒子加宽 `STATION_COL_WIDTH = 28`（24px 格 + 4px 边距，与主区 4px 内边距对称）；主区右移一列——`showPage` 的 overlay.init x/mainX = boxX+28（w = boxW-28）、paged 手动铺格、`drawItemGrid` gx 均 +28；`boxLeft()`（非 grid）改为 `overlay.getX() - STATION_COL_WIDTH`，盒子背景 blit（两处 `acc.getX()`→`boxLeft()`）恢复盒左；grid 盒子用静态 boxX 不变
  - 工作站集 `rebuildStationColumn()`：内置类别按 Family 取 `RecipeViewerIndex.workstationItems(Family)`（新公共 API：遍历注册表取该家族全部 workstation 的 fallbackIcons，已进食 Hide 过滤——**furnace/fuel = FURNACE 家族：熔炉/高炉/烟熏炉/营火/灵魂营火直接罗列**）；plugin（mod）类别取 `PluginRecipeViewerCategory.stations()`（新 getter）；`RecipeViewerIndex.Family` private→public。打开/切类别时重建，关闭清空
  - 绘制 `drawStationColumn`：24px 普通格（同 fuel 格）+ 从下往上（底格 = 底部第 1 个对象）、底部对齐；视口行数 = 主区行数 `(boxH-8)/25`；工作站数 ≤ 视口 → 全显示（无空行、列裁切到实际内容）；> 视口 → `stationScroll` 滑动窗口（滚轮在列上滚动滑动一格，clamp 首尾）；hover = 普通物品 tooltip（物品名 + showModName 时的模组名行），无燃料/概率等特殊信息行
  - 交互：**左键点击对象 = 查询该对象的用途**（`openFor(screen, stack, true)`——open 拆出 `openFor(显式 target)` 共享主体，音效同点击）；`mouseClicked` 在盒子背景吞点击**之前**处理列点击；`mouseScrolled` 在标签条之后处理列滚轮
  - 几何同步：`contains`/`exclusionArea`/`overScrollZone` 用 boxW（含列）自动；标签条/tab 位置不变（列在盒内底部上方，tab 挂盒下）
- 已编译、已部署两实例（备份 20260825-29xxxx；部署前确认无游戏实例运行）。验证：① R/U 查询任意对象 → 左侧一列从上到下/从下到上排列的工作站格（烧炼类 = 熔炉/高炉/烟熏炉/营火），悬停显示物品名 tooltip、点击查询其用途（viewer 重开该对象的用途）；② 开"隐藏无配方书工作站所属的对象"→ 切石机/铁砧等无配方书站从列中消失；③ 非烧炼/燃料的配方 tooltip 不再有工作站图标行（烧炼/燃料保留）；④ 工作站超过视口行数时滚轮滑动窗口

**2026-08-25（三十二）：工作站列修正——附加在盒左、不动主区、列渲染置顶（两分支同步）**——用户实机反馈三项（（三十一）的首版实现有三处问题）：
- **① 列应附加在基准（最左标签/对象区）左侧，不改变其他元素布局**：撤销（三十一）的"主区右移一列"方案——`showPage` 恢复 `mainX = boxX`、w = boxW；`boxLeft()` 恢复 `overlay.getX()`；`drawItemGrid` gx/paged 手工铺格恢复原坐标；`computeBoxSize` 不再 `boxW += STATION_COL_WIDTH`。改为**面板向左扩**：新增 `panelLeft() = boxLeft() - STATION_COL_WIDTH`（列 = 盒左外侧一条），盒子背景 blit（非 grid 两处 + drawItemGrid 一处）改为 `blitSprite(panelLeft, by, boxW + STATION_COL_WIDTH, boxH)`；`exclusionArea()`/`contains()` 用面板矩形。对象区/标签/翻页按钮坐标完全不变
- **② 列有判定区但看不见渲染**：原因是列在盒子背景**之前**绘制被 blit 覆盖。修复：`drawStationColumn` 调用移到最上层——grid 分支在 `drawCategoryTabs(false)` 之后、tooltip 之前；非 grid 在 `drawCategoryTabs(false)` 之后、popup 之前（保持 popup/tooltip 顶层）
- **③ 列翻页区域独立**：`handleStationColumnScroll` 判定区 = `(panelLeft, boxY, STATION_COL_WIDTH, boxH)`（列专属）；主区配方翻页滚轮（overScrollZone）仍只在主区；标签条滚轮互不影响
- 列几何同步：`drawStationColumn`/`handleStationColumnClick` 格 x = `panelLeft() + 2`；`stationViewRows`/视口/裁切逻辑不变（列在盒外侧底部对齐）
- 已编译、已部署两实例（备份 20260825-30xxxx；部署前确认无游戏实例运行）。验证：R/U 查询 → 工作站列出现在**盒外左侧**（左缘 = 盒左-28），对象区/标签/翻页按钮位置与（三十）一致；格子、悬停 tooltip、点击查询、滚轮滑动均正常

**2026-08-26（三十三）：工作站列与对象网格对齐 + 扩展区纳入判定区域（两分支同步）**——用户实机反馈两项：
- **① 列位置偏左，应与列中心线对齐**：用户澄清"工作站列就是一个独立的对象列"——列作为对象网格的"第 -1 列"：`STATION_COL_WIDTH` 28→**25**（= 一格距），面板左扩宽 = boxW + 25；格 x 由 `panelLeft()+2` 改 **`panelLeft()+4`**（与对象格同样的 4px 内缩）→ 列中心线 = panelLeft+16 = 对象第 0 列中心线（boxLeft+16）- 25px，恰好落在一格距上（网格对齐，不再有 28px 的错位感）。滚轮判定区随格位调整为 `(panelLeft, boxY, STATION_COL_WIDTH + 4, boxH)`（覆盖整格，不越过对象区首列）
- **② 扩展区（工作站列）也是查询界面的判定区域**：`inBox`（非 grid 与 grid 两分支）判定矩形从盒矩形改为**面板矩形** `(panelLeft, boxY, boxW + STATION_COL_WIDTH, boxH)`——点击列空白不再误关 viewer；`exclusionArea()`/`contains()` 已是面板矩形，不变
- 已编译、已部署两实例（备份 20260826-002023；部署前确认无游戏实例运行）。验证：R/U 查询任意对象 → 工作站列单元格中心线与对象网格列中心线相差整 25px（与对象列同格距）；点击列空白不关闭 viewer；格悬停/点击/滚轮正常

**2026-08-26（三十四）：工作站列单击=查询合成 + 悬停支持 R/U 快捷键 + 列面板紧致裁切（两分支同步）**——用户两项要求：
- **① 单击列对象查询"合成"而非用途**：`handleStationColumnClick` 的 `openFor(screen, stack, true)`（U 语义）改为 `openFor(screen, stack, false)`（R 语义 = 查看合成）；并抽公共 `stationCellAt(mx,my)`（格点命中，与绘制的 x=panelLeft+4/bottom=boxY+boxH-4/j*25 几何一致）
- **① 列对象支持 R/U 快捷键**：`captureTarget` 末尾新增站列悬停捕获——viewer 激活时鼠标在列格上 → 返回该对象（`stationCellAt`），R/U 键因此能查询列上对象的合成/用途（与点击语义解耦：R=合成、U=用途）
- **② 列空位裁切 + UI 边界**：面板背景从"全盒高延展条"改为**独立紧致 9-slice 面板**——·主盒背景 blit（非 grid 两处 + drawItemGrid 一处）恢复 `boxLeft()/boxW`（不再向左延展）；·`drawStationColumn` 先画列面板 `blitSprite(OVERLAY_RECIPE_SPRITE, panelLeft(), colTop, STATION_COL_WIDTH+4, colH)`：右缘 = 主盒左 border（4px 边框与主盒边框重合，两面板视觉连成一体）、底部齐主盒底、顶边 = 顶格上方 4px（空位裁掉）；colTop = bottom - shown*25 + 1 - 4、colH = (boxY+boxH)-colTop；·`handleStationColumnScroll` 判定区跟随面板（`(panelLeft, colTop, STATION_COL_WIDTH+4, colH)`，此处 shown 恒取视口行数——可滚动时格子满视口）
- 已编译、已部署两实例（备份 20260826-004735；部署前确认无游戏实例运行）。验证：① 单击工作站列任一对象 → 打开该对象的**合成**（R）而非用途；② 悬停列对象按 R/U → 分别查合成/用途；③ 工作站少时列面板上端随内容裁切、与主盒左边框无缝拼合；④ 列滚轮、点击、悬停 tooltip 正常

**2026-08-26（三十五）：工作站列衔接重做——侧翼一体方案（两分支同步）**——用户反馈（三十四）的独立列面板与主盒"断开"（两面板各自圆角边框叠在一起，接缝像两块独立 UI）。候选方案：A 侧翼一体 / B 整面板（不裁切）/ C 无框内衬。用户选 **A**：
- 根因：列面板是独立 9-slice 框、画在**主盒之上**，它的右边框+圆角与主盒左边框+圆角两条边线叠画 → 接缝断裂感
- 修复：**列背景改画在主盒下层**——拆出 `drawStationColumnPanel(gui)`（只画背景 blit）与 `stationColumnPanelRect(shown)`（面板矩形 helper），三处主盒 blit（grid 的 drawItemGrid 前、非 grid paged/非 paged 的盒背景前）先调 `drawStationColumnPanel`；`drawStationColumn` 只保留格子/悬停/tooltip（格子仍在最上层）
- 几何：列面板宽 `STATION_COL_WIDTH+4`，右缘右探 4px 伸进主盒 → **被主盒绘制覆盖** → 列没有自己的右边框，主盒左边框兼作列右边框（单边框接缝）；列底边 = 主盒底（底边框共线）；列顶边 = 顶格上方 4px（空位裁切保留）；列顶边框横线在 boxLeft 处与主盒左边框自然交接（"T 形"汇入）
- `handleStationColumnScroll` 判定区统一走 `stationColumnPanelRect`（与绘制同几何，删重复计算）
- 已编译、已部署两实例（备份 20260826-010534；部署前确认无游戏实例运行）。验证：R/U 查询 → 列与主盒之间**只有一条边框线**（列像主盒左侧长出的侧翼）；列顶随内容裁切；单击列=查询合成、悬停 R/U、滚轮、tooltip 不受影响

**2026-08-26（三十六）：工作站列衔接再修——接缝抹灰（两分支同步）**——用户反馈（三十五）侧翼方案仍不衔接：① 主体（主盒）的 9-slice 左边框（黑-白-灰条纹）完整画在列与内容之间的过渡区域，把两侧"切断"；② 满载时侧翼比主体矮 1px（`colH = shown*25+7` vs 主盒 `rows*25+8`）。
- **接缝抹灰（衔接）**：新增 `eraseStationColumnSeam(gui)`——列面板右缘与主盒左边框重合的 4px 竖条（`boxLeft..boxLeft+4` × 列面板矩形上下各缩 4px）用主盒内容色 `0xFFC6C6C6`（198 灰，与 9-slice 拉伸区同色）重涂，**抹掉列右边框与主盒左边框** → 列与主盒内容连成同一连续表面，无边框线隔断；列的上/左/下边框保留（上边框横向汇入主盒"T"形、下边框与主盒底边框共线）。调用点：`drawStationColumn` 开头（主盒 blit 之后、画格子之前）——grid/paged/非 paged 三条渲染路径天然覆盖
- **1px 修正**：列面板顶部内边距 4→**5**（`colTop = bottom - shown*25 + 1 - 5`；主盒格子顶 = boxY+5，同款内边距）→ 满载时 `colTop = boxY`、`colH = boxH`，列与主盒同高同顶，无 1px 差值
- 已编译、已部署两实例（备份 20260826-012705；部署前确认无游戏实例运行）。验证：① 列与主盒之间无黑/白竖线，格子区域连成一体（列像主盒左侧长出的翼）；② 列顶边框横线在 boxLeft 处"T"形汇入主盒边框；③ 工作站数 = 视口行数时列与主盒完全等高；④ 列左/上/下边框完整、格子/点击/滚轮/R/U 不受影响

**2026-08-26（三十七）：工作站列衔接重做——纯色条带绘制（两分支同步）**——用户反馈（三十六）抹灰法在拐角/底部仍不衔接：两个 9-slice 的圆角在接缝处错位（列右上圆角 vs 主盒左边框、列右下角 vs 主盒底部圆角冲突，底部双边框夹缝）。根因：**任何两个 9-slice 框架的圆角都无法在直角接缝处自然汇合**，抹灰只能抹直线段、抹不掉圆角。
- **彻底方案：列不再用 9-slice 框架**，改用与精灵同色的**纯色条带**绘制（`drawStationColumnSurfaces`，替代 drawStationColumnPanel/eraseStationColumnSeam）：
  - 内容板：`fill(panelLeft, colTop, boxLeft+4, colBottom-3, 0xC6C6C6)`——与 9-slice 内部同色灰，右探 4px 浸入主盒左边框，列与主盒连成同一表面（无接缝线、无圆角）
  - 左边框：黑 1px（`0x000000`）+ 白 2px（`0xFFFFFF`）竖条（同精灵左边框色序：黑1+白2+内容灰）
  - 顶边框：黑 1px 行 + 白 2px 行横条，右端到 `boxLeft+4`——在 boxLeft 处与主盒左边框形成平直的 **T 形汇入**（无独立圆角）
  - 底边带：`0x555555` 2px 行 + 黑 1px 行（同精灵底边框色序），横贯列宽与主盒底边框**共线续接**
  - 绘制时机：`drawStationColumn` 开头（主盒 blit 之后、格子之前，三渲染路径天然覆盖）；删除三处主盒前 `drawStationColumnPanel` 调用
- 仿真（Python 复现精灵+绘制顺序）验证：无缝、无一像素圆角伪影。已编译、已部署两实例（备份 20260826-015358；部署前确认无游戏实例运行）。验证：① 列与主盒之间纯灰连续、无任何边框线/圆角错位；② 列顶边框平直 T 形汇入主盒左边框；③ 列底边带与主盒底边框共线；④ 列左/上边框色序与主盒一致（黑1白2）；⑤ 格子/点击=查合成/悬停 R/U/滚轮/tooltip 正常

**2026-08-26（三十八）：列面板专属 9-slice 纹理（右开口版）——两顶角圆角 + 左下拐角无黑边（两分支同步）**——用户反馈（三十七）纯色条带方案：两顶角变直角（非原纹理圆角）、左下拐角内侧有黑 L 边不美观。要求"拐角内去掉黑边，两顶角保持原纹理（圆角）"。
- **新增专属纹理** `assets/zzzbrbe/textures/gui/sprites/recipe_book/column_panel.png`（+mcmeta，32x32 nine_slice border 4，两分支同文件）：从 `overlay_recipe` 派生，**右侧开口**——右中 3 列（x=29..31, y=4..27）与 BR 角（x=28..31, y=28..31）重涂为内容灰 `0xC6C6C6`；左/上/下边框与 TL/TR/BL 圆角**保持原纹理像素**（BL 角内侧为白 2px+内容灰，无黑边；顶边框黑行+白行完整、两端圆角）
- `drawStationColumnSurfaces` 由 7 段纯色 fill 改为一次 `blitSprite(COLUMN_PANEL_SPRITE, panelLeft, colTop, STATION_COL_WIDTH+4, colH)`：右缘 4px（覆盖主盒左边框区）为内容灰 → 与主盒无缝；底带黑行延伸至面板右缘（= 主盒左边界）与主盒底边框共线；顶部随内容裁切（5px 内边距）保留 TL/TR 圆角；BR 角灰化避免与主盒 BL 圆角撞角
- Python 仿真（原纹理+绘制顺序）验证：两顶角原圆角、左下拐角内侧无黑边、右侧无接缝、底边共线。已编译、已部署两实例（备份 20260826-020902；部署前确认无游戏实例运行）。验证：① 列面板左/上/下三边框纹理与主盒一致（黑1白2圆角）；② 左下角圆角内侧无黑 L 边；③ 右侧与主盒灰面无缝、底部黑线与主盒共线；④ 格子/点击=查合成/悬停 R/U/滚轮/tooltip 正常

**2026-08-26（三十九）：侧翼黑紫错误纹理修复——精灵 ID 带全路径导致查找失败（两分支同步）**——用户反馈（三十八）部署后侧翼显示黑紫错误纹理（missing texture）。
- 根因：`COLUMN_PANEL_SPRITE` 误写成 `Identifier.fromNamespaceAndPath("zzzbrbe", "textures/gui/sprites/recipe_book/column_panel")`——**GUI 精灵 ID 是相对 `textures/gui/sprites/` 的路径**（对照同文件可用的 `OVERLAY_RECIPE_SPRITE` = `recipe_book/overlay_recipe`，及 `BRBTextures` 全部精灵均 `recipe_book/...` 形式）；带全路径的 ID 在 gui 图集中查不到 → `blitSprite` 渲染错误纹理（黑紫格）
- 修复：两分支 ID 改为 `Identifier.fromNamespaceAndPath("zzzbrbe", "recipe_book/column_panel")`，javadoc 注明约定（防回归）。mcmeta（`{"gui":{"scaling":{"type":"nine_slice",...}}}`）与 PNG（32x32 RGBA）经与原版 jar 内精灵对照确认无误，未动
- 已编译、已部署两实例（备份 20260826-133612；部署前确认无游戏实例运行），md5 一致，jar 内精灵+mcmeta 在、class 常量池为 `recipe_book/column_panel`。验证：R/U 查询任意类别 → 侧翼列面板应显示正常纹理（两顶角圆角、左下无黑边、右侧无缝），不再是黑紫格

**2026-08-26（四十）：侧翼右上/右下角与主盒衔接修复（两分支同步）**——用户反馈（三十九）部署后：右上角和右下角的衔接没做好（截图：TR 角有悬浮灰方块+黑/白碎屑，BR 角底带被打断出现白十字）。
- 根因（Python 3x 仿真 + 逐像素行程对照实锤）：`column_panel` 精灵仍以 32x32 完整盒框（四角圆角+右缘 D/K 边）派生、仅右中列涂灰——九宫格下：
  - **TR 角（sprite x28-31, y0-3）** = 原纹理圆角：透明镂空（x30-31, y0-1）让底层盒体左边框（K/W）透出成碎屑；右缘 D85/K（y2-3）残屑挂在弧线下 → 悬浮灰方块 + 黑边框碎片
  - **BR 角** = 纯灰：底带（D/D/K）在右缘前 4px 中断，盒体底边框被灰方块打断 + 盒体 BL 圆角残影 → 白/灰/黑十字
- **前提核对**：原版 `overlay_recipe.png.mcmeta` 存在（九宫格 border 4）→ 盒体左边框恰 4px（K1+W2+G1），被面板右缘 4px（boxLeft..+3）完全覆盖，接缝设计正确；唯一病根是面板精灵自身的角像素
- 修复（仅改纹理，`column_panel.png` v2，两分支同文件）：
  - TR 角：透明镂空填充内容灰 198（x30-31/y0、x31/y1）；右缘 D85→198（y2-3 的 x29-30），**保留弧线黑色外沿**（y1 x30、y2-3 x31 的 K）→ 圆角弧线坐在灰上、灰色与盒体内容无缝、无盒体边框透出
  - BR 角：底带（D85/D85/K）延长贯穿至右缘（y29-31 的 x28-31 = D/D/K）→ 与盒体底部九宫格底边框（同为 D/D/K 共线）连成一条不间断底带；y28 残留 D85 清为 198（内容行）
- 3x 仿真（宽盒 133 / 窄盒 33 两种 boxW）验证：TR 弧线干净、BR 底带连续、右侧无任何盒体边框残留。已编译、已部署两实例（备份 20260826-135723；部署前确认无游戏实例运行），jar 内 png md5=8718d5… 与源一致。验证：R/U 查询任意类别 → 侧翼右上角为圆角弧线贴合灰面、右下角底带与主盒底边框连成一条线，无碎屑/无灰块/无十字

**2026-08-26（四十一）：侧翼右上角改平直 T 形衔接（两分支同步）**——用户反馈（四十）后：右下角 OK，右上角仍不行（截图：顶边框黑行/白行跑到盒体边框处被"掐断"，白列上有 1px 黑缺口 + 弧线黑桩悬在灰面上 + 按钮边框紧贴其右，多套线条互相打架）。
- 根因（对照新截图逐像素行程）：v2 的 TR 仍保留原纹理**圆角弧线**——但盒体左边框的黑列（blit x25）/白列（x26-27）垂直贯通到面板顶边框处，圆角弧线（K 行延伸到 x26、弧线黑桩在 x28 y2-3、镂空灰）把盒体边框的白列/灰列切出 1px 黑缺口与深灰桩；且弧线右侧只盖到盒体左边框的一半，按钮边框（K D，位于 px+4）紧贴其后形成三层并排线条
- **设计决定**：TR 角不做圆角（圆角与盒体边框线条无缝兼容不可能并存），改为**平直 T 形汇入**——面板顶边框的黑行（外）/白行（内）分别接到盒体左边框的黑列（外）/白列（内），所有线条一一连通；仅 TL 角保留圆角。javadoc 同步更新（明确说明"只用 TL 圆角，TR 平直 T"）
- 修复（仅改 `column_panel.png` v3，两分支同文件）：TR 角 4x4 像素 = 行0 [K,W,W,G] / 行1 [W,W,W,G] / 行2-3 [全 G]（sprite x28=K、x29-30=白→接盒体白列、x31=灰→接盒体内容列；删除弧线 K 行溢出、x30 y1 弧线 K、y2-3 x31 黑桩与 x29-30 残 D）；BR 角维持 v2（底带 D/D/K 贯穿）
- 3x 仿真像素级验证：面板黑行止于盒体黑列（blit x25）、白行接盒体白列（x26-27）、盒体灰列（x28）连续，下方渐变灰无缝；无缺口/无黑桩/无第三层线条。已编译、已部署两实例（备份 20260826-141843；部署前确认无游戏实例运行），jar 内 png md5=d61895… 与源一致。验证：R/U 查询任意类别 → 侧翼右上角 = 面板顶边框平直汇入盒体左边框（黑接黑、白接白），无圆角残片/无黑缺口/无灰缝

**2026-08-26（四十二）：侧翼顶部白行右端补 2 个白色像素（两分支同步）**——用户反馈（四十一）后：右上角形态可接受，但"侧翼顶部白行右侧还要向右补两个白色像素"（白行右端与按钮边框之间的 2 个灰色像素让白线"差一口气"）。
- 修复：`column_panel.png` v4（仅动 TR 角 2 个像素）：sprite (31,0) 与 (31,1) 灰 198 → 白 255 —— 即九宫格下 blit x28（面板右缘列 / 盒体边框灰列位置）的 top 行与 white 行由灰转白；白行/白列右端直通面板右缘**贴着按钮黑边框**（Y0 黑行结束后接 3px 白，Y1 白行全宽），下方 y2+ 仍为灰（内容渐变不改）。其余（T 形黑接黑、BR 底带、4px 右缘覆盖盒体边框）维持 v3
- 已编译、已部署两实例（备份 20260826-142752；部署前确认无游戏实例运行），jar 内 png md5=5643d6… 与源一致（TR y0=[K,W,W,W] / y1=[W,W,W,W]）。验证：R/U 查询任意类别 → 侧翼顶部白行右端 = 2px 白色延伸至面板右缘、紧贴按钮边框，无灰色缝隙

**2026-08-26（四十三）：顶部白行补白位置修正——补在白边下缘（两分支同步）**——用户反馈（四十二）：补错位置（v4 把白行右端 2 像素补在了面板右缘列 x28 的 y0/y1 行——出现白色竖桩、且让白行右缘凸出），应补在**顶部白边下缘**：面板顶边框原纹理是 1 黑行（y0）+ **2 白行**（y1/y2），v3 只把 y1 白行通到 blit x27，**y2 白行（下缘）只到 x25**——白边右端呈"上长下短"的 2px 阶梯缺口
- 修复：`column_panel.png` v5：**回退 v4**（sprite (31,0)/(31,1) 灰 198 → 恢复原 G）＋ 白边下缘补白（sprite (29,2)/(30,2) 灰 → 白）——blit y1 与 y2 两行白边右端**都对齐到 x27**（盒体白列 x26-27 两侧同行），白边右端齐平、无阶梯缺口、无右缘竖桩；x28（盒体边框灰列）保持灰
- TR 角现状（9-slice 固定 4px 区）：y0=[K,W,W,G] / y1=[W,W,W,G] / y2=[W,W,W,G] / y3=[全 G]（另：左侧白列顶部 2 行同配、BR 底带 D/D/K 贯穿、BL/TL 圆角如旧）
- 已编译、已部署两实例（备份 20260826-144010；部署前确认无游戏实例运行），jar 内 png md5=ed5b43… 与源一致。验证：R/U 查询任意类别 → 侧翼顶部白边（2px 高）右端齐平收口于盒体白列，下缘无 2px 灰缺口、无右缘白竖桩

**2026-08-26（四十四）：侧翼顶与主盒顶齐平时使用顶对齐纹理变体（两分支同步）**——用户要求：当侧翼列面板顶部与主界面（主盒）顶部齐平时（列满、面板裁切顶=盒顶），面板顶部改用专用纹理 `column_panel_top.png`（用户提供，放在 26.2 资源目录；已同步到 1.21.11 并补九宫格 mcmeta，两文件同 md5=888f9f…）。
- 变体设计（32x32，同九宫格 border 4）：顶边框（黑行 y0 + 2 白行 y1-2）**通到右缘 x31**——面板顶边框与主盒顶边框（同为 K+WW，行对齐）连成一条直线；TL 圆角保留；右侧开口（y3+ 灰面）、底带 D/D/K 贯穿与 `column_panel` 一致
- 代码（两分支 `RecipeViewerOverlay`）：新增 `COLUMN_PANEL_TOP_SPRITE`（`zzzbrbe:recipe_book/column_panel_top`）；`drawStationColumnSurfaces` 按 `stationColumnPanelRect(shown)[0] == boxTop()` 选择变体（colTop==boxTop ⟺ shown==行数，即列满、面板顶=盒顶；行数低于盒高时仍用普通 `column_panel` 的 TR T 形衔接）
- 已编译、已部署两实例（备份 20260826-150611；部署前确认无游戏实例运行），md5 一致，jar 内 top png+mcmeta 在（TR y0=[K,K,K,K]、y3=[G,G,G,G]）。验证：查询一类工作站数量 ≥ 盒行数（列满顶齐平）→ 面板顶边框与主盒顶边框连成一条直线、无 T 形截断；列不满时行为不变（TR 平直 T 汇入）

**2026-08-26（四十五）：工作站列滑动窗口三角翻页标记（两分支同步）**——用户要求：工作站列启用滑动窗口时，每个工作站 tooltip 上放 ▲△▼▽ 标记翻页情况（无滑动窗口则不放置）：
- 判定（`drawStationColumn` hover tooltip）：`maxScroll = items.size() - stationViewRows()`；`maxScroll > 0` = 窗口启用。上三角 ▲/△（实心=可向上滑 `stationScroll>0`，空心=已到顶），下三角 ▼/▽（实心=可向下滑 `stationScroll<maxScroll`，空心=已到底）——所有格子共用同一窗口状态，标记一致
- 布局：▲ 居右放在**工作站标题行**（标题后**至少 4 空格**再 ▲，并按 tooltip 最大行宽右对齐补空格；若需 >12 空格（tooltip 已很大）则不加额外空格，保持最小 4 空格）；▼ 居右放在**标题行下的空行**（即原模组名上方分隔空行——"和模组名显示的空行重叠"；无模组名时也加该空行承载 ▼）；模组名行在其后。窗口未启用时维持原文案结构（标题 + [空行 + 模组名]）
- 字符：▲ U+25B2 / △ U+25B3 / ▼ U+25BC / ▽ U+25BD（原版 default.json 含 `include/unifont` 回退，可渲染）
- 已编译、已部署两实例（备份 20260826-152629；部署前确认无游戏实例运行），md5 一致。验证：R/U 查询工作站多的类别 → 悬停列格子：title 行右端 ▲、下方空行右端 ▼；滚轮滑到底 → ▲ 变 △、▼ 保持；滑到顶 → ▲ 变 △、▼ 变▽；工作站 ≤ 行数时无三角；▲ 与标题至少 4 空格

**2026-08-26（四十六）：工作站三角标记修正——始终右对齐（两分支同步）**——用户反馈（四十五）：① 两个三角没有居右；② 确认语义：滑到顶=上三角变空心（△）、滑到底=下三角变空心（▽）。
- ① 根因：上一版"tooltip 已很大则不补空格"上限（>12 空格）在一些场景（模组名/标题较长）触发后 **▲/▼ 停在 4 空格间隙处、未到右缘**。修复：**删除上限**——▲/▼ 始终按全部行（标题+标记、空行+标记、模组名）的最宽行补空格到**同一右缘**（最小间隔恒为 4 空格）；经反编译核对原版 `ClientLanguage.getVisualOrder`（FormattedBidiReorder，不裁剪空格）、`Font.width(FormattedText/FormattedCharSequence)`（同一 StringSplitter 口径）、`ClientTextTooltip`（宽度=font.width、绘制=左对齐逐行）——空格右对齐链路完整可用
- ② 语义复核无误（代码即此判定）：`up = stationScroll>0 ? ▲ : △`（到顶=stationScroll==0 → △）；`down = stationScroll<maxScroll ? ▼ : ▽`（到底 → ▽）
- 已编译、已部署两实例（备份 20260826-153530；部署前确认无游戏实例运行），md5 一致。验证：悬停工作站（窗口启用）→ ▲、▼ 右端与 tooltip 右缘对齐且彼此同列；滑到顶 △ + ▼；滑到底 ▲ + ▽

**2026-08-26（四十七）：工作站滚轮方向反转（三角语义错位的真凶）＋标记对齐改纯字符串宽度（两分支同步）**——用户反馈（四十六部署并重进游戏后）：两个 bug 仍未解决。
- **滚轮方向反转**（实锤语义 bug）：`handleStationColumnScroll` 原为 `vertical > 0`（滚轮上）→ `stationScroll + 1`（窗口向列表下方滑）——与标准列表滚动相反：用户"向上滚想去顶"反而一路滑到底 → 上三角始终实心、下三角先变空心，与"滑到顶→上三角△"预期完全对不上。修复：`vertical > 0 → -1`（滚轮上 = 滑向列表顶部），滚轮下 = 向底部
- **标记对齐改纯字符串宽度**（`String title/emptyBase + Component.literal`；原 Component.copy().append 版本）：宽度全部用 `font.width(String)` 计算，`target = max(title+4sp+▲, mod, 4sp+▼)`，两行补空格到 target（▲/▼ 右缘对齐），最小间隔恒 4 空格；语义不变。⚠️ 待用户截图确认——如仍不右对齐则需带 tooltip 截图逐像素定位
- 已编译、已部署两实例（备份 20260826-154327；部署前确认无游戏实例运行），md5 一致。验证：① 滚轮**向上** → 窗口滑向列表顶部、上三角逐步变空心（到顶=△+▼）；滚轮**向下** → 到底（▲+▽）；② 悬停工作站（窗口启用）→ ▲▼ 右缘对齐

**2026-08-26（四十八）：三角放置规则按用户定义重写——▲固定标题后4空格、▼与▲同铅垂线（两分支同步）**——用户提供截图（154804）＋明确规则：① 上三角与标题差距>4 空格则不再补空格（=▲恒为标题后 4 空格）；② 下三角须与上三角在同一根铅垂线上。
- 截图逐像素分析实锤：▲ 实际停在标题+4空格处、▼ 停在行首附近——**MC tooltip 渲染会丢弃行尾空格**（此前"行尾补空格右对齐"方案无效，这就是"不居右"与 ▼ 不随动的根因）；行首空格保留可用
- 修复：**▲ 行 = 标题 + 恰好 4 空格 + ▲（无任何行尾补齐）**；**▼ 行 = 仅行首空格 `pad = (width(标题)+4*spaceW)/spaceW` 个 + ▼**（行首空格，保证与 ▲ 同 x 铅垂线，且不会被裁剪）。两分支 `RecipeViewerOverlay.drawStationColumn`
- 已编译、已部署两实例（备份 20260826-155239；部署前确认无游戏实例运行），md5 一致。验证：悬停工作站（窗口启用）→ ▲ 紧贴标题后 4 空格；▼ 与 ▲ 上下同一竖线；滑到顶 ▲→△、滑到底 ▼→▽；滚轮上=向顶、下=向底

**2026-08-26（四十九）：三角右缘对齐重写——先定 tooltip 宽度再插入三角（两分支同步）**——用户反馈（四十八部署并重进后）：两个三角（理论上都应居右）实际没对齐；且"上三角与标题差距＞4 空格则不再补、＜4 则补到恰好 4"的规则未实现；用户判断是**元素放置顺序**问题，设想先放标题/空行/模组名行定尺寸、两三角最后插入（此时界面大小已定）。
- 反编译 26.2 `GuiGraphicsExtractor.setComponentTooltipForNextFrame` → `Component.getVisualOrderText()` → `ClientTextTooltip.extractText`（`graphics.text(…, true)` 逐行左对齐）：行内空格（非行尾）必然渲染；`Font.width` 测量与渲染同源
- 按用户设想重构 `drawStationColumn`：① 先按基础行（标题、空行、模组名）测宽得到 contentW（▲ 行按最小 4 空格间距可能超宽，此时允许撑宽 tooltip）；② **▲ 与 ▼ 均相对 contentW 右缘定位**——`gap = max(4, (contentW-titleW-upW)/spaceW)`（▲ 与标题 ≥4 空格，右缘更远时取右缘）＋ `pad = (contentW-downW)/spaceW`（▼ 贴同一右缘）→ 两三角共用同一右缘＝同一铅垂线（字形同宽、标题 advance 均为空格宽整数倍时像素级重合）；③ mod 行宽度改用 `getVisualOrderText()` 样式感知测量——ModNameUtil 的 mod 组件带 ITALIC，`getString()` 测量会漏掉斜体加宽
- 已编译、已部署两实例（备份 20260826-161943；部署前确认无游戏实例运行），md5 一致（26.2 1c76389c…、1.21.11 c8ce8914…）。验证：悬停工作站（窗口启用）→ ▲ 位于 tooltip 右缘、距标题 ≥4 空格（不足则补到 4）；▼ 与 ▲ 精确同一竖线；滑到顶 ▲→△、滑到底 ▼→▽

**2026-08-26（五十）：▼ 改为像素级锚定 ▲——弃用空格网格对齐（两分支同步）**——用户反馈（四十九部署并重进后）：▼ 有时与右边界隔一个空格（相对 ▲ 左移约 1 空格）、有时对齐、有时右偏；要求"▼ 直接锚定 ▲，尽量不独立配置"。
- 根因（反编译 26.2 `BitmapProvider$Definition.load` 实锤）：**字形 advance = (int)(0.5 + 实际字形像素宽 × 缩放) + 1**（如 'i'≈2px、'm'≈9px，任意整数），**不是 4 的倍数**；空格（space provider）恒 4px → 标题宽 mod 4 余数任意 → 用 4px 空格网格无法精确凑出 ▲ 位置 → ▼ 相对 ▲ 漂移 0–4px（有的标题对齐、有的错位）
- 修复：**彻底放弃空格填充**——新增两个 tooltip 行组件（复用 `ClientTooltipComponent` 通道，与 TitleWithIcon/RecipePreviewTooltipComponent 同机制）：
  - `StationTitleMarkerTooltipComponent`（标题行）：`extractText/renderText` 在 `x` 画标题、在**精确像素 `x + anchorX`** 画 ▲（无空格隔断）
  - `StationMarkerTooltipComponent`（▼ 行）：在**同一 anchorX** 画 ▼ → ▲/▼ **像素级同 x**（共享一个锚点，▼ 零独立配置）
  - anchorX = max(titleW + 16px, contentW - upW)：▲ 距标题 ≥4 空格（16px），右缘更远时贴 contentW 右缘（右对齐）；contentW = max(titleW, modW, titleW+16+upW)
- 顺带收益：标题改用 `getHoverName().getVisualOrderText()` 保留原样式；行宽 getWidth = anchorX + 字形宽（不撑宽 tooltip）；26.2 走 `gui.tooltip(...)`、1.21.11 走 `gui.renderTooltip(...)`（`DefaultTooltipPositioner` + `DataComponents.TOOLTIP_STYLE`，与 renderPopupSlotTooltip 同构）
- 已编译、已部署两实例（备份 20260826-170047；部署前确认无游戏实例运行），md5 一致（26.2 6705e98d…、1.21.11 5ac611c1…）。验证：悬停工作站 → ▲ 与 ▼ 严格同一竖线（任意标题宽度、任意缩放）、▲ 距标题 ≥4 空格、滑到顶 ▲→△、滑到底 ▼→▽

**2026-08-26（五十一）：烧炼/燃料左栏工作站按子类别分组排列（两分支同步）**——用户要求分配烧炼与烧炼燃料类别的工作站排列：① **初始窗口位于列表最底部**（所有类别的基础机制）；② 列从下到上 = 烧炼、熔炼、烟熏、营火烹饪四个子类别；③ 每个子类别内工作站顺序 = 该子类别 tooltip 行图标从左到右的顺序；④ 所有类别工作站从下往上放置。
- 现状：燃料/烧炼左栏此前用 `workstationItems(FURNACE)` 平铺注册顺序（furnace/blast/smoker/campfire/soul_campfire+mod 站混排、无分组）；渲染方向（index 0 在底部、`stationScroll=0` 初始窗口在列表底部内容）本就自下而上且初始在底，予以保留并注释固化
- 修复：`RecipeViewerIndex` 新增 `furnaceStationColumnItems()`——按 `FURNACE_SUBCATEGORY_PREFIXES = [furnace_, blast_furnace_, smoker_, campfire]`（与 tooltip 子类别行 `stationCategoryPrefix(0..3)` 同源同序）分组，组内 = `workstationsIconsForPrefix(prefix)`（即 tooltip 行从左到右顺序），**按 Item 去重**（多子类别匹配的站保留最底位置）；`RecipeViewerOverlay.rebuildStationColumn` 对 `Family.FURNACE`（烧炼 + 燃料类别）改走该分组列表，其他类别保持 `workstationItems(family)` 平铺（其方向/初始窗口规则不变）
- 已编译、已部署两实例（备份 20260826-172315；部署前确认无游戏实例运行），md5 一致（26.2 4c9ebf26…、1.21.11 768cca66…）。验证：U 查询熔炉/燃料物品 → 左栏自下而上 = 烧炼（熔炉+mod 烧炼站）→ 熔炼（鼓风炉+mod 熔炼站）→ 烟熏（烟熏炉）→ 营火（营火+灵魂营火）；组内顺序与对应 tooltip 行图标一致；打开时窗口在列表底部（▲空心/▼实心），滚轮上=向顶、下=向底

**2026-08-26（五十二）：滚轮方向与三角空心语义按用户定义修正（两分支同步）**——用户要求：① 鼠标滚轮**向上**滚 = 滑动窗口**向上**移动（向列表顶部内容）；② 窗口位于列表**底部**时**下三角变空心 ▽**；③ 窗口位于列表**顶部**时**上三角变空心 △**（打开时窗口在底部 → 显示 ▲/▽，与五十一轮"初始窗口在最底部"呼应）。
- 改动（`RecipeViewerOverlay`，26.2 + 1.21.11）：
  - `handleStationColumnScroll`：`next = stationScroll + (vertical > 0 ? 1 : -1)`——滚轮向上窗口上移（stationScroll 增大，显示列表更靠上的内容），向下回底（此方向曾于四十七轮反向，以本次用户定义为准）
  - 标记判定重写：`up = stationScroll < maxScroll ? ▲ : △`（未到顶实心、到顶空心）；`down = stationScroll > 0 ? ▼ : ▽`（未到底实心、到底空心）——底部 ▽、顶部 △
- 已编译、已部署两实例（备份 20260826-172830；部署前确认无游戏实例运行）。验证：打开工作站多的查询 → 初始（底部）▲/▽；滚轮向上 → 窗口上移、▼ 变实心；滚到顶 → △/▼；滚轮向下回到底 → ▲/▽

**2026-08-26（五十三）：工作站 tooltip 被后续单元覆盖 + 侧翼空白判定未裁剪（两分支同步）**——用户反馈两项：① 悬停工作站对象显示 tooltip 时，其他工作站对象的 UI 遮住 tooltip；② 侧翼没放满时判定区域仍是一整列，空白处应被裁切。
- **问题 1 根因**（反编译 26.2 `GuiGraphicsExtractor`）：`gui.tooltip(...)` 是**就地提取**（提取顺序 = 绘制顺序，后提取者覆盖先提取者）——五 十 轮把工作站 tooltip 从延迟的 `setComponentTooltipForNextFrame` 改为 `gui.tooltip/renderTooltip`（因自定义标记组件无法走 Component 通道），调用点在 cell 循环内 → 循环中后续 cell 的提取盖住 tooltip
- **问题 1 修复**：tooltip **延迟到 overlay 渲染末尾**——新增 `pendingStationTooltip{X,Y,Style}` 字段（`drawStationColumn` 只存不画），新 `flushStationTooltip(gui)` 在 `render()` 两分支（grid / 非 grid）的 `renderTooltip(...)` **之后**调用（26.2 走 `gui.tooltip(...)`、1.21.11 走 `gui.renderTooltip(...)`）——tooltip 成为整帧最后绘制的内容，任何单元/弹窗都盖不住
- **问题 2 根因**：`handleStationColumnScroll` 的滚轮判定用了 `stationColumnPanelRect(stationViewRows())`（整列视口高），而面板背景渲染用的是 `stationColumnPanelRect(shown)`（实际内容行、顶边跟随最上格）→ 空白带仍可触发滚轮
- **问题 2 修复**：滚轮判定改用 `stationColumnPanelRect(min(size, rows))`（与渲染同一矩形）——面板上方空白不再是命中区；单元格悬停/点击判定本就是 24×24 逐格精确，无需改动
- 已编译、已部署两实例（备份 20260826-173906；部署前确认无游戏实例运行），md5 一致（26.2 7bc95033…、1.21.11 7ff5a037…）。验证：① 悬停工作站（尤其列中部）→ tooltip 完整显于所有 UI 之上；② 工作站不足一列时悬停/滚轮面板上方空白区 → 无反应（仅实际单元格区域有效）

**2026-08-26（五十四）：JEI 物品区遮挡 BRBE 查询界面 tooltip——浮层活跃时强制隐藏 JEI/REI（两分支同步）**——用户反馈：JEI 的物品区（ingredient list overlay）会挡住 BRBE 查询界面的所有 tooltip（JEI 的 overlay 渲染在 BRBE 浮层之后，53 轮把 BRBE tooltip 挪到其渲染流末尾仍在其下）。
- 修复（`BetterRecipeBookClientFabric` 的 END_CLIENT_TICK，两分支同步）：每 tick 判定 `RecipeViewerOverlay.isActive() || PinOverlayManager.hasPins()`（**BRBE 自身浮层**——查询 viewer/固定 pin；刻意**不含**原版配方书 overlay 与普通容器界面，避免误伤用户在配方书界面看 JEI 的习惯）→ 活跃时 `OverlayHider.setOverlaysHidden(true)`（**无条件**，不依赖 hideReiJeiOverlay 配置）；不再活跃即恢复 `hideReiJeiOverlay` 配置值。查询界面是硬模态，JEI 物品列此时无交互意义，隐藏是正确语义
- ⚠️ 本次部署时 26.2 实例**正在运行**（部署脚本无 gate 检查的教训——ps 输出被 head 截断没触发拦截），已用 `cp` 覆盖运行中 jar（zip 读取损坏风险，参考 2026-08-25 20:47 先例，需用户重启实例）
- 已构建、已部署两实例（备份 20260826-174722；26.2 3225a48e…、1.21.11 b633a341…）。验证：打开查询 viewer / pin（hideReiJeiOverlay=关）→ JEI 物品列消失、悬停任何对象 tooltip 完整（不被任何 UI 遮）；关闭 viewer → JEI 物品列恢复原配置状态

**2026-08-26（五十五）：JEI 遮挡修复改走 mixin 权威门——遮蔽条件扩为"配置开 OR BRBE 浮层活跃"（两分支同步）**——用户反馈（五十四部署后）：tooltip 仍被 JEI 物品界面挡住。根因：五十四轮走的 `OverlayHider.setOverlaysHidden(true)` 依赖 `JeiHudHider` 反射 `mezz.jei.common.Internal.getClientToggleState()`/`IClientToggleState.isOverlayEnabled`（对 26.2 真实 JEI 30.x 静默失败，ensureHidden 空转）；而真正生效的 `hideoverlay/IngredientListOverlayMixin`/`BookmarkOverlayMixin` 守卫是**配置** `hideReiJeiOverlay`（用户配置关 → 不隐藏）。
- 修复：**遮蔽 mixin 成为权威门**——守卫改为 `hideReiJeiOverlay || RecipeViewerOverlay.isActive() || PinOverlayManager.hasPins()`（viewer/pin 活跃时无条件取消 JEI 物品列表与书签层绘制；关闭后恢复配置行为）。五十四轮的 tick 反射逻辑保留作辅助（有效时提前切换 JEI 状态，无效时无害）
- 已构建、已部署两实例（备份 20260826-175211；**部署前硬性检查无实例运行**——此前五十四轮部署时实例在跑造成 zip 覆盖风险，已收敛为 `ps ... | grep -q && exit 1` 门禁）；md5 一致（26.2 3385ebce…、1.21.11 118c9169…）。验证：打开查询 viewer/pin（hideReiJeiOverlay=关）→ JEI 物品列与书签列消失、悬停任意对象 tooltip 完整；关闭 viewer → JEI 恢复

**2026-08-26（五十六）：JEI 遮挡诊断版——遮蔽 mixin 命中即打一次性日志（两分支同步）**——用户截图（180202）证明 JEI 物品列仍在 BRBE tooltip 之上；已核实：部署 jar 的 mixin 字节码是 55 轮新守卫、fabric.mod.json 注册了 mixins.brbe-jei-common.json、真实 JEI 30.24 的 `IngredientListOverlay.drawScreen(Minecraft, GuiGraphicsExtractor, int, int, float)` 存在——理论上应生效但实测没生效（`required:false` 下 mixin 应用失败会静默）。加装诊断：守卫命中时打印一次性 WARN `[BRBE] JEI ingredient overlay hidden`（有日志=守卫执行、遮挡物另寻绘制路径；无日志=mixin 未应用、换目标）。
- 用户授权部署流程简化（2026-08-26）：只要产物完整部署即可，不必等待其确认（部署前仍保持进程检查，实例在跑则跳过覆盖——zip 覆盖风险不因授权而消失）
- 已构建、已部署两实例（备份 20260826-180704；26.2 5db3a180…、1.21.11 2694d7a4…）

**2026-08-26（五十七）：根因实锤——26.2 内嵌 mezz fork（841 源文件/1136 打包类）与真实 JEI 同名类冲突，改为 libs 依赖路线（仅 26.2；1.21.11 保留 fork 内嵌=无 JEI 路线）**——诊断链：① 遮蔽 mixin（require=1 后）启动无注入错误、守卫日志从未出现 → mixin 目标类疑似未命中；② JeiHudHider 反射的 `mezz.jei.common.Internal.getClientToggleState` 在真实 JEI 30.24 存在且签名一致，但 54/55 轮隐藏无效；③ 查证 26.2 源码树 `src/main/java/mezz/` 有 **841 文件**（含 `gui/overlay/IngredientListOverlay.java`、`common/Internal.java`——后者 `getClientToggleState()` 返回 **fork 自建 `ClientToggleState` 假状态**）→ 打包后 BRBE jar 与真实 JEI **1136 个类同名**，运行时类加载竞速——fork 类（无真实状态/无真实渲染循环）被抢先加载时：mixin 打不到真实 `IngredientListOverlay`、反射 toggle 的是 fork 假状态 → JEI 物品列永远显示
- 修复（26.2）：
  - `build.gradle` 依赖改 `implementation files("libs/jei-26.2-fabric-30.24.0.165.jar")`（删除源码 fork 后编译 mezz 类）
  - **删除 `src/main/java/mezz/` 全树**（841 文件，git 可恢复）；打包后 jar 中 `mezz/` 类 = **0**，与真实 JEI 零冲突
  - fabric.mod.json client entrypoints 移除 `BrbeJeiPluginsClientFabric`；改由 `BetterRecipeBookClientFabric.onInitializeClient` 以 `isModLoaded("jei")` 守卫调用（无 JEI 时不加载 mezz 引用类；`jei_mod_plugin` entrypoint 本身只在 JEI 存在时被读取）
  - **1.21.11 不动**：其 fork 为 135 文件 API 集（无 gui/overlay、无 Internal 冲突？——fork 含 Internal（getClientSyncedRecipes 需要）+实例无真实 JEI（`jei-1.21.11-fabric-27.4.0.22.jar.disabled` 被禁用）→ 无冲突；该实例即"无 JEI"场景验证位
- 已构建、已部署 26.2（备份 20260826-181854；md5 d059207e…）。验证：26.2 启动 → 打开查询 viewer（JEI 开启状态）→ JEI 物品列应随 BRBE 浮层消失（遮蔽 mixin/反射均打在真实 JEI 上）；关闭 viewer 恢复

**2026-08-26（五十八）：按用户要求回退五十七轮——内置无头 JEI（vendored fork）原样保留（仅 26.2）**——用户明确"不要动内置的无头 JEI，回退"：撤销全部 fork 移除改动——`src/main/java/mezz/` 全树恢复（841 源文件；jar 打包 mezz 类回到 1136）、build.gradle 撤 libs 依赖（恢复源码 fork 编译路线）、fabric.mod.json 恢复 `BrbeJeiPluginsClientFabric` client entrypoint、`BetterRecipeBookClientFabric` 删除 isModLoaded 守卫块（其他轮次改动保留）。
- 现状：26.2 回到 fork 内嵌状态（JEI 遮挡问题随之回到"fork 与真实 JEI 同名类冲突"的未解决状态）。**后续方案待用户定夺**（保留 fork 的前提下，可探索：不注入 JEI 类、改在 BRBE 侧更高层处理；或 fork 裁剪——用户已明确"不动"）
- 已构建回退版（jar md5 待部署时确认）；**部署因 26.2 实例正在运行被拦截**（未覆盖），待实例退出后 cp 即可

**2026-08-26（五十九）：BRBE 全部 tooltip 改走 GUI 帧末 deferredTooltip——顶层渲染（26.2，未动内置无头 JEI）**——用户要求"继续调查 Tooltip 最顶层渲染方案"：**不动 fork**，让 BRBE tooltip 渲染在任何 UI（含 JEI）之上。
- 关键实证：`GuiGraphicsExtractorAccessor.brbe$setDeferredTooltip(Runnable)` 已存在（早前给 PinOverlayManager 用）——**pin tooltip 走的就是 GUI 的 `deferredTooltip`**（在 `extractDeferredElements` 帧末、提取流最高 stratum 执行）→ **从未被 JEI 遮挡**；而 RecipeViewerOverlay 的 4 处 tooltip（station 悬停/弹窗槽位/网格工具提示/对象详情）此前 `gui.tooltip(...)` **就地提取**（提取流内，落在 JEI 之下）→ 被 JEI 物品列覆盖
- 修复：新增 `RecipeViewerOverlay.deferTooltip(gui, components, mx, my, style)`（同 PinOverlayManager 写法：`((GuiGraphicsExtractorAccessor) gui).brbe$setDeferredTooltip(() -> gui.tooltip(...))`），4 处就地调用全部替换；station 悬停的临时 pending 机制（字段+flush 方法+render() 两处 flush 调用）随之**删除简化**（deferredTooltip 单槽语义天然满足"一帧一个 tooltip"，且帧末自动清空）
- 时序依据：afterExtract（BRBE/JEI 浮层）先执行 → `extractDeferredElements`（deferredTooltip）后执行 = 屏幕提取流最终层
- 已构建、已部署 26.2（备份 2026-08-26 18:5x；md5 a53fc549…，部署前确认无实例运行）。验证：打开查询 viewer 悬停任意对象（工作站/配方按钮/网格）→ tooltip 完整显示在 JEI 物品列之上；pin tooltip 行为不变。**1.21.11 未改**（其渲染链无 deferredTooltip 机制且实例无 JEI）

**2026-08-26（六十）：修复残缺配方红罩盖住多配方堆叠图标的底层图标（三分支同步）**——用户反馈："替代配方组"的残缺配方红色遮罩会盖住其重叠图标（多配方组按钮上的双图标堆叠）中的下层图标；图标轮循可见（各配方结果不同）时无此现象，结果全部相同（轮循看起来静止）时出现。
- 根因（三版一致，已对照反编译字节码实证）：配方书页面按钮 `RecipeButton.renderWidget`/`extractWidgetRenderState` 在 `hasMultipleRecipes() && allRecipesHaveSameResultDisplay`（1.21.1：`hasSingleResultItem() && size>1`）时**先 `renderItem`(x+offset+1,y+offset+1) 后 `renderFakeItem`(x+offset,y+offset)**（1px 错位双图标"多配方"堆叠）；`incompletecrafting/RecipeButtonMixin.brbe$renderPartialOverlay` 原先注入在 **`fakeItem` 之前** → 红罩/红勾贴图恰好落在两个堆叠图标**之间** → 下层（后下 1px 的）图标被盖；结果各异的按钮不画堆叠（单图标轮循）→ 无此现象
- 修复：注入点 `fakeItem BEFORE` → **`blitSprite AFTER`**（槽位贴图之后、一切图标之前）→ 红罩位于槽位 sprite 之上、堆叠双图标之下（与替代配方 overlay 按钮 sprite→mask→icons 层级一致）；单图标路径像素级不变。**26.1.2 停维不改**
- 已构建 26.2/1.21.11/1.21.1（注入点 remap 已验证：1.21.11 产物 annotation target 已映射 `class_332;method_52706…`；1.21.1 保持官方字符串。**部署待游戏实例关闭**（部署前进程检查）

**2026-08-26（六十一）：查询 viewer 底部标签滑动窗口规则重申 + 标签 tooltip 滑动指示（两分支同步）**——用户重申两条窗口规则并新增指示器需求：
1. 选中标签位于窗口第 6 个（从左数）时，再向右选中 = 同时选中下一标签 + 窗口右滑；
2. 选中标签位于窗口第 6 个（从右数）时，再向左选中 = 同时选中上一标签 + 窗口左滑；
3. 每个底部标签 tooltip 加工作站列同款滑动指示：◀（实心左三角）/◁（空心）/▶（实心右三角）/▷（空心）；左三角在标题右侧隔 **4 个空格**，右三角在左三角右侧隔 **1 个空格**；滑到最左端左三角空心、最右端右三角空心
- **规则 1/2（`RecipeViewerOverlay.mouseScrolledTabs`）**：旧逻辑只在选中**跑出窗口边缘**时滑动（选中被钉在窗口边缘）；现改为选中位于窗口第 6 槽及更靠边缘时随选中**同向滑动一格**——右选中：`slot >= 5`（第 6 个即 0-based 槽 5 及右侧）→ `tabWindowStart+1`；左选中：`slot <= 4`（10 槽窗口第 6 个从右数 = 0-based 槽 4 及左侧）→ `tabWindowStart-1`；窗口滑动一格 + 选中前进一格 → 高亮视觉上一直停在原槽位；原"保持选中可见"兜底（点击等途径到达边缘）保留
- **规则 3（`drawTabTooltip` + 新 `TabMarkerTitleTooltipComponent`）**：指示器仅在窗口真正可滑动（类别数 > `MAX_TABS`=10）时显示（与工作站列"窗口启用才显示标记"一致）；◀ 实心 = 窗口左侧仍有内容（`tabWindowStart > 0`），◁ 空心 = 最左端；▶ 实心 = 右侧仍有内容（`tabWindowStart < maxStart`），▷ 空心 = 最右端；全部标签 tooltip 显示同一窗口状态。左三角锚点 = 标题宽 + 4×spaceW（16px），右三角锚点 = 左三角锚点 + 左三角字形宽 + 1×spaceW（4px）——沿用工作站列的**精确像素锚点**（自定义 tooltip 行组件），不用空格拼接（4px 空格网格无法复现任意字形 advance，会漂移）
- **渲染机制**：26.2 标签 tooltip 改走 `deferTooltip`（帧末提取流顶层，与（五十九）一致——原 `setComponentTooltipForNextFrame` 无 ClientTooltipComponent 重载，组件化后顺势升级）；1.21.11 改**pending 字段 + render 末尾 flush**（`pendingTabTooltip` 四字段 + `flushTabTooltip`，与本分支工作站列机制一致——悬停 tooltip 在 tabs-behind pass 生成，box 在它之后绘制，就地渲染会被盖）
- 已构建、已部署两实例（26.2 备份 `20260826-21:2x`、md5 `ad471bc6…`；1.21.11 备份同刻、md5 `576fe877…`；部署前确认无实例运行）。验证：R/U 查询打开 viewer → 悬停底部标签 → tooltip 为「标题 4空格 ◀ 1空格 ▶」（窗口可滑动时），滚到最左端显示 ◁、最右端显示 ▷；滚轮在标签上从第 1 个滚到第 6 个后继续右滚 → 选中切换与窗口滑动同步（高亮停在原槽位）；反向同理；≤10 个类别时 tooltip 无指示器（无窗口可滑）

**2026-08-26（六十二）：查询 viewer 裁切的工作站列空白区吞掉"点击外部关闭"（两分支同步）**——用户反馈（截图 212914-1）：工作站数量少于对象区行数时列面板按（三十八?）的裁切特性只画实际内容，但面板上方的空白条仍被算作 viewer 命中区——点击那里无法关闭 viewer（点击 viewer 外部本应关闭）。
- 根因：`inBox`（点击吞掉区）与 `contains`（modal 掩码/pin 让位）都按**整条列宽 × 盒子全高**矩形判定（`panelLeft()..panelLeft()+STATION_COL_WIDTH × boxY..boxY+boxH`），而 `drawStationColumnSurfaces`/`stationColumnPanelRect` 把面板**裁切到实际内容**（colTop 随 shown 上移，空面板时整个列都不画）——判定区与绘制区不一致
- 修复（`RecipeViewerOverlay`，两分支）：`inBox` 改为「盒子全尺寸矩形 ∪ 裁切后列面板矩形（`stationColumnPanelRect(shown)`，shown=min(items, rows)）」；`contains` 同步改为「盒子（含下方标签条）∪ 裁切后列面板」——空白条属于背景：点击→关闭 viewer（且点击被吞不穿透容器，与原"点击外部关闭"语义一致）、悬停→穿透到下层屏幕、下层 pin 恢复可交互。`exclusionArea()`（JEI 避让矩形）**保持整条矩形不变**（JEI 多避让无副作用，且单 Rect2i 表达不了 L 形）
- 已构建、已部署两实例（26.2 备份 `20260826-214xxx`、md5 `ae2a485f…`；1.21.11 备份同刻、md5 `c4751f22…`；部署前确认无实例运行）。验证：打开站点数少（如 1-2 个工作站）的查询类别 → 点击列面板上方的空白条 → viewer 应立即关闭；点击面板本体/盒子/标签仍保持打开；滚动判定区（原已裁切）不变

**2026-08-26（六十九）：Ctrl+O 浏览补上"创建其他类别"——浏览时标签条显示全部完整池非空类别（两分支同步）**——用户反馈：按 Ctrl+O 后"并没有创建其他类别"——只有查询相关类别有标签，其他类别根本没出现。根因：标签条仍由 `visibleCategories()` 按查询内容（hasContent(queryTarget, usage)）过滤，浏览模式只换了当前标签的数据源，标签条没换。
- **修复**：`visibleCategories()` 在 browse 模式返回新 `browseCategories()`——遍历 `RecipeViewerCategories.all()`，取**完整池非空**的类别（grid 类别判 `allGridItems()`，其余判 `filterByRecipeBookStations(allEntries(), cat)`），隐藏集（隐藏无配方书工作站）照常应用；结果缓存（`cachedBrowseCategories`），失效时机 = 隐藏集重建（`hiddenCategoryIds()` 内清缓存）或 browse 模式翻转——完整池只在每次进入浏览时枚举一次，不逐帧重算
- **附带**：`refreshCurrentCategory` 非 grid 分支补 `clampBoxX()`（浏览进出后盒子宽度随标签条变化，与 `switchCategory` 一致）
- 效果：Ctrl+O 后底部标签条出现**所有**有对象的类别标签（含原本对该查询无内容的类别），每个标签显示自己类别的完整池；再按 Ctrl+O 恢复为只有查询相关类别的标签条
- 已构建、已部署两实例（26.2 备份 `20260827-01:0x`、md5 `0a490231…`；1.21.11 备份同刻、md5 `f5d52262…`；部署前确认无实例运行）。验证：R/U 查询某个仅命中 1-2 个类别的物品 → Ctrl+O → 底部标签条应**新增**许多类别标签（合成/烧炼/切石/锻造/铁砧/酿造/研磨/燃料…），逐个点击各显示其全部对象；再按 Ctrl+O → 标签条与内容都恢复

**2026-08-26（七十）：Ctrl+O 浏览后选中标签高亮保持——非网格分支补 repaginateToSelected（两分支同步）**——用户反馈：按 Ctrl+O 后"当前标签的选中状态会改变"。根因：浏览标签条比查询标签条多出大量**插在前面**的类别，而进入/离开浏览走的 `refreshCurrentCategory` 非 grid 分支没有像 grid 分支与 `switchCategory` 那样调用 `repaginateToSelected()` → 当前类别下标可能滑出 10 槽滑动窗口 → 选中标签不再被绘制（高亮消失/移位）。
- 修复：`refreshCurrentCategory` 非 grid 分支在 `clampBoxX()` 后补 `repaginateToSelected()`——模式翻转后窗口总是包含选中标签，选中高亮保持在屏幕内
- 已构建、已部署两实例（26.2 备份 `20260826-233149`、md5 `de7f3a64…`；1.21.11 备份同刻、md5 `75227746…`；部署前确认无实例运行）。验证：R/U 查询任意物品 → Ctrl+O → 当前标签高亮应始终可见（窗口滑动到包含它）；再按 Ctrl+O 恢复

**2026-08-26（七十一）：标签条对齐对象列——运行时纵向裁切标签贴图（两分支同步）**——用户要求：标签贴图**运行时纵向裁切**（不改贴图文件），使每个标签都对齐**每列的中心铅垂线**，且标签之间的间隔不变。
- 几何：对象列中心在 `boxX+16+i*25`（按钮 `boxX+4+i*25`、宽24、间距25）；旧标签条 `tabX(i)=boxX+3+i*27`（27 宽 27 间距）→ 中心偏 0.5px、间距 27≠25，越靠右越偏
- **裁切**：底部标签贴图（35×27）旋转 -90° 绘制，贴图纵向 27 行 = 屏幕标签宽度；新增 `TAB_CROP=1`，左右两半 blit 的源 v 偏移 1、绘制高度改 `TAB_WIDTH=25`（原为 27）→ 屏幕宽 25，两端各裁 1px（纯运行时裁切，贴图文件未动）
- **对齐**：`TAB_WIDTH=25` = 对象列间距；`tabX(i)=boxX+4+i*25`（标签左缘对齐第 i 列左缘）→ 图标（`x+4..x+20`）中心恰好落在列中心 `boxX+16+i*25`；标签 25 宽 25 间距**无缝拼接**，间隔均匀不变
- 命中/滚轮/加宽随动：点击与 hover 的 `inside(..., TAB_WIDTH, ...)`、`overTabStrip`、`ensureTabWidth` 全部随 `TAB_WIDTH=25` 自动一致（10 标签时盒宽恰好 = 10 列宽 258，无需再加宽）
- 已构建、已部署两实例（26.2 备份 `20260826-234743`、md5 `3c29eff9…`；1.21.11 备份同刻、md5 `c86d64e5…`；部署前确认无实例运行）。验证：R/U 查询 → 底部标签条每个标签的图标中心应正对各列中心铅垂线，标签等宽相接、间距均匀；点击/滚轮/滑动窗口行为不变

**2026-08-26（七十二）：标签间隔恢复 2px——面板再裁窄 2px（两分支同步）**——用户反馈（七十一版）"裁切不够"：标签之间视觉间隔比原来明显小了。像素分析证实：非选中贴图纵向 0..3 行与 24..26 行是角落渐变/透明（选中贴图只有 26 行透明），原 27px 间距下贴图自身的角渐变呈现为约 2px 的视觉间隔。
- 修复：间距（`TAB_WIDTH=25`）与列中心对齐**不变**；面板绘制宽度拆为独立常量 `TAB_DRAW_WIDTH = TAB_WIDTH-2 = 23`（blit 高度 23、源 v 偏移 `TAB_CROP=(27-23)/2=2`，每端裁 2 行）→ 标签之间恢复 2px 均匀间隔
- `tabX(i)` 起点 +4→+5：图标（`x+3..x+19`，中心 `x+11`）仍精确落在列中心 `boxX+16+i*25`
- 命中/滚轮/盒宽仍按间距 `TAB_WIDTH=25`（点击命中含 2px 间隙，无死角）；`iconX` 用 `TAB_DRAW_WIDTH` 居中
- 已构建、已部署两实例（26.2 备份 `20260826-235901`、md5 `6fef20c5…`；1.21.11 备份 `20260826-235520`、md5 `ecad95c7…`；部署前确认无实例运行）。验证：R/U 查询 → 标签图标中心对正各列中心铅垂线，标签之间约 2px 均匀间隔（与老版本观感一致）；点击/滚轮/滑动窗口行为不变

**2026-08-27（七十三）：标签裁切改为裁中间——保留两端圆角（两分支同步）**——用户修正（七十二）"应该裁中间部分，而不是边缘"：边缘裁切把标签两端的圆角削平了。
- 重构：删除 `TAB_CROP`（每端裁 N 行），改 v 向拼接——贴图纵向（=屏幕标签宽度）保留 `[0, TAB_V_TOP)` 与 `[TAB_V_TOP+TAB_V_CUT, 27)` 两段（`TAB_V_TOP=12`、`TAB_V_CUT=4`、`TAB_V_BOTTOM=11`），**抽掉中间 4 行**，两端圆角完整保留；绘制由 2 个 blit 改为 4 个（每个水平半段拆上/下两段拼接）
- 宽度与间距不变：`TAB_DRAW_WIDTH=23`、间距 25、图标中心仍精确对正列中心 `boxX+16+i*25`
- 已构建、已部署两实例（26.2 备份 `20260827-000800`、md5 `640bc594…`；1.21.11 备份同刻、md5 `10fd30e5…`；部署前确认无实例运行）。验证：R/U 查询 → 标签两端圆角应完整（不再被削平），标签间约 2px 间隔，图标中心对正列中心

**2026-08-27（七十四）：标签裁切量修正——中间只抽 2 行，视觉间隙复刻原版（两分支同步）**——用户反馈（七十三版）"裁的有点多了，中间的间距看起来比之前宽了"。逐像素分析贴图定位根因：非选中贴图的左右竖边框线在 v=0 / v=25 行（v=26 全透明）；七十三版抽中间 4 行后右竖线被拼到屏幕 x=21 → 相邻标签间出现 **3 列纯空**（原版仅 1 列），视觉间隙从"线到线 2px"变成 4px，且面板 23px 偏窄。
- 修复：`TAB_V_TOP=13`、`TAB_V_CUT=2`、`TAB_V_BOTTOM=12`（抽 v=13,14 两行），面板回到 `TAB_DRAW_WIDTH=25` = 间距——左竖线 x=0、右竖线 x=23、下标签左竖线 x=25 → **纯空 1 列 + 线到线 2px**，与原版 27px 间距时代的观感完全一致（脚本逐列验证：原版与新版同为"末可见列/首可见列差 2"）；`tabX(i)` 起点回到 `boxX+4+i*25`（图标中心 `x+12 = boxX+16+i*25` 仍精确对正列中心）
- 已构建、已部署两实例（26.2 备份 `20260827-002006`、md5 `b3226c31…`；1.21.11 备份 `20260827-001648`、md5 `95e2dbd0…`；部署前确认无实例运行）。验证：R/U 查询 → 标签形状与原版一致（左右边框线完整、间隔与原版观感相同），标签在 25px 间距下对正每列中心

**2026-08-27（七十五）：浏览模式盒子位置钳制修复 + 浏览快捷键 Ctrl+O 改为 O（两分支同步）**——用户反馈两项：
- **位置 bug**：Ctrl+O（浏览）展示所有对象时"无法正常调整界面位置（30px 边缘间距）"。根因：`rebuildWithHits`/`rebuildGrid`（切类别/进出浏览时）只做 `boxY = max(0, bottomAnchor - boxH)`——只钳下界 0，**没有 open() 那样的 30px 双距钳制**；浏览模式盒子变高（满 5 行 + 标签条）后顶部贴屏幕顶（丢 30px 间距）、底部可能超出屏幕
  - 修复：新增 `clampBoxToAnchor()`——`overlayH ≤ guiH-60` 时钳 `[30, guiH-overlayH-30]`（30px 双距），否则全屏内 `[0, guiH-overlayH]`；刷新 `bottomAnchor = boxY+boxH` 使后续 rebuild 保持钳制后位置。`clampBoxX()` 同步升级为同样的 30px 双距（盒子变宽时水平方向也不贴边）。`rebuildWithHits`/`rebuildGrid` 改用之
- **快捷键**：浏览开关从 Ctrl+O 改为 **O**（去掉 MOD_CONTROL 检查；仍保持"鼠标在查询界面内才监控"的门控，注释同步更新）
- 已构建、已部署两实例（26.2 备份 `20260827-003346`、md5 `1b06ca3c…`；1.21.11 备份同刻、md5 `f398cf6d…`；部署前确认无实例运行）。验证：① R/U 查询 → 按 O（不再是 Ctrl+O）→ 全部对象展示，盒子顶部/底部应保持 ≥30px 屏幕边缘间距（不再贴边/出界），切换类别、进出浏览位置稳定；② 界面外按 O 无反应

**2026-08-27（七十六）：信息类别模组名改本模组 + 切石机类别改名切石 + 模组名精简（两分支同步）**——用户三项要求：
- **信息类别所属模组**：`drawTabTooltip` 的模组名行对 `InfoRecipeCategory` 特判——其图标是原版物品（成书）本会解析为 "Minecraft"，现改为 `ModNameUtil.resolveModName("zzzbrbe")`（反射读本模组 metadata 名，样式与其它类别一致 BLUE+ITALIC）
- **切石机类别名 → 切石**：语言键 `zzzbrbe.category.stonecutting`：zh_cn "切石机"→"切石"、zh_tw "切石機"→"切石"、en_us "Stonecutter"→"Stone Cutting"（ja/pl/ru/tr 回退 en_us 无需改）
- **模组名精简**：两分支 `fabric.mod.json` 的 name 由 "Better Recipe Book (Adorable♡Girl aVa Seriously 🔥Extended🔥. Oh, and also Teamed Up with Great Mr.DeepSeek)" 改为 **"Better Recipe Book (Adorable♡Girl aVa Seriously Extended)"**（信息类别模组名行/ModMenu 等显示同步生效；tip.7 的 DeepSeek 文案未动）
- 已构建、已部署两实例（26.2 备份 `20260827-003538`、md5 `83a02e33…`；1.21.11 备份同刻、md5 `3f94eaac…`；部署前确认无实例运行）。验证：① 悬停"信息"标签 → 模组名行显示"Better Recipe Book (Adorable♡Girl aVa Seriously Extended)"；② 标签条/类别名显示"切石"；③ 模组列表（ModMenu/暂停界面 mod 列表）显示新名

**2026-08-27（七十七）：信息类别模组名修复——改为直接调用 FabricLoader API（两分支同步）**——用户反馈（七十六版）信息类别来源模组名显示的是 "zzzbrbe" 而不是完整名。根因：七十六版用 `ModNameUtil.resolveModName("zzzbrbe")`——它走**反射**调 FabricLoader，独立实验证实该反射链路在 Java 25 + fabric-loader 0.19.3 下抛 NoClassDefFoundError（asm 依赖解析问题）等异常被 catch 吞掉 → 落到 fallback。
- 修复：新增 `RecipeViewerOverlay.selfModName()`——**直接 import `net.fabricmc.loader.api.FabricLoader` 调用** `getModContainer(MOD_ID).getMetadata().getName()`（fabric 单模块编译期依赖，无需反射），样式与其他模组名行一致（BLUE+ITALIC）；`drawTabTooltip` 特判分支改用之
- 已构建、已部署两实例（26.2 备份 `20260827-004005`、md5 `dd3699a8…`；1.21.11 备份同刻、md5 `23e86bd5…`；部署前确认无实例运行）。验证：悬停"信息"标签 → 模组名行显示 "Better Recipe Book (Adorable♡Girl aVa Seriously Extended)"

**2026-08-27（一百一十一）：查询 viewer 按钮顺序与 pin 排序脱节——不可合成 pin 对象不置顶 + pin 贴图错挂（两分支同步）**——用户反馈：配方书中 pin 不可合成配方后，查询界面对应对象无法移动到首位（被前方残缺/可合成配方拦住），pin 贴图却挂在最前面的对象上（"pin 贴图与对象的放置并不挂钩"）。
- 根因（反编译 26.2 原版 `OverlayRecipeComponent.init` 证实）：原版 init 从 `getSelectedRecipes(CRAFTABLE)` 开始建按钮，再拼 `NOT_CRAFTABLE`——按钮列表是**可合成优先**顺序；而 viewerRecipes 按 pin → 可合成 → 残缺 → 不可合成重排。两者顺序不同导致：① 按钮位置按列表索引铺排 → 不可合成的 pin 对象视觉上被可合成/残缺对象"挡住"（排布不对齐 viewerRecipes）；② `drawViewerPinMarkers` 的"按钮 i ↔ 第 i 条 viewerRecipes 条目"索引映射错位 → pin 贴图挂在别的按钮上（此前可合成 pin 恰好在两种顺序的前部，故一直未暴露）
- 修复（`RecipeViewerOverlay.showPage`）：`overlay.init` 之后、按钮位置铺排**之前**，按 `pageEntries`（= viewerRecipes 当前页切片）顺序重排 `getRecipeButtons()`——按钮 id（`OverlayRecipeButtonAccessor.brbe$getRecipe()`）→ pageEntries 索引映射，稳定排序，未命中项沉底。此后：按钮视觉顺序 = viewerRecipes 顺序（pin 置顶生效），`drawViewerPinMarkers` 索引映射归位（贴图挂到正确对象）
- 已构建、已部署两实例（26.2 备份 `20260827-205614`、md5 `a64ef1bd…`；1.21.11 备份同刻、md5 `9b59716c…`；部署前确认无实例运行）。验证：① 配方书 pin 一个**不可合成**配方 → R/U 查询该物品 → 对应对象排在最前（带 pin 贴图），贴图不在别的对象上；② 可合成/残缺 pin 行为不变；③ 翻页后 pin 贴图仍与对象绑定

**2026-08-27（一百一十）：pin 提取后原组残缺配方退化为不可合成——管线新增 Stage 6b 残缺标记重放（两分支同步）**——用户反馈：pin 替代配方组的其中之一后，组内其余残缺配方全部变成不可合成配方。
- 根因：残缺标记/注入按 RecipeCollection **对象身份**记录（tagger 弱键 WeakHashMap）；pin 提取生成的新组（rest 包/pin 包）是全新 RecipeCollection 对象 → 无残缺标记、缺材料配方不在 craftable 集合 → 渲染退化为不可合成（红罩/灰色标志丢失）
- 修复：管线 Stage 6 之后新增 **Stage 6b** `brbe$reapplyPartialMarking(list)`（`mixins/pipeline/RecipeBookComponentMixin`）——对最终管线列表重放残缺标记流程，参数与 incompletecrafting 主 passes 完全一致：① `markPartialMaterials(collection, inventoryItems, counts, markItems, onInventoryScreen)`；② carried/副手非空时 `elevateFullyCraftableWithCarried`；③ `onInventoryScreen && showAllRecipesInSurvival` 时 `elevateFullyCraftable3x3`；④ 有 partial 标记的组把残缺 ID 注入 craftable（`RecipeCollectionAccessor.brbe$getCraftable().add`）。已检查过的原组由 `wasChecked` 自动跳过（零副作用），仅未检查的重打包组真正生效；注入是 Set.add，幂等
- 效果：rest 包内残缺配方保持红罩/灰色；pin 出的变体（若本身残缺）也保持残缺状态
- 已构建、已部署两实例（26.2 备份 `20260827-204050`、md5 `cba6130b…`；1.21.11 备份 `20260827-204022`、md5 `f1dd3b3b…`；部署前确认实例未运行）。验证：pin 替代配方组 1 个变体 → 原组（rest 包）内的残缺配方仍显示残缺（红罩/灰色），不变成不可合成；pin 出的独立变体同样保持残缺状态；取消 pin 恢复原组后残缺正常

**2026-08-27（一百零九）：替代组 pin 变体后原组不再重排 + 恢复普通配方网格层固定 + pin 组置顶（两分支同步）**——用户两项反馈：① pin 替代配方组中 1 个变体后，原配方组被提到首位（期望原组排序完全不受影响）；② 普通配方无法固定/取消固定了（只能进组后 pin 变体）。
- **根因一（原组重排）**：Stage 3 `applyPins` 与 Stage 4 `applyPartialSort` 用 `pinnedRecipeManager.has(...)`（组内**任一**配方被 pin 即整组按 pin 集合对待）→ 含 1 个 pin 变体的原组被置顶 + 划入 pinned 桶参与重排。修复：两处判定改为 `isFullyPinned(...)`（组内**每个**配方都被 pin 才置顶）——原组（部分 pin）按"未 pin"位置参与排序，排序不再受 pin 变体影响
- **根因二（普通配方不可固定）**：一百轮"组不能直接 pin"实现过宽——`AbstractContainerScreenMixin.onKeyPressed` 网格按钮分支**无条件**吞掉固定键（任何组都不允许直接 pin）。修复：单配方组（普通配方）→ 直接 `toggleFavourite(entry)` + 刷新 + 音效（恢复九十七轮老行为）；多变体组（替代配方组）→ 照旧吞键（规则 1：只能进组后 pin 单个变体）。附带收益：上一轮 pin 提取出的**独立单配方**（置顶带图钉）现在可按固定键直接取消固定
- **Stage 6 调整**：pin 组（独立单配方/副本组）从"紧跟原组之后"改为**置顶**（`collections.addAll(0, pinPacks)`，多个原组的 pin 组保持原组遍历顺序）；原组位置只保留 rest 组（原位替换，顺序不受影响）
- **附带修复（管线缓存隐患）**：`RecipeBookComponentMixin` 的管线缓存存/取改用**浅拷贝快照**（`new ArrayList<>(list)` 各一次）——此前缓存与 Stage 6 原地改写共用同一列表对象，缓存命中后再次运行 Stage 6 只会看到残留的重打包组（原组无处还原），可能导致列表被清空
- 已构建、已部署两实例（26.2 备份 `20260827-202647`、md5 `daf1961d…`；1.21.11 备份同刻、md5 `301d5f70…`；部署前确认无实例运行——先前 `pgrep -f KnotClient` 的 "RUNNING" 是匹配到探测命令自身命令行的误报）。验证：① pin 1 个变体 → 原组**保持原来位置**（不再跳到首位），提取的变体独立按钮置顶（带图钉）；② 再 pin 1 个 → 副本组置顶（带图钉），原组继续原位；③ 取消 1 个 pin → 回退到独立按钮；④ 全 pin 组照常置顶；⑤ 普通配方（单配方按钮）A 键直接固定/取消固定；⑥ 悬停多变体组按 A → 无反应（需进组 pin 变体）

**2026-08-27（一百零八）：pin 剥离式展示——1 个 pin 独立成组、≥2 组合成副本组（两分支同步）**——用户澄清真正预期交互：pin 替代配方组中 1 个配方后，该配方应**完整取出来作为独立配方**放在配方区（原替代配方组中移除它）；再 pin 1 个（共 2 个）→ 自动**组合成新的有 pin 贴图的替代配方组**。
- 重写 `CollectionPipeline.applyPinCopyGroups`（管线 Stage 6，改名 pin extraction 语义）：逐组剥离——原组重打包为「未 pin 变体组」+「pin 组」：
  - **1 个 pin** → pin 组为**独立单配方组**（按钮带 pin 贴图，`isFullyPinned` 命中）
  - **≥2 个 pin** → pin 组为**副本替代配方组**（只含 pin 配方，全 pin → 贴图判定命中）
  - **全 pin**（组内全部被 pin）→ 原组即 pin 组形态（不再重打包，保留贴图）
  - **取消 pin** → 变体回归原组（下次管线重算自动还原；重打包组从列表移除重建，幂等）
- 位置：原组位置替换为「rest 组 + pin 组（紧跟其后）」；新组 `selectRecipes(玩家物品栏, true)` 全选中（craftable 按真实物品栏）
- 上一版（一百零七"原组贴图仅全 pin"判定）保留：rest 组（未全 pin）无贴图 ✓
- 诊断日志更新为 `[BRBE-PINS] pin-extract: N recipes, M pinned -> rest X + pin-group M`
- 已构建、已部署两实例（26.2 备份 `20260827-193601`、md5 `d3dcef6e…`；1.21.11 备份同刻、md5 `5e1e05d3…`；部署前确认无实例运行）。验证：① 组内 pin 1 个变体 → 原组按钮变为「15 变体组（无贴图）」+ 其后「独立单配方按钮（带贴图）」；② 再 pin 1 个 → 「14 变体组」+「2 配方的副本组（带贴图）」；③ 取消 1 个 pin → 回退到「1 独立按钮」状态；④ 全 pin → 只剩原组（全 pin 组，带贴图）

**2026-08-27（一百零七）：原组按钮不再显示 pin 贴图——贴图仅属"全 pin 组"（两分支同步）**——用户详细复现：① pin 组内第 1 个变体后，原组（16 配方）整体出现 pin 贴图（被误认为"整体变成了副本组"），pin 变体本身带标识但无贴图；② pin 第 2 个变体后才出现真正副本组（2 配方）——此时"两个副本组"（16 配方假副本 + 2 配方真副本）并存
- 根因：网格按钮贴图判定 `pinnedRecipeManager.has(collection)` 是"**组内任一配方被 pin 即整组显示贴图**"——含 1 个 pin 变体的原组也带贴图，观感 = 整组变副本组（其实那个"16 配方副本组"就是原组本体，副本组功能本身已正常生成）
- 修复：新增 `PinnedRecipeManager.isFullyPinned(...)`（PinnableRecipeCollection / GenericRecipeBookCollection 两个重载：组内**每个**配方都被 pin 才 true）；三处网格贴图判定改为全 pin——
  ① `mixins/pins/RecipeButtonMixin`（原版配方书按钮）
  ② `mixins/scrollablepages/RecipeBookPageAnimationMixin`（翻页动画期间贴图）
  ③ `generic/GenericRecipeButton`（自研酿造/锻造配方书按钮）
- 保持不动：替代浮层变体贴图（`OverlayRecipeButtonMixin`，单变体被 pin 即贴图——组内 pin 的变体带图钉 ✓）；查询 viewer 的 pin 置顶/贴图（单对象视角）；网格排序置顶（含 pin 变体的原组仍置顶，老行为）
- 效果：pin 第 1 个变体 → 只有浮层变体带图钉，原组无贴图；pin 第 2 个 → 原组后出现"副本组"（2 配方，带贴图）；不会再有"16 配方假副本组"
- 已构建、已部署两实例（26.2 备份 `20260827-192509`、md5 `cfd0c8be…`；1.21.11 备份同刻、md5 `fbc6504f…`；部署前确认无实例运行）。验证：① pin 组内 1 个变体 → 浮层变体有图钉、原组按钮无贴图；② pin 2 个变体 → 原组后出现 1 个副本组（只含 2 个 pin 配方、带贴图），原组仍无贴图；③ 取消 1 个 pin → 副本组消失；④ 查询 viewer 置顶/贴图不受影响

**2026-08-27（一百零六）：副本组套娃漏洞修复 + 诊断日志（两分支同步）**——用户反馈替代配方组新规则"基本不能按预期运行"（副本组内容不对 + 其他错误）。静态审查确认**套娃漏洞**：副本组自身也是"全 pin 组"——stale 检测（弱引用注册表）一旦失效（弱引用 GC / 管线缓存重建 / 类别切换后原组对象重建），副本组会被当"原组"再生成"副本的副本"，列表无限套娃 → 副本组内容/数量错乱（"内容不对"）
- 修复：`CollectionPipeline.applyPinCopyGroups` 生成条件改为 **pin 变体 ≥2 且非全 pin**（`pinned.size() < getRecipes().size()`）——①全 pin 组的副本=源组，语义上无意义；②副本组（恒全 pin）永远被跳过 → **从根上杜绝套娃**，不再依赖外部队列清理
- 注册表从 WeakHashMap<原组, WeakReference<副本>> 简化为 **弱集合 PIN_COPIES**（幂等 stale 清理保留：缓存命中列表中的旧副本先移除再重建）
- 新增诊断日志：`[BRBE-PINS] pin-copy group: N recipes, M pinned variants -> copy`（生成副本时 INFO 打印；复现时凭日志核对原组条目数/pin 数）
- 已构建、已部署两实例（26.2 备份 `20260827-191259`、md5 `b4f3a9ea…`；1.21.11 备份同刻、md5 `f3ec7db2…`；部署前确认无实例运行）。验证：pin 组内 2/3 变体 → 副本组只含 pin 变体、打开浮层变体数正确；再 pin 至全 pin → 副本组消失（副本=原组无意义）；多次翻页/切类别/重开配方书 → 副本组不重复出现（无套娃）；日志显示生成一行

**2026-08-27（一百零五）：修复 OverlayRecipeButtonMixin 使替代浮层崩溃（两分支同步）**——用户反馈：一百零四部署后所有替代配方组无法展示配方（组为空）。日志实锤（26.2-Fabric/logs/latest.log）：
```
Mixin apply for mod zzzbrbe failed ... pins.OverlayRecipeButtonMixin 
→ @Shadow method getX()I ... was not located in the target class OverlayRecipeComponent$OverlayRecipeButton
→ mouseClicked ... Mixin transformation of OverlayRecipeComponent$OverlayRecipeButton failed
```
- 根因：`OverlayRecipeButtonMixin` 的 `@Shadow getX()/getY()` 是**继承自 AbstractWidget 的成员**（target 内部类未自己声明），Mixin 找不到 → 整个 Mixin 应用失败 → `OverlayRecipeButton` 类变换失败 → 替代浮层的按钮渲染/点击全部抛错 → 替代配方组显示为空
- 修复：去掉 `@Shadow getX()/getY()`，注入方法内运行时强转 `(AbstractWidget)(Object)this` 取坐标；访问器（`brbe$getOuterComponent`/`brbe$getRecipe`）方式不变；javadoc 注明"勿用 @Shadow 继承成员"（防回归）
- 已构建、已部署两实例（26.2 备份 `20260827-170454`、md5 `d034628d…`；1.21.11 备份同刻、md5 `4287d0a3…`；部署前确认无实例运行）。验证：① 任意替代配方组点击打开 → 变体按钮正常显示（不再为空）；② 组内 pin 变体左上角图钉仍在；③ 日志无 OverlayRecipeButtonMixin 相关 ERROR

**2026-08-27（一百零四）：替代浮层变体按钮 pin 贴图 + 清理 26.2 实例 pin 缓存（两分支同步）**——用户两项：① 清理 26.2 测试实例的配方 pin 缓存；② 副本替代配方组（/替代配方组打开后浮层）中被 pin 的配方按钮左上角也要显示 pin 贴图。
- ① 缓存清理：26.2 实例 gameDir 的 `brbe.pins`（旧命名空间）/`zzzbrbe.pins`/`zzzbrbe.pins.json`/`zzzbrbe.pinoverlays.json` 共 4 个文件移入 `/tmp/brbe-pins-backup-20260827/`（实例目录清空；`zzzbrbe.tabpins.json`（RBIP 标签 pin）保留——非配方 pin 缓存）
- ② 贴图：新增 `mixins/pins/OverlayRecipeButtonMixin`（26.2 注入 `extractWidgetRenderState` RETURN / 1.21.11 注入 `renderWidget` RETURN，target `OverlayRecipeComponent$OverlayRecipeButton`）：变体按钮绘制后按 `OverlayRecipeButtonAccessor.brbe$getRecipe()` 在组集合中定位 entry、`isPinnedEntry` 判定 → `RECIPE_BOOK_PIN_SPRITE`（x-4, y-4, 32×32，与网格按钮/查询 viewer 同款左上角）——替代配方浮层（含副本替代配方组）打开时，被 pin 的变体带图钉。注册进 `mixins.brbe-common.json`（client 段 `pins.RecipeButtonMixin` 之后）
- 已构建、已部署两实例（26.2 备份 `20260827-165821`、md5 `82f4a8e9…`；1.21.11 备份同刻、md5 `485adae2…`；部署前确认无实例运行）。验证：① 顶替组内 pin ≥2 个变体 → 打开副本组（或原组）→ 浮层中被 pin 的变体按钮左上角出现图钉，未 pin 变体无标记；② 查询 viewer 与网格按钮行为不受影响

**2026-08-27（一百零三）：替代配方组 pin 规则 + 查询 viewer pin 修正（两分支同步）**——用户反馈：配方书中 pin 不可合成配方/替代配方组后，查询界面对象没置顶、pin 贴图错误落在第一个可合成对象上；且 pin 替代配方组时大量无关物品被 pin（整组被 pin）。先修替代配方组 pin 规则：
- 规则 1（组不可直接 pin）：`AbstractContainerScreenMixin.onKeyPressed` 固定键分支重写——① 替代浮层（overlay）可见时，固定键只对**悬停的具体变体按钮**生效（`OverlayRecipeButtonAccessor.brbe$getRecipe()` → `entryForId` 在组集合中定位 → `PinnedRecipeManager.toggleFavourite(entry)` 切换**单个变体**）；未悬停变体时吞掉按键（防误 pin 整组）；② 网格按钮（配方组）悬停时固定键同样吞掉（组不能在网格层直接 pin，只能进组后 pin 变体）；RBIP 标签 pin 分支保留
- 规则 2（≥2 个 pin 变体 → 副本替代配方组）：`CollectionPipeline` 新增 Stage 6 `applyPinCopyGroups`（管线 mixin 在 `page.updateCollections` 前调用）：组内 pin 变体 ≥2 → 生成**副本组**（`new RecipeCollection(pinnedEntries)` + `selectRecipes(fillSearchSpaceStackedContents, true)`）**插在原组之后**——副本组全为 pin 配方 → `RecipeButtonMixin` 贴图判定自然命中（副本组按钮带 pin 贴图），点击打开只含 pin 配方的替代浮层；取消 pin 后变体归回原组（副本组下次管线重算自动消失）；幂等：旧副本先从列表移除再生成（弱键 + 弱引用注册表 `PIN_COPY_GROUPS`，防止缓存列表递归膨胀）
- 查询 viewer 错位随之修复：此前"整组被 pin"导致贴图/置顶落在组内所有对象上（含可合成变体）——规则 1 后只 pin 单个变体，查询界面置顶+贴图与之一致
- 新 API：`PinnedRecipeManager.toggleFavourite(RecipeDisplayEntry)`（单变体切换，key 与 isPinnedEntry 同源）
- 已构建、已部署两实例（26.2 备份 `20260827-164922`、md5 `d04a9b21…`；1.21.11 备份同刻、md5 `1c55346a…`；部署前确认无实例运行）。验证：① 配方书网格按钮按固定键 → 无任何反应（组不能直接 pin）；② 点击组打开替代浮层 → 悬停某个变体按 A → 只 pin 该变体（其它变体不受影响）；③ 组内 pin 2 个变体 → 原组按钮**后面**出现"副本组"按钮（带 pin 贴图），点击打开的浮层只含这两个 pin 配方；④ 对一个变体取消 pin → 副本组只剩 1 个（<2）自动消失；⑤ 查询界面：pin 变体对象置顶+贴图正确（不再错位到可合成对象）

**2026-08-27（一百零二）：查询 viewer pin 对象排在可合成配方前面（两分支同步）**——用户实测发现上轮"pin 置顶"未生效：查询 viewer 的对象列表在 `rebuildWithHits` 有「可合成 > 残缺 > 不可合成」排序（`recipeRank`），它覆盖了 `categoryHits` 的 pin 置顶——pin 对象被按可合成状态重新排序。
- 修复：`recipeRank` 排序优先级改为 **3 = pin 标记 > 2 = 完全可合成 > 1 = 残缺 > 0 = 不可合成**——pin 对象恒排最前（即使可合成配方也排在它之后），同档内保持原顺序（List.sort 稳定）；`categoryHits` 置顶保留（双重保证，浏览模式等路径同样生效）
- 已构建、已部署两实例（26.2 备份 `20260827-162242`、md5 `d4c6e01a…`；1.21.11 备份同刻、md5 `29c91756…`；部署前确认无实例运行——26.2 实例此前正在运行，构建完等用户关闭后部署）。验证：查询任意对象 → pin 配方对象排在第一位的**最前**（在完全可合成配方的上面），左上角图钉；其余对象按 可合成→残缺→不可合成 排列

**2026-08-27（一百零一）：pin 状态传入查询界面——置顶 + 左上角贴图（两分支同步）**——用户澄清正确需求：**配方书中的 pin 状态传入查询界面**（R/U viewer），带 pin 标记的对象排在前面并加 pin 贴图；**配方书与查询界面的交互逻辑一律不改**。本轮同时**回退一百轮"固定快捷键只增不减"**（用户反馈"现在我在配方书中无法取消pin配方"）。
- 回退：配方书内 pin 键恢复 `addOrRemoveFavourite` toggle（原版书 `AbstractContainerScreenMixin.onKeyPressed` 两分支、自研书 `GenericRecipeBookComponent.keyPressed`）；`PinnedRecipeManager.addFavourites` 两个重载删除；`if (true)` 死代码清理保留（无行为影响）
- 打通判定：`PinnableRecipeCollection.idFor` 改 public static（某条 `RecipeDisplayEntry` 的稳定 pin key = SHA-1(category|group|display)）；`PinnedRecipeManager.isPinnedEntry(entry)` 查询该 key 是否在 pin 集合——同一条目（display 值相等）在配方书与查询 viewer 中 key 相同，**原版/known 配方天然匹配**（mod synthetic 条目无配方书 pin 来源，不匹配无妨）
- 置顶（`RecipeViewerOverlay.categoryHits`）：命中列表重排——pinned 条目全部移到最前（保持相对顺序），仅命中数 >1 时执行
- 贴图（`RecipeViewerOverlay.drawViewerPinMarkers`）：对象按钮绘制后按「按钮 i ↔ 当前页第 i 条 viewerRecipes」画 `RECIPE_BOOK_PIN_SPRITE`（x-4, y-4, 32×32，与配方书同款左上角），paged/非 paged 两分支均调用
- 已构建、已部署两实例（26.2 备份 `20260827-161056`、md5 `acc7c708…`；1.21.11 备份同刻、md5 `c36dd700…`；部署前确认无实例运行）。验证：① 配方书中 pin 若干配方 → R/U 查询任意对象 → 对应类别中 pin 配方对象排在最前且按钮左上角有图钉（翻页后仍在）；② 配方书内 A 键再次按同一配方 → **可正常取消**（toggle 恢复）；③ 查询界面原有交互（A 键等）不受影响

**2026-08-27（一百）：配方书 pin 配方置顶+贴图收尾——固定快捷键只增不减（两分支同步）**——用户需求：① 配方书（所有配方书）中 pin 配方在相关类别提到最前面；② 按钮左上角 pin 贴图；③ 用户不能以固定快捷键取消其配方书中的 pin 状态。
- 现状盘点（26.2/1.21.11 已有实现，本轮确认并收尾）：置顶——原版书走 `CollectionPipeline.applyPins`（管线 Stage 3）、自研酿造/锻造书走 `IPinningComponent.brbe$sortByPinsInPlace`（GenericRecipeBookComponent.updateCollections）；贴图——原版 `RecipeButtonMixin`/自研 `GenericRecipeButton` 均 `blitSprite(RECIPE_BOOK_PIN_SPRITE, getX()-4, getY()-4, 32, 32)`（图钉 sprite icon 位于左上角 → 视觉 = 按钮左上角），翻页动画 `RecipeBookPageAnimationMixin` 同步绘制防翻页丢失
- ③ 实现：`PinnedRecipeManager` 新增**只增不减** `addFavourites(...)`（GenericRecipeBookCollection / PinnableRecipeCollection 两个重载：仅补全新 id，已 pin 不取消；version++ 仅在有新增时）替换配方书内 pin 键的 `addOrRemoveFavourite` toggle——原版书 `AbstractContainerScreenMixin.onKeyPressed`（overlay 按钮分支 + 主按钮分支）、自研书 `GenericRecipeBookComponent.keyPressed` 全部改走 `addFavourites`。**固定快捷键（默认 A）在配方书内不再取消任何 pin**；取消途径保留在其它界面（查询 viewer 的 pin 浮层等）
- 顺带清理：`IPinningComponent` / `mixins/pins/RecipeBookComponentMixin` 的 `if (true)` 死代码分支
- 已构建、已部署两实例（26.2 备份 `20260827-160019`、md5 `ff394e8c…`；1.21.11 备份同刻、md5 `397717fd…`；部署前确认无实例运行）。验证：① 配方书内对任意配方按固定键 → 按钮左上角出现图钉贴图、该配方跳到当前类别最前（翻页动画期间贴图不丢）；② 再次对同一配方按固定键 → **不再取消**（贴图/置顶保持）；③ 酿造台/锻造台配方书同行为；④ 已 pin 配方在关书重开后仍置顶+贴图（brbe.pins 持久化）

**2026-08-27（九十九）：查询系统全部 tooltip 背景调淡（两分支同步）**——用户要求：查询系统（R/U viewer + pin）所有 tooltip 的背景透明度调高（更透明）。经确认：背景 alpha 240（94% 不透明）→ 160（63%）。
- 机制：原版 tooltip 背景是 sprite 渲染（`TooltipRenderUtil`：`tooltip/background` + `tooltip/frame` 九宫格，alpha 在 PNG 里）；tooltip 链路的最后一个 `Identifier` 参数（物品 `TOOLTIP_STYLE` 组件，`{id}.withPath(p -> "tooltip/"+p+"_background")`）可换背景命名空间
- 实现：新增 `ClientCompat.VIEWER_TOOLTIP_STYLE`（`zzzbrbe:viewer`）→ 解析为 `zzzbrbe:tooltip/viewer_background` / `viewer_frame` sprite；打包两分支 `assets/zzzbrbe/textures/gui/sprites/tooltip/viewer_*.png`（背景 alpha 240→160、边框保持原版 80，含 nine_slice mcmeta）
- 覆盖出口（查询系统全部 tooltip）：`RecipeViewerOverlay.deferTooltip`（对象/网格/弹窗槽位/详细配方全部 tooltip 的唯一出口，强制 viewer 风格，忽略物品自带 style）、页码提示 `setTooltipForNextFrame`、`PinOverlayManager` pin 物品 tooltip（26.2 ×2 出口 / 1.21.11 同）；1.21.11 另有标签悬停 `flushTabTooltip`、工作站列悬停 `flushStationTooltip` 两处
- 不影响：配方书 hover tooltip、原版/其他 mod tooltip、物品自带 custom style（仅查询系统内统一替换）
- 已构建、已部署两实例（26.2 备份 `20260827-151141`、md5 `82c6b228…`；1.21.11 备份同刻、md5 `1b417c55…`；部署前确认无实例运行）。验证：R/U 查询任意物品 → 悬停对象/配方按钮/网格/弹窗槽位 → tooltip 背景明显变淡（63%），文字清晰；A 键 pin 内物品 tooltip 同款；配方书悬停 tooltip 背景不变

**2026-08-27（九十八）：修复 JEI 插件配方去重误伤——同产物物品不同组件的配方被合并（两分支同步）**——用户反馈：Better Archeology 考古学桌（鉴定配方）JEI 查询用途能看到三个配方（产物为**穿透打击/御风/隧道领**三本不同属性的附魔书），BRBE 查询只有穿透打击一本。
- 根因：`PluginRecipeIndexer.indexPluginRecipe` 的 `seenRecipes` 去重指纹 `fingerprint()` 只按**物品 id** 归纳（`appendSortedItemIds`）——三个 `betterarcheology:identifying` 配方输入都是 `unidentified_artifact`、产物都是 `minecraft:enchanted_book`（仅 `stored_enchantments` 组件不同）→ 指纹完全相同 → 第二个起被当重复配方跳过（日志 `duplicate betterarcheology:identifying recipe skipped`），只留下注册顺序第一条（穿透打击，P<S<T 字母序）
- 修复：指纹改为 `stackKey(stack)` = 物品 id + **完整组件数据**（`ItemStack.getComponentsPatch()` 的描述，附魔/药水等全部计入）——仅物品 id 相同但组件不同的配方不再合并；真正重复的配方（物品与组件全同）仍按原语义去重
- 已构建、已部署两实例（26.2 备份 `20260827-045611`、md5 `319b7a63…`；1.21.11 备份同刻、md5 `3a74f889…`；部署前确认无实例运行）。验证：U 查询"未鉴定的文物" → 考古学桌类别显示三个配方（穿透打击/御风/隧道领三本附魔书），Shift 预览/pin 各自为对应属性的完整 JEI 界面

**2026-08-27（九十七）：点击选中标签双向切换浏览模式 + 恢复时回退到已有标签（两分支同步）**——用户两项调整：① 展示所有对象时点击选中标签即可恢复（相当于按了一下 O）；② 修复：恢复时若选中了此前不存在的标签（浏览模式才出现的类别），对象区仍保留恢复前的类别——正确的操作是走流程选中已有标签。
- ① `handleCategoryTabClick`：选中标签分支改为 `toggleBrowseAll()`——未浏览时进入展示所有对象、浏览时恢复（与 O 键完全同行为）；九十一轮的"浏览中点击=无事件"规则被本要求取代
- ② `leaveBrowseAll` 恢复兜底：新增 `browseAllReturnCategory`（进入浏览时记录原选中类别）——恢复时当前类别在**非浏览查询视图下无内容**（浏览专属标签）→ 走正常选择流程（`switchCategory`）改选**已有标签**：先选进入浏览前的类别（`categoryHasQueryContent` 校验），再兜底 `bestContentCategory`；新判定 `categoryHasQueryContent(category)`（网格类别走 `gridSource`、对象类别走 `categoryHits`，均按查询视图）
- 已构建、已部署两实例（26.2 备份 `20260827-044210`、md5 `89fe7a64…`；1.21.11 备份 `20260827-044211`、md5 `6f73d397…`；部署前确认无实例运行）。验证：① 查询 → 点选中标签 → 全部对象；浏览时再点选中标签 → 恢复原视图（等价按 O）；② 浏览模式切到查询仅显示时才有的标签（如燃料）→ 按 O 恢复 → 自动切回进入浏览前的类别（或其它有内容的类别），对象区不再卡在浏览专属类别

**2026-08-27（九十六）：点击选中标签也能展示所有对象（仅未浏览时生效，两分支同步）**——用户要求：点击选中标签同样能进入"展示所有对象"（浏览模式，O 键的等价入口）；已在展示所有对象时点击选中标签仍为无事件（九十一轮规则保留）。
- 修改：`handleCategoryTabClick`——`cat == currentCategory && !browseAllMode` 分支改调 `enterBrowseAll()`（与 O 键 `toggleBrowseAll` 同一入口：记录返回页码、`browseAllMode = true`、翻回第 0 页、按全量重建当前类别）；已在浏览模式时该分支不触发（纯 no-op）。点击音效沿用统一的 `ClientCompat.playPageFlipSound`
- 已构建、已部署两实例（26.2 备份 `20260827-043111`、md5 `4d9ef3c5…`；1.21.11 备份同刻、md5 `27fd8ebc…`；部署前确认无实例运行）。验证：查询任意物品 → 点击当前选中标签 → 该类别展示全部对象（对象区变为全量、页码翻回第 0 页）；浏览模式中再点选中标签 → 无事件；按 O 恢复原视图

**2026-08-27（九十五）：翻页音效统一门控+音量——新增 shared playPageFlipSound（两分支同步）**——用户两项反馈：① 查询界面标签翻页的音效不归"鼠标滚轮翻页音效"开关控制；② rbip标签区、查询界面对象区、查询界面工作站区三处翻页音效音量无法受配置音量控制。
- 新增 `ClientCompat.playPageFlipSound(Minecraft)`：统一出口——`scrollPageSound` 开关门控 + `0.25 × pageFlipVolume` 音量（与配方书滚轮翻页同款 `SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, volume)`）
- 替换的翻页音效点（26.2 8 处 / 1.21.11 同）：
  - 查询 viewer：滚轮翻页（对象区滚轮）、翻页键×2（`handlePageButtonClick`）、**标签点击切换类别**（`handleCategoryTabClick`——此前未门控，正是用户反馈①）、标签条滚轮翻页（`mouseScrolledTabs`）、工作站区滚轮滑动（`handleStationColumnScroll`）
  - RBIP 标签区：翻页键×2（`rbip$pageControl` 点击）、滚轮翻页（`rbip$scrollPages`）
- 保留不变：放置配方点击（`placeRecipe`）、工作站区点击查询（`handleStationColumnClick`）——非翻页音效，仍走原 `playButtonClickSound`
- 已构建、已部署两实例（26.2 备份 `20260827-042458`、md5 `e402a6ae…`；1.21.11 备份同刻、md5 `e7388f75…`；部署前确认无实例运行）。验证：① 关"鼠标滚轮翻页音效"→ 标签点击/标签条滚轮/对象区翻页键与滚轮/工作站滑动/RBIP 翻页全部静音；② 音效设置里调小"翻页音效音量"→ 上述所有翻页音效音量同步降低

**2026-08-27（九十四）：查询 viewer 对象区翻页键支持 Ctrl+点击跳首页/尾页（两分支同步）**——用户要求：按住 Ctrl 点击对象区翻页键能快速跳转至首页和尾页（就像配方区那样）。
- 修改：`RecipeViewerOverlay.handlePageButtonClick`——上一页键 Ctrl+点击 → `viewerPage = 0`；下一页键 Ctrl+点击 → `viewerPage = viewerPageCount - 1`（`ClientCompat.isControlDown()`，与配方书 `RecipeBookPageMixin.brbe$mouseClickedJumpToEdge` 的 Ctrl 语义一致）；已在首页/尾页时不变（保底 `prev/next != viewerPage` 判定在，不发声不重建）；网格类别同样生效（`fitGridBoxToPage` 分支不变）
- 已构建、已部署两实例（26.2 备份 `20260827-041608`、md5 `75ae38ef…`；1.21.11 备份同刻、md5 `1f2d1620…`；部署前确认无实例运行）。验证：多页查询（如按 O 浏览全部对象）→ 按住 Ctrl 点击上一页键 → 直达首页；点击下一页键 → 直达尾页；普通点击仍逐页翻

**2026-08-27（九十三）：Alt+滚轮步进重写——弃用 indexOf，槽位持久计数器精确步进（两分支同步）**——用户反馈（九十ニ轮之后）：轮循物品暂停后的滚轮翻动"存在较大缺陷"——翻动物品错误（偏离事实）、缺失部分轮循物品组内物品。
- **根因实锤**：javap 证实 `mezz.jei.common.ingredients.TypedIngredient` **没有重写 equals/hashCode**（纯对象身份相等）——旧代码 `all.indexOf(displayed)` 中的 displayed（`getDisplayedSlotIngredient` 经 SlotIngredient 转换出的新实例）与 `getAllIngredientsList()` 的原始实例恒不相等 → **indexOf 恒 -1 → 每次滚轮都从 0 重算** → 首次翻动后每步钉住同一个候选（卡死），且 raw 列表与 JEI 经可见性过滤的计算列表顺序不同 → 显示错误偏移的物品、其余变体永远不可达
- 修复（`SyntheticRecipeRendererImpl.stepVariants` 重写）：**每槽位持久步进计数器**（`Map<IRecipeSlotDrawable, Integer> SLOT_COUNTERS`，按槽位对象身份）——首步按**物品值**（`ItemStack.equals`，NBT 敏感）在槽位候选列表中定位当前显示变体（仅在 `getDisplayedIngredient` 取不到值时回退 0），之后每次滚轮**精确 ±1**、`floorMod` 模运算遍历该槽位的**全部**候选——每格每个变体恰好可达一次；Alt 松开/重建时计数器与 overrides 一并清理
- 已构建、已部署两实例（26.2 备份 `20260827-035127`、md5 `34d99251…`；1.21.11 备份同刻、md5 `a1cb1b0b…`；部署前确认无实例运行）。验证：Shift 预览/pin/tooltip 内嵌预览 → 按住 Alt → 滚轮 → 每个多变体槽位按 ±1 精确切换，全部变体可遍历（不再卡在同一物品）；悬停物品 tooltip 与画面一致

**2026-08-27（九十二）：Alt 暂停/滚轮翻动覆盖全部轮循界面——预览/pin 的 JEI 委托物品参与冻结与步进（两分支同步）**——用户两项要求：① 按住 Alt 应阻止**所有**界面（不止鼠标指向的）的轮循物品；② 滚轮快速翻动轮循物品**也要包括预览界面/pin 界面**里的轮循物品，触发器依旧是鼠标指向的界面。
- 根因：26.2 实例有真实 JEI（1.21.11 已禁用 JEI、预览/pin 走 BRBE 自绘路径，本无此问题）——预览/pin/tooltip 内嵌预览的可见物品由真实 JEI drawable 驱动（其 CycleTicker 轮循），BRBE 的 Alt 冻结（`currentSlotSelectIndex`）与 vendored-fork 反射步进（`manualIndexOverride` 字段被真实 JEI 遮蔽、反射失败）都管不到它 → 只能靠 JEI 自身的暂停键映射冻结（不可步进、且与 BRBE 前端不同步）
- 修复（`SyntheticRecipeRendererImpl`，两分支同步）：
  - **Alt 冻结全界面**：Alt 按住时不再调 `drawable.tick()`（冻结所有委托 UI 的变体轮循，不依赖 JEI 暂停键映射，真实 JEI/fork 均有效）；松开恢复
  - **滚轮步进含预览/pin**：`stepVariants(delta)` 遍历每个缓存 drawable 的槽位（`getSlotViews()` + instanceof `IRecipeSlotDrawable` 还原完整槽位，运行时槽位实现两者均实现该接口），对多变体槽位按**其当前显示变体**（含上一次 override）相对步进 ±1，经 `clearDisplayOverrides()→createDisplayOverrides().addItemStack()` 钉住新变体（JEI 官方覆盖显示机制，javap 验证 clear 会重建 acceptor、不会累积）；Alt 松开时统一清除 override
  - `SyntheticRecipeRenderer` 接口加 default `stepVariants`（NONE lambda 兼容）；`RecipeViewerOverlay` 新增 `isCycleAltDown()`；滚轮分支加**指针在界面内**门控（viewer `contains` / 弹窗 `contains` / `topInteractivePin`）——触发器=鼠标指向的界面，界外 Alt+滚轮保持原行为
- 已构建、已部署两实例（26.2 备份 `20260827-033942`、md5 `ed5e6fef…`；1.21.11 备份同刻、md5 `397b7bfb…`；部署前确认无实例运行）。验证：① Shift 预览/pin/tooltip 内嵌预览中按住 Alt → **全部**轮循物品冻结（含真实 JEI 绘制的物品）；② Alt+滚轮指向弹窗/pin/viewer → 对应界面的轮循物品逐一翻动变体（真实 JEI 下也生效，不再只翻 BRBE 自绘部分）；③ 界外 Alt+滚轮不翻动轮循物品

**2026-08-27（九十一）：O 浏览模式下点击选中标签改为无事件（两分支同步）**——用户反馈：按 O 展示所有对象后，点击**已选中的标签**会把界面恢复到展示所有对象之前的状态；重复点击选中标签应无任何事件。
- 根因：`handleCategoryTabClick` 的 `cat == currentCategory && browseAllMode` 分支调 `leaveBrowseAll()`（原注释"clicking the (pre-toggle) category's own tab leaves browse-all and restores it"）——恢复入口被错误地绑在"点击选中标签"上，与既定语义（点标签/滚轮在各房间浏览；再按 **O** 恢复）冲突
- 修复：删除该分支——点击已选中标签 = 纯 no-op（不播放点击音效、不触发任何状态变化）；恢复只由 O 键 `toggleBrowseAll` 触发（`leaveBrowseAll` 的 O 键调用保留）
- 已构建、已部署两实例（26.2 备份 `20260827-031xxx`、md5 `8495c193…`；1.21.11 备份同刻、md5 `2e7596c9…`；部署前确认无实例运行）。验证：O 浏览 → 点击当前选中标签 → 界面纹丝不动（无音效、不恢复）；点击其他标签正常切换；按 O 才恢复原视图（原页码）

**2026-08-27（九十）：查询 viewer 产物数据补全——酿造/铁砧/研磨等合成条目携带完整组件（两分支同步）**——用户反馈：① 酿造类别的对象产物全部退化为对应的"不可合成的药水/喷溅型药水/滞留型药水"；② 若产物为附魔书也应传入正确属性——**产物应传入完整的数据**。
- 根因：`SyntheticRecipeDisplayEntryFactory.toSlotDisplay` 用 `new SlotDisplay.ItemSlotDisplay(stack.getItem())` 构造合成条目的显示——只保留 item、丢弃全部组件（potion_contents / stored_enchantments 等）。数据源本身完整（javap 验证真实 JEI 酿造类别输出走 `add(ItemStack)`，`BrewingRecipeUtil` 用 `PotionContents.createItemStack` 构造带数据的药水栈），组件只在 BRBE 合成显示处丢失
- 修复：`toSlotDisplay` 全部改带完整栈的显示类——26.2 用 `ItemStackSlotDisplay(ItemStackTemplate.fromStack(stack))`（该版本 ItemStackSlotDisplay 收 ItemStackTemplate，与 `CacheableRecipeDisplayEntry.makeSlotDisplay` 同模式）；1.21.11 用 `ItemStackSlotDisplay(stack)`（收 ItemStack）。产物/输入/工作站显示统一受益：`resultItems` 解析后恢复完整数据——酿造对象名称与图标（药水颜色）、附魔书 stored_enchantments、铁砧/研磨产物 NBT 全部正确
- 已构建、已部署两实例（26.2 备份 `20260827-030646`、md5 `0012273a…`；1.21.11 备份同刻、md5 `66608f48…`；部署前确认无实例运行）。验证：U/R 查询任意物品 → 酿造类别对象名称应为具体药水（如"迅捷药水"）而非"不可合成的药水"，图标带药水颜色；Shift 预览/pin/tooltip 内嵌预览中产物数据一致；附魔书类产物显示其附魔属性

**2026-08-27（八十九）：边缘安全区 30px → 25px（两分支同步）**——用户定位"翻页前后锚点微小变动"的完整成因：翻页前满行（5 行 133px），同时满足"覆盖功能区（合成网格）"与"覆盖 30px 边缘区"时优先满足前者（网格避让把盒子推到网格下方）；翻页后行数不足 5，盒子变矮后为满足 30px 边缘区而**上移** → 锚点微小变动。决定将边缘安全区从 30px 改为 **25px**。
- 修改：`clampBoxX`/`clampBoxToAnchor` 的 30px 边距 → 25px（阈值 `guiW/guiH - 60` → `- 50`，即 2×安全区）；相关注释同步（30px → 25px）
- 已构建、已部署两实例（26.2 备份 `20260827-024215`、md5 `d436c989…`；1.21.11 备份同刻、md5 `4cbd6668…`；部署前确认无实例运行）。验证：合成/背包界面查询 → 满页翻到末页（裁去空行）→ 界面贴边时的上移幅度变小、锚点跳动减少

**2026-08-27（八十八）：轮循暂停键右 Shift → Alt + Alt+滚轮快速翻动（两分支同步）**——用户三项要求：
- **① 移除右 Shift 暂停轮循规则，改为按住 Alt**：vendored `CycleTicker`/`CycleTimer` 的暂停判定统一改为**直接读 GLFW KEY_LALT/KEY_RALT**（不再依赖 JEI KeyMapping/options.txt）；fork `InternalKeyMappings.pauseRecipeCycling` 默认键 LEFT/RIGHT_SHIFT → LEFT_ALT；26.2 实例 options.txt 的 `key_key.jei.pauseRecipeCycling` 绑定 right.shift → left.alt（真实 JEI GUI 的暂停键同步）
- **② BRBE 自研前端也应用**：所有自研前端（Shift 弹窗、tooltip 内嵌预览、pin、配方书按钮 4× 预览、ghost 槽位 tooltip）的轮循索引读取统一走新共享方法 `RecipeViewerOverlay.currentSlotSelectIndex(autoIndex)`——Alt 按下瞬间锁存当前索引（`cyclePaused`/`manualCycleIndex`）→ 全部自研前端同步冻结
- **③ Alt 按住时滚轮快速翻动**：`mouseScrolled` 最前面新增分支——Alt 按下且 viewer/pin 激活时，滚轮步进 `manualCycleIndex`（向上滚=上一个变体），并**反射**同步到 vendored `CycleTicker`/`CycleTimer.manualIndexOverride`（26.2 真实 JEI 遮蔽 fork 类时反射失败被吞——真实 JEI drawable 仅 Alt 暂停、不可步进；1.21.11 headless 与自研前端完整支持）；Alt 松开自动恢复轮循并清除 override
- 已构建、已部署两实例（26.2 备份 `20260827-023108`、md5 `405fc1c9…`；1.21.11 备份同刻、md5 `21d37be5…`；部署前确认无实例运行；26.2 options.txt 暂停键已改 left.alt）。验证：① Shift 预览/pin/tooltip 中物品持续轮循（右 Shift 不再冻结）；② 按住 Alt → 所有轮循物品冻结在按下的变体；③ Alt+滚轮 → 所有轮循组同步翻动变体；④ 松开 Alt → 恢复自动轮循

**2026-08-27（八十七）：规定固化——每次限制级位置调整后刷新锚点（两分支同步）**——用户确认一项规定：**每次"限制级位置调整"后，都要刷新一遍锚点的位置（第一行第一个对象）**。
- 该规定已由（八十六）实现并在此固化：`fitBoxToPage` 内所有限制级调整（30px 边距 `clampBoxToAnchor`/`clampBoxX`、合成网格避让 `avoidCraftingGrid`）执行完毕后，**无条件**把 `anchorScreenX/anchorScreenY` 刷新为实际第一对象中心（`bottomAnchor` 同步）——限制级调整的结果即新锚点，下次 rebuild 从落定位置继续；未触发调整时刷新为同一值（等效恒定）。javadoc 以 ESTABLISHED RULE 标注。仅注释变更，无行为变化，未重新构建部署

**2026-08-27（八十六）：锚点跟随限制级调整——界面移动后不再跳回旧位置（两分支同步）**——用户定位到八十五轮"锚点微移"的残余症状：按 O 展示所有对象后，界面因限制级调整（盒子变大 → 贴边/避让合成网格）而移动时，锚点不跟着移动——后续重建（翻页/切类别/退出浏览）重新锚定到打开时的旧锚点，界面跳回，视觉上"锚点漂移"。
- **修复**：`fitBoxToPage` 每次布局末尾把 `anchorScreenX/anchorScreenY` 更新为**实际第一对象中心**（限制级调整的结果即新锚点），`bottomAnchor` 同步——任何 rebuild 都从"上次落定位置"继续，界面在生命周期内**永不跳回**；无限制调整的翻页/收缩仍纹丝不动（锚点更新为同一值）
- 已构建、已部署两实例（26.2 备份 `20260827-020301`、md5 `ecf92cf2…`；1.21.11 备份同刻、md5 `db9f5628…`；部署前确认无实例运行）。验证：① R/U 打开 → 按 O 浏览（盒子变大触发避让/贴边）→ 界面被移动后，翻页/切类别/退出浏览均从移动后的位置继续，不跳回；② 不触发限制时翻页/裁去空行，第一对象中心纹丝不动

**2026-08-27（八十五）：工作站列滚轮音效 + 锚点生命周期内恒定（两分支同步）**——用户两项需求：
- **① 工作站列滚轮滑动播放翻页音效**：`handleStationColumnScroll` 在窗口实际滑动时（next 合法）播放 `AbstractWidget.playButtonClickSound`，受"鼠标滚轮翻页音效"开关（`scrollPageSound`）控制，与对象区滚轮翻页一致
- **② 锚点生命周期内恒定**：用户发现翻到最后一页、裁去空行后锚点会微微移动。根因：`clampBoxToAnchor`/`avoidCraftingGrid` 触发限制级调整时会把 `bottomAnchor` 刷新为"实际值"，翻页后继续用被污染的值 → 第一对象中心漂移。修复：新增 `anchorScreenX/anchorScreenY`（第一行第一个对象的中心，open 时在**限制级调整之后**捕获实际位置并固定）；`fitBoxToPage` 每次重新锚定 `boxX = anchorScreenX-16`、`boxY = anchorScreenY-boxH+16`；`clampBoxToAnchor`/`avoidCraftingGrid` **不再改写 bottomAnchor**（它 = anchorScreenY+16 恒定）——翻页/收缩/切类别时第一对象中心严格不动，只有新的限制级调整（贴边/避让网格）临时移动它，解除后自动回到锚点
- 已构建、已部署两实例（26.2 备份 `20260827-015553`、md5 `5aa03a69…`；1.21.11 备份同刻、md5 `38e3cc1c…`；部署前确认无实例运行）。验证：① 鼠标悬停工作站列滚轮 → 每次滑动有翻页音效（开关关闭则无声）；② 打开查询界面 → 翻页到最后一页（裁去空行/空列）→ 第一行第一个对象位置纹丝不动（含鼠标在屏幕右侧时水平方向）；③ 切类别同样不移动

**2026-08-27（八十四）：查询锚定彻底摆脱"幻影行列"——限制级调整统一用收缩后实际尺寸（两分支同步）**——用户推测：限制级位置调整把"不存在的列与行"（未创建的行列）当成了真实边界。核对后确认两处残留：
- **残留一（open() 的 30px 钳制）**：`boxY = max(30, min(anchorY-117, guiH-158-30))` 用**满页 5 行高度**钳制后再 `bottomAnchor = boxY + boxH`——鼠标靠近屏幕边缘时锚点被"满页高度"污染（如鼠标在顶部，bottomAnchor 被钳成 163 而非 66）
- **残留二（rebuildWithHits 的提前 clamp）**：`clampBoxToAnchor()` 在满页 boxH=133 下执行并**刷新 bottomAnchor**——同样的幻影边界污染，且 round 78 引入时本意是"布局前统一钳制"，现在 `fitBoxToPage`（收缩后）已在 `showPage` 内统一完成
- **修复**：`open()` 锚定改为直接 `bottomAnchor = anchorY + 16`（第一对象中心语义，不经过满页钳制）；`rebuildWithHits` 删除提前 clamp——所有限制级调整（30px 边距 `clampBoxToAnchor`/`clampBoxX`、合成网格避让 `avoidCraftingGrid`）统一在 `fitBoxToPage` 用**收缩后实际 boxW/boxH** 执行。验证场景全过：鼠标在物品区/屏幕顶部/底部、结果 1 行/满页、网格避让触发与否，第一对象中心均 = 鼠标（除非盒子真的放不下或必须避让网格）
- 已构建、已部署两实例（26.2 备份 `20260827-014215`、md5 `f94d37d6…`；1.21.11 备份同刻、md5 `1f29245f…`；部署前确认无实例运行）。验证：任意界面任意位置按 R/U → 短结果第一对象中心正对鼠标（含屏幕边缘附近）；满页盒子贴边时按 30px 边距钳制；会遮合成网格时才避让

**2026-08-27（八十三）：查询界面"总在下方"根因——合成网格避让用满页高度误判（两分支同步）**——用户反馈：每次查询界面都出现在下方，推测"系统将不存在的第五行当作了第一行"（行序反转后仍有旧概念残留）。
- **根因**：`open()` 的 craft-grid 规则（盒子不遮合成网格）用**满页 5 行高度（boxH=133）**判断 `boxY < gridBottom`——而 `boxY = anchorY - 133 + 16`（锚定公式）使该条件在背包/合成界面（2×2 网格，鼠标在物品区）**几乎总是成立** → 盒子被推到网格底部 → `bottomAnchor = gridBottom+133` → 第一对象中心恒为 `gridBottom+117`，与鼠标（物品区）脱节 → "每次都在下方"（实测典型偏移 ~43px）
- **修复**：craft-grid 避让从 `open()` 移到 `fitBoxToPage` 末尾（新 `avoidCraftingGrid()`），用**收缩后的实际盒高**判断——短盒子（≤4 行）在背包界面查询时盒子顶部已在网格下方，不触发避让，第一对象中心保持对准鼠标；只有真正会遮网格的盒子才被推到网格下方（限制级位置调整，允许偏离）；避让后刷新 `bottomAnchor`
- 已构建、已部署两实例（26.2 备份 `20260827-013409`、md5 `9018fce8…`；1.21.11 备份同刻、md5 `a98858e4…`；部署前确认无实例运行）。验证：背包/合成台界面悬停物品按 R/U → 短结果（1-4 行）时第一对象中心正对鼠标、界面不再被推到网格下方；结果满 5 行且盒子会遮网格时才避让（允许偏移）

**2026-08-27（八十二）：查询界面锚定鼠标指针——第一行第一个对象的中心对准鼠标（两分支同步）**——用户反馈：每次按查询键（R/U）弹出的查询界面都在意想不到的位置；按道理，未触发限制级位置调整时，第一行第一个对象的中心点应位于鼠标指针位置。
- **根因（两处）**：① 锚点取自悬停槽位/按钮的**左上角**（`leftPos+slot.x` 等），不是鼠标指针；② 盒子**左上角**对准锚点（`boxX=anchorX`、`boxY=anchorY`），而按钮中心在 `(boxX+16, boxY+…+12)`——上一轮行序反转后第一行在盒子底部，偏差被放大到 `(16, boxH-16)`，尤其盒子高时第一对象落在鼠标下方很远
- **修复**：锚点改取**鼠标指针**（`mc.mouseHandler.getScaledXPos/YPos(Window)`，指针在窗口外时回退旧锚点）；盒子起点改为 `boxX = anchorX - 16`、`boxY = anchorY - boxH + 16`（第 0 列按钮中心 `boxX+16`、行序反转后第 0 行中心 `boxY+boxH-16`）——再配合 `clampBoxToAnchor` 底部锚定，盒子收缩后第一对象中心仍精确落在鼠标上；30px 边距钳制与合成网格避让规则（限制级位置调整）保持原样，触发时允许偏离鼠标
- 已构建、已部署两实例（26.2 备份 `20260827-012555`、md5 `7408f210…`；1.21.11 备份同刻、md5 `e8a7666b…`；部署前确认无实例运行）。验证：鼠标悬停任意槽位/配方书按钮/查询界面按钮按 R 或 U → 查询界面打开，第一行第一个对象中心正对鼠标指针；靠近屏幕边缘（触发 30px 钳制）或与合成网格重叠时允许偏移

**2026-08-27（八十一）：查询 viewer 行序反转 + 翻页空行裁去（两分支同步）**——用户要求：① 对象区第一行应始终位于最下一行（行从下到上排布）；② 翻页后存在空行则裁去。
- **行序反转**：`showPage` 按钮重排与 `drawItemGrid` 的 y 坐标改为 `boxY + boxH - 28 - row*25`——row 0 在盒子**底部**（紧贴标签条），后续行向上生长；行数不变时最顶行仍在 `boxY+5`（公式自洽：rows=1 时 y=boxY+5，与原来一致）
- **空行裁去（分页也收缩）**：新增 `fitBoxToPage(pageCount)`——按**当前页**对象数算 columns（≤10，空列裁）+ rows（空行裁），重算 boxW/boxH → `ensureTabWidth`（标签仍可撑宽）→ `clampBoxToAnchor`（底部锚定，盒子变矮只影响顶部）→ `clampBoxX`；`computeBoxSize` 回归只算页数与满尺寸。配方类别在 `showPage` 内调用（按钮布局用 fit 后的静态 boxX/boxY/boxW/boxH，不再用调用方传入的旧值）；grid 类别（无 overlay 按钮，showPage 为空操作）在 `rebuildGrid`（`fitGridBoxToPage`）与 `mouseScrolled`/`handlePageButtonClick` 翻页处补调
- `drawItemGrid` 列数也按当前页对象数（`min(10, 页对象数)`），与 fitBoxToPage 一致
- 已构建、已部署两实例（26.2 备份 `20260827-011601`、md5 `60d166f9…`；1.21.11 备份同刻、md5 `8f971f40…`；部署前确认无实例运行）。验证：① R/U 查询对象少的类别 → 对象贴着盒子底部（标签条上方）向上排，第 1 个对象在最下行；② 翻到最后一页（不满 5 行）→ 盒子高度收缩到实际行数（标签条不动）；③ 对象 >10 的类别仍 10 列换行

**2026-08-27（八十）：查询 viewer 空列收缩——无对象也无标签的列不再创建（两分支同步）**——用户补充（七十九）：列上限 10 保留，但"如果此列上没有对象也没有标签，那就应该去掉它"——实际会无条件创建空列。
- **修复**：`computeBoxSize(int total)` 单页列数改为 `Math.max(1, Math.min(PAGE_COLS, total))`——对象 4 个 → 4 列（1 行），12 个 → 10 列 2 行（上限 10 不变）；分页恒 10×5。**有标签的列保留**：`ensureTabWidth` 原逻辑不变（浏览模式 10 个标签 → 盒子仍 258 宽，空列上有标签不算空）
- **布局一致性无需改动**：对象始终排在 10-per-row 的 PAGE_COLS 间距上（`drawItemGrid`/`showPage` 重排不变）——对象 ≤10 时全部落在第 1 行前 N 格，恰好填满收缩后的盒子；`contains()` 光标门控/工作站列/翻页按钮/clamp 均基于 boxX/boxW 自动跟随；`overScrollZone` 只在分页时生效（分页盒子恒 10 列）不受影响
- 已构建、已部署两实例（26.2 备份 `20260827-010348`、md5 `f97fccf8…`；1.21.11 备份同刻、md5 `ae55b72c…`；部署前确认无实例运行）。验证：R/U 查询对象少的类别（信息/燃料/铁砧等 4-6 个对象）→ 盒子宽度收缩到实际列数、右侧无空列；对象 >10 的类别仍 10 列换行；按 O 浏览（10 标签）→ 盒子仍被标签撑到 10 列宽

**2026-08-27（七十九）：查询 viewer 所有类别统一每行 10 个对象——单页不再自适应列数（两分支同步）**——用户反馈：农夫乐事的烹饪、信息等类别"每行对象的上限并不是十个，可能是四个、五个、六个"，希望所有类别列数上限都是十。
- **根因（两处单页自适应）**：① 配方类别（烹饪等）非分页时 `overlay.init` 走 vanilla 4/5 列布局（vanilla 按 `总数≤16 ? 4列 : 5列` 排按钮——反编译 `OverlayRecipeComponent.init` 证实），只有分页（>50）才强制 10 列；② grid 类别（燃料/堆肥/信息）`drawItemGrid` 单页时列数 = `AlternativeOverlayLayout.columnsFor`（≤16 对象 → 4 列），且 `computeBoxSize` 单页时盒子宽度随列数收缩 → 对象少的类别每行 4/5/6 个、盒子宽度在各类别间变化
- **修复（统一 10 列上限）**：`computeBoxSize(int total)` 单页也固定 `boxW = PAGE_COLS*25+8`（高度缩到所需行数 `ceil(total/10)`）；`drawItemGrid` 列数固定 `PAGE_COLS`；`showPage` 的按钮重排（`setX/setY` 钉 10 列网格）从 `if (paged)` 改为**无条件执行**——非分页时也把 vanilla 4/5 列按钮重排到固定 10 列（vanilla init 创建全部按钮不裁剪，反编译证实安全）；相关注释同步更新。`AlternativeOverlayLayout` 不再被 viewer 引用（配方书 UI 调用点未动）
- 已构建、已部署两实例（26.2 备份 `20260827-005805`、md5 `b6d02c95…`；1.21.11 备份同刻、md5 `91b836d4…`；部署前确认无实例运行）。验证：R/U 查询或按 O 浏览 → 任何类别（农夫乐事烹饪、信息、燃料、堆肥等）每行都是 10 个对象（对象不足 10 个时占前几格、盒子宽度与 10 列一致）；切换类别时盒子宽度不再变化

**2026-08-27（七十八）：浏览模式元素错位根因修复——clampBoxX 移到按钮布局之前（两分支同步）**——用户反馈：按 O 展示所有对象时"有时只有标签移动了位置，而其他元素全部超出了界面"（部分元素调整了位置、其余没动）。
- **根因**：`showPage` 在 `overlay.init`/`setX/setY` 里**缓存**了 recipe buttons 的位置（基于当时的 boxX/boxY）；而 `clampBoxX()` 是在 `rebuildWithHits` **之后**（`refreshCurrentCategory`/`switchCategory` 里）才调用。浏览模式盒子因标签条变宽（10 标签 → boxW=258）触发 `clampBoxX` 钳制 boxX → 盒子背景/标签条/工作站列/翻页按钮（每帧实时定位）移到新 boxX，**按钮留在旧 boxX** → 元素错位/按钮出界
- **修复（根治）**：`rebuildWithHits`/`rebuildGrid` 内把 `clampBoxX()` 与 `clampBoxToAnchor()` 一起移到 `showPage`/布局重建**之前**——所有元素（按钮、网格、工作站列、标签条、翻页按钮）在同一 rebuild 内共享同一最终 boxX/boxY；外部 `refreshCurrentCategory`/`switchCategory` 残留的 clampBoxX 调用幂等保留
- 已构建、已部署两实例（26.2 备份 `20260827-004849`、md5 `f332c308…`；1.21.11 备份同刻、md5 `56cfa98b…`；部署前确认无实例运行）。验证：R/U 查询 → 按 O 浏览 → 盒子变宽/变高时所有元素（按钮、标签条、工作站列、翻页按钮）整体一致移动，无单独错位；退出浏览同样一致

**2026-08-26（六十八）：Ctrl+O 浏览改为"分配到正确类别"——每个类别标签承载自己的全部对象（两分支同步）**——用户反馈（六十七）版导入的对象"全部堆在一个类别中，没有放入正确的类别"，要求**分配到正确的类别中**。最终方案：房子的"房间"= **底部类别标签**——Ctrl+O 后每个标签持有自己类别的**全部**对象，点标签/滚轮在各房间浏览；再按 Ctrl+O 恢复。
- **机制重构（大幅简化）**：删除整个合并网格机制（`BrowseAllCell`/`browseAllCells`/`browseAllEntryCategories`/`showBrowseAllPage`/`drawBrowseAllCells`/id→槽位重钉/showPage 路由/渲染各处的 browse 分支）——浏览模式**复用正常的单类别视图**（选中标签高亮、工作站列、tooltip、弹窗全部照旧按当前类别工作）
- **数据源切换**：新 `categoryHits(cat)`/`gridSource(cat)` 帮助方法——browse 时取 `allEntries()`/`allGridItems()`（完整池），否则取 `query(target,usage)`/`gridItems(target,usage)`；`switchCategory`/`refreshCurrentCategory` 改用之（switchCategory 的"隐藏无配方书工作站"非法站切断在 browse 下跳过；`hasRecipeBookStation` 的 query-station 切断同样 `!browseAllMode` 才生效——浏览即分发全部可查询对象）
- **进出**：进入 = 保留当前类别，用其完整池重建（页码归 0，其他标签切换后同样显示各自完整池）；离开 = 用查询子集重建并恢复保存页码 + 工作站列；点击当前类别标签也可退出浏览
- **回退清理**：`viewerModeFor`/`categoryFor` 浏览映射分支、渲染浏览分支（`!isGridMode() || browseAllMode` 等）、tab 全未选中（恢复正常选中高亮）、grid tooltip 条件全部还原
- 已构建、已部署两实例（26.2 备份 `20260827-00:0x`、md5 `a1c2fd55…`；1.21.11 备份同刻、md5 `ae2811eb…`；部署前确认无实例运行）。验证：R/U 查询某物品 → 界内 Ctrl+O → 当前标签显示该类别**全部**对象（选中标签仍高亮）；点击/滚轮其他标签 → 各自显示其全部对象（合成=所有合成配方、烧炼=所有烧炼配方、燃料=全部燃料…）；再按 Ctrl+O → 各标签回到只有查询相关对象的原视图（原页码）

**2026-08-26（六十七）：Ctrl+O 全类别浏览重做——"房子"隐喻：导入当前所有可查询对象（两分支同步）**——用户用比喻澄清：查询界面是**大房子**，查询物品时相关对象进房子；Ctrl+O = **把当前所有可以被查询到的对象全部集合进房子**（不限当前查询目标、无类别装饰）；再按 = **只把新加入的对象全部赶走**（恢复原状）。此前各版只合并"当前目标的各类别命中"，对象数几乎不变，故总显得"没做对"。
- **导入池（新接口方法）**：`RecipeViewerCategory.allEntries()`——类别**全部**配方对象（与查询目标无关）：6 个内建类别 = `RecipeViewerEngine.allRecipes(TYPE)`；furnace = 三类合并按 `furnaceContentKey` 去重；plugin = 其全部 uids 的 allRecipes 并集按 id 去重；`allGridItems()`——网格类别全部条目（fuel=allFuelItems、compost=allCompostables、info=所有信息配方涉及的物品）
- **进入（`enterBrowseAll`）**：遍历 `RecipeViewerCategories.all()`（**不是 visibleCategories**），当前类别在前、其余按标签顺序；每类取 `allEntries()`（过逐类别 `filterByRecipeBookStations`，跨类别按 `RecipeDisplayId` 去重）与 `allGridItems()`——**纯对象导入，无图标单元格/无横幅/无任何类别装饰**
- **恢复**：再按 Ctrl+O（或点击标签/滚轮切标签）→ `leaveBrowseAll` 恢复原类别、原页码、原工作站列（"把新加入的对象赶走"）；点击/滚轮/翻页/Shift 预览/pin/放置配方均照常
- 其余保留：Ctrl+O 仅鼠标在界面内监控、固定 10×5 页网格、逐条目 tooltip/弹窗按条目自身类别（`modeForCategory`/`categoryFor` 映射）、id→槽位重钉
- 已构建、已部署两实例（26.2 备份 `20260826-23:5x`、md5 `5f1a1a5a…`；1.21.11 备份同刻、md5 `42717ac4…`；部署前确认无实例运行）。验证：R/U 查询某物品 → 界内 Ctrl+O → 房子涌入**全部可查询对象**（合成/烧炼/切石/锻造/铁砧/酿造/研磨/厨锅等所有配方 + 全部燃料/堆肥/信息条目，可能几十上百页）；再按 Ctrl+O → 立即回到原查询视图（只有相关对象）

**2026-08-26（六十六）：Ctrl+O 全类别浏览最终语义——在当前界面插入"每个类别 + 其全部对象"（两分支同步）**——用户澄清（六十五仍不对）：**在当前界面中插入所有类别及其各个类别的所有对象（均来自可查询对象），再按 Ctrl+O 恢复**。类别必须"被插入"（可见可辨），但不是整行横幅式的"额外设计"。
- **最终实现**：合并网格按「**当前类别在前**（原视图位置），其余类别按标签顺序随后」排列；每个类别 = **一个 24px 类别图标单元格**（与底部标签同款图标，普通单元格外观）+ 该类别的全部对象（配方按钮/信息单元格）——"类别被直接插入，其图标即该类别"；悬停图标单元格 → tooltip 显示类别名（+ 模组名行，与底部标签 tooltip 一致），占一个普通格位（无整行横幅、无文本、无对齐填充）
- 复用（六十五）的 `BrowseAllCell`（新增 HEADER 态 + `header(cat)`），`drawBrowseAllCells` 分派 HEADER（图标单元格+悬停）/ITEM（信息单元格），`renderTooltip` 增加类别悬停分支（`browseAllHeaderCategory` 帧首清空）；`enterBrowseAll` 按 current-first 顺序构建（每类 header+group，空组跳过）
- 保留：逐类别过滤（`filterByRecipeBookStations(hits, cat)`）、无跨类别去重、id→槽位重钉、固定 10×5 页网格、标签全未选中、工作站列清空、离开恢复原类别页码与列、Ctrl+O 界内监控
- 已构建、已部署两实例（26.2 备份 `20260826-23:5x`、md5 `8d51ce12…`；1.21.11 备份同刻、md5 `a38a6cfe…`；部署前确认无实例运行）。验证：R/U 打开 viewer → 界内 Ctrl+O → 当前类别的图标单元格在最前、其对象随后，其余类别逐一「图标 → 对象」插入；悬停类别图标显示类别名；再按 Ctrl+O 恢复

**2026-08-26（六十五）：Ctrl+O 全类别浏览去掉类别横幅设计（两分支同步）**——用户反馈（六十四）加了类别横幅后"更糟"，明确指示：**不要引入额外设计**，目标就是"按下 Ctrl+O 展示所有类别以及所有对象，再按一下恢复"。
- **回退**：删除 HEADER/EMPTY 四态模型、整行类别横幅（图标+名称）、行首对齐填充——`BrowseAllCell` 回归两态（RECIPE 按钮 / ITEM 单元格），`enterBrowseAll` 直接按标签顺序合并各类别对象（无横幅、无对齐、**无跨类别去重**），`drawBrowseAllCells` 只画 ITEM 单元格
- **保留**（均为正确性修复，不是"额外设计"）：逐类别过滤（`filterByRecipeBookStations(hits, cat)`，判据用条目所属类别而非进入前 currentCategory）；`showBrowseAllPage` 的 id→槽位重钉（条目与信息单元格混排时按钮仍落在自己的格位）；悬停状态帧首清空
- 其余（Ctrl+O 界内监控、固定 10×5 页网格、标签全未选中、工作站列清空、离开恢复原类别页码、逐条目 tooltip/弹窗按条目类别）不变
- 已构建、已部署两实例（26.2 备份 `20260826-23:3x`、md5 `df7111f2…`；1.21.11 备份同刻、md5 `a30630f2…`；部署前确认无实例运行）。验证：R/U 打开 viewer → 界内 Ctrl+O → 一页一页看到所有类别对象（无任何横幅/标注的朴素网格），再按 Ctrl+O 恢复；鼠标在界外按 Ctrl+O 无反应

**2026-08-26（六十四）：Ctrl+O 全类别浏览重做——JEI 概念：类别+对象依次直接插入（两分支同步）**——用户反馈（六十三）版按 Ctrl+O 后"并没有展示所有类别，看起来就像是跳转到了一个不存在的类别"，要求重做，效果应与 JEI 的"查看所有类别"一致：**所有类别以及对应类别的所有对象直接插入**。
- **根因（两处）**：① 无标注合并网格（tab 全部未选中 + 内容无类别标识）→ 读起来像"选中了一个不存在的类别"；② 逐类别收集时 `filterByRecipeBookStations` 沿用**进入前的 currentCategory** 判定其他类别的对象（开启"隐藏无配方书工作站"时误弃/误放，条目不全）；③ 跨类别按 `RecipeDisplayId` 去重会吞掉 id 相同的条目
- **重做**：浏览视图 = **JEI 式合并列表**——按标签顺序，每个类别**先插入一行类别横幅**（整行 250px 背景条 + 类别图标 + 类别名），随后是该类别的**全部**对象（配方按钮/信息单元格）；页网格 10×5，横幅对齐到行首（头部行占满一行），跨页连续
- **实现**：`BrowseAllCell` 四态（HEADER/RECIPE/ITEM/EMPTY 对齐填充）；`enterBrowseAll` 按 `visibleCategories()` 顺序构建**无跨类别去重**的布局（每类的条目 = 该 tab 自己的 query 结果，网格类别 = gridItems）；`filterByRecipeBookStations`/`hasRecipeBookStation` 增加**按类别**重载（收集时传入条目所属类别）；`showBrowseAllPage` 用 `id→页内槽位` 映射把每个按钮重新钉到**其单元格的槽位**（overlay 自身布局无法跳过横幅行）；`drawBrowseAllCells` 画横幅（图标+名称）与信息单元格（悬停 tooltip 按单元格类别）；悬停状态帧首清空（防陈旧 tooltip）
- 离开/恢复、标签全未选中、工作站列清空、逐条目 tooltip/弹窗/预览按条目类别（沿用（六十三）重构的 `modeForCategory`/`categoryFor` 映射）不变
- 已构建、已部署两实例（26.2 备份 `20260826-23:0x`、md5 `7f8d08d1…`；1.21.11 备份同刻、md5 `f63f2e84…`；部署前确认无实例运行）。验证：R/U 打开 viewer → 界内 Ctrl+O → 应看到「🔨 合成台……条目」→「🔥 熔炉……条目」→……每类一行横幅+其对象依次插入；页数与横幅一致；再按 Ctrl+O 恢复；悬停单元格出对应类别信息行

**2026-08-26（六十三）：查询 viewer Ctrl+O 全类别浏览（两分支同步）**——用户要求：查询界面内按 **Ctrl+O 展示所有类别的所有对象**，再按一次恢复；随后补充"**只有鼠标在对应界面内才监控快捷键**"。
- **快捷键**：固定 Ctrl+O（vanilla `KeyMapping` 无法表达修饰键——监 `event.key()==InputConstants.KEY_O && (modifiers & MOD_CONTROL)!=0`）；**仅当鼠标位于查询界面内**（`contains` 绘制区：盒子/裁切工作站面板/下方标签条，或打开的 Shift 弹窗 `previewOwnsCursor`）才消费，界面外不拦截（回落 vanilla/其他 mod）
- **进入（`enterBrowseAll`）**：收集全部**可见类别**的对象——非 grid 类别按标签顺序取 `filterByRecipeBookStations(query)` 条目（`RecipeDisplayId` 去重，附 `id→类别` 映射），随后 grid 类别（燃料/堆肥/信息）的 `gridItems` 作为**纯单元格**（条目必须排在纯单元格前：页面按钮占据 overlay 布局的前导槽位，纯单元格填后随槽位——页内槽位 = 页面索引）
- **布局/状态**：恒用固定 10×5 页网格（`viewerPageCount=max(1,ceil)`，单页也走 paged 路径）；工作站列清空（browse 无单一类别，列条即背景：点击关闭，与（六十二）语义一致）；全部标签画为非选中；`showPage` 路由到新 `showBrowseAllPage`（页面条目 → `toCollection`+`prepareForViewer`+`snapshotPartials` 同常规路径 → 按钮钉 10×5 网格 → `drawBrowseAllCells` 画纯单元格，悬停走 grid 类别 tooltip 按**单元格所属类别**显示燃料烧炼行/堆肥概率/信息文案）
- **逐条目类别**：`categoryFor` 优先查浏览期映射（tooltip 工作站行）、`viewerModeFor`（弹窗/内嵌预览布局按条目自身类别；`viewerMode` 重构为 `modeForCategory` 单源，含 fuel→FURNACE）；`gridTooltipComponents`/`fuelTooltipComponents` 改按显式类别参数
- **离开**：再按 Ctrl+O / 点击任意标签（含原类别标签）/ 滚轮切类别（`switchCategory` 重置 browse）→ `leaveBrowseAll` 恢复原类别**页面**（`browseAllReturnPage`）+ 工作站列
- **交互修正**：render 的按钮扫描不再受 `isGridMode` 限制（原类别是 grid 时 browse 也有按钮）；`mouseClicked` 的按钮点击判定 `(isGridMode() && !browseAllMode) ? false : overlay.mouseClicked(...)`（browse 下按钮可点击放置）；grid 分支/工作站列在 browse 下跳过
- 已构建、已部署两实例（26.2 备份 `20260826-22:2x`、md5 `daae8e3c…`；1.21.11 备份同刻、md5 `68b803c5…`；部署前确认无实例运行）。验证：R/U 打开 viewer → 鼠标在界内按 Ctrl+O → 全部类别对象一页页展示（每类按钮 + 燃料等单元格），再按 Ctrl+O 恢复原类别与页码；鼠标在界面外按 Ctrl+O 无反应；点击/滚轮标签退出浏览；browse 下 Shift 预览/pin/红罩/放置配方正常

## 2026-08-28：无头 JEI 独立化（jar-in-jar，核心分支移除内嵌完成）

- **独立项目**（分支 headless-jei，26.2/ 工程）：mezz fork（854/841）+ 无头核心/收集 +
  轻量桥（JeiRecipeRegistry/JeiPopupRenderer）→ 编译并产出
  headless-jei-fabric-26.2-1.0.0.jar
- **本分支移除内嵌**：删除 mezz.jei.* fork（854/841）+ com.alonie.brbe.jei.* 收集/核心
  （保留 SRImpl/SDFF/PluginRecipeViewerCategory 适配；SRImpl 改反射渲染委托 +
  BrbeJeiBridge 反射桥：registry → 引擎 registerType/registerLayout）；
  InfoRecipeCategory/BetterRecipeBookJEIPlugin/BrbeJeiMinecraftMixin 反射化
- **jar-in-jar**：headless-jei 产物嵌入 BRBE（src/main/resources/META-INF/jars/ +
  fabric.mod.json "jars"）；**仅外部 JEI 缺席时注册**（BrbeJeiPlatform.realJeiLoaded
  guard，既有）；外部 JEI 存在 → guard 跳过 + 类遮蔽（jei < zzzbrbe 不变）
- 编译参考：26.2 使用 headless-jei-fabric-26.2-1.0.0.jar（**26.2 的官方映射/Extractor 签名，
  no-remap 直接编译**；1.21.11 的 intermediary 产物不可编译——1.21.1 同因用真实
  JEI 19.27 jar + 反射桥）；mezzdev（suffixtree/baked-substring）依赖移除
- 部署：26.2-Fabric 实例已更新（备份 20260828-0242xx，md5 一致，单装 BRBE）
- 测试要点：JOIN 后日志 [BRBE-JEI-BRIDGE] imported N JEI entries；U 查询铁砧/研磨石 →
  JEI 配方条目 + Shift 完整 JEI UI；不装 headless-jei（BRBE 独立）时 BRBE 正常降级

## 2026-08-28：modid zzzbrbe → brbe 全链回退（轮次记录）

**背景**：用户决策——维护分支 modid 全部改回 `brbe`（资源包/lang/配置名/日志/pin 文件等引用同步）。

**已落地**：
- fabric.mod.json `id` → `brbe`；assets/zzzbrbe → assets/brbe（icon/lang 7 语言/textures/pinyin.txt/animation）
- resourcepacks/zzzbrbe_unique_dark → brbe_unique_dark；注册名同步
- lang 键 `zzzbrbe.*` → `brbe.*` 全链；MOD_ID、pin 文件（brbe.pins 等）、日志名、Identifier namespace
- 部署：26.2-Fabric 实例已更新（备份 20260828-131604，md5 一致）

**注意**：CLAUDE.md 历史轮次中的 `zzzbrbe` 为当时事实描述，保持原样不改写。

## 2026-08-28：真实 JEI 共存修复 + 烧炼 mod 工作站 + 部署规则升级（26.2 实测通过）

### 真实 JEI 冲突完整底层逻辑（根因）
Fabric Loader 0.19.3 `ModResolver.findCompatibleSet` 最终按 `ModCandidateImpl::getId` 字母序稳定
排序，`FabricLoaderImpl.finishModLoading` 按此顺序 `addToClassPath`（`KnotClassDelegate.addCodeSource`
→ `DynamicURLClassLoader.addURL`；URLClassLoader 按 URL 数组顺序查类）。内嵌无头 JEI 嵌套 mod id 原为
`headlessjei`（h<j）排在真实 JEI（`jei`，root）之前 → 无头 fork 1136 个 `mezz.jei.*` 类遮蔽真实 JEI；
fork 缺真实 JEI 70 类（含入口 `JustEnoughItemsClient`、fabric mixin/events、gui Scrollbar 等）→
真实 JEI NoClassDefFoundError/错乱。迁移方案.md 的 `jei < zzzbrbe` 设计顺序被实施时破坏（顺序反转）。

### 修复
1. **嵌套 id `headlessjei`→`zheadlessjei`**（真实 JEI 类 100% 优先；无真实 JEI 时无头唯一提供者）。
2. **真实 JEI = 无头只做数据搬运**：入口 real 分支 tick 检查 `JeiRuntimeBridge.recipeManager()!=null`
   后一次性 `collectAndInject()`（插件+VanillaPlugin 运行时类型从真实 JEI manager 读入 registry），
   不启动 runtime/不注册图集。主侧删除 `refreshFromRealJei`/`buildFromRecipe`反射链（
   `createRecipeLookup(Object.class)` 签名错→每 tick NoSuchMethodException），统一 registry 数据流。
3. **烧炼 mod 工作站**（断链：indexModData catalysts 被 `SKIP_VANILLA` 丢弃 + 主侧
   `registerExternalWorkstations` 无调用者）：indexer catalysts 不跳 vanilla uid；主侧
   `registerExternalWorkstations` 同 typeId 覆盖；`BrbeJeiBridge.importVanillaStationSpecs`
   （10 原版类型→WorkstationSpec，fingerprint 纳 stations 数）；`builtinWorkstationItemIds()` 去重
   （anvil/brewing/grindstone 的 vanillaStationsFor 与 BUILTIN 重复——工作站列曾每站两份）。
4. **部署规则**：删"运行中禁部署"，改**原子替换**（cp → mods/.brbe-deploy.tmp + mv rename）；
   禁 cp 直写覆盖（2026-08-25 20:47 事故根源）。

### 验证/部署
真实 JEI 同居实测通过（真实 JEI 正常、mod 站（BetterEnd 冶炼炉）显示、无重复、2695 条目导入）。
备份链：175222→203500；最终产物 de633796…。

## 2026-08-28（晚）：打开查询界面不再隐藏真实 JEI（26.2 + 1.21.11）

用户反馈（1.21.11，真实 JEI）：打开 BRBE 查询界面后真实 JEI 界面消失。根因：
hideoverlay 的 IngredientListOverlay/BookmarkOverlay mixin 与主 tick 的隐藏判定带
"BRBE viewer 激活 || 有 pin" 条件（当时为防 JEI 列表盖住 BRBE tooltip 加的）——与
"查询界面与真实 JEI 共存"的期望冲突。修复：三个触发点（两个 mixin + 主 tick 的
setOverlaysHidden）只保留 `hideReiJeiOverlay` 配置开关；BRBE 查询/pin 打开时 JEI
照常显示（IngredientListOverlayMixin 仍是配置开关的权威 gate）。1.21.1 的守卫本就
只认配置（无需改）。已部署两实例（备份 20260828-221500，原子替换）。

## 2026-09-08：高级搜索"几乎完全损坏"修复（26.2 + 1.21.11）

用户反馈：本分支与 1.21.11 的配方书高级搜索几乎完全不可用（1.21.1 正常）。调查产物见
`docs/1.21.11-26.2-高级搜索损坏调查报告.md`，回归测试 `tools/search-cache-harness/run.sh all`
（用本分支真实编译产物驱动真实 `pipeline/RecipeBookComponentMixin`，只 stub Minecraft/协作者）。

- **根因**：管线输出缓存（`18b0f87e`，2026-08-25 引入）的缓存键只比较「搜索是否激活」的布尔量
  `brbe$cacheSearchActive == (brbe$parsedQuery != null)`，**漏掉搜索词本身**（注释却写着
  "Invalidated on … search query change"）。→ 搜索框「空→非空」的第一次按键正常，之后每次改词
  都命中缓存返回上一次的旧结果。高级语法因此冻结在 1 字符前缀：`parseToken` 只在长度 >1 时识别
  `@`/`$`/`#`/`r/`，单字符被降级为普通子串 → 没有物品名含这些字符 → **整页空白**。
  切标签（vanilla 唯一传 `resetPageNumber=true` 的路径）、重开配方书、物品栏变化会让缓存失配而
  临时恢复一次——故现象是"几乎"而非"完全"。
- **修复**：`brbe$cacheSearchActive: boolean` → `brbe$cacheSearchText: String`；命中判定
  `java.util.Objects.equals(brbe$cacheSearchText, brbe$currentSearchText())`，存缓存时同步赋值。
  ⚠️ 取值必须用 HEAD 捕获的 `brbe$savedSearchText`（`brbe$runPipeline` 期间搜索框已被清空、
  `brbe$parsedQuery` 不携带原文）；无搜索时归一化为 `""`。新增 `@Unique brbe$currentSearchText()`。
- **验证**：回归测试修复前 5 处 FAIL（`applySearch calls: [0]`）→ 修复后 `[0, 1, 2]`、**ALL GREEN
  退出码 0**；`compileJava`/`build` 通过，已原子替换部署（备份 20260911-131947，md5 4e60edb4e44f…）。
- **同源次要问题（未修，见报告 §4）**：`|` 在引号/正则内被 `OR_SPLIT` 提前切分（`r/a|b/` 失效，
  测试标 `[XFAIL]`）；`brbe$saveSearchText` 无条件清空搜索框（javadoc 写的是 "if found"）→
  `isAdvanced()` 成死代码、普通搜索不再走 vanilla `searchTrees()`；HEAD 清空 + TAIL 写回导致
  EditBox 光标每次按键跳到末尾。

## 2026-09-11：管线输出缓存第二个缺失键 —— `isFiltering`（"空气占位符"）

用户反馈：关闭"移除配方过滤"（`partialCraftingEnabled=false`）并开启"仅显示可合成"后，
**原本应被移除的配方变成空气占位符**（应直接隐藏）。

- **根因**：管线输出缓存的键里也没有 `isFiltering`。该值决定 **vanilla 在管线之前**的过滤
  （`RecipeBookComponent.updateCollections` 的 `if (isFiltering) removeIf(!hasCraftable())`），
  管线又用它决定是否跑 Stage 4 排序——两者都是缓存输出的输入。
  「过滤器关闭 → 管线缓存记下未过滤列表」→ 点"仅显示可合成"：vanilla 第 ③ 步已正确剔除
  不可合成集合，但缓存命中（物品栏/配置/pin/搜索词都没变）→ **把未过滤的旧列表交回页面**。
- **症状链**：`RecipeButton.init(collection, isFiltering=true)` →
  `getSelectedRecipes(CRAFTABLE)` 对不可合成集合返回空 → `selectedEntries` 为空 →
  渲染 `getDisplayStack()` 除以 0 被 `incompletecrafting/RecipeButtonSafetyMixin` 兜住返回
  `ItemStack.EMPTY` = **空气占位符**；点击时 `RecipeBookPage.mouseClicked` 直接调
  `getCurrentRecipe()`（**无兜底**）→ `ArithmeticException: / by zero` 崩客户端
  （实例日志 `26.2-Fabric/logs/latest.log` 15:54:02 实锤）。
- **修复**：缓存键加入 `isFiltering`（新 `@Unique brbe$cacheIsFiltering`，命中判定
  `&& brbe$cacheIsFiltering == isFiltering`，存缓存时同步赋值）。回归测试新增 Phase C：
  先 `isFiltering=false` 跑一次，再 `isFiltering=true` **且输入列表不同**跑一次，断言页面拿到
  本次输入（修复前红：`unfiltered-all-recipes`；修复后绿）。已编译、两分支 ALL GREEN、
  已构建部署。
- **同批修复（第二轮）**：① `incompletecrafting/RecipeBookComponentMixin` 的
  `@Redirect(ordinal = 0)` 实际落在 `!hasAnySelected()`（旧注释误称"主可合成过滤"；真正的是
  第三处、只在 isFiltering 时执行）。旧 `keepPartial/keepIncompatible` 包装**只在集合没有任何
  被选中配方时**才走到放行分支 —— 等于专门放行"没有可渲染条目"的集合（空气占位符 + 点击崩）。
  修复：挂点留在 ordinal 0（唯一**无条件执行**的位置，残缺标注必须每轮都跑），谓词**原样交还
  vanilla**（`return collections.removeIf(predicate);`）。残缺/不兼容的保留并不依赖绕过：
  不兼容靠 `incompatibleenvironment/CraftingRecipeBookComponentMixin` 强制 `canDisplay=true`
  → 被 `selectRecipes` 选中 → 通过该谓词，`elevateFullyCraftable3x3` 再进 craftable → 通过
  可合成过滤；残缺同理（注入 craftable + canDisplay）。与 1.21.1「显式重现 vanilla 两处
  removeIf、不绕过」对齐。**唯一可见变化**：物品栏界面 + `showAllRecipesInSurvival=false` 时
  3×3 配方不再被私自放行（vanilla 语义：该界面看不了 3×3）。
  ② 新增 `incompletecrafting/RecipeBookPageSafetyMixin`（已注册进 `mixins.brbe-common.json`）：
  `updateButtonsForPage` RETURN 把**没有可渲染条目**的按钮 `visible = false`（直接隐藏，不再有
  空气槽位；也让它天然不可点击），`mouseClicked` HEAD 再兜一层吞掉点到空按钮的点击 → 不再
  `/ by zero` 崩客户端。注入点/字段均为本分支已在生产的同款写法
  （`scrollablepages/RecipeBookPageMixin` 同样 @Inject 这两个方法，`RecipeBookPageAnimationMixin`
  同样 `@Shadow private List<RecipeButton> buttons`），javap 核对过 1.21.11/26.2 MC jar 描述符
  （`mouseClicked(MouseButtonEvent,int,int,int,int,boolean)`）。
  第二轮已编译、harness ALL GREEN、已构建部署（备份 20260911-160604，md5 一致）。

## 2026-09-11（二）：查询窗口存在时 ESC 退不出界面

用户反馈："查询界面存在时无法通过按 ESC 退出当前界面；无论查询界面是否存在，用户都应该能用
ESC 退出界面。"（详见 `docs/1.21.11-26.2-查询窗口ESC退出问题.md`）

- **根因**：ESC 在 `mixins/recipeviewer/KeyboardHandlerMixin`（`KeyboardHandler.keyPress` HEAD，
  `priority = 2000`，早于 `Screen.keyPressed`）就被 `RecipeViewerOverlay.keyPressed` 消费
  （`ci.cancel()`）；而它调用的 `close()` 是**被动关闭**——只清 `spec.materialized`、保留持久化
  spec（设计意图：窗口像 pin 一样在**下一个**容器界面恢复）。可是恢复通道
  `restorePendingViewers()` 每帧由 `PinOverlayManager.render` 调用，且
  `ViewerInstance.restoreFrom(spec, screen)` **不校验界面身份** → ESC 关掉的窗口**下一帧就在同一
  界面复活** → ESC 每按一次只换来"窗口闪一下又回来"，用户永远退不出去。设计注释写的是
  "restores on the **next** container screen"，实现与意图不符。
- **修复**（`util/RecipeViewerOverlay.java`）：
  ① 新增会话态 `restoreSuppressedScreen` + `suppressRestoreOnCurrentScreen()`；
  `restorePendingViewers()` 里"界面未变"则跳过恢复（界面一变自动解除，**跨界面存活的原有语义保留**）；
  ② ESC 分支改为：`close()` 关掉已打开的窗口 + 抑制本界面复活 + **`return false` 不消费**
  （交回屏幕走原版 ESC）。语义：有窗口时 ESC = 关窗 **+** 原版 ESC 语义
  （配方书界面先收起配方书——vanilla `RecipeBookComponent.keyPressed` 的
  `isEscape() && !isOffsetNextToMainGUI()` → `setVisible(false)`，与本 mod 无关，故意不改；
  其它容器界面直接关闭，窗口随宿主界面一起关）。
  `mixins/recipeviewer/AbstractContainerScreenMixin` 的类 javadoc 同步更新。
- 两分支同步；已编译、已构建部署（备份 20260911-162512，md5 一致）。
- **顺带发现（未改）**：`mixins/scrollablepages/RecipeBookPageMixin` 翻页回调里的
  `RecipeViewerOverlay.close()` 走同一被动关闭路径 → 同样下一帧被复活，该"翻页关窗"从未生效。

## 2026-09-11（四）：Unique Dark - Lite 兼容包补 `column_panel`

用户自制深色版 `column_panel.png`（32×32 RGBA：深绿 `#336B41` 填充 + 亮绿 `#6DA843` 描边 +
外圈 1px 黑边）加入本分支兼容包
`src/main/resources/resourcepacks/brbe_unique_dark/assets/brbe/textures/gui/sprites/recipe_book/column_panel.png`。

- **此前缺项**：基础资源 `assets/brbe/.../recipe_book/column_panel.png`（浅灰 `#C6C6C6`）一直有，
  但兼容包从未覆盖它 → 深色包下查询 viewer 的工作站列（`RecipeViewerOverlay.COLUMN_PANEL_SPRITE`
  = `brbe:recipe_book/column_panel`，`drawStationColumnSurfaces` 以 9-slice 绘制）仍是浅色。
- **无需新增 mcmeta**：新图与基础图同为 32×32，基础包的
  `column_panel.png.mcmeta`（`nine_slice width/height=32 border=4`）继续生效；包内其余覆盖贴图同样只放 PNG。
- `column_panel_top` 未动（代码侧已不再使用该变体）。
- 已构建、原子替换部署（备份 20260911-174055，md5 一致），jar 内贴图与用户源文件 md5 一致。
## 2026-09-22：三项用户反馈（LEI 左键关窗 / 预览黑紫 / 严重掉帧）

用户实测反馈三个问题，全部定位并处理（掉帧一项**不是本 mod 的问题**）。

### ① LEI 查询界面"左键一点就关" —— 26.3 鼠标键号改成了 SDL3 约定

**根因**：26.3 输入层从 GLFW 换成 SDL3，鼠标键号随之变化 —— **左=1、中=2、右=3**
（26.2 及更早是 GLFW 的 左=0、中=2、右=1，**左右互换**）。证据（javap 26.3 客户端 jar）：
`InputConstants$Type` 静态初始化 `key.mouse.left=1 / middle=2 / right=3`；原版
`AbstractContainerScreen.mouseClicked` 判 `button()==1 || button()==3`；
`RecipeButton.isValidClickButton` 同款。BRBE 26.3 的点击判定照抄了 26.2 的字面量，
于是 `RecipeViewerOverlay` 里"右键关窗"的 `event.button() == 1` **命中的是左键** →
用户看到的就是"左键点窗口内部直接关掉"；同时所有 `event.button() != 0` 的"左键专属"
分支（放置配方、切标签、翻页、拖窗、工作站列查询…）全部失效。

**修复**：`util/ClientCompat` 新增 `MOUSE_LEFT/MIDDLE/RIGHT` 常量与 `isLeftClick/isRightClick`
谓词，**20 处硬编码键号全部改用它**（RecipeViewerOverlay ×8、WorkstationTitleTrigger、
StateSwitchingButton、GenericRecipeButton/Page/BookComponent、SmithingOverlayRecipeComponent ×2、
scrollablepages/RecipeBookPageMixin ×2、pipeline/RecipeBookComponentMixin、
RBIP RecipeBookWidgetMixin）。**原样透传 `event.button()` 给原版控件的调用点不动**（原版已按 SDL3 判定）。
详见 `docs/26.3-mouse-button-renumbering.md`。

### ② LEI 预览"黑紫纹理" —— headless JEI 的贴图目录还是 26.2 的自定义图集布局

**根因**：26.3 的 JEI 精灵走**原版 GUI 图集**（`Internal.getTextures()` →
`minecraft.getAtlasManager().getAtlasOrThrow(AtlasIds.GUI)`，`Textures.createSpriteId(name)`
→ `jei:<name>`），要求贴图位于 `assets/jei/textures/gui/sprites/**`；而 fork 里装的是 26.2 时代的
`assets/jei/textures/jei/atlas/gui/**` + 自定义图集 `assets/jei/atlases/gui.json`。**那份 json 永远
不会被读**（26.3 的 `SpriteSourceList.load(rm, minecraft:gui)` 只读
`assets/minecraft/atlases/gui.json`；`AtlasManager.KNOWN_ATLASES` 是写死的 12 个图集）→
贴图从未缝合进 GUI 图集 → `getSprite(jei:slot)` 返回 missing sprite（黑紫）。

**修复**：`headless-jei/26.3` 的资源树整体换成官方 JEI 26.3 的
（`rm -rf assets/jei && cp -r <official>/Common/src/main/resources/assets/jei assets/`，
再补 `jei-icon.png`；删掉死掉的 `atlases/gui.json`；顺带补齐官方新增的
`button_disabled/enabled/highlight`、`icons/tag_badge`、`icons/list_badge`、
`interactive_ingredient_tooltip_background` 等代码已在引用的图）。重建 fork → 覆盖
`26.3/libs/`（编译参考 + dev 运行时）与 `26.3/src/main/resources/META-INF/jars/`（成品内嵌）→ 重建主 mod。
详见 `docs/26.3-jei-atlas-textures.md`；前后对比图 `docs/26.3-jei-preview-before.png` / `-after.png`。

### ③ "掉帧非常严重" —— **与本 mod 无关**：NVIDIA 驱动不匹配 → llvmpipe 软渲染

**结论**：这台机器上 Minecraft 根本没在用独显，跑的是 Mesa **llvmpipe（CPU 软件光栅化）**：
日志 `Using graphics device: llvmpipe (LLVM 22.1.8, 256 bits) (Mesa)` +
`MESA-EGL: warning: ... driver (null)` / `egl: failed to create dri2 screen`。
用户自测 F3 26–31 fps、单客户端探针 62 fps（界面态）都是这个原因。

**根因链**：机器 2026-09-20 18:41 启动后未重启；2026-09-21 12:15–12:26 pacman 把
`nvidia-utils`/`nvidia-open-dkms` 610.57.04 → **615.71.09**；已装内核 `linux 7.2.6.arch2-1`
（其 dkms 模块 = 615.71.09）但**还在跑 7.1.10-arch1-1**（该内核的模块目录已被删除），
内存里的 nvidia 模块仍是 610.57.04 → 用户态与内核模块不匹配（`nvidia-smi`：
`Failed to initialize NVML: Driver/library version mismatch`）→ EGL 初始化失败 → 软件渲染。
**处理：重启**。详见 `docs/26.3-framerate-llvmpipe.md`。

**排除 BRBE 的证据**：探针帧时/栈归因显示渲染线程 ~100% 卡在 GL 绘制调用
（`nglMultiDrawElementsBaseVertex` 等），BRBE/JEI 栈占比个位数百分比；去掉 Sodium 同样慢。

### 附：本轮新增/清理的工具与日志

- **`tools/brbe-perf-probe/`**（仓库根，不入 git）：无人值守实测回路 —— 进世界后按阶段测
  帧时（`getFrameTimeNs()` 高频采样去重）+ 采样线程做栈归因 + 子系统占比（BRBE/JEI/Sodium/MC/GL），
  并能反射驱动 **clicktest**（左键点窗口内部不得关窗、右键应关窗）与 **shot**（悬停截图，
  光标位置经 `MouseHandler.xpos/ypos` 反射写入，注意是**窗口像素坐标**，要按 GUI scale 换算）。
  `run.sh perf "world,book,viewer,viewerhover,world2"` / `run.sh clicktest` / `NOSODIUM=1 ...`。
- **清理遗留调试日志**（都是前几轮排查留下的"Temporary"埋点，用户日志里每次交互刷十几行）：
  `RecipeViewerCategories` 的 `[DEBUG-bug1]`（3 处）、`BrbeJeiBridge` 的 `[DEBUG-layout]`、
  `RecipeViewerOverlay` 的 `[VIEWER-DBG] open/render`（含限流字段）、`GhostSlotsMixin` 的
  `[VIEWER-DBG] ghostFill`（**每帧限流打印，还附带一次只为打日志的
  `PartialGhostOverlayUtil.shouldShowRedMask` 调用**）。`[BRBE-DIAG-PARTIAL]` 保留（26.2/1.21.11 同款）。
- 同期仍存在的 **26.2 / 1.21.11 分支**没有这两个 26.3 专属 bug（键号与图集都是 26.3 才变的）；
  上面清理掉的调试日志在这两个分支同样存在，需要时再清。

## 2026-09-22（二）：调试日志统一到 `-Dbrbe.debug` 开关

**用户需求**：日志输出改由一个 JVM 参数启用，并精简日志相关的调试工具；目标 1.21.1 / 1.21.11 /
26.2 / 26.3 四个分支（1.21.1 暂不部署）。本分支为参照实现，其余分支照此同步。

**统一后的形态**（四分支同源）：
- **唯一开关** `-Dbrbe.debug=true`；**唯一出口** `com.alonie.brbe.util.BrbeLogger`
  （类加载时求值一次，未开时全部调用是空操作，JIT 直接消除）；
- 输出写 `<gameDir>/logs/brbe-debug.log`，**不再写 latest.log**；格式
  `[HH:mm:ss.SSS] [TAG] msg`，`{}` 顺序占位（**不是** `String.format`——全仓库既有日志都是
  log4j 风格，`%` 不转义对中文文案更安全）；
- 入口接线：`fabric/BetterRecipeBookClientFabric#onInitializeClient` 调一次
  `BrbeLogger.init(Minecraft.getInstance().gameDirectory.toPath())`。

**分级规则**：
- **门控**：所有 `LOGGER.info/debug`；纯诊断的 `LOGGER.warn`（`BRBE-DIAG`、`BRBE-DIAG-PARTIAL`、
  `BRBE-CACHE` 的状态报告、`BRBE-DUMP`、`BRBE-RECIPE-PROGRESS`、`BRBE-JEI-BRIDGE` 的成功路径、
  `BRBE-POPUP`、`BRBE-EDGE`、`RBIP` 的 info、`DEBUG-*`、`VIEWER-DBG`）；
- **保持默认可见**：真正的故障——配置/pins/queryviewers/pinoverlays/tabpins 文件读写失败、
  `brbe_workstations.json` 项非法、mixin/兼容注册失败、进度包写入失败、REI 打开失败等，
  继续走 `LOGGER.warn/error`，用户默认看得到；
- **旧开关合并**：`RecipeStateDiagnostic` 的 `brbe.diagnostics` → `BrbeLogger.isEnabled()`；
- `System.err/out.println` 全部消除（故障 → `LOGGER.warn`，调试 → `BrbeLogger`）；
- 移除前几轮遗留的临时埋点：`[DEBUG-fb]`（BrbeJeiBridge，含其 5s 限频字段）、
  `[BRBE-DIAG-PARTIAL]` 的裸 warn 形态等。

**落地数字（26.3）**：门控 74 处、保留 warn/error 45 处、`LOGGER.info/debug` 残留 0、
裸 `System.out/err` 残留 0、遗留临时标签 0；涉及 26 个文件 + 新增 `util/BrbeLogger.java`。

**实测验证**（`tools/brbe-perf-probe/run.sh perf world`，两次运行）：
- 不带参数：`latest.log` 里调试标签行数 **0**，`logs/brbe-debug.log` 不存在；
- `JAVA_TOOL_OPTIONS=-Dbrbe.debug=true`：生成 `logs/brbe-debug.log`（74 行；标签分布
  `BRBE` 28 / `BRBE-RECIPE-PROGRESS` 26 / `BRBE-CACHE` 12 / `BRBE-JEI-BRIDGE` 2 / 其余 6）。

**顺带修好探针工具**：`/tmp/brbe-launch.sh` 会被 tmp 清理，`run.sh` 现在缺文件时自动从
HMCL 日志重建（`~/.hmcl/logs/*.log` 里 `Launched process:` 那行），并把 HMCL 隐去的
`--accessToken <access token>` 换成离线 `--accessToken 0`（否则 bash 把 `<` 当重定向而启动失败）。

**未纳入本轮**：headless-jei fork 自己的 `[BRBE-JEI-Plugins]` 启动 INFO 行（独立工程
`headless-jei/26.3`，不在本次四个目标分支内）仍在 latest.log，约 20 行/次；需要的话另开一轮。

## 2026-09-22（三）：无头 JEI 的日志开关并入 `-Dbrbe.debug`

用户提议"无头 JEI 干脆和 BRBE 共用同一个 JVM 参数"，采纳。实现分两条路（详见 `headless-jei` 分支提交
`63202339`）：

1. **fork 自有的 `com.alonie.brbe.jei.*`**（无头核心/收集器，26.3 共 21 处）：新增
   `HeadlessJeiLog`（与 `BrbeLogger` 同款 API/开关/文件），`LOGGER.info/debug` 全部改走它；
   只保留"整个集成起不来"的 4 条 warn 默认可见。
2. **官方 `mezz.jei.*`（逐字节上游，不动源码）**：`HeadlessJeiLog.init()` 里按开关反射调
   `Configurator.setLevel("mezz.jei", WARN|INFO)`——关闭时 JEI 那些 `Starting JEI…` /
   `took 214.2 microseconds` / `Registering recipes…` 的 INFO 不再进 latest.log。

输出与 BRBE 主 mod 同一个文件；**两个 mod 都用 `CREATE+APPEND` + 各写一行会话头**
（`=== BRBE Debug Log ===` / `--- headless-jei attached ---`），谁先初始化都不会抹掉对方，
只有文件 >4MB 时 BRBE 侧就地清空重来。

**实测（26.3-Fabric）**：不带参数 → latest.log 里 `BRBE-JEI-Plugins` **0 行**、JEI 官方 INFO 行
从 ~40 降到 1、无 `brbe-debug.log`；带 `-Dbrbe.debug=true` → `brbe-debug.log` 生成，且其中出现
fork 自己写的 `[BRBE-JEI-PLUGINS]` 行。
改动需重建 fork 并覆盖内嵌产物才生效（`libs/` + `META-INF/jars/`），本轮已重建部署
（备份 `brbe-ava-fabric-26.3-2.3.1.jar.bak.20260922-2118`，md5 `8c65bb35b41c5afde8127644b77835f6`）。

### 追加（同日）：fork 的日志"整体消失"根因 = 非追加句柄互相覆盖（提交 `7b438f8c`）

**现象**：fork 明明跑了（插桩确认 `init` 被调用、`writer` 已开、`Files.size` 也涨到 187），
但 `brbe-debug.log` 里**连它的会话头都没有**。

**根因**：`BrbeLogger` 在"文件不存在/超 4MB"分支用 `Files.newBufferedWriter(p, UTF_8)`
（= `CREATE+TRUNCATE_EXISTING+WRITE`，**没有 O_APPEND**），写入走**自己的文件位置**；
fork 用 `CREATE+APPEND`（O_APPEND，写到真实末尾）。于是 BRBE 建文件→fork 把会话头追加到末尾
→BRBE 从自己的位置继续写→**把 fork 的整段字节覆盖掉**。最小复现：
`A(非追加)` 写 2 行、`B(追加)` 写 1 行 → B 的行彻底消失。这也解释了"插桩那次却能看到 fork 的行"
（那次文件已存在，BRBE 走追加分支）。

**修复**：`BrbeLogger.init` 恒以 `CREATE+APPEND` 打开（26.2/1.21.11/1.21.1 原本是**无条件截断**，
比 26.3 更糟，一并改掉）；>4MB 轮转改为**就地 truncate**
（`FileChannel.open(file, WRITE, TRUNCATE_EXISTING)`——NIO 不允许 `APPEND+TRUNCATE_EXISTING`
同时给，会抛 `IllegalArgumentException`；而"删除再建"会让 fork 的句柄悬在 unlink 的 inode 上）。

**验证**（`tools/brbe-perf-probe/`，均先删除日志文件以复现 fresh 场景）：
26.3 开开关 → 85 行含 `--- headless-jei attached ---` / `collecting from 1 plugins` /
`embedded JEI core started (3 plugins)` / `indexed 5 JEI types (1704 entries)`；关开关 → 无文件、
latest.log 调试标签 0 行。26.2 → 106 行含 fork 会话头 + `collecting from 10 plugins` /
`collected categories=15 recipeTypes=16 catalysts=18`；1.21.11 → 450 行含 fork 会话头 +
`indexed 5 JEI types (174 entries, mod plugins)`。完整诊断见
`docs/brbe-debug-log-写入冲突诊断.md`。

**部署**：26.3 md5 `517efb955aeedf7172b0d15b8ef6429b`（备份 20260922-2147）、
26.2 md5 `a6d4eccd9d94998f1cbf46c34727f781`、1.21.11 md5 `07977bb5bd0a976626d2bd70c2644e19`
（备份 20260922-2148）。

### 追加（同日）：`advancement poll failed` 每秒刷屏修复（进度回填重新可用）

**现象**：26.3 进世界后 `brbe-debug.log` 每秒一条
`[BRBE-RECIPE-PROGRESS] advancement poll failed: NoSuchMethodException: ClientAdvancements.getTree()`。

**根因**：`RecipeUnlockTracker.applyCompletedProgress` 用**字符串反射**走
`getTree()/nodes()/holder()/id()/isDone()`。26.3 把 `ClientAdvancements.getTree()` 改名
**`tree()`**（同时把私有 `progress` 字段提升为公共 `progress()`）→ 全链路失败。
⚠️ 更要紧的是：字符串反射在 **1.21.x 的 intermediary 运行时一个也匹配不上**
（字段/方法名是 `field_XXXX`/`method_XXXX`）——那里 `findProgressField` 直接返回 null，
整段逻辑**从未生效**（进度回填静默失效，只因为默认不开调试日志而无人发现）。

**修复**（26.3 / 26.2 / 1.21.11 同步）：
- **只反射解析"进度表"这一个入口**，按类缓存，三级回退：
  ① 公共 `progress()`（26.3）→ ② 私有字段 `progress`（26.2/26.3）→
  ③ 声明里唯一的 `Map` 字段（intermediary 兜底）。
- 拿到 `Map` 后**全部走编译期类型化调用**：直接遍历 `entrySet()`（键就是
  `AdvancementHolder`，不再需要 advancement 树），`AdvancementProgress#isDone()` /
  `AdvancementHolder#id()` 都是普通引用，loom 会正确 remap → 1.21.11 也恢复可用。
- 失败日志**每会话最多 3 条**（原为每秒一条）+ 成功时一次性输出
  `advancement table: N entries (M brbe) via <accessor>` 便于确认绑定。

**验证**：
- 26.3 实机（probe 进世界，`-Dbrbe.debug=true`）：`advancement poll failed` **0 行**
  （修复前约每秒 1 行），并出现
  `advancement table: 73 entries (2 brbe) via public java.util.Map
  net.minecraft.client.multiplayer.ClientAdvancements.progress()`。
- 离线对**真实 MC 类**跑同一解析算法（`/tmp/accprobe/AccessorProbe.java`）：
  26.3 → ① `public progress()`；26.2 → ② `field named progress`；
  混淆名模拟类（单 `Map` 字段 + 无 `progress()`）→ ③ 命中唯一 `Map` 字段。
- 三个分支 `compileJava` + `build` 通过、已部署（26.3 md5 `16d748ac`，
  备份 20260922-2211；26.2 md5 `ef019aac`、1.21.11 md5 `f27e051b`，备份同日 2212）。


## 2026-09-22（二）：日志改为「恒写一个文件」，取消 `-Dbrbe.debug`

用户评估后拍板：不再用 JVM 参数控制日志输出，BRBE 与无头 JEI 的日志**全部输出到
`<gameDir>/logs/brbe-debug.log`**——既不干扰 `latest.log`，也不需要开关。

**BRBE 侧**（`util/BrbeLogger.java`，四分支逐字节一致 md5 `20e8f30a…`）：
- 删掉 `PROPERTY`/`ENABLED`/`isEnabled()`，`init()` 与 `log()` **恒写**该文件
  （`CREATE+APPEND` + 会话头 + >4 MB 就地清空，沿用同日修好的追加规则）。
- 昂贵自检改由**独立闸门** `-Dbrbe.diag=true`（`BrbeLogger.diagnosticsEnabled()`，默认关）：
  `RecipeStateDiagnostic`（每次刷新对全部配方做独立预测，官方注释写明"生产路径开启会显著
  拖慢配方书刷新"）、1.21.1 的两处集合计数诊断、`RecipeBookDebugLogger`、`PerfTimer`。

**无头 JEI 侧**（`HeadlessJeiLog.java`，四工程一致）：恒写同一文件；并新增
**log4j 路由**——入口在**没有真实 JEI**（`!isModLoaded("jei")`）时把 `mezz.jei` logger
接到本文件（`additivity=false` + 自有 appender，INFO 起），WARN 及以上旁路回原
`File` appender（`latest.log` 仍能看到 JEI 的告警/错误）；**装了真实 JEI 时完全不碰**。

**验证**（详见根 `docs/brbe-debug-log-写入冲突诊断.md` §7）：
- 26.3 实机不带任何 JVM 参数进世界 → `brbe-debug.log` 124 行，含两个会话头、路由确认行、
  官方 `Starting JEI… / Configuring JEI took 402.0 microseconds / Registering recipes…`；
  `latest.log` 里 BRBE 调试标签 0 行、JEI 官方行 0 行。
- 26.2 / 1.21.11 **装有真实 JEI** → 无 `routed here`（未劫持），JEI 行留在 `latest.log`。
- WARN 旁路用离线探针（真实 fork jar + 实例 `log4j2.xml`）确定性验证：`mezz.jei.probe`
  的 INFO 只进 brbe-debug.log，WARN 两个文件都有。

**部署**：26.3 `8f97e426…`（备份 20260922-2229）、26.2 `4dc7122a…`、1.21.11 `bde411d7…`
（备份同日 2230）；1.21.1 按用户要求只构建不部署（BRBE 双端 + fork 双端 jar 均已构建）。


## 2026-09-22（三）：版本号 2.3 → 2.3.1

用户要求：1.21.11 / 26.2 / 26.3 三个分支的版本号改为 **2.3.1**（1.21.1 保持 2.3 不动）。

- 每分支两处：`gradle.properties` 的 `mod_version=2.3` → `2.3.1`；
  `src/main/resources/fabric.mod.json` 的 `"version": "2.3"` → `"2.3.1"`
  （该字段是**硬编码**，`processResources` 只做文件排除、不做占位符替换）。
- 产物名随之变为 `brbe-ava-fabric-<mc>-2.3.1.jar`；游戏内 mod 列表显示 `brbe 2.3.1`。
- 部署：新 jar 原子替换入实例后**删除旧的 2.3 jar**（同 mod id 并存会让 Loader 报重复），
  旧 jar 已备份为 `*.jar.bak.<时间戳>`。
- 验证：26.3 冒烟跑通 → `latest.log` 里 `- brbe 2.3.1`、调试标签 0 行；
  `brbe-debug.log` 照常恒写（126 行，含会话头）。

## 2026-09-22（四）：`latest.log` 里 BRBE 噪声清零（mixin 内 lambda / static 字段的实例 accessor）

用户要求：查 26.3 测试实例 `latest.log` 还有没有 BRBE 的冗余日志。审计出的残留共两类，
每个会话都会重复出现（不影响功能），已全部消除：

| 噪声行 | 数量 | 根因 | 修法 |
|---|---|---|---|
| `Renaming synthetic method lambda$… to … in … from mod brbe` | 9（另有 8 个同类尚未触发） | Mixin 必须给 mixin 类里所有非 public 方法改名防冲突（`MixinPreProcessorStandard.attachUniqueMethod`）；lambda 体会被编译成**合成方法** → 每个 lambda 一行 INFO | 全部改成 **`@Unique` 方法 + 方法引用**；通用谓词挪进普通工具类 |
| `should be static as its target is` | 4 | `RecipeButtonAccessor` 用**实例** `@Accessor` 读原版 `static final` 的 `SLOT_*_SPRITE` | 删掉这 4 个 accessor，改在 `RecipeBookPageAnimationMixin` 里直接构造 `Identifier`（字面量经 javap 核对原版 static 初始化器，逐字一致） |

**为什么方法引用安全**：方法引用不产生合成方法；它的 `MethodHandle` 是 invokedynamic 的
bootstrap 参数，Mixin 用 `MixinTargetContext.transformConstant → transformHandle →
transformMethodRef` 处理——与普通 `INVOKEVIRTUAL/INVOKESTATIC` 指令**同一套重映射**
（owner 从 mixin 类改写到目标类），所以合并进目标类的 `@Unique`/`@Shadow` 成员都能解析。

**改动清单**（三分支同步）：
- RBIP `groups/ClientRecipeBookMixin`：2× `computeIfAbsent` + 4× `forEach` → `rbip$bucketFor`（get+put）
  + 显式 `entrySet()` 循环；`EntryBucket.toCollections()` 去 stream（共 6 行）
- RBIP `widget/RecipeBookTooltipMixin`：`stream().filter().forEach()` + `Optional.map().ifPresent()`
  → 显式 `for` 循环（3 行）
- `accessors/RecipeButtonAccessor`：删 4 个贴图 accessor；`scrollablepages/RecipeBookPageAnimationMixin`
  新增 4 个 `SLOT_*_SPRITE` 常量
- `BrewingStandScreenMixin` / `SmithingScreenMixin` / `settings/RecipeBookComponentMixin` /
  `pausescreen/PauseScreenConfigButtonMixin`：按钮回调 → `@Unique` 方法 + `this::`
- `unlockrecipes/MultiPlayerGameModeMixin`：`storeItem` 的谓词 → 普通类
  `RecipeMenuUtil.notCraftingMenuSlot`（普通类里的 lambda 不经 Mixin）
- `soundoptions/SoundOptionsScreenMixin`：`xmap` 两个换算 + 值回调 → 3 个 `private static` 方法 + 方法引用
  （1.21.11 的滑块是自建 `AbstractSliderButton`，本就没有 lambda，未改）

**本轮新增的离线审计**（可复用）：扫 `*.mixins.json` → `javap -p` 每个 mixin 类查 `lambda$`
合成方法；扫全部 `@Accessor/@Invoker` → `javap` 目标成员比对 static 修饰。
结果：三分支 **mixin 类 0 个 lambda**；accessor 条目（26.3/26.2/1.21.11 = 49/50/48 条）static 全部匹配。

**验证**：
- 真实实例跑探针 `world + book` 阶段 → `latest.log` 224 行里 `Renaming`=0、`should be static`=0、
  BRBE 调试标签=0；`Mixing … ClientRecipeBookMixin / RecipeBookTooltipMixin …` 行仍在
  （mixin 确实生效，只是不再改名）；`brbe-debug.log` 照常追加会话头。
- 临时自测 mod（`tools/brbe-screen-selftest/BrbeScreenCallbackTest.java` + `run-cbtest.sh`，
  跑完自动卸载）在真实实例里依次打开并操作：暂停菜单 BRBE 按钮 → `ClothConfigScreen` ✓；
  `SoundOptionsScreen` 混入且翻页滑块在列表里 ✓；酿造台/锻造台按钮按下后配方书组件挂上 ✓；
  物品栏配方书（设置按钮所在）正常构造 ✓。
- `-Dmixin.debug.export=true` 导出转换后的目标类做**字节码级核对**：invokedynamic 的
  bootstrap `MethodHandle` owner 已从 mixin 类改写成目标类——
  `PauseScreen.brbe$openConfigFromPauseMenu`、`BrewingStandScreen`/`SmithingScreen.brbe$onRecipeBookButton`、
  `RecipeBookComponent.brbe$openConfigFromSettings`、
  `SoundOptionsScreen.brbe$toSliderValue`/`fromSliderValue`/`applyPageFlipVolume`；
  转换后的目标类里 0 个 `brbe$lambda$` 合成方法；`ClientRecipeBook.rbip$bucketFor` 已合并进去。

**部署**：26.3 `371cb7eb…`、26.2 `876df8d0…`、1.21.11 `e99411a2…`（备份 20260922-230047，原子替换）。
**规则已写入根 `CLAUDE.md`**：mixin 类内不要写 lambda；`@Accessor` 读 static 字段必须声明 `static`。

## 2026-09-22（五）：26.3 移植后两处 bug 修复（堆肥概率全 0% / 锻造详细界面缺料红罩不显示）

用户反馈（均为 26.3，26.2 / 1.21.11 无此问题）：① LEI 查询 → 锻造台类别的详细界面里，物品
不显示"缺料"红色遮罩（其他类别暂未见）；② 堆肥类别里每个对象都是"概率：0%"。

### ① 堆肥概率全 0% —— 根因：`context_int_provider` 注册表**不同步到客户端**

反编译核对 `net.minecraft.resources.RegistryDataLoader` 静态初始化：
`Registries.CONTEXT_INT_PROVIDER` 只出现在 `RELOADABLE_REGISTRIES`（putstatic #774），
而 `SYNCHRONIZED_REGISTRIES`（putstatic #792）是**显式 `List.of(...)`**、里面没有它。
→ `LootIntResolver.lookup()` 查客户端注册表失败 → 返回 null → `expected()` 恒 0。
（燃料同一处：`FuelRecipeCategory.allItems()` 过滤 `burnTimeOf > 0` → 修复前燃料类别应当是**空的**，
一并解决。）

**运行时实测**（探针 mod，26.3 真实实例）：
`client level registry: UNAVAILABLE (IllegalStateException)`、
`integrated server registry: UNAVAILABLE (IllegalStateException)`（集成服务端也拿不到）——
所以数值实际必须由**内置兜底表**提供。

**修复**：
- 新增 **生成物** `util/ContextIntProviderFallbacks`（26 条），由
  `tools/context-int-provider-fallback/gen.py` 从客户端 jar 的
  `data/minecraft/context_int_provider/**.json` 按展示口径求结构化期望值生成
  （weighted_list→加权均值、number_dispatcher→default、conditional→on_false、div→左/右、
  字符串→引用递归）：`compostable/low=0.3`、`low_medium=0.5`、`medium=0.65`、`medium_high=0.85`、
  `always_add_one=1.0`、`cooking/time_coal=1600`、`time_bamboo=50`、`time_dried_kelp_block=4001` …
  **升级 MC 后重跑该脚本**。
- `LootIntResolver`：解析顺序 = 客户端 level 注册表 → 单人集成服务端注册表 → 内置兜底表；
  新增 `resolvable(...)`；`CompostRecipeCategory.chanceKnown(...)`；
  `RecipeViewerOverlay.compostTooltipComponents` 解析不出来时**不显示该行**（而不是"概率：0%"）。
- 探针实测（修复后）：wheat 0.65 / kelp 0.3 / oak_sapling 0.3 / dried_kelp_block 0.5，
  `resolvable=true`；coal 1600 ticks、oak_planks 300、dried_kelp_block 4001。

### ② 锻造详细界面缺料红罩不显示 —— 根因：兜底 layout 的槽位 `stacks` 为空

`BrbeJeiBridge.attachSmithingFallbackLayouts` 给"JEI 侧收集不到 layout"的锻造条目
（原版 18 个纹饰 + 部分 mod 配方）挂的是**固定几何常量 `SMITHING_LAYOUT`，四个槽位的
`stacks` 全是 `List.of()`**（原注释："委托渲染时由真实 JEI drawable 自绘槽位内容"）。
而委托渲染（完整 JEI UI）的逐槽红罩由 `PopupRenderer.drawDelegatedGhostMasksAt` 计算，
它对 `stacks` 为空的槽位**一律 `continue`** → 这些条目**永远不画缺料红罩**；
transform 类条目有 headless 给的原生 layout + stacks，所以只有"**部分**对象"出问题
——与用户描述完全一致（桥日志 `attached vanilla JEI layout to 30 ... (12+18)`：
12 条原生有 stacks、18 条兜底为空 → 0 红罩）。

**修复（`BrbeJeiBridge`）**：
- `smithingLayoutFor(holder)`：固定几何 + 该条目三件输入的候选物品
  （`SmithingRecipe.templateIngredient/baseIngredient/additionIngredient` → `Ingredient.items()`；
  tag 类材料展开成整个 tag，配合既有"拥有任意一个即不缺料"判定）；
- `fillSmithingLayoutStacks(all)`：对"已有几何但输入槽 stacks 全空"的条目按 x 升序补
  template/base/addition（覆盖 headless 只给几何的原生 layout），并打
  `filled empty smithing layout slot stacks on N entries`；
- 删除旧的空 stacks 常量。

**探针实测**（修复后）：`smithing entries=30 layoutWithInputStacks=30
layoutWithoutInputStacks=0 noLayout=0 delegatedRenderable=30
masksWithEmptyInventory=90 masksWithLiveInventory=90`
（30 条全走委托渲染，每条 3 个输入槽都能算出缺料 → 每条最多 3 个红罩）。

### 落地

- 26.3 构建并原子替换部署：md5 `90c66f20…`（备份 `20260922-235408`）。
- 26.2 / 1.21.11 **不改**：两者燃料/堆肥仍是旧表（`ComposterBlock.COMPOSTABLES` / `FuelValues`）、
  锻造 layout 由 JEI 插件原生收集（自带 stacks），无此二问题。
- 复现/验证工具：`tools/brbe-screen-selftest/BrbeCompostProbe.java`（探针 mod）+
  `run-cbtest.sh`（`TEST_SRC=... TEST_ENTRY=... TEST_ID=... TEST_JAR=... GREP_TAG=BRBE-CPROBE`），
  跑完自动卸载 mod。

## 2026-09-23：三处缺陷修复 + 酿造书标签页重复配方（26.2 / 1.21.11 同步其中三项）

用户反馈四项：① 配方状态变化后配方书**整体排序不刷新**；② 酿造配方**完全不解锁**（连
`unlockAll` 都不行）；③ 锻造台幽灵物品没有工作台那套遮罩调整；④ **某组酿造配方解锁后，
该标签页里出现三份一模一样的配方**。并指示"这些问题我不能保证 26.2 及以前不存在，顺带检查"。

### ① 状态变化后排序不刷新（26.2 / 1.21.11 同源，已同修）

**根因**：`CollectionPipeline.CATEGORY_CACHE` 的命中判据只有
`RecipeCraftingIndex.currentVersion()`，而分类（`TRULY_CRAFTABLE / PARTIAL / UNASSIGNED`）的
实际输入是「集合的 craftable 集合 + 残缺标记」——**这两者都会在库存数量没变时改变**：

- 配置整轮重标记（`partialMarkingEnabled` 开关 / `invalidateCaches`）；
- 手持（carried）或副手变化触发的重标记 —— BRBE 的 slotHash 计入二者，但 vanilla 的
  `stackedContents`（= `RecipeCraftingIndex` 的 diff 源）**不含副手、也不含 carried**
  → `currentVersion()` 不变；
- pin 浮层的 `forceReevaluate` / carried 提升注入 craftable。

于是分类一直是过期的：按钮状态（读 craftable 集合）已经变了，Stage 4 排出来的顺序却还把它
放在旧桶里 —— 症状正是"状态变了、排序不刷新"。

**修复**（`util/CollectionPipeline.java`）：`CachedCategory(category, version)` →
`CachedCategory(category, version, stateHash)`，`stateHash = PartialCraftingUtil.pipelineStateHash(
List.of(c))`（与管线指纹同一套分量），命中需 `version` 与 `stateHash` 同时相等。

**交叉检查**：26.2 / 1.21.11 是同一份代码 → 同修（已部署）；**1.21.1 无此缓存**
（`@ModifyArg` 就地过滤 + `isAdvanced()` 守卫），不受影响。

**运行时证据**（探针，修复后的 jar；目标集合在列表末尾 → 可合成后应移到 unpick 桶）：
`craftable false->true index 1055->0 freshCategory=TRULY_CRAFTABLE`、
`stateChanged=true orderChanged=true → OK`。

### ② 酿造配方完全不解锁（26.3 独有回归）

26.3 删除了硬编码的 `PotionBrewing`，酿造改为常规数据驱动配方 `RecipeType.BREWING`；
而客户端 `ClientLevel.recipeAccess()` 返回的是 vanilla `ClientRecipeContainer`——**只有
配方属性集与切石配方**，不是 `RecipeManager`（26.2 客户端还能从 `level.potionBrewing()`
拿硬编码表）。旧写法 `instanceof RecipeManager` 在客户端**永远不成立** → 返回空列表 →
`PotionLoader.POTIONS` 为空 → 酿造书一条都不显示，`unlockAll` 也救不了（根本没有条目）。

**修复**（`brewingstand/fabric/PlatformPotionUtilImpl`）：`getPotionMixes` 三级解析：
服务端 `RecipeManager`（`ServerLevel.recipeAccess()` 就是它）→ fabric-recipe-api 同步集
`FabricRecipeAccess.getSynchronizedRecipes().getAllOfType(RecipeType.BREWING)` →
单人集成服务端 `Minecraft.getSingleplayerServer().getRecipeManager()` → 空。
`RecipeUnlockTracker.deriveMaterialResults` 用同一入口，材料→产物映射随之恢复。
26.2 / 1.21.11 **无此问题**（仍走 `potionBrewing()` + `FabricPotionBrewingAccessor`）。

### ③ 锻造/酿造幽灵物品缺少工作台的遮罩调整（26.2 / 1.21.11 同源，已同修）

工作台幽灵由 `incompletecrafting/GhostSlotsMixin` 绘制（已有材料跳过红底+白罩、缺料红底加深为
`0x66FF0000`），而锻造/酿造走 BRBE 自研的 `GenericGhostRecipe`，固定画 `0x30FF0000` 红底 +
`0x30FFFFFF` 白罩，没有这套判定 → 已有材料仍被遮罩盖住、缺料红底也不加深。

**修复**（`generic/GenericGhostRecipe`）：新增常量 `GHOST_RED / GHOST_RED_STRONG / GHOST_WHITE`；
`render` 先算 `missing[]`（`PartialGhostOverlayUtil.computeMissing` +
`PartialCraftingUtil.searchSpaceItemCounts()`，与查询预览/pin 的逐槽红罩同源——"候选变体任意
一个拥有即不缺料"，按 (y,x) 顺序扣减数量）；已有材料的槽位**红底与白罩都不画**，缺料槽位红底用
加深值。`GenericGhostIngredient` 新增 `getVariants()`（轮循槽位的全部候选物品）。

### ④ 酿造书标签页出现三份一模一样的配方（26.3 独有回归，本轮新修）

**数据事实**（26.3 客户端 jar `data/minecraft/recipe/brewing/**.json`，共 **279** 条）：
26.3 把三种物品形态（普通/喷溅/滞留药水）放进**同一份**配方表——按 `input.item` 统计
`minecraft:potion` 108 / `splash_potion` 108 / `lingering_potion` 63；同一个「药水→药水」
转换以三种形态各存在一条（**63 组 × 3 条**，组内只有 `input.item`/`output.id` 不同，内容完全
相同），另有 45 条「药水 + 火药 → 喷溅药水」、45 条「喷溅 + 龙息 → 滞留药水」。

旧版 `PotionBrewing.Mix` 只有药水→药水、**不含物品形态**（形态完全由标签页决定；26.2 实例
日志实测 `Loaded 68 potions.`），所以旧代码"每个标签页列出全部条目"是对的。26.3 若不按形态
过滤，同一标签页就会把三种形态全部列出，而结果图标又统一按标签页物品绘制
（`getResult` 用 `category.getItemIcons()`）→ **三个一模一样的按钮**。

**修复**：
- `PlatformPotionUtil` 新增 `getInputItem / getOutputItem`（接口**默认返回 null = 未知**，
  旧分支不覆写即行为不变）；26.3 fabric 实现从 `BrewingRecipe` 取
  `getInput().ingredient()`（基底物品）与 `getOutput().create()`（产物物品）。
- `BrewableResult` 新增 `inputItem() / outputItem() / belongsToTab(category) /
  inputFormItem / outputFormItem`；结果、悬停名、幽灵/放置用的输入栈全部改用**配方真实物品
  形态**（火药→喷溅、龙息→滞留的产物不再是标签页物品）。
- `BrewingRecipeBookComponent.getCollectionsForCategory` 按 `belongsToTab` 过滤；
  `getInputStack` 改为 `result.inputAsItemStack(category)`（消除重复的形态分支）。
- `PotionLoader.load` 的日志加形态明细（见下），便于现场核对归属。

**归属口径 = 基底物品**（设计蓝图 §2.8「根据基底物品类型区分」）：标签页 = 你放进酿造台的
瓶子形态，所以「普通药水 + 火药 → 喷溅药水」出现在**普通药水**页（它的基底是普通药水）——
这两类转换在 26.2 因数据不含形态而**整个看不到**，现在各归其位、不重复。

**离线复核**（拿真实 26.3 数据按新口径重算，脚本化）：
普通 108 / 喷溅 108 / 滞留 63，合计 279 = 配方总数（每条恰好出现一次）；
"同基底 + 同材料 + 同产物"的重复为 **0**。残留 9 条同图标条目是**原版多路径**
（发酵蛛眼：3 条 → 伤害、2 条 → 缓慢、2 条 → 长缓慢、2 条 → 强伤害）——
26.2 同样存在（反编译 26.2 `PotionBrewing` 静态初始化可见 `healing / poison / long_poison /
strong_poison + fermented_spider_eye → harming` 多条），其 tooltip 末行会显示各自不同的输入
药水，按原样保留。

**新日志行**（写 `brbe-debug.log`）：
`Loaded 279 potions (minecraft:potion=108, minecraft:splash_potion=108, minecraft:lingering_potion=63).`
—— 三个数应分别等于各标签页的条目数、合计等于总数；旧分支形态未知时**不打括号部分**，日志保持原样。

### 跨分支落地

| 分支 | ① 排序缓存 | ② 酿造数据源 | ③ 幽灵遮罩 | ④ 标签页形态 |
|---|---|---|---|---|
| 26.3 | 修 | 修（26.3 回归） | 修 | 修（26.3 回归） |
| 26.2 | 修（同源） | 无此问题 | 修（同源） | 不适用（数据无形态；代码形状已同步，`belongsToTab` 恒 true） |
| 1.21.11 | 修（同源） | 无此问题 | 修（同源） | 同上 |
| 1.21.1 | 无此缓存，不涉及 | 无此问题 | 未改（旧版 `GhostRecipe` 需另加 accessor，用户先前要求暂缓） | 不适用 |

**④ 的旧分支同步**：`PlatformPotionUtil` / `BrewableResult` / `BrewingRecipeBookComponent` /
`PotionLoader` 四个文件在 26.2 / 1.21.11 也做了同样的**形状同步**——新增的都是 `null` 回退
路径，旧分支行为**等价**（`belongsToTab` 恒 true、`inputFormItem` 回退标签页物品），目的是让
三个分支的共享文件不再分叉、将来移植零冲突。

### 部署

- 26.3-Fabric `28cb15f4d87c1e0086d3ad8d7dd62837`（备份 `20260923-180233`）
- 26.2-Fabric `6a669a2ca822a5f64ee493a9068b0633`（备份 `20260923-180237`）
- 1.21.11-Fabric `37d6238e634b978d02a816a3cabaf734`（备份 `20260923-180233`）

全部原子替换；`javap` 核对 jar 内 `BrewableResult.belongsToTab / inputFormItem /
outputFormItem`、`PlatformPotionUtilImpl.getInputItem / getOutputItem`、
`PotionLoader.formTally` 均在。

### 验证

- **未跑**：④ 的实机确认（看三个标签页条目数）与 ② 的实机确认（`Loaded N potions (...)`
  行）都还没做——用户当时在用自己的实例，未获许可前不启动游戏。日志行落地后**用户自己开一次
  游戏即可核对**：`brbe-debug.log` 里应为上面那一行；酿造书三个标签页分别 108 / 108 / 63 条，
  同一转换不再出现三次。
- 已跑（上一轮）：① 排序探针、③ 堆肥/锻造遮罩探针（详见 2026-09-22（五））。

## 2026-09-23（二）：两个开关默认改为关（四分支同步）

用户要求：「在生存模式配方书中显示3x3配方」（`showAllRecipesInSurvival`）与
「优化原版配方过滤器」（`partialCraftingEnabled`）的默认启用状态 → **关**。
四分支各自 `BrbeConfig` 只改默认值 + 补一行注释，门控逻辑一行未动。

**默认关之后的语义**（涉及两条既有代码路径）：
- `showAllRecipesInSurvival=false`：生存模式配方书不再放行 3×3 配方与环境不兼容配方
  （物品栏 2×2 界面不含 3×3），残缺配方的材料注入路径随之关闭 → 默认即"接近原版"。
- `partialCraftingEnabled=false`：保留原版「仅显示可合成」过滤按钮（不再被
  `DisableCraftableFilter` 移除），Stage 4「可合成置顶」排序只在玩家手动开启过滤时生效。
  ⚠️ "按钮可见 + 玩家开启过滤"正是 2026-09-11 修过的「空气占位符 / 点击崩溃」路径，
  当时的修复（`RecipeBookPageSafetyMixin` + 放行原版谓词）仍在，已随本次构建一起部署。

**已有实例不受影响**：Cloth Config 的 `brbe.toml` 保存着旧值（五个实例实测均为 `= true`，
1.21.1-NeoForge 的 `showAllRecipesInSurvival` 例外为 `false`），默认值只对**新生成**的配置生效；
要立即生效需在配置界面手动关闭或改 toml（未擅自改动用户存档配置）。

**构建/部署**：26.3 `24f37c00`、26.2 `8e113a55`、1.21.11 `51969fbc`（备份 `20260923-181610`，
原子替换）；1.21.1 按既有规则**只构建不部署**（fabric `6eab9d71` / neoforge `a3087c0e`）。
`javap -c` 核对四个 jar 的 `BrbeConfig.<init>`：两字段初始化均为 `iconst_0`（false）。

## 2026-09-23（三）：关闭「在生存模式配方书中显示3x3配方」后 3×3 配方仍留在背包配方书（四分支同源）

**用户反馈**：关闭该开关后，背包（2×2）配方书里的 3×3 配方不消失——没有不可合成标记，
还能点击并弹出幽灵物品；26.3 更严重（在游戏内关掉开关完全无效）。要求四分支排查。

**根因：增量 canCraft 索引的「跳过」判据不覆盖选择谓词**

- vanilla 的显示门是"集合里有没有被选中的配方"：26.x 是
  `RecipeCollection.selectRecipes(stacked, predicate)` 写 `selected`/`craftable`
  （反编译核实：`hasAnySelected()` = `!selected.isEmpty()`；
  `CraftingRecipeBookComponent.canDisplay` 按 `menu.getGridWidth()/getGridHeight()`
  判定"3×3 放不下 2×2"）；1.21.1 是 `canCraft(stacked, w, h, book)` 填
  `craftable`/`fitsDimensions`（显示门 `hasFitting()`）。
- BRBE 的 `RecipeCraftingIndex` + `RecipeCollectionMixin`（26.x）/ `forEachRedirect`
  （1.21.1）为省掉全量重算，在"库存内容没变"时**整体跳过**
  `selectRecipes`/`canCraft`。但它的失效签名只取
  `menu.getRecipeBookType().ordinal()` —— 而**物品栏（`InventoryMenu`）与工作台
  （`CraftingMenu`）都返回 `RecipeBookType.CRAFTING`**（对 26.3/26.2/1.21.11 三个
  版本的本体 jar 反编译核实）。
- 后果：① 从工作台回到背包，签名不变 → 跳过重算 → 3×3 配方带着 3×3 网格下算出的
  selected/craftable **残留**显示在 2×2 背包书里；② 游戏内切换开关同理——谓词变了
  （`incompatibleenvironment/CraftingRecipeBookComponentMixin` 只在开关开启时强制
  `canDisplay=true`），但选择没重算 → 开关"不生效"；③ 残留条目处于"已选中"状态，
  所以按钮照常渲染且可点击 → 2×2 放不下 → 弹出幽灵物品；④ 没有不可合成标记是因为
  `retainIncompatible = 物品栏 && showAllRecipesInSurvival` 在开关关闭时恒 false
  （设计上关闭 = 纯 vanilla，本就不该显示它们，因此也不标记）。

**修复（两层）**

1. **失效签名覆盖整个选择谓词**（四分支）：
   `sig = 配方书类型 *31 + 网格宽 *31 + 网格高 *31 + (开关开启 && 物品栏 ? 1 : 0)`。
   26.x：`selectMatchingRecipes` HEAD 处调用新增的 `brbe$selectionSignature()`；
   1.21.1：在 `brbe$forEachRedirect` 内就地扩展（`menu.getGridWidth/getGridHeight`）。
2. **显示路径兜底**（26.3 / 26.2 / 1.21.11）：pipeline 新增 **Stage 0**
   `brbe$applyGridVisibility(list)` —— 开关关闭且当前网格 < 3×3 时，把"需要更大网格"
   的配方从 `selected`/`craftable` 中剔除，只剩 3×3 的集合整组丢弃。它挂在**显示路径**
   （缓存指纹之前、每轮无条件执行），因此无论 selected 是否被重算，显示结果都正确。
   **1.21.1 不需要第 2 层**：其 `RecipePipeline.applyVisibility` 早就是同一语义的
   无条件兜底（还顺带在 3×3 网格重建 `fitsDimensions`），所以 1.21.1 只有潜在缺陷、
   没有可见症状（用户也未在该分支报告）。

**为什么 26.3 显得"更严重"**：26.2/26.3 这段代码完全相同，差别只在触发路径——26.3 的
实测流程（在配置界面里关开关 / 先进过工作台）正好命中"谓词变了但选择不重算"；26.2 那次
可能是改 toml 重启（新会话重新全量计算）因此没暴露。同一根因，本次一并修掉。

**构建/部署**：原子替换，备份 `20260923-203451`；`javap` 核对 jar 内
`brbe$selectionSignature` / `brbe$applyGridVisibility` 均在。
**分支构建**：26.3-Fabric `85b8ceab3b49655aba2a04425f047d7a`；1.21.1 按规则只构建不部署。

**验证状态**：静态证据完整（三版本反编译核实两个菜单的 RecipeBookType 相同 + 显示门
链路）；**实机验证未做**——需要一次实例启动（探针或用户自测）：关掉开关后背包配方书
不应再有 3×3 配方，从工作台回背包同样不应有。


## 2026-09-25：unlockAll 关掉后配方书全空（BRBE 自己删了玩家进度）+ Mouse Wheelie 滚轮冲突

**用户报告（26.2-Fabric 0.19.5-for-test 整合包）**：关掉「自动解锁所有配方」后所有配方书
标签与配方立刻消失（纯净实例不复现）；另有两项滚轮异常：配方区滚轮不触发翻页动画/音效、
标签条滚轮会切换选中的标签。

### ① 配方书全空 —— 根因是 BRBE 的"污染修复"误判并删掉了玩家真实进度

实例日志实锤（`logs/2026-09-24-1.log.gz`、`logs/brbe-debug.log`）：

- 该整合包装了 `get-recipes-1.0.2`（其 `ServerRecipeBookMixin` 注入
  `ServerRecipeBook.sendInitialRecipeBook` HEAD，给玩家解锁**全部**配方 →
  `GetRecipes: server sent 1568 recipe book entr(ies) (replace=true)`）。
- 关掉开关那一刻：`unlock-all syncToConfig: unlockAll=false last=true` →
  `[Server thread/WARN] [BRBE] reset unlock-all-polluted server recipe book: removed 1561
  known recipes` → `[BRBE-CACHE] rebuild RETURN — known=0` → 配方书（含标签）空白。
- 旧 `repairPollutedServerBook()` 的判据是「**服务端**配方书已解锁 ≥90% ⇒ 一定是旧版 BRBE
  污染的」，但"解锁全部"模组/数据包、管理员指令、通关存档都会产生同一状态 —— 它删的是
  玩家真实进度（服务端数据，且会持久化）。纯净实例只有 126/3300 解锁 → 阈值未命中 →
  所以只有整合包复现（用户猜"模组冲突"方向正确，但冲突点是 BRBE 的破坏性启发式）。
- 第二重：`get-recipes` 还会在客户端本地补注入（不经 packet），而 `unlockRecipes()` 把
  **全部 1568 个 display 都记进 `unlockAllInjected`**（其中绝大多数本来就在 known 里、
  不是 BRBE 加的）；于是下一次「开→关」时 `revokeUnlockAll()` 按标记删掉 1568 个 →
  又空一次（`unlock-all revoked: removed 1568 displays, 0 server-unlocked kept`）。

**修复（26.2 / 26.3 / 1.21.11，`util/RecipeUnlockUtil.java`）**

1. **删除破坏性修复**：`repairPollutedServerBook()` → `reportFullyUnlockedServerBook()`，
   只做**每会话一次**的诊断（服务端 known ≥90% 时 WARN 一行，说明"配方书被 BRBE 以外的
   东西全解锁了，开关无法隐藏它们"），**不再改动任何服务端数据**。当前实现本就纯客户端，
   没有需要自动"修复"的东西。
2. **只标记自己真正加进去的 display**：`unlockRecipes()` 先 `known.containsKey(id)` 判定，
   已在书里的（服务端解锁 / 其它模组注入）跳过且不记标记 → `revokeUnlockAll()` 只撤销
   BRBE 自己加的那些。1.21.1 的 unlock 实现是另一套（无污染修复，按"服务端权威集合"
   回滚），无对应缺陷，未改。

### ② 滚轮无翻页动画/音效 + 滚轮切换选中标签 —— Mouse Wheelie 兼容在 26.x 失效

反编译实例内 `mouse-wheelie-1.16.3+mc26.2.jar` 及其内嵌 `amecs-mouse-inputs` 实锤：

- Mouse Wheelie 的配方书滚轮实现已从 `IScrollableRecipeBook.mouseWheelie_onMouseScrollRecipeBook`
  （**26.x 中已无人实现 = 死分支**）迁到 `ISpecialScrollableScreen`
  （`MixinAbstractRecipeBookScreen`）→ `IRecipeBookWidget.mouseWheelie_scrollRecipeBook`
  （`MixinRecipeBookWidget`）：书矩形内自己翻页；书左侧 30px 标签条内**直接切换选中标签**。
- 其滚轮键位 `key.mousewheelie.scroll_up/down` 默认绑到
  `key.amecs_mouse_inputs.scroll.up/down`，属于 amecs **priority** 键位；amecs 在
  `MouseHandler.onScroll` 内对 priority 命中会 **`ci.cancel()`** —— BRBE 的
  `MouseScrollHandler`（RETURN 注入）因此收不到滚动 → 翻页动画与音效永不触发，页面由
  mousewheelie 自己瞬翻。
- BRBE 原有兼容 `MixinMWClient` 只取消了那条**死分支**的调用 → 26.x 上等于没生效。

**修复**：`MixinMWClient` 增补 `@Inject(HEAD, cancellable = true, require = 0)` —— 光标位于
配方书矩形或左侧标签条上时 `triggerScroll` 直接返回 `false`（"未处理"）：amecs 不再取消
原版滚动 → BRBE 正常入队并自己翻页（动画+音效恢复），标签也不会被误切；配方书以外的位置
（容器槽位、创造物品栏扫物品）行为不变。几何判定抽到普通类 `compat/MouseWheelieCompat`
（用 `AbstractRecipeBookScreenAccessor` + `RecipeBookComponentAccessor.brbe$invokeGetXOrigin/
GetYOrigin`；26.x 用 `minecraft.gui.screen()`，1.21.11 用 `minecraft.screen`）。
**1.21.1 未移植**：该分支实例均无 Mouse Wheelie，且 1.21.1 世代的 mousewheelie 仍走旧接口
（现有兼容注入即那条活分支），无法验证故不动。

### ③ 顺带修正 RBIP 标签滚动区（三分支 + 1.21.1）

`rbip$isMouseOverAnyVisibleTab` 的上下两条原以标签矩形为中心上下各扩 20px
（1.21.1 为 `SCROLL_PADDING`），于是下方那条**伸进书体 20px、盖住配方网格最后一行** ——
在配方区滚动会被当成"滚标签"吃掉（标签页数量 >1 时可见）。改为**余量只加在书体外侧**、
内侧止于标签自身边缘。

### 构建 / 部署（原子替换，备份 `20260925-010108`）

- 26.2-Fabric `1acc668fd2008d5cd1cc26fa6256c841` —— 部署 `26.2-Fabric` 与
  `26.2-Fabric 0.19.5-for-test` 两个实例
- 26.3-Fabric `61017b36af5bd3471fe16b8050ff5a3a`；1.21.11-Fabric `74b2177c3c506f89aa18d4307c22fc28`
- 1.21.1（按规则只构建不部署）：fabric `ec89a86f7a2d8c03dd279be0dc0d1c36`、
  neoforge `465ae140deaec2cac7b2e315f714ff9b` —— 本分支只有 ③

### 验证方法（未做，待用户实机）

1. 整合包里关掉「自动解锁所有配方」：配方书不再空白（get-recipes 解锁的配方全部保留）；
   开→关反复切换同样保留。日志应出现
   `unlock-all: injected N displays (M already unlocked, left untouched)`，且**不再出现**
   `reset unlock-all-polluted server recipe book`。
2. 配方区滚轮 = 平滑翻页动画 + 翻页音效；标签条滚轮不再切换选中标签；槽位上的
   mousewheelie 物品滚动（滚一个物品进出）不受影响。

> 本分支已部署，md5 `61017b36af5bd3471fe16b8050ff5a3a`。


## 2026-09-25（二）：滚轮接缝收敛 + 兼容自检（架构，26.2 / 26.3 / 1.21.11）

**动机**：上一轮定位到"滚轮无翻页动画/音效 + 滚轮切标签"是 mousewheelie 抢走了手势。修好之后
顺带把这一类问题的**根**处理掉：同一个手势当时有<b>三套各自为政</b>的实现，

| 实现 | 位置 | 职责 | 问题 |
|---|---|---|---|
| BRBE | `mixins/MouseScrollHandler`（RETURN 注入） | 只把滚动排进 `queuedScroll` | 不认领事件；被 amecs 的 priority 键位一 cancel 就再也收不到 |
| RBIP | `mixin/MouseMixin`（HEAD 注入） | 标签栏翻页并 `ci.cancel()` | 与上者各写一套坐标/判定，谁先跑取决于 mixin 应用顺序 |
| 兼容 | `MixinMWClient` | 事后堵 mousewheelie | 26.x 上落在**没有任何实现类**的死分支（白挂几个月） |

### ① 接缝收敛：`util/RecipeBookGesture`

新增普通类 `RecipeBookGesture`（`BOOK_WIDTH/HEIGHT/TAB_STRIP_WIDTH` 三常量在此唯一定义）：

- `claimScroll(mouseX, mouseY, verticalAmount)`：**唯一归属判定 + 唯一消费点**。
  ① 先问 RBIP 标签栏（左列/上下条带/翻页箭头，内部自带开关守卫）；
  ② 再判配方书面板本体 + 其左侧标签条 → 写入 `BetterRecipeBook.queuedScroll`
  （渲染时由 `scrollablepages/RecipeBookPageMixin` 翻页，动画/音效/残缺红罩/pin 全链路一行未改）。
- `ownsRecipeBookArea(x, y)`：纯几何判定，供 mousewheelie 兼容做兜底（接缝在前，正常轮不到它）。

`MouseScrollHandler` 与 RBIP `MouseMixin` 都改成 **`MouseHandler.onScroll` HEAD 调 `claimScroll`**：
先跑到的那一个认领并 `ci.cancel()`，**另一个连同原版方法体一起被跳过** → 注入顺序不再影响结果；
下游（mousewheelie / amecs priority 键位 / 原版快捷栏滚动）根本看不到这次事件。
保留 RBIP 那个入口（而不是删掉）是为了 RBIP 配置独立加载时的鲁棒性——两个入口幂等，只有一个能认领。

**范围与可见行为变化**：接缝只认领**原版配方书界面**（`AbstractRecipeBookScreen`：背包/工作台/熔炉族）。
BRBE 自研的酿造台/锻造台书没有竞争者，仍走"兜底无条件入队 + 各自页面命中判定"的老路。
唯一可见变化：在配方书区域内滚动时**原版快捷栏不再跟着滚动**（装了 mousewheelie 时本来就是这个表现；
即旧版 `scrolling.enabled`「把滚轮限制在配方书区域」的语义）。

### ② 兼容自检：`compat/CompatSelfCheck` + `compat/ModPresence`

- `ModPresence`：纯反射（**不引用任何 Minecraft 类**，Mixin 引导阶段可安全调用）——`CompatMixinPlugin`
  也改用它，判定只有一份。
- `CompatSelfCheck.run()`（`BetterRecipeBookClientFabric` 的 `CLIENT_STARTED`）：
  逐个条件兼容打状态行；**目标缺失一律 WARN 进 latest.log**；`Class.forName(..., initialize=false)`
  保证自检绝不会提前触发目标模组的静态初始化。
  例：`mousewheelie 1.16.3+mc26.2: triggerScroll ✓ legacyTarget ✓ → 配方书滚轮由 BRBE 接缝认领`
  或（这次的真实情况）`旧接口 IScrollableRecipeBook 已不存在/…` 的显式提示。
- `CompatSelfCheck.noteSeamClaim(...)`：**首次接缝认领**时记一行（每次会话一行，且只在与滚轮模组共存时记）
  ——这是"接缝真的生效、下游被绕过"的运行时证据。
- RBIP 接缝也自检：`RecipeBookScrollAccess.class.isAssignableFrom(RecipeBookComponent.class)`
  → 不成立说明 RBIP mixin 没应用，启动即 WARN（标签栏滚轮会失效）。

**1.21.1 未改**：该分支滚轮只有单一入口（`MouseScrollHandler` HEAD + RBIP 走原版
`mouseScrolled` 分发），没有第二个消费者需要仲裁，也没有装 mousewheelie 的实例，无法验证故不动。

**构建/部署**（原子替换，备份 `20260925-013242`）：26.2-Fabric `396a37314e29f116869231d9b56050f8`
（部署两个 26.2 实例）、26.3-Fabric `41ccb600704251414e973ed6f50dbdfb`、
1.21.11-Fabric `69d517aefbcf8d357be31d9228c37d38`；`javap` 核对三个 jar：
`MouseScrollHandler` 为 HEAD + cancellable 且调用 `RecipeBookGesture.claimScroll`、
RBIP `MouseMixin` 只转发同一方法、新旧 mixin 类均无 `lambda$`。

**验证方法**：启动后 `brbe-debug.log` 应有三行 `[BRBE-COMPAT]`（mousewheelie 状态 / 接缝状态），
整合包首滚配方书时再多一行"滚轮接缝生效…"；配方区滚轮 = 平滑翻页 + 音效，标签条滚轮不再切标签。

> 本分支已部署，md5 `41ccb600704251414e973ed6f50dbdfb`。


## 2026-09-25（三）：unlockAll 关闭 = 只显示「进度系统已解锁」的配方（26.2 / 26.3 / 1.21.11）

**用户反馈（26.2 整合包）**：「关掉『自动解锁所有配方』后不再空书了，但进度系统中未解锁的
配方并不会被隐藏」——开关关掉后配方书照样全亮。

**根因（新语义缺口，不是回归）**：上一轮的修复只做"撤销 BRBE 自己注入的 display"，这在纯净
环境等价于恢复服务端状态；但整合包里的 `get-recipes` 会给**服务端配方书授予全部配方**
（实例日志 `GetRecipes: server sent 1568 ... (replace=true)`、`serverUnlocked=1568`、
`unlock-all: injected 0 displays (1568 already unlocked, left untouched)`），于是"服务端状态"
本身就是全解锁 —— 撤销管不到别人塞进来的东西。

**修复：进度可见性白名单（`util/ProgressionUnlocks`）**

- 权威信号 = **原版进度系统**：每条原版配方都有一条 `minecraft:advancement/recipes/**`
  成就（26.2 共 1572 条，`rewards.recipes` 就是它解锁的配方），BRBE 自己生成的
  `brbe:recipe/**`（模组锻造）同样走 `rewards.recipes`。于是
  `白名单 = ⋃ { advancement.rewards.recipes() | 该 advancement 已完成 }`
  （服务端枚举 `MinecraftServer.getAdvancements().getAllAdvancements()` +
  `PlayerAdvancements.getOrStartProgress(holder).isDone()`），再经
  `RecipeManager.listDisplaysForRecipe` 映射成 display id（与 unlock-all 同一套枚举）。
  与酿造/锻造书既有的 `RecipeUnlockTracker` 同一个进度权威，语义一致。
- 作用点：26.x 管线新增 **Stage 0b `brbe$applyProgressionVisibility`**（紧随 Stage 0 网格
  可见性、指纹/缓存之前，每轮无条件执行）——把白名单外的 display 从 `selected`/`craftable`
  剔除、只剩白名单项的集合整组丢弃。与几何兜底同一哲学：**无论谁往配方书里塞了什么，
  显示结果由白名单决定**。
- 失效与开销：脏标记（世界卸载 `clear()`、`handleUpdateAdvancementsPacket` RETURN、
  开关切换）驱动；`whitelist()` 250ms 节流，且先做廉价的"配方集合是否变化"比较，
  只有真变了才做昂贵的 display 枚举。
- **多人服务器**：无集成服务器 → `whitelist()` 返回 `null` → **不过滤**（保持服务端状态；
  与 unlock-all 本身只支持单机一致），并记一行说明。
- 诊断：`progress filter: N recipes / M displays unlocked by completed advancements`（每次重算）
  与 `progress filter: hid K displays not unlocked by progression`（隐藏条数变化时）。
  **副产物**：纯净/正常世界里"没人绕过进度塞配方" ⇒ `hid 0`，即该过滤器是**零行为变化**的
  安全网；只有存在绕过进度的授予（解锁全部模组、`/recipe give`、直接 awardRecipes 的模组）
  时才生效。

**未做**：R/U 查询 viewer 仍按既有规则显示（本次只收敛配方书显示路径）；1.21.1 未移植
（其配方书是 `RecipeHolder`/`known: Set<ResourceLocation>` 的另一套模型，需单独实现）。

**构建/部署**（原子替换，备份 `20260925-012746`）：26.2-Fabric `df09b4642b990a80c58833b4dc90cd39`
（部署两个 26.2 实例）、26.3-Fabric `04277b31f0fbfa7a6c3b87ffc5b7ec94`、
1.21.11-Fabric `82b59c9305fdc9cf6a2bee07f4c4f688`；`javap` 核对三个 jar：`ProgressionUnlocks` 在、
管线 mixin 引用 3 处、无 `lambda$`。

**验证方法**：整合包里关掉「自动解锁所有配方」→ 配方书只剩进度（成就）解锁的配方，
`brbe-debug.log` 出现 `progress filter: …` 与 `progress filter: hid …`；
开关打开 → 立即恢复全量。纯净实例（没有绕过进度的授予）应看到 `hid 0`（行为不变）。

> 本分支已部署，md5 `04277b31f0fbfa7a6c3b87ffc5b7ec94`。


## 2026-09-25（四）：「优化原版配方过滤器」默认改回**开**

用户要求（2026-09-25）：`partialCraftingEnabled` 的默认启用状态 → **开**（`true`），
除 26.1.2（停维）外四分支同步。

- 仅改 `BrbeConfig` 的字段默认值与注释：26.2 / 26.3 / 1.21.11 在
  `src/main/java/com/alonie/brbe/config/BrbeConfig.java:116`，1.21.1 在
  `common/src/main/java/com/alonie/brbe/config/BrbeConfig.java:99`；注释改为
  「默认开（2026-09-25 用户要求；2026-09-23 曾按要求改为关）」。
- 另一个开关「在生存模式配方书中显示3x3配方」（`showAllRecipesInSurvival`）**保持默认关**，
  2026-09-23 的结论不变。
- **按用户要求本轮不构建、不部署**：已存在实例的 `brbe.toml` 不受影响（默认值只作用于
  新生成的配置文件）；lang tooltip 未写默认值，无需同步。
- 上一条 2026-09-23「两个开关默认改为关」的记录中，关于 `partialCraftingEnabled` 的部分
  由本轮取代（`showAllRecipesInSurvival` 部分仍然有效）。


## 2026-09-25（五）：RBIP 标签页看不到 3×3 配方 + 「刷怪蛋」标签消失（同一根因）

**用户报告（26.2 整合包）**：① 创造标签「刷怪蛋」里的嘎枝之心（`minecraft:creaking_heart`）
在配方书里没有归到「刷怪蛋」标签 —— 该标签**整个消失**（`unlockAll` 开着），但配方能搜到；
② RBIP 标签页**完全无法显示 3×3 配方**，即便当前打开的是工作台。

**同一个根因**：`recipebookispain_extended/mixin/groups/ClientRecipeBookMixin`
（`rbip$refreshCreativeGroups`）里的这段**数据路径**过滤：

```java
if (config != null && !config.showAllRecipesInSurvival) {
    if (rbip$needsLargerGrid(display)) continue;   // ← 丢掉"需要更大网格"的配方
}
```

它在 `ClientRecipeBook.rebuildCollections` 时机运行，只认 `known` 集合，
**看不到当前打开的是 2×2 背包还是 3×3 工作台**，于是：

- 工作台上也被丢掉 → RBIP 标签页永远没有 3×3 配方（报告 ②）；
- 嘎枝之心配方是 `crafting_shaped` 的 `" L ", " R ", " L "`（**3×3**，`data/minecraft/recipe/
  creaking_heart.json`，反编译核实）；「刷怪蛋」组里它**唯一**有配方的物品 → 该组一个
  bucket 都建不出来 → `collectionsByTab` 里没有该类别 → `rbip$paginateTabButtons` 按
  "类别无配方集合"把标签隐藏（报告 ①）；配方本身仍在原版 `crafting_misc` 集合里，故可搜索。

**修复**：删掉这段数据路径过滤（连同 `rbip$needsLargerGrid` 助手与不再使用的两个 import）。
网格可见性**只由显示路径按当前菜单判定**：`pipeline/RecipeBookComponentMixin
.brbe$applyGridVisibility`（Stage 0）—— 2×2 + `showAllRecipesInSurvival=false` 时把放不下的
配方从 `selected`/`craftable` 剔除、放得下时原样放行；RBIP 的合成组走同一条管线，因此
背包里仍然看不到/点不到 3×3 配方（2026-09-23 的诉求不变），工作台上则正常显示。

> 教训（与「接缝收敛」同一类）：**数据路径的过滤看不见上下文**（这里缺的是"当前网格"），
> 凡是"按当前界面状态决定显示什么"的逻辑都必须挂在显示路径上，否则会被缓存/时机固化。

**1.21.1 无此问题**：该分支 RBIP 没有这段过滤（无 `rbip$needsLargerGrid`/`showAllRecipesInSurvival`
引用），未改。

**构建/部署**（原子替换，备份 `20260925-013901`）：26.2-Fabric `32ed9f6048a7ae66df19d806c5281a5c`
（部署两个 26.2 实例）、26.3-Fabric `c33fd2c1d1b93f8be31f6544812d0f98`、
1.21.11-Fabric `83c7cb408df5bb6719757a6d02f6a4ae`；核对三个 jar 内
`ClientRecipeBookMixin.class` 已不含 `needsLargerGrid` / `showAllRecipesInSurvival`。

**验证方法**：工作台 → RBIP 标签（如「建筑方块」）应能看到并点击 3×3 配方；
「刷怪蛋」标签应重新出现且含嘎枝之心；背包（2×2）里这些 3×3 配方仍不显示、不可点。


## 2026-09-25（六）：查询窗口压在配方书上时两边都翻不了页（滚轮接缝的回归）

**用户报告**：查询界面（LEI/R-U viewer）放在配方书**上面**时，窗口的翻页区域被配方书占用
——窗口翻不了页，配方书的翻页区域也触发不了。

**根因：当天（二）"滚轮接缝收敛"引入的回归**。接缝把认领点提到了
`MouseHandler.onScroll` HEAD，而它只按"**配方书矩形** + 标签条"认领并 `ci.cancel()`，
没看"窗口是不是压在书上面"：

| 步骤 | 收敛前 | 收敛后（回归） |
|---|---|---|
| `MouseHandler.onScroll` HEAD | 无人认领 | 接缝按配方书矩形**认领并 cancel** |
| 屏幕分发 → 静态 `RecipeViewerOverlay.mouseScrolled` | 窗口/pin 拿到滚轮 → 翻页 ✓ | **收不到**（事件已 cancel） |
| 配方书自己的入队滚动 | 窗口在上时被 `RecipeBookPageMixin` 的 `modalMaskOwnsCursor` 守卫丢弃 | 同样被丢弃 |

两边都失效，与用户描述完全一致。`isPageTurnButton`/点击路径不受影响（点击仍走
`AbstractRecipeBookScreen.mouseClicked` HEAD 的窗口分发）——所以症状只在**滚轮**上。

**修复**（`util/RecipeBookGesture#claimScroll`，26.2 / 26.3 / 1.21.11）：认领配方书矩形**之前**
先判桌面窗口语义——`RecipeViewerOverlay.modalMaskOwnsCursor(x, y)`（窗口/pin/预览拥有光标）成立时，
把这次滚动**转交给** `RecipeViewerOverlay.mouseScrolled(...)` 并照样认领（cancel）：窗口/pin
正常翻页，同时仍挡掉 mousewheelie / amecs priority 键位 / 原版快捷栏滚动。

> 与 1.21.1 的实现一致：那分支的 `MouseScrollHandler` 本来就是"先给 viewer、消费才 cancel"，
> 所以从未有这个回归。26.x 的接缝收敛时漏掉了这一步。

**构建/部署**（原子替换，备份 `20260925-023423`）：26.2-Fabric `2008fee5074a61a211eb1e95008c3266`
（部署两个 26.2 实例）、26.3-Fabric `0439539fb4395cacabbd0d27e783fc68`、
1.21.11-Fabric `fd64d73904ef27d9668bd695efc7e9f1`；核对三个 jar 内
`RecipeBookGesture.class` 均引用 `modalMaskOwnsCursor` + `mouseScrolled`。

**验证方法**：把查询窗口拖到配方书上方（重叠）→ 在窗口的翻页区域滚轮应能翻查询页；
把指针移到窗口之外的配方书区域滚轮 → 配方书照常翻页（窗口不挡）；实测无误即可。


## 2026-09-25（七）：新增 `/brbe` 客户端指令（clear 子命令）

用户需求（四条，26.1.2 除外的四个分支都要做）：

```
/brbe                        → 用法
/brbe clear                  → 列出 clear 的全部子命令与功能描述
/brbe clear configchange     → 一键把配置界面的所有配置项恢复为默认值
/brbe clear rbippin          → 清除所有 RBIP 标签的 pin
/brbe clear recipepin        → 清除配方书配方的 pin
/brbe clear leipin           → 清除查询界面（LEI）对象的 pin
```

**结构**（与加载器解耦，四个分支共用同一套语义与文案键）：

- `command/BrbeCommandTree`：指令树 + `Feedback<S>` 源适配接口（`success/failure`），
  树的形状只有一份；动作抛异常只翻成聊天错误、不冒泡。
- `command/BrbeCommandActions`：四个动作，返回 `Result{ok, langKey, args}`。
- 注册：Fabric 三个分支与 1.21.1-fabric 用 `ClientCommandRegistrationCallback.EVENT`
  + `FabricClientCommandSource.sendFeedback/sendError`；1.21.1-neoforge 用**游戏总线**
  `RegisterClientCommandsEvent` + `CommandSourceStack.sendSuccess/sendFailure`。

**为此新增的底层能力**：

| 位置 | 新增 | 说明 |
|---|---|---|
| `PinnedRecipeManager` | `clearAll()` | 清空 + `version++`（管线缓存失效）+ `store()` 落盘（异步 PinStore） |
| `pin/TabPinManager` | `clearAll()` | 清空 + `save()`（`brbe.tabpins.json`） |
| `pinoverlay/PinOverlayManager` | `clearAllAndSave()` | 清空 + `save()`（`brbe.pinoverlays.json`） |
| `config/KeybindingGuiRegistrar` | `applyConfigToKeyMappings()` | 配置→原版 `KeyMapping` + 落盘 options.txt |
| 各客户端入口 | 指令注册 | Fabric/NeoForge 各一处 |

**两处语义要点（易错）**：

1. Cloth 的 `resetToDefault()` **只替换配置对象**（反编译 `ConfigManager.resetToDefault` 核实：
   仅 `serializer.createDefault()` + validate，**不落盘、不通知监听器**）→ 必须补一次 `save()`，
   否则 BRBE 的 `ConfigChanged` 监听（UI/引擎/管线刷新）不会触发；键位字段还要
   `applyConfigToKeyMappings()` 写回 `KeyMapping`，否则运行中的按键仍是旧绑定
   （KeyMapping 只持久化在 options.txt，下次改键会把旧值写回配置）。
2. 清 pin 后调 `updateTabs` + `recipesUpdated()`（1.21.1 走 `RecipeUpdateListener`），
   让当前打开的配方书**立即**重建（固定顺序/固定标记不用等重开界面）。

**语言**：7 语言 × 11 键 × 4 分支 = 28 个 lang 文件（`brbe.command.*`），JSON 全部校验通过。

**构建/部署**（原子替换，备份 `20260925-025315`）：26.2-Fabric `1dc4a5b2c231123ac9bc126d8fa805a3`
（部署两个 26.2 实例）、26.3-Fabric `98188c544d6f101fc1ad5790420752e6`、
1.21.11-Fabric `256bd67e492c91b0db6fe57937f82a3f`；1.21.1 按规则**只构建不部署**：
fabric `8bc30a8a9fc869dc68aa2dba4adc6ffc`、neoforge `a44cf6c16349dc16a0c7352860373807`。
核对三个部署 jar：`command/BrbeCommand{Tree,Actions}.class` 在、客户端入口含
`ClientCommandRegistrationCallback`、jar 内 zh_cn 含 11 个 `brbe.command.*` 键。

**验证方法**：游戏内（或主菜单）执行 `/brbe`、`/brbe clear` 应列出用法/子命令；
`/brbe clear configchange` 后配置界面各项回到默认值且按键绑定同步回默认键；
三个 pin 子命令分别清空 RBIP 标签固定 / 配方固定 / 查询对象固定，并给出条数反馈。

**2026-09-25（二）：拼音搜索「语言相关默认值」修复——`/brbe clear configchange` 不再关掉拼音（四分支同步）**

用户反馈：拼音搜索是**语言条件配置项**——中文（zh_*）下配置界面显示该开关、默认**开**；其他语言下
不显示该项、默认**关**。但执行 `/brbe clear configchange` 后（语言为中文）拼音搜索被改成了**关**。

**根因**：`BrbeConfig.pinyinSearch` 的字段默认值只能是常量 `false`（"语言相关默认值"写不进 POJO），
中文下的"默认开"是启动钩子在运行时收敛的（`CLIENT_STARTED`）。而 `/brbe clear configchange` 走
Cloth 的 `holder.resetToDefault()`——**反编译核实**（`ConfigManager.resetToDefault`）它只做
`config = serializer.createDefault()` + `validatePostLoad`，于是"恢复默认"= 回到 POJO 常量 `false`，
**绕过了语言默认值语义**（与配置界面该项 `setDefaultValue(true)` 自相矛盾）。同时核实 `save()` 会以
`this.config`（新对象）逐个回调保存监听器，故 `resetToDefault()` 之后补的 `save()` 仍是"落盘 + 通知"。

**修复：新增单一决策点 `config/PinyinSearchDefaults`**
- `isChineseLanguage(Minecraft)` / `isChineseLanguage()` / `languageDefault()` /
  `applyLanguageDefault(BrbeConfig, Minecraft)`：中文 = 开、其他语言 = 关；**语言未知
  （`options == null`）时不动值**——不把"读不到语言"误判成非中文而关掉功能；返回是否有改动，
  调用方据此决定要不要 `save()`。
- `PinyinSearchGuiRegistrar.isChineseLanguage()` 删除（唯一调用点改走决策点），注册类只保留
  "显示与否"职责（javadoc 同步）。
- 两个调用方：① 启动钩子（原内联 `if (chinese)` / `else if (!chinese)` 收敛逻辑改调
  `applyLanguageDefault`）；② `BrbeCommandActions.resetConfig()`——在 `resetToDefault()` **之后、
  `save()` 之前**对 **`holder.getConfig()`（新对象）** 收敛。⚠️ 必须取 `holder.getConfig()`：
  `resetToDefault()` 换掉的是 holder 内的新实例，此刻静态字段 `BetterRecipeBook.config` 仍指向旧对象；
  随后的 `save()` 才以新对象触发保存监听器（`AppContext` 的监听器借此把静态引用切到新对象）。

**顺带补齐（仅 1.21.1-NeoForge）**：该端此前**完全没有**拼音接线——既没注册
`PinyinSearchGuiRegistrar`（配置项在所有语言下都显示，不受语言条件约束），也没有语言默认值收敛
（中文下默认值仍为关）。本轮补上：入口 `PinyinSearchGuiRegistrar.register()` + 首个
`ClientTickEvent.Post` 一次性 `applyLanguageDefault`（NeoForge 无 `CLIENT_STARTED` 对应事件，
首个客户端 tick 是等价的"初始化完成、只跑一次"时机；`pinyinDefaultsApplied` 静态闸门保证只跑一次）。

**构建/部署**（原子替换，备份 `20260925-030542`）：26.2-Fabric `c818951ab9661d1657ffa188d6b26b72`
（部署两个 26.2 实例）、26.3-Fabric `c42fdcf60fd264c1df430ce0ea2e8ef6`、
1.21.11-Fabric `7d998447be3ba88b9f560da4d1b58c90`；1.21.1 按规则**只构建不部署**：
fabric `1f981039a60ec75df8857e897e71c4af`、neoforge `babb971d93d75a49ba20233bfd3a954f`。
（部署时 26.2-Fabric 0.19.5-for-test 实例正在运行——原子替换对运行中的会话安全。）

**验证**（javap 部署 jar 核实）：`com/alonie/brbe/config/PinyinSearchDefaults` 三个方法在；
`BrbeCommandActions.resetConfig` 字节码顺序 = `resetToDefault()` → `getConfig()` →
`Minecraft.getInstance()` → `applyLanguageDefault(...)` → `save()` → `applyConfigToKeyMappings()`；
各入口含对 `PinyinSearchDefaults` 的引用（1.21.1-NeoForge 入口另含 `PinyinSearchGuiRegistrar.register`）。

**验证方法**：中文语言下执行 `/brbe clear configchange` → 配置界面各项回默认值，且「拼音搜索」
**仍为开**（此前会被关掉）；切到非中文语言重启 → 该项被强制关且配置界面不显示；
再把语言切回中文重启 → 自动恢复为开。

**2026-09-25（三）：自定义翻页音效 —— `/brbe set pagesound <声音ID>` + 配置项「翻页音效」（四分支同步）**

用户需求：BRBE 全部界面的翻页音效可自定义，两条途径——新指令 `/brbe set pagesound [MC 声音资源 ID]`
与配置界面新增配置项（「快捷键&数值」页「动画时长」下方，标题「翻页音效」，字符串，默认值 = 现有翻页音效 ID，
无 tooltip），数据存配置文件。

**当前默认音效 = `minecraft:ui.button.click`**（反编译四个版本 `SoundEvents` 常量池核实：
`UI_BUTTON_CLICK` 注册名即 `ui.button.click`；也就是 BRBE 一直用的"哒"声，音量 0.25 × `pageFlipVolume`）。

**落地（四分支一致）**：

| 层 | 改动 |
|---|---|
| 解析（单一入口） | 新增 `util/PageFlipSound`：`DEFAULT_ID`、`resolve()`（读配置 → `Identifier/ResourceLocation.tryParse` → `BuiltInRegistries.SOUND_EVENT.getOptional` → 失败**回退默认音效**，不静音）、`lookup(raw)`、`exists(raw)` |
| 播放 | `ClientCompat.playPageFlipSound` 改播 `PageFlipSound.resolve()` —— 全部 BRBE 翻页界面（配方书配方区/RBIP 标签栏/查询 viewer 对象区、标签条、工作站列）本来就走这里，一处生效全站生效 |
| 配方书滚轮翻页 | 26.2/26.3/1.21.11 的 `scrollablepages/RecipeBookPageMixin` 原先把 `SoundEvents.UI_BUTTON_CLICK` **内联**播放（绕过了 ClientCompat）→ 改为调用 `ClientCompat.playPageFlipSound`（保留 10ms 节流）；顺带删掉两条不再使用的 import |
| 配置 | `BrbeConfig.pageFlipSound`（String，`@ConfigEntry.Category("keybindings")`，声明在 `pageAnimationDuration` 之后 → GUI 顺序 = 音效音量 → 动画时长 → **翻页音效**；无 `@ConfigEntry.Gui.Tooltip` → 无 tooltip）。Cloth 默认字符串条目即文本输入框，`TextFieldListEntry` 内部 `EditBox.setMaxLength(999999)`（反编译核实，无 32 字上限） |
| 指令 | `BrbeCommandTree` 新增 `set` / `set pagesound <sound>`（`IdentifierArgument.id()`；1.21.1 为 `ResourceLocationArgument.id()`），带**声音 ID 前缀补全**（遍历 `SOUND_EVENT.keySet()`）；`BrbeCommandActions.setPageFlipSound(String)` 校验 `PageFlipSound.exists` → 未注册则报错不写入 |
| 文案 | 7 语言 × 4 分支：新增 `text.autoconfig.brbe.option.pageFlipSound`、`brbe.command.set.header`、`brbe.command.set.pagesound.{usage,done,unknown}`，并把 `brbe.command.usage` 补上 `/brbe set …`（28 个 lang 文件，JSON 全部校验通过，diff 每文件仅 9 行） |

**两个实现要点**：
1. **不能用 `IdentifierArgument.getId(ctx, …)`**：它固定收 `CommandContext<CommandSourceStack>`，而
   `BrbeCommandTree` 对源类型泛型（Fabric/NeoForge 各自适配）→ 改用
   `ctx.getArgument("sound", Identifier.class)`。
2. **`/brbe set pagesound` 会立即试听一声**：与真正的翻页共用 `ClientCompat.playPageFlipSound`，
   因此同样受「鼠标滚轮翻页音效」开关与「音效音量」控制（关着开关时不会响）。

**顺带补齐（1.21.1 标签栏翻页箭头）**：26.2/26.3/1.21.11 的 RBIP 标签栏翻页箭头点击会播放翻页音效，
1.21.1 的 `rbip$handleClick` 两个箭头分支**此前静音** → 补 `ClientCompat.playPageFlipSound`，四分支行为一致。

**未覆盖（说明）**：原版配方书自带的 `<` / `>` 翻页箭头是 vanilla `ImageButton`，点击音由
`AbstractWidget.playDownSound` 播放（vanilla 硬编码），本配置不影响它们；RBIP 标签栏箭头、滚轮翻页、
查询窗口翻页等 BRBE 自绘／自管的翻页路径全部走配置音效。

**构建/部署**（原子替换，备份 `20260925-132834`）：26.2-Fabric `0de76cc19ad692a1b062ce4ae828c022`
（部署两个 26.2 实例）、26.3-Fabric `ee5c40fc764a08a4de73ab584212d33b`、
1.21.11-Fabric `0f2418097a827f96d9211fcb823f1942`；1.21.1 按规则**只构建不部署**：
fabric `693789f711869eddd078cf97c1836924`、neoforge `20e375971228041c038e104e15d84ec3`。

**字节码核对**：五个 jar 内 `PageFlipSound`（`DEFAULT_ID`/`resolve`/`lookup`/`exists`）在；
`BrbeConfig.pageFlipSound` 字段带 `ConfigEntry$Category("keybindings")`、默认值常量
`minecraft:ui.button.click`，且字节码赋值顺序紧跟在 `pageAnimationDuration` 之后；
`BrbeCommandTree` 常量池含 `set`/`pagesound`/`sound`；`ClientCompat.playPageFlipSound` 调用
`PageFlipSound.resolve()`；`scrollablepages/RecipeBookPageMixin` 不再引用 `SoundEvents`/`SimpleSoundInstance`；
jar 内 zh_cn 含 `text.autoconfig.brbe.option.pageFlipSound` 与 4 个 `brbe.command.set.*` 键。

**验证方法**：① `/brbe set pagesound minecraft:wooden_door` → 聊天栏"翻页音效已设为
minecraft:wooden_door"并听到开门声；② 滚轮翻页 / RBIP 标签栏箭头 / 查询窗口翻页 → 都是新音效；
③ 配置界面「快捷键&数值」页：动画时长下方出现「翻页音效」文本框（无 tooltip），改成别的 ID 保存后翻页生效；
④ 填一个不存在的 ID（如 `foo:bar`）→ 自动回退默认"哒"声，不静音；⑤ `/brbe set pagesound` 参数处按
Tab 补全出声音 ID 列表；⑥ `/brbe clear configchange` → 该项回默认 `minecraft:ui.button.click`。

**2026-09-25（四）：指令补全条目「点击试听」翻页音效（四分支同步）**

用户需求：在聊天栏输入 `/brbe set pagesound …` 时，**点补全预选框里的任一条目就能立刻听到那个声音**
（不必回车执行指令），方便边翻列表边挑音效。

**可行性结论：可以做**，挂点是 vanilla 客户端补全列表 `CommandSuggestions$SuggestionsList.mouseClicked`。
四个版本的该类结构一致（javap 核实）：`private final String originalContents`（点击**前**的输入文本）、
`private final List<Suggestion> suggestionList`、`private int current`（当前选中索引）；
`mouseClicked` 流程 = 命中判定 → `select(索引)` → `useSuggestion()` → 返回 true
（26.2/26.3/1.21.11 为 `mouseClicked(II)Z`，1.21.1 为 `mouseClicked(III)Z`；外层
`CommandSuggestions.mouseClicked` 都转调它）。

**实现**：新增 `mixins/command/SuggestionsListMixin`（注册进各分支 `mixins.brbe-common.json`
的 **`client` 数组**——该配置只有 `client` 段，天然只在客户端应用）：

```
@Mixin(CommandSuggestions.SuggestionsList.class)
@Shadow @Final private String originalContents;   // 点击前的输入
@Shadow @Final private List<Suggestion> suggestionList;
@Shadow private int current;
@Inject(method = "mouseClicked", at = @At("RETURN"))  // 1.21.1 多一个 button 参数
    if (!cir.getReturnValueZ()) return;              // 点在列表外 → 跳过
    PageFlipSound.previewSuggestion(originalContents, suggestionList.get(current).getText());
```

**三处设计取舍（都有缘由）**：
1. **挂 RETURN 而不是 HEAD**：返回 true 才算点中条目，**不用自己重算命中区**
   （vanilla 用 `(mouseY - rect.y) / 12 + offset`）；且此刻 `current` 已是被点条目、
   `originalContents` 仍是点击前的文本——正好用来判断"是否停在我们指令的参数位"。
2. **不碰外层 `CommandSuggestions` 的合成字段**：`this$0`（26.x）/`field_21615`（remap 分支）
   是编译器合成字段、没有映射名，跨分支写法不通用（历史上 `OverlayRecipeButtonAccessor`
   就在这上面踩过坑）。改用 `originalContents` 判据，四个版本同一个写法。
3. **`@Shadow @Final`**：目标两个字段是 final（`RecipeBookPage.buttons` 同款，仓库既有写法），
   漏 `@Final` 会在运行时校验报错。

**判据集中在指令树**：`BrbeCommandTree.isPageSoundArgumentInput(String)`
（`^\s*/\s*brbe\s+set\s+pagesound(\s|$)`，忽略大小写）——指令字面量在哪定义、判据就在哪，
把"我们的指令"与其它也用声音 ID 的指令（如 `/playsound`）区分开。

**试听路径独立于自动翻页声**：`PageFlipSound` 新增
- `play(Minecraft, SoundEvent, float)`：底层播放（pitch 1.0）；
- `previewVolume()` = `0.25 ×「音效音量」`；
- `playPreview(String id)` / `playConfiguredPreview()`：**不受「鼠标滚轮翻页音效」开关影响**
  （那个开关管的是自动翻页声；试听是显式动作，关掉开关也该听得到），只受「音效音量」控制（0 = 静音）；
- **试听实例去重**：记住上一次试听的 `SoundInstance`，点下一条目前先 `SoundManager.stop(...)`
  ——连续点选不会把长音效叠起来（挑音乐唱片类长音时尤其明显）。
`ClientCompat.playPageFlipSound`（自动翻页路径）与 `BrbeCommandActions.setPageFlipSound`
（执行指令后的试听）都改为复用这套底层播放。

**范围说明**：只有**鼠标点击**补全条目会试听——键盘 Tab 轮循（vanilla 每按一次就应用下一条建议）
不试听，避免快速轮循时连续叠音。需要的话可以照做（挂在 `useSuggestion` 上即可覆盖键盘路径）。

**构建/部署**（原子替换，备份 `20260925-135400`）：26.2-Fabric `1b3113be5162ff67a336423962214638`
（部署两个 26.2 实例）、26.3-Fabric `11581582a5faacf67cbd28eeffeeb828`、
1.21.11-Fabric `2ee5a5605e15849828e3145ecdf45bbc`；1.21.1 按规则**只构建不部署**：
fabric `292b73219d472b700bb7a43fae5dd56b`、neoforge `22c8d373a29309788bc7f293265751eb`。

**产物核对（离线可验，未启动游戏）**：
- 三个 remap 分支的 jar 内 mixin 类字段已被 loom 重映射为 intermediary 名——
  `field_2768`/`field_25709`/`field_2766`；查 `mappings.tiny`
  （`CommandSuggestions$SuggestionsList` = `class_4717$class_464`）确认三者正是
  `originalContents`/`suggestionList`/`current`（描述符依次 `Ljava/lang/String;`/
  `Ljava/util/List;`/`I`，逐一对应）→ 影子字段解析正确；
- 处理签名按分支区分：26.2/26.3/1.21.11 = `(int,int,CallbackInfoReturnable)`、
  1.21.1 = `(int,int,int,CallbackInfoReturnable)`（对应各版本 `mouseClicked` 的参数个数）；
- mixin 类内 `lambda$` 计数 = 0（仓库规则：mixin 里不写 lambda）。

**验证方法**：聊天栏输入 `/brbe set pagesound `（或 `… bamboo`）→ 点补全列表里的条目
→ **立即听到该声音**（输入框同时填入该 ID，按回车才真正写入配置）；
点列表外/点非声音条目（如字面量建议）不发声；`/brbe set pagesound minecraft:wooden_door` 回车
→ 开门声 + 聊天栏"翻页音效已设为 …"；把「音效音量」拉到 0 → 试听与翻页都静音。

**2026-09-25（五）：前端音效四处修正（查窗标题/标签 = 按钮音、配方点击不再叠音、配方书翻页箭头 = 翻页音效，四分支同步）**

用户反馈四条（均在 26.2 实测）：① 点 LEI 查询窗口的**标题条**（＝浏览全部）播的是翻页音效；
② 查询窗口里**点配方按钮**的声音比其他按钮响一档；③ 查询窗口**底部类别标签**点击播的是翻页音效；
④ **配方书的翻页箭头**点击播的是原版音效，应当是翻页音效。

**①③ 语义归位：普通按钮 → 原版点击音**。新增 `ClientCompat.playButtonClickSound()`（音量 0.25，
与 vanilla `AbstractWidget.playButtonClickSound` 同音源同音量；1.21.1 无该静态方法，直接按同参数构造），
把"非翻页"的按钮反馈从 `playPageFlipSound` 换过去：
- 26.2/26.3/1.21.11：`RecipeViewerOverlay.handleWindowReleased()`（标题条＝浏览全部）；
  `handleCategoryTabClick()`（切换类别）；
- 1.21.1：`handleCategoryTabClick()` 的两处（切类别 / 点已选标签＝浏览全部）。
**保持不变**：滚轮翻页、窗口内翻页箭头、工作站列滑动、标签条滚动翻页（这些确实是"翻页"）。

**② 配方点击"响两遍"根因**：查询窗口的配方网格是 vanilla `OverlayRecipeComponent` 的
**真 widget** —— `ViewerInstance.mouseClicked` 先 `overlay.mouseClicked(...)`，其
`OverlayRecipeButton`（extends `AbstractWidget`，未覆写 `mouseClicked`）在
`AbstractWidget.mouseClicked` 里已经 `playDownSound` 响过一声（0.25）；BRBE 随后在
`placeRecipe` 里又补一声 → 同一声叠两遍 ≈ 响一档（反编译核实：`AbstractWidget.mouseClicked`
→ `playDownSound` → `playButtonClickSound` → `forUI(sound, 1.0f)` → `forUI(sound, 1.0f, 0.25f)`，
即 vanilla 按钮音量本来就是 0.25，BRBE 并非音量写错）。
修复：`placeRecipe` 增加 `playClickSound` 开关——**查询网格点击传 false**（widget 已响），
**Shift 预览弹窗点击传 true**（弹窗是 BRBE 自绘，点击不经过任何 widget），
pin 浮层走 4 参重载（默认 true，行为不变）。

**④ 配方书翻页箭头改播翻页音效**：
- 新增 `util/PageTurnArrows`（`WeakHashMap` 登记表）+ `mixins/scrollablepages/PageTurnArrowSoundMixin`
  （`@Mixin(AbstractWidget.class)`，`@Inject(method="playDownSound", HEAD, cancellable)`）：
  **登记过的箭头**按下时取消原版点击声，改播 `ClientCompat.playPageFlipSound`（配置音效 + 音效音量）。
  挂 `playDownSound` 是唯一能一次覆盖全部箭头点击路径的点（原版 `RecipeBookPage.mouseClicked`、
  BRBE 的 `scrollAround` HEAD 拦截、Ctrl+跳页、BRBE 自研书写法）。
- 登记点：`scrollablepages/RecipeBookPageMixin` 的 `updateArrowButtons` RETURN（26.2/26.3/1.21.11）
  与 `updateButtonsForPage` RETURN（1.21.1，该分支没有 updateArrowButtons）；
  `generic/GenericRecipePage` 构造器里箭头创建之后（四分支）。
  附带收益：**酿造台/锻造台配方书**的箭头原先也被 `flipTo` 播一次翻页音效、再被 widget 播一次
  原版点击声（同样叠音），登记后只剩翻页音效。
- 注册：`mixins.brbe-common.json` 的 client 列表（四分支）。

**JAR 校验（离线，未启动游戏）**：四个 jar 内 `util/PageTurnArrows.class` 与
`mixins/scrollablepages/PageTurnArrowSoundMixin.class` 在、mixin 配置里已注册；
`@Inject` 的 `method` 值在 1.21.11/1.21.1 已被 loom 重映射为 **`method_25354`**（26.2 保持
`playDownSound`）→ 运行时能解析；`RecipeBookPageMixin` 内含 `PageTurnArrows.register` 调用；
查看器字节码：`ViewerInstance` 内 `ClientCompat.playButtonClickSound` 3 处（placeRecipe/标签/标题）、
`playPageFlipSound` 5 处（滚轮/两箭头/工作站列/标签滚动）、两处 5 参 `placeRecipe` 调用分别压
`iconst_1`（弹窗）/`iconst_0`（网格）。

**构建/部署**（原子替换，备份 `20260925-141339`）：26.2-Fabric `cbaebf0a1c145d687dfe767271f816d0`
（部署两个 26.2 实例）、26.3-Fabric `932fd284818dd54cb611c16c0ad214b1`、
1.21.11-Fabric `f641f90f36acdbb712b3d9a10733a9e2`；1.21.1 按规则**只构建不部署**：
fabric `b524bf923352bf1177e504845750fb57`、neoforge `3609d19c6cb03089c44dc33226b71d86`。

**验证方法**：① 点查询窗口标题条 → 普通"哒"（不是翻页音效）；② 点底部类别标签 → 普通"哒"；
③ 点查询窗口里的配方 → 与其它按钮同响度（不再响一档）；Shift 预览弹窗内点击 → 同样响一声；
④ 配方书 `<`/`>` 翻页箭头 → 播放配置的翻页音效（`/brbe set pagesound` 换一个 ID 即可验证）；
酿造台/锻造台配方书的箭头同理；⑤ 滚轮翻页、窗口内翻页箭头、标签条滚动仍为翻页音效。

## 2026-09-25：配方书**悬停即预览**（幽灵物品随指针，四分支同步）

**用户诉求**："当用户将鼠标悬停在配方书的配方上时，直接在对应功能方块的工作区展示幽灵
物品（相当于点击配方所展示的幽灵物品），当鼠标移开后立马消失。对于展开的替代配方组，
悬停在组内各配方上时不再展示任何界面，也像其他配方在工作区展示幽灵物品。对于未展开的
替代配方组，则展示当前轮循到的物品的幽灵物品。还有一个重点，对于可合成物品也要展示
幽灵物品。"

**核心机制（`util/HoverGhostRecipe.java`，新增）**：写入走**原版自己的幽灵填充方法**
（26.x/1.21.11 = `RecipeBookComponent.fillGhostRecipe(RecipeDisplay)`；1.21.1 =
`setupGhostRecipe(RecipeHolder, List<Slot>)`，槽位列表用 `player.containerMenu.slots`
——与 `ClientPacketListener.handlePlaceRecipe` 完全同参）。因此：

- 外观 / 红罩 / 幽灵轮循 / 逐物品折叠锁（`GhostSlotsCycleLockMixin`）全部自动与
  "点击后的缺料引导"一致，**不需要服务端往返**（点击路径之所以要点一下才出幽灵，
  正是因为幽灵来自 `ClientboundPlaceGhostRecipePacket` 回包）；
- **可合成配方同样写入**（不查 `isCraftable`）——合成网格里幽灵叠在已有材料上，这正是
  用户"实际效果不用担心"的那部分。

两个状态标志解决与原版流程的冲突：

- **快照**：悬停前可能已经有幽灵（例如刚点过另一个配方）。悬停只是**预览层**，移开时
  还原进入前的状态（26.x/1.21.11 复制 `GhostSlots.ingredients` 条目；1.21.1 复制
  `GhostRecipe` 的 `GhostIngredient` 列表 + recipe，直接改列表以免 `clear()` 把轮循
  计时归零）；
- **接管（overridden）**：点击配方（`tryPlaceRecipe` / 1.21.1 `mouseClicked` 里的
  `ghostRecipe.clear()` 重定向）或服务端回包（`fillGhostRecipe` / `setupGhostRecipe`
  注入，自己的写入带 `selfFill` 标志区分）之后，幽灵所有权交回原版 →
  `release()` **绝不再用旧快照覆盖**（否则玩家点完配方把鼠标移向工作区时，点击留下的
  缺料引导会被抹掉）。

**命中来源与四个场景**：

| 场景 | 命中源 | 配方 |
|---|---|---|
| 配方书页按钮 | `RecipeBookPage.extractRenderState`/`render` RETURN 注入，复用原版逐帧算好的 `hoveredButton`（天然兼容翻页动画期间的视觉命中覆盖） | `getCurrentRecipe()` = **当前轮循到**的那条 → 未展开的替代配方组显示当前变体 |
| 展开的替代配方组浮层 | `OverlayRecipeComponent.extractRenderState`/`render` RETURN 注入，遍历 `recipeButtons` 找 `isHoveredOrFocused()` | 该按钮的**具体变体** id |
| 浮层打开时的页按钮 | 页注入在 `overlay.isVisible()` 时整体让位（浮层按钮才是命中目标） | — |
| 指针被 LEI 查询窗口/预览弹窗/pin 挡住 | `RecipeViewerOverlay.modalMaskOwnsCursor(mouseX, mouseY)` → 释放（与 `CycleLock.claimScreen` 同口径） | — |

**替代配方组浮层的悬停界面已移除**（`alternativerecipes/OverlayRecipeButtonMixin`）：
悬停不再 `PopupRenderer.renderRecipePopup(..., 2f)` 放大预览，只保留 `renderBaseButton`
的普通 hover 高亮 + 工作区幽灵物品（1.21.1 的浮层本就没有该弹窗，无需改动）。

**酿造台/锻造台配方书（BRBE 自研书）**：`GenericRecipeBookComponent` 在 `recipesPage.render`
之后调 `brbe$updateHoverGhost()`——只有"光标下的配方换了"才 `ghostRecipe.clear()` +
`setupHoverGhost(R)`（子类实现 = 点击路径的 `setupGhostRecipe(result, menu.slots)`）；
点击放置后同一按钮上的悬停不再插手，缺料引导保留。

**残缺配方红罩判定修正（26.2/26.3/1.21.11）**：`PartialGhostOverlayUtil.prepare` 旧签名
要求 `lastRecipe`/`lastRecipeCollection` 非空（**两者只为判空存在，函数体从不使用**），
而悬停预览的幽灵是客户端直接写的、原版这两个字段仍为空 → 遮罩退化成"全部缺料"
（悬停可合成配方 = 整格强红）。改为 `prepare(menuSlots, carried, ghostSlots)`：判定只看
即将绘制的幽灵内容本身。（1.21.1 无此问题——`setupGhostRecipe` 会把 recipe 写进
`GhostRecipe`，其 `brbe$findGhostRecipeCollection` 能找到集合。）

**延迟 1 帧（已知且不可见）**：`AbstractRecipeBookScreen` 的绘制顺序是
`extractSlots`（画幽灵）→ `recipeBookComponent.extractRenderState`（画书页 = 我们的
命中更新），所以幽灵内容比指针晚一帧生效（≈16ms），红罩与幽灵同帧一致。

**新增文件**：`util/HoverGhostRecipe.java`、`mixins/hoverghost/{RecipeBookComponentMixin,
RecipeBookPageMixin, OverlayRecipeComponentMixin}.java`（四分支同名），注册进各分支
`mixins.brbe-common.json` 的 `client` 数组尾部（保证同注入点上晚于既有 mixin 执行）。
**accessor 增补**：`RecipeBookPageAccessor`（+`hoveredButton`/`parent`，1.21.1 只需已有的
`hoveredButton`）、`RecipeButtonAccessor`（+`selectedEntries`，用于挡掉原版
`getCurrentRecipe()` 的空列表 /0；1.21.1 用已有的 `getOrderedRecipes`+`currentIndex` 自己取模）。

**分支差异**：26.2/26.3 用 `GuiGraphicsExtractor`；1.21.11 用 `GuiGraphics` +
`mc.screen`（字段）+ `RecipeBookPage.render` / `OverlayRecipeComponent.render`；
1.21.1 用 `GuiGraphics` + `RecipeUpdateListener.getRecipeBookComponent()`（`RecipeBookPage`
**没有** `parent` 字段）+ `GhostRecipe` 旧结构 + `mouseClicked` 的 `GhostRecipe.clear()`
`@Redirect`（该分支没有 `tryPlaceRecipe` 方法）。

**验证方法（未启动游戏，运行时待用户实测）**：① 悬停配方书任一配方 → 工作区立刻出现
幽灵物品（可合成配方同样出现）；移开 → 立刻消失；② 悬停多配方组按钮 → 幽灵随图标轮循
换变体；③ 右键展开替代配方组 → 悬停组内各变体：**不弹任何放大预览**，只有工作区幽灵，
且是"该变体"的；④ 先点一个配方（出现缺料引导）再把鼠标移开 → 引导**不被清掉**；
⑤ 酿造台/锻造台配方书悬停 → 药水槽/锻造槽出现幽灵；⑥ 查询窗口浮在配方书上时，悬停
窗口不会触发背后的配方书幽灵；⑦ 收起配方书（ESC）→ 幽灵不残留。

**JAR 校验（离线）**：四个 jar 内 `hoverghost/*.class` + `util/HoverGhostRecipe.class` 在、
mixin 配置已注册；mixin 注解的 `method`/`target` 已被 loom 重映射为 intermediary
（26.2 无需重映射）——1.21.11 `fillGhostRecipe` → `method_64875(Lnet/minecraft/class_10295;)V`、
`tryPlaceRecipe` → `method_62889`、`setVisible` → `method_2593`；1.21.1 `mouseClicked` →
`method_25402`、`GhostRecipe.clear()` → `Lnet/minecraft/class_505;method_2571()V`；
accessor 值 `hoveredButton`/`parent`/`selectedEntries` 均在；mixin 类内**无 `lambda$`**。

**构建/部署**（原子替换）：26.2-Fabric `48f642c8e8b885fc493881de2f81c3e4`（部署两个 26.2
实例）、26.3-Fabric `9986f6b100e9dfca4df6a67383f99fda`、1.21.11-Fabric
`d53718f2f3f488c7ddb392e9a4004aab`；1.21.1 按规则**只构建不部署**：fabric
`cfbd6703342642f91ebf2952389f1d01`、neoforge `9cc23a920f08548f9d5860fa1747607f`。
备份 tag `20260925-164500`（中间一轮 `20260925-161907`/`20260925-163000`）。
**提交**：`730d0b33`（26.3 分支）。

## 2026-09-25（二）：进游戏闪退修复（`CallbackInfoReturnable`）+ 新增 mixin 离线自检工具

**用户反馈**："打开工作台的时候游戏完全崩溃"（26.3-Fabric 实例，崩溃包已提供）。

**根因（我的上一轮引入）**：`hoverghost/RecipeBookComponentMixin.brbe$notePlacedRecipe` 注入
`RecipeBookComponent.tryPlaceRecipe`——该目标**有返回值**（`boolean`），而处理器末参写成了
`CallbackInfo`。Mixin 在类变换期直接抛：

```
InvalidInjectionException: Invalid descriptor on ... brbe$notePlacedRecipe(...CallbackInfo;)V!
CallbackInfoReturnable is required!
```

发生在 `MixinProcessor.applyMixins` → `MenuScreens.<clinit>` 构造 `CraftingScreen` →
`RecipeBookComponent` 变换失败 → **只要打开任何带配方书的界面（工作台/背包/熔炉…）就崩**。
`compileJava` / `build` 全绿（Mixin AP 不做完整校验），所以上轮离线自查没发现。
（同一坑 2026-09-13 已记录过一次：有返回值的目标必须用 `CallbackInfoReturnable`。）

**修复**：处理器改 `CallbackInfoReturnable<Boolean> cir`（26.2 / 26.3 / 1.21.11）。
1.21.1 不受影响——该分支没有 `tryPlaceRecipe`，点击路径用的是 `mouseClicked` 里
`GhostRecipe.clear()` 的 `@Redirect`（无回调参数）。

**新增工程资产：`tools/mixin-check/check.py`（离线 mixin 自检）**。这类错误发生在类变换期、
只在实际进游戏时才炸，因此在**不启动游戏**的前提下把已构建 jar 里的 mixin 注解与目标类逐个对照：

1. `@Inject(method=…)` → 目标方法必须存在（支持 `<init>`）；处理器参数里的回调类型必须与目标
   返回值匹配（void ⇔ `CallbackInfo`，有返回值 ⇔ `CallbackInfoReturnable`）；
2. `@Redirect/ModifyArg/ModifyVariable` → 目标成员必须存在（**含父类与接口链**），且该成员必须在
   被注入的方法体里真的被调用（否则运行时找不到注入点）；
3. `@Accessor`/`@Invoker` → 目标字段/方法必须存在。

实现要点：`javap -v` 解析注解 + `javap -p/-c` 解析目标类成员与字节码；JDK 类自动回退
（去掉 `-classpath` 再解析）；嵌套类 `$`↔`.` 归一化；泛型/通配符擦除。
**用真实的崩过的 jar 反向验证过**：`--jar <修复前的 jar>` 只报出那一条
`tryPlaceRecipe … 必须用 CallbackInfoReturnable`，零误报。

**用法**：`python3 tools/mixin-check/check.py --branch 26.2 --branch 26.3 --branch 1.21.11 --branch 1.21.1`

**顺带修掉的一个真实问题（工具发现）**：1.21.11 的 `mixins.brbe-common.json` 里
`recipebook.RecipeBookToggleFocusMixin` 是**悬挂注册**（该类从未移植到 1.21.11，jar 内无此 class）
——Mixin 每次启动都会 `Unable to register … the specified class was not found` 并跳过。
本轮把 26.2 的该 mixin（配方书切换按钮点击后不清键盘焦点的原版缺陷修复）**原样移植到 1.21.11**
（`AbstractRecipeBookScreen.mouseClicked(MouseButtonEvent, boolean)` 签名一致），悬挂注册随之消除。

**构建/部署**（原子替换，备份 `20260925-165500` / `20260925-170000`）：26.2-Fabric
`e128ece6e1dfda5993d78fd8259c2b97`（两个 26.2 实例）、26.3-Fabric
`351329792c7cb739e75dba07f93c55a9`、1.21.11-Fabric `c8fc666659c890ed5bf196972683b930`；
1.21.1 无改动（代码未变、无悬挂注册）。**四分支 `tools/mixin-check` 现全部 `[OK]`**。

## 2026-09-25（三）：替代配方组按钮的纹理与内容对齐（悬停=配方预览，四分支同步）

**用户诉求**：开启「仅在悬停时显示替代配方」时，悬停替代配方应显示**配方预览界面**
（不再用之前的放大界面），并使用 `minecraft:recipe_book/crafting_overlay_highlighted` 与
`..._disabled_highlighted`（分别对应可合成/残缺 与 不可合成）；**未悬停**纹理用
`brbe:recipe_book/crafting_overlay(_disabled)`；**关闭**该配置时未悬停纹理改用原版
`minecraft:recipe_book/crafting_overlay(_disabled)`。

**上一轮的遗留缺陷（本轮一并修）**：`renderBaseButton` 传给内容渲染的 `hover` 恒为
`false`（那是查询界面"悬停不展开内容、只换高亮底板"的刻意设计），配方书替代配方组
复用它之后 → **悬停时内容仍是产物图标**（只是底板变高亮），即"悬停看不到任何配方预览"。
本轮为配方书替代配方组新增独立入口 `PopupRenderer.renderAlternativesButton(...)`：
内容侧传**真实 hover**，于是悬停 = 完整 3×3 配方布局 + 产物（1:1，不放大）。

**规则收口（单一真源）**：`PopupRenderer.revealsFullPreview(hover, lockReveal)
= hover || !(onHover || lockReveal)` —— 原先散落在 6 处（`renderSlotItems` /
`renderSynthetic` / `renderFixedPair` / `renderFurnace` / `renderGenericCrafting` 各自的
`(onHover || lockReveal) && !hover`）的同一判据全部改调它；**纹理选择与内容展开共用这一个
布尔量**，两者不会再脱节：

| 状态 | 内容 | 底板纹理 |
|---|---|---|
| 悬停 | 完整配方预览（3×3 + 产物，1:1） | 原版 `crafting_overlay_highlighted`（残缺/可合成）/ `crafting_overlay_disabled_highlighted`（不可合成） |
| 未悬停 + 配置开 | 只画产物图标 | BRBE `crafting_overlay` / `crafting_overlay_disabled` |
| 未悬停 + 配置关 | 完整配方预览 | 原版 `crafting_overlay` / `crafting_overlay_disabled` |

（用户只列了后两行的"未悬停"与第一行的"悬停"；**配置关 + 悬停**按同一原则 = 原版高亮面。）

**熔炉系（熔炉/鼓风炉/烟熏炉）**：原版没有 smithing_overlay 面，但有 `furnace_overlay`
系列 → 熔炉系按同一原则处理：完整预览用原版 `furnace_overlay(_disabled)(_highlighted)`、
产物图标态仍用 BRBE `plain_overlay(_disabled)`（这是**我的推断**，用户只点名了 crafting
系列；不喜欢的话把 `renderAlternativesButton` 里 `VANILLA_FURNACE_OVERLAY_SPRITE` 换回
`RECIPE_BOOK_PLAIN_OVERLAY_SPRITE` 一行即可）。锻造台自研浮层
（`SmithingOverlayRecipeComponent`，原版无对应面）保持原状。

**新增纹理常量**（`BRBTextures`）：`VANILLA_CRAFTING_OVERLAY_SPRITE` /
`VANILLA_FURNACE_OVERLAY_SPRITE`（`minecraft:recipe_book/crafting_overlay*` /
`furnace_overlay*` 四态）。

**分支差异**：26.2/26.3/1.21.11 走 `PopupRenderer`（前者 `GuiGraphicsExtractor`、后者
`GuiGraphics`）；1.21.1 的替代配方按钮是自研渲染（`alternativerecipes/OverlayRecipeButtonMixin`
内联），按同一规则就地改（`fullPreview = hovered || !onHover`，同样驱动底板与内容）。
查询界面按钮（`renderBaseButton`）**行为不变**：仍"悬停只换 BRBE plain 高亮面、内容恒为
产物图标"，完整界面由 Shift 弹窗给。

**验证方法**：配方书里右键展开替代配方组 →① 开启「仅在悬停时显示替代配方」：未悬停 =
BRBE 纹理 + 只显示产物；悬停 = 原版高亮纹理 + 完整配方（3×3 材料 + 产物，不放大）；
② 关闭该配置：未悬停 = 原版普通纹理 + 完整配方；悬停 = 原版高亮纹理。

**构建/部署**（原子替换，备份 `20260925-182010`）：26.2-Fabric `e7fc8d873d4ffae0e3ed41011efd6c73`
（两个 26.2 实例）、26.3-Fabric `3e90ae8aa61af3f9a13fdd7f328e0f14`、1.21.11-Fabric
`1d437c4d4cdd47817bb0ccbeb0efa33f`；1.21.1 只构建不部署：fabric
`b40cc6731374b4ca7c0eaa9b0638ae68`、neoforge `68b3d8261e5179b409837630ebc33876`。
四分支 `tools/mixin-check` 全 `[OK]`；jar 字节码核对：mixin 内 `renderAlternativesButton`
与 `renderBaseButton` 各 1 处调用（配方书 / 查询界面），`BRBTextures` 常量池含 crafting/furnace
两套原版面 + BRBE plain/crafting 面。

## 2026-09-25（四）：新配置「自动填充幽灵配方」（悬停幽灵预览总开关，四分支同步）

用户需求：在「拼音搜索」**上方**新增一个布尔配置项，标题「自动填充幽灵配方」，
tooltip「当鼠标指向配方书中的某个配方时，自动填充其幽灵配方，移开则消失。」，**默认开**；
true = 启用「鼠标悬停配方临时展示幽灵物品」（2026-09-25 落地的悬停预览功能），false = 禁用。

**配置字段**：`BrbeConfig.autoFillGhostRecipe = true`（`@ConfigEntry.Gui.Tooltip`，**声明在
`pinyinSearch` 之前**）。Cloth/AutoConfig 按**字段声明顺序**生成类别条目，故它出现在
「拼音搜索」上方（「功能」类别页首）；非中文语言下拼音项被 `PinyinSearchGuiRegistrar`
整个隐藏，本项仍照常显示在页首。

**唯一判定入口**：`HoverGhostRecipe.enabled()`（`BetterRecipeBook.config != null &&
config.autoFillGhostRecipe`）——合成台与酿造/锻造台**共用同一个谓词**：
- **合成台**（原版配方书页按钮 + 展开的替代配方组浮层）：`hoverghost/RecipeBookPageMixin`
  与 `hoverghost/OverlayRecipeComponentMixin` 逐帧调 `HoverGhostRecipe.hover(...)`，
  在本方法**第一行**判定：关闭时先 `release()` 再返回——既不写幽灵，也会**立刻撤下**
  已显示的预览；`release()` 依旧尊重 `overridden` 语义，点击放置留下的缺料引导不受影响。
- **酿造台/锻造台**（BRBE 自研配方书）：`GenericRecipeBookComponent.brbe$updateHoverGhost()`
  开头判定：关闭时若 `brbe$hoverGhostRecipe != null` 则 `ghostRecipe.clear()` 并置空
  （只撤我们写的那一份，原版点击放置的幽灵不动），随后 `return`。

**为什么不把守卫写进 mixin**：`HoverGhostRecipe` 是普通工具类（非 mixin），三个 hoverghost
mixin 与两个组件都调它，判定集中一处就够；配置是**逐帧读取**的，所以配置界面里改完
**不必重开界面**即刻生效。

**语言**：7 语言各 +3 键（`text.autoconfig.brbe.option.autoFillGhostRecipe` +
`.@Tooltip` + `@Tooltip` 双写，与既有键同格式），按字母序插在
`alternativeRecipes@PrefixText` 与 `enableBook` 之间：
zh_cn「自动填充幽灵配方」/「当鼠标指向配方书中的某个配方时，自动填充其幽灵配方，移开则消失。」·
zh_tw「自動填充幽靈配方」· en_us "Auto-fill Ghost Recipe" · ja_jp「ゴーストレシピの自動入力」·
pl_pl "Automatyczne wypełnianie upiornej receptury" · ru_ru "Автозаполнение рецепта-призрака" ·
tr_tr "Hayalet Tarifi Otomatik Doldur"。

**构建/部署**（原子替换，备份 `20260925-184527`）：26.2-Fabric `087e77cd38f5e3e9d3944988851c0f46`
（两个 26.2 实例）、26.3-Fabric `423553921a3fd0a753031231da392448`、1.21.11-Fabric
`225b583a82b284c046af5d79e655ffa0`；1.21.1 只构建不部署：fabric
`5f85a2c22d55cce9b5c395e3c02b5996`、neoforge `dd2672e4f905b9cfdc98e43eb3b52821`。
四分支 `tools/mixin-check` 全 `[OK]`（本轮未改 mixin，走例行核对）。

**字节码核对**（`javap -p -c`，各分支产物一致）：`BrbeConfig.<init>` 内
`iconst_1 → putfield autoFillGhostRecipe`（默认 **true**）；`HoverGhostRecipe.enabled()` =
`getstatic BetterRecipeBook.config / ifnull / getstatic config / getfield autoFillGhostRecipe:Z`；
`HoverGhostRecipe.hover` 首指令 `invokestatic enabled():Z / ifne / invokestatic release():V / return`；
`GenericRecipeBookComponent.brbe$updateHoverGhost` 第三段 `invokestatic HoverGhostRecipe.enabled():Z`。
jar 内 zh_cn 键值 = 自动填充幽灵配方。

**验证方法**：配置界面（中文）→「功能」页最上方（「拼音搜索」之上）出现「自动填充幽灵配方」，
默认**开**；悬停配方书任意配方 → 工作区出现幽灵物品、移开消失；关掉它 → 悬停不再有任何幽灵
（**点击配方放置的幽灵与缺料引导不受影响**），且无需重开界面即时生效；
酿造台 / 锻造台配方书同样受该开关控制。

（本分支取值点 = `recipesPage.hoveredRecipe`。）

## 2026-09-25（五）：折叠**幽灵物品**的锁定 + 滚轮翻动（四分支同步）

用户需求：「我之前不久给「锁定」（默认 Alt 键）做了泛用性的定义及实现，现在我希望添加其对
折叠幽灵物品的支持，也就是用 Alt 锁定正在轮循的幽灵物品，且使用滚轮翻动物品。」

盘点后是**两处真实断点**（不是"幽灵物品没纳入锁定设计"，而是它在这个状态下压根没生效）：

### ① 原版配方书：书体收起后没人消费滚轮（"锁得住、翻不动"）

- 幽灵物品的**绘制**与原版书体的可见性无关：`AbstractRecipeBookScreen.extractSlots`
  （1.21.11 是 `renderSlots`）**无条件**调用 `extractGhostRecipe`/`renderGhostRecipe`
  → `GhostSlots.extractRenderState`（1.21.1 是 `GhostRecipe.render`）**每帧都跑**，
  所以"指针停在幽灵物品上按 Alt"能 claim、能 latch（锁得住）。
- 但**消费排队滚轮**的那条分支写在配方书**页**的绘制里
  （`scrollablepages/RecipeBookPageMixin`），而页只在**书体可见**时绘制
  （`RecipeBookComponent.extractRenderState|render` 开头 `if (!isVisible()) return;`）。
- **而原版点击配方就会把书体收起**：`RecipeBookComponent.mouseClicked` 里
  `tryPlaceRecipe` 返回 false（材料不全 → 只写幽灵物品）之后
  `if (!isOffsetNextToMainGUI()) setVisible(false);` —— 工作台/熔炉这类整屏容器恒为 true
  （只有背包 2×2 那种贴边界面才 `isOffsetNextToMainGUI()`）。也就是说
  **"合成格里留着幽灵物品"恰恰就是书体收起的状态**，滚轮翻动在它最常见的状态下无人消费。
- **修复**：新增 `CycleLock.consumeQueuedScroll()`（锁定键 + 排队滚轮 → `step` 指针下那一件，
  成功则清队列），**配方书页与幽灵物品绘制路径共用它**，谁先跑到谁消费（队列清空后另一个
  自然不再重复步进）。幽灵侧挂钩：
  - 26.2 / 26.3：`cyclelock/GhostSlotsCycleLockMixin` 新增
    `@Inject(method = "extractRenderState", at = @At("RETURN"))`；
  - 1.21.11：同一 mixin，`method = "render"`（remap 后 `method_62033`）；
  - 1.21.1：`cyclelock/GhostRecipeCycleLockMixin` 新增 `@Inject(method = "render", at = @At("RETURN"))`
    （`method_2567`；与既有 HEAD「记录渲染原点」同方法、互不干扰）。
  - 与 `claimScreen` 同口径：指针被 LEI 查询窗口 / pin / 预览挡住时不插手（那些浮层的折叠
    槽位由它们自己的滚轮分发器步进，且不往 `queuedScroll` 排队）。
  - 挂在 RETURN（本帧幽灵 claim **之后**）比页上那条更"新鲜"：把指针移上去后**第一次**滚轮
    就能翻动，不用等下一帧。

### ② BRBE 自研配方书（酿造台 / 锻造台）：幽灵物品根本没接锁定

- 这两本书的幽灵是 BRBE 自己的 `GenericGhostRecipe`（变体由它自己的 `time` 驱动：
  `items[floor(time / 30) % n]`），**不经过**原版 `SlotSelectTime` → `CycleLockSlotSelectTime`
  那套对它无效；页面滚轮也只有翻页一条路（而且 `GenericRecipePage.render` 会把
  `queuedScroll` **无条件清零**）。
- 实测哪本书真的有折叠幽灵：**锻造台**——`SmithingRecipeBookComponent.setupGhostRecipe` 用
  `addIngredient(slot, Ingredient, x, y)` 加**附加材料 / 模板**（整套 Ingredient，多候选择 →
  轮循）；酿造台的原料槽走 `ClientCompat.firstIngredientItem(...)`（单候选，本就不是折叠物品
  → `getDisplayStack` 直接跳过，不会占着"指针下的物品"害得旁边的折叠物品翻不动）。
- **修复**（`generic/GenericGhostRecipe`）：
  - 新增 `GenericGhostIngredient.getDisplayStack(screenX, screenY)`：单变体原样返回；多候选时
    屏幕矩形 = 渲染原点 + 该槽位相对坐标（16×16）→ `CycleLock.claimScreen` +
    `indexFor(key = 该 ingredient 实例, auto = floor(time/30) 取模)`；指针离开 `release`。
    `render` 与 `drawTooltip` 都改用它——**锁定期间鼠标提示与画出来的那一件一致**。
  - `GenericRecipePage.render`：翻页前先 `CycleLock.consumeQueuedScroll()`
    （`!consume && 鼠标在配方区 && totalPages > 1` 才翻页），其余行为不变。

### 统一入口

`CycleLock` 新增 `consumeQueuedScroll()`（26.x / 1.21.11 读 `BetterRecipeBook.queuedScroll`；
1.21.1 走 `getQueuedScroll()/setQueuedScroll()`），配方书页那条内联分支改为调用它——
步进语义与 2026-09-13 那轮完全一致，只是多了一个"每帧都跑"的消费点。

**构建/部署**（原子替换，备份 `20260925-191824`）：26.2-Fabric `99e4081993def106a345d7a4300ec0b6`
（两个 26.2 实例）、26.3-Fabric `edcd779b35306881ecd1d50dbaac0c95`、1.21.11-Fabric
`28450f3a04488fd6e35dbbb3706dd7e8`；1.21.1 只构建不部署：fabric
`f724bd561bbf5143f89cd73409f859e9`、neoforge `531ef73231276124ad451af259579dbb`。
四分支 `tools/mixin-check` 全 `[OK]`（含新增的 @Inject 目标与回调类型）。

**字节码核对**（`javap -p -c/-v`）：
- `CycleLock.consumeQueuedScroll()` = `getstatic BetterRecipeBook.queuedScroll`（1.21.1 是
  `invokestatic getQueuedScroll`）→ `isDown` → `modalMaskOwnsCursor(cursorX, cursorY)` → `step`；
- 幽灵侧注入：26.2/26.3 `@Inject(method=["extractRenderState"], at=RETURN)`；1.21.11
  `method=["method_62033"]`；1.21.1 `method=["method_2567"]` HEAD（**既有**）+ RETURN（**新增**）；
  参数类型在 remap 分支是 `class_332`/`class_310`（GuiGraphics / Minecraft）；
- `GenericGhostRecipe` 里对 `getItem()` 的调用 **0** 处、`getDisplayStack` **2** 处
  （render + drawTooltip）；`GenericRecipePage` 调 `consumeQueuedScroll` **1** 处。

**验证方法**：
1. **工作台（原版书）**：点一个材料不全的配方 → 书体自动收起、合成格里留下幽灵物品 → 指针移到
   某个**折叠**幽灵物品上（多候选的槽，例如"任意木板"类）→ 按住 Alt：该槽停住、其余槽照常轮换；
   **Alt + 滚轮逐格翻动它**；移开指针恢复自动轮换。
2. **熔炉 / 背包**：同上（背包 2×2 界面书体不会自动收起，两种状态都该能翻）。
3. **锻造台（BRBE 自研书）**：点配方或悬停配方 → 附加材料 / 模板槽出现幽灵物品 → Alt 锁定 +
   滚轮翻动；锁定期间鼠标提示与显示的那一件一致。
4. 指针不在任何折叠物品上时：滚轮照常翻页（配方书）/ 不被吞掉。
