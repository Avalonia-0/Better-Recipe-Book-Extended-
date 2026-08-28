# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Multi-branch architecture

Each git branch targets a **different Minecraft version** and is built independently:

| Branch    | Minecraft | Java | Loom                              | Mod Loaders      |
|-----------|-----------|------|-----------------------------------|------------------|
| `1.21.1`  | 1.21.1    | 21   | Architectury Loom                 | Fabric + NeoForge |
| `1.21.11` | 1.21.11   | 21   | fabric-loom-remap (`net.fabricmc.fabric-loom-remap` 1.14.6, 单模块) | Fabric |
| `26.2`    | 26.2      | 25   | fabric-loom (`net.fabricmc.fabric-loom`, no-remap) | Fabric |

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
./gradlew build                       # full build (single module)
./gradlew compileJava                 # compile-only check
./gradlew runClient                   # launch Fabric dev client
./gradlew clean build                 # full clean rebuild

# Cache corruption recovery (after branch switches)
./gradlew cleanLoomCache && rm -rf .gradle && ./gradlew build

# Deploy (build JAR → copy to test instance)
cp build/libs/brbe-ava-fabric-26.2-2.3-beta.3.jar /home/avalonia/data/MinecraftLib/versions/26.2-Fabric/mods/
```

Test instance path rule: `/home/avalonia/data/MinecraftLib/versions/{GAME_VERSION}-{MOD_LOADER}/mods/` (`MOD_LOADER` capitalized: `Fabric`). 构建完必须部署；部署前将实例内同版本 JAR 备份为 `*.jar.bak.YYYYMMDD`。

⚠️ **部署用原子替换，实例运行中也可安全部署**：先 `cp` 到 `mods/` 下的临时文件，再 `mv` 改名到目标（同目录 rename 是原子操作）——正在运行的会话其 zip 句柄指向旧 inode、内容完好，新启动的会话加载新 jar。**禁止 `cp` 直写覆盖目标 jar**：同 inode 截断重写会让正在运行的会话 zip 读取损坏（典型症状 `java.util.zip.ZipException: ZipFile invalid LOC header (bad signature)`，启动后运行途中随机崩溃——2026-08-25 20:47 部署时实例正开，20:35 启动的会话在 20:47 渲染时读 brbe jar 的类失败即此因）。

```bash
# 原子替换部署（实例运行中也安全）
cp build/libs/brbe-ava-fabric-26.2-*.jar /home/avalonia/data/MinecraftLib/versions/26.2-Fabric/mods/.brbe-deploy.tmp && mv /home/avalonia/data/MinecraftLib/versions/26.2-Fabric/mods/.brbe-deploy.tmp /home/avalonia/data/MinecraftLib/versions/26.2-Fabric/mods/brbe-ava-fabric-26.2-*.jar
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
- **JEI 已可用（vendored fork）**：主 jar 内嵌 `mezz.jei.api` fork（无 `breaks: jei`，与真实 JEI 共存），运行时若真实 JEI 存在则直接依赖它（只维护默认构建，jeiJar 变体已移除）。`libs/jei-26.2-fabric-30.24.0.165.jar` 仍作编译期依赖。**REI 仍不可用**（26.2 无 REI jar，`mixins.brbe-rei-common.json` 注册但无运行时实现）。
- **Cloth Config for 26.2 is bundled as a separate mod** (not jar-in-jar). The config registration is wrapped in try-catch; if Cloth Config is absent, the mod still runs with default values.
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
