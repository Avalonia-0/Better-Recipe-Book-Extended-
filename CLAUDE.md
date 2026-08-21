# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Multi-branch architecture

Each git branch targets a **different Minecraft version** and is built independently:

| Branch    | Minecraft | Java | Loom                              | Mod Loaders      |
|-----------|-----------|------|-----------------------------------|------------------|
| `1.21.1`  | 1.21.1    | 21   | Architectury Loom                 | Fabric + NeoForge |
| `1.21.11` | 1.21.11   | 21   | Architectury Loom                 | Fabric + NeoForge |
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
- `src/main/java/mezz/jei/api/` —— vendored JEI API fork（默认 jar 内嵌，`fabric.mod.json` 声明 `breaks: jei`）
- `jeiJar` variant 排除 `mezz/**`、依赖真实 JEI（`depends: jei`）

```bash
./gradlew build        # 默认构建：内嵌 vendored mezz.jei.api
./gradlew jeiJar       # JEI 变体：排除 mezz/**，运行时依赖真实 JEI
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
- **JEI 已可用（vendored fork）**：默认 jar 内嵌 `mezz.jei.api` fork（`breaks: jei`）；`jeiJar` variant 用 `libs/jei-26.2-fabric-30.24.0.165.jar` 编译、运行时依赖真实 JEI（`depends: jei`）。**REI 仍不可用**（26.2 无 REI jar，`mixins.brbe-rei-common.json` 注册但无运行时实现）。
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
