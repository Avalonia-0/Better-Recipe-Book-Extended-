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
mod_version 2.3.1-beta.1 · 真实 JEI 参考版本 31.3.0.17。

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
| `alternativeRecipes.splitMode`（显示名「拆散替代配方组」，**枚举切换按钮三档**：`FULL`「完全」（原 `noGrouped=true`）→ `SELECTIVE`「选择性」（原 `false`，**默认**）→ `OFF`「关闭」（2026-10-03 新增）；档位名走 `text.autoconfig.brbe.option.alternativeRecipes.splitMode.<常量>`，**三行 tooltip** = `…splitMode.@Tooltip[0]/[1]/[2]`，与「标签模式」同排版） | **完全**：所有替代配方组拆成单配方格（`ungroup/ClientRecipeBookMixin` + `CollectionPipeline.applyUngroup`），并优先于「自动收纳同产物配方」；**选择性**：`CollectionPipeline.applySortExtraction`（Stage 2.5）按变体状态（pin / 可合成 / 残缺 / 搜索命中）把变体剥出去参与排序；**关闭**：两者都不做 —— Stage 2.5 跳过、不拆散，替代配方组整体保持原样（组内变体在同一按钮上轮循，代价是组内变体被 pin / 搜索命中时不再单独拎出来） | `ungroup/ClientRecipeBookMixin` + `CollectionPipeline` + `mixins/pipeline/RecipeBookComponentMixin`（缓存键分位：选择性 0 / 完全 bit4 / 关闭 bit16） |
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

## 2026-09-25（六）：幽灵物品锁定的**真正根因** —— 命中矩形用错坐标系（26.2 / 26.3 / 1.21.11；1.21.1 本就正确）

**用户反馈**：（五）部署后 26.3 实测 **Alt 既锁不住轮循的幽灵物品、也翻不动它**；同一次实测里
配方书**网格按钮**的锁定 + Alt+滚轮一切正常（"能冻结、也能翻"）。

**诊断链**（临时探针 `LockProbe` 写 `<gameDir>/logs/brbe-debug.log`，定位后已全部删除）：

| 探针 | 实测值 | 含义 |
|---|---|---|
| `BRBE-LOCK-KEY` | `isDown=true lalt=true` | 按键判定活着（按钮锁定同源，互证） |
| `BRBE-LOCK-GHOSTF` | `entries=3` | 幽灵逐槽循环的 `@Redirect` **确实在跑**（挂钩没问题） |
| `BRBE-LOCK-CTX-Slot` | `MISS rect=(30,17) cursor=(350,118)` | 上下文压进去了，但命中判定失配 |
| `BRBE-LOCK-SCROLL` | `down=true hovered=null latch=false stepped=false` | claim 从未成功 → 既不冻结也不步进 |

关键推论：`rect=(30,17)` 是**容器相对**坐标，`(350,118)` 是**屏幕**坐标。由同帧日志里的配方书
按钮矩形 `(171,123)` 反推容器原点 = `(307,101)`（= `leftPos-147+11, topPos-9+31`，与 176×166
居中公式 `(790-176)/2, (368-166)/2` 完全吻合）→ 该槽位真实屏幕矩形 `(337,118,16,16)`，
指针 `(350,118)` **正落在里面**：用户指的确实是那一格。（五）把它当屏幕坐标比较 → 永远 MISS。

**根因（字节码实证）**：原版 `AbstractContainerScreen.extractContents`（1.21.11 是 `render`）
先 `pose.translate(leftPos, topPos)`，再走 `extractSlots → AbstractRecipeBookScreen.extractSlots
→ RecipeBookComponent.extractGhostRecipe → GhostSlots.extractRenderState`，而
`lambda$extractRenderState$0` 用 `slot.x/slot.y` 直接 `fill/fakeItem` —— **`Slot.x/y` 是容器相对坐标**。

**修复**：`mixins/cyclelock/GhostSlotsCycleLockMixin` 新增 `@Unique brbe$containerOrigin()`
（当前界面 `instanceof AbstractContainerScreen` → 复用既有 `AbstractContainerScreenAccessor`
的 `brbe$getLeftPos/getTopPos`；非容器界面退回 `(0,0)`），`forEach` 与 tooltip 两处压上下文改为
`origin[0] + slot.x, origin[1] + slot.y`。**同一个坑仓库里已有先例**：`WorkstationTitleTrigger.titleRect`
的注释「`extractLabels` 在 `pose.translate(leftPos, topPos)` 内绘制标题——绝对屏幕位置 = `leftPos + titleLabelX`」。

**1.21.1 无需修改**：该分支 `GhostRecipe.render(gui, mc, x, y, …)` 的参数里就带渲染原点
（调用方 `InventoryScreen.render` 传 `renderGhostRecipe(gui, leftPos, topPos, false, f)`），
`GhostIngredientCycleLockMixin` 一直用 `origin + getX()/getY()`，坐标系本就正确。

**验证**：用户 26.3 实机通过（Alt 冻结 + Alt+滚轮逐格翻动）。26.2（含 `0.19.5-for-test`）与
1.21.11 同步修改并部署，未单独实机验证（同一份代码、同一坐标系）。

## 2026-09-26（一）：替代配方组**分页** + **独立滚轮翻页区**（26.3 先行，其余分支待移植）

**用户诉求**（递进四轮）：① 给替代配方组加**行上限 4、列上限 4** 的分页，翻页按钮的位置/贴图与
LEI 查询窗口一致，但每对键的左右角标要**运行时裁切**交换成 **左 ▲ / 右 ▼**（"必须裁切中心三角后
交换，而不是替换两部分贴图的位置"）；② 微调：顶部不拓展容器、翻页键**悬浮**；裁切宽度右拓 1px；
整体右移 5px、上移 5px；③ 分页后的组浮层要有**独立滚轮翻页区**，并**覆盖在配方书之上**；④ 两处都做
（原版合成书 + 锻造台自研书）。

**落地**：
- 新增 `util/LeiPageButtons`：LEI 查询窗口那对键的复用（同贴图 `brbe:recipe_book/lei_page_button`、
  14×13、间距 15、同音效、同 `n/m` tooltip、Ctrl 跳首/末页）；三角按"裁切中心三角后交换"实现
  （左键贴右键的 ▲、右键贴左键的 ▼，`srcX/dstX` 各偏 1px 对齐两块贴图的 1px 错位）。
- 新增 `util/AlternativesPaging`：组浮层的独立滚轮区（登记/认领/失效规则见（二））。
- `mixins/alternativerecipes/OverlayRecipeComponentPagingMixin`（原版浮层，init 尾部留档全量按钮、
  按页放回 16 个并重排 4 列）+ `smithingtable/SmithingOverlayRecipeComponent`（自研浮层按页重建按钮）：
  每页 16 条（4×4）；分页时面板宽度**固定 4 列**（右对齐的翻页键不随每页条目数漂）；面板**不向上
  拓展**、翻页键悬浮在右上角（`LeiPageButtons.ABOVE`）；点击翻页 + 翻页音效；命中翻页键时清
  `lastRecipeClicked`（否则宿主把上次点过的配方再放一次）。
- 滚轮认领挂在 `RecipeBookGesture.claimScroll` **最前面**（配方书滚轮唯一接缝，先于书体与 RBIP
  标签栏；也必须在 `AbstractRecipeBookScreen` 分支之前，否则自研书那条兜底入队路径走不到）；
  指针落在"浮层盒 + 悬浮翻页键"内 → 翻浮层的页、配方书不翻；**单页**组浮层不占滚轮区。
- `SmithingRecipeBookPage` 把组浮层内悬停的变体补进页面 `hoveredRecipe`（组内悬停 → 自动填充幽灵
  配方在此前读不到悬停）。

## 2026-09-26（二）：**看不见的滚轮区** —— 失效的组浮层仍占着配方书的滚动区域

**用户反馈**："在工作台配方书中滚动页面时，似乎配方区的滚动区域被某个看不见的滚动区域占用了，
滚动滚轮时能听见翻页音效（当时我使用了自定义翻页音效），而且配方区并没有翻动而是停留在原地。"

**根因（两处叠加，均在源码可查）**：
1. `Target.screen()` 由实现方**动态**返回 `Minecraft.gui.screen()` → "条目带所属界面、换界面即失效"
   形同虚设：旧条目在**任何**界面都自称属于当前界面，`track()` 里"按界面清旧条目"那行永远清不掉它。
2. 浮层的可见标志在**离开界面时无人复位**：`SmithingOverlayRecipeComponent.visible` 只在"点到组外"
   被置 false（关界面 / 收起书都不清）；原版 `OverlayRecipeComponent.isVisible` 同理
   （只有 `RecipeBookComponent.setVisible(false)` → `RecipeBookPage.setInvisible()` 这一条路径会清）。
   而两种浮层的盒坐标与配方格**几乎完全重合**（面板原点都是 `(width-147)/2 - xOffset`；浮层盒 =
   面板 +7,+26，配方格 = 面板 +11,+31）——于是正好"配方区的滚动区域被看不见的东西占了"。
   字节码实证：26.3 `RecipeBookComponent.getXOrigin() = (width-147)/2 - xOffset`（`xOffset=86`），
   与 `GenericRecipeBookComponent.initVisuals` 逐字一致。

**修复**：
- `AlternativesPaging` 加**两道独立守卫**：① 界面身份在 `track()` 时**取值快照**（`Target` 接口不再
  有 `screen()`，实现方无从"动态回答"）；② **心跳**（`LIVE_TTL_MS = 250ms` 未续 = 不再渲染 → 条目
  作废，与 `HoverGhostRecipe` 的预览心跳同款写法）。换界面 / 心跳过期 / 不再分页的条目一律清除；
  认领仍只认一个（最新登记的那个赢了）。
- `GenericRecipePage#hideOverlay()`（默认空实现）+ `GenericRecipeBookComponent.setVisible(false)` 调用
  + `SmithingRecipeBookPage` 覆写：自研书收起时一并关闭组浮层（原版走 `setInvisible`，自研书此前
  漏了 → 收起再打开那个过期浮层会原样冒回来）。

**回归工具（新增）**：`tools/alternatives-paging-harness/` —— 用**真实编译产物**
（各分支 `build/classes/java/main`）+ `stubs/` 最小替身驱动 `AlternativesPaging`；
`run.sh [26.3|26.2|1.21.11|1.21.1|all]`。14 项断言：正常认领/翻页/两端钳制、区外不认领、
**换界面后旧条目不得认领**、**心跳过期不得认领**、持续渲染仍认领、`paged=false` 不得认领、
同屏多目标只认领最新一个。并用**旧语义复刻**（out-of-tree `AlternativesPagingOld` + 动态 `screen()`）
自查过 harness 确实能抓到旧 bug：旧 = `认领=true`、页码 0→1（= 音效响、配方书被吞），新 = `认领=false`。

**部署**：26.3 md5 `ed38e47acb7406cf12453743d9df5b17`（备份 `20260926-1353`）；
harness 26.3 **14/14 绿**、`tools/mixin-check` 全通过。其余分支此功能尚未移植。

## 2026-09-26（三）：打开锻造台 = NPE 断线 —— `setVisible` 早于 `recipesPage` 创建

**用户反馈**：打开锻造台存档崩溃，报"网络协议错误"（客户端与集成服务端断连、游戏没退）。
崩溃报告实锤：

```
java.lang.NullPointerException: Cannot invoke "com.alonie.brbe.generic.GenericRecipePage.hideOverlay()"
  because "this.recipesPage" is null
  at GenericRecipeBookComponent.setVisible(GenericRecipeBookComponent.java:500)
  at GenericRecipeBookComponent.init(GenericRecipeBookComponent.java:126)
  at SmithingRecipeBookComponent.init(SmithingRecipeBookComponent.java:36)
```

**根因（我上一条修复引入）**：（二）在 `setVisible(false)` 里加了 `this.recipesPage.hideOverlay()`，
但 `setVisible` 有两条调用时点：`init` 第 126 行 `setVisible(BRBBookSettings.isOpen(...))`
**远早于**页面创建——`SmithingRecipeBookComponent.init` 是先 `super.init(...)`（第 36 行，里面就会
`setVisible(false)`）再 `this.recipesPage = new SmithingRecipeBookPage(...)`（第 40 行）。
于是"打开界面时配方书处于收起状态"（`BRBBookSettings.isOpen` = false）→ 立即 NPE；酿造台同理。

**修复**：`setVisible` 的 `!visible` 分支补判空（与类中既有写法一致——`resetToPage`/`brbe$updateHoverGhost`
等处同样是 `if (this.recipesPage == null) return;`）：

```java
if (!visible) {
    this.brbe$hoverGhostRecipe = null;
    if (this.recipesPage != null) {          // init 早期页面还没建 = 没有浮层可收
        this.recipesPage.hideOverlay();
    }
}
```

**部署**：26.3 md5 `e388009a0b2184dd911349d1fdacec53`（备份 `20260926-1418`）；
部署 jar 内 `javap -c` 核对 `getfield recipesPage; ifnull` 判空已就位；harness 14/14、mixin-check 全通过。

**教训**：跨分支移植这一版时，判空必须一起带过去（四分支的 `init` 顺序相同）。

## 2026-09-26（四）：分页组浮层"总是生成在同一个位置" —— 位置仍按**整组**几何算

**用户反馈**："分页的替代配方组总是生成在同一个位置，而不是像普通的替代配方组随鼠标的位置生成。"

**根因（我的分页改动遗漏的一半）**：原版 `OverlayRecipeComponent.init` 的 x/y 是按**整组**条目数算的
（26.3 字节码逐式核实）：列数 = `size<=16 ? 4 : 5`、行数 = `ceil(size/columns)`，然后三步定位：

| 步 | 公式（原版） | 作用 |
|---|---|---|
| ① x | `right = x + min(size, columns)*25`；`right > centerX+50` → `x -= f*(int)((right-(centerX+50))/f)`（**截断**取整） | 盒右缘不超过锚点右侧 |
| ② y | `bottom = y + rows*25`；`bottom > centerY+50` → `y -= f*ceil((bottom-(centerY+50))/f)` | 盒底留在配方格内 |
| ③ y | `y < centerY-100` → `y -= f*ceil((y-(centerY-100))/f)` | 盒顶不低于锚点上方 100 |

其中 `(x, y)` = **被点击的组按钮**坐标，`(centerX, centerY)` = 配方页面区域中心，`f` = 格宽 25。
分页只改了盒子的**列/行**（4×4）与面板宽度，却把位置留给了"整组"：45 条的组被当成 **5 列 × 9 行**，
② 一次把盒底往上顶 225px → `y ≈ 面板顶 - 19`，再经"上屏夹取"就固定成**配方书上方/左上角的一个点**，
点哪一行、哪一列都一样（离线复算：640×360 缩放下恒为 `(171, 78)`，而配方格从 `(171, 128)` 起）。

**修复**：`OverlayRecipeComponentPagingMixin` 新增 `brbe$placePagedBox(...)`，在 `init` TAIL 按
**本页几何**（4 列 × 4 行）重跑上面三步，再走统一的 `AlternativeOverlayLayout.clampToScreen(...)`
（新抽出的共享夹取：30px 边距优先、放不下贴边；`OverlayRecipeComponentPositionMixin` 改为调用它，
两条路径规则不再可能漂移）。修复后同一场景：

```
右键第 1 列第 1 行 → (171, 128)      右键第 3 列第 2 行 → (196, 128)   第 5 列第 4 行 → (196, 128)
对照：原版一个 14 条的普通组（4 列 × 4 行）→ (196, 128)   ← 完全同规则
```

即：盒子回到配方格顶部、横向按"被点的列"对齐；**4 行高的盒子在原版规则下盒顶就是固定在配方格顶部**
（原版 13~16 条的组同样如此，②的作用就是把盒子留在配方书内）——若要"盒顶严格贴被点那一行、
允许浮层伸出配方书"，需去掉②，已就此询问用户。

**部署**：26.3 md5 `364b046970cbf023efb5e3eb77df4169`（备份 `20260926-1441`）；harness 14/14、mixin-check 全通过。

**（四·续）用户选定：分页盒保持原版规则 + 自研书也改成贴被点的组按钮（同日 15:02）**

两个设计点问过用户，答复：① 分页盒**保持原版规则**（盒顶=配方格顶，与 13~16 条的普通组一致，
不为了"贴行"把浮层伸出配方书）；② 锻造台/酿造台自研书的组浮层**也改成贴被点的组按钮**（此前写死
`面板 +7,+26`，点哪一行都同一处）。

**收口**：定位公式抽到 `AlternativeOverlayLayout#placeBox(...)`（+ `pageCenterX/pageCenterY`），
三处调用同一实现——原版合成书分页盒（`OverlayRecipeComponentPagingMixin.brbe$placePagedBox`）、
原版普通盒（`OverlayRecipeComponentPositionMixin` 的夹取改用共享 `clampToScreen`）、自研书
（`SmithingOverlayRecipeComponent.brbe$placeBox`）。`GenericRecipePage#initOverlay` 签名补上
**被点击的组按钮坐标**（`anchorX/anchorY`），`SmithingRecipeBookPage` 透传给浮层。

离线复算（640×360 缩放，书面板 (160,97)、配方格 (171,128) 起）自研书新落点：

| 组大小（盒） | 第 1 列第 1 行 | 第 2 列第 2 行 | 第 3 列第 4 行 |
|---|---|---|---|
| 3 条（83×33） | (171,128) | (196,153) | (221,203) ← 完全跟着点的行 |
| 6 条（108×58） | (171,128) | (196,153) | (196,178) ← 第 4 行被②拉回 |
| 20 条（108×108，分页） | (171,128) | (196,128) | (196,128) ← 与合成书分页盒一致 |

与原版合成书**逐式同规则**（包括"②把盒底留在配方格内"这一条：所以 2 行以上的盒子盒顶同样落在配方格顶，
只有 1 行的组会严格跟着点的行）。**部署**：26.3 md5 `26d21718253de0b3cc407c44ff9972eb`（备份 `20260926-1502`）。

## 2026-09-26（五）：悬停预览**整片隐藏工作区真实物品**（四分支同步）

**用户反馈（设计缺陷）**："自动填充幽灵配方时，如果某个真实物品放在了幽灵配方覆盖不到的地方
（比方说幽灵只占左上角一格，用户把物品放在了别的格子），幽灵就会和真实物品混在一起。
我的想法是：通过此功能显示幽灵配方时，**不管有没有覆盖到，都暂时隐藏工作区的真实物品**。"

**根因**：这是 2026-09-25 那次修复留下的过度限制——当时为了修"锻造台工作区槽位整格空白"
（幽灵渲染谓词只画空槽位），把隐藏范围收窄成"**本次幽灵会画到的槽位**"
（原版书查 `GhostSlots.ingredients`、自研书查组件上报的覆盖槽位集合）。于是幽灵**没有**覆盖的
槽位里的真实物品原样显示 → 与幽灵混排。

**修复**（`HoverGhostRecipe#hidesRealItemIn`，判定简化为一条）：

```java
if (slot == null || !isPreviewing()) return false;
return !(slot.container instanceof Inventory);   // 非玩家背包 = 工作区 → 预览期间一律隐藏
```

覆盖到的槽位显示幽灵、没覆盖的槽位保持空白；玩家背包/快捷栏/护甲/副手（`container` 都是
`Inventory`）不受影响。**敢整片隐藏的前提**是幽灵绘制不再依赖"槽位为空"：原版 `GhostSlots`
本来就无条件画（`lambda$extractRenderState$0` 无空槽判定，字节码核实），BRBE 自研书由
`GenericGhostRecipe.render` 的 `hoverPreview` 忽略渲染谓词强制画——否则会回到 2026-09-25 那个
"整格空白、连幽灵都没有"的回归。

**顺带清掉的死代码**（隐藏范围不再需要"覆盖槽位"信息）：`HoverGhostRecipe.genericPreviewSlots`
字段、`setGenericPreviewing(boolean, Set)` 的第二参数、`GenericGhostRecipe#coveredContainerSlots()`、
`GenericRecipeBookComponent.brbe$hoverGhostSlots` 字段。

**部署**：26.3 md5 `8eaebd4404adfae4b06d679c99983296`（备份 `20260926-1520`）；javap 核对部署 jar
内 `hidesRealItemIn` = `ifnull/isPreviewing` + `!(container instanceof Inventory)`。
26.2 / 1.21.11 / 1.21.1 同步移植并构建部署（同一套判定，1.21.1 的 `hidesRealItemIn` 本来就只差
"原版路径怎么查幽灵槽位"那一段，现已一并简化）。

## 2026-09-26（六）：产物槽遮罩被误伤 —— 只该对**可合成**配方移除

**用户反馈**："残缺配方和不可合成配方的幽灵配方中产物槽的红色遮罩和白色遮罩也被移除了
（之前尝试移除可合成配方的幽灵配方里的产物的红白遮罩，也许是误将残缺配方和不可合成配方也进行了修改）。"

**根因**：上一轮（2026-09-25「显示幽灵配方时产物格不该有红/白罩」）是**无条件**跳过结果槽遮罩
——`PartialGhostOverlayUtil.prepare` 一见到结果槽就 `noRedMaskSlots.add(...)` + `continue`；
而 `GhostSlotsMixin` 的**同一个** `fill` 重定向用 `shouldShowRedMask` 同时门控红罩（`0x30FF0000`）
与白罩（`0x30FFFFFF`），所以两种遮罩在整个工作台路径上被一起洗白，残缺/不可合成配方也遭殃。

**修复**（`prepare`，四分支同一套）：结果槽**先记下**，等材料槽判完再决定——

```java
if (resultSlot != null && allMaterialsOwned) {   // 材料全齐 = 可合成 → 不画遮罩
    noRedMaskSlots.add(key(resultSlot.x, resultSlot.y));
}
```

- 可合成（材料全齐）→ 产物格不画红/白罩（2026-09-25 诉求保持）；
- 残缺 / 不可合成（有材料槽缺料）→ 产物格遮罩照原版画（本次修复）。
- "齐没齐"直接取材料槽本轮有没有被标进 `noRedMaskSlots`，与逐槽红罩同源，不引入第二套判定；
  结果槽仍**不参与**数量扣除（产物本来就不是要凑的材料）。

**部署**：26.3 md5 `5b090851861aaa391a08026c843248ac`（备份 `20260926-1550`）；
javap 核对部署 jar 内 `prepare` 尾部 = `resultSlot != null && allMaterialsOwned` 两道判空后才 add；
四分支 `mixin-check` 全通过。26.2 / 1.21.11 / 1.21.1 同步（1.21.1 的结果槽按位置识别，
改法是记下 `resultKey` 后同样条件加入）。

## 2026-09-26（七）：酿造配方书**标签页分类错误** —— 判据该用产物形态，不是基底形态

**用户反馈**："酿造台里的每个配方书标签对应的配方存在分类错误的情况，比如在饮用型里会出现喷溅型药水，
喷溅型里会出现滞留型药水。"

**根因**：26.3 把酿造改成数据驱动配方（`minecraft:brewing`，279 条）后，三形态（普通/喷溅/滞留药水）
各有一份同内容的转换，我按**基底物品**归属标签页（`belongsToTab` 比 `inputItem()`）。但**火药与龙息
会改变形态**：

| 配方 | 基底 | 试剂 | 产物 |
|---|---|---|---|
| `potion_awkward_gunpowder` | `minecraft:potion`（饮用型） | 火药 | `minecraft:splash_potion` |
| `splash_potion_awkward_dragon_breath` | `minecraft:splash_potion`（喷溅型） | 龙息 | `minecraft:lingering_potion` |

于是"饮用型 + 火药"落进**饮用型**标签页（结果图标又是按产物形态画的 → 饮用型里显示一枚喷溅药水），
"喷溅型 + 龙息"落进**喷溅型** —— 正是用户看到的现象。

**修复**：判据换成**产物形态**（`outputItem()`，与 `getResult` 画图标用的同一取值）：

```java
public boolean belongsToTab(BRBBookCategories.Category category) {
    Item output = outputItem();
    return output == null || output == category.getItemIcons().getFirst().getItem();
}
```

用真实配方数据离线统计（26.3 jar 内 `data/minecraft/recipe/brewing/`，279 条）：

| | 饮用型 | 喷溅型 | 滞留型 |
|---|---|---|---|
| 修前（按基底） | 108（含 45 条**产物是喷溅药水**） | 108（含 45 条**产物是滞留药水**） | 63 |
| 修后（按产物） | 63 | 108 | 108 |

修后每个标签页 = **该形态的全部药水及其酿造路线**（喷溅型 108 = 45 条"饮用型 + 火药" + 63 条喷溅内部转换；
滞留型 108 = 45 条"喷溅型 + 龙息" + 63 条滞留内部转换），与结果图标、幽灵预览（`inputAsItemStack`
仍用配方自己的基底物品 → "先放饮用型药水 + 火药"）全部自洽。形态未知（`outputItem()` 为 null）时恒 true。

**部署**：26.3 md5 `bdc04f3cd28c8b241e2a300dbfd1f512`；26.2 `5fc490bca5a6892c090c59f917e4ed0f`、
1.21.11 `a43f08cd8ffdef3a573cade39c4eb7a2`（备份 `20260926-1620`；26.2/1.21.11 的 data 无形态信息，
两种取值都是 null → 行为不变，仅为与 26.3 同一套写法）。javap 核对部署 jar 内 `belongsToTab` 调用
`outputItem()`。**1.21.1 不动**：它的 `getResult` 按标签页物品绘制图标（旧语义），不会出现"标签页里
混入别形态图标"，其内容一直是"所有 Mix 列在三个标签页"。

## 2026-09-26（八）：点击配方的**缺料引导**被悬停逻辑抹掉 —— 点击引导应是持续的

**用户反馈**："酿造台的配方点击后无法填充幽灵配方（是原版方式填充），只能靠鼠标悬停来临时填充
幽灵配方。锻造台也有相同问题。"

**根因（悬停预览与点击放置打架）**：`GenericRecipeBookComponent#brbe$updateHoverGhost` 每帧跑，
"指针下没有配方"时无条件 `ghostRecipe.clear()`。于是：

```
悬停 R  → 预览写幽灵 R（hoverGhostRecipe = R）
点击 R  → handlePlaceRecipe 写"缺料引导" R（原版语义：点击后一直显示）
指针移开 → hovered == null ≠ R → ghostRecipe.clear()  ← 点击写的引导当场被抹掉
```

而点击后玩家**必然**要把鼠标移向工作区放材料——于是引导刚好在你需要看它的时候消失，观感就是
"点击不填充幽灵配方，只有悬停时才临时出现"。工作台（原版书）不受影响：那条路径由服务端
`fillGhostRecipe` 写入，`HoverGhostRecipe.invalidate()` 已把所有权交回原版（`release()` 不再还原
旧快照）。

**修复**（`GenericRecipeBookComponent` + 两个子类的 `handlePlaceRecipe`）：

- 新增 `brbe$placedGhostRecipe`（点击留下的引导）与两个受保护入口
  `brbe$showPlacedGhost(R)`（写入 + 登记，与悬停预览同一写入路径）/ `brbe$clearPlacedGhost()`；
- 酿造 / 锻造的 `handlePlaceRecipe`：**材料不齐**分支改写 `brbe$showPlacedGhost(result)`；
  **材料齐、直接放置**分支补 `brbe$clearPlacedGhost()`（此时幽灵已清空，不能再被"还原"回来）；
- `brbe$updateHoverGhost`：指针离开按钮时不再一律清空——若登记了点击引导就把它**还原**回来
  （悬停预览只是临时盖在引导之上）；配置「自动填充幽灵配方」关闭时同样保留点击引导
  （用 `ghostRecipe.size() == 0` 判断"当前显示的是不是悬停预览"，避免每帧 clear+重写把轮循计时清零）。

**部署**：26.3 md5 `aca70a25e7615b2e85ab65a1dd37ff65`、26.2 `608bdbc744cc822767eef753eb23a786`、
1.21.11 `18c25b0d5da1b06f1300c12b33a1de70`、1.21.1 fabric `54b6686969a7999446a4d986dfcb50dc` /
neoforge `9fab99d47f58c71a6e9db378af6820e3`（备份 `20260926-1705`）；四分支 `mixin-check` 全通过；
javap 核对部署 jar 内 `brbe$placedGhostRecipe` / `brbe$showPlacedGhost` / `brbe$clearPlacedGhost` 均在。

## 2026-09-26（九）：点击缺料配方时**先把工作区里的真实物品退回背包**（四分支同步）

**用户反馈**："当工作区槽位里有真实物品了，此时点击缺失材料的配方并填充幽灵物品后并不会将此真实物品
放入背包，而是任其留在工作区内。原版配方书的做法是将其移回背包。"

**原版语义（26.3 jar 字节码核实，`net.minecraft.recipebook.ServerPlaceRecipe`）**：

```java
static PostPlaceAction placeRecipe(..., boolean bl /* 允许无视清格检查 */) {
    ServerPlaceRecipe spr = new ServerPlaceRecipe(...);
    if (!bl && !spr.testClearGrid()) return NOTHING;   // 网格塞不回背包 → 连幽灵都不出
    StackedItemContents contents = inventory + craftSlots;
    return spr.tryPlaceRecipe(recipe, contents);
}
private PostPlaceAction tryPlaceRecipe(recipe, contents) {
    if (contents.canCraft(recipe, null)) { placeRecipe(recipe, contents); return NOTHING; }
    clearGrid();                                       // ← 缺料：先把网格物品退回背包
    return PLACE_GHOST_RECIPE;                         // 再发幽灵
}
private void clearGrid() {
    for (Slot s : slotsToClear) {
        ItemStack stack = s.getItem().copy();
        inventory.placeItemBackInInventory(stack, false, SERVER_ONLY);
        s.set(stack);
    }
    menu.clearCraftingContent();
}
```

即：**缺料分支（PLACE_GHOST_RECIPE）在原版里本来就包含"清空工作区、物品退回背包"这一步**；
BRBE 的自研书（酿造台/锻造台）只做了"写幽灵"，少了这一步 → 工作区里的旧物品留在原地和幽灵叠在一起。

**实现**（四分支同步；客户端放置路径没有服务端 `placeItemBackInInventory`，用已有的
`ClientInventoryUtil.storeItem`（PICKUP 点击序列）等价实现）：

- `ClientInventoryUtil.canReturnSlotsToInventory(menu, workspaceSlot)` —— vanilla `testClearGrid()`
  的等价判定：用"背包现状模型"（可合并的同类堆 + 剩余空格数）逐堆模拟 `storeItem` 的落点；手上的东西
  （`menu.getCarried()`）会被 `storeItem` 先放进背包，所以**先按它占位**；任何一堆放不下即 false。
- `ClientInventoryUtil.returnSlotsToInventory(menu, workspaceSlot)` —— 逐槽
  `storeItem(i, 玩家背包槽)`。⚠️ 传的是**菜单槽位序号**（= `handleContainerInput` 的 slotId /
  `menu.slots` 下标），不是 `Slot#index`（那是槽位在它自己容器里的序号，玩家背包槽两者并不相同）。
- 酿造台 / 锻造台 `handlePlaceRecipe` 的缺料分支：`canReturn…` 为 false → **直接 return
  （连幽灵都不写，= vanilla 的 NOTHING；宁可什么都不做，也不要把物品挤到光标上/地上）**；
  否则先 `returnSlotsToInventory` 再 `brbe$showPlacedGhost(result)`。
- 工作区槽位（`brbe$isWorkspaceSlot`）：酿造台 = 三个瓶子槽（0..2）+ 材料槽（3），**不含燃料槽**；
  锻造台 = 模板/基底/附加材料，**不含结果槽**（与 vanilla `slotsToClear` 只清配方输入槽同义）。

**验证方法**：酿造台/锻造台工作区先放几件真实物品 → 点一个**材料不齐**的配方 → 工作区应被清空、
物品回到背包，随后显示缺料幽灵引导；背包塞不下时点击不产生任何变化（vanilla 同款）。

**部署**（备份 `20260926-2045`，原子替换）：26.3 `60155f5700af81d720af55f2daf70e12`；
`javap` 核对部署 jar：`canReturnSlotsToInventory` / `returnSlotsToInventory` /
`insertIntoInventoryModel` / `isPlayerInventorySlot` + 两个组件的 `brbe$isWorkspaceSlot` 均在。

**同批仍在 26.3 里的临时诊断**（用户 2026-09-26 反馈"纯悬停预览后工作区留下持久化幽灵"，待定位后删除）：
`GenericRecipeBookComponent` 的 `BRBE-GHOST` 追踪（悬停/引导/收书状态变化、1Hz 状态采样、
每次 `GenericGhostRecipe.addIngredient` 的调用者栈帧、`STUCK` 悬停探测）、
`HoverGhostRecipe.setGenericPreviewing` 的心跳翻转、两处屏幕 mixin 的外部清空点；
另外顺手加了"**无主幽灵一律清掉**"的兜底（指针不在配方上且没有点击引导时，幽灵必须为空）。

## 2026-09-26（十）：纹饰组显示模板 + 「右键获取更多信息」行 + 酿造台同产物路线合并成组（四分支同步）

**用户反馈（两条）**：
① "MC 的锻造模板分为升级模板和纹理（纹饰）模板配方，按当前锻造台配方书的设定，使用相同纹理模板的
会通过替代配方组折叠起来，不过未展开时呈现为各个产物的轮循。所以我希望它们在未展开时将对应单元格的
展示物品改为这个组所使用的纹理模板。"
② "在锻造台和酿造台中，替代配方组的 tooltip 没有加上原版配方书的「单击鼠标右键获取更多信息」。请完善它。"

### ① 纹饰组折叠单元格 → 显示该组共用的模板

**数据事实**（26.3 jar 内 `data/minecraft/recipe/*_armor_trim_smithing_template_smithing_trim.json`）：
原版 18 条纹饰配方的 {@code base} 是 {@code #minecraft:trimmable_armor} **标签** →
`BRBSmithingTrimRecipe#from` 把 `display.base().resolveForStacks(ctx)` 展开成"每种可纹饰装备一件"
的一整组（头盔/胸甲/护腿/靴子/马铠…）→ 折叠单元格原本按 `time/30` 轮循这些产物。
"升级组"（`BRBSmithingTransformRecipe`，如下界合金升级）的 base 是具体物品、组内只有一条 → 不受影响。

**实现**：
- `GenericRecipeButton` 新增受保护钩子 `getDisplayedStack(category)`（默认 = 当前轮循配方的产物），
  `extractWidgetRenderState` / `renderSquashed` 的取物品改走它；tooltip 拆出
  `getTooltipFor(ItemStack)`（物品行 + 右键提示行 + 模组名行）。
- 新增 `smithingtable/SmithingRecipeButton`：`showCollection` 时（**只在换了集合时**重算，`showCollection`
  每帧都会被页面调用）判定"整组都是纹饰配方且共用同一模板物品" → 展示该模板、tooltip 也换成模板的
  （否则会出现"画着模板、写的是某件装备"）。
- `SmithingRecipeBookPage` 改用 `SmithingRecipeButton`。
- **只改展示**：内部"当前配方"照旧轮循 → 左键放置的仍是轮循到的那件、悬停幽灵预览照旧显示当前变体、
  右键展开的浮层里每个变体仍各自显示自己的产物（选装备部位的地方）。

### ② 「单击鼠标右键获取更多信息」行

**原版判据**（javap 26.3 `net.minecraft.client.gui.screens.recipebook.RecipeButton`）：

```java
public List<Component> getTooltipText(ItemStack stack) {
    List<Component> list = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), stack));
    if (hasMultipleRecipes()) list.add(MORE_RECIPES_TOOLTIP);   // gui.recipebook.moreRecipes
    return list;
}
private boolean hasMultipleRecipes() { return selectedEntries.size() > 1; }
```

**实现**：`GenericRecipeButton.getTooltipFor(ItemStack)` 在物品行之后、模组名行之前插入
`Component.translatable("gui.recipebook.moreRecipes")`，条件 `getOrderedRecipes().size() > 1`
（与原版 `hasMultipleRecipes()` 同义，= `!isOnlyOption()`，即右键能展开）。行序与合成书
（原版 `RecipeButton` + BRBE 的 `modname/RecipeButtonMixin`）完全一致：物品行 → 右键提示 → 空行 + 模组名。
自研书的 tooltip 走的是页面的 `hoveredButton.getTooltipText(recipe, category)`（1.21.1 是无参重载），
两处都在同一个 `getTooltipFor` 里，因此两个分支形态一致。

### ③ 酿造台：同一瓶药水的多条路线合并成替代配方组（用户选定）

**数据事实**（26.3 jar `data/minecraft/recipe/brewing/` 279 条配方解析）：
**只有 134 个唯一产物，其中 94 个产物有两条路线**——例：滞留型治疗药水既能
"滞留型粗制药水 + 闪烁的西瓜片"，也能"治疗药水 + 龙息"（两条的输出物品+药水完全相同）。
而 `BrewingRecipeBookComponent#getCollectionsForCategory` 此前是 **一条配方一个集合**
（`new BrewingRecipeCollection(List.of(potion), ...)`，四分支皆然），于是：

- 配方书里同一瓶药水出现**两个一模一样的格子**；
- 每个格子都只有一条配方 → `isOnlyOption()` 恒 true → 酿造台**根本没有替代配方组**，
  右键无反应，也就永远看不到"单击鼠标右键获取更多信息"那一行（用户反馈②里的酿造台部分）。

**实现**（四分支；请示用户后选择"合并 + 新建组浮层"）：

- `getCollectionsForCategory` 改为按 `BrewableResult#id()`（= 产物药水 id，与 pin 标识同源）分组，
  一个产物一个 `BrewingRecipeCollection`（组内 = 该产物的各条路线）。
- 新增 `brewingstand/BrewingOverlayRecipeComponent`：右键组格 → 展开路线格（结构照抄各分支自己的
  `SmithingOverlayRecipeComponent`，含"必须调**外层** render/extractRenderState 才会刷新悬停标记"
  那条踩坑注释）。**格子画的是该路线的酿造材料**（reagent；一组的产物都一样，材料才是区别），
  悬停时由页面把该路线补进 `hoveredRecipe` → 工作区照常显示这条路线要放什么的幽灵预览（含输入药水形态）。
  路线数极少（原版最多 2 条）→ 无分页、无滚轮翻页区。
- 新增 `brewingstand/BrewingRecipeBookPage`（`GenericRecipePage` 子类）：`initOverlay` /
  `overlayMouseClicked` / `render`（画浮层 + 补 hovered）/ `overlayIsVisible`（26.3 另有 `hideOverlay`）。
  `BrewingRecipeBookComponent` 改用它。右键点击组格 → 浮层选路线 → 左键点击 → 走既有的
  `handlePlaceRecipe`（`getCurrentClickedRecipe()` 由页面的 `lastClickedRecipe` 提供）→ 放该路线的材料。
- 顺带收益：pin 现在按"一个产物"生效（此前同产物的两条路线是两个可分别 pin 的格子）。

**部署**（备份 `20260926-2120`，原子替换）：26.3 `d288115abb59efd727fcc46ad4b5aa6e`、
26.2 `3e36f45f2a2a482bf99daf42c7526c02`（两实例）、1.21.11 `77eb508519a046065f93f595ab8c1c2e`、
1.21.1 fabric `1df4672c237dc90317f32a1e2e9aa247` / neoforge `d7452457461c53c17b6235f8052700e7`；
五个部署 jar 的 `javap` 核对：`SmithingRecipeButton`（`getDisplayedStack`/`brbe$resolveTrimTemplate`）、
`GenericRecipeButton`（`getDisplayedStack`/`getTooltipFor`）、`BrewingOverlayRecipeComponent`（`RouteButton`/`hoveredButton`）、
`BrewingRecipeBookPage`（`overlay`）均在。

**验证方法**：① 锻造台纹饰标签页 → 折叠格应显示**纹饰模板**（如"bolt 盔甲纹饰"）而不是轮循各件装备，
tooltip 为模板说明 +「单击鼠标右键获取更多信息」；右键展开 → 各变体各自显示装备；
升级标签页（下界合金）行为不变。② 酿造台 → 同产物只应有一个格子（如滞留型治疗药水从 2 个变 1 个），
tooltip 带右键提示行，右键 → 浮层显示两条路线的材料（闪烁的西瓜片 / 龙息），选中后左键放置该路线。

## 2026-09-26（十一）：悬停穿透修复 + 纹饰组去交互 + pin 半固定修复 + 酿造台浮层收进书体（四分支同步）

用户一次报了四个问题，逐个定位如下。

### ① 幽灵预览**透过替代配方组浮层**触发

**根因**：`GenericRecipePage.renderButtonGrid` 的命中判定只看"按钮矩形 + 光标"，不看浮层——
浮层打开时鼠标落在浮层的面板/间隙上，底下那格的网格按钮照样被判为悬停 → 写幽灵 + 出 tooltip。
原版合成书没这个问题：`hoverghost/RecipeBookPageMixin` 在 `overlay.isVisible()` 时**直接 return**，
整片网格不参与悬停判定。

**修复**：`GenericRecipePage` 新增 `suppressGridHover()`（默认 false），
`SmithingRecipeBookPage` / `BrewingRecipeBookPage` 覆写为 `overlayIsVisible()`；
命中判定加 `!suppressGridHover()`。浮层打开时网格不再产生 hovered*，
悬停浮层格子仍由页面的浮层分支单独喂（见下）→ 幽灵预览只属于浮层本身。

### ② 纹饰组：去掉"悬停预览幽灵 / 左键点击（含音效）"，并停止组内轮循

用户观察：展示虽然换成了模板，**逻辑上仍在轮循组内配方**（合成状态/边框/幽灵会随轮循抖）。
三处一起收口（`SmithingRecipeButton`）：

- `getCurrentDisplayedRecipe()` 覆写 → 纹饰组恒取 `getOrderedRecipes().get(0)`（不再按 `time/30` 轮循）；
- `GenericRecipeButton` 新增钩子 `providesHoverPreview()`（默认 true），纹饰组返回 false；
  `GenericRecipePage` 新增字段 `hoverGhostRecipe`（**幽灵预览专用**的悬停配方，每帧重置）：
  网格命中时 = `providesHoverPreview() ? hoveredRecipe : null`，浮层内悬停时 = 该变体。
  `GenericRecipeBookComponent.brbe$updateHoverGhost` 改读 `hoverGhostRecipe`（`hoveredRecipe`
  继续服务 tooltip / R-U 查询）→ 悬停纹饰组不写幽灵、也不"暂隐工作区真实物品"。
- `mouseClicked` 覆写：纹饰组的**左键直接返回 false**（`AbstractWidget.mouseClicked` 在
  `playDownSound` 之前判定 → 不放置、不放点击音效）；组内变体只能在右键展开的浮层里点选。

### ③ pin 后取消固定，排序不恢复且**持久化**

**根因**（`PinnedRecipeManager#addOrRemoveFavourite(GenericRecipeBookCollection)`）：旧实现对每个
已 pin 的 id 只删掉**第一个命中**就 `return`：

```java
for (Identifier id : this.pinned) for (R r : target.getRecipes()) if (r.id().equals(id)) { this.pinned.remove(id); this.store(); return; }
```

一个集合可能包含**多条**配方——锻造台纹饰组 = 每种可纹饰装备一条、酿造台一个产物 = 多条酿造路线。
于是取消固定只清掉一个 id，组内其余 id 仍是 pin 状态 → `has(collection)` 仍为 true → 排序继续把它
顶到最前；这个"半 pin"状态又被 `store()` 写进 `brbe.pins` → **重启后依然如此**（用户看到的"持久化"）。

**修复**：整组一起切换（取消 = `removeAll(组内全部 id)`；固定 = `addAll`），与另一重载
`addOrRemoveFavourite(PinnableRecipeCollection)` 的 `removeIf(target::has)` 同义；补 `version++`。
⚠️ 存量半 pin 状态：对那个组**再按一次固定键**即整组清掉（修复后的行为），或删 `brbe.pins` 里对应条目。
（查看器"单变体 pin"走另一重载，语义不变。）

### ④ 酿造台组浮层：越出书体 + 点书外关不掉

- **越界**：`placeBox` 只保证不越出**屏幕**（`clampToScreen` 的 30px 边距），不保证留在书体内。
  新增 `AlternativeOverlayLayout#clampToBook(x, y, boxW, boxH, panelLeft, panelTop)`
  （`BOOK_WIDTH/HEIGHT` 提为 public），酿造台浮层在定位后再按面板夹一次（放不下时贴面板左上角，
  宁可压住配方格也不出书）；按钮随位移重排。
- **关不掉**：锻造台屏幕实现了 `TopLayerOverlayProvider` 并在 `mouseClicked` 里把点击**优先路由**给
  配方书组件（`brbe$clickTopLayerOverlay`）→ 点到浮层格子 = 选路线、点到别处 = 关浮层。
  酿造台屏幕此前没有这套 → 点到书外时点击被容器槽位吃掉，浮层永不关。现在照抄同一套
  （`implements TopLayerOverlayProvider` + `brbe$hasTopLayerOverlay`/`brbe$renderTopLayerOverlay`/
  `brbe$clickTopLayerOverlay`/`brbe$getTopLayerOverlayBounds` + `mouseClicked` 路由；1.21.1 用
  `(double,double,int)` 签名），浮层同时改为**顶层绘制**（与锻造台一致，落在 carried item 之上）。

**部署**（备份 `20260926-2205`，原子替换）：26.3 `d995233de90757e3ad6b1864fe9e2400`、
26.2 `539d4e6f2d3008208ae683b36923632f`（两实例）、1.21.11 `f333f17c2f0124b999bf97511821fa0f`、
1.21.1 fabric `c2d3d07dbc437236480ded60d09cff05` / neoforge `b7ee40e235da3d98a7c609ff04282cc3`；
`javap` 核对部署 jar：`GenericRecipeButton.providesHoverPreview`、`GenericRecipePage.hoverGhostRecipe/
suppressGridHover`、`SmithingRecipeButton.getCurrentDisplayedRecipe/mouseClicked`、
`BrewingRecipeBookPage.suppressGridHover`、`BrewingStandScreenMixin.brbe$hasTopLayerOverlay/
brbe$clickTopLayerOverlay`、`AlternativeOverlayLayout.clampToBook` 均在
（1.21.11/1.21.1 fabric 走 intermediary 重映射，vanilla 覆写如 `mouseClicked` 显示为 `method_25402`）。

**验证方法**：① 打开组浮层 → 鼠标停在浮层面板（非格子）上：工作区**不应**出现幽灵、也不应出 tooltip；
② 锻造台纹饰组：折叠格显示模板、无轮循、悬停不出幽灵、左键无反应（无音效），右键仍能展开并在浮层里点选；
③ 任意自研书组（纹饰组 / 酿造台同产物路线）按固定键两次 → 排序应恢复原状，重启后也不再"粘"在最前；
④ 酿造台右键展开组 → 浮层完全落在书体内；点击书外的槽位/空处 → 浮层关闭（与锻造台一致）。

## 2026-09-26（十二）：点击引导**复活**根治 —— 外部清空即结束 + 停留预览取代引导（四分支同步）

**用户反馈**："酿造台出现了锻造台之前踩过的坑：悬停配方触发的幽灵配方在展示结束后会留下一个持久化
幽灵配方（这个配方的来源无规律）。"

**实机日志实证**（26.3 上一轮部署的 `BRBE-GHOST` 追踪，`logs/brbe-debug.log`）：

```
[21:35:44.256] guide-registered BrewableResult[滞留型水肺药水]#2e3aabbe (ghostSize=4)   ← 用户点了这格（缺料）
[21:35:58.930] external-clear:brewing-slotClicked slot=1 ghostSize=4                   ← 玩家点了酿造槽：幽灵被清
[21:35:59.374] hover - -> BrewableResult[滞留型水肺药水]#2e3aabbe (ghostSize=0 guide=...)
[21:35:56/21:36:17] ghost-add slot=3 dragon_breath ... setupGhostRecipe:103             ← 悬停结束时**引导又被写回来**
```

`orphan-cleared` 那一大串同理（每轮悬停预览结束都会把早先点击留下的引导还原一次）。
即：用户看到的那份"来源无规律"的幽灵 = **早先点击留下的缺料引导**，而不是刚悬停的配方。

### 修复 A：幽灵被外部清空 = 引导就此结束（日志实证的那个 bug）

新增 `GenericRecipeBookComponent#brbe$endGhostGuide()`（清登记 + 清预览守卫 + 清幽灵），
三处外部清空点由 `ghostRecipe.clear()` 改为调用它：

| 分支/文件 | 位置 |
|---|---|
| `BrewingStandScreenMixin` | `slotClicked`（点空槽且手上没东西） |
| `SmithingScreenMixin` | `slotClicked`（同上） / `slotChanged`（0..3 槽变化） |

不结束的话，这份**已被玩家处理掉**的引导会在下一次"指针离开悬停配方"时被还原 → 工作区自己冒出幽灵。
语义与工作台那条路径的 `HoverGhostRecipe.invalidate()`（原版流程接管幽灵 → 放弃还原权）一致。

### 修复 B：在别的配方上**停留**过 → 悬停预览取代点击引导

只在 A 修完还不够：引导若一直没被清（点完一直没放材料），每次悬停别人再移开都会把它还原回来，
观感仍是"来源无规律"。但**不能**简单地"一悬停就取消引导"——鼠标从被点的格子移向工作区时会**掠过**
别的格子，那样又会回到"点击后引导被鼠标移开抹掉"的老问题（用户 2026-09-26 早先反馈）。

因此 `brbe$updateHoverGhost` 的释放分支改成分三种情况（`GUIDE_SUPERSEDE_MS = 300ms`）：

| 离开时的情况 | 行为 |
|---|---|
| 离开的就是引导自己那条（同一组各条路线 `id()` 相同） | 还原引导（内容本来一样） |
| 只是**掠过**别的格子（停留 < 300ms） | 还原引导（"点击后移向工作区"的路径不被破坏） |
| 在**别的**配方上停留 ≥ 300ms | 引导**就此结束**，工作区保持干净 |

计时从"预览开始"那一刻起算（同一格内轮循换配方**不**重置——酿造台一个格子 = 同产物的一组路线，
每 1.5s 换一条，重置的话"停留"永远算不出来）；`brbe$showPlacedGhost` 里点击即刷新计时，
所以"点完马上移开"绝不会被算成"停留看过"。
另外把无主幽灵自愈收窄为"连预览守卫也为 null"才算孤儿（此前它会把正常的释放路径也吞掉，日志因此
把每次正常释放都记成 `orphan-cleared`）。

**部署**（备份 `20260926-2240`，原子替换）：26.3 `925f0cd7d7ab7026d6b1ed781fb99715`、
26.2 `b81720e8246c8df532375e4d8977a099`（两实例）、1.21.11 `d67223a20ce0ec8d84569370c18b9c44`、
1.21.1 fabric `0d800a4e51c5135cd359d04c5164f79d` / neoforge `f27ae019f504fcef543fe89bfa2ede1b`；
`javap` 核对：五份 jar 的 `GenericRecipeBookComponent` 都有 `brbe$endGhostGuide` +
`GUIDE_SUPERSEDE_MS`/`brbe$hoverStartedAt`/`brbe$sameRecipeId`，两个屏幕 mixin 的
`brbe$endGhostGuide` 调用点为 brewing 1 处 / smithing 2 处。

**验证方法**：① 点一个缺料配方（引导出现）→ 往工作区放一件材料（引导消失）→ 再随便悬停几个配方并
移开 → 工作区**不应**再冒出那份引导；② 点一个缺料配方 → 直接移向工作区（路上掠过其他格子）→
引导**应保留**（老行为不变）；③ 点一个缺料配方 → 在**别的**配方上停一会儿（>0.3s）再移开 →
引导结束、工作区干净；④ 同一格悬停（含酿造台一组多路线轮循）移开后不留任何幽灵。

## 2026-09-27：替代配方组浮层里的配方格加上 tooltip（四分支同步）

用户诉求："为替代配方组里的配方加上 tooltip（就像普通配方那样），锻造台、酿造台的配方书也要做。"
（AskUserQuestion 确认取 **A 方案**：只显示该格代表物品，格式与普通配方格一致。）

**为什么此前没有**：浮层打开时两处 tooltip 通路都被主动关掉——
① 自研书（锻造台/酿造台）：`GenericRecipeBookComponent.drawTooltip` 的 `if (!recipesPage.overlayIsVisible())`
直接不问页面（浮层盖住网格，网格 tooltip 会穿透）；
② 原版书（合成台/熔炉系）：`RecipeBookPage.extractTooltip` 的判定是
`hoveredButton != null && !overlay.isVisible()` —— 浮层打开时什么都不画。

**实现**（四分支同构）：
- 新增 `util/RecipeCellTooltips.forStack(registryAccess, stack, moreRecipes)`：与
  `GenericRecipeButton#getTooltipFor` **共用同一套行构造**（物品行 → 可选「单击鼠标右键获取更多信息」
  → 空行 + 模组名，模组名走本分支的 config 访问），后者改为委托它——两边格式永不漂移。
  **格子不带 moreRecipes**：格子本身就是展开后的单个变体，右键不再展开。
- 自研书：`GenericRecipePage.overlayTooltip()`（默认 {@code null}）新钩子；`SmithingRecipeBookPage` /
  `BrewingRecipeBookPage` 覆写它，从浮层 `hoveredButton()` 取行内容；`GenericRecipeBookComponent.drawTooltip`
  改为"先问浮层格子，再走原来的网格/设置按钮分支"。
- 原版书：`OverlayRecipeButtonMixin` 在浮层渲染路径里缓存**本帧画出来的那件产物**
  （`PopupRenderer.displayedResult(entry, selIdx)`），并实现新接口 `interfaces/IOverlayCellTooltip`；新增 mixin
  `alternativerecipes/RecipeBookPageOverlayTooltipMixin` 注入 `RecipeBookPage.extractTooltip` HEAD——那里才是
  原版配方书 tooltip 的正规出口（容器槽位 tooltip 先注册、会被它盖掉），据此补上格子 tooltip 并
  `ci.cancel()` 一并挡掉"格子底下那格"的 tooltip。查询窗口/pin 盖住指针时一律不出
  （`modalMaskOwnsCursor` / `isViewerActive` + `PinOverlayManager.covers`）。
- 酿造台格子 tooltip 与 `BrewableRecipeButton#getTooltipText()` **同款行序**（产物药水 + 效果 +
  空行 + 「材料 -> 输入药水」，白/灰 = 背包里有没有），只是用**本路线自己的**材料与输入药水；
  为此 `BrewingOverlayRecipeComponent.init` 增参传入标签页与菜单。锻造台格子 = 该变体产物行 + 模组名。

**1.21.1 顺带对齐**：该分支 `GenericRecipeButton#getTooltipFor` 原先漏了模组名行（另三分支都有），
本轮统一走 `RecipeCellTooltips` 后补齐（`showModName` 开关照旧）。

**验证**：四分支 `compileJava`/`build` 通过；`tools/mixin-check` 四分支全绿；javap 核对部署 jar
（`RecipeCellTooltips` / `IOverlayCellTooltip` / 新 mixin 的 `implements` 与注入目标、两个浮层格子的
`getTooltipText`、`GenericRecipePage.overlayTooltip`；1.21.11/1.21.1 的 mixin 目标已 remap 为
`method_2628`）。已原子替换部署六实例（备份 20260926-2232）：26.3-Fabric
`6dcd36c2a9fa28e97ac50ba7c551b4d0`、26.2-Fabric ×2 `a7155ee5bec5484dd9261d06fa9b36dd`、
1.21.11-Fabric `39339d1b2790372d7b1972f7da0720f4`、1.21.1-Fabric
`5c720d6d0687a239ccc17e44064adbfd`、1.21.1-NeoForge `0f37035feba483d0a778b8ea3e14f232`。

**验证方法**：右键展开任一替代配方组 → 悬停组内任一格：合成台/熔炉系显示产物物品行（+ 模组名）；
锻造台显示该变体装备名（纹饰组 = 带纹饰的那件装备）；酿造台显示"药水名 + 效果 + 材料 -> 输入药水"
（缺料为灰字）。同一组里合成书各格 tooltip 相同（产物一致），锻造台/酿造台各格不同。

## 2026-09-27（二）：组内格子 tooltip 被替代配方组浮层压住 —— 顶层重绘那一遍修复（26.2 / 26.3 改动；1.21.x 无需改）

用户反馈："替代配方组里的配方的 tooltip 会被压在替代配方组的 UI 下。"

**根因（字节码实锤）**：BRBE 的替代配方组浮层会被**画两遍**——第一遍在屏幕提取里（配方书页面渲染），
第二遍在 Fabric {@code ScreenEvents.afterExtract} 里由 {@code TopLayerOverlayRenderer} **抬一层 stratum
重画**（那是 2026-09-13 为"组浮层压住查询窗口"引入的顶层重绘）。而 Fabric 的 {@code GuiMixin} 用
{@code @WrapOperation} 包住的调用正是
{@code Screen.extractRenderStateWithTooltipAndSubtitles(GuiGraphicsExtractor,int,int,float)}
（javap 常量池：`Lnet/minecraft/client/gui/screens/Screen;extractRenderStateWithTooltipAndSubtitles(...)V`），
`afterExtract` 在该方法**返回之后**才触发；而原版 tooltip 的刷新（`extractDeferredElements`）就在那个方法里。
⇒ 顺序是「浮层第一遍 → 帧末 tooltip 刷新（画格子 tooltip）→ afterExtract 重画浮层」，
格子 tooltip 被**自己那一遍浮层**（更新的 stratum）压住；因为 tooltip 就在指针处、而指针正在浮层格子上，
所以整块 tooltip 基本被面板盖住，只在面板外露出一点。

**修复**（26.2 / 26.3）：
- `TopLayerOverlayRenderer.redrawsOverlayOnTop(Screen)`：该屏幕的组浮层是否会在 afterExtract 里被重画
  （判据与 `render` 的两个分支严格一致；`RecipeViewerOverlay.isActive()` 时返回 false）。
- `ClientCompat.drawComponentTooltipNow(gui, lines, mx, my)`：**立即**绘制组件 tooltip——与
  `setComponentTooltipForNextFrame` 的延迟体同一条绘制链（`ClientTooltipComponent.create` +
  `DefaultTooltipPositioner.INSTANCE` + style=null；26.2 的 `tooltip(...)` 末尾还没有 26.3 那个 boolean）。
- `TopLayerOverlayProvider.brbe$renderTopLayerTooltip(...)`（default 空实现）新钩子，由
  `SmithingScreenMixin` / `BrewingStandScreenMixin` 实现（取 `page.overlayTooltip()` 就地画）；
  `TopLayerOverlayRenderer.render` 在重画浮层**之后**调用它；原版书（合成台/熔炉系）分支同样在
  `overlay.extractRenderState(...)` 之后用 `OverlayCellTooltips.hoveredLines(overlay)` 就地画。
- **延迟注册让位**：`GenericRecipeBookComponent.drawTooltip`（自研书）与
  `RecipeBookPageOverlayTooltipMixin`（原版书）在该屏幕会被顶层重画时**不再注册**延迟 tooltip
  （否则同一帧画两遍：下面一份被盖、上面一份正常 → 重复混色）。
- 顶层那一遍补画前再判一次 `OverlayCellTooltips.blockedByOverlay(mx,my)`（pin / 查询窗口盖住指针时不出）
  ——那一遍跑在 pin 自己的 tooltip 之后，不判的话盖住指针的 pin 会被格子 tooltip 反过来压住。
- 新增 `util/OverlayCellTooltips`（找"指针下那一格"的行内容 + 穿透判据），原版书的两个使用点共用。

**为什么 1.21.11 / 1.21.1 不用改**：1.21.11 的 `TopLayerOverlayRenderer` 没有任何调用点（顶层重绘未接线）；
1.21.1 的顶层重绘挂在 `Screen.render` TAIL，而 `AbstractContainerScreen.render` 是在**开头**调
`super.render(...)` 的 → 那一遍在配方书渲染/格子 tooltip **之前**；且 1.21.x 的 tooltip 是立即绘制、
走原版 z=400 深度，后面的面板（z≈0/150）会被深度测试挡掉。

**验证**：两分支 `compileJava`/`build` 通过；`tools/mixin-check` 全绿；javap 核对部署 jar
（`TopLayerOverlayRenderer.redrawsOverlayOnTop`、`ClientCompat.drawComponentTooltipNow`、
`OverlayCellTooltips.hoveredLines`/`blockedByOverlay`、两个屏幕 mixin 的 `brbe$renderTopLayerTooltip`、
`TopLayerOverlayProvider` 的 default 方法）。已原子替换部署三实例（备份 20260926-2255；1.21.x 无改动未重新部署）：
26.3-Fabric `0dd4475fda2a3cd8135adc2aa7189e4e`、26.2-Fabric ×2 `203243f7f5691ed2f2397976cc842ae1`。

**验证方法**：右键展开替代配方组 → 悬停组内任一格：tooltip 应完整显示在浮层**之上**（不再被面板遮住），
移动鼠标时 tooltip 跟随不滞后；pin 或查询窗口盖住指针时不出格子 tooltip。

## 2026-09-26：配方区翻页动画参考反解（研究，**未改代码**）

用户提供一段手机音乐 App（专辑轮播）的录屏，认为其"退场"动画是配方区动画的理想答案。
逐帧反解（424 帧 / 24.28fps，ffmpeg 抽帧 + 逐行边缘测量 + 内容互相关）结论：
**参考里没有"缩放压缩"这个动作** —— 视窗是静止的硬裁切边界，卡片整体平移被边界吃掉，
内容 1:1 定尺居中裁切（宽 824→638 全程字形宽度不变；居中裁切假设 MAD 4.4~4.9，
横向压缩假设 19~40，压缩被排除），可见宽度收到 0 即"成一条线"。详见
`docs/配方区退场动画-参考反解.md`（含全部测量数据）。

对照本分支现役实现（`mixins/scrollablepages/RecipeBookPageAnimationMixin`，四分支持分支同一套）：
位移量、刚性长条、内容不缩放只裁切、无淡出、硬边界 **全部已经一致**；
**唯一实质差异是速度分布** —— 参考可见退场 ≈0.99s、中段近匀速
（有限时长 ease-out 拟合 R²=0.994，指数拟合 0.961），
现役是 `frac = 1-exp(-base·dt)`、`base = 6.2/pageAnimationDuration`（默认 0.5s），
0.05/0.10/0.15/0.25s 分别走完 46%/71%/84%/96%（"啪一下到位再慢慢蹭"）。
次要差异：收尾最后一条切片（现役 `effW≤0` 直接不画）、参考的邻页窄条 peek（120px→整页渐变）、
页面底纹静止（原版结构，建议不动）。改曲线的落点只有 `brbe$advanceAnimation` 里 6 行。

工具（离线，不启动游戏）：`tools/panel-exit-anim/preview.py`（A 裁切 / B 塌缩 / C 混合三种收场）、
`tools/panel-exit-anim/flip-compare.py`（实测 vs 现役速度曲线图 + 并排翻页动图），
输出在 `docs/panel-exit-anim/`。待用户指认"细节"具体指哪一项后再决定是否动代码。

## 2026-09-27：配方区翻页动画单元格退场「系统性重做」→ **已按用户要求完整还原**

用户先要求把"视频里视窗退场的细节"（靠边界那侧停住、另一侧继续走、中间被切掉、收成一条线）
系统性重做一版到**配方区的单元格**上，理由是"普通裁切观感不好、像素风直接拉伸更差"。
重做版（`util/PageFlipCellRenderer` 单一实现 + 两条路径共用；修掉残缺红罩横向拉伸、
pin 角标钉边界/悬出配方区、每格 3 个 scissor、锻造酿造书动画丢角标四条）已编译、mixin-check
全绿、原子替换部署（md5 `e636e96a1c13f596c074d7d7bace07ee`）。

**随后用户要求还原**（"还是还原吧，回到之前的版本"，未说明具体原因）。还原方式与结果：

- `RecipeBookPageAnimationMixin.java` 全文件 diff 均系本轮改动 → `git checkout --` 还原；
  `GenericRecipeButton.java` / `GenericRecipePage.java` 含**更早轮次的未提交改动**，
  因此按本轮改动内容**逐处反向修补**（不动其它未提交工作）；
  删除本轮新增的 `util/PageFlipCellRenderer.java`。
- **还原正确性证明**：重新构建后，三个受影响 class 与还原前备份 jar 内同名 class
  **逐字节一致**（`RecipeBookPageAnimationMixin` `06ddfda6…`、`GenericRecipeButton`
  `3933897008…`、`GenericRecipePage` `f11d6521…`），且整包 md5 回到
  `0dd4475fda2a3cd8135adc2aa7189e4e`（= 还原前备份 jar）。
- 实例已还原部署（原子替换）；重做版 jar 保留一份在
  `mods/brbe-ava-fabric-26.3-2.3.1.jar.bak.20260927-0149-redo`（md5 `e636e96a…`），随时可取回。
- 重做的设计说明与逐帧对照留在 `docs/配方区翻页动画-重做方案.md`、
  `docs/panel-exit-anim/cell-exit-compare.png/.gif`、
  `tools/panel-exit-anim/cell-exit-compare.py`（含"重做前 / 重做后 / 纯裁切 / 纯横向压缩"四列），
  作为后续讨论的素材；**代码未保留任何重做痕迹**。
- `tools/mixin-check --branch 26.3` 在还原后的构建上重跑：全部通过。

**若以后再动这块**：先与用户确认"切口处要不要补该格自己的贴图列"（`PageAnimationEdges`
左 2 / 右 3，暗色包 0/0）——用户不接受"普通裁切"，也排斥横向拉伸重采样，这两条是硬约束。

## 2026-09-27（三）：熔炉系替代配方格子改画**材料** + 其余配方书统一**不画微缩配方**（26.3 单分支）

**用户诉求**（两条，明确只改 26.3）：
1. 熔炉类配方书（熔炉 / 高炉 / 烟熏炉）的**替代配方组**浮层里，格子改用**材料**当展示物品，
   代替原版的"微缩配方"；
2. 熔炉类、合成类以外的配方书（模组为自己的功能方块定制的配方书，含**直接调用原版 API**
   拿到原版 `OverlayRecipeComponent` 的那种）**统一不显示微缩配方**——酿造台 / 锻造台已经做到了。

**旧行为为什么不对（根因）**：`alternativerecipes/OverlayRecipeButtonMixin` 的配方书分支把
`mode` 交给**查询窗口的全局状态**算（`RecipeViewerOverlay.isFurnaceMode()`）——没有查询窗口时
恒为 `MODE_CRAFTING`，于是**熔炉书**的组浮层格子按合成书渲染：底板取 crafting_overlay 系，
内容走 `renderSlotItems` 的 crafting 分支（烧炼配方只有 1 个 `Pos` → 材料以 0.375 缩放画在
左上角 6×6 格子里 = 用户看到的那张"微缩配方"）。且同一组里**各变体产物相同、材料不同**，
画产物根本分不出谁是谁。模组自建的配方书则完全没有分派依据，一律按合成书渲染。

**实现**：
- 新增 `util/RecipeBookKind`（`CRAFTING` / `FURNACE` / `OTHER` + `of(book, overlayIsFurnaceMenu)` /
  `current(...)` / `showsMicroRecipe()`）：26.3 原版只有 `CraftingRecipeBookComponent` 与
  `FurnaceRecipeBookComponent` 两个具体子类、`RecipeBookComponent` 本身 abstract →
  "既不是合成类也不是熔炉类"即"模组自建"。组件取 `HoverGhostRecipe.currentBook()`（浮层自己
  没有指回组件的引用）；拿不到组件时按界面菜单兜底（`AbstractFurnaceMenu` / `AbstractCraftingMenu`），
  最后才用浮层自带的 `isFurnaceMenu` 标记。
- `render/PopupRenderer`：`renderAlternativesButton(...)` 新增 `RecipeBookKind kind` 参数；
  `!kind.showsMicroRecipe()` 时走新的 `renderItemOnlyButton(...)`——**一块底板 + 一件展示物品**，
  不画任何配方布局：`FURNACE` → 底板 = 原版 `furnace_overlay` 系（悬停 `_highlighted`）、
  展示物品 = `alternativesCellItem()` 解出的**材料**（`FurnaceRecipeDisplay.ingredient()`，
  逐变体轮循）；`OTHER` → 底板 = BRBE `plain_overlay` 系（与酿造 / 锻造格子同款）、
  展示物品 = 产物。残缺红罩照旧。`CRAFTING` 的三条老规则（悬停完整预览 / 未悬停只画产物 /
  关闭「仅在悬停时显示替代配方」仍画完整配方）一行未动。
- `mixins/alternativerecipes/OverlayRecipeButtonMixin`：配方书分支按 `kind` 分派，
  `bookMode = kind == FURNACE ? MODE_FURNACE : MODE_CRAFTING`（**不再读查询窗口的全局态**，
  顺手修掉"熔炉书被当合成书渲染"这一层）；格子 tooltip 的 `brbe$drawnProduct` 改取
  `PopupRenderer.alternativesCellItem(entry, selIdx, kind)`——与锻造台配方格同一条约定：
  **画着什么，tooltip 就写着什么**（熔炉格子现在写的是材料名）。
- 删掉因此失去唯一调用者的 `PopupRenderer.displayedResult(...)`（死代码）。
- 未新增 mixin 类 → 六份 mixin 注册表均无需改动。

**验证方法**：① 熔炉 / 高炉 / 烟熏炉书里右键展开任一替代配方组 → 每格 = `furnace_overlay` 底
（悬停高亮面）+ **材料**图标，格与格靠材料区分，不再出现微缩配方；② 工作台 / 背包（合成类）的
组浮层行为与改动前**完全一致**（「仅在悬停时显示替代配方」开关仍生效）；③ 装了自建配方书的
模组时，那种书的组浮层格子只画产物图标、悬停只有高亮，不出现任何配方布局；④ 格子 tooltip
与格上画的那件物品一致。

**构建部署**：`compileJava` / `build` 通过；javap 核对部署 jar（`RecipeBookKind` 三个枚举常量、
`renderItemOnlyButton` / `alternativesCellItem` / `materialVariants` 均在；mixin 内已引用
`RecipeBookKind.current`，`renderAlternativesButton` 描述符末尾多出 `RecipeBookKind`）。
原子替换部署 26.3 实例（备份 `mods/brbe-ava-fabric-26.3-2.3.1.jar.bak.20260927-0350`，
新 jar md5 `f534f28245170dc52674386904d7df2d`）。

## 2026-09-27（四）：锻造台 / 酿造台支持「优化原版配方过滤器」（26.3 先行，随移植同步其余分支）

**用户诉求**："再为锻造台和酿造台加上「优化原版配方过滤器」功能的支持"——该配置项即
`partialCraftingEnabled`（GUI 标题「优化原版配方过滤器」，tooltip「移除仅显示可合成按钮，
可合成物品将始终置于首页」），此前只对**原版书**生效（`mixins/DisableCraftableFilter`
把原版过滤按钮隐藏并强制 `isFiltering=false`，管线 Stage 4 恒排序）。

**实现（三处收口，与自研书的过滤语义对齐）**：
- `api/BRBBookSettings`：
  - 新增 `partialFilterMode()`（读 `BetterRecipeBook.config.partialCraftingEnabled`，配置未就绪时 false）；
  - `isFiltering(book)` 改为返回**有效**过滤状态——`partialFilterMode()` 时恒 `false`
    （原始按钮状态仍存在 `TypeSettings` 里，只是不再生效）。一处改动即覆盖全部调用点：
    过滤按钮初值、`updateCollections` 的 removeIf、`GenericRecipeButton.getOrderedRecipes`
    的 `filteringSupplier`、锻造 / 酿造替代配方组浮层的取数分支。
- `generic/GenericRecipeBookComponent`：
  - `initVisuals`：`partialFilterMode()` 时 `filterButton.visible/active = false` +
    搜索栏加宽到 97（居中，左右各距书缘 25，与原版书 `DisableCraftableFilter` 同款）；
  - 渲染 / 点击两处按 `visible` 守卫（隐藏即不画、不吞点击）；`toggleFiltering()` 在
    partial 模式下恒归零并返回 false（防陈旧状态）；
  - `updateCollections`：过滤只在 `filtering` 为真时执行；排序条件改为
    `filtering || partialFilterMode()`——**按钮隐藏但恒排序**，即"可合成物品始终置于首页"；
  - `brbe$sortCraftableBeforePartial` 从两段升为**三段稳定分段**：可合成 → 残缺 → 其余
    （与原版书 `CollectionPipeline.applyPartialSort` 同语义；`atleastOnePartiallyCraftable`
    不受 `partialMarkingEnabled` 影响，故关闭"残缺配方"标记时排序仍可用）。

**效果**：开启该配置 → 锻造台 / 酿造台配方书不再有过滤按钮，配方按 可合成 → 残缺 → 其余
排列，首页恒定是可合成项；关闭 → 原行为（按钮在、可按"只显示能做的"过滤）。
酿造 / 锻造替代配方组浮层的取数随之走 `getDisplayRecipes(false)`（全量），与配方区一致。

**构建部署**：`compileJava` / `build` 通过，已原子替换部署（备份
`mods/brbe-ava-fabric-26.3-2.3.1.jar.bak.20260927-0420`，md5 `4e26a212665e45e325748ed8a48fb40b`）。
`tools/mixin-check --branch 26.3` 全部通过。同轮已按用户指示移植到 26.2 / 1.21.11 / 1.21.1
（见各分支 CLAUDE.md 同轮记录）。

## 2026-09-27（五）：锻造台 / 酿造台搜索两处缺陷修复（NPE 空指针 + 纹饰组按模板名查不到）

**用户反馈**：① 在锻造台 / 酿造台配方书里搜索时**必须切到其他标签才会刷新搜索结果**；
② 锻造台的**纹饰（trim）组**无法通过搜索**模板名**找到——用户指出原版机制是"只能通过搜索
替代配方组**内的配方**来查找这个替代配方组"，原版没有"替代配方组特殊展示物品"这回事。

**缺陷一根因（搜索不刷新）**：`GenericRecipeBookComponent.lastSearch` 声明为
`private String lastSearch;` **从未初始化**（null），`checkSearchStringUpdate()` 里
`this.lastSearch.isEmpty()` 直接抛 NPE。实机日志（`26.3-Fabric/logs/latest.log`）实锤：

```
NullPointerException: Cannot invoke "String.isEmpty()" because "this.lastSearch" is null
  at GenericRecipeBookComponent.checkSearchStringUpdate(GenericRecipeBookComponent.java:462)
  at GenericRecipeBookComponent.charTyped(...:447)  ← BrewingStandScreen.charTyped
  at net.minecraft.client.KeyboardHandler.charTyped(KeyboardHandler.java:603)
```

该异常被原版 `KeyboardHandler` 捕获（`wrapOperation$...invokeCharTypedEvents`）——只写日志、
**不崩游戏**，于是"输入框有字、配方区停在旧结果"；切标签走 `updateCollections(...)`
（不经过 checkSearchStringUpdate）→ 搜索词这才被应用，正是用户描述的现象。
**修复**：字段初始化 `= ""`，并在 `checkSearchStringUpdate` 内加 `previous = lastSearch == null ? "" : lastSearch`
判空兜底（三处 null 引用一并消除）。

**缺陷二根因（纹饰组按模板名查不到）**：BRBE 的搜索谓词只匹配**产物物品名**
（`query.matches(result, cache)` → `TextArgument` 走 `getHoverName()`）。而**原版**配方书搜索
走 `SessionSearchTrees.recipes()`——`FullTextSearchTree` 的键函数是
`RecipeCollection.getRecipes() → RecipeDisplayEntry.resultItems(...) → getTooltipLines(..., TooltipFlag.NORMAL)`，
即**产物物品的全部 tooltip 行**。纹饰产物的 tooltip 含 `ArmorTrim.addToTooltip` 无条件加的三行
（javap 核对：无 `isAdvanced()` 门槛）——"盔甲纹饰升级" + **纹饰名（海岸）** + 材质名，
所以原版能按模板/纹饰名查到整组，BRBE 查不到。
**修复（对齐原版语料，不发明"组级搜索键"）**：
- `SearchCache` 新增 `tooltipFallback` 开关（默认关——tooltip 生成有开销）；
- `TextArgument.matches`：名字未命中且开关打开时，再在 `cache.getTooltipText(stack)` 全文里找一次
  （拼音分支同款复用，抽成 `matchesText(haystack, needle)`）；
- `GenericRecipeBookComponent.updateCollections` 的搜索谓词 `cache.setTooltipFallback(true)`
  ——**只给自研书**（锻造 / 酿造语料小，几十个集合）。原版书（合成 / 熔炉）的管线搜索**未动**：
  那里集合 × 条目上千，逐条建 tooltip 的开销不可接受；若也要同样能力，应做成"名字全不命中时
  再跑一遍 tooltip"的两趟式。
- 效果：搜"海岸盔甲纹饰"/"海岸"/拼音 `haian` → 纹饰组保留；单元格展示的纹饰模板名因此可搜
  （**展示仍是模板，未改**）。

**验证方法**：① 锻造台 / 酿造台输入框逐字输入 → 结果**实时**刷新（不再需要切标签）；
② 锻造台"纹饰"页搜"海岸"（en 客户端 `coast`、拼音 `haian`）→ 该纹饰组留在列表；
③ 清空搜索 → 恢复完整列表与浏览页码；④ 日志不再出现 `charTyped` / `keyPressed` 的 NPE。

**构建部署**：`compileJava` / `build` 通过；javap 核对部署 jar（`SearchCache.tooltipFallback`、
`TextArgument.matchesText`）。原子替换部署 26.3 实例（备份
`mods/brbe-ava-fabric-26.3-2.3.1.jar.bak.20260927-0550`，
md5 `887d8eb6cf686a7345d4d29cf620f771`）。`tools/mixin-check --branch 26.3` 全部通过。
同轮已移植到 26.2 / 1.21.11 / 1.21.1（26.2 / 1.21.11 连 NPE 一起修；1.21.1 的
`checkSearchStringUpdate` 没有那句 `lastSearch.isEmpty()`、本就没有该 NPE，只补字段初始化 +
原版语料对齐）。

## 2026-09-27（六）：替代配方组「按排序原因剥离成子组」通用机制（多层嵌套）

**用户诉求**：替代配方组里"通过**任何**方式需要调整排序"的变体（pin / 可合成 / 残缺 / 搜索命中）
都要从原组**剥离出来**；同一父组剥出来的多个变体合成一个**子组**；要兼容**多层嵌套**
（某个组同时是父组和子组）。并指出原版是"拖家带口"（搜"木栅栏"给整组、组内一个变体变可合成
就把整组前移），BRBE 要更细的粒度。

**用户拍板的四个岔路口**：① 原版书 + 自研书都做；② 搜索时原组隐藏、只展示命中的子组；
③ 子组按自身类别参与正常排序（pin 子组仍置顶）；④ 触发原因 = pin / 可合成 / 残缺 / 搜索命中。

**实现（通用核心 + 两处接线）**：
- 新增 `util/SortCategory`（NORMAL < PARTIAL < CRAFTABLE < PINNED）与 `util/RecipeExtraction`
  ——**类型无关**的剥离核心，`Plan<C,E>` 提供 entries / category / visible / subset /
  onPacksCreated：**基线 = 组内最低类别**（`ANCHOR_IS_MIN`），基线变体留在原组且位置不变，
  比基线高的**每个类别各成一个子组**（同类别多个变体合并成子组、单个则单配方组），
  子组**递归**再剥离（`MAX_DEPTH = 6`）；搜索未命中的变体不可见（原组可能被整体丢弃）。
- **原版书**：`CollectionPipeline.applySortExtraction(...)` = 新 **Stage 2.5**，**取代**原 Stage 6
  的 pin 专用剥离（`applyPinCopyGroups` 与 `PIN_COPIES` 弱集合一并删除）。位置在 Stage 2
  （展开）之后、Stage 3/4（pin 置顶 / 可合成→残缺排序）之前 —— 子组因此天然按自己的类别归位；
  `applySearch` 与新 stage **共用** `entryMatches(...)`（判据漂移会让"整组被保留、组内却没有
  命中变体"→ 整组消失）；新组（含重打包的原组）经 `onPacksCreated` 立刻重放残缺标记
  （`brbe$reapplyPartialMarking`，集合对象身份标记的老坑）；末尾 Stage 6b 保留（缓存命中时
  快照里的子组可能在 `invalidateCaches()` 后丢标记）。
- **自研书（锻造/酿造）**：`GenericRecipeBookCollection.subset(List<R>)` 抽象方法 + 两本书各自
  协变实现；`GenericRecipeBookComponent.brbe$extractBySortReason(...)` 用同一 `RecipeExtraction`
  核心（pin 判据 `pinnedRecipeManager.pinned.contains(id)`、可合成/残缺用
  `isCraftable(recipe, slots)` / `getPartiallyCraftableRecipes(slots)`），接在搜索过滤之后、
  既有排序之前；搜索判据抽成 `brbe$recipeMatchesSearch` 供两处共用。

**保留的既有行为**：全 pin 组不重打包（原组即 pin 组形态）；部分 pin → 剥离 + Stage 3 置顶；
1 个 pin 独立成组 / ≥2 个成组（本机制的自然结果）；取消 pin 后回归原组。

**已知取舍（改一行可切换）**：基线取"最低类别" = 尽可能细的粒度 —— "4 个可合成 + 1 个不可合成"
也会拆成 子组(4) + 单配方(1)。若要"多数派留在原组"，把 `RecipeExtraction.anchorOf` 换成多数
类别即可（类注释里写明了这个取舍）。

**构建部署**：编译 / 构建通过；`tools/mixin-check --branch 26.3` 全部通过；原子替换部署
（备份 `mods/brbe-ava-fabric-26.3-2.3.1.jar.bak.20260927-0640`，
md5 `1bd348d5314aa94397d423c3767a01b9`）。同轮移植 26.2 / 1.21.11（见各分支记录）；
**1.21.1 未移植**（RecipeHolder 模型，逐条 API 需重写，等本机制在 26.3 实测通过后再适配）。

## 2026-09-27（二）：排序原因剥离修复 —— 残缺变体不再与可合成变体并组（三分支同步）

**用户实测反馈**："我在26.3测试发现，残缺配方还是会和可合成配方并在一起"（上一轮 Stage 2.5
排序原因剥离机制上线后）。

**根因（新写的 category 判据优先级反了）**：`applySortExtraction` 的 `category(...)` 先判
可合成、再判残缺，而**残缺配方会被注入 craftable 集合**——
`mixins/incompletecrafting/RecipeBookComponentMixin` 的 "Inject partial recipes into craftable
set" 段（该段在管线之前执行：它挂在 `collections.removeIf(...)` 的 `@Redirect` 上，早于
`RecipeBookPage.updateCollections` 的 `@Redirect` = 管线入口），因此
`collection.isCraftable(partialId) == true`。于是组内"真可合成"与"残缺"两个变体都被归到
`SortCategory.CRAFTABLE` → 合并进**同一个子组** → 界面上仍是一个按钮轮循两种状态
（"并在一起"）。★ 代码里所有既有"真可合成"判据用的都是 **partial 优先**：
Stage 4 的 `categorizeEvenIfStale`（`if (partial) … else if (isCraftable)`）、
`tools/brbe-screen-selftest/BrbeOrderProbe`、`RecipeViewerOverlay.viewerKindRank`
（`craftable && !partial`）—— 只有新写的剥离 stage 反过来。

**修复**：`category(...)` 把残缺判定提到可合成之前（pin 保持绝对最高优先）：
`PINNED → PARTIAL(isPartiallyCraftableEvenIfStale) → CRAFTABLE(isCraftable) → NORMAL`。
必须与 Stage 4 完全一致，否则"剥离分组"与"排序桶"会互相矛盾（同一集合被判成两类）。
两处实现同步改（各加注释说明为何不能反）：
- `util/CollectionPipeline.applySortExtraction`（原版书；真正的 bug 所在）
- `generic/GenericRecipeBookComponent.brbe$extractBySortReason`（自研书；其 `isCraftable` 与
  `getPartiallyCraftableRecipes` 当前互斥，属一致性/防回归改动，行为不变）

**效果**：{可合成, 残缺} 的组稳定拆成两组 —— 残缺变体独立成按钮并被 Stage 4 排进残缺区，
可合成变体独立成组排进可合成区，不再并组轮循；全残缺组/全可合成组行为不变。

**验证方法**：找一个组内既有可合成变体、又有残缺变体（缺材料的 1:1 变体，如某一色木板/
某一色羊毛）的替代配方组 → 应为**两个按钮**，一个在可合成区、一个在残缺区（红罩），
而不是一个按钮轮循两种状态。

**构建部署**：`build` 通过；`tools/mixin-check --branch 26.3` 全部通过；javap 核对**部署 jar** 内
`CollectionPipeline$1.category` 的调用序 = `isPinnedEntry → isPartiallyCraftableEvenIfStale →
isCraftable`（偏移 27 / 42）；原子替换部署（备份
`mods/brbe-ava-fabric-26.3-2.3.1.jar.bak.20260927-0522`，md5 `04a3bffef48eee8b3c35cf84153f2c6f`）。
同轮同步 26.2 / 1.21.11（见各分支记录）；1.21.1 仍未移植剥离机制（RecipeHolder 模型待适配）。

## 2026-09-27（三）：幽灵配方收尾 —— 工作区已摆好该配方时悬停不再预览（四分支同步）

**用户诉求**：「为自动填充幽灵配方功能做一个收尾——当工作区已经摆放好了某个可合成配方的
配方，此时再悬停该配方的话就不会尝试展示幽灵配方了。」

**为什么必须收尾**：悬停预览的写入与显示是两件事——① 用原版 `fillGhostRecipe` 把幽灵写进
工作区；② `previewing` 让 {@code hoverghost/AbstractContainerScreenSlotMixin} **把工作区真实
物品整片藏掉**（2026-09-26 的诉求）。于是"配方已经摆好"时悬停，摆好的实物被藏起来、换成一份
一模一样的幽灵——看起来像"我的材料变成了幽灵"；而这份幽灵其实**一格也补不进去**。

**实现（判据取自原版刚写好的那份幽灵）**：`install()` 之后立刻回读 `GhostSlots` 里原版写入的
条目，逐条与工作区实物比对（`HoverGhostRecipe.satisfies`：物品相同、数量不少于候选、候选带
组件时组件也一致）：
- 全部**材料**条目都已被满足 → **本次不预览**：撤销这次写入（还原快照）、**不置 previewing**
  （真实物品照常显示）、记 `suppressed` 并保留 `hoverOwner/shown` 身份（同一按钮上不再每帧重算）；
- **结果槽条目不参与判定**：熔炉的结果槽要等烧炼完成才是满的，算进去会让"输入+燃料已摆好"的
  熔炉永远判不出"已摆好"；全是结果条目（没有材料）也不算已摆好；
- 判据不用自己按配方类型重写映射——槽位映射就是原版 {@code fillGhostRecipe} 产出的
  （造型走 `PlaceRecipeHelper`、无序按序对齐、熔炉输入+燃料…）。
- `endPreview()` / `invalidate()`（点击放置 / 服务端回包接管）清 `suppressed`，下一帧按新的
  工作区内容重判。

**新 accessor**：`mixins/accessors/GhostSlotAccessor` —— `GhostSlots$GhostSlot` 是**包私有
record**，用 `@Mixin(targets=...)` + `@Invoker("items")` / `@Invoker("isResultSlot")` 读候选物品
与"是否结果槽"（已注册进 `mixins.brbe-common.json`；26.2/1.21.11 同）。

**自研书（锻造/酿造）**：`GenericGhostRecipe.isLaidOutInWorkspace(menu)` 同判据；
`GenericRecipeBookComponent` 的悬停心跳改为 `setGenericPreviewing(hovered != null && !suppressed)`
并在悬停命中时跳过 `setupHoverGhost`——那边 `isPreviewing()` 还控制"忽略幽灵渲染谓词"，
不一起关掉的话，幽灵谓词只画空槽位 → 工作区会整片空白。

**同批（本轮前段）**：`applySortExtraction` 残缺优先于可合成的判据修复（见上一条）也包含在本次
构建里。

**构建部署**：`build` 通过；`tools/mixin-check --branch 26.3` 全部通过；javap 核对 `@Invoker`
值（26.3/26.2 = `items`/`isResultSlot`，1.21.11 remap = `comp_2983`/`comp_2984`，与既有
`GhostSlotsSetSlotAccessor` 的 `method_64873` 同一机制）；原子替换部署（备份
`mods/brbe-ava-fabric-26.3-2.3.1.jar.bak.20260927-0605`，md5 `b5e9bbd17c215cf4cfc22fc5d3b3830f`）。

## 2026-09-27（四）：点击放置后真实物品消失修复 —— 预览当场结束并"交接给原版"（四分支同步）

**用户实测反馈**：「点击可合成配方进行真实物品填充时，真实物品会消失（鼠标没离开单元格）；
拿开鼠标再移回才显示正常（此时不展示幽灵配方）。」

**根因（26.3 字节码核实，两段）**：
① 客户端 `RecipeBookComponent.tryPlaceRecipe`：先 `ghostSlots.clear()` 再发 `handlePlaceRecipe`
包（**本地不写回幽灵**）；
② 服务端 `ServerGamePacketListenerImpl.handlePlaceRecipe`：只有 `RecipeBookMenu.handlePlacement(...)`
返回 `PostPlaceAction.PLACE_GHOST_RECIPE`（材料不齐）时才回 `ClientboundPlaceGhostRecipePacket`
——**材料齐全时没有幽灵回包**（真实物品直接进工作区）。
于是点击后"幽灵图空 + 悬停态仍算 `previewing`"（`invalidate()` 此前只置 `overridden`——它管的是
"别用旧快照覆盖"，并没有结束预览本体）⇒ `isPreviewing()` 恒真 ⇒ 工作区真实物品被
`hoverghost/AbstractContainerScreenSlotMixin` 全藏掉 ⇒ 看起来"物品消失"；而 `hover()` 的
身份短路（同按钮同配方直接 return）让状态**永不重判**，只有鼠标移开（`release`）才恢复。

**修复（`util/HoverGhostRecipe`）**：
- `invalidate()`：**预览当场结束**（`previewing = false`、`activeBook = null`；不还原——幽灵所有权
  已在原版手里），并记交接：`handedOver = true` + `handedOverDisplay = shown`；
- `hover()` 短路改为：`handedOver && (同一格 || 同一配方 handedOverDisplay == display)` → 直接
  return（"同一配方"这一条覆盖"点击后配方页因槽位变化重建按钮对象"的情形，否则交接会失效、
  预览被重新装回去）；命中时顺手续上 `hoverOwner/shown` 身份；
- `endPreview()` 清 `handedOver`/`handedOverDisplay`（换目标或移开 → 恢复常规预览与抑制判定）；
- 新增 `isHandedOver()` / `markHandedOver()` / `clearHandedOver()` 供自研书使用。

**自研书（锻造/酿造）**：`brbe$showPlacedGhost`（材料不齐 → 写原版风格缺料引导）与
`brbe$clearPlacedGhost`（材料齐 → 真实放置）两处登记交接；`brbe$updateHoverGhost` 的心跳与预览
分支都排除交接态，且"**同一条配方的等价对象**"（`brbe$sameRecipeId`，页面重建/同格换实例）**不清
交接、不清工作区幽灵**——否则点击后的第一帧会把刚放好的真实物品又藏起来、或把刚写好的引导清掉。

**语义结果**：点击后 = 原版语义（材料齐：真实物品原样显示、无幽灵；材料不齐：原版缺料引导 +
真实物品可见）；鼠标不动不再变化；移开再移回 = 按"工作区已摆好"规则走（已摆好 → 不预览；
没摆好 → 照常预览）。

**构建部署**：`build` 通过；`tools/mixin-check --branch 26.3` 全部通过；javap 核对**部署 jar**内
`HoverGhostRecipe.invalidate()` 已写入 `previewing/activeBook/snapshot/suppressed/handedOver/
handedOverDisplay`、`hover()` 短路为 `handedOver && (sameTarget || handedOverDisplay == display)`；
原子替换部署（备份 `mods/brbe-ava-fabric-26.3-2.3.1.jar.bak.20260927-1426`，
md5 `58dd7db7c1e72d5463c63e70edb410bb`）。

## 2026-09-27（四）：版本号 2.3.1 → **2.3.1-beta.1**

用户要求：1.21.11 / 26.2 / 26.3 三个分支的版本号改为 **2.3.1-beta.1**（1.21.1 保持 2.3 不动）。

- 每分支两处（与 2026-09-22（三）同款）：`gradle.properties` 的 `mod_version=2.3.1` → `2.3.1-beta.1`；
  `src/main/resources/fabric.mod.json` 的 `"version"` —— 该字段是**硬编码**，
  `processResources` 只做文件排除、不做 `${version}` 替换，必须一起改。
- 产物名变为 `brbe-ava-fabric-<mc>-2.3.1-beta.1.jar`；游戏内 mod 列表显示 `brbe 2.3.1-beta.1`。
- 部署（四个实例：26.3-Fabric、26.2-Fabric、26.2-Fabric 0.19.5-for-test、1.21.11-Fabric）：
  新 jar 原子替换；因为**文件名变了**，旧 `-2.3.1.jar` 必须**移出 mods**（同 mod id 两份 jar 并存
  会让 Loader 报重复），已备份为 `*.jar.bak.20260927-1657`，每实例 mods 内现只剩一份 BRBE jar。
- md5：26.3 `17b576f4d5a1ce31bd8c59dd90a33dd0`、26.2 `6c29c16ada0a4cf072346160de8c5d5e`（两实例同）、
  1.21.11 `0f552c9417b9810bb84ed4af5b85408a`；jar 内 `fabric.mod.json` 版本已逐一核对 = 2.3.1-beta.1。
- 本轮只改版本字符串，**无代码变更**（这些 jar 与各自上一版构建除该字符串外一致）。

## 2026-09-27（二）：锻造台打开延迟根因修复 + 幽灵点击空窗期 + 升级配方折叠（用户三条反馈）

**① 打开延迟（已实测确认修复）**。用户："右键锻造台、界面出现的瞬间卡一下，比酿造台/工作台都高"。
探针实测（`[BRBE-PERF]`，汇总脚本 `tools/brbe-openlatency/report.py`）定位到
`updateCollections` 的 `extract`（排序原因剥离）占打开耗时 **93%**：`RecipeExtraction` 对每条配方
问 2~3 次分类，而 `Plan#category()` 每次调 `getPartiallyCraftableRecipes(slots)` 整表重扫
（纹饰组 29 条 × 40 槽位，18 组 → ~9 万次配方判定）；`BRBSmithingRecipe#hasBase` 还把
`getBase()`（ItemStack 复制）写在槽位循环里（每判据 40 次分配）。

修复：新增 `util/RecipeSlotState`（槽位指纹 = 逐槽 `ItemStack.hashItemAndComponents` + 数量 +
鼠标物品；**必须带组件**——酿造输入判据是 `isSameItemSameComponents`，药水区别全在
`POTION_CONTENTS` 里）+ `GenericRecipeBookCollection` 槽位状态缓存（可合成/其余/残缺三个列表 +
`craftableSet`/`partialIds` + 两个集合级布尔，子类只留 `hasMaterials`/`hasPartialMaterials`
两条原始判据）；`hasBase` 的 `getBase()` 提到循环外。渲染路径保留「残缺配方标记」开关语义
（新增 `isPartiallyMarked`，与排序用的 `isPartiallyCraftable` 分开）。

实测（7 次开界面中位/最小/最大，ms）：`screenInit` **69.04 → 10.41**；`extract` **63.96 → 3.57**；
`build` 0.56 → 0.58（本来不是瓶颈）；逐帧 `pageRender` 2.08 → 无 ≥2ms 记录。
用户确认"延迟已经基本修复了"。残留 ~5.8ms 在 `SmithingScreenMixin.init` 的按钮注册段，已加
`widgets=` 探针待定位。详见 `docs/26.3-锻造台打开延迟调查.md`（含完整数据表与 O(n²) 推导）。

**② 自动填充幽灵的"点击空窗期"**。用户：点击可合成配方时，幽灵撤下比服务端把真实物品放进工作区
快一拍，中间工作区是空的。成因（javap 26.3 实证）：`RecipeBookComponent.tryPlaceRecipe` 里
`ghostSlots.clear()` 是**无条件**的（发包之前），而客户端从不预测容器槽位 → t=0 到 t=1~3 tick
之间既无幽灵也无实物；自研书 `handlePlaceRecipe` 开头同样先 `ghostRecipe.clear()`。

修复 = **交接保持**：`HoverGhostRecipe` 新增 `holding/heldDisplay`（`beginPlacementHandover` /
`reinstallHeldGhost` / `tickHandover` / `endHold`，`endHold` 在没有保持时是 no-op 以保持
`invalidate()` 原语义），`hoverghost/RecipeBookComponentMixin` 在 `tryPlaceRecipe` 的 HEAD
（仅 `collection.isCraftable(recipe)` 时进入）与 RETURN（原版 clear 后写回幽灵）各一处注入；
自研书在 `GenericRecipeBookComponent` 里同一套（`brbe$beginPlacementHandover` /
`brbe$tickPlacementHandover`，判定直接用当前幽灵的 `isLaidOutInWorkspace`），并给
`SmithingScreenMixin.slotChanged` 加 `brbe$isHandoverHolding()` 守卫——放置本身就在改槽位，
不挡的话第一件物品落格就把幽灵清掉、退回空窗期。超时兜底 600ms。
详见 `docs/26.3-自动填充幽灵-点击空窗期.md`。

**③ 升级配方折叠成替代配方组（新 UI 行为）**。用户："升级模板的配方也得像纹饰模板那样通过
替代配方组折叠，代表物品是锭而不是模板"。本实例 12 条 `smithing_transform`（斧/靴/胸甲/头盔/锄/
马铠/护腿/鹦鹉螺铠/镐/锹/矛/剑）共用 `netherite_upgrade_smithing_template` 与
`#minecraft:netherite_tool_materials`（= 下界合金锭，26.3 的标签只有这一项）。
`SmithingRecipeBookComponent#brbe$groupKey` 按「模板物品串 + 加成材料物品串」折叠（无 `entry.group()`
时生效；纹饰配方不在此折叠——原版一条纹饰本身就展开成整组）；`SmithingRecipeButton` 的
"纹饰模板代表"泛化为**折叠组代表**：纹饰组取模板（既有行为），升级组（≥2 条）取**加成材料（锭）**；
左键屏蔽、悬停不预览、右键展开选部位（与纹饰组同一套规则）；组内顺序按产物物品 id 字典序
（known 是 Map，迭代顺序跨会话不稳定）。

**部署**（26.3-Fabric，原子替换）：探针版 `5c06a2f7…`（基线，备份 `20260927-1817`）、
修复版 `4cfe4fe4…`（延迟+幽灵，备份 `20260927-1823`）、本版 `b49604f2…`（+升级折叠，
备份 `20260927-1855`）；修复前的干净 2.3.1-beta.1 在 `*.bak.20260927-1739`。
mixin-check 26.3 全部通过。**探针（`util/BrbePerf` 及各 `BRBE-PERF` 调用点）待用户验收后移除。**

## 2026-09-27（三）：自研书替代配方组的固定（pin）规则修复（用户反馈）

用户："锻造台和酿造台的替代配方组还有诸多缺陷：① 替代配方组能够被 pin；② 替代配方组里的配方
无法被 pin（会直接穿透替代配方组 pin 到下面的配方）。根据 BRBE 的设定：替代配方组不可以被 pin，
替代配方组里的配方可以被 pin。"

**对照**：工作台原版书早已是对的（`mixins/pins/AbstractContainerScreenMixin#onKeyPressed`：
浮层可见 → 只 `toggleFavourite(entry)` 悬停的变体、没悬停就吞掉按键；网格 → 只有
`collection.getRecipes().size() == 1` 的单配方格能 pin）。自研书走的是另一条路
（`GenericRecipeBookComponent#keyPressed`），它**只看网格按钮**且固定的是**集合**
（`addOrRemoveFavourite(collection)` 把组内全部 id 写进 `brbe.pins`）→ 组被整体 pin；
浮层打开时网格悬停被 `suppressGridHover()` 压掉，但浮层不参与固定键判定 → 按键落到浮层下面的那格、
穿透 pin 到组。

**修复**：
- `GenericRecipeBookComponent`：固定键分支改为 `brbe$togglePinUnderCursor()` —— 浮层可见时只固定
  `recipesPage.hoveredRecipe`（页面 render 已同步浮层里悬停的变体）、没悬停变体则吞键；网格只有
  单配方格（`getRecipes().size() == 1`）能固定，多变体组吞键；隐藏按钮跳过（悬停字段陈旧，与原版书同）。
  实际切换走新增的 `brbe$togglePinRecipe(recipe)`（`pinnedRecipeManager.toggleFavourite(recipe)` +
  `updateCollections(false)` + 点击音效）。
- `PinnedRecipeManager#toggleFavourite(GenericRecipe)`（新重载）：键 = `recipe.id()`，与既有的
  `toggleFavourite(RecipeDisplayEntry)` 同义；`addOrRemoveFavourite(集合)` 保留但已无调用者，
  javadoc 标注"别再接回固定键"。
- 组浮层里的 pin 反馈：`SmithingOverlayRecipeComponent.OverlayRecipeButton` 与
  `BrewingOverlayRecipeComponent.RouteButton` 渲染末尾补画 `RECIPE_BOOK_PIN_SPRITE`（原版书浮层由
  `mixins/pins/OverlayRecipeButtonMixin` 画，自研书浮层此前没有）。

**已知取舍（待用户确认）**：`BrewableResult#id()` 是**产物药水 id**（既有设计：同一瓶药水的不同路线
id 相同、与 pin 标识同源）→ 酿造台 pin 一条路线等于 pin 这瓶药水（两路线一起剥出来、组按钮带图钉）。
若要逐路线独立 pin，需换逐路线 id（会改 `brbe.pins` 的键，旧文件需迁移）。

**部署**：26.3-Fabric 原子替换 md5 `3f10b865658039ea89775335b888a7f3`（备份 `20260927-1959`，
上一版 `b49604f2…`）；mixin-check 26.3 全部通过。详见 `docs/26.3-替代配方组pin规则修复.md`。
探针仍在内（待用户验收后移除）。

## 2026-09-27（四）：组浮层不刷新导致 pin 后"留在原位的镜像"（用户反馈，接（三））

用户："pin 替代配方组里的配方时，这个配方虽然表现基本正常，但会留下一份镜像在原位置（替代配方组内），
除非重新打开替代配方组否则不会恢复。"

成因：组浮层持有的是**打开那一刻的快照**（`allRecipes`/`recipeButtons` + 所属集合对象）。pin 切换会重跑
管线：被 pin 的变体从原组剥出单独成格，原组被 `RecipeExtraction` 的 `plan.subset(...)` **重新打包成新对象**
→ 页面按钮重建，但浮层没有任何刷新通道 → 仍显示旧快照（含已被剥走的变体）；"重开组恢复"正是重开会用新对象
`init`。

修复（`GenericRecipePage`）：右键打开组时记下该组 **id 快照**（`brbe$overlayGroupIds`）；`setResults(...)`
末尾 `brbe$refreshOpenOverlay(list)` —— 浮层可见时按 **id 交集最大**找到"同一个组"的新对象（剥离后原组保留
其余变体，交集最大的一定是它；剥出去的那格只交集一个 id），用新列表里对应按钮坐标就地 `initOverlay(...)`
重建；整组没了则 `hideOverlay()`。选择"就地重建"而非"关闭浮层"是为了能连续 pin 多个变体。

**工作台（原版书）同类隐患未改**：原版 `OverlayRecipeComponent` 同样只有 `init` 一条重建通道，BRBE 的 pin 走
`updateCollectionsInvoker` → 管线重跑 → 原版浮层同样会留旧快照；但查询窗口也复用同一个原版浮层，贸然加
"页面更新即重建"有风险，待用户在工作台实测确认后再定。

**部署**：26.3-Fabric 原子替换 md5 `0a80ac65ae71e2c7f9d323ca18f3f28d`（备份 `20260927-2019`，
上一版 `3f10b865…`）。详见 `docs/26.3-替代配方组pin规则修复.md` §3。

## 2026-09-27（六）：LEI 锁定（预览/pin）修复 —— JEI 委托路径的逐槽位命中判定

用户：查询窗口对象网格锁定正常；**预览弹窗 / pin** 里按住 Alt 只锁得住产物，指针停在**材料**上时材料
"抽搐"；合成类别（BRBE 定制界面、不走 JEI 委托）正常。

`BRBE-CYCLE` 追踪（26.2 实测）实锤：`applyCycleLock` 用 `slot.getAreaIncludingBackground()` 当命中矩形，
而本环境里多个槽位的该区域**起点相同、只有宽度不同**（rect 全是 308,163，宽 36/54/108）→ 最宽的永远命中、
指针下的窄槽永远不被冻结（材料继续由 JEI 轮循器推进 = 抽搐），按钮层那次冻结只影响产物。

修复：改用 JEI 自己的 `IRecipeLayoutDrawable#getSlotUnderMouse(double,double)`（与它的 tooltip 同源；
反编译 `RecipeLayout#getSlotUnderMouse` 确认坐标系：先减 `area` 原点再逐 widget 判定，而 drawable 被
`setPosition(0,0)` → 内容局部坐标 = (屏幕 − 内容原点)/fit）；只有它判定为指针下的槽位进入冻结，
`CycleLock.claim(slot, cursorX, cursorY, 1, 1)` 仅用于把该件登记给滚轮 `step()`。

部署：26.3 `972cc85ec26d41c342d852c9d75bd8d0`（备份 20260927-2341）、26.2 `4dd97f2e…`、1.21.11 `d0047709…`。
`BRBE-CYCLE` 追踪暂留，复测确认后摘除。详见 `docs/26.3-自动填充幽灵-点击空窗期.md` 附录。

**2026-09-28（一）：锻造台/酿造台配方书三项修复**（用户 09-27 晚反馈，26.3 为实现分支）

三条症状与根因（完整记录见 `docs/26.3-替代配方组pin规则修复.md` §6~§9）：

1. **升级组只看加成材料**：`SmithingRecipeBookComponent#brbe$groupKey` 由
   `"upgrade:" + template + "+" + addition` 改为 **`"upgrade:" + addition`**——用户规则："升级模板配方
   只要锭相同就合在一个组里"。BetterEnd 的下界合金锻锤用自家升级模板 + 下界合金锭，早先按「模板 + 材料」
   分组时它单独成一格（像"漏了一组"）。`SmithingRecipeButton#brbe$resolveGroupRep` ② 同步把
   "同一模板 + 同一材料"收窄为**只要材料相同**（`requiresTemplate()` 仍要求为真）。
   字节码核对：jar 内 `brbe$groupKey` 只剩 1 次 `brbe$displayItemsKey` 调用。
2. **剥离子组不再用折叠代表物品**：pin 出来的**单条纹饰配方**同样满足"整组都是纹饰配方 + 共用模板"→
   那一格画的是**纹饰模板**（= 原组的图），看不出 pin 的是哪件装备。新增
   `GenericRecipeBookCollection#brbe$extractionSubgroup` 标记（`isExtractionSubgroup()` /
   `markExtractionSubgroup()` / `subset()` 里 `brbe$inheritExtractionState`）+ 新回调
   `RecipeExtraction.Plan#onSubgroupPack`（**只对子组触发**；重打包的原组仍是原组，继续显示模板/锭）；
   `brbe$resolveGroupRep` 开头对子组直接返回 null。连带修好：单条纹饰格不再被当折叠组
   （左键可放置、悬停有幽灵预览）。
3. **组浮层刷新只跟随"自己那一格"+ 位置/页码不动**：`brbe$refreshOpenOverlay` 原按"id 交集最大"找组，
   ① 并列（原组只剩 1 条时输给刚 pin 出来的格）→ 浮层内容跳变；② 吞并（pin 子格全解除 pin 后交集命中
   父组）→ **浮层突然变成父组的**；且刷新用网格按钮坐标重新落位 + 页码复位 → "界面乱动"。现在：右键时
   记下落点（`brbe$overlayAnchorX/Y`）并**沿用**；浮层这一格是全 pin 格时只接受"本格的子集"候选
   （本格散开 → 收起浮层，不再改开父组）；交并列时优先"还有未 pin 变体"的那一格；新增页面钩子
   `GenericRecipePage#refreshOverlay(...)`，`SmithingRecipeBookPage` 覆写为
   `SmithingOverlayRecipeComponent#refresh(...)`（**保页码**，按新页数钳制）。

部署：26.3-Fabric 原子替换 md5 `77820dc0478f3ad1dd0d749c0b9a3cf0`（备份 `20260928-004219`），
mixin-check 全部通过。26.2 / 1.21.11 同步移植（见各分支 CLAUDE.md）。
`BRBE-GHOST` / `BRBE-CYCLE` 临时追踪**仍保留**（ghost 侧还有 1Hz 采样、LEI 锁定用户称"暂时解决"），
用户确认稳定后一并摘除。

**2026-09-28（二）：锻造台去掉「搜索」标签页**（用户当日追加诉求：只留升级模板 / 纹饰模板两页）

- `api/BRBBookCategories#createUnlistedSearch()`（新）+ `util/BRBHelper.Book#createUnlistedSearch()`：
  创建类别但**不登记进 `getCategories(book)`**（标签列表的唯一来源）——
  字节码核对：该方法只有 `new Category(...)`，对照组 `createSearch()` 里能看到 `createCategory` 调用。
- `config/AppContext#ensureCategories`：`smithing.createSearch()` → `smithing.createUnlistedSearch()`。
- 搜索类别对象**保留**：锻造台组浮层用 `SMITHING_SEARCH` 当 `getResult(registryAccess, category)`
  的类别参数（锻造配方产物与类别无关）；`SmithingRecipeBookComponent#shouldInclude` 的
  "搜索页 = 全部配方"分支保留（不再有该标签页 → 不可达，留作将来需要时恢复）。
- 顺带更新 `GenericRecipeBookComponent#drawTooltip` 里"搜索页'搜索'"的过时注释；
  设计蓝图（`BRBE-功能设计蓝图.md` / `BRBE-前端工程设计蓝图.md`）标签页表同步。
- 部署见本轮报告（四分支六实例，备份 `20260928-005343` / 1.21.1 复部署 `20260928-005405`）。

**2026-09-28（三）：锻造台盔甲架预览残留修复**（用户当日反馈："悬停展示配方后盔甲架上的装备模型无法移除"）

- 原版语义（javap 反编译 `SmithingScreen`）：`slotChanged(menu, 3, stack)` → `updateArmorStandPreview(stack)`
  —— **盔甲架 = 结果槽物品**，只在结果槽变化时刷新。
- 根因：BRBE 的幽灵每帧把**幽灵产物**推进盔甲架（`GenericGhostRecipe#render` → `onGhostUpdate`），
  但幽灵收起走的是另一条路 `GenericGhostRecipe#clear()`（悬停离开 / 点击引导结束 / 收书 /
  "工作区已摆好"探针都经它）——那里**没有复位**，于是模型一直挂着，直到原版因结果槽变化才刷回。
- 修复：`GenericGhostRecipe` 新增 `setOnGhostRelease(Runnable)` + `releaseExternalPreview()`
  （幂等，`externalPreviewHeld` 标记）；`clear()` 末尾复位；`render` 推进时置标记；
  `GenericRecipeBookComponent#setVisible(false)`（收书 = 幽灵不再显示）也复位一次。
  锻造台组件留住原版的 setter 并注册 `brbe$restoreArmorStandPreview()`（复位值 = 结果槽当前物品，
  这样"工作区摆好真实配方"时回到**真实**产物而不是无条件清空）。酿造台没传 setter → 钩子恒空操作。
- 部署：四分支六实例（26.3 `bd6504a5…`、26.2 `772c02cd…`、1.21.11 `0874829c…`、
  1.21.1-Fabric `34a43dd0…` / NeoForge `f09809e0…`，备份 `20260928-013101`）。

**2026-09-28（四）：纹饰条目补全注入去重键加固（三分支同步）+ 1.21.11 组浮层 tooltip 反压修复**

用户当日两条反馈都在 **1.21.11** 上实测，第二条的加固动到了本分支（详见
`docs/1.21.11-替代配方组tooltip与纹饰副本.md`）：

- **① 组浮层压住组内配方 tooltip**：本分支**本来就对**（`ScreenEvents.afterExtract` 由
  `GameRendererMixin` 包在 `Screen#extractRenderStateWithTooltipAndSubtitles` 外层，
  09-27 已按"顶层那一遍就地画 tooltip + 常规那遍让位"修过）。1.21.11 当时没跟上，本批补齐，
  **26.3 无改动**（无源码变更、无重新部署）。
- **② 锻造台纹饰页每组多一份副本**：1.21.11 实测 `injected (complement): 18 cached, 1440 skipped`
  ——18 条缓存纹饰条目（每个图案一条）被重复注入 `known`，各自成一格、折叠成同样的模板图标
  → 18 组各多一份副本。根因是 `trim:<图案 id>` 去重键在 1.21.11 上取不到
  （`registry.getKey(holder.value())` 按 byValue 身份表反查失败）。三分支统一加固：
  - `CacheableRecipeDisplayEntry` 新增 `trimTemplateItem()`（`templateIngredients` 第一条备选，
    `#tag` 不参与）；
  - `VanillaRecipeCache.collectServerResultItems` 同时产出 `trim:`（优先 holder 自己的
    `unwrapKey()`，失败回退老反查）与 `trimtmpl:<模板物品 id>`（`SmithingRecipeDisplay#template()`
    的 `ItemSlotDisplay`，已 javap 核实单物品 `Ingredient#display()` 走 `displayForSingleItem`）；
    `injectEntries` 两条键命中任一即跳过，并统计 `N of them trim-by-template`；
  - 日志新增 `trim keys: holder=/registryLookup=/templateItem=` 便于复验。
- 部署：26.3 `6aff510c2a6487016cc96f9750ba9ae6`、26.2 `8f8b3a99ed5570db6f74e1f7f6741af9`（两实例）、
  1.21.11 `43749b2b81459900c8e666c3037b2007`，备份 `20260928-120451`；三分支 mixin-check 全部通过。
  本分支行为预期不变（去重键只会更多、不会更少）。

**2026-09-28（五）：新功能「强制合并相同产物的配方」（原版书，四分支同步）**

用户设想：模组给同一件物品写了几套配方却没共用 `group`，配方书里各占一格；希望开关一开，
A（落单）+ B/C（已是一组）合成同一个替代配方组。语义由用户逐条拍板（见
`docs/同产物配方合并.md`）：**只原版书**（合成台/熔炉系）、产物 = **物品 + 组件、忽略数量**、
**取消分组优先**、合并组**仍可被排序剥离**、不限规模、默认关。

- `util/CollectionPipeline` 新增 **Stage 2.6 `applyResultMerge(list, displayContext, remarkPacks)`**：
  逐集合算"统一产物键"（组内产物必须一致，解不出产物/不一致的集合不参与）→ 按键分桶 →
  每个键只在**第一次出现的位置**留一个合并组 → 复用既有 `buildPack(entries, template)` 重建
  （与 Stage 2.5 的重打包同一套）→ 补不兼容标记 → 交给调用方重放残缺标记。
- `mixins/pipeline/RecipeBookComponentMixin`：主路径 + 诊断路径各调用一次（主路径传
  `this::brbe$reapplyPartialMarking`）；**`brbe$configKey()` 加 `mergeSameResult`**
  （否则开关切换不生效——搜索词 / `isFiltering` 已在这条键上栽过两次）。
- 配置 `AlternativeRecipes.mergeSameResult`（默认关、带 tooltip）+ 7 语言两键。
- 部署：26.3 `cfc25dbcd6bae1d458c64494af8886f2`、26.2 `e4c631098b315eeefdca4af8ceab7d09`（两实例）、
  1.21.11 `40c86c304a54a56bf9b82e6edf7484fd`、1.21.1-Fabric `ea6de01514377bc8a3f9ccb09b8caee9` /
  NeoForge `88bc53531fa41a5292dd7db456341495`，备份 `20260928-165523`；字节码核对
  `CollectionPipeline.applyResultMerge`、`AlternativeRecipes.mergeSameResult`、jar 内 zh_cn 新键均在。



**2026-09-28（七）：同产物合并改为"专用收纳格"口径（复制而非移动）**

用户定稿（见 `docs/同产物配方合并.md` §1）：**混合配方组里的同产物配方复制一份进专用收纳格、原组一条不动**；
**非混合格**（独立格/全同产物组）里的同产物配方**搬进**收纳格（唯一非混合格则原地当专用格）；
产物只在一个格子里出现 → 不处理；**两份同时脱离父组时合二为一**（保留专用格那一份）；**搜索时副本不重复显示**。

- `util/CollectionPipeline`：`applyResultMerge(...)` 重写为"产物结构 → 来源集合 → 逐个产物建收纳格"，
  并返回 `MergeResult`（`dedicated` 专用格血统 + `copied` 副本索引，均按集合身份）。
  源集合**不被修改**（搬 = 从输出列表去掉原格）→ 管线反复运行幂等。
- `util/RecipeExtraction`：`Plan#onSubgroupPack` 回调补上父集合参数 `(parent, pack)`。
- `generic/GenericRecipeBookComponent`：`onSubgroupPack(C parent, C pack)`（自研书行为不变）。
- `mixins/pipeline/RecipeBookComponentMixin`：主路径持有 `MergeResult` 并传给 Stage 2.5；
  诊断路径丢弃它（不重放标记）。
- Stage 2.5 三处接线：`subset` 继承血统/副本索引、`onSubgroupPack` 登记剥离子组、
  `visible` 搜索时隐藏副本；提取结束后 `dedupeCopiedExtractions` 把"两边都脱离父组"的副本合一。
- 配置 tooltip 与 javadoc 改写为复制口径（7 语言）。

**数据**（随仓库原版配方集 1007 格 / 40 混合组）：291 种产物跨格 → 243 种有非混合来源（净 −355 格）、
48 种只在混合组里（净 +48 格）→ **合计净 −307 格，混合组成员一条不少**。

## 2026-09-28（二）：同产物合并 —— 修复"同一混合组多个产物互相覆盖" + 新增路线族拼接（已部署）

**用户实测**："多了一个黄色挽具的专用收纳组（奇怪的是只多了黄色组这一个，其他颜色没有），
原版的两个挽具组纹丝不动（预期行为）。"

**① bug（`insertBefore` 下标覆盖）**：`CollectionPipeline.applyResultMerge` 的待插入清单曾是
`Map<格子下标, 条目列表>`，而同一混合组里多个产物（16 色挽具）的"第一个来源"是**同一个下标**
→ `put` 互相覆盖 → 只剩最后一个产物（原版遍历顺序里最后一个颜色恰好是黄色 → `yellow_harness`；
地毯/床同理各剩一个黄色格）。次要症状：`copied` 索引照样登记了另外 15 色的副本 → 搜索这些颜色时
副本在原组里被隐藏（"收纳格已收录"）而收纳格并不存在 → 搜索结果缺失。
**修复**：值改为 `List<List<条目>>`，组装输出时按顺序逐个插入
（`insertBefore.computeIfAbsent(mixed.get(0), k -> new ArrayList<>()).add(entries)`）。

> ⚠️ 更正上一轮的数据：上轮写的"净 −307 格（243 非混合 + 48 混合组）"是**设计语义**的模型值；
> 部署的 jar 因该 bug 实际只有 **−354 格、三族各剩 1 个黄色格**。修复后 = −309 格（三族各 16 格）。

**② 新功能：路线族拼接**（`coalesceRouteFamilies`，Stage 2.6 的**第 0 步**，在 `applyResultMerge` 内）：
互为"平行路线"的两个混合组先拼成**一个**组，产物因此不再跨组 → 不再各建收纳格。判据是**结构式**的
（与 group 命名无关）：两侧产物都可解、各 ≥2 个、**每个产物在各自组内只出现一次**、
**小侧产物全被大侧包含**、交集 ≥2；三条以上路线用**并查集**连锁成一组。拼接 = 字面拼接
（先出现的组的条目在前），位置取族内**最靠前**的那一格；源集合不被修改（幂等），新组并入
`newPacks`（不兼容标记就地补做 + 残缺标记交调用方重放）。
原版命中且**只**命中三对：`harness`+`harness_dye`、`carpet`+`carpet_dye`（18 ⊇ 16，苔藓地毯留在组里）、
`bed`+`bed_dye` —— 39 个混合组 / 741 个两两组合里 **0 误报**；`stained_glass_pane`（两条路线本来
同组）、`wool`/`banner`/`stained_glass`/`concrete_powder`（另一条路线是无组独立配方，不是混合组）
都不会被并入。

**数据**（随仓库配方集 1585 文件 → 1027 格 / 40 混合组）：跨格产物 293 种 = 245 种含非混合来源
（净 −357）+ 48 种只在混合组里（就是那三个族）；修复 bug = 718 格（−309）；
**加拼接 = 667 格（−360）**、混合组 40 → 37、三族各 2 格 → 1 格（harness 32 配方/16 产物、
carpet 34/18、bed 32/16）。

**落地**：`util/CollectionPipeline.java`（1.21.11 / 26.2 / 26.3 **字节一致**，源码 md5 `da0fab28…`）；
配置 tooltip 追加一句（7 语言）。

**部署**：备份 `20260928-221144`；26.3-Fabric md5 `10f9e34df61045f833a0ff4c18598782`。
三分支 mixin-check 通过；javap 核对：`coalesceRouteFamilies` / `isParallelRoute` /
`ROUTE_FAMILY_MIN_SHARED` 在位，`applyResultMerge` 体内 `invokestatic coalesceRouteFamilies`。

**待实测**：挽具/地毯/床 = **一个组**（不再是 16 格、也不是一个黄格）；搜索非黄色挽具不再缺条目；
散格收纳 / 混合组不动 / pin 剥离合一 / 关掉开关即恢复 均不变。

## 2026-09-29：路线族判据修正 —— 产物多重性不再否决成族 + 比例保护（已部署）

**用户实测（1.21.11 + Aerial Hell）**："模组为每个床添加了一些配方（AH 加的是羊毛线）……
原版状态是 AH 的床配方和原版床配方合在一起、床的交叉染色线独立。开启合并后我预想的是合到一个组里，
但实际上配方书里罗列了 16 个床配方组（BRBE 收纳组），每个组各含 1 条原版羊毛线 + 1 条 AH + 1 条原版
交叉染色线；那个 AH + 原版羊毛线大组则原封不动。"

**根因**：AH 把 16 条床配方写进 **vanilla 的 `bed` 组**（`group:"bed"`，产物 = `minecraft:*_bed`），
该组变成"16 产物 × 每产物 2 条"；上一轮判据里的"**每个产物在各自组内只出现一次**"（保守起见加的）
把整族判成非路线族 → 不拼接 → 回落成按产物建收纳格 = **16 格 × 3 条**，两个混合组按"只复制不动"不变。
旁证：AH 一共往 **12 个 vanilla 组名**里塞配方（bed 16、planks 22、hanging_sign 9、sign 8、
stained_glass 4、stained_glass_pane 8、wooden_button/door/fence/fence_gate/pressure_plate/trapdoor 8–14），
实机 51 个混合组里有 5 个"同产物多条配方"（planks 最多 5 条/产物）——多重性在整合包里是常态。

**用户拍板（2026-09-29）**：① 床终态 = 一个组（48 配方/16 产物）；② 模组自建组名时也并（结构优先）；
③ 小侧 ⊆ 大侧 → 并、互不包含 → 不并；④ 加比例保护（大侧 ≤ 2×小侧）。

**改动**（`util/CollectionPipeline.coalesceRouteFamilies` / `isParallelRoute`）：产物集合改取
`counts.keySet()`（**不再要求多重性为 1**）；新增 `ROUTE_FAMILY_MAX_RATIO = 2`；判据 =
两侧产物可解且各 ≥2 + 小侧 ⊆ 大侧 + 大侧 ≤ 2×小侧（命名不参与）。

**实测**（实机数据集 vanilla + AH 721 + FD 339 = 1195 格 / 51 混合组）：旧判据 2 对（carpet/harness）
→ 16 个"只在混合组里"的收纳格；新判据 3 对（+bed）→ **0 个**，床 = 1 组（48 配方/16 产物）；
AH/FD 的上千条配方**无新增误并**（1195 → 1192 格）。参考配方集（不随 jar 发布）里
`hanging_sign`(20) 与 `wooden_hanging_sign`(12)（比例 1.67）也会成族。

**部署**：备份 `20260929-153049`；26.3-Fabric md5 `d2d928ceaad325133e8e721324b52e1b`。
mixin-check 三分支通过；javap 核对：`ROUTE_FAMILY_MAX_RATIO` 字段在位、
`coalesceRouteFamilies` 体内**无** `Map.entrySet`/`getValue`、有 `keySet`；`isParallelRoute`
字节码 = `size()>=2` + `iconst_2 / imul / if_icmpgt` + `containsAll`。

**待实测**：装同类模组的实例里 **床 = 1 个组**，不再出现按产物建的一批收纳格；挽具/地毯保持一组的现状。

## 2026-09-29（二）：组内同产物相邻 —— 调整组里的配方排序（已部署）

**用户要求**："我希望每个组里的同产物配方都能放在一起，而不是一前一后中间夹着其他配方，
也就是调整组里的配方排序。"

**问题**：路线族拼接是字面拼接（A 组全部在前、B 组全部在后）→ 同一产物的多条做法被拉开
（床：原版羊毛线 …16 条… AH 木板线 …16 条… 原版交叉染色）；模组往 vanilla 组里追加配方
（AH 的 planks / hanging_sign / stained_glass_pane / black_dye）同理。

**实现**（`util/CollectionPipeline` 第 0.5 步，26.x/1.21.11 与 1.21.1 同算法）：
- `applyResultClustering(collections, …, newPacks)`：逐集合把同一产物的条目聚拢；**产物按首次出现
  顺序**排列、**同一产物内部保持原相对顺序**（原组在前、追加在后）→ 黑床（羊毛线 → 木板线 →
  交叉染色）→ 蓝床 → …；
- `clusteredEntries(entries, …)`：产物只有一条的组**原样返回入参**（不重建、不复制）；
  解不出产物的条目留在原位；只有顺序真的变了才返回新列表；
- 顺序会变的组用 `buildPack` 重建并并入 `newPacks`（不兼容标记就地补做 + 残缺标记交调用方重放）；
  **源集合不被修改** → 关掉开关立刻恢复原顺序；
- 族组在 `coalesceRouteFamilies` 内就地对齐（字面拼接后先聚拢再 `buildPack`），其余集合（含未被动过的
  vanilla 混合组）在第 0.5 步统一处理。

**效果**：床族 48 配方 / 16 产物 → `black(AH, 原版羊毛, 原版染色) | blue(…) | … | yellow(…)`。

**部署**：备份 `20260929-162228`；26.3-Fabric md5 `0077c8fd0b85f70d42088545445804d6`。
mixin-check 三分支通过；javap：`applyResultClustering` / `clusteredEntries` 在位，
`applyResultMerge` 体内调用 `applyResultClustering`。

**待实测**：床/挽具/地毯组内同产物做法相邻；模组追加进 vanilla 组的组同样相邻；
每产物只有一条的组顺序不变；关掉开关顺序恢复。

## 2026-09-29（三）：同产物同形融合 —— 形状一致的做法合为一条、差异材料轮循（已部署）

**用户需求**："如果某对同产物配方的合成形状相同……就将它们融合，也就是合为一个配方，配方的差异部分通过
轮循物品来轮流展示。"追加三条：① 融合**排在收纳组之后**；② 融合后 **pin 状态跟随**；③ 融合后配方状态取
成员里**优先级最高**的那个（可合成 &gt; 残缺 &gt; 不可合成）。另有一条硬条件：融合后"逐槽选项的笛卡尔积"
里**每个组合都必须实际可合成**，否则放弃融合（反例：蛋糕一号 = 中心 3 种鸡蛋 × 顶排原版奶桶、
蛋糕二号 = 中心 鸡蛋 × 顶排模组奶桶 → "模组奶桶 + 红/蓝鸡蛋"没有对应配方）。

**落点**：`CollectionPipeline` Stage 2.6 **第 5 步** `applySameShapeFusion`（族拼接 → 组内相邻 → 按产物收纳 → **同形融合**）。
判据：shaped↔shaped 比**去掉空行空列**后的图案（宽高 + 非空格分布）；shapeless↔shapeless 比材料条数；
shaped↔shapeless 比材料数并取 shaped 那版的形状（shapeless 材料按"选项重叠最大"配对落格）；result display
必须一致；**积覆盖**（某成员 D_i = ∅，或所有成员都只在同一个槽上不完整）。判据用暴力枚举交叉验证：
4000 组随机选项集规则误判 0（更保守），蛋糕例不融合、火把（煤炭/木炭）融合。

**实现**：差异槽 = `SlotDisplay.Composite`（原版多物品原料的轮循表示，无新渲染代码）；requirements =
逐槽物品并集（三态天然取最高优先级）；融合条目 id = 主成员（有 pin 的优先）真实 id；新增
`util/FusedRecipeVariants`（成员表 + 成员 pin 键）——`PinnedRecipeManager.isPinnedEntry/toggleFavourite`
按成员 pin 键跟随/落键；`mixins/pipeline/RecipeBookComponentMixin`（**既有 mixin，未改 mixin 配置**）
`@Redirect` `tryPlaceRecipe` 内的 `handlePlaceRecipe`，点击时换成"成员里当前物品栏真能做的那一条"
（都做不了则回退主成员）。**1.21.1 暂缓**（无 display 体系，用户决定）。

**部署**：备份 `20260929-232414`；26.3-Fabric md5 `a34647777950669292bfd56ab32ca31c`。
mixin-check 三分支通过；javap 核对同上。

**待实测**：同形同产物融合 + 差异轮循；pin 跟随；点击按材料挑版本；有假组合的不融合。

## 2026-09-29（四）：修"融合一个成功案例都看不到" —— 提前 return 挡住了第 5 步（已部署）

**用户反馈**（1.21.11 实例）："并没有看到成功案例，还是有很多未融合配方。"

**根因**：`applyResultMerge` 里"没有任何收纳格"的分支直接 `return`，而**第 5 步（同产物同形融合）
写在它后面** → 只要该标签页没有"产物跨格"的收纳格，融合整段不执行（族拼接后的 bed/carpet/harness
正好如此）。组内相邻（第 0.5 步）在其之前，故表现为"排序生效、融合没有"。

**修复**：提前返回分支同样调用 `applySameShapeFusion`，并补做收尾（不兼容标记 + 残缺标记重放）。
javap 核对：`applyResultMerge` 体内 `applySameShapeFusion` **2 处调用**（两条路径都覆盖）。

**修好后实测（按实例配方集复算）**：同产物对里满足判据 **182 对**；被"积里有假组合"挡下 23 对
（如床：vanilla 床与 AH 床在**三个木板槽**上都不同 → 混搭组合无配方 → 按用户硬条件放弃）；
形状不同 24 对、材料数不同 57 对。可见变化 = 18 个组共 **−54 条条目**（`suspicious_stew` −15、
`planks` −12、各色染料/骨粉/糖等 −2…−3）。

**部署**：备份 `20260929-233729`；26.3-Fabric md5 `3bb24f517e5a44d4c1e2fd32a7a159db`。

**待实测**：同 1.21.11（planks / suspicious_stew 等条目数明显减少；多槽差异不融合）。

## 2026-09-30（一）：第二轮实测复核 —— 跨标签页边界 / 判据边界 / 收纳格被排序剥离拆散（已部署）

**用户反馈**（1.21.11 实例，Aerial Hell 的"天空木棍"武器变体）："…它们仍然没有融合，甚至收纳它们的
组都被拆散了。"

**四条结论**（完整证据链见 `docs/同产物配方合并.md` §十二）：

1. **合并只作用于"当前标签页"**（设计边界）：vanilla `RecipeBookComponent.updateCollections` 把
   `book.getCollection(selectedTab.getCategory())` 交给管线（javap 实锤）→ Stage 2.6 每次只见一页。
   AH 的天空木棍变体配方**没有 `category` 字段** → vanilla 默认 `CraftingBookCategory.MISC`（javap：
   `CraftingRecipe$CraftingBookInfo.MAP_CODEC` 的默认值）→ **杂项**页；原版武器 `category: equipment`
   → **装备**页。日志实锤 `known-by-category: {crafting_equipment=129, crafting_misc=1097}`（装备页 =
   原版 123 + FD 6，AH 一条不占）→ **原版四页里这对配方永远不同格**。用户看到"被收进一格"的地方是
   **RBIP 镜像创造标签页**（`rbip$refreshCreativeGroups` 按"产物所在创造页"分桶；实例
   `enableRecipeBookIsPain=true`，日志 `mirrored 19 creative groups`）。
2. **按用户硬条件，16 对武器里只有 3 把剑能融**（判据边界，非 bug）：木棍在斧 `["XX","X#"," #"]` /
   镐 `["XXX"," # "," # "]` / 锹 `["X","#","#"]` / 锄 `["XX"," #"," #"]` 里占 **2 格** → 逐槽并集的积
   4 组合中有 2 组（1 木棍 + 1 天空木棍）无任何配方 → 按"所有预测组合都必须实际可合成"放弃。
   全库（vanilla+AH+FD，1712 条合成配方）：严格判据 **31 组融合（90→31 条）**，被挡 **50 组**
   （16 床、蛋糕、盔甲架、比较器、阳光探测器、12 件双手柄工具、盾牌…）。
3. **收纳格会被"排序剥离"拆散（真缺陷，本轮修掉）**：`applySortExtraction` → `RecipeExtraction.extractOne`
   以"组内**最低**类别"为基线、把高类别变体剥成子组；收纳格成员天然常处于不同类别（原版做法可合成、
   换材料做法不可合成）→ 格内"可合成那条"被剥出去单独成组、"不可合成那条"留在格内 = 用户看到的
   "收纳它们的组都被拆散了"。融合条目不受影响（单条目 → `all.size() < 2` 原样返回）。
4. **那一次测试的开关在配置里是关的**（环境状态，需用户确认）：活配置 `config/brbe.toml`
   （`@Config(name="brbe")`）`[alternativeRecipes] mergeSameResult = false`，mtime `2026-09-29 23:48:45`
   = 会话启动时刻（config save listener 会在启动时重写该文件），此后无写入 ⇒ 启动时读到的就是 `false`
   ——若整场没在配置界面改过（或改了没保存），则整场没跑合并。

**本轮落地（两处）**：

- ① `CollectionPipeline`：专用收纳格（`MergeResult.isDedicated`）内**非 pin** 成员统一取"该格最高类别"
  （新 `cellSortCategory` = `categorizeEvenIfStale` 的映射）→ **格内不再互相拆散**，整格按最好的成员
  参与后续排序（与"融合后状态取最高优先级"同一原则）；**pin 仍剥出**；搜索可见性不变。
- ② 合并诊断日志：`AppContext` 启动/保存打 `[BRBE-MERGE] config: noGrouped={} mergeSameResult={}`；
  开关开启时 `pipeline/RecipeBookComponentMixin.brbe$logMergeStats`（新 `@Unique`）在每次摘要变化时打
  `[BRBE-MERGE] <bookKey> noGrouped=… cells=… fused=…(from N recipes) rejected[shape/result/coverage/other] coverageSamples=[…]`
  （`CollectionPipeline.mergeDiagnostics()`；关闭时**不打**，日志里"没有这一行"本身就是答案）。

**部署**：备份 `20260930-002326`（原子替换；mixin-check 三分支全过）。

| 实例 | md5 |
|---|---|
| 1.21.11-Fabric | `0225f07daa2e668b95ba57018f77d650` |
| 26.2-Fabric ／ 26.2-Fabric 0.19.5-for-test | `64ba1cdeaa59ec7d981bcc22dd625a43` |
| 26.3-Fabric | `b87076dfd57c99fbb85491b0e29cbde6` |

javap 核对：三个 jar 的 `CollectionPipeline` 含 `cellSortCategory` / `mergeDiagnostics` / `diagReset`；
mixin 含 `brbe$logMergeStats` → `CollectionPipeline.mergeDiagnostics` 且 `ldc "BRBE-MERGE"`；
`AppContext` 含 `config: noGrouped={} mergeSameResult={}` 且读 `AlternativeRecipes.mergeSameResult`；
`applyResultMerge` 体内 `applySameShapeFusion` 仍为 2 处调用。

**待用户决策（本轮未实现）**：**A** 融合展示方式改"**整版轮循**"（每个槽的候选列表补成等长 →
共享 `SlotSelectTime` 下标让全槽同步切到同一成员 → 屏幕上永远是某个真实配方的完整布局，硬条件由构造
满足、可再融 50 组：全库 246→105 条）；**B** 跨标签页归并（管线目前逐标签页 + 指纹缓存，要做全书范围
合并再按页切片；RBIP 开启时这个缺口基本看不到，优先级低于 A）。

## 2026-09-30（二）：同产物配方合并（`mergeSameResult`）**暂时搁置** + 开发过程记录

**用户决定**（原话）："我觉得还是暂时放弃这个新模块吧，然后记录一下这个模块的开发过程。"

**处理方式**：**代码保留、配置项默认关闭**（`AlternativeRecipes.mergeSameResult = false`，玩家侧零影响；
用户实例 `config/brbe.toml` 里也是 false）。`noGrouped` 行为不受影响。

**新增文档**：`docs/同产物配方合并-开发过程记录.md` —— 完整开发过程：
需求演进（09-28 起 12 条用户定稿）、实现地图（Stage 2.6 五步 + 相关文件 + 展示/点击链路）、
**7 条开发中发现的缺陷**（含①收纳格索引覆盖 ②成族判据误伤床族 ③提前 return 挡住融合第 5 步
④副本索引与建格不一致 ⑤收纳格被排序剥离拆散 ⑥诊断缺失 ⑦部署包与源码不一致）、
离线实测数据（16 对武器只有 3 把剑可融；全库 31 组 90→31 条；整版轮循可到 105 组 246→105 条；
暴力校验 0 误判）、未解矛盾（逐槽并集 vs 硬条件、跨标签页、收纳格 vs 排序优先级）、
重启路线图（A 整版轮循 / B 跨标签页归并 / C 判据精确化 / D 1.21.1 移植 TODO / E 诊断先行）、
部署台账（7 次部署的备份 tag + 三分支 md5）。

**代码侧标注**（仅 javadoc，无行为变化）：`config/AlternativeRecipes.java` 的 `mergeSameResult` 字段与
`util/CollectionPipeline.java` 的 `applyResultMerge` 各加一段"⏸️ 2026-09-30 起暂时搁置"说明，
指向开发过程记录。`docs/同产物配方合并.md` 顶部加状态横幅。

**已重新构建并原子替换部署**（备份 `20260930-005918`；三个 jar 与构建产物 md5 一致）：

| 实例 | md5 |
|---|---|
| 1.21.11-Fabric | `37a7ecfdf27ffcb35c34021d92bc8c5d` |
| 26.2-Fabric ／ 26.2-Fabric 0.19.5-for-test | `fc645f09f475507c6b6720b9ac3f53d2` |
| 26.3-Fabric | `8c7e569334f261df455eb2ccf9ba7316` |

**"本轮只改注释"的字节码证明**：新旧 jar 的 `CollectionPipeline` / `AlternativeRecipes` /
`RecipeExtraction` / `pipeline/RecipeBookComponentMixin` 逐个 `javap -p -c` 对比
（**忽略 LineNumberTable 行号**）→ 差异 **0 行** ⇒ 搁置这一轮**没有任何行为变化**；
重新构建部署只是让部署物与工作区源码对齐（旧部署 `0225f07d…` / `64ba1cde…` / `b87076df…` 行为相同）。

**补记（同轮，实测证据）**：用户随后在 00:55–00:56 用**这一版**跑了实测（会话中途打开开关），
`logs/brbe-debug.log` 的 `[BRBE-MERGE]` 行第一次给出游戏内数字：

```
[00:55:54.811] … cells=2 fused=0 rejected[shape=2 coverage=5] samples=[rabbit_stew, suspicious_stew ×4]
[00:56:04.051] … cells=4 fused=0 rejected[shape=0 coverage=4] samples=[diamond_sword, golden_sword, iron_sword, shield]
```

⇒ ① 开关打开后**确实建了收纳格**（2 → 4 格），但**融合 0 条**（用户"看不到融合"属实）；
② 否决原因**全是 `coverage`**，**连离线模型预测能融的 3 把剑也被挡** ⇒ 客户端逐槽候选集比离线模型大
（已确认模组会追加 vanilla 标签：AH 84 个 / FD 52 个 `data/minecraft/tags/**` 文件，离线模型没合并）；
③ 结论：现行"逐槽并集 + 硬条件"在真实数据下几乎融不动 → 已写入开发过程记录 §4.5 / §5-5 / §6-E-0
（重启第一步：先加"逐槽候选集转储"日志）。

## 2026-09-30（三）：1.21.11 游戏"突然崩溃"排查 —— **JVM（G1 GC）问题，与模组无关**

**用户反馈**：1.21.11 突然崩溃。查因结果（完整报告见 `docs/JVM-GC崩溃调查.md`）：

- **本次**（01:08:59，`hs_err_pid4147347.log`）：`SIGSEGV` 在 `libjvm.so`，崩溃线程 **`G1 Conc#3`（G1 并发标记
  工作线程）**，栈里 **0 个 Java 帧**；堆仅用 580MB/7.9G（非 OOM）；`si_addr` 在元数据/库区（不在 Java 堆）；
  `crash-reports/` 无本次报告（JVM 直接 abort）；`latest.log` 最后一行是崩溃前 12 分钟的区块保存（空闲期崩溃）；
  内核无 MCE。core dump 已由 systemd-coredump 保留（`coredumpctl info 4147347`）。
- **同机自 2026-06-08 起共 15 次同类崩溃**（脚本扫描 `versions/*/hs_err_pid*.log`）：15/15 都在 GC 工作线程
  （`G1 Conc#N` 12 次 + `GC Thread#N` 3 次），跨 **5 个实例**（1.21.11-F / 1.21.1-NF / 26.1.2-NF / 26.2-F ×7 /
  **All the Mods 10 ×3（未装 BRBE）**）、跨 **JDK 21.0.11 / 21.0.12.1 / 25.0.4**。
- **06-08 那次符号完整**（Ubuntu 21.0.11）：栈顶 `OopOopIterateDispatch<G1CMOopClosure>::oop_oop_iterate<InstanceKlass, narrowOop>`
  ← `G1CMTask::process_grey_task_entry` ← `G1CMTask::do_marking_step` ← `G1CMConcurrentMarkingTask::work`
  ⇒ G1 并发标记遍历对象引用时拿到坏的 `Klass*`。今天这次 Arch 构建符号被 strip，属同一类。
- **⇒ 排除 BRBE/模组**：崩溃在 JVM 内部 GC 线程（Java 代码无法执行到那里）、未装 BRBE 的实例也崩、
  崩溃时游戏空闲 12 分钟、时间跨度与跨加载器/JDK 都对不上。BRBE 至多是"分配更多 → GC 更频繁 → 更容易踩到"的加重因素。
- **建议排查顺序**：① 换 GC（`-XX:+UseZGC -XX:+ZGenerational` 或 Shenandoah）；② 去掉 HMCL 自动加的
  `-XX:G1HeapRegionSize=32m` 等自定义参数，试裸 `-Xmx8G -XX:+UseG1GC`；③ 换 JDK 构建/厂商；
  ④ 硬件侧（13 代 HX，虽无 MCE）memtest86+/mprime 压测 + 更新 BIOS 微码。
- 数据面：存档在 00:56:26 已保存、崩溃发生在空闲期 → 无损坏；已删除本次调查临时解出的 4.3GB core（系统里仍保留压缩转储）。

## 2026-09-30（四）：收纳格**不再豁免**排序剥离（撤回上一轮的 `cellSortCategory`，已部署）

**用户反馈**："收纳组无法遵循之前做过的智能组拆分机制，比如组内的部分配方的配方状态改变就需要拆分配方。"

**判定**：上一轮（09-30 一）我为了消除"收纳格被拆散"而加的"整格取最高类别、格内不拆"（`cellSortCategory`）
**挡住了 2026-09-27 定稿的「按排序原因智能拆分」**——那条是用户明确要的机制（"哪个变体需要调序就剥哪个，
不要拖家带口"）。拆格不是缺陷，是机制在按设计工作（09-30 一 的 §12.3 结论已推翻）。

**改动**（`util/CollectionPipeline.java`，三分支字节一致，源码 md5 `898f93ee33743f5099fb856eb1ffbd91`）：
- 删 `cellSortCategory` 及其 javadoc；`applySortExtraction` 的 `Plan#category` 去掉收纳格特判，恢复原样；
- 恢复后的行为：格内变体状态改变（**pin / 可合成 / 残缺**）→ 该变体按类别剥出参与排序，格内只留
  **基线（最低类别）**变体且位置不变；剥离子组继承专用格血统（`MergeResult.inherit`）、
  "两边都脱离父组"的副本合二为一（`dedupeCopiedExtractions`）随之**重新生效**（豁免期间是死代码）；
- **融合条目不受影响**（单条目 → `RecipeExtraction` 的 `size() < 2` 原样返回）。

**部署**：备份 `20260930-012131`；1.21.11-Fabric `9585d22a98744b0f2e17db6863187976`、
26.2-Fabric（含 for-test）`926299cdd0404b00cc26fb4dd3f33afe`、26.3-Fabric `6ca08a88526ac7ef585ef1c6d3054265`。
字节码核对：三个 jar 的 `cellSortCategory` 消失、`CollectionPipeline$1`（Plan 实现）的 `category` 不再引用
`isDedicated`；`[BRBE-MERGE]` 诊断保留。文档：`docs/同产物配方合并.md` §十三（+ §12.3 加"已撤回"标注）、
开发过程记录 §1/§3-⑤/§5-3 同步修正。

## 2026-09-30（五）：配置项改名「非常智能地收纳配方」+ tooltip 精简 + **默认改为开**（已部署）

**用户指令**：标题改「非常智能地收纳配方」；tooltip 改「将相同产物的配方整理到一起。」；
**配置项位置不变**；默认状态由关改为**开**。

**改动**（三分支同步）：
- `config/AlternativeRecipes.java`：`mergeSameResult` 默认 `false` → **`true`**（字段位置不动，
  仍在 `alternativeRecipes` 子项、GUI「配方」页原位置平铺）；javadoc 更新为
  「显示名 + 默认开 + 收纳格不特殊 + 暂停继续开发」。三分支该文件字节一致（md5 `b5fe630612a8b4a3824fbdb5878366a2`）。
- 7 语言 lang：标题与 `@Tooltip` 全量替换（zh_cn 非常智能地收纳配方 / 将相同产物的配方整理到一起。；
  zh_tw 非常智能地收納配方 / 將相同產物的配方整理到一起。；en Very Smart Recipe Tidying /
  Gathers recipes with the same result together.；ja とてもスマートなレシピ収納 /
  同じ産物のレシピをひとつにまとめます。；pl Bardzo inteligentne porządkowanie przepisów /
  Grupuje przepisy o tym samym wyniku razem.；ru Очень умная раскладка рецептов /
  Собирает рецепты с одинаковым результатом вместе.；tr Çok Akıllı Tarif Düzenleme /
  Aynı sonucu veren tarifleri bir araya toplar.）。改法是**逐键替换 JSON 字符串字面量** →
  文件其余格式与键序零改动（每文件 diff 恰好 +2 行）。
- `util/CollectionPipeline` 的暂停开发说明同步（"功能本身默认开启"）；根 `CLAUDE.md` 配置表、
  `docs/同产物配方合并.md`（横幅 + §12.6b + 新增 §十四）、`docs/同产物配方合并-开发过程记录.md`
  （§0/§1/§2.2/§6E/§8 的"默认关闭"全部改口）。

**生效范围**：`brbe.toml` 里没有该键的玩家（首次生成 / 从未手动改过）会拿到新默认值 `true`；
显式写过 `mergeSameResult = false` 的配置不受影响。

**部署**：备份 `20260930-013248`；1.21.11-Fabric `1d0448d09baf1020fa6fa53eb43db9d2`、
26.2-Fabric（含 for-test）`46a597711bfeb1ba7ac3dfdd0c0f367c`、26.3-Fabric `c4571e52c1563050c1eef5ae9f4c893b`。
验证：三个 jar 的 `AlternativeRecipes.<init>` 里 `mergeSameResult` 均为 `iconst_1`（默认 true）、
jar 内 zh_cn 两键为新文案；三分支 `compileJava`+`build` 通过、`mixin-check 1.21.11` 通过。

## 2026-09-30（六）：1.21.11 启动失败 —— architectury jar **静默损坏**（已还原，与 BRBE 无关）

**现象**：启动报 `Mod discovery failed! … architectury-19.0.1-fabric.jar: java.util.zip.ZipException:
invalid CEN header (bad signature)`。

**取证**：该 jar 中央目录里 **6 个字节被翻转**（偏移 556623…556731，全部落在**文件名**字段上，
非连续块 ⇒ 位翻转而非截断）；mtime 仍是 6 月 5 日（损坏不改 mtime）；
`Vanilla Creamy Exploration 1` 实例里有一份同版本完好副本，逐字节比对恰好这 6 字节不同。
当天 00:55 的会话还正常加载了 architectury（页缓存里是旧的有效副本），01:08 JVM 崩过一次
（见 `docs/JVM-GC崩溃调查.md`）→ 缓存丢掉后才读到坏数据 ⇒ "昨天还好好的、今天开不了"。

**处置**：坏文件留证 `architectury-19.0.1-fabric.jar.corrupt-20260930-013908`，从完好副本**原子替换**还原
（md5 `f5adfe74f690e3cd014f1c95129a6ed4`、zip 494 条目可读、id/version = architectury 19.0.1）。

**影响面**：脚本扫描全部实例 **3218 个 mod jar，仅这 1 个损坏**；游戏本体等 28 个 jar 完好。
磁盘：ext4（无数据校验）、两片 NVMe SMART PASSED、内核无 I/O 报错，但 `Unsafe Shutdowns`
计数偏高（nvme0 371 / nvme1 125）。

**记录**：`docs/实例文件静默损坏记录.md`（含检测/还原套路）。**建议**：与 15 次 G1 SIGSEGV 一起
做内存/CPU 压测；数据盘可考虑 btrfs（校验和 + scrub）以便发现/修复此类损坏。

## 2026-09-30（七）：配置项定名四改 + `noGrouped` 补 tooltip 注解（已部署）

**用户指令**（四条）：① 「拆散替代配方组」→「**完全拆散替代配方组**」；② 「非常智能地收纳配方」→
「**自动收纳同产物配方**」；③ tooltip →「将所有替代配方组拆散，开启后禁用“自动收纳同产物配方”，
关闭后启用选择性拆散替代配方组（将不同选中状态的配方拆散开）」（文案描述的正是**完全拆散**这一项，
故落在 `noGrouped` 上）；④ 「只在悬停时显示替代配方」→「**只在悬停时显示微缩配方**」。

**改动**（三分支同步；`AlternativeRecipes.java` 仍字节一致）：

- `config/AlternativeRecipes.java`：`noGrouped` **补 `@ConfigEntry.Gui.Tooltip()`**——它此前既无注解也
  无翻译键，配置界面里悬停不出提示（反编译 `cloth-config-fabric 26.3.158` 的同类
  `DefaultGuiTransformers.lambda$apply$2` 逻辑一致：只有带注解才去查 `<前缀>.<字段>.@Tooltip`）；
  三处 javadoc 按新显示名改写。**字段顺序与默认值不变**：`onHover`=true → `noGrouped`=false →
  `mergeSameResult`=true。
- 7 语言：3 处标题替换 + 1 行新增 `noGrouped.@Tooltip`（zh_cn 逐字采用用户原话；每个文件
  `git diff` 恰好 +1 行）。en/tr 文案内的引号按 JSON 规范转义为 `\"`（首轮脚本多转义了一层反斜杠，
  复核 `json.loads` 结果后修正）。
- 注释同步（4 文件 7 行，纯注释）：`util/CollectionPipeline`、`render/PopupRenderer`、
  `util/BRBTextures`、`mixins/alternativerecipes/OverlayRecipeButtonMixin` 里对旧显示名的引用。
- 根文档：`CLAUDE.md` 配置表两行、`BRBE-功能设计蓝图.md` 对应小节、`docs/同产物配方合并.md` §15、
  `docs/同产物配方合并-开发过程记录.md` §1/§7/§8。

**部署**（备份 `20260930-112110`，原子替换）：1.21.11-Fabric `fb76bef8f03eb51807fa90ba26dae846`、
26.2-Fabric（含 `0.19.5-for-test`）`a5a9bc212cbffccf186ee6fbc5fd8fbe`、
26.3-Fabric `8478b747a2c882ab31e352482ea12a71`。

**验证**（javap/jar 实测）：三个 jar 内 `AlternativeRecipes` 的三个字段均带
`ConfigEntry$Gui$Tooltip` 注解；`<init>` 仍为 `iconst_1 / iconst_0 / iconst_1`；jar 内 zh_cn 五键 =
新文案；21 个 lang 文件全部 `json.loads` 通过；三分支 `build` 通过；`tools/mixin-check` 三分支全过。

**1.21.1 未改**：本模块在 1.21.1 仍是旧实现与旧标题（「强制合并相同产物的配方」），tooltip 里点名的
「自动收纳同产物配方」在该分支并不存在，故不做半套同步。

## 2026-09-30（八）：RBIP 新功能「紧凑型标签」+ 配置界面 RBIP 小节搬到「功能」页（已部署）

**用户需求**：RBIP 新增"把同一个模组的配方收纳到一个标签里"的功能 —— 不是照搬创造模式物品栏：
**原版标签还原原版配方书的标签**，模组物品各自进专属标签，图标用该模组创造标签里**序号最靠前**的那个，
模组标签接在原版标签后面，tooltip 用**模组名**（去掉斜体、改白色）；其他机制（上下侧标签、固定）不变。
配置界面：在「功能」页「启用一键制作」下面建黄字行「Recipe Book Is Pain（配方书标签）」，
其下依次是改名为「启用RBIP」的原主开关、新开关「紧凑型标签」（默认关）、原有两个子开关。

**实现**（三分支同步；RBIP 5 个文件 + 2 个配置相关文件）：

| 位置 | 改动 |
|---|---|
| `RecipeBookIsPain` | 新增紧凑注册表（`ns → 分类对象`，**身份稳定** —— `collectionsByTab` 按身份比较，每次 new 会让标签变空；四种配方书类型各一套）、`compactEnabled` / `toCompactGroup` / `isCompactGroup` / `compactNamespace` / `compactIconTab`（代表创造标签：先"模组自有创造标签"里最靠前的，再"最早含该模组物品的标签"，有缓存）、`withCompactTabs` / `withCompactFurnaceTabs`、`compactTabTooltip`（白色非斜体模组名）；`toItemGroup` 增加紧凑分支（图标 / pin 键 / 取消解锁弹跳共用） |
| `ClientRecipeBookMixin` | 紧凑模式按模组归组（`minecraft` 命名空间返回 null ⇒ 不进 RBIP 桶）、**不删**原版熔炉分类、对 **10 个原版标签分类**（合成 4 + 熔炉系 6）**逐条过滤**掉模组产物（`rbip$vanillaOnly` 保留原版分组，搜索分类不动）、把模组记进对应熔炉类型的"有配方"集合 |
| `RecipeBookWidgetMixin` | 标签栏按开关二选一（`withCompactTabs` / `withCreativeTabs`，熔炉系同理）；配置变化时**先 `book.rebuildCollections()` 再 `updateTabs(false)`**（否则新分类没有集合 → 被"空集合隐藏"挡掉，表现为"切了开关没反应"） |
| `RecipeBookTooltipMixin` | 紧凑标签 tooltip = 模组名（`ModNameUtil.resolveModName`，白色 + `withItalic(false)`） |
| `RecipeBookIsPainExtendedConfig` | 新增 `compactTabs()`，纳入 `reloadIfChanged()` |
| `BrbeConfig.RecipeBookIsPain` | 新字段 `public boolean compactTabs = false;`（紧随主开关；TOML 路径 `[rbip]` 不变，老配置不失效） |
| `ConfigTipsHelper` | RBIP 小节整体搬到「功能」页「启用一键制作」之后（黄字行 + 启用RBIP + 紧凑型标签 + 启用上侧和下侧的标签 + 隐藏翻页按钮）；「界面」页那份**取消**（避免同名黄字行出现两次）；删除失去调用者的 `moveAfter` / `moveBeforeEntry` |

**7 语言**：`option.rbip.enableRecipeBookIsPain` → 启用RBIP（en Enable RBIP / zh_tw 啟用RBIP / ja RBIPを有効化 / …）；
新增 `option.rbip.compactTabs` + `.@Tooltip`（紧凑型标签 / Compact Tabs / …）——21 个 lang 文件。

**部署**（备份 `20260930-122943`，原子替换）：1.21.11-Fabric `9898565df37ff0c795f6629328678906`、
26.2-Fabric（含 `0.19.5-for-test`）`bb736e9275a1240fc78ffa76b9cce5e7`、26.3-Fabric `9bc9fa4bf2c3c6c7e8f8c77812e92c4a`。

**验证**：三分支 `compileJava` + `build -x test -x check` 通过；`tools/mixin-check` 三分支全过；
javap 核对三个 jar：`RecipeBookIsPain` 含 `compactEnabled`/`withCompactTabs`/`withCompactFurnaceTabs`/
`toCompactGroup`/`isCompactGroup`/`compactNamespace`/`compactIconTab`/`compactTabTooltip`；
jar 内 `assets/brbe/lang/zh_cn.json` = 启用RBIP / 紧凑型标签 / 新 tooltip。

**已知边界**：模组自定义工作站的配方（切石/锻造/厨锅等非原版分类）本就不在 RBIP 标签体系内 —— 未改；
**1.21.1 未实现**（该分支 RBIP 是旧枚举式配方书，需另写等价实现）。详见 `docs/RBIP紧凑型标签.md`。

**待用户实机验证**：开关打开后标签栏 = 原版 4 个合成标签（**只含原版配方**）+ 每个模组一个标签
（图标 = 该模组最靠前的创造标签、悬停 = 白色非斜体模组名、接在原版之后），pin 过的模组标签排模组块最前；
熔炉/烟熏炉/高炉同理；关闭该开关应完全回到原 RBIP 行为。

## 2026-09-30（九）：配置界面布局修正 —— 「界面」页 RBIP 小节**恢复原位**（已部署）

**用户指令**：『"界面"页面的 RBIP 配置项不要移动到功能页面内，请恢复』。

（八）轮把整个 RBIP 小节（黄字行 + 四个条目）合并到了「功能」页；本轮按要求改回**两节并存**：

| 页面 | 最终顺序 |
|---|---|
| 「功能」 | … → 启用一键制作 → **[黄字] Recipe Book Is Pain（配方书标签）** → 启用RBIP → 紧凑型标签 → [§eLikewise Enough Items 文字行] → 启用BRBE的查询功能 → … |
| 「界面」 | … → 配方书居中 → 显示设置按钮 → 启用配方书 → **[黄字] 同名行** → 启用上侧和下侧的标签 → 隐藏翻页按钮 → …（**与改动前一致**） |

- 实现：`ConfigTipsHelper.relocateEntries` 拆成两节 —— 常量 `FUNCTION_RBIP_OPTION_KEYS`（功能页两项）
  与 `UI_RBIP_OPTION_KEYS`（界面页两项 + `RBIP_ANCHOR_OPTION_KEY = keepCentered`），
  并**恢复**（八）轮删掉的两个私有辅助 `moveAfter` / `moveBeforeEntry`（第 4 步"显示设置按钮/启用配方书
  排到黄字行之前"要用）。
- 黄字行 `brbe.gui.section.recipeBookIsPain` 现在**两页各出现一次**（用户明确要求的结果）；
  语言键与文案不变（无 lang 改动）。
- 代价：无（只重排已有条目对象，字段与 TOML 路径仍原地不动）。

**验证**：三分支 `compileJava` + `build -x test -x check` 通过；`tools/mixin-check` 三分支全过。

**部署**（备份 `20260930-132520`，原子替换）：1.21.11-Fabric `946e39dd5e63ec336fe954617a94804f`、
26.2-Fabric（含 for-test）`6bb24d2cc4521140c93acaa8862a8b65`、26.3-Fabric `72f2c84d536f9dc5adf436ac6f447958`。

## 2026-09-30（十）：标签固定（pin）**按模式分开存储**（已部署）

**用户指令**：『两个模式的标签 pin 状态需要分开存储』。

**问题**：`TabPinManager` 只有一份固定列表，而两种模式用的 pin 键虽然不同（普通模式 = 创造标签 id；
紧凑模式 = 该模组的"代表创造标签" id），却写进同一个 `brbe.tabpins.json` → 在一个模式里固定/取消
会串到另一个模式。

**实现**（`pin/TabPinManager.java`，三分支逐字节一致，md5 `c146f4fa01398bb1a8a52a90e43219cc`）：

- 两份有序列表：`pinnedIds`（普通）与 `compactPinnedIds`（紧凑型标签）；所有公开方法
  （`isPinned` / `toggle` / `pinnedIds` / `pinnedTabs`）都作用于**当前模式**那一份，
  由 `activeIds()` 按配置项 `rbip.compactTabs` 选择 —— 同一创造标签 id 在两个模式里也各存各的。
- 磁盘格式升级为 `{"tabs":[…],"compactTabs":[…]}`；**旧格式（裸数组）自动迁移到 `tabs`**，
  旧数据不丢（用同一套解析逻辑离线验证：裸数组 → `tabs`；对象 → 两份；缺键/空数组 → 空列表；
  写→读往返稳定）。
- `/brbe clear rbippin` 仍清**两个模式**（命令语义是"清除所有 RBIP 标签固定"），返回两者之和。
- pin 标记绘制与固定键处理都走同一套"当前模式"判定 → 切模式后标记与排序立即跟着切
  （（八）轮的重建路径已经会在配置变化时 `rebuildCollections()` + `updateTabs(false)`，无需额外接线）。

**验证**：三分支 `compileJava` + `build -x test -x check` 通过；`tools/mixin-check` 三分支全过；
javap 核对三个 jar：`TabPinManager` 含 `compactPinnedIds` 字段、`activeIds()` 方法与 `KEY_COMPACT` 常量，
`activeIds()` 字节码按 `compactPinnedIds`/`pinnedIds` 二选一。

**部署**（备份 `20260930-134527`，原子替换）：1.21.11-Fabric `3e773f8551b5755101cdef32e1cf8873`、
26.2-Fabric（含 for-test）`e773692b0866b0e5a13f86ef9549ea72`、26.3-Fabric `7fda01526f4e63159bfe2e871c3eb303`。

## 2026-09-30（十一）：存储作用域规格落地 —— LEI pin / 查询窗口 / 浏览记录改为**分存档**（已部署）

**用户指令**：『rbip双模式pin状态、各配方书的配方pin状态、LEI的pin状态以及窗口摆放、配方书浏览记录。
特别说明一下前面两项属于全局存储，而后两者需要分存档/服务器存储。』

**最终存储规格**（完整清单见根级 `docs/BRBE数据存储清单.md`）：

| 数据 | 文件 | 作用域 |
|---|---|---|
| RBIP 双模式标签 pin | `brbe.tabpins.json` | 全局（不变） |
| 各配方书的配方 pin | `brbe.pins.json` | 全局（不变） |
| LEI pin 浮层 | `pinoverlays.json` | **分存档 / 分服务器**（本轮改动） |
| 查询窗口摆放 | `queryviewers.json` | **分存档 / 分服务器**（本轮改动） |
| 配方书浏览记录 | `positions.json` | **分存档 / 分服务器**（此前仅内存，从不落盘） |

**新工具类 `util/WorldScopedStore`**（三分支逐字节一致，md5 `e54a18d0714303c321daa07b83fadf7f`）：

- `refresh()` 用 **token 身份比较**判定作用域变化：单机 = `IntegratedServer` 实例、多人 = `ServerData`
  实例（其实现在三个版本都是稳定字段取值，已 javap 核实：`Minecraft.getCurrentServer()` =
  `getConnection().getServerData()`，`ClientPacketListener.getServerData()` 返回存储字段）、
  `Minecraft.level == null` = 无作用域（主菜单，不读不写）。token 未变时每帧只花两次 getter +
  一次引用比较，不解析路径。
- 目录：单机 `<world>/brbe/`；多人 `<gameDir>/brbe/servers/<key>/`。`<key>` = `sanitize(ip)`
  （Realms = `realm-<name>`；**LAN 去掉端口**——端口每次会话都变、带上会每次进房都是新作用域；
  地址取不到才退化用名字）；路径非法字符替换 `_`、去结尾点、超长截断加哈希、非 ASCII 名称保留；
  结果为空 / `.` / `..` → 无作用域。
- 作用域切换顺序：**旧作用域先落盘（文件路径由各存储自己记着）→ 清空内存 → 迁移旧文件 → 载入新作用域**。
  不清空会让上一个世界的 pin / 浏览位置泄漏到下一个存档或服务器。
- 旧版全局文件一次性迁移：作用域文件不存在且 gameDir 旧文件存在时**复制**过去，再把旧文件改名为
  `<name>.migrated`（数据保留、不再被读取——否则每个新作用域都会继承同一份旧数据）。

**三个存储接入**：

- `PinOverlayManager`：`init()` 从"一次性初始化"改为"注册监听 + `WorldScopedStore.refresh()`"
  （每帧由 `render()` 驱动，`render` 第一行就是 `init()`，三分支皆然）；`pinFile` 由监听者重设；
  `save()` 拆出 `snapshot()` / `writeTo(target, specs)`，作用域切换时用旧路径落盘。
  `load()` 改为先清空 `pendingSpecs`（文件缺失 = 空列表，不再残留内存态）。
- `RecipeViewerOverlay`：`initViewerPersistence()` 同样改为注册 + refresh；`saveViewerSpecs()` 拆出
  `writeViewerSpecs(target)`；`loadViewerSpecs()` 在文件缺失时也清空列表。
- `RecipeBookPositionMemory`（三分支逐字节一致，md5 `a78cb7e45dde20d8fb0cb88605c2a221`）：新增落盘。
  格式 `{"positions":{"<书>":{"<标签下标>":{"page","tabPage","basePage","search"}}},"activeTabs":{...}}`。
  `save()` 每帧被渲染钩子调用，但**位置没变就不写盘**（`Pos` 记录相等 + 激活标签未变），且
  **同一时刻最多一个异步写入在途**（`WRITE_LOCK` + `dirty`/`writing`，写入期间又有变化则补写一次）；
  作用域切换时**同步**写（必须赶在清空内存之前完成）。`Pos` 与公开 API 未变，调用点无需改动。

**验证**：三分支 `compileJava` + `build -x test -x check` 通过（仅 26.3 有既存的 JEI fork
deprecation/unchecked 警告）；`tools/mixin-check` 对三个新 jar 定向校验**全过**（本轮未改任何 mixin
类与 mixin 配置，跑它是回归确认）；javap 核对三个 jar——
`WorldScopedStore.refresh` 字节码 = `getInstance → tokenFor → resolved/token 引用比较 → resolveDir`，
`resolveDir` 含 `brbe` / `servers` 常量与 `serverKey` 调用（1.21.11 以 intermediary 名呈现：
`class_310.field_1687`（level）/`method_1576`（getSingleplayerServer）/`method_1558`（getCurrentServer）、
`class_1132.method_27050`（IntegratedServer.getWorldPath））；
`PinOverlayManager` / `RecipeViewerOverlay` 的初始化字节码 = `addListener` + `refresh`，且
`pinoverlays.json` / `queryviewers.json`（新）与 `brbe.pinoverlays.json` / `brbe.queryviewers.json`（旧）
两个常量与 `migrateLegacy` 调用都在；`RecipeBookPositionMemory` 含 `file`/`registered`/`dirty`/`writing`
字段与 `positions.json` 常量。另外离线验证：`positions.json` 的 Gson 往返（含引号 / 反斜杠 / 中文搜索词）
与 `serverKey`/`sanitize` 规则（端口、LAN 去端口、`..` → 空、中文名保留、超长截断）。

**行为变化（需知）**：旧版全局的 pin 浮层 / 查询窗口会在**装好后第一次进入的那个存档或服务器**里被继承
（旧文件同时改名 `*.migrated` 留档）；此后每个存档 / 服务器各存各的。1.21.1 分支**未同步**（其查询窗口
无持久化通道，只有全局 `brbe.pinoverlays.json` 与内存版浏览记录），需要时按同一方案移植。

**部署**（备份 `20260930-184220`，原子替换）：1.21.11-Fabric `61ee04a27a430c3f05e5a503e4121757`、
26.2-Fabric（含 for-test）`678bcacb21674c678e247d14760d5d5c`、26.3-Fabric `473c8293f571066cf51820aa3df7b86c`。

**验证方法**：① 存档 A 开几个 LEI pin + 查询窗口 + 翻几页 → 退出进存档 B 应为空 → 回 A 全部恢复；
② 存档目录下出现 `brbe/pinoverlays.json`、`queryviewers.json`、`positions.json`；
③ 首次进存档后 gameDir 的旧 `brbe.pinoverlays.json` / `brbe.queryviewers.json` 变成 `*.migrated`；
④ 联机时数据落在 `<gameDir>/brbe/servers/<key>/`，与单机互不干扰；⑤ 主菜单不产生任何新文件。

## 2026-09-30（十二）：RBIP 标签页配方状态**冻结**修复 —— 增量 canCraft 索引没覆盖 RBIP 新建的集合（已部署）

**用户反馈**（26.3）：『选中 rbip 创造模式标签时，部分配方行为异常，比如配方状态刷新延迟——
打开「原材料」标签，用铁锭合成铁粒后，该配方本应变为不可合成（铁锭已消耗完），但它仍被标记为
可合成，而且该配方的所有材料均标记为缺失。搜索页看不出问题。』

**根因**（三分支同构，与 mixin 注入顺序无关）：增量 canCraft 索引
（`util/RecipeCraftingIndex`）的输入取的是 `ClientRecipeBook.getCollections()`，而它返回的是
**vanilla 自己重建时生成的扁平表 `allCollections`**（javap 核实：`getCollections()` 字节码只有
`getfield allCollections; areturn`）。RBIP 的 `rbip$refreshCreativeGroups`（`rebuildCollections`
TAIL）只改写 `collectionsByTab`，而且每个标签页的集合都是
`EntryBucket.toCollections()` **新建的 `RecipeCollection` 对象**（紧凑模式下原版标签页用的
也是新建的过滤副本）——这些对象**永远不在 `allCollections` 里**，模组里也没有任何代码写
`allCollections`（grep 核实）。于是：

1. `INDEX`（物品 → 集合）里没有 RBIP 的集合；
2. `shouldSkip(collection)` 的语义是「在 COMPUTED 里 && 变更物品不在它的原料里 → 跳过」，
   "查不到"被当成"不受影响" → 返回 true；
3. `RecipeCollection.selectRecipes` 被 `localcache/RecipeCollectionMixin` 整体取消 →
   该集合的 `craftable` / `selected` 停在**上一次全量重算**的状态（= `rebuildCollections` 那次）；
4. 而残缺标记（`markPartialMaterials`）用的是**实时库存** → 「配方仍可合成」+「材料全缺失」
   的矛盾显示。

**为什么搜索页正常**：搜索分类的集合是 vanilla 建的、在 `allCollections` 里 → 命中索引 →
增量跳过正确（与 `selectMatchingRecipes` 的遍历来源一致：它走 `tabInfos` →
`book.getCollection(category)` → `collectionsByTab`，RBIP 分类包含在内，所以 RBIP 集合确实会被
`selectRecipes` 求值、也确实会被错误跳过）。「刷新延迟」= 下一次 `rebuildCollections`
（解锁新配方 / 重开书等）时索引重建、状态才恢复。

**修复**（三分支同步，两处互为兜底 + 一处保守守卫）：

| 位置 | 改动 |
|---|---|
| `util/RecipeCraftingIndex`（三分支逐字节一致，md5 `c5c9b9f21b9240db48b9935c8de450ff`） | 新增 `INDEXED`（弱引用集合，`rebuild` 时记录输入集合）；`shouldSkip` 对**索引不认识**的集合一律返回 false（宁慢勿错） |
| `mixins/localcache/ClientRecipeBookMixin`（逐字节一致，md5 `b90c2d40d145b4a49e7108ef3b49cb29`） | 索引输入从 `getCollections()` 改为新 `@Unique brbe$displayedCollections(book)` = **`allCollections` ∪ `collectionsByTab` 的全部集合**（IdentityHashMap 去重） |
| `accessors/ClientRecipeBookAccessor`（逐字节一致，md5 `4dbfb90996d2038ecaa0af0e048602a8`） | 新增 `@Accessor("collectionsByTab") brbe$getCollectionsByTab()`（不动 mixin 配置） |
| RBIP `mixin/groups/ClientRecipeBookMixin` | 改写 `collectionsByTab` **之后**再 `RecipeCraftingIndex.rebuild(rbip$flattenCollections(updatedResults))` 一次（与 brbe 侧的重建互为兜底，两个 mixin 谁先注入都成立）；`rbip$flattenCollections` 为 `@Unique` 静态方法 |

效果：RBIP 标签页的集合重新纳入索引（增量跳过仍然生效，不是把优化关掉）；任何"索引外的集合"
都不会再被跳过 → 可合成状态永远跟得上库存。

**验证**：三分支 `compileJava` + `build -x test -x check` 通过；`tools/mixin-check` 对三个新 jar
（含新 `@Accessor` 目标字段 `collectionsByTab`、新 `@Unique` 方法）**全部通过**；
lambda 自查：改动的三个 mixin 类 `lambda$` 计数均为 0（无 Mixin 合成方法噪声）；
javap 核对三个 jar：`shouldSkip` 字节码开头依次为 `COMPUTED.containsKey → iconst_0/ireturn`、
**`INDEXED.contains → iconst_0/ireturn`** → `changedItems`；`rebuild` 含两处 `INDEXED` 访问；
brbe 侧 RETURN 处为 `brbe$displayedCollections(book)` → `RecipeCraftingIndex.rebuild(List)`，
RBIP 侧为 `rbip$flattenCollections(Map)` → `RecipeCraftingIndex.rebuild(List)`；
accessor 两个 getter 都在（1.21.11 呈现为 `class_10287`/`class_516`）。

**部署**（备份 `20260930-184937`，原子替换）：1.21.11-Fabric `9a4963dfe39f6f735da57f02232e03c8`、
26.2-Fabric（含 for-test）`ddac4d77fe2968a7d72311dd6d11db58`、26.3-Fabric
`64db0fb3ddd8074d5a3526b6d7d0878f`。

**验证方法**：进入存档 → 打开配方书 → 选中一个 RBIP 创造标签（如「原材料」）→ 用最后一个铁锭
合成铁粒 → 铁粒配方应立刻变为**不可合成**（不再"可合成 + 材料全缺失"）；继续合成/拾取物品时
各标签页状态应实时跟随；搜索页行为不变。

> 注：本节列的是当轮部署的 md5；（十三）的配置键修复随后重新构建并覆盖部署，**实例内当前为（十三）的 md5**。

## 2026-09-30（十三）：顺带修复 —— 管线缓存「配置键」压成 boolean OR 的失效漏洞 + 回归工具桩刷新（已部署）

**怎么发现的**：修（十二）的 RBIP 索引 bug 时按惯例跑 `tools/search-cache-harness/` 回归，发现它**早已跑不起来**
（在 Phase A/B 直接抛 `NoSuchMethodError` / `NoClassDefFoundError`）——桩与实现漂移（08-30 之后的几轮工作
加了新字段/新阶段，桩没跟）。刷新桩后 harness 恢复运行，并抓到一处**真实缺陷**。

**① 桩漂移修复**（`tools/search-cache-harness/stubs/`，仅测试工具，不影响成品）：

| 桩 | 补了什么 |
|---|---|
| `search/SearchCache` | `tooltipFallback()` / `setTooltipFallback()`（2026-09-27 tooltip 全文回退） |
| `config/BrbeConfig` | `unlockAll`（`ProgressionUnlocks.filtersActive` 读它） |
| `config/AlternativeRecipes` | `onHover` / `mergeSameResult`（管线指纹 `configKey` 读它），默认值与真实一致 |
| `RecipeCollection` | 实现 `RecipeCollectionAccessor`（显示路径 `brbe$applyGridVisibility` 会强转它并直接删 `craftable`/`selected`），`hasAnySelected()` 默认可见 |
| `util/CollectionPipeline` | 新增阶段 `applyResultMerge` / `applySortExtraction` / `mergeDiagnostics` + `MergeResult` 同形类（no-op 合并；缓存键测试与合并语义无关） |
| 新类型 | `world/entity/player/StackedItemContents`、`client/multiplayer/MultiPlayerGameMode`（mixin 字段/方法签名引用，框架反射 declared members 时需要） |

**② 真实缺陷：管线输出缓存的「配置键」是 4 个开关的 boolean OR**

`brbe$configKey()` 原先返回 `partialCraftingEnabled || partialMarkingEnabled || noGrouped || mergeSameResult`，
指纹里按 `? 1 : 0` 混进去。`mergeSameResult` **默认开** → OR 恒真 → **切换其余任何开关都不会改变缓存键**，
该开关本身在注释里写的意图（"同产物合并会改变管线输出的分组 → 必须进缓存键，否则开关切换不生效"）失效。
游戏内被其它指纹分量（残缺标记 revision / 索引 version）掩盖，所以玩家侧不易察觉；harness 的
Phase D ④「配置变化后管线重跑」与 Phase E「配置变 → 指纹变」把它钉了出来。

修复（三分支 `mixins/pipeline/RecipeBookComponentMixin`）：新增 `@Unique brbe$configMask()` 返回**位掩码**
（1=partialCraftingEnabled / 2=partialMarkingEnabled / 4=noGrouped / 8=mergeSameResult），
指纹行改为 `h = h * 31 + brbe$configMask();`；`brbe$configKey()` 保留为 `brbe$configMask() != 0`（诊断字段仍用它）。
1.21.11 与 26.3 的 OR 本来就含 `mergeSameResult`；26.2 的 OR 少这一项 —— 现在三分支统一为同一套掩码语义。

**验证**：三分支 `compileJava` + `build -x test -x check` 通过；`tools/mixin-check` 三个最终 jar **全过**；
`tools/search-cache-harness/run.sh all` **三分支 ALL GREEN**（唯一 `[XFAIL]` 是文档化的已知正则问题）；
javap 核对三个 jar：`brbe$configMask()` 在（`configMask` 三处引用 = 方法本身 + `configKey` 委派 + 指纹行），
改动的 mixin 类 `lambda$` 计数为 0。

**部署**（备份 `20260930-185536`，原子替换）：1.21.11-Fabric `36d83ce973f428b7db261960050686bd`、
26.2-Fabric（含 for-test）`f7f306de51e1ea3b001b0ad624dfe66c`、26.3-Fabric `826adbf0246d3ac42e730568f649bf15`。

⚠️ **harness 今后再漂移的处理**：报错形如 `NoSuchMethodError` / `NoSuchFieldError` / `NoClassDefFoundError`
的就是桩没跟上实现（工具本身没坏）；按上表同样方式补齐即可，补完必须仍能 ALL GREEN 才算数。

## 2026-09-30（十四）：紧凑型标签改版 —— **原版配方也独占一个标签（草方块图标）**（已部署）

**用户指令**：『我想调整一下"紧凑型标签"这个功能，我希望原版配方也使用唯一的标签（就和其他模组一样），
而不是原版配方书的标签，图标特别设置为草方块。』

**改动**（三分支同步，`recipebookispain_extended` 内）：

| 位置 | 旧 | 新 |
|---|---|---|
| `toCompactGroup(stack, variant)` | `minecraft` 命名空间返回 `null`（"不动它"，留在原版标签） | **任何**命名空间（含 `minecraft`）都返回自己的分类 |
| `compactIconTab(ns)` | 跳过 `minecraft` 命名空间的创造标签与物品 | 不再跳过 —— `minecraft` 的"代表创造标签"= 创造栏第一个原版标签（建筑方块），**只用于排序与 pin 身份** |
| `compactIconStack(ns)` | 代表创造标签的图标 | `minecraft` → **`new ItemStack(Items.GRASS_BLOCK)`**（用户指定） |
| `withCompactTabs` / `withCompactFurnaceTabs` | 原版标签列表原样保留 + 追加模组标签 | 新增 `compactBaseTabs()`：**只保留搜索标签**，再接上所有命名空间标签（含 `minecraft`） |
| `ClientRecipeBookMixin`（RBIP 归组） | 紧凑模式下对 10 个原版标签分类逐条过滤（`rbip$vanillaOnly` + `rbip$isVanillaEntry` + `RBIP_VANILLA_TAB_CATEGORIES`） | **整段删除**——原版标签不再出现，"模组配方同时出现在两处"的问题自然消失 |
| `RecipeGroupButtonMixin`（owo 图标） | — | 新增 `isVanillaCompactGroup` 判定：`minecraft` 紧凑标签**跳过 owo 的创造标签渲染**，保证图标就是草方块 |
| 配置 tooltip（7 语言 × 3 分支） | 「…原版配方则使用原版配方书的标签。」 | 「…原版配方也独占一个标签（图标为草方块）。」（en/ja/pl/ru/tr/zh_cn/zh_tw 各自译法见 lang） |

**效果**：紧凑模式下的标签栏 = **搜索标签 + 草方块图标的「Minecraft」标签 + 每个模组一个标签**；
原版的建筑方块/红石/装备/杂项（以及熔炉系食物/方块/杂项）标签**全部消失**，其配方进草方块标签。
`minecraft` 的排序按"代表创造标签"（原版第一个创造标签，序号 0）→ 通常排在模组标签之前；
pin 的键仍是该代表创造标签（`minecraft:building_blocks`），pin 后同样排到标签块最前。
**其他机制不变**（上下侧标签、翻页、滚轮、搜索并集、两模式 pin 分开存储）。

**验证**：三分支 `compileJava` + `build -x test -x check` 通过；`tools/mixin-check` 三个新 jar **全过**；
javap 核对三个 jar：`RecipeBookIsPain` 含 `compactBaseTabs` / `isVanillaCompactGroup`，
`compactIconStack` 字节码先比 `"minecraft"` 再取草方块（1.21.11 呈现为
`getstatic net/minecraft/class_1802.field_8270`，查 1.21.11 mappings 确认 `field_8270` = `GRASS_BLOCK`；
26.2/26.3 为 `Items.GRASS_BLOCK`）；改动的两个 mixin 类 `lambda$` 计数均为 0；
三个 jar 内 `lang/zh_cn.json` 的 `rbip.compactTabs.@Tooltip` = 新文案。

**部署**（备份 `20260930-220142`，原子替换）：1.21.11-Fabric `65caa87c67b56f7f6841b7977b95c1b8`、
26.2-Fabric（含 for-test）`2f6bb6de44628334e75c4b920de5e413`、26.3-Fabric `9aaad2fee9e40c4ab3eefe674ebe0c17`。

**验证方法**：配置 → 功能页打开「紧凑型标签」→ 打开工作台配方书：
应为「搜索 + 草方块「Minecraft」+ 各模组」；原版四个标签不再出现，其配方在草方块标签里；
熔炉/烟熏炉/高炉同理；悬停草方块标签显示白色非斜体的「Minecraft」；pin（默认 A 键）仍有效。

## 2026-09-30（十五）：「紧凑型标签」开关 → Cloth **枚举切换按钮**「标签模式」（已部署）

**用户指令**：『先用最小成本试试枚举按钮的样式』（先确认了 Cloth 有没有多档位控件 —— 结论：
有内置的枚举切换按钮 `EnumHandler(BUTTON)`，但没有并排分段控件）。

**改动**（三分支同步，`rbip.compactTabs: boolean` → `rbip.tabMode: TabMode`）：

| 位置 | 改动 |
|---|---|
| `config/BrbeConfig$RecipeBookIsPain` | 新增 `@ConfigEntry.Gui.EnumHandler(option = BUTTON)` + `@Tooltip` 的 `public TabMode tabMode = TabMode.CREATIVE_TABS;`；嵌套 `enum TabMode { CREATIVE_TABS, COMPACT }` 实现 Cloth 的 `SelectionListEntry.Translatable`（按钮上显示本地化档位名）；新增 `compactTabsEnabled()`（给核心代码用，避免耦合枚举）与 `migrateLegacyCompactTabs()` |
| 旧布尔字段 | 保留为 `@ConfigEntry.Gui.Excluded public boolean compactTabs`，**只用于一次性迁移**：`BetterRecipeBook.init()` 里 `if (config.rbip.migrateLegacyCompactTabs()) configHolder.save();`（老配置 `compactTabs = true` → `tabMode = COMPACT`，随即复位，幂等） |
| `RecipeBookIsPainExtendedConfig` | `compactTabs()` → `isCompact()`（读枚举）；`reloadIfChanged()` 同步改名 |
| `RecipeBookIsPain.compactEnabled()` | 调 `isCompact()`（其余紧凑逻辑与调用点全部不变） |
| `pin/TabPinManager.activeIds()` | 读 `config.rbip.compactTabsEnabled()`（**pin 存储格式不动**：仍是 `tabs` / `compactTabs` 两份列表 —— 两档正好；将来加第三档才需要改成按模式键存） |
| `ConfigTipsHelper` | 搬迁键 `…option.rbip.compactTabs` → `…option.rbip.tabMode`（黄字行与小节顺序不变） |
| lang（7 语言） | 删 `option.rbip.compactTabs[.@Tooltip]`，新增 4 条：`option.rbip.tabMode`（标签模式）/ `.@Tooltip`（两档语义说明）/ `.CREATIVE_TABS`（创造模式物品栏）/ `.COMPACT`（紧凑型标签） |

**行为**：两档与原来完全等价（`CREATIVE_TABS` = 旧 `false`，`COMPACT` = 旧 `true`）；
界面上是「标签模式 [创造模式物品栏]（重置）」一行，**点按钮切到下一档、到底回绕**，档位名走 lang。

**验证**：三分支 `compileJava` + `build -x test -x check` 通过；`tools/mixin-check` 三个 jar **全过**；
`tools/search-cache-harness/run.sh all` **三分支 ALL GREEN**；javap 核对三个 jar：
`RecipeBookIsPain.tabMode` 字段类型 = `TabMode`、枚举含 `CREATIVE_TABS`/`COMPACT` 且 `implements
me.shedaniel.clothconfig2.gui.entries.SelectionListEntry$Translatable`、`getKey()` 在；
`javap -v` 确认字段注解 = `@EnumHandler(option = …EnumDisplayOption;.BUTTON)`，旧字段 = `@Gui$Excluded`；
三个 jar 内 `lang/zh_cn.json` 含 4 条新键、无 `rbip.compactTabs*` 旧键。

**部署**（备份 `20261001-000904`，原子替换）：1.21.11-Fabric `8810b7373f31fa9782b731c5ce631878`、
26.2-Fabric（含 for-test）`1f5b832fb90a2fd2332512701a05090a`、26.3-Fabric `fd8f71b8419b962316e19486bfa179bd`。

**验证方法**：打开配置 → 「功能」页「启用RBIP」下方应出现「标签模式」+ 一个显示当前档位的按钮 + 重置按钮；
点按钮应在「创造模式物品栏」↔「紧凑型标签」之间切换，且**配方书标签栏立即跟着变**（热重载路径已跟踪该开关）；
老配置（`compactTabs = true`）启动后应自动变成 `tabMode = "COMPACT"` 且旧键复位为 false。

**边界**：① Cloth 缺失时 `BrbeConfig` 本来就因 `implements ConfigData` 加载失败（本轮实测复现，**既有问题、非本轮引入**）；
② 只有两档的 pin 存储仍是硬编码两份列表，加第三档需同时改 `brbe.tabpins.json` 格式。

## 2026-10-01（十六）：档位改名「紧凑型标签」→「命名空间」+ 两行 tooltip（已部署）

**用户指令**：『将标签模式的“紧凑型标签”改名为“命名空间”』，并给出 tooltip 原文（逐字采用）：

> “创造模式物品栏”：用创造模式物品栏的物品分类数据来界定对应配方的分类。
> “命名空间”：按配方所属的命名空间分类，在模组比较多的时候会更加合适。

**改动**（三分支同步，纯显示层，无逻辑变化）：

| 位置 | 改动 |
|---|---|
| `config/BrbeConfig$RecipeBookIsPain.tabMode` | `@ConfigEntry.Gui.Tooltip` → `@ConfigEntry.Gui.Tooltip(count = 2)`（**Cloth 的多行 tooltip 是按下标读键**，不是换行符） |
| lang（7 语言） | `…option.rbip.tabMode.COMPACT` 值改为「命名空间」；tooltip 由单条 `…tabMode.@Tooltip` 改为 `…tabMode.@Tooltip[0]` / `[1]` 两条 |
| `docs/RBIP紧凑型标签.md` / `docs/INDEX.md` / 根 `CLAUDE.md` | 改名 + tooltip 原文 + 归组键说明 |

⚠️ **Cloth tooltip 键格式**：`@ConfigEntry.Gui.Tooltip(count = N)` 读的是
`String.format("%s.%s[%d]", 配置前缀, 字段名, i)` → `…rbip.tabMode.@Tooltip[0]`、`[1]`；
`count = 1`（或不写 count）才读 `…tabMode.@Tooltip`。**两种写法不能混**，否则界面显示原始键名。

**枚举常量名不动**：Java 侧仍是 `CREATIVE_TABS` / `COMPACT`（只有显示名走 lang），
所以 `brbe.toml` 里已有的 `tabMode = "COMPACT"` 无需迁移、`brbe.tabpins.json` 的两份 pin 列表也不受影响。

**归组键复查确认**：「命名空间」档的分组键是**配方产物物品的命名空间**
（`BuiltInRegistries.ITEM.getKey(产物)`，落在 `RecipeBookIsPain.toCompactGroup`），**不是数据包、也不是配方 id**。
纯数据包配方因不能新增物品，产物必然属于某个已有物品 → 进该产物命名空间的标签（原版产物 → 草方块「Minecraft」标签）。
客户端拿不到配方 id 的命名空间（本分支的 `RecipeDisplayEntry` 只有 int 型 `RecipeDisplayId`，
`ClientboundRecipeBookAddPacket` / `ClientboundUpdateRecipesPacket` 都不带 `ResourceKey<Recipe>`），
所以「按数据包分标签」在当前客户端数据下做不到，需要额外的客户端配方 id 来源（如 JEI 的同步配方表）。

**验证**：三分支 `compileJava` + `build -x test -x check` 通过；`tools/mixin-check` 三个 jar 全过；
`tools/search-cache-harness/run.sh all` 三分支 **ALL GREEN**；`javap -v` 确认
`tabMode` 字段注解为 `ConfigEntry$Gui$Tooltip(count = 2)`；三个 jar 内 `lang/zh_cn.json`
含 `tabMode` / `tabMode.CREATIVE_TABS` / `tabMode.COMPACT` / `tabMode.@Tooltip[0]` / `[1]` 五条键。

**部署**（备份 `20261001-010816`，原子替换）：1.21.11-Fabric `bc2a442702ffb7491355ffdc7bd4df87`、
26.2-Fabric（含 for-test）`6c118fae486220b7a0a1f8ec681117f9`、26.3-Fabric `4cb18e86ae21646ba77b53b52e537475`。

**验证方法**：配置 → 「功能」页 → 「标签模式」按钮应显示「命名空间」；鼠标悬停出现**两行**说明
（不是一条带 `[0]` 字样的原始键名）。

## 2026-10-01（十七）：「命名空间」档改为按**配方 id 的命名空间**归组（已部署）

**用户指令**：『要不把命名空间模式的分配逻辑改为"按配方所属的命名空间来创建标签"（而不是配方产物）吧』
+ 两个二选一（用户已选）：**纯数据包标签的图标** = 「该标签下第一个配方的产物物品」；
**纯数据包标签可以 pin** → 无代表创造标签时 pin 键退回**裸命名空间**。

**关键前提（本轮反编译核实）**：客户端确实拿不到配方的 `ResourceKey`（`RecipeDisplayEntry` 只有 int 型
`RecipeDisplayId`；`ClientboundRecipeBookAddPacket` 只带 display 条目 + `replace`；
`ClientboundUpdateRecipesPacket` 只带 itemSets + 切石单输入集），但**可以按 display 值相等反查配方 id** ——
`BrbeJeiBridge` 给锻造/切石条目挂 JEI layout 用的就是这套判据（生产已验证）。

**新增** `brbe/cache/RecipeNamespaceIndex.java`（三分支逐字节一致）：`RecipeDisplay → 配方 id 命名空间`，
三级来源按可用性择一（**单机** = 集成服务端 `RecipeManager.getRecipes()`，唯一含**数据包**配方；
**联机** = Fabric `SynchronizedRecipes`；**兜底** = `VanillaRecipeCache`（原版 + 模组，不含服务端数据包））。
只索引 RBIP 会归组的显示类型（`ShapedCraftingRecipeDisplay` / `ShapelessCraftingRecipeDisplay` /
`FurnaceRecipeDisplay`）；**来源实例变了才重建**（换存档 / 重新同步 / 缓存 `generation` +1），
三处都缺时每 1s 才重试一次；**同名 display 对应多个命名空间 → 判歧义 → 返回 `null`**（调用方回退）。

**改动清单**（三分支同步）：

| 文件 | 改动 |
|---|---|
| `brbe/cache/RecipeNamespaceIndex.java` | 新增（见上） |
| `brbe/cache/BrbeJeiBridge.java` | 新增 `public static SynchronizedRecipes syncedRecipesOrNull()`（复用既有监听/反射兜底） |
| `brbe/cache/VanillaRecipeCache.java` | 新增 `generation`（`init()` +1）+ `entries()` / `generation()` |
| `recipebookispain_extended/RecipeBookIsPain.java` | 新增 `compactGroupFor(ns, variant)`（主路径）、`rememberCompactProduct(ns, stack)`、`compactPinKey(String)` / `compactPinKey(分类)`、`compactFallbackIcons`；`compactIconStack` 增加"该标签第一个配方的产物 → 知识之书"兜底；`appendCompactTabs` 的 pin 判定改走 `TabPinManager.isPinnedKey(compactPinKey(ns))` |
| `recipebookispain_extended/mixin/groups/ClientRecipeBookMixin.java` | `rbip$getCompactGroupForEntry`：**先** `RecipeNamespaceIndex.namespaceOf(entry.display())`，Miss 才回退产物命名空间；顺带 `rememberCompactProduct`；熔炉桶的 `resultItems(...).iterator().next()` 加 `hasNext()` 守卫（按配方 id 归组时 group 可能非空而产物为空 → 旧写法会抛 `NoSuchElementException`） |
| `brbe/pin/TabPinManager.java` | 新增 `isPinnedKey(String)` / `toggleKey(String)`（`isPinned(Identifier)` / `toggle(Identifier)` 改为委托）——**文件格式不变**，命名空间不含冒号、与 `ns:path` 标签 id 不会撞键，旧数据零迁移 |
| `brbe/mixins/pins/AbstractContainerScreenMixin.java` | 固定键处理：紧凑标签走 `compactPinKey`（不再要求 `toItemGroup` 非空） |
| `recipebookispain_extended/mixin/widget/RecipeGroupButtonMixin.java` | pin 标记绘制同上；`rbip$skipCreativeTabUnlockBounce` 补 `isCompactGroup` 判定（纯数据包标签也不播解锁弹跳） |

**验证**：三分支 `compileJava` + `build -x test -x check` 通过；`tools/mixin-check` 三分支全过；
`tools/search-cache-harness/run.sh all` 三分支 **ALL GREEN**；jar 内 `javap` 确认三分支都有
`RecipeNamespaceIndex.namespaceOf` 与 `RecipeBookIsPain.compactGroupFor / compactPinKey×2 /
rememberCompactProduct`、`TabPinManager.isPinnedKey / toggleKey`；三个被改的 mixin 类 `javap -p` 无 `lambda$`。

**部署**（备份 `20261001-014700`，原子替换）：1.21.11-Fabric `6388baa0e37a9dd1bbeda6fa61bbbc63`、
26.2-Fabric（含 for-test）`43a283699ef2a85e31944855cded8ee4`、26.3-Fabric `d9b0ad5236d8a7879165e0f5b73c2aef`。

**验证方法**：① 装一个"只加配方、不加物品"的数据包 → 命名空间档应出现以该数据包命名空间为名的标签
（图标 = 它第一个配方的产物物品），A 键可 pin；② 某个模组"产出原版物品"的配方（如加铁粒配方）
应进该模组标签而不是 Minecraft 标签；③ 原版配方仍全在草方块「Minecraft」标签。

**边界**：联机且服务端没装 Fabric API → 索引退到本地缓存 → **服务端数据包配方回退产物命名空间**（旧行为）；
同名 display 多命名空间判歧义同样回退；同步配方晚到时索引会自动重建，重开配方书即生效。详见 `docs/RBIP紧凑型标签.md`。

## 2026-10-01（十八）：**数据包切石配方在 LEI 里搜不到**修复（已部署）

**用户反馈**：『装了一个包含大量切石机配方的数据包，但 LEI 完全没有搜索到这些配方；合成配方的数据包却能搜到 —— 那就有可能是切石机这一块的问题了』。

**诊断（证据链）**：数据包 = MasterCutter v1.8.0（离线统计 zip：**1368 条 `minecraft:stonecutting`**）。实例 `logs/brbe-debug.log`：

```
[BRBE-JEI-PLUGINS] vanilla runtime type minecraft:stonecutting: 351 recipes indexed
[BRBE] rebuildEngine known-by-category: {…, stonecutter=1719} unmatched=0
[BRBE] rebuildEngine: 7 types, 3116 entries
```

1719 − 351 = 1368，正好是数据包条数 → **配方书已知集里有全部切石配方，但引擎里一条都没注册**。

**根因**：`RecipeViewerIndex.rebuildEngine` 里有一个 `continue` 把**切石整类委托给无头 JEI**（"条目与 layout 由 headless-jei 提供"），而**无头 JEI 的配方源只有客户端自带的 RECIPE 注册表**（`minecraft.level.registryAccess().lookup(Registries.RECIPE)` → `RecipeMap.create`）= 原版 + 已装模组，**看不到服务端数据包配方**。javap 核实客户端为什么没有这些配方对象：`ClientboundUpdateRecipesPacket.stonecutterRecipes()` 用 `SelectableRecipe$SingleInputSet.noRecipeCodec()` 序列化 → 客户端 `SelectableRecipe.recipe()` 恒为 `Optional.empty()`（只有 `optionDisplay`、没有 `RecipeHolder`）。
该 `continue` 与 `BrbeJeiBridge.attachVanillaLayouts` 的 javadoc（"切石/锻造：条目已由 RecipeViewerIndex 注册"）本来就矛盾 —— 即它是遗留错误。

**修复**（三分支同步，4 处）：

| 文件 | 改动 |
|---|---|
| `cache/RecipeViewerIndex.java` | `rebuildEngine` 删除切石的 `continue`：**每个 uid 一律按配方书已知集注册**（锻造注释并入同一段说明）；新增诊断行 `rebuildEngine per-type: {uid=条目数}` |
| `cache/BrbeJeiBridge.java` | attach-only 分支扩到 `minecraft:smithing \|\| minecraft:stonecutting`；**只有引擎里该类型一条都没有时**才用 headless 兜底注册（服务端不下发配方书的场合），否则只 `attachVanillaLayouts` |
| 同上 `reattachBookLayouts()` | 每次 rebuild 后**也重挂切石** layout（BRBE 条目与 headless 条目是不同 `RecipeDisplayId`，layout 按 id 存） |
| 同上 `attachVanillaLayouts()` | layout 挂接从"每个 headless 条目线性扫全部引擎条目"（O(n·m) 记录 equals）改为一次性 `display → 条目` 哈希表 —— 切石已知集上千条且每次 rebuild 都要跑 |

**效果**：原版 + 模组切石配方（351）仍按 display 匹配拿到 JEI 原生 layout（弹窗/预览为完整 JEI UI）；数据包切石配方（JEI 无其 layout）走 BRBE 自带的"输入 → 箭头 → 产物"紧凑预览，但**可搜索、可按材料反查、可 pin**。

**验证**：三分支 `compileJava` + `build -x test -x check` 通过；`tools/mixin-check` 三分支全过；`tools/search-cache-harness/run.sh all` 三分支 **ALL GREEN**；部署 jar 内 `RecipeViewerIndex.class` 含 `rebuildEngine per-type` 日志、源码侧三分支 `getKey().equals("minecraft:stonecutting")` 计数 = 0。

**部署**（备份 `20261001-153543`，原子替换）：1.21.11-Fabric `b13e5c3d82a221e63a43aa412ea1c1ec`、
26.2-Fabric（含 for-test）`3e78a3e1fb151adfe8c17d6e19971f64`、26.3-Fabric `ed53b9cda752c607e9774b0ec61a4a91`。

**验证方法**：装着 MasterCutter 进世界 → `logs/brbe-debug.log` 里应出现
`rebuildEngine per-type: {…, minecraft:stonecutting=1719, …}`（而不是 351）；LEI 里 R/U 查询任意石头/木头 →
"切石"类别应能翻到数据包配方（约 1368 条）。

**同源边界（未修，已写入 `docs/LEI查询数据源.md`）**：**任何"只走无头 JEI"的类别都看不到数据包配方** ——
酿造（`PotionLoader` + headless）、无配方书的模组 JEI 类别；26.3 的燃料/堆肥因数据包注册表不同步也是如此。
单机其实能从集成服务端 `RecipeManager` 拿到真实配方实例（连数据包配方的 JEI 原生 layout 都能补齐），但联机客户端没有任何配方对象，只能覆盖单机，暂不做。

## 2026-10-01（十九）：RBIP「命名空间」档**底层级转向「数据包」档**（已部署）

**用户指令**：『我打算将 RBIP 的标签模式的"命名空间"正式转向为"数据包"，是一次底层级转向。
我希望启用数据包模式时，直接按配方来源于哪个数据包来分配标签，标签图标则随机选择标签内的某个配方的
产物图标，tooltip 则使用数据包名字。』+ 四个已定决策：**联机 → 全部进一个「服务器」标签**、
**图标 = 稳定伪随机**、**原版保留草方块特例**、**配置与 pin 都做迁移**。

### 硬边界（先说清楚）

包列表只存在于**服务端**（`MinecraftServer.getResourceManager()`）。联机时客户端拿不到服务端装了
哪些数据包（协议里没有这条信息）→ `RecipePackIndex.available() == false` → 按用户选择，
**全部配方进 `#unknown` 一个「服务器」标签**（tooltip = 服务器名，取不到用「服务器」）。单机才按真实包分组。

### 实现

| 文件 | 改动 |
|---|---|
| `brbe/cache/RecipeNamespaceIndex` | 改为存**完整配方 id**（`recipeIdOf`），`namespaceOf` 派生；歧义（同名 display 多命名空间）仍视为查不到 |
| `brbe/cache/RecipePackIndex` **（新增）** | 配方 id → 来源数据包：`data/<ns>/recipe/<path>.json`（三分支实测目录都是**单数** `recipe`）→ 一次 `ResourceManager.listResources("recipe", …)` → **`Resource.sourcePackId()`**（覆盖关系由游戏资源栈判定 → 数据包覆盖原版配方时归数据包）；名字 `PackRepository.getAvailablePacks()` → `Pack.getTitle()`（pack.mcmeta，模组数据包 = 模组名），兜底 `PackResources.location().title()` / 包 id；包序 = `MultiPackResourceManager.listPacks()`；另建 `namespace→主要提供者` 表（回退与 pin 迁移用）。身份 = 服务端 `ResourceManager` 实例，变了才重建；联机清空 + 每 1s 节流重试；诊断行 `[BRBE-PACK-INDEX] index built: … packs=… labels=…` 与 `pack order: {…}` |
| `RecipeBookIsPain` | 归组键从命名空间换成**包 id**：`packKeyOfRecipe(display)`（配方 id → 来源包；联机 → `#unknown`；单机未命中 → 命名空间归属包）、`packGroupFor`、`toPackGroup`（产物命名空间回退）、`rememberPackProduct`（**图标池**，每包上限 64）、`packIconStack`（**原版草方块**；否则 `floorMod(包id.hashCode()*31 + WorldScopedStore.salt(), 池大小)` → **稳定伪随机**，池空兜底知识之书）、`packTabTooltip`（`RecipePackIndex.label` 白色非斜体）、`packPinKey`（= 包 id）、`withPackTabs` / `withPackFurnaceTabs`、`packKeyOrder`（pin 最前 → 资源栈顺序 → 包 id）、**`ensurePackPinKeysMigrated`**。**删掉**了旧的"代表创造标签"整套逻辑（`compactIconTab`/`compactTabIndex`/`compactIconTabs`/全标签物品扫描）——数据包标签不再委托 owo 图标、不再播解锁弹跳（`toItemGroup` 对数据包标签返回 null） |
| `RecipeBookIsPainExtendedConfig` | `isCompact()` → `isDatapackMode()`（热重载跟踪同步改名） |
| `brbe/config/BrbeConfig` | `TabMode.COMPACT` → **`TabMode.DATAPACK`**；`compactTabsEnabled()` → `datapackModeEnabled()`；`migrateLegacyCompactTabs()` → `migrateLegacyTabMode()`（只处理布尔旧值）；tooltip 改 **三行**（`@Tooltip(count = 3)`） |
| `brbe/BetterRecipeBook` | **新增 `migrateLegacyTabModeInToml()`（在 `AutoConfig.register` 之前运行）**：把 `brbe.toml` 里的 `tabMode = "COMPACT"` 改写成 `"DATAPACK"` —— 枚举常量删掉后旧值反序列化会抛异常 → Cloth register 整体失败 → **玩家的整份配置都会丢**，所以必须在加载前改文件 |
| `brbe/pin/TabPinManager` | 新增 `migrateCompactKeys(mapper)`：旧键（代表创造标签 id / 裸命名空间）→ 包 id；mapper 返回 null 即丢弃；重复键去重；返回改动数（>0 才落盘） |
| `brbe/util/WorldScopedStore` | 新增 `salt()`：当前作用域的稳定散列（同一存档/服务器恒定）——图标"每存档稳定"的随机盐 |
| lang（7 语言） | 删 `tabMode.COMPACT` 与旧两行 tooltip；新增 `tabMode.DATAPACK`（数据包/Data Pack/…）、`@Tooltip[0..2]`（第三行说明联机边界）、`brbe.tab.datapack.server`（服务器）、`brbe.tab.datapack.unknown`（未知来源） |
| mixin（`ClientRecipeBookMixin` / `RecipeGroupButtonMixin` / `RecipeBookTooltipMixin` / `RecipeBookWidgetMixin` / `AbstractContainerScreenMixin`） | 调用点全部改名到新 API；归组逻辑改为"解析包 key → 产物进图标池 → 熔炉 active 集合按包 id 记" |

### 验证

三分支 `compileJava` + `build -x test -x check` 通过；`tools/mixin-check` 三分支全过；
`tools/search-cache-harness/run.sh all` 三分支 **ALL GREEN**；源码 `grep` 确认无旧 API 残留
（`COMPACT_*` / `compactGroup*` / `withCompactTabs` / `TabMode.COMPACT` / `compactTabsEnabled` = 0）；
三个 jar 内 `RecipePackIndex` 存在、`lang/zh_cn.json` 含 `tabMode.DATAPACK` + 三行 tooltip + 两个新键。

**部署**（备份 `20261001-175205`，原子替换）：1.21.11-Fabric `229a961d4b3ee7944befcdde80c93678`、
26.2-Fabric（含 for-test）`0d406ac12f1bf5789130e1280eab9c77`、26.3-Fabric `1d4aea0d07bb5c83b490838675169c88`。

### 验证方法（用户）

1. 装 MasterCutter 之类数据包 → 「数据包」档：应出现**以该数据包为名的标签**（不是命名空间名），图标是它某条
   配方的产物；原版标签仍是草方块；`logs/brbe-debug.log` 看 `[BRBE-PACK-INDEX] index built: …` 与 `pack order: {…}`。
2. pin 数据包标签 → 重启仍在首页；`brbe.tabpins.json` 的 `compactTabs` 里应是包 id。
3. 老配置：`brbe.toml` 的 `tabMode = "COMPACT"` 启动后应变 `"DATAPACK"`，其余设置不丢。
4. 联机：整档只有一个「服务器」标签（tooltip = 服务器名），原版草方块标签不出现。

### 边界

联机无法按数据包分组（硬边界）；模组数据包的名字取决于其 pack.mcmeta；图标带世界盐（换存档可能换一个）；
图标池上限 64；1.21.1 未实现；RBIP 仍只覆盖合成 4 类 + 熔炉系。详见 `docs/RBIP数据包标签.md`。

## 2026-10-01（二十）：RBIP 标签模式拆成三档 + 联机自动降级/隐藏 + 两个 bug 修复（三分支同步）

**用户实机反馈**（26.3，装了 Furnicraft v7.5 + MasterCutter v1.8.0）：
① Furnicraft（**沿用 `minecraft` 命名空间**的数据包，只有 1 条配方 `data/minecraft/recipe/bench_craft.json`）
的配方被划进**原版标签**，而不是它自己的数据包标签；
② 一条"各种探险家地图 + 空地图 → **产物是空气**"的配方落到「未知来源」，怀疑是 BRBE 的 bug；
③ 决定：**保留命名空间模式**（"兼容性最佳"），并在追问后定为**三档并存** + 联机时若当前是数据包档则
**自动切到命名空间档并隐藏该选项**、离开服务器恢复；命名空间档图标用**稳定伪随机产物**；
pin 存三份（`tabs` / `namespaceTabs` / `datapackTabs`）；三档默认值仍是「创造模式物品栏」。

### 根因（两个都是 BRBE 的 bug）

**bug A（上一轮的实现错误，导致①）**：`ResourceManager.listResources("recipe", …)` 返回的键是
**`ns:recipe/<path>.json`**（带目录前缀与扩展名 —— 反编译 `FileToIdConverter.fileToId` 证实它做的是
`path.substring(prefix.length()+1, path.length()-extension.length())`；`FallbackResourceManager.lambda$listResources$0`
原样 `map.put(收到的 Identifier, …)`，类常量池里连 `.json` 字面量都没有），而 `RecipePackIndex.recipeResource()`
拼的是 `ns:recipe/<path>` → **每一次查询都 miss** → 静默退回"命名空间的主要归属包" →
`minecraft` 命名空间的绝大多数配方在原版包里 → 原版标签。实机日志印证索引本身建对了：
`[BRBE-PACK-INDEX] index built: recipe resources=3411 packs=5 namespaces=2 labels=9`
（2042 原版 + 1368 MasterCutter + 1 Furnicraft = 3411）。修复：键补 `.json`（1 行）。

**bug B（老缓存解析缺口，导致②）**：原版 `data/minecraft/recipe/map_cloning.json` 是 `crafting_transmute`，
**`"result": {}` 是空对象**（产物运行时由输入地图派生、数量随材料数增加）。`extractResultItem()` 读不到 `id`
→ 返回 null，`CacheableRecipeDisplayEntry` 照样造出 `ShapelessCraftingRecipeDisplay(材料, 产物=空)`
→ 按钮渲染成**空气**；产物为 null 使"按产物物品去重"对不上服务器真正的 `minecraft:map_cloning`
→ 被当新配方注入（日志实证：`injected (complement): 1 cached, 1731 skipped`，唯一那条 =
`[transmute/map_cloning → null]`）；归组时自造 display 与服务器 display 不等 → 反查不到 → 回退产物命名空间
→ 产物是空气 → `#unknown`「未知来源」（**命名空间档同样中招**，与档位无关）。
修复：`CacheableRecipeDisplayEntry.fromJson` 丢弃"取不到静态产物、又不是 `smithing_trim`（纹饰走 pattern）"的条目
（全原版只有 `map_cloning` 命中；`map_extending` 早已在跳过表里）。

### 落地（三分支 26.3 / 26.2 / 1.21.11）

- **配置**：`BrbeConfig.RecipeBookIsPain.TabMode` 三常量（`CREATIVE_TABS` / `NAMESPACE` / `DATAPACK`，默认第一个），
  `@Tooltip(count = 4)`；新增 `effectiveTabMode()`（**联机时数据包档降级为命名空间档**）、`namespaceModeEnabled()`、
  `datapackModeHidden()`；`datapackModeEnabled()` 改看生效档位。
  TOML 预处理改为 `tabMode = "COMPACT"` → **`"NAMESPACE"`**；旧布尔 `compactTabs=true` → `NAMESPACE`。
- **联机判定**：`WorldScopedStore.onRemoteServer()` = 作用域 token 是 `ServerData`（单机与**局域网主机**都算本地，
  主菜单不算联机）——数据包档要的服务端包列表在联机时拿不到。
- **配置界面**：新增 `brbe/config/TabModeGuiRegistrar`，用 **predicate transformer** 把 AutoConfig 生成的枚举条目
  换成自己的 `startSelector`（AutoConfig 内置枚举 provider 与自定义 provider 同优先级桶、`findFirst` 先到先得，
  只有 transformer 能稳换掉）；联机时可选档位只剩「创造模式物品栏 / 命名空间」，当前值显示为实际生效档，
  此时"再选命名空间"**不写盘**（保住玩家的数据包档偏好，离开服务器自动恢复）。
- **RBIP**：新增**命名空间档**整套（分类注册表 ×4 配方书、`namespaceKeyOfRecipe` / `namespaceGroupFor` /
  `toNamespaceGroup` / `rememberNamespaceProduct` / `nsIconStack` / `namespaceTabTooltip` / `withNamespaceTabs` /
  `withNamespaceFurnaceTabs`），与数据包档**并行且互不干扰**；抽出共用 `appendExtendedTabs`
  （`appendPackTabs` 变成它的薄包装）与统一判定 `isExtendedTabGroup` / `isVanillaTabGroup` / `extendedPinKey` /
  `extendedTabTooltip`；新增三档分派 `craftingTabsForCurrentMode` / `furnaceTabsForCurrentMode`（标签栏）
  与 `ClientRecipeBookMixin` 内的三档归组分派。
  - 命名空间档图标：`minecraft` → **草方块**；其它 → 该命名空间内产物的**稳定伪随机**（种子 = 命名空间 + 世界盐，
    池上限 64）；池空 → 该命名空间**第一个注册物品**（缓存，扫一次注册表）；再兜底知识之书。
  - 命名空间档排序：有代表创造标签的命名空间（模组）按创造标签顺序，其余（纯数据包命名空间）按名字典序。
  - tooltip：`ModNameUtil.resolveModName(命名空间)`（模组名，取不到就是命名空间原文），白色非斜体；
    归组不到 → `brbe.tab.namespace.unknown`「未知来源」。
  - 数据包档保持上一轮语义（本次仅修 bug A）。
- **pin 三键区**：`TabPinManager` 改为 `tabs` / `namespaceTabs` / `datapackTabs` 三份（`activeIds()` 按生效档位选），
  旧的 `compactTabs` 不直接采用，交 `TabPinManager.resolveLegacyCompact(packTest, nsMapper, packMapper)`
  **按内容分流**（包 id 形态 → `datapackTabs`；其余 → `namespaceTabs`（`ns:path` 取冒号前）；配不上的丢弃并记
  `[RBIP] tab pins: migrated/split N legacy keys`）；分流需要包索引 → 由 `RecipeBookIsPain.ensureExtendedPinKeysMigrated()`
  推迟到"进世界后首次打开配方书"（联机时索引不可用则留到单机）。旧的 `migrateCompactKeys` 删除。
- **热重载**：`RecipeBookIsPainExtendedConfig.reloadIfChanged()` 改为比对**生效档位名** —— 进出服务器引起的
  降级/恢复也会触发配方书重载。
- **lang（7 语言）**：新增 `…tabMode.NAMESPACE`、第 4 行 tooltip（[1] 命名空间说明、[2] 数据包说明、
  [3] 联机降级说明）、`brbe.tab.namespace.unknown`；`DATAPACK` 与其余保持。
- **1.21.11 的两个移植差异**：`RecipeBookTooltipMixin` 用的是 `setComponentTooltipForNextFrame(font, List.of(…), x, y)`
  （不是 26.x 的 `setTooltipForNextFrame(font, Component, x, y)`）；另外发现**上一轮的数据包档 pin 迁移在 1.21.11 根本没接线**
  （`withPackTabs` / `withPackFurnaceTabs` 里没有调用）→ 本次一并补上。

### 验证

- 三分支 `compileJava` 通过（26.2 首轮编译暴露移植脚本重跑导致的重复块 —— `pair` 的幂等判定只查"旧文本不在"，
  插入型替换的旧文本仍在 → 已加"新文本已在则跳过"并清理重复块）；`tools/mixin-check` 三分支全过；
  `tools/search-cache-harness/run.sh all` **ALL GREEN**。
- 部署 md5 / 备份 tag 见下方"部署"。
- 文档：`docs/RBIP数据包标签.md` → 重写并改名 **`docs/RBIP标签模式.md`**（三档语义、联机降级、pin 三分区、
  两个 bug 根因与证据、验证清单、已知边界）；根 `CLAUDE.md`（`rbip.tabMode` 行 + 存储作用域表）、
  `docs/INDEX.md`、`docs/BRBE数据存储清单.md` 同步。

**补丁（同日稍后，用户反馈）**：数据包档的**原版标签 tooltip 显示「默认」而不是 `Minecraft`** ——
原版数据包在游戏包列表里的官方标题就是「默认 / Default」，`RecipePackIndex.label()` 先查标题表，
`if (packKey.contains("vanilla")) …"Minecraft"` 这条兜底永远走不到（注释写了、代码没兑现）。
修复：把原版包判定提到标题表之前（新 `isVanillaPack()`：`vanilla` / 以 `/vanilla` 结尾）→ 固定 `Minecraft`，
与命名空间档 `minecraft` 标签（`ModNameUtil.resolveModName("minecraft")` 的兜底）一致。
三支同步、重新构建部署。

**补丁 2（同日稍后，用户提问）**：数据包标签 tooltip 改用**作者署名的项目名**。
反编译核实 `PackMetadataSection` **只有 `description` + `supportedFormats`，没有 name 字段** ——
游戏包列表里显示的"名字"只是**文件名**（`furnicraft-v7.5.zip`），作者的项目名写在 `description`
（如 `Ketket's FurniCraft` / `MasterCutter | Version 1.8 …`）。原实现先取 `Pack.getTitle()`（文件名）、
只在 title 为 null 时才退回 description —— 于是文件名永远赢。修复：`build()` 建标签表时分流 ——
**文件型数据包**（id 前缀 `file/`）优先 `getDescription()`，**模组自带数据包**仍以 `getTitle()`（模组名）为准，
空/取不到时逐级兜底；作者自带的 `§` 颜色码保留（根样式仍是白色非斜体）。
三支同步、重新构建部署。

**补丁 3（同日稍后，用户指定文案）**：两处 tooltip 改写（7 语言 ×3 分支，共 21 文件）——
①「保存配方书浏览记录」（`…option.saveRecipeBookPosition.@Tooltip` 与历史无点别名键）→
**「保存上一次关闭配方书时的浏览记录。」**（其余语言同步各自译法）；
②「标签模式」（`…rbip.tabMode.@Tooltip`）由**四行收成三行**，[1]/[2] 换成用户给的短句、
旧的 [3]（联机说明）并入 [2]：
```
① “创造模式物品栏”：用创造模式物品栏的物品分类数据来界定对应配方的分类。（沿用）
② “命名空间”：按配方所属的命名空间来分。
③ “数据包”：按配方所属的数据包来分。连接其他服务器时将回退到命名空间模式。
```
配套：`BrbeConfig` 的 `@Tooltip(count = 4)` → `3`，`TabModeGuiRegistrar.TOOLTIP_LINES` → `3`
（两处必须同步，否则自定义选择器会拼出空行）。

## 2026-10-01（二十二）：创造模式档一条配方进**多个**标签（多标签归组，用户反馈）

**用户反馈**：创造模式物品栏里**木桶同时在「红石方块」和「功能方块」**，但配方书里木桶配方只
出现在「功能方块」；要求"**还原优先于去重**"（`创造模式物品栏还原程度 > 配方重复情况`）。

**根因**：RBIP 给每条配方只算**一个**归属 —— `toRecipeBookGroup(ItemStack)` 走
`TAB_OVERRIDES` → 命名空间代表标签（`namespaceCache`）→ 物品自报（`rbip$getPossibleGroup`），
全是单值；而原版创造模式允许**同一物品挂在多个标签下**（`CreativeModeTab.getDisplayItems()`
里同一 ItemStack 出现在多个标签）。

**落地（三分支 26.3 / 26.2 / 1.21.11）**：

- `RecipeBookIsPain` 新增**物品 → 创造标签集合**反向索引 `ITEM_TABS`：
  - 数据源 = `MIRRORED_ITEM_GROUPS` 里每个标签的 `getDisplayItems()`；建索引前先自己调一次
    `CreativeModeTabs.tryRebuildTabContents(connection.enabledFeatures(), player.canUseGameMasterBlocks(),
    level.registryAccess())`（那份列表 vanilla 只在**打开创造模式界面**时建），随后
    `CreativeModeTabsAccessor.brbe$invalidateCachedParameters(null)` —— 与 `ensureInitialized()` 同款处理，
    否则创造模式界面会跳过自己的重建、连带不建 `SessionSearchTrees`（创造模式搜索会空白）。
  - 惰性建立；建成但为空时按 1 s 节流重试，**最多 3 次**（防内容一直建不起来时每秒重建）；
    `buildNamespaceCache()`（初始化 / Polymer 刷新）里 `invalidateItemTabsIndex()` 让它失效重建。
  - 新增 `tabsForItem(ItemStack)`、多值 `toRecipeBookGroups(ItemStack)` /
    `toFurnaceRecipeBookGroups(ItemStack, FurnaceVariant)`；`candidateTabs()` 顺序 =
    **覆盖表（叠加）** → 真实所在全部标签 → 命名空间代表标签（索引不可用回退）→ 物品自报。
    原单值 `toRecipeBookGroup(ItemStack)` / `toFurnaceRecipeBookGroup(...)` 改为取第一个（外部无调用者）。
- `ClientRecipeBookMixin`：创造模式档改用 `rbip$addCreativeEntry` / `rbip$addFurnaceCreativeEntry`
  （替换原 `rbip$getGroupForEntry` / `rbip$getFurnaceGroupForEntry`）——把条目加进产物所属的
  **每一个**分类；熔炉系同时把每个命中标签记进对应 `FURNACE/SMOKER/BLAST_*_ACTIVE_TABS`。
  命名空间 / 数据包档保持一配方一标签（它们按命名空间 / 包归组，与创造标签无关）。
- 日志新增一行：`[RBIP] creative item→tabs index: N items from M tabs`。

**副作用（有意，用户已确认方向）**：配方会在多个标签里重复出现；`TAB_OVERRIDES`（红石火把 →
红石方块）由"替换"变"叠加"（红石火把配方同时出现在红石方块与其原版归属标签里）。

**验证**：三分支 `compileJava` + `build -x test -x check` 通过；`tools/mixin-check` 三分支全过；
jar 内字节码含 `tabsForItem` / `toRecipeBookGroups` / `toFurnaceRecipeBookGroups` / `ITEM_TABS` 相关方法。
⚠️ **多标签效果未实机验证**（需进游戏确认木桶配方同时出现在两个标签里）。

**补丁（同日稍后，用户反馈）**：屏蔽原版「管理员用品」标签（`minecraft:op_blocks`）。
`shouldMirror()` 增加按**注册 id** 的排除 + `buildNamespaceCache()` 同样跳过
（否则物品仍可能被命名空间路由到一个不再显示的标签）；`registerNewGroup()` 复用 `shouldMirror` 一并生效。
只在那张表里出现的物品（命令方块一族 / 屏障 / 结构方块 / 拼图方块 / 光源方块 / 调试棒…）
不再有任何创造标签归属——它们原版都没有配方，配方书无损失。三支同步、重新构建部署。

## 2026-10-01（二十三）：RBIP 两档的标签图标 / tooltip / 排序改成"向创造模式物品栏对齐"（三分支同步，用户口述规则）

**用户规则（原话要点）**
- 数据包档：数据包**归属于 Mod** 时，图标 = 该 Mod 在创造模式物品栏里**序号最靠前**的标签图标
  （没有创造标签 → 仍按规则抽产物图标）；tooltip **优先数据包内的声明**，否则用 mod 的声明
  ——「不要这样标记"Fabric模组'XXX'"，直接打印标题」。
- 命名空间档：模组标签 tooltip **直接取 mod 声明里的模组名**（模组菜单里那行）；图标 = 该模组
  **序号最靠前的创造标签**图标；若该命名空间的配方在创造物品栏里**归于原版标签** → 在该命名空间的
  配方里抽图标。
- 两档共同：**原版标签的序号一定要最前**；图标需要从创造物品栏抽取、且**候选标签只有一个**时，
  该配方书标签的 tooltip 也采用那个创造标签的 tooltip（**特例**）。

**实现**
- 新增「来源 → 创造标签」索引 `RecipeBookIsPain.sourceCreativeTabs(key)`（key = 命名空间 / 模组 id）：
  ① 该来源自己注册的创造标签（注册 id 的命名空间 == key）→ ② 退而求其次：包含该命名空间物品的
  创造标签（`buildItemTabsIndex` 顺带建的 `TAB_NAMESPACES` 反向表）→ ③ **原版标签（`minecraft:*`）
  一律不算候选**。结果缓存在 `SOURCE_TABS`，与 `ITEM_TABS` / `TAB_NAMESPACES` 一同由
  `invalidateItemTabsIndex()` 失效（`buildNamespaceCache()`、`registerNewGroup()` 现在也会触发）。
- 图标：`nsIconStack` / `packIconStack` 改为「原版草方块 → `sourceIconStack(key)`（候选里**序号最靠前**
  那个的 `getIconItem()`）→ 产物池稳定伪随机 → 第一个注册物品 → 知识之书」。
  数据包档只在 `ModNameUtil.isModId(packKey)`（包 id 就是模组 id）时走创造标签图标。
- tooltip：`namespaceTabTooltip` 改用 `ModNameUtil.resolveModDisplayName(ns)`（**mod 声明名优先**，
  再 jade → 命名空间）；`packTabTooltip` 走 `RecipePackIndex.label`（规则见下）；两者都先试
  `singleCandidateTabTooltip(key)`（**候选恰好一个 → 用该创造标签的 `getDisplayName()`**）；
  统一 `white()`（白色非斜体）。
- 排序：`namespaceSortRank` / `packSortRank` 给原版键返回 **-1**（钉死最前），`#` 开头的
  「未知来源 / 服务器」返回 `MAX_VALUE` 且在字典序前判为"最后"（此前 `#unknown` 因 `'#'` 小于字母
  反而排最前，与文档意图不符，一并修正）。pin 仍优先于一切。
- `RecipePackIndex.computeLabel(packId, pack)`（新）：模组自带数据包（包 id 就是模组 id）→
  `pack.mcmeta` 的 **description 优先**，其次**模组声明名** `fabric.mod.json:name`（新增
  `ModNameUtil.metadataModName` / `isModId` / `resolveModDisplayName`）；文件型数据包 → description；
  兜底 title / description。**新增 `unwrapModPackTitle(Component)`**：Fabric 的
  `ModNioPackResources` 给模组数据包起的 title 是 `translatable("pack.name.fabricMod"[, ".subPack"],
  模组名)`（lang `zh_cn` = 「Fabric 模组 "%s"」——用户看到的「Fabric模组'XXX'」就是它），
  `TranslatableContents` 的**第一个参数就是裸模组名**，直接取出来用。

**为什么 26.3 实例上会看到"本地化名"**：Advanced Netherite / Beautify / Farmer's Delight / Naturalist
各只有**一个**创造标签 → 命中单候选特例 → tooltip 是创造标签的本地化名（「高级下界合金」「美化！」
「农夫乐事」「自然主义」）；纯数据包（Incendium / Dungeons and Taverns / MasterCutter / 两个
`file/…zip`）没有物品也没有标签 → tooltip 是作者写在 `pack.mcmeta` 的项目名；Nature's Compass
有物品但无自有标签 → tooltip = 模组声明名 + 产物图标。

**静态证据**：三分支 jar 内 `RecipeBookIsPain` 含 `sourceCreativeTabs` / `sourceIconStack` /
`singleCandidateTabTooltip` / `isVanillaCreativeTab` / `tabHoldsNamespace` / `creativeTabIcon` /
`namespaceSortRank` / `packSortRank` / `isUnknownKey` / `white`；`RecipePackIndex` 含 `computeLabel` /
`unwrapModPackTitle`（字节码 `instanceof TranslatableContents` + 常量 `pack.name.fabricMod`、
`pack.name.fabricMod.subPack`）；`ModNameUtil` 含 `metadataModName` / `isModId` /
`resolveModDisplayName`。`CreativeModeTabs.allTabs()` 的顺序 = `Registry.stream()` 走 `byId` 注册顺序
（反编译核实）→ 与创造模式物品栏标签顺序一致，`MIRRORED_ITEM_GROUPS.indexOf` 即"序号"。
1.21.11 部署 jar 是 intermediary 重映射产物，核对用 dev 类。

**实机反馈修补（同日稍后）**：用户截图显示命名空间档下 Nature's Compass 的标签 tooltip 是
`Naturescompass` 而不是 `Nature's Compass` → 查出 `ModNameUtil` 的**老反射 bug**：
`ModContainerImpl.getMetadata()` 返回的 `V1ModMetadata` 是**包私有 final class**（fabric-loader
0.19.5 javap），跨包 `impl.getClass().getMethod("getName").invoke(impl)` 抛
`IllegalAccessException`，被 `catch (Throwable ignored)` 静默吞掉 → 模组名退回"命名空间首字母大写"。
修复：改从公开 API 接口 (`api.ModContainer` / `api.metadata.ModMetadata`) 取 `Method` 再 invoke
（`invokeApi` 助手，同 `compat/ModPresence.invokeOn`），异常改为 `warnFailureOnce` 记一行日志。
顺带修好「显示物品来源模组」tooltip —— 它自诞生起一直走同一条失败路径。
（最小复现 + 真 loader `javap` 证据见 `docs/RBIP标签模式.md` §7.8。）

**部署**：原子替换四实例，备份 tag `20261001-235705`；
1.21.11-Fabric `a3508d9b2adc648807a0944c5ff1aeee`、
26.2-Fabric（+for-test）`64a516ddb30046a97d6f1d42fdbb5083`、
26.3-Fabric `edef7474da2742854e5b6a805a4d2d03`（第二轮含"索引重建时一并清 `SOURCE_TABS`"的补丁）。

⚠️ **未实机验证**（图标/tooltip 属视觉行为，需进游戏看）：见 `docs/RBIP标签模式.md` §7.7 与 §8 的
待验证清单。

**第三轮修补（2026-10-02，用户实测 + 提议）**：用户反馈**数据包档**里自然罗盘显示成
`naturescompass`（命名空间档已正常），并提议"先查模组再查数据包"。根因（反编译实锤）：
`fabric-resource-loader-v1` 的 `ModPackResourcesUtil.getName(ModMetadata)` 是
`if (getId() != null) return Component.literal(getId())` —— Fabric 给**没有 pack.mcmeta 的模组数据包
合成的那份元数据**里，名字就是**裸模组 id**；而当时的顺序是"数据包自己的声明优先"，于是把它当成了
标签名（有 pack.mcmeta 的模组则显示宣传语，如 naturalist 的「Naturalist Resources」）。
修复（`RecipePackIndex.computeLabel`，三分支同步）：① **模组声明优先**；② 非模组包才用
`pack.mcmeta` 的 description；③ 兜底 title 并把"与包 id 相同"的占位名判为无效丢弃；
④ `label()` 索引里查不到时**现场再查一次模组声明**，最后才退回包 id。
另加诊断日志 `[BRBE-PACK-INDEX] labels(text): {包 id=标签文本, …}`（与既有 `pack order` 对照）。
部署 tag `20261002-010745`：1.21.11 `0ac20a582a5f37ab7a6b5929f84697cb`、
26.2（+for-test）`0592557cd90c3a1bb8044fda77e010ad`、26.3 `c8994ccb4b9236e8a4da83d35da3c737`；
`tools/mixin-check` 三分支全过。详见 `docs/RBIP标签模式.md` §7.9。

**命名空间档改按「产物的命名空间」归组（2026-10-02，用户指令）**：用户要求"分类依据改为配方的**产物的
命名空间**，作为核心依据"，并把配置项 tooltip 的"……按配方所属的……"改成"……**按配方的产物所属的**……"。
- `ClientRecipeBookMixin.rbip$getNamespaceGroupForEntry`：**翻转优先级** —— ① `entry.resultItems()`
  第一个可解析产物 → `toNamespaceGroup`（主路径）；② 产物解析不出来 → `namespaceKeyOfRecipe(display)`
  （配方 id 的命名空间）；③ 都没有 → `#unknown`。产物照旧进该标签的图标随机池。
- 语义：数据包/模组给**原版物品**加配方 → 产物命名空间 = `minecraft` → 并进**原版标签**；
  产物是模组物品 → 进该模组标签。pin 键仍是命名空间字符串（同一 pin 可能指向内容不同的标签）。
- lang：`text.autoconfig.brbe.option.rbip.tabMode.@Tooltip[1]` 7 语言全部改写（zh_cn
  「按配方的产物所属的命名空间来分。」、zh_tw「按配方的產物所屬的命名空間來分。」、en
  "by the namespace their results belong to"、ja「レシピの産物が属する…」、pl/ru/tr 同步）。
- 三分支同步构建部署（备份 tag `20261002-110826`：1.21.11 `ced495cd254485ee91b13f7f7fb5f70c`、
  26.2 `6d6221000ff2367c53f9f8c79cbf3f6b`、26.3 `8ea9705fb2bab976e2fd3572ce97698c`）；
  `tools/mixin-check` 三分支全过。26.3 额外带一个**临时诊断**（`[RBIP-DIAG]`，调查 Guns++ 少数配方
  流入原版标签，上限 40 行/会话），查清后移除。

**LEI 栈级索引：物品 + 组件（2026-10-02，用户反馈"LEI 不好查询数据包物品"）**

用户观察正确：LEI 只认**物品注册 id**，而数据包物品普遍是"原版物品 id + 组件"（Guns++ 的 44 把枪
全是 `minecraft:carrot_on_a_stick` + 各自的 `item_model`/`custom_data`）→ 查一把枪等于查全部枪，
窗口重开后退化成裸物品。用户提议"按组件建索引"，确认方向后按 **方案 A**（栈级索引 + 分层匹配 +
持久化）落地，详见 `docs/LEI栈级索引.md`。

- 新增 `util/StackIdentity`：`IdentityKey`(物品 + 剔除易变组件后的 `DataComponentPatch`) /
  `ModelKey`(物品 + `minecraft:item_model`)；易变组件 = `custom_data`/`damage`/`lore`/`repair_cost`
  （用官方 `DataComponentPatch.forget(Predicate)`；26.3 的 `DataComponentPatch` **没有**公开
  `entrySet`，别手撸遍历）。
- `recipeviewer/engine/RecipeViewerEngine`：`RecipeTypeData` 新增 `outputByStack`/`inputByStack`/
  `outputByModel`/`inputByModel`，`resultsFor`/`usagesFor` 走**三级阶梯** ① 身份键 → ② 模型键 →
  ③ 物品 id（旧索引保留为兜底，行为不变）。
- `util/RecipeViewerOverlay`：`ViewSpec` 新增可选字段 `components`（补丁的 SNBT，
  `DataComponentPatch.CODEC` + `RegistryOps(NbtOps)`）；保存时写入、恢复时
  `applyEncodedPatch` 补回组件；老文件无该字段 → 物品级（行为同以前）。
- **离线验证**（无头专用服务器探针 `tools/brbe-screen-selftest/BrbeStackIdProbe.java`，遍历 Guns++
  全部 63 条配方）：`roundTripFail=0`（补丁→SNBT→TagParser→补丁 逐条 equals，745 字符的
  `custom_data` 组合也精确往返）、`identityFail=0`（把产物当"用过的枪"改写 `custom_data`+`damage`
  后 `fullEqual=false` 但 `identityEqual=true`）→ 见日志
  `[BRBE-SIDPROBE] checked=63 withComponents=63 roundTripFail=0 identityFail=0`。
- 已知边界（**待用户决定**）：pin 浮层 `pinoverlays.json` 仍只有物品 id（`PinSpec.resultItem` +
  `PinOverlay.itemFromKey`），pin 住的枪同样认不出是哪把；修法与本次同款但要改 pin 文件格式。
  查询窗口没有搜索框（本轮用户选择不做），真名/`item_model` 路径已确认可用于将来的搜索。
- 部署（备份 tag `20261002-112912`）：1.21.11 `44f23a0caafb3eadf0208b757aa700ea`、
  26.2 `175afdf8e65d299170fe462f8b1b21b0`、26.3 `dd69627fad3f5b19ad403618e463fd3c`；
  三分支 `compileJava`/`build` 通过，`tools/mixin-check` 全过。**实机效果待用户验证**
  （本仓库无测试套件，索引/查询是客户端运行时行为）。

**切石类别"数据包配方加载不出 JEI 界面"修复（2026-10-02，用户反馈）**

用户："LEI 的切石机类别内很多配方无法正常加载 JEI 界面，这些配方很有可能来自独立数据包"——
推测正确，实据（用户实例 `brbe-debug.log`）：引擎切石 **1731** 条 vs JEI 切石表 **351** 条，
而原版 26.3 的切石配方恰好 **351** 条、MasterCutter（世界里的独立数据包 zip）**1368** 条。

- **根因**：headless 的 `injectSyncedModRecipes()` 跳过**配方类型命名空间 = minecraft** 的同步配方
  （`if (typeKey.getNamespace().equals("minecraft")) continue;`，那条规则是给 mod 配方写的），
  而 JEI 自己的原版类型配方表在单机只拿到 `VanillaClientRecipeLoader` 的类路径原版配方 →
  "原版类型 + 数据包来源"的配方不在 JEI 表里 → `attachVanillaLayouts` 的 display 等价匹配挂不上
  → 无 native layout / 无 JEI 配方对象 → 弹窗退回原版双槽。锻造早有兜底（12+60），切石没有（351+0）。
- **修法**（三分支同步，`BrbeJeiBridge.attachStonecuttingFallbackLayouts`）：与锻造兜底同套路——
  遍历缺 layout 的切石条目；holder 先 `getRecipeFromDisplay(displayId)`（集成服务器 1:1、O(1)），
  否则用一次性建好的 display→holder 表（集成服务器全量 → fabric 同步集；避免 1380×1731 次嵌套
  equals）；几何拿**已挂上的切石 layout 当模板**（同类别几何一致）；槽内物品由条目自己的
  `StonecutterRecipeDisplay` 经 `SlotDisplay.resolveForStacks(ctx)` 现解；挂 layout + UID + RECIPE。
- ⚠️ **未实机验证**（依赖客户端 JEI 运行时，仓库无测试套件）。验证点：日志出现
  `stonecutting fallback: attached N datapack recipes missing from JEI's own recipe table (pending=…)`
  （N 应≈1380）；游戏内切石类别原空白配方应显示完整 JEI 界面。
- 更上游修法（未做，留选项）：headless-jei 的 `injectSyncedModRecipes()` 不再跳过 minecraft 类型
  （按 recipe id 去重注入），需改独立工程重出内嵌 jar；本次选 BRBE 侧兜底，改动面更小。
- 文档：`docs/LEI栈级索引.md` §4。部署（备份 tag `20261002-114631`）：1.21.11
  `5477c04b2de12a21002a46551a50eddf`、26.2 `31a840d3980f7e100a66c2c2085dbb2a`、
  26.3 `fb05835fb8a9b6b2206d77a380494e3e`。

**headless-jei 上游修复：JEI 配方表纳入数据包（2026-10-02，用户批准改 headless-jei 工程）**

用户指示"可以修改 headless-jei 工程，只要能达到目的即可，最好是让所有类别都能接入数据包"。
把上一轮只兜底切石的做法换成**上游修复**（`headless-jei/26.3` 两处，[BRBE fork] 注释标注）：

1. `mezz/jei/common/Internal.setClientRecipes`：上游 `if (connectionId != null)` 会在**集成服务器 /
   早期 tick**（拿不到 loggable address）时**整份丢弃**配方表 → 加 else 分支用哨兵 id `brbe:local`
   保留，`getClientRecipes()` 的 id 比较改 `Objects.equals`。26.2 / 1.21.11 的 fork 早已有同款修补
   （id `"embedded"`），故只有 26.3 缺这条。
2. `BrbeJeiHeadlessCore.start()`：改为优先 `clientRecipeMap()`（**客户端 RECIPE 注册表** = 服务器同步
   的全量配方，含数据包）→ 注册表未落地时**推迟启动**（≤60 tick，入口加
   `ClientTickEvents.END_CLIENT_TICK` → `BrbeJeiHeadlessCore.tick()` 重试）→ 最后才退回内置原版配方
   并打醒目日志。效果：JEI 自己的原版类型表带上数据包配方 → **切石/锻造/合成/烧炼等所有类别**都受益，
   BRBE 侧上一轮加的切石兜底保留为安全网。

- 产物：`headless-jei/26.3/build/libs/headless-jei-fabric-26.3-1.0.0.jar` md5
  `ca1bf8f5e1d7e49a32614a6d294becd6` → 同步进 `26.3/libs/` 与
  `26.3/src/main/resources/META-INF/jars/`（**jar-in-jar 取的是 resources 目录那份**，只换 libs
  不会进成品 jar——本轮踩过），重出 BRBE 26.3 `60c20681b5ea2ac812c8d14d7fddddb8`。
- ⚠️ **未实机验证**。验证点（`logs/brbe-debug.log`）：出现
  `client recipe map: N recipes (client RECIPE registry, datapacks included)`（N 应≈4090）且
  `vanilla runtime type minecraft:stonecutting: 1731 recipes indexed`（原为 351）；
  若出现 `BUNDLED VANILLA FALLBACK` 行说明客户端 RECIPE 注册表在启动时仍为空，需要换数据源
  （26.3 的 `RecipeMap.create` 只收 `HolderLookup`，没有 26.2/1.21.11 那种 Collection 重载）。
- 部署：备份 tag `20261002-120256`（26.3 `60c20681b5ea2ac812c8d14d7fddddb8`；26.2/1.21.11 本轮未改，
  仍是 `31a840d3980f7e100a66c2c2085dbb2a` / `5477c04b2de12a21002a46551a50eddf`）。

**回归修复：进存档"连接丢失"（2026-10-02，headless-jei 上游修复的副产物）**

上一轮给 `BrbeJeiHeadlessCore.start()` 加"推迟启动"后，**同一个 Fabric 网络包处理器**
（`ClientRecipeSynchronizedEvent` 回调）紧接着调用 `injectSyncedModRecipes()` →
`JeiRuntimeBridge.recipeManager()` → 上游 `Internal.getJeiRuntime()` 用
`Preconditions.checkState`（**未创建 runtime 时抛异常**）→ 异常从 `fabric:recipe_sync`
处理器逃逸 → 客户端 **disconnecting**（用户："进入存档就因为连接丢失退出存档了"）。
日志实锤：`Encountered exception while handling in channel with name "fabric:recipe_sync"` +
`IllegalStateException: Jei Runtime has not been created yet.`（栈顶 `BrbeJeiHeadlessCore.injectSyncedModRecipes`）。

三层加固（三分支同步，[BRBE fork] 注释）：
1. `JeiRuntimeBridge.recipeManager()/runtime()/runtimeAvailable()` 改用
   `Internal.getOptionalJeiRuntime()`，**永不抛异常**（该 bridge 在同步包 / tick 路径被调用）；
2. 同步包处理器整体 `try/catch(Throwable)` + 仅 `running` 时才 `injectSyncedModRecipes()` /
   `collectAndInject()`——**网络处理器里绝不能让异常逃逸**；
3. `start()` 的"推迟/无 level"日志限频（tick 重试不再刷屏）。

- 产物：headless 26.3 `e548d652878cda1ee5c1f8c6a37739a8`、26.2 `6978ef6ae9506431d46b2a35b465987b`、
  1.21.11 `8f39d99145f38aec6d2a1c7f87d803a9`（均已同步进各自 `libs/` 与
  `src/main/resources/META-INF/jars/`）；BRBE 三分支 `9a86f7a7c90e732ac1e160898fef76fd` /
  `6a80b5058c3e0653e45b4f1a97463312` / `78f3818a44a83fa295810e68ed155e36`。
- 部署：备份 tag `20261002-120935`；字节码核对内嵌 jar md5 + `getOptionalJeiRuntime` +
  `recipe sync handler failed` 串均 ✔。

**上游修复第二轮回归修复（2026-10-02 同日，接上一轮的连接丢失）**

用户反馈：重启后 **① JEI 原版类别（切石/锻造…）全都不加载 JEI 界面；② 酿造类别直接消失**。
日志实锤：`[BRBE-JEI-PLUGINS] client RECIPE registry not populated yet (attempt 60)`（推迟 60 tick 仍为空）
→ 走到 `Internal.hasClientRecipes()` 为 true（因为我把**空表**也落了库）→ 跳过内置原版兜底
→ JEI 零配方，上游报 `This server sent recipes to JEI, but none were usable.`（酿造类别因此空掉、消失）。

修法（三分支同步）：
1. `mezz/jei/common/Internal.setClientRecipes`：**空表绝不落库**（空表会让 `hasClientRecipes()` 变 true 而遮蔽兜底）；
2. `BrbeJeiHeadlessCore.start()`：只有**非空**表才喂给 JEI；兜底判据从 `!hasClientRecipes()` 改成
   `Internal.getClientSyncedRecipes().values().isEmpty()`（"JEI 手里没有可用配方"）；
3. **去掉"推迟启动"**：26.3 的客户端 RECIPE 注册表实测**始终为空**，推迟只是白等 3 秒。

**结论（重要）**：这轮"让所有原版类型都吃数据包配方"的上游目标**未达成**，JEI 回到改动前的可用状态
（内置原版配方兜底）；数据包配方仍靠 **BRBE 侧的切石兜底**（§4 前半段，已生效）。
26.3 的 `RecipeMap.create` 只有 `HolderLookup` 重载（26.2/1.21.11 有 Collection 重载），要另找数据源
（候选：单机集成服务器 `RecipeManager`、或自建 `HolderLookup` 喂 fabric 同步集）。
**再动这块前必须先想清楚验证手段**：本工程没有客户端测试回路，每轮都要用户进游戏试错。

部署（备份 tag `20261002-125717`）：26.3 `c3f1b0ba213d542d4e79573238df7bf3`、
26.2 `85ee629451a274942b05d0450fc51a57`、1.21.11 `dd9c364fb03b9f0dd7f3634e6bac2339`；
内嵌 headless 分别为 `453e689134c6b180704ec318fa625291` / `24dce2a75ab492a00a88d02ba6676b04` /
`1423b9735690ca65ca3d85920ff38c14`（逐一核对 ✔）。

**数据包档：注入条目按配方自己的 id 归属（2026-10-02，收掉 Guns++ 反馈）**

用户几轮前反馈"数据包档下 Guns++ 少数配方流入原版标签"。临时诊断（`[RBIP-DIAG]`，本轮用完已移除）
给出实锤：`fallback id=-33 injected=true recipeId=null packKey=null products=[minecraft:bamboo_chest_raft]`
—— **负 id = 本地缓存补全注入的条目**，其 display 是本地重建的（`CacheableRecipeDisplayEntry` 只存物品
id、不含数据组件），与服务端 display 不相等 → display 反查必然 miss → 回退"产物命名空间" → 原版标签。

修法（三分支同步）：
- `VanillaRecipeCache`：注入时记 **负 id → 配方 key**（`injectedRecipeKeys`，每 pass 重建，`clear()` 一并清）；
- `RecipeNamespaceIndex.recipeIdOfEntry(entry)`：负 id 直接取该 key，其余仍走 display 反查；
- `RecipeBookIsPain.packKeyOfRecipeEntry(entry)` + 集合端数据包档改用它；
- **命名空间档不动**（该档按用户 2026-10-02 的决定以**产物命名空间**为核心依据，注入条目按产物归属是对的）。

部署（备份 tag `20261002-131130` 附近）：26.3 `7553d9bf4136e0f691d01748abaf3f18`、
26.2 `afe6e5bb2dc3bd0cc9eed11235b54e07`、1.21.11 `f347f55e4d0750a600b22c47ff4a1edc`；
字节码核对 `packKeyOfRecipeEntry` / `recipeIdOfEntry` / `injectedRecipeKey` 齐备、`RBIP-DIAG` 已无残留；
`tools/mixin-check` 三分支全过。**实机效果待验证**（数据包档下 Guns++ 配方应全部落在 mr_guns 标签）。

**LEI「酿造」类别的数据包配方 —— 用户 2026-10-02 决定搁置**：`minecraft:brewing` 属原版类型，
headless 的 `injectSyncedModRecipes` 跳过它 → LEI 酿造类别的数据源（JEI 表）只有内置原版配方。
实测影响：实例里 `naturalist-2.0.6` 的 **12 条** brewing 配方在酿造**书**里有、LEI 里没有。
其余类别均已覆盖（切石/锻造有兜底，实机 1731=351+1380）。将来补法见 `docs/LEI栈级索引.md` §5.1
（⚠️ 只换数据源会让 92 条只挂上 35 条 layout，必须连固定几何兜底一起做）。

**首次打开配方书标签集体消失 —— 修（2026-10-02，用户反馈的老问题）**

现象：切档位（配置保存后不重启配方书）或进存档后**第一次**开配方书时标签全消失，重开才正常。
根因：标签栏的键取自**已被创建的分组对象**（`PACK_CRAFTING_GROUPS.keySet()` / `NS_…`），而分组是
集合构建时才懒创建的；原版 `initVisuals` 是 `updateTabs()` → `updateCollections()`，首次打开时分组表
为空 ⇒ 只剩搜索标签；集合跑完也不会自动重建标签栏 ⇒ 必须重开。

修法（三分支同步，`RecipeBookIsPain`）：`withPackTabs` / `withNamespaceTabs` 先调
`prewarmPackGroups()` / `prewarmNamespaceGroups()` —— 分组表为空时用**同一套归属函数**
（数据包档 `packKeyOfRecipeEntry`；命名空间档产物命名空间优先，与集合端一致）遍历
`RecipeViewerIndex.knownEntries()` 预创建分组。结果与集合构建一致，不会多出空标签。

部署（备份 tag `20261002-151745`）：26.3 `0fbb4687f7207bef3e0ad9f351870200`、
26.2 `7e8ad7a1bd86b80e33877f2e106cbdac`、1.21.11 `ebca3119dd2521a0cc1851aaa04d7799`。
**实机效果待验证**（首次开书即应显示全部标签）。

---
**Incendium 数据包标签只剩两条 —— 调查结论：不是 bug（2026-10-02）**

用户反馈"开启数据包后 Incendium 标签只剩红石中继器与比较器"。核查：Incendium 共 8 条配方
（1 smithing + 7 条原版红石配方覆盖：lever/dropper/dispenser/observer/piston/repeater/comparator），
而 **Stellarity 也在 base 路径**（非其那几个在 121 上不生效的 overlay）覆盖了其中 **5 条**
（lever/dropper/dispenser/observer/piston）。资源栈里 Stellarity 优先级更高 ⇒ 这 5 条按定义
"由 Stellarity 提供"，出现在 **stellarity 标签**；Incendium 标签只剩它独占的 repeater + comparator。
**行为符合数据包档定义，未做改动**。若要"每个提供者都显示一份"，需要把
`sourcePackId`（只取胜者）改成枚举全部提供者（语义变更，待用户决定）。

---
**2026-10-02（四）：数据包档「每个提供者都显示一份」+ JEI 预览候选角标抑制（三分支同步，已部署）**

① **多提供者标签**（用户指令"我希望「每个提供者都显示一份」"；"Incendium 标签只剩 2 条"经核查**不是 bug**——
Stellarity 也在 base 路径覆盖了其中 5 条原版红石配方 lever/dropper/dispenser/observer/piston，资源栈优先
⇒ 按数据包档定义归 Stellarity）：
- `RecipePackIndex`：新增 `PACKS_BY_RESOURCE`（资源键 → **全部**提供者，资源栈顺序、胜者在最前）+
  `packKeysOf(Identifier)`；数据源 `ResourceManager.listResourceStacks("recipe", id -> true)`
  （26.2 / 1.21.11 与 26.3 只差第二个参数类型 `ResourceManager$Selector`，lambda 两者都吃），
  拿不到 stacks 时**退化为"只有胜者"**（与改动前一致）；`clear()` 与冻结快照同步。
- `RecipeBookIsPain.packKeysOfRecipeEntry(entry)`（全部提供者；为空时：包索引不可用 → `#unknown`，
  否则 `packKeyForNamespace` 单键）；旧 `packKeyOfRecipeEntry` 语义不变、保留。
- `ClientRecipeBookMixin`：数据包档两处调用点改**循环**（`rbip$getCompactGroupForEntry` →
  `rbip$getCompactGroupsForEntry`，返回 `java.util.List<ExtendedRecipeBookCategory>`），每个提供者的标签
  各放一份条目、产物进**每个**标签的图标池、`PACK_*_ACTIVE` 对每个 group 都记。
- `prewarmPackGroups()` 同步改成遍历全部提供者（与集合构建结果一致，见上一条的"标签集体消失"修复）。
- 26.3 先落地，本轮把同三处改动移植到 26.2 / 1.21.11（字面量锚点脚本，锚点全中才写文件）。

② **JEI 预览"候选 / 标签"角标抑制（用户 2026-10-02 选定方案 A）**：
- headless fork `mezz/jei/library/gui/ingredients/RecipeSlot.java`：新增
  `public static volatile boolean brbeSuppressCandidatesBadge`，`drawCandidatesBadge` 首行短路
  （26.3 / 1.21.11 fork；**26.2 fork 没有这个角标**，不改）。
- BRBE `jei/plugins/engine/SyntheticRecipeRendererImpl.render`：用**反射**置位 / 复位，只包住
  `drawable.drawRecipe(...)`（helper `brbe$setCandidatesBadgeSuppressed`，字段只查一次、找不到静默跳过）。
- `drawFrozenBadges`（Alt 冻结槽位的功能标记，2026-09-13 诉求 3）**不在抑制范围内**。
- fork jar 重新构建并同步进 `libs/` 与 `src/main/resources/META-INF/jars/`（**jar-in-jar 才真正生效**）；
  字节码核对：`drawCandidatesBadge` 首两条 = `getstatic brbeSuppressCandidatesBadge / ifne`。

**验证**：三分支 `compileJava` + `build` 全绿；`tools/mixin-check/check.py --branch 1.21.11 --branch 26.2
--branch 26.3` → **全部通过**；mixin 类 `javap -p` 无 `lambda$`（新代码用 for 循环，未引入 lambda）；
主 jar 内嵌 fork jar md5 = 源 jar（26.3 `6fb6ebf0bc8d`、1.21.11 `023799064f98`）。
**实机效果待用户验证**：被多处覆盖的配方（如 Incendium 的 5 条红石配方）应**同时**出现在 Incendium 与
stellarity 标签；JEI 预览 / pin 的槽位右上角不再有 tag / list 角标。

**部署**（备份 tag `20261002-163610`，四实例原子替换）：1.21.11 `771a625425537b9b70ff16ae17dcc097`、
26.2 `7d47450e64668c03ffd5e4a8b43db3b0`、26.3 `a8b91868e6ae66e390ba01be70868198`。

---

## 2026-10-02（五）：配置界面 —— 类别「配方」改名「行为」+ 三个条目置顶（四分支同步，已部署）

**用户指令**："将类别「配方」改为「行为」；然后将「自动填充幽灵配方」「保存配方书浏览记录」
「循环滚动」这三个配置项移动到行为类别的顶部，相对顺序不变。"

**实现**（`util/ConfigTipsHelper.java`，四个分支同步）：

1. **改名只改显示文本**：`text.autoconfig.brbe.category.recipeSettings` 的 7 语言值
   zh_cn 配方→**行为**、zh_tw 配方→**行為**、en_us Recipes→**Behavior**、ja_jp レシピ→**挙動**、
   pl_pl Receptury→**Zachowanie**、ru_ru Рецепты→**Поведение**、tr_tr Tarifler→**Davranış**。
   ⚠️ **类别 id（`recipeSettings`）、`@ConfigEntry.Category` 注解、字段名、TOML 路径一律不动** ——
   Cloth 的 category 只影响 GUI 分组，序列化按字段名走，所以**没有配置迁移问题**；
   代码/文档里看到 id 仍是 `recipeSettings` 属正常（注释已标注显示名）。
2. **三个条目置顶**：`relocateEntries` 新增一步（26.3/26.2/1.21.11 = 第 6 步，1.21.1 = 第 7 步）——
   按新常量 `BEHAVIOR_TOP_OPTION_KEYS`
   （`…option.autoFillGhostRecipe` → `…option.saveRecipeBookPosition` → `…option.scrolling.scrollAround`）
   依次从**「功能」页**（前两个是无 `@Category` 的顶层字段，第三个是 `scrolling` 子对象字段）
   摘出条目对象，再 `recipeEntries.addAll(0, …)` 放进该页最前 —— 相对顺序 = 常量顺序。
   三者在原文里本来就被 `pinyinSearch` / `showModName` 等隔开（不是连续的），所以是逐条摘取。

**位置细节**：`@PrefixText` 黄字行由 AutoConfig 插在**该字段自己那一组**的第 0 位
（反编译 `DefaultGuiTransformers`：`ArrayList.add(I, Object)`），所以「行为」页原本以
`unlockAll` 的黄字行「§e更好的过滤器」开头；本次按"顶部"的字面语义把三条插在**该黄字行之上**。
若希望它们落在黄字行下面，把 `addAll(0, …)` 的下标改成 1 即可（一行）。

**验证**：四分支 `compileJava` / `build` 全绿；7 语言 JSON 解析通过；
部署 jar 内实测 zh_cn 的 `category.recipeSettings` = **行为**，且 `ConfigTipsHelper.class` 的常量池里
三个 option key 与 `BEHAVIOR_TOP_OPTION_KEYS` 均在；`tools/mixin-check/check.py` 四个分支**全部通过**。

**部署**（备份 tag `20261002-215030`，六个实例原子替换）：
26.3 `264e29716844cd71029da49a928eb0dd`、26.2 `a5da06a3253d5887e9f9c1493a4c8657`、
1.21.11 `a92e69636765d62dc730df56767c92a6`、1.21.1-Fabric `97fc8752ad2f2d82833fc80352e4b747`、
1.21.1-NeoForge `52446064213412c402264e0cfc98a16d`。
**界面观感待用户验证**（打开配置界面：「行为」页最上面三条依次为 自动填充幽灵配方 /
保存配方书浏览记录 / 循环滚动，其后才是「§e更好的过滤器」黄字行与原有选项）。

---

## 2026-10-02（七）：标签"集体消失"老 bug 的**真正根因**与修复（26.3 / 26.2 / 1.21.11 同步，已部署）

**用户反馈**（第三次）："重新进入存档后的第一次打开配方书**或者**切换 RBIP 的标签模式后，
搜索标签以外的标签集体消失。"（§7.12 的 prewarm 修的是**另一半**，所以一直没好）

**根因（这次读用户实例真实日志 + 代码定位）**：标签其实**都建出来了**，消失发生在
`RecipeBookWidgetMixin.rbip$paginateTabButtons`（`updateTabs` TAIL）里那段
"**category 没有集合就隐藏**"：

```java
List<RecipeCollection> collections = this.book.getCollection(widget.getCategory());
if (collections == null || collections.isEmpty()) { widget.visible = false; continue; }
```

- 标签栏的键来自**分组对象**（`PACK_CRAFTING_GROUPS` / `NS_CRAFTING_GROUPS` / 创造镜像组），
  而集合（`collectionsByTab`）只在 `ClientRecipeBook.rebuildCollections()` 里由 RBIP 的
  TAIL 注入生成（26.3 vanilla 只有 `ClientPacketListener.refreshRecipeBook` 一个调用点）。
- **首次打开配方书**：`initVisuals()` 是 `updateTabs()` → `updateCollections()`，而此刻
  `collectionsByTab` 还是「RBIP 之前」的版本（登录期的那些 rebuild 跑在分组/档位就绪之前）
  → 每个扩展标签都取不到集合 → **整排隐藏，只剩搜索标签**（搜索标签的原版分类有集合）。
  用户实例日志实证：22:41:11.881 首次开书（`[BRBE-MERGE]` 管线）→ 22:41:12.028 才出现
  `[BRBE-CACHE] rebuild RETURN — known=3631`（集合此刻才重建）→ 第二次开书就正常。
- **切档位**：新档位的分组对象是**刚被 `prewarm*Groups()` 建出来**的（集合一个都还没有），
  若这一次 `updateTabs` 跑在"为新档位重建集合"之前，同样整排隐藏。

**修法**（`rbip$paginateTabButtons`，三分支同步）：
1. **隐藏之前先让集合与当前标签栏同代** —— 只要有**非搜索**标签取不到集合，就
   `this.book.rebuildCollections()` 强制重建一次（**500ms 去抖**；正常状态下不会触发，
   因为重建后每个真有配方的标签都有集合），然后按重建后的结果继续判断。
2. **兜底**：若重建之后**每一个**非搜索标签仍取不到集合（说明集合表与标签不是同一套 =
   数据管线不同步），本轮**不隐藏**任何标签 —— 宁可标签在、页面暂空（`getCollection` 对
   未知分类返回空列表，不会崩），也不要"标签集体消失"。
3. **一条低频日志**（只在触发时打）：
   `[RBIP] updateTabs: stale collections for the current tab set (X/Y tabs without) → rebuilt
   collections, still without = Z` —— 以后再出问题，日志直接说明是"没重建"还是"重建了也对不上"。

**为什么不改 prewarm**：§7.12 的 prewarm 解决的是"分组对象不存在 → 标签没建出来"，本次是
"标签建出来了但集合对不上"，两件事；prewarm 保留不动。

**验证**：三分支 `compileJava` + `build` 全绿；`javap` 核对部署 jar 内
`RecipeBookWidgetMixin` 有 `rbip$countTabsWithoutCollection` / `rbip$countNonSearchTabs` /
`rbip$lastCollectionsRefreshAt` 且**无 `lambda$`**；`tools/mixin-check` 三分支**全部通过**。
**实机效果待用户验证**：进存档后第一次开书、切档位后第一次开书，标签都应完整。

**部署**（备份 tag `20261002-224928`）：26.3 `036d31264fad0eb4e9e58cb9cdb3caf1`、
26.2 `24bf22c2e17ee9866e4f536c4d54a604`、1.21.11 `35332f1af03f22310ae8cd7fd5f8556b`。
（1.21.1 无 RBIP 三档标签栏，代码路径不存在，未改。）

---

## 2026-10-03（八）：配方书内**切标签档位**后标签集体消失 —— vanilla 顺序陷阱（26.3 / 26.2 / 1.21.11，已部署）

**用户反馈**："进存档后第一次打开配方书的标签显示正常了，但在配方书内切换标签模式后的标签显示
问题还是没有解决。"（§7 修的是首次打开那一半）

**根因（vanilla 26.3 字节码实证，`javap -c`）**：vanilla 的刷新顺序是

```
RecipeBookComponent.init(w,h,mc,tooNarrow)
  └ initVisuals():
      ① 按**当前 tabInfos** 现造 RecipeBookTabButton   （offset 343-357）
      ② selectMatchingRecipes()  遍历 tabInfos → 每个集合 selectRecipes() → selected 非空（444）
      ③ updateTabs()             （449）
      ④ updateCollections(...)   （455）
```

- `updateTabs(boolean)` **只重排已有按钮**（`tabButtons` 由 ① 造），不会为新分类造按钮；
  它对每个按钮调 `RecipeBookTabButton.updateVisibility(book)` = **"该分类至少有一个集合
  `hasAnySelected()`（`selected` 非空）才可见"**；`SearchRecipeBookCategory` 在 vanilla 里
  **恒可见**（offset 74-90 `instanceof SearchRecipeBookCategory → visible = true`）。
- 旧代码两处踩了这个顺序：
  1. `rbip$syncLateGroups` 只在 **`updateTabs` 的 HEAD** 按新档位重建 `tabInfos` —— 对 ① 来说
     太晚 ⇒ 新档位的分类**一个按钮都没有**；
  2. `rbip$hotReloadOnConfigChange` 切档后只做 `book.rebuildCollections()` + `updateTabs(false)`
     —— 刚重建的集合 `selected` 是空的（`RecipeCollection` 构造器只 new 一个空 `HashSet`，
     `javap` 实证）⇒ `updateVisibility()` 全 false ⇒ **除搜索标签外整排隐藏**。
- 首次打开之所以正常：那条路径不触发 hotReload，且集合已经被上一轮 `selectMatchingRecipes()`
  选过（§7 的强制重建把"集合缺失"那一半补上了）。

**修法（三分支同步，`RecipeBookWidgetMixin` + `RecipeBookIsPainExtendedConfig`）**：

1. **`RecipeBookComponent.init` HEAD 新注入 `rbip$rebuildTabsOnInit`**：比对「标签布局指纹」
   （`RecipeBookIsPainExtendedConfig.tabLayoutKey()` = 生效档位 + RBIP 开关 + 每页标签数，
   `<init>` 时种下），变了就在 **vanilla 造按钮之前** 重建 `tabInfos`（`rbip$rebuildTabInfosForCurrentMode`）
   并 `book.rebuildCollections()` ⇒ 随后 vanilla 的 ①②③④ 自然把按钮、选中集合、可见性都做对。
2. **`rbip$hotReloadOnConfigChange` 改走 vanilla 的完整顺序**：
   `rbip$rebuildTabInfosForCurrentMode()` → `book.rebuildCollections()` → **`this.recipesUpdated()`**
   （= `selectMatchingRecipes()` 遍历 tabInfos 全量重选 → `updateTabs()` → `updateCollections()`）。
   `recipesUpdated()` 是 vanilla 的 public 入口，新增 `@Shadow public void recipesUpdated()`。
3. 上一轮加的「集合缺失就强制重建 + 全空时不隐藏」兜底保留（`rbip$paginateTabButtons`）。

**验证**：三分支 `compileJava` + `build` 全绿；`javap` 核对部署 jar：`rbip$rebuildTabsOnInit` /
`rbip$rebuildTabInfosForCurrentMode` / `tabLayoutKey` 均在，mixin 类**无 `lambda$`**；
`tools/mixin-check` 三分支**全部通过**（含新 `@Inject(method="init")` 与 `recipesUpdated` shadow）。
**实机效果待用户验证**：配方书内 → 设置 → 切换标签模式 → 返回，标签应完整（不必重开配方书）。

**仍未覆盖的边角**：配置在**不触发界面重开**的情况下被改（例如从 ModMenu 改完直接返回当前容器，
或外部编辑 `brbe.toml`）时，按钮列表不会重建（新档位的分类没有按钮）——这条路径仍靠 §7 的兜底
与日志 `[RBIP] updateTabs: stale collections …` 提示；用户实际路径（配方书内设置按钮 / ModMenu /
暂停菜单）都会走 `setScreen(parent)` → `init`，已被本次修复覆盖。

**部署**（备份 tag `20261003-011330`）：26.3 `0c34c357b8e0d6b26c69c1567a7f45f1`、
26.2 `3b1c76f427f9538d69f4ec6ad603edcf`、1.21.11 `7f07a1a4f8d4b74a0181eb22d014bd71`。
（1.21.1 无 RBIP 三档标签栏，未改。）

---

## 2026-10-03（九）：配置项「配方区翻页方向」—— 布尔改枚举（自然 / 常规）+ 旧值迁移（四分支同步，已部署）

**用户指令**："将「在配方区使用自然的翻页方向」改为「配方区翻页方向」；然后把配置项类型改为
枚举模式，设两个模式，一个是自然，一个是常规，常规对应原来的 false，自然对应原来的 true。"

**语义（与旧布尔一一对应）**

| 枚举值 | 显示名 | 含义 | 旧值 |
|---|---|---|---|
| `NATURAL` | 自然 | 鼠标滚轮向前（上滚）＝往后翻页 | `true` |
| `REGULAR` | 常规 | 旧方向：上滚＝往前翻页（**默认**） | `false` |

**落地**（`26.3` / `26.2` / `1.21.11` / `1.21.1` 四分支同步）：

1. **配置字段**：`BrbeConfig.naturalPageDirection`（boolean）→
   **`BrbeConfig.pageFlipDirection`**（新枚举 `BrbeConfig.PageFlipDirection { NATURAL, REGULAR }`，
   默认 `REGULAR`）。枚举实现 `SelectionListEntry.Translatable#getKey()` 返回
   `text.autoconfig.brbe.option.pageFlipDirection.<常量名>` —— **实证** Cloth 的
   `DefaultGuiProviders.DEFAULT_NAME_PROVIDER` 对 `instanceof SelectionListEntry.Translatable`
   走 `getKey()`（`javap -c` 字节码），所以 AutoConfig 内置的枚举选择器就够用，
   **不需要**像 `tabMode` 那样写自定义 provider / transformer。
2. **旧 TOML 迁移**：新增 `migrateLegacyPageFlipDirectionInToml()`，在 **AutoConfig 注册之前**把
   `naturalPageDirection = true|false` 正则改写成 `pageFlipDirection = "NATURAL"|"REGULAR"`。
   挂点：26.x 在 `BetterRecipeBook.init()`（紧接 `migrateLegacyTabModeInToml()`）；
   **1.21.1 在 `config/AppContext` 构造器**（该分支的 `AutoConfig.register` 在那里）。
   ⚠️ 不做迁移的话旧布尔值会让枚举反序列化抛异常 → Cloth 的 register 整体失败 → **整份配置丢失**
   （与 `tabMode = "COMPACT"` 同源，见 §7 的教训）。迁移失败只记一行 WARN。
3. **取值处**：`RecipeViewerOverlay.naturalPageDirection()` 改读枚举
   （`config == null` → 自然；字段为 null → 自然；否则 `direction.natural()`），调用点
   `int delta = (vertical > 0) == naturalPageDirection() ? 1 : -1;` **未变** —— 行为与改动前一致。
4. **语言**（4 分支 × 7 语言）：键 `…option.naturalPageDirection` / `…@Tooltip` 改名为
   `…option.pageFlipDirection` / `…@Tooltip`，并新增两档名 `…pageFlipDirection.NATURAL` /
   `…pageFlipDirection.REGULAR`。文案：标题 zh_cn「配方区翻页方向」/ zh_tw「配方區翻頁方向」/
   en「Recipe Area Page Turn Direction」/ ja「レシピエリアのページ送り方向」/
   pl「Kierunek przewracania stron w obszarze przepisów」/ ru「Направление перелистывания в области рецептов」/
   tr「Tarif alanında sayfa çevirme yönü」；两档名 自然·常规 / 自然·常規 / Natural·Regular /
   自然·通常 / Naturalny·Zwykły / Естественное·Обычное / Doğal·Normal；
   tooltip 改成描述两档（「自然：滚轮上滚＝往后翻页；常规：滚轮下滚＝往后翻页。」等 7 语言）。

**验证**：四分支 `compileJava` / `build` 全绿；`javap` 核对部署 jar 内有
`BrbeConfig$PageFlipDirection` 类、字段 `pageFlipDirection` 与常量 `NATURAL`/`REGULAR`/`getKey()`/`natural()`；
jar 内 `assets/brbe/lang/zh_cn.json` 的 `…pageFlipDirection` = 「配方区翻页方向」、
`.NATURAL` = 「自然」、`.REGULAR` = 「常规」；28 个语言文件 JSON 解析通过且旧键已消失；
`tools/mixin-check` **四分支全部通过**（本轮未改 mixin）。

**部署**（备份 tag `20261003-134740`，六实例原子替换）：
26.3 `b50e73d0e0434c05f0812bf778d62a11`、26.2 `64e10682dbee63fc9e24bc21e5c41fd8`、
1.21.11 `11b93b0408fb568abf41c39777d58e33`、1.21.1-Fabric `bc0b7d82fd553e7c1b63e2bdafab1173`、
1.21.1-NeoForge `e6dcc012a6eec53f3173ac1706790905`。
**实机待用户验证**：配置界面该行应是枚举选择器（自然 / 常规），旧 `brbe.toml` 里若原本是
`naturalPageDirection = true`，首次启动会看到日志
`[BRBE] Migrated brbe.toml page direction: naturalPageDirection -> pageFlipDirection`
且该行变成 `pageFlipDirection = "NATURAL"`。

> 说明：2026-09-13（五十三）那条轮次记录里的字段名 / 类型（`boolean naturalPageDirection`）
> 已被本轮取代，仅作历史存档。

---

## 2026-10-03（十）：枚举项「只有一个档位」的根因 —— 缺 `@EnumHandler(BUTTON)`（四分支同步，已部署）

**用户反馈**："枚举模式不应该就是「标签模式」配置项那样吗？「配方区翻页方向」采用了什么配置类型？"
以及"「配方区翻页方向」只有「常规」一个项"。

**根因（Cloth Config `DefaultGuiProviders` 字节码实证）**：AutoConfig 给**枚举字段**注册了**两条** GUI 路径 ——

| 路径 | 谓词 | Builder | 控件 |
|---|---|---|---|
| ① `lambda$apply$16` | `lambda$apply$19`：`isEnum()` **且** 字段带 `@ConfigEntry.Gui.EnumHandler` 且 `option()==BUTTON` | `startSelector(...)` | **`SelectionListEntry`＝枚举切换按钮**（点一下切下一档） |
| ② `lambda$apply$20` | `lambda$apply$24`：**只要求 `isEnum()`** | `startDropdownMenu(...).setSelections(...)` | **`DropdownBoxEntry`＝可搜索下拉框** |

① 在 `apply()` 里**先注册**（offset 144）② 在后（offset 158/179），而 `GuiRegistry` 取值用 `findFirst`
→ **带注解的枚举字段走切换按钮，不带的落到下拉框**。

「标签模式」（`rbip.tabMode`）一直带着
`@ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)`；
本轮（九）新加的 `pageFlipDirection` **漏了这个注解** → 走 ② —— 实测下拉框的候选只剩**当前档位**
（用户看到的"只有『常规』一个项"，无法选到「自然」）。

> 四种在用的 Cloth（1.21.1 的 `15.0.140`、1.21.11 的 `21.11.153`、26.2 的 `26.2.155`、
> 26.3 的 `26.3.158`）**结构完全一致**：`startSelector` ×1、`startDropdownMenu` ×1、
> `EnumDisplayOption.BUTTON` 常量都在 —— 所以该注解是**跨分支通用**的修法。
> （`startEnumSelector` / `EnumListEntry` 在 AutoConfig 里**从未被调用**，只是 Cloth 的公开 API。）

**修复**：`BrbeConfig.pageFlipDirection` 补上
`@ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)`
（与 `tabMode` 逐字一致），并加注释说明"漏了它会退化成下拉框"。
验证：`javap -v` 部署 jar 内 `BrbeConfig.class` 的 `pageFlipDirection` 字段
`RuntimeVisibleAnnotations` 已含 `ConfigEntry$Gui$Tooltip` + `ConfigEntry$Gui$EnumHandler(option=BUTTON)`。

> 订正（九）里"不需要自定义 provider / transformer"的说法：**结论仍成立**（带注解后
> AutoConfig 自己就生成与「标签模式」**同一个控件类** `SelectionListEntry`），但当时**漏掉了
> 那个必需的注解** —— 「标签模式」之所以正常，正是因为它带着 `@EnumHandler(BUTTON)`。

**部署**（备份 tag `20261003-141341`，六实例原子替换）：
26.3 `b2b07351e24970e4fcd072a503422ebf`、26.2 `f434dc4fc0b445af8e35ece859113738`、
1.21.11 `f2cf69a82293e6f8a076881890026b53`、1.21.1-Fabric `cce60b227e4ca0815ce7aea0cf3f395f`、
1.21.1-NeoForge `1e2c65c9c3f11de816ac9f2180bcfb82`；`tools/mixin-check` 四分支全部通过。

**实机待验证**：配置界面「配方区翻页方向」应是一个**切换按钮**（显示当前档「常规」），
点一下变成「自然」、再点回「常规」，与「标签模式」的交互完全一致。

---

## 2026-10-03（十一）：两项布尔 → 枚举 ——「预览模式」→「窗口模式」、「配方书模式」→「查询范围」（四分支同步，已部署）

**用户指令**：① 「预览模式」改为「窗口模式」，类型改为枚举，两档「持久」（原 `false`）/「预览」（原 `true`）；
② 「配方书模式」改为「查询范围」，类型改为枚举，两档「仅限配方书」（原 `true`）/「全类别」（原 `false`）。

| 旧字段（布尔） | 新字段（枚举） | 档位（旧值） | 默认 |
|---|---|---|---|
| `previewMode` | `windowMode`（`WindowMode`） | `PERSISTENT`＝持久（`false`）· `PREVIEW`＝预览（`true`） | 持久 |
| `hideNoRecipeBookStationObjects` | `queryScope`（`QueryScope`） | `RECIPE_BOOK_ONLY`＝仅限配方书（`true`）· `ALL_CATEGORIES`＝全类别（`false`） | 全类别 |

**落地**（26.3 / 26.2 / 1.21.11 / 1.21.1；1.21.1 无 `previewMode`，只做 `queryScope`）：

1. **配置字段**：`BrbeConfig` 新增嵌套枚举 `WindowMode` / `QueryScope`（都实现
   `SelectionListEntry.Translatable#getKey()`，档位名键 `…option.windowMode.<常量>` /
   `…option.queryScope.<常量>`）。两者都带
   `@ConfigEntry.Gui.EnumHandler(option = BUTTON)` —— **（十）的教训**：不带它 AutoConfig 会生成
   `DropdownBoxEntry` 可搜索下拉框（候选只剩当前档位），带上才走枚举切换按钮（`SelectionListEntry`）。
2. **读取点**：字段改名为 `windowMode` / `queryScope`，所有读取点机械替换为
   `BetterRecipeBook.config.previewMode()` / `recipeBookOnly()`（`BrbeConfig` 上的便捷方法，
   语义与旧布尔一致；字段缺失/为 null 时按各自默认档）。替换处数：26.x / 1.21.11 各 3 + 16，1.21.1 12。
3. **1.21.1 特有坑：`RecipeViewerGuiRegistrar`**（26.x/1.21.11 没有这个类）——它用 predicate
   provider 接管 `recipeViewerEnabled` 与旧 `hideNoRecipeBookStationObjects` 的渲染（`brbe.disableRecipeViewer`
   屏蔽时**整段隐藏**，含"查询合成/用途"标题行）。字段一旦变成枚举，它的
   `field.getBoolean/setBoolean` 会抛 `IllegalArgumentException`（被 catch 后静默 fallback = 那一行
   变成点不动的开关）。修复：`VIEWER_BOOLEAN_FIELDS` → `VIEWER_FIELDS`，并在 provider 里为
   `queryScope`（新常量 `QUERY_SCOPE_FIELD`）**单独造同款 `startSelector` 选择器** ——
   既保住"屏蔽时整段隐藏"的门控，又避免被 AutoConfig 内置枚举 provider 抢回去。
4. **TOML 迁移**：`migrateLegacyPageFlipDirectionInToml()` 改名 **`migrateLegacyConfigValuesInToml()`**，
   一次处理三个布尔项 → 枚举值（26.x 三条；1.21.1 两条）。仍然必须在 `AutoConfig.register` **之前**
   跑（旧布尔值会让枚举反序列化失败 → 整份配置丢失），日志改为
   `Migrated brbe.toml legacy boolean options to enums` / 失败 `legacy option TOML migration failed`。
5. **语言**（4 分支 × 7 语言）：键 `…option.hideNoRecipeBookStationObjects` →
   `…option.queryScope`、`…option.previewMode` → `…option.windowMode`，各自新增两档名，tooltip 改成
   **描述两档**（如「仅限配方书：只显示配方书内的对象；全类别：不按配方书体系过滤。」、
   「持久：重新开启界面时查询窗口恢复；预览：不再恢复，与其他元素交互时也会关闭查询窗口。」）。
   26.x/1.21.11 里那个旧式无点重复键（`…hideNoRecipeBookStationObjects@Tooltip`）一并改名。
   ⚠️ **1.21.1 的显示名对齐（披露）**：该分支这一项原本叫「隐藏无配方书工作站所属的对象」且
   **没有 tooltip**（字段没带 `@ConfigEntry.Gui.Tooltip`）——本轮为跨分支一致，统一成
   「查询范围」+ 两档名 + 新 tooltip（补上 `@ConfigEntry.Gui.Tooltip` 注解）。如要保留 1.21.1 旧名，改 7 个
   `option.queryScope` 的值即可。

**验证**：四分支 `compileJava` / `build` 全绿；`javap -v` 逐个核对部署 jar 内 `BrbeConfig` 的
`pageFlipDirection` / `queryScope` / `windowMode` 字段都带 `EnumHandler(option=BUTTON)`，
且 `BrbeConfig$QueryScope` / `BrbeConfig$WindowMode` 类都在；28 个语言文件 JSON 解析通过、
旧键（`hideNoRecipeBookStationObjects` / `previewMode`）已全部消失、新键齐全；
`tools/mixin-check` 四分支全部通过（本轮未改 mixin）。

**部署**（备份 tag `20261003-151809`，六实例原子替换）：
26.3 `91831bb4514d778b1cf58867f51da1e5`、26.2 `329fa7afb149ce5385e27d92baff31d6`、
1.21.11 `8fd3ce97b7cc6201ff5f08edbfb3a670`、1.21.1-Fabric `e42923aecf9f8ae69822df568cb5cb34`、
1.21.1-NeoForge `95096256ee301438591123b34d7264b8`。

**实机待用户验证**：配置界面「窗口模式」是切换按钮（持久 ⇄ 预览）、「查询范围」是切换按钮
（仅限配方书 ⇄ 全类别），与「标签模式 / 配方区翻页方向」交互一致；旧 `brbe.toml` 里的
`previewMode` / `hideNoRecipeBookStationObjects` 首次启动会被改写成 `windowMode = "…"` /
`queryScope = "…"`（值不丢）。

---

## 2026-10-03（十二）：「完全拆散替代配方组」→「拆散替代配方组」+ 三档枚举（完全 / 选择性 / 关闭）（四分支同步，已部署）

**用户指令**：「完全拆散替代配方组」改为「拆散替代配方组」，类型改为枚举，三档「完全」（原 `true`）、
「选择性」（原 `false`），**新增「关闭」** —— "关闭则是把选择性拆散替代配方组（根据替代配方组内配方的
选中状态来拆分，比如不同配方状态的配方要拆开，被搜索选中的配方也要拆开，还有被pin的配方也要被拆开）
也关掉"。

| 枚举值 | 显示名 | 含义 | 旧值 |
|---|---|---|---|
| `FULL` | 完全 | 每个替代配方组拆成单配方格（`ungroup/ClientRecipeBookMixin` + `CollectionPipeline.applyUngroup`），并优先于「自动收纳同产物配方」 | `noGrouped=true` |
| `SELECTIVE` | 选择性 | Stage 2.5 `CollectionPipeline.applySortExtraction`：按变体状态（pin / 可合成 / 残缺 / 搜索命中）把变体剥出去参与排序 | `noGrouped=false`（**默认**） |
| `OFF` | 关闭 | **两者都不做**：Stage 2.5 跳过、也不拆散 —— 替代配方组整体保持原样，组内变体在同一按钮上轮循 | 新增 |

**落地**（26.3 / 26.2 / 1.21.11 / 1.21.1）：

1. **配置**：`AlternativeRecipes.noGrouped`（boolean）→ **`AlternativeRecipes.splitMode`**
   （新枚举 `SplitMode`，默认 `SELECTIVE`），带 `@ConfigEntry.Gui.EnumHandler(option = BUTTON)`
   —— 同（十）的教训，不带它 AutoConfig 会生成候选只剩当前档位的下拉框。读取点新增
   `noGrouped()`（= 完全档）与 `selectiveSplitEnabled()`（= 选择性档）两个便捷方法，
   全部老读取点机械替换（26.x 各 4 个文件、1.21.1 5 个文件）。
2. **「关闭」的两处闸门**（26.x / 1.21.11）：
   - `mixins/pipeline/RecipeBookComponentMixin`：Stage 2.5 `applySortExtraction(...)` 调用包在
     `if (selectiveSplitEnabled())` 里 —— 这是"不同状态 / 搜索命中 / pin 变体被拆开"的唯一来源；
   - `generic/GenericRecipeBookComponent`（BRBE 自研的酿造台 / 锻造台书）：`brbe$extractBySortReason`
     同样被该开关闸住（与原版书行为一致）。
   - 未受影响：Stage 3 `applyPins`（整体全 pin 的组仍置顶）、Stage 2.6 收纳（由
     `mergeSameResult` 自己管）、Stage 4 排序（只排集合、不拆组）。
   - **缓存键分位**：`brbe$configMask()` 里「选择性＝0 位 / 完全＝bit4 / 关闭＝bit16」——
     三档必须分开，否则档位切换不改变缓存键（与 2026-09-30 那次"开关压成 OR"同源的坑）。
3. **1.21.1 的差异（披露）**：该分支**没有** Stage 2.5「按排序原因剥离」，组内变体从来不按状态
   拆出去 —— 所以 1.21.1 上「选择性」与「关闭」行为相同（保留 OFF 档只为跨分支配置/界面一致）。
4. **TOML 迁移**：`migrateLegacyConfigValuesInToml()` 追加
   `noGrouped = true|false` → `splitMode = "FULL"|"SELECTIVE"`（26.x 现共四条、1.21.1 三条），
   仍在 `AutoConfig.register` 之前执行。
5. **语言**（4 分支 × 7 语言）：`…option.alternativeRecipes.noGrouped` →
   `…option.alternativeRecipes.splitMode`，标题「拆散替代配方组」，新增 `.FULL` / `.SELECTIVE` /
   `.OFF` 三档名，tooltip 改为描述三档（「完全：将所有替代配方组拆散成单配方格（并禁用「自动收纳
   同产物配方」）；选择性：按变体状态拆散（被pin、可合成、残缺、被搜索选中的配方会拆出来）；
   关闭：不做任何拆散，替代配方组保持原样。」）。⚠️ 1.21.1 该字段原本**没有 tooltip**（无
   `@ConfigEntry.Gui.Tooltip`），本轮补上注解与键（与其他分支一致）。

**验证**：四分支 `compileJava` / `build` 全绿；`javap -v` 核对部署 jar 内
`AlternativeRecipes$SplitMode` 类存在、`splitMode` 字段带 `EnumHandler(option=BUTTON)`；
28 个语言文件 JSON 解析通过、旧键 `…noGrouped` 已消失、新键（含三档名）齐全；
`tools/mixin-check` 四分支全部通过（本轮未改 mixin 注册）。

**部署**（备份 tag `20261003-154251`，六实例原子替换）：
26.3 `c6296236de39edb78668d0b84301fb2c`、26.2 `e0d45d3bd42af76de7e8ccda0baa3cce`、
1.21.11 `9d822bc6151fb2c262a4f3177e0658c3`、1.21.1-Fabric `9f8e0cb0a36ffe1e2c32183a2c3ed866`、
1.21.1-NeoForge `ba9a424cc8a56344b788673305e1aec7`。

**实机待用户验证**：配置界面「拆散替代配方组」是切换按钮（完全 ⇄ 选择性 ⇄ 关闭）；
「关闭」档下替代配方组整体不动（组内变体轮循），pin / 搜索命中不再把组内变体单独拎出来
（这是"关闭"的预期代价）；旧 `brbe.toml` 的 `noGrouped` 首次启动会被改写成
`splitMode = "FULL"|"SELECTIVE"`（值不丢）。

---

## 2026-10-03（十三）：四个枚举配置项的 tooltip 排版对齐「标签模式」（多行，四分支同步，已部署）

**用户指令**：「请把现在的枚举配置项的 tooltip 的排版风格向「标签模式」对齐」。

**「标签模式」的原排版**（照抄的目标）：注解 `@ConfigEntry.Gui.Tooltip(count = N)` +
语言键 `…@Tooltip[0]` / `[1]` / `[2]`，**每行一个档位**，行首是档位名的引号形式
（zh_cn `“命名空间”：…`），**不是**用换行符拼一坨。机制实证：Cloth 的
`DefaultGuiTransformers` 读 `Tooltip.count()`，`count == 1` 时取 `…@Tooltip`、
`count > 1` 时按 `%s.%s[%d]` 拼出 `…@Tooltip[i]`（`javap -c` 里能看到 `@Tooltip` 与 `%s.%s[%d]` 两个字面量）。

**改动**（四个枚举项，四分支）：

| 配置项 | 行数 | 语言键 |
|---|---|---|
| `pageFlipDirection`（配方区翻页方向） | 2 | `…pageFlipDirection.@Tooltip[0]/[1]`：自然 / 常规 |
| `windowMode`（窗口模式） | 2 | `…windowMode.@Tooltip[0]/[1]`：持久 / 预览 |
| `queryScope`（查询范围） | 2 | `…queryScope.@Tooltip[0]/[1]`：仅限配方书 / 全类别 |
| `alternativeRecipes.splitMode`（拆散替代配方组） | 3 | `…splitMode.@Tooltip[0]/[1]/[2]`：完全 / 选择性 / 关闭 |

- 注解：`@ConfigEntry.Gui.Tooltip` → `@ConfigEntry.Gui.Tooltip(count = 2)`（splitMode 为 `3`），
  与 `rbip.tabMode` 的 `count = 3` 同一写法；每处旁边补了一行注释说明"两行/三行说明（排版对齐
  「标签模式」）"。旧单行键 `…@Tooltip` **已删除**（留着会与 `count > 1` 的新键并存、误导以后改文案的人）。
- 语言：7 语言全部改写为逐档一行（引号风格跟各语言既有习惯：zh_cn `“”`、zh_tw/ja `「」`、
  en/tr `" "`、pl `„ ”`、ru `« »`）。
  **1.21.1 的「关闭」行**额外带一句括号说明（该分支没有 Stage 2.5，本档与「选择性」行为相同）。
- **1.21.1 的 `RecipeViewerGuiRegistrar`**（`queryScope` 的 GUI 由它自造选择器）：原先只读单行
  `…@Tooltip` 键 → 改为按 `QUERY_SCOPE_TOOLTIP_LINES = 2` 循环取 `…@Tooltip[0]/[1]`，
  与 `TabModeGuiRegistrar` 的 `TOOLTIP_LINES` 同一写法（常量 + 注释要求与注解上的 N 保持一致）。

**验证**：四分支 `compileJava` / `build` 全绿；`javap -v` 核对部署 jar 内
`BrbeConfig` 的 `pageFlipDirection` / `queryScope` / `windowMode` 与 `AlternativeRecipes` 的
`splitMode` 四个字段的 `Tooltip` 注解分别带 `count=2/2/2/3`（且仍带 `EnumHandler(option=BUTTON)`）；
28 个语言文件 JSON 解析通过、旧单行键已消失、`…@Tooltip[i]` 行数分别为 2/2/2/3；
`tools/mixin-check` 四分支全部通过。

**部署**（备份 tag `20261003-160850`，六实例原子替换）：
26.3 `fd00d0d3e279e151202e00b9c1c08393`、26.2 `4d038cbcb5975615b9b2bb1ac0abe376`、
1.21.11 `b52c4bafbcd196a9339080623d64aea2`、1.21.1-Fabric `746e1b585479089e0c447ac9e78478d9`、
1.21.1-NeoForge `f3b7a72b1b4c279a11d372bfa6bf6279`。

**实机待用户验证**：四项的 tooltip 现在应是**多行**（每档一行，行首“档位名”：说明），
排版与「标签模式」一致；1.21.1 的「查询范围」同样多行（走的是自造选择器那条路径）。

---

## 2026-10-03（十四）：附魔台不出词条 → 事故根因：BRBE 写进存档的 `brbe_progress` 数据包是坏包（26.3/26.2/1.21.11 三个分支，已部署）

**用户报告**：「安装 BRBE 后 Enchanting Infuser 的两个附魔台都无法附魔，把装备塞进去不跳附魔词条，
经验足够也不行；原版附魔台同样；卸载 BRBE 就恢复。」用户随后确认：**修好本轮问题后附魔台稳定出词条**
（即该症状是本轮数据包事故的连带表现，不是附魔逻辑本身被 BRBE 改坏）。

**排查手段（新增两个可复用的回路，见下）**：
1. `tools/brbe-screen-selftest/BrbeEnchantProbe.java` —— 真实实例里的附魔探针：给玩家 30 级 + 钻石剑 +
   青金石 → 在服务端开 `EnchantmentMenu`（真实 `ContainerLevelAccess`）→ 等 `EnchantmentScreen` 打开 →
   用**真实界面点击路径**（`AbstractContainerScreen.mouseClicked`，也就是 BRBE 的 HEAD 注入层）把剑点进
   附魔槽，点不进去再退回 `handleContainerInput(QUICK_MOVE)` → 打印客户端/服务端 `costs`、`enchantClue`、
   附魔注册表规模、BRBE 浮层状态，最后 `mc.stop()` 自动退出。跑法：
   `TEST_SRC=BrbeEnchantProbe.java TEST_ENTRY=brbeselftest.BrbeEnchantProbe TEST_ID=brbe_eprobe \
    TEST_NAME="BRBE Enchant Probe" TEST_JAR=brbe-eprobe.jar GREP_TAG=BRBE-ENCHPROBE bash run-cbtest.sh 110`
2. `tools/brbe-progress-pack-check/check.py` —— 静态校验进度包 JSON 模板（从 `RecipeUnlockTracker.java`
   的字符串拼接还原成品 JSON 再 `json.loads`），1 秒出结论，不必进游戏。

**探针结论（BRBE 没改坏附魔逻辑的正面证据）**：客户端与服务端 `costs` 一致且非 0（`[1,2,7]`）、
`enchantClue=[69,44,80]`、附魔注册表 87 条 / `#minecraft:in_enchanting_table` 36 条、
`RecipeViewerOverlay.isActive()=false` / `ownsPoint=false` / `pin=null`（浮层没有吞点击）。
即"装备进槽 → 两侧各自本地算词条"这条链在 BRBE 存在时是通的。

**真正的根因：`brbe_progress` 进度包写坏 → 26.3 的 loader 直接拒绝加载注册表**：
```
java.lang.IllegalStateException: Failed to parse brbe:recipe/advancednetherite/netherite_iron_helmet_smithing from pack file/brbe_progress
Caused by: ... Advancement completion requirements did not exactly match specified criteria. Missing: []. Unknown: [has_the_recipe]
Caused by: java.lang.IllegalStateException: Failed to load registries due to errors
```
三个缺陷叠加（都在 `brewingstand/RecipeUnlockTracker.java`）：

| # | 缺陷 | 证据 / 影响 |
|---|---|---|
| ① | 锻造触发器的条件键写成 `"conditions":{"recipe":…}`，而 **26.3 改名为 `recipes`** | `javap -c` 实证：26.3 `RecipeUnlockedTrigger$TriggerInstance` 常量池是 `"recipes"`；26.2 / 1.21.11 / 1.21.1 是 `"recipe"`（所以**只有 26.3 要改**）。42 个文件全部解析失败 → `Failed to load registries due to errors` → **整个世界打不开**（实例日志 17:42，44 条 parse 错误 + 游戏退回标题界面） |
| ② | `pack.mcmeta` 只写 `pack_format`，缺 `min_format`/`max_format` | data pack 格式跨过 `PackFormat.lastPreMinorVersion(SERVER_DATA)`（=81）后这两个字段是**强制**的（26.3=121、26.2=107、1.21.11=94 都超）。26.3：`WARN Error reading pack metadata, attempting fallback type`；**1.21.11：`ERROR Couldn't load file/brbe_progress pack metadata`**（包整个不生效）。修法：`progressPackMeta()` 按 `format.major() > lastPreMinorVersion(SERVER_DATA)` 决定是否补 `min_format`/`max_format`（数组形式 `[major,minor]`，与内置资源包一致） |
| ③ | `requirements` 写成 `[[a],[b]]`（**AND**），与类注释"镜像原版语义 / 任一满足"矛盾 | 奖励本身就是"解锁该配方"，AND 之下模组锻造配方**永远解锁不了**。改为原版同款单组 OR：`[["has_the_recipe","has_addition"]]` |

配套改动：
- **指纹加版本尾巴**（`.materials.fingerprint` → `…|brew-v3`、`.recipes.fingerprint` → `…|vanilla-native|adv-v3`）：
  否则指纹不变 → 老世界里的坏包**永远不会被重写**，改完代码也救不回来。
- **落盘前自校验**：`writePackJson()`（原 `writeBrewJson`）先 `JsonParser.parseString` 再写；
  校验失败记 `WARN [BRBE-RECIPE-PROGRESS] refusing to write invalid JSON to …` 并**删掉同名旧文件**
  （老版本写坏的残留文件留着照样锁死世界）。⚠️ 教训：**本轮我第一次补丁少写了一个 `}`**，
  生成的 42 个文件全是非法 JSON（靠"校验生成物"抓到，游戏里当时没报错是因为那一次加载根本没读这个包）；
  有了这道闸门，这类手写 JSON 事故以后最多是"少写一个文件 + 一行 WARN"，不会再锁死世界。

**验证**：
- 修复前（红）：世界加载失败 —— 44 条 `Failed to parse brbe:…` + `Failed to load registries due to errors`。
- 修复后（绿）：世界正常加载；日志 0 条 `Failed to parse brbe` / 0 条 `Error reading pack metadata` /
  0 条 `Failed to load registries`；世界内进度包 **67/67 个 JSON 全部合法**，42 个锻造触发器都是
  `conditions.recipes` + `requirements=[["has_the_recipe", …]]`；`pack.mcmeta` =
  `{"pack":{"pack_format":121,"min_format":[121,0],"max_format":[121,0],…}}`；
  附魔探针 `VERDICT=PASS`（客户端 `costs=[1,2,7]`）；用户实机确认附魔台稳定出词条。
- `tools/brbe-progress-pack-check/check.py` 三分支**全部合法**；`tools/mixin-check` 四分支全部通过。

**已部署**（备份 tag `20261003-181114`，原子替换）：26.3 `94cdd4a74951768f9a85bd1c74eaa0d0`、
26.2 `aa89da95cf6f500264ca874c16fdb5e7`、1.21.11 `b3f7d761424b9c8cec251f1a7dcbf546`。
**1.21.1 未改也未重部署**：该分支没有进度包写入器（无 `RecipeUnlockTracker`），无此缺陷。

**用户存档的清理（已做）**：
- `saves/新的世界`：坏包先移到世界根目录备份（`brbe_progress.broken-20261003`、`brbe_progress.v2broken-20261003`，
  都在 `datapacks/` **之外**、不会被加载），随后由新构建重写出合法包；现包 67 个文件全合法。
- `saves/新的世界 (4)`：含 42 个非法文件的坏包移到 `<world>/brbe_progress.broken-20261003`（否则该世界打不开）。
- `新的世界 (1)(2)(3)`：只有酿造包、无非法文件，但 `pack.mcmeta` 是旧格式 → 下次打开时会被
  `brew-v3`/`adv-v3` 指纹强制重写为合规格式。
- ⚠️ 探针在 `新的世界` 里留了痕迹（供后续排查参考）：在 `25,65,-76` 放了附魔台方块、
  给玩家塞了钻石剑 + 16 青金石、每次运行 +30 经验等级（共 5 次 ≈ +150 级）。

---

## 2026-10-03（十五）【调查中·未修复】Enchanting Infuser 附魔台仍不出词条 —— 已定位到「模组数据包标签在世界加载时缺失」，且与 BRBE 的存在强相关

**用户报告**（原版附魔台已由（十四）修好之后）：「Enchanting Infuser 的两个附魔台仍然异常：放进槽位、经验足够，就是不跳词条。」

### 已确认的事实（可复现的回路在 `tools/brbe-screen-selftest/`）

- `BrbeInfuserProbe.java`：镜像 `InfuserBlock.useWithoutItem` 的服务端三步（放方块 → `openMenu`
  → 对 `InfuserBlockEntity` 强制 `containerMenu.slotsChanged(be)`），把钻石剑 quick-move 进附魔槽，
  读两侧 `InfuserMenu` 的 `availableEnchantmentLevels` 等私有字段 → **装了 BRBE 时恒为空**
  （`available=0 required=0`，`mayEnchantStack=true`、`power=15` 都正常）；不装 BRBE 时非空（9 条）。
- `BrbeTagProbe.java`：**世界加载完成时**（第一次 dump）读附魔注册表：
  - 装 BRBE：`#minecraft:in_enchanting_table=36`、`enchantinginfuser:in_enchanting_infuser` **不存在**、
    `modTagsInRegistry=[]`（**所有模组数据包贡献的标签都没绑上**）；模组的方块标签条目
    （`#minecraft:mineable/pickaxe` 里的附魔台）也缺失。
  - 不装 BRBE：`=41`、mod 标签 present=41、mod 标签都在 ✓。
  - **两条路线都验证过：装 BRBE 时资源管理器里明明有那些标签文件**
    （`listResources("tags/enchantment")` 96 个文件，含 `enchantinginfuser:tags/enchantment/in_enchanting_infuser.json`），
    且 `enchantinginfuser` 包在 `server.getResourceManager().listPacks()` 里 ✓ ——**文件在，绑定没做**。
  - 触发一次数据包 reload（`server.reloadResources(selectedIds)`，等价 `/reload`）后**立刻正常**
    （`#in_enchanting_table=41`、mod 标签 present=true、41 条）⇒ **`/reload` 是当前可用的绕过手段**。

### 已排除

- **不是进度包（`brbe_progress`）**：把世界里的包整个挪走再进世界，标签依旧缺失。
- **不是客户端入口代码**（内嵌资源包注册等）：把 `fabric.mod.json` 的 `client` entrypoint 去掉，标签依旧缺失。
- **不是 RBIP 的那组 mixin**（`ItemMixin`/`MouseMixin`/`ClientRecipeBookMixin`/`HandledScreenMixin`/…）：去掉
  `recipe-book-is-pain-extended.mixins.json` 后标签依旧缺失。
- **不是 `accessors.HolderReferenceAccessor`**（唯一碰注册表的 mixin，且全工程无引用）：从
  `mixins.brbe-common.json` 去掉该条目后标签依旧缺失。
- 不带任何 mixin 的 BRBE 会在世界加载时崩（`ClientRecipeBook cannot be cast to ClientRecipeBookAccessor`，
  BRBE 代码依赖自己的 accessor），该变体无法用于判定。

### 结论与待办

- 现象 = **模组数据包提供的标签在世界加载阶段没有被绑定**（配方反而正常；重载后一切正常），
  而 BRBE 的**存在**（具体说是 `mixins.brbe-common.json` 里某个 mixin，96 条待二分）会稳定触发它。
  依赖自身标签的功能因此失灵：Enchanting Infuser 的附魔表就是其一。
- **待办**：对 `mixins.brbe-common.json` 做二分（构造只改 `fabric.mod.json`/mixin 配置的 jar 变体，
  用 `BrbeTagProbe` 判定 `modTag.present`），定位到具体 mixin 后再谈修法；
  若最终确认是 Fabric/26.3 的标签绑定时序竞态（BRBE 只是改变了时序），需要另行评估绕过方案。
- 本轮**没有代码改动、没有重新部署**（实例里 BRBE jar 仍是 `94cdd4a74951768f9a85bd1c74eaa0d0`）。

---

## 2026-10-03（十六）：【已修复·仅 26.3】Infuser 标签被抹掉的根因 = 内嵌无头 JEI 的「原版配方兜底」把标签写回了活注册表

（十五）里定位到「装 BRBE 时世界加载阶段模组数据包标签没绑上」，本轮把根因追到了源码级并修好。

### 根因链（全部有源码/实测证据）

1. **触发时机**：BRBE 内嵌的无头 JEI（`zheadlessjei`）不是启动即跑，而是**进世界后**才启动
   （`BrbeJeiHeadlessCore.tick()` 等到 `minecraft.level != null` 才 `start()`）；
   26.3 上客户端 RECIPE 注册表**恒为空**（fork 内注释早有记录），于是
   `JeiStarter.start()` 里 `if (!Internal.hasClientRecipes())` 成立、
   BRBE 自己那段兜底（`clientRecipeMap` 为空时的 `else if`）也成立 —— 两条都会调用
   `VanillaClientRecipeLoader.getVanillaRecipes(level.registryAccess())`。
2. **破坏点**（`headless-jei/26.3` → `mezz/jei/common/recipes/VanillaClientRecipeLoader.loadVanillaRecipeRegistry`）：
   该方法为了**只加载原版配方**，用了一个**只含原版包**的资源管理器：
   `new MultiPackResourceManager(SERVER_DATA, List.of(ServerPacksSource.createVanillaPackSource().fullResources()))`，
   但它随后对**调用方传入的那个活注册表**做了：
   ```java
   var pending = TagLoader.loadTagsForExistingRegistries(resourceManager, liveRegistryAccess);
   ...
   pending.forEach(Registry.PendingTags::apply);   // ← 用"只有原版包"的标签集覆盖活注册表
   ```
   ⇒ 游戏当前所有注册表的标签被**整体替换成"只有原版包"的集合**：模组数据包的标签全部消失
   （`enchantinginfuser:in_enchanting_infuser` 直接查不到、`#minecraft:in_enchanting_table`
   只剩原版 36 条、模组往 `#minecraft:mineable/pickaxe` 里加的条目也丢）——**且不报任何错**。
3. **为什么表现为"要 reload"**：标签是加载数据包时绑定的，`/reload` 会完整重绑一次 ⇒ 立刻恢复；
   而世界加载那次绑定本来是对的，是**进世界之后**被上面这一步抹掉的。
4. **为什么原版附魔台看着没事**：原版三档 `costs/clue` 不依赖标签（靠附魔能力/书架 + 原版标签），
   而 Enchanting Infuser 的候选表**完全来自它自己的标签** ⇒ 标签没了就是空表。
5. **为什么"卸载 BRBE 就好了"**：内嵌 JEI 随 BRBE 一起消失，没人再走这条兜底。

### 修法（改在 BRBE 自己的 JEI fork 里，1 处、删 2 行）

`headless-jei/26.3/src/main/java/mezz/jei/common/recipes/VanillaClientRecipeLoader.java`：
**不再把 pending tags 写回"已存在的注册表"**（删掉 `basePendingTags`/`worldPendingTags` 的
`PendingTags::apply`），只保留本地 `baseLookups`/`worldLookups`——它们本来就带着标签，足够解析原版
配方里的 `Ingredient`；写回活注册表是 JEI 自己并不需要的副作用。代码里留了
`[BRBE fork] …请勿恢复这两行 apply()` 的注释（含事故经过）。

随后重建 fork jar（`9b03edb80cacdc62ea8617d90f8f3180`）→ 覆盖 BRBE `libs/` +
`src/main/resources/META-INF/jars/`（**文件名不变，未动构建逻辑**）→ 重建主 jar。

### 验证（装 BRBE、**不做任何 reload**）

- `BrbeInfuserProbe`：世界加载完即 `reg.get(modTag).isPresent=true modTagResolved=41`、
  `#in_enchanting_table=41`、`modHelperList=9`、两侧 `available=9 required=9` → **VERDICT=PASS**
  （修复前同一探针是 `present=false / 0 / available=0`）。
- JEI 侧数据没被削：BRBE 日志仍有 `client recipe map: 2042 recipes (BUNDLED VANILLA FALLBACK…)`，
  `setSyncedRecipes called (146 recipes)`、`imported 1 JEI entries`、`attached vanilla smithing layout to 72 trim entries`。
- 部署：26.3 `065a786e0c9c489925633674e715ba97`（内嵌 headless-jei
  `9b03edb80cacdc62ea8617d90f8f3180`）。

### 影响面 / 其他分支

- **仅 26.3 存在**：26.2 与 1.21.11 的 fork 里根本没有这条代码路径（`PendingTags::apply` 0 处），
  1.21.1 没有内嵌 JEI ⇒ 三分支本轮**未改、未重部署**（保持同步性不受影响）。
- 该兜底的语义副作用（"原版配方类型看不到数据包配方"）与本次修复无关，是 fork 里原有的、
  已文档化的取舍，保持现状。
- 遗留：`headless-jei` 工程（独立 worktree/分支）的这次改动**未提交**；BRBE 侧的 `libs/` 与
  `META-INF/jars/` 里的 fork jar 已更新，同样未提交。

---

## 2026-10-03（十七）：（十六）的修法不完整 —— 已修正；顺带把「启动失败重试风暴」堵掉

### 出了什么问题（用户实测："许多类别凭空消失，JEI 界面也无法加载"）

（十六）只删掉了 `basePendingTags/worldPendingTags.forEach(PendingTags::apply)`，**保留了**
`TagLoader.buildUpdatedLookups(...)` 产出的那些 lookup。实测：这些 lookup 里的
`HolderSet.Named` 是 **pending/unbound** 的（只有 `apply()` 才会绑定），于是本地加载原版配方时
一碰到标签就抛：

```
IllegalStateException: Trying to access unbound tag
  'TagKey[minecraft:item / minecraft:clonable_maps]' from registry Registry[… minecraft:item (Stable)]
```

→ `BrbeJeiHeadlessCore.start()` 抛异常 → **内嵌 JEI 核心根本没起来**
（日志里 `embedded JEI core started` 不再出现，只有 1 条
`[BRBE-JEI-Plugins] embedded JEI core failed to start: …`）→ BRBE 的 JEI 桥只剩
`imported 1 JEI entries (1 types)`（正常是 `imported 3039 JEI entries (8 types)`）
→ 用户看到的"类别凭空消失 + JEI 界面加载不出来"。**是（十六）引入的回归，不是原 bug。**

更糟的是 `tick()` 每 tick 重试 `start()`，而 mezz_config 的配置 schema 注册不可重入 →
从第二次起全部失败于 `IllegalArgumentException: There is already a config schema registered for:
…/config/jei/client/jei-debug.ini`，把首次失败的真实原因刷了下去。

### 正确修法（同一文件，改成"根本不用 pending tags"）

`VanillaClientRecipeLoader.loadVanillaRecipeRegistry`：

- **不再** `TagLoader.loadTagsForExistingRegistries(...)` / `buildUpdatedLookups(...)`；
  改为直接用**调用方注册表自己的 lookups**（`registryAccess.registries().map(RegistryEntry::value)`，
  `Registry` 本身就实现 `HolderLookup.RegistryLookup`）——它们的标签是游戏已经绑好的那一套，
  **既不写回活注册表、也不会 unbound**。
- 其余结构不变（只用原版包的 `MultiPackResourceManager` 读配方 JSON → `RegistryDataLoader.load`
  WORLD/RELOADABLE → `RecipeMap.create`）。语义上只是"配方里的标签改用活注册表里真实的那套解析"。

`BrbeJeiHeadlessCore`：新增 `failed` 标志——**启动失败只报一次、不再每 tick 重试**，
并把首次失败的**完整堆栈**打进日志（`embedded JEI core failed to start（不再重试）` + stack）。

### 验证（一局之内同时满足两件事，无 /reload）

| 项 | 修复前（十六） | 现在 |
|---|---|---|
| Infuser 探针标签 | `modTagResolved=41` ✓ | `modTagResolved=41` ✓（`VERDICT=PASS`） |
| `client recipe map` | 2042 ✓ | **2042 ✓** |
| `embedded JEI core` | **failed to start** ✗（+重试风暴） | **`embedded JEI core started (4 plugins)`** ✓ |
| BRBE JEI 桥 | `imported 1 JEI entries (1 types)` ✗ | **`imported 3039 JEI entries (8 types)`** ✓ |
| 类别索引 | 只有 mod 插件 1 类 | `indexed 5 JEI types (2893 entries, vanilla runtime)` + `3 JEI types (146 entries, mod plugins)` ✓ |
| 切石/锻造布局 | 仅 72 条 | `attached vanilla JEI layout to 1731 entries (351+1380)`、`stonecutting fallback … 1380` ✓ |
| `unbound tag` / `failed to start` | 有 ✗ | 全日志 **0 次** ✓ |

### 部署

- fork jar：`0f83cc0b3a018d09a151f7b35b1e5bcf`（`headless-jei/26.3` 重建 → 覆盖 BRBE `libs/` + `META-INF/jars/`）
- 主 jar：**26.3 `e4761389dcc01573044180ac17a01af0`**
- 事故中间态（十六的 `065a786e…`，JEI 起不来）**没有留在实例里**：发现后先把实例回滚到修复前版本
  （`94cdd4a74951768f9a85bd1c74eaa0d0`，已有备份 `.bak.20261003-201211`），再部署本次修正版；
  当前实例内无探针、无临时文件。
- 其他分支不受影响（26.2 / 1.21.11 的 fork 无此代码路径，1.21.1 无内嵌 JEI）。

### 教训（下次验证别再漏）

验证"标签/配方数据"类改动时，**必须同时确认服务侧产物真的起来了**：本次第一版只看了
`modTagResolved=41` 就收工，漏看了 debug 日志里的 `embedded JEI core failed to start`。
以后这类改动要把 `tools/brbe-screen-selftest` 的探针输出 + `logs/brbe-debug.log` 的
`embedded JEI core started` / `imported N JEI entries` 一起作为验收项。

## 2026-10-03（十八）：RBIP 标签栏翻页动画（26.3 / 26.2 / 1.21.11 三分支同步，已部署）

**需求（用户原话要点）**：在「配方书翻页动画」下新加一个模块 = **RBIP 标签的翻页动画**。两条并行：

1. **收起当前标签**：选中标签暂时取消选中态 → 左/上/下标签**向中间平移**，直到被配方书界面盖住；
2. **伸出最终要展示的标签**：选中标签暂时取消选中态 → 左/上/下标签**从配方书内部**沿各自所在方向
   （**只能在 X 或 Y 单轴**移动）移动，直到达到预定位置 → 给具有选中状态的标签显示其选中状态。

关键细节（全部落实）：进场标签**始终位于退场标签顶层**；**搜索标签始终保持不变**；
**滚动跨度越大动画速度越快**，同时要能在看清标签图标的前提下**不露馅**（露馅 = 直接替换标签图标）；
**图标和标签是一个整体**，一起动；整体曲线**由快到慢**（与配方区动画类似）。

**开关（用户定）**：不新增配置条目 —— 并入现有主开关，标题由「配方书翻页动画」改为
**「翻页动画」**（只改 zh_cn / zh_tw；其余 5 语言的 `Page Flip Animation` 本来就是对的；
`pageAnimationDuration`（「动画时长」）这条数值项的文案未动，但它现在同时驱动标签动画的速率）。

### 实现（`recipebookispain_extended/`，三分支同源）

| 文件 | 内容 |
|---|---|
| `animation/TabFlipState.java`（新） | 单标签状态：`extend`（伸出度 1=静止位 / 0=完全没入书体）、`target`、`tracked`、静止位 `baseX/baseY`、朝向 `placement` |
| `animation/TabFlipGeometry.java`（新） | 纯几何：缩进位移（`RETRACT_DISTANCE=35`）、单轴渲染坐标、**裁剪框 = 静止位矩形外放 `CLIP_PADDING=5`，书体那一侧收在书体真实边缘**（配方书恒在移动标签之上，见文末补记） |
| `access/RecipeGroupButtonFlipAccess.java`（新） | `rbip$flipState()` 访问口（状态挂在按钮身上，随 `initVisuals()` 整批重建一起生灭，不会残留） |
| `mixin/widget/RecipeGroupButtonMixin` | 每按钮一份 `TabFlipState`（`@Unique` 字段 + 访问口实现） |
| `mixin/widget/RecipeBookWidgetMixin` | 翻页检测 / 目标追逐 / 推迟绘制（全部逻辑） |

**驱动流程**
- 用户翻页的三个入口（页控件左箭头、右箭头含 Ctrl 跳页、滚轮）在**改 `rbip$page` 之前**记下起始页
  （`rbip$tabFlipFromPage`）并置 `rbip$tabFlipUserFlip` —— 调用方是先赋值再调 `rbip$applyPagination`，
  在重排内部读 `rbip$page` 拿到的已经是新页（第一版就踩了这个坑）。
- `rbip$applyPagination` **开头**把当前可见的非搜索标签登记为退场基准（此刻 `placeTab` / `resetTabPlacement`
  还没改写朝向与坐标，晚一步离场标签的 `placement` 就被打回 NORMAL 了）；**末尾** `rbip$finishPagination()`
  判定：用户翻页且真换页 → 跨度累加、`tabFlipActive = true`，再按「是否在本页可见」给全部参与标签定目标。
- 已跑动画时（连滚 / 标签集合变化）**就地改目标**：正在缩进的标签直接反向走，不跳回静止位；
  新登场的标签 `extend` 从 0 起（从书体内部伸出）。
- 推进（`extractRenderState` HEAD）：与配方区**同一条指数减速曲线** ——
  `rate = 6.2·√跨度 / pageAnimationDuration`；单帧位移上限 `0.45+√剩余·0.12`；收敛阈值 `0.002`。
  连滚跨度累加 ⇒ **跨度越大越快**；动画收尾（或关开关、重开配方书 `init`）清零。
  √跨度 而不是线性，就是为了跨度再大也还看得清标签图标。

**渲染（`@Redirect` + TAIL 两趟）**
- `@Redirect` 拦下 `RecipeBookComponent.render/extractRenderState` 里那句 `tabButton.…render(...)`：
  参与动画的标签**不就地画**，推迟到本方法 TAIL 统一绘制 —— 原版是按标签自身顺序逐个画的，
  退场/进场在列表里交错，就地画无法定序。搜索标签与未参与动画的标签照原样（搜索标签**始终不变**）。
- TAIL 两趟：先全部 `target=0`（退场），再全部 `target=1`（进场）⇒ **进场恒在退场之上**。
- 单标签绘制：临时 `setRectangle` 挪到动画位置（只沿单轴）、`enableScissor` 套裁剪框、`unselect()`
  挂起选中态 → `render` → 全部还原。图标（含 owo 创造标签渲染）与固定标记都在 `render` 内部，
  天然**跟着标签整体平移**；裁剪框外放 5px 是为了不把悬出标签边缘的 pin 切掉。
- **为什么裁剪框贴着静止位矩形**：标签在书体那一侧本来就与书体重叠 5px（左列 x = 书体左缘-30、宽 35），
  这 5 源列在**未选中**贴图里全透明、动画期间又一律按未选中态绘制，所以静止帧与原版**逐像素一致**
  （起止不跳变）；越过书体边缘的部分一律不画 —— 视觉上就是「滑进书皮底下」，
  全程没有任何一帧是「直接替换标签图标」。

### 离线预览工具

`tools/tab-flip-anim/preview.py`（真实贴图逐帧合成，输出 `docs/tab-flip-anim/`：`flip.gif` /
`filmstrip.png` / `detail-strip.png`）。把 Java 侧的几何与曲线常量 1:1 搬到 Python，用来在进游戏前
判断观感（`--span N` 模拟连滚 N 页的更快速率）。**Java 是唯一权威**，改动画参数两边一起改。

### 验证与部署

- 三分支 `compileJava` + `build` 通过；`tools/verify_mixin_targets.py` **0 问题**（含新增 `@Redirect`
  的 `@At` 调用点字节码级校验）；`tools/mixin-audit/audit.py`（本次给它补了 **26.3** 分支条目）
  只剩 4 条**既有误报**（incompletecrafting ×2 的 @Redirect 形状、localcache/scrollablepages 的
  `<init>` 解析，均与本次无关）；两个被改的 mixin 类 `javap` 无 lambda 合成方法。
- 原子替换部署（部署时 26.3 实例正在运行，zip 句柄指向旧 inode，不受影响）：

| 分支 | 主 jar md5 | 备份 |
|---|---|---|
| 26.3 | `81923fc3994bb8c30bf911cb454ac364` | `.bak.20261003-222606` |
| 26.2 | `9cd9579dc39f45f169c8b86e4c52268a` | `.bak.20261003-222713` |
| 1.21.11 | `060b243ce20d37a37d304f6618002eb2` | `.bak.20261003-222856` |

- **未做**：1.21.1（本轮用户指定只做三分支）。26.1.2 停维不动。
- **待实机确认**：动画观感（`docs/tab-flip-anim/flip.gif` 是离线复刻、不是实机录屏）；
  重点是「标签从书皮底下滑出/滑入」是否成立、pin 标记是否全程跟着标签、缩进方向是否都朝书体。


**补记（同日）：书体那一侧的裁剪边要收到书体真实边缘**。第一版裁剪框用的是静止位矩形
（书体那一侧 = `base + 35` = 书体边缘往里 5px），纸面上没问题 —— 这 5 源列在未选中贴图里全透明
（`recipe_book/tab.png` 第 30–34 列 alpha=0，BRBE 的 `rbip/{top,bottom}_tab.png` 同样），
所以逐像素看与"收到书体边缘"完全一样。但**资源包换了不透明贴图时就会露馅**（移动中的标签会压在
书体边框上），而用户的要求是明确的：**配方书始终盖在移动的标签上面**。已改为
`TabFlipGeometry.clip(state, bookX, bookY, bookW, bookH, out)` —— 书体那一侧直接取书体边缘
（左列 `bookX` / 上排 `bookY` / 下排 `bookY+bookH`），另外三侧仍放 5px 给 pin 图标；
`rbip$drawFlippingTab` 传入 `rbip$getBookX()/getBookY()` + `RBIP_BOOK_WIDTH/HEIGHT`。
顺带把早退条件换成 `shift >= OUTSIDE_EXTENT`（30 = 标签伸出书体之外的宽度，三种朝向都是 30）。
离线预览工具同步改，并加了一条数值核对：**逐帧把移动标签单独画在透明画布上、取书体矩形内的
alpha，必须恒为 0**（当前 11 帧全部 0）。

## 2026-10-03（十九）：RBIP 标签「选中 / 取消选中」渐变过渡（26.3 / 26.2 / 1.21.11 三分支同步，已部署）

**需求（用户）**：给选中标签和取消选中加一个过渡动画，**采用渐变效果直接过渡**；
这个模块仍然挂在「翻页动画」开关下面。

### 实现

| 文件 | 内容 |
|---|---|
| `animation/TabSelectFade.java`（新） | 纯数学：`advance(blend, target, rate, deltaTicks)`（与翻页动画同一条指数减速 `rate = 6.2 / max(0.12, 动画时长×0.5)`、单帧上限 `0.45+√剩余·0.12`、阈值 0.002）、`shift(blend) = round(2·blend)`、`color(alpha) = alpha<<24 \| 0xFFFFFF` |
| `mixin/widget/RecipeGroupButtonMixin` | 每按钮 `rbip$selectBlend`（0 = 未选中外观、1 = 选中外观）+ 首帧对齐标志 |

- **推进放在「正在渲染的标签」里**（`rbip$drawTabContents` / 自绘路径），端点状态无需推进 ——
  所以不必新增注入点，也不会出现「没渲染的标签偷偷跑完渐变」。
- **首帧对齐**：按钮刚建出来（`initVisuals` 造完立刻 `select()`）时直接吸附到当前选中态，
  不给开场补一段渐变。
- **正常朝向（左列）**：`extractContents` HEAD 接管 —— 两张贴图
  （`sprites.get(true,false)` / `sprites.get(true,true)`）在**同一个**（按 `shift(blend)` 左移的）
  位置上各按 `1-blend` / `blend` 着 alpha 叠画；图标只画一次、偏移同步插值；pin 自行补画
  （取消了原方法就不会走 `extractIcon` 的 RETURN 注入）。渐变停在端点、或该标签正在播
  「解锁弹跳」时**原样交回原版**，静止观感逐像素不变。
- **上/下侧旋转条带**：本来就是自绘，换成同一套交叉渐变（新增 `rbip$blitRotated` 带 color ——
  BRBE 的 rbip 贴图走 `blit` 直连纹理、原版贴图走 `blitSprite`，两种都吃 alpha）；
  旋转图标的 Y 偏移从 `selected ? ±2 : 0` 改成 `±shift(blend)`。
- **owo 创造标签渲染**的图标偏移改成 `-shift(blend)`（原版是 `selected ? -2 : 0`）。
- **为什么两张贴图放同一个插值位置**：原版选中态除了换贴图还整体左移 2px。若两张贴图各自按
  原版位置画，这 2px 位移会让图标在渐变期间出现重影（半透明的两个图标错开 2px）。
  放同一位置 + 图标只画一次 = 纯渐变、无位移重影。
- **与翻页动画的关系**：翻页期间参与标签本来就「选中态挂起」（`unselect()` 包住绘制），
  渐变会自然跟着淡出；翻页结束、选中态恢复时再淡入 —— 也就是「给具有选中状态的标签显示其
  选中状态」现在自带渐变。

**开关**：不新增配置条目，沿用 `pageAnimation.pageAnimationEnabled`（关掉 = 直接吸附 = 原版硬切换）。

### 验证与部署

- 三分支 `compileJava` + `build` 通过；`tools/verify_mixin_targets.py` **0 问题**；
  `tools/mixin-audit/audit.py` 仍只有那 4 条既有误报；`javap` 无 lambda 合成方法。
- 离线预览工具加了 `--mode select-fade`（真实贴图 + 真实 alpha 合成，输出
  `docs/tab-flip-anim/select-fade.png`：blend 0 / 0.25 / 0.5 / 0.75 / 1 的并排对照，
  正常朝向与上侧旋转各一份）。
- 原子替换部署（备份 `20261003-230335`）：26.3 `db0b878ef0972f5ac6386ba789309134`、
  26.2 `b29b885a1627441415596b2bcf362754`、1.21.11 `2fa84c3c4f82cb5a640946919990e707`；
  jar 内 `animation/TabSelectFade.class` 三支齐备。
- **待实机确认**：渐变时长观感（默认「动画时长」0.5s → 渐变 0.25s 时间常数）、
  上/下侧旋转标签的渐变、以及翻页结束瞬间选中态淡入是否自然。


**补记（同日，两轮调整）**：

1. **选中态渐变与翻页的伸出/缩入同进度**：翻页期间参与标签的 `blend` 不再走自己的曲线，而是
   直接取它的「伸出度」（`blend ≡ extend`）—— 退场标签随缩进淡出选中态、进场标签随伸出淡入
   选中态，**同起止、同进度**。赋值而非追逐，所以某一帧没被画到也不会积累偏差；翻页只在
   `extend` 距目标 0.002 内收尾，所以渐变**不会留尾巴**（收尾时 blend 已经等于目标，不需要补淡入）。
   连带删掉了「翻页期间临时 `unselect()`／`select()`」那套写法：选中态挂起现在由渐变本身表达，
   `selected` 字段全程不动（也让 pin 的位置不再中途跳）。
2. **过渡期间配方书恒在标签之上**：选中态渐变进行中（`blend ≠ 目标`，即还没完全选中）一律用
   书体边缘裁剪 —— 原先选中贴图是**半透明**压在书体边框上的（书皮被"染色"），现在渐变全程
   不越界，只有**完全选中**那一刻才恢复原版那种「贴着书皮」的形态。裁剪框与翻页共用同一条
   `TabFlipGeometry.clip(baseX, baseY, placement, out)`：书体边缘 = 静止位 + `bookEdgeOffset`
   （左列/上排 30px、下排 5px；三个数字与 `rbip$getTabX/getTopTabY/getBottomTabY` 的布局常量绑定，
   改布局要一起改）。翻页期间不重复套（外层已有同一套裁剪）。

- 离线预览工具 `--mode select-fade` 同步加上了这条裁剪，`flip` 模式的细节条也能看出
  「被选中的那个进场标签随伸出淡入选中态」（金色图标那个）。
- 部署（备份 `20261003-231425`）：26.3 `08290a530b9de2e1c1df4c41844db933`、26.2 `d865ac18437c71fbf0a40f3e4e2a4463`、1.21.11 `bbc27e4fe6dd801721feac752c3c086e`。


**补记（同日，第三轮）：两处修正**

1. **「选中形态压在配方书上」与「未选中形态被配方书盖住」其实不冲突** —— 两张贴图在书体那一侧
   并不占同一批像素：未选中贴图（`tab.png`）第 30–34 列**全透明**，只有选中贴图
   （`tab_selected.png`）在那几列不透明（那段"贴着书皮"的形态）。所以把裁剪**只加在未选中那张
   贴图上**：书皮上出现的永远是「选中形态 × 当前 alpha」（随渐变淡入淡出），未选中形态一个像素
   也上不去书体。上一个补记里"整块裁掉"的写法是过头的 —— 它会让选中态那 5px 在过渡期间消失、
   结束瞬间再"啪"地贴回书皮；现在两端都连续。图标与 pin 也不再裁（它们完全落在标签本体之内，
   裁了只会让下侧标签的 pin 缺掉压进书体的 1px）。
2. **搜索标签图标在过渡期间下沉 1px** —— 根因：BRBE 的 `RecipeBookComponentTabIconOffsetMixin`
   把**首个可见标签 -1px、末个可见标签 +1px** 的微调经 `@ModifyArg` 加在**原版** `extractIcon`
   上；RBIP 的自绘接管取消了原方法、自己画图标，却没带这个偏移 → 首个可见标签（正是搜索标签）
   在过渡期间比静止态低 1px。修法：`RecipeBookTabButtonIconOffset` 补 `brbe$getIconYOffset()`，
   `rbip$renderNormalIcons` 的 y 改成 `getY() + 5 + iconYOffset`。旋转条带那条路径本来就全程不带
   该偏移（静止与过渡一致），不受影响、不动。

- 部署（备份 `20261003-232246`）：26.3 `9f070a7bd4b5ae8fee9366829c1b4d77`、
  26.2 `4e0b615efef87e3c7336d03b0aca2231`、1.21.11 `6a970a32a67a2008219992c8dfb44c4f`。


**补记（同日，第四轮）：选中态淡入延迟到「落地之后」**

用户反馈：*"选中标签在伸出过程中会被配方书盖住，直到静止下来才会盖在配方书上面。我觉得可以尝试给选中标签渐变的过程加延迟"*
—— 观察与机制判断都对：翻页期间整块标签（含选中形态那 5px）都被书体裁掉（这是既定的"配方书始终盖在移动的标签上面"），
落地、裁剪解除的瞬间那 5px 才"啪"地贴回书皮。所以问题不在裁，而在**渐变不该在移动中就把选中形态顶到 100%**。

落地：`rbip$advanceSelectBlend` 的翻页分支由 `blend = extend` 改为 **`blend = min(blend, extend)`**（只减不增）：

- **退场**：`blend` 被 `extend` 逐帧压低，选中形态随缩进淡出 —— 与移动仍然同进度；
- **进场**：按住不动（首次进场就是纯未选中外观），**落地、裁剪解除之后再按常规选中态速率淡入** ——
  那 5px 是渐显到书皮上的，静止瞬间不再有跳变。延迟量天然等于"伸出所需的时间"，不需要额外计时器；
- 用 `min` 而不是按方向分支：它单调不增，**半路改方向（快速来回滚轮）也不跳**（冻结在当前值，落地后从它继续升）；
- 落地后的那次淡入正好落在第三轮的规则上（只裁未选中形态），所以选中形态是连那 5px 一起渐显的。

离线 `--mode flip` 预览同步：`draw_tab` 现在区分「未选中形态恒裁 / 选中形态只在移动中裁」，
并在翻页帧之后追加 6 帧落地淡入帧（`tracked=false`）；17 帧的细节条里能直接看出金色那个被选中标签
在滑动全程是纯未选中外观、落地后才渐显选中形态。

- 部署（备份 `20261003-233050`）：26.3 `4be52982fe15b4c088e7cb618a4f98e0`、
  26.2 `960e995e2b62440ff2e504bba523e5fc`、1.21.11 `752cc9a510ee5faf5e491fb00a778692`。


**补记（同日，第五轮）：渐变「延迟起点」而非「等落地」+ 裁剪下沉到贴图层**

用户第三轮澄清：*"我观察到选中标签在伸出的过程中还是会被配方书压在下面。我的意思是延迟选中过渡动画的起点时间，
把过渡动画的起点放的靠后一点，但也不要等到完全静止后才播放。"*
—— 第四轮把渐变整个推迟到落地之后，于是伸出全程都是"未选中标签"，看起来就是"一直被书压着"。

**① 起点靠后、但不等到静止**：`rbip$advanceSelectBlend` 的翻页分支由 `min(blend, extend)` 改为
**`blend = TabSelectFade.fromExtend(extend)`** —— 新增常量 `EXTEND_FADE_START = 0.6`：

```java
public static float fromExtend(float extend) {          // 纯函数，无状态
    if (extend <= EXTEND_FADE_START) return 0.0F;       // 前 60% 行程：纯未选中
    float t = (extend - EXTEND_FADE_START) / (1.0F - EXTEND_FADE_START);
    return t >= 1.0F ? 1.0F : t;                        // 后 40% 行程：一边滑一边成形
}
```

翻页位移是指数减速，所以 0.6 落在开始后约 70ms、走完 60% 行程处：实测（rate 12.4/s、35px 行程）
第 5 帧 extend 0.64 / blend 11%，第 9 帧 0.84 / 61%，**落地那一刻 blend 正好 1.00** —— 不需要补一段淡入，
也不会有"落地才啪一下"的跳变。进出场共用同一条曲线（对 extend 对称），并且是纯函数，
半路改方向（快速来回滚轮）天然连续。

**② 裁剪下沉到贴图层**：原先翻页时 `RecipeBookWidgetMixin#rbip$drawFlippingTab` 给整个标签套一层
scissor（一律裁在书体边缘），所以即使渐变起跑了，选中形态那 5px 也画不到书皮上。现在撤掉那层整块
scissor，改由按钮按贴图分别处理（沿用第三轮的规则）：

- **未选中形态**：恒裁在书体边缘 —— 书体盖住标签本体（连同位移中的图标与 pin，
  新增 `rbip$beginBodyClip`，翻页移动中给图标/pin 也套同一层，否则标签往里滑时图标会浮到书皮上）；
- **选中形态**：不裁 —— 允许压在书皮上，且透明度就是当前 blend，所以"一边滑到位、一边把末端贴到书皮上"
  是渐显的，落地时恰好 5px 贴住书皮（与静止时原版的选中外观完全一致）。
- 翻页期间裁剪框必须按**静止位**算（`rbip$flip.baseX/baseY`），否则框会跟着标签一起滑、等于没裁。

顺带清掉组件侧因此失效的 `rbip$tabFlipClip` 暂存字段与那次 `TabFlipGeometry.clip(...)` 调用。

离线 `--mode flip` 预览同步成 `fromExtend`（落地帧不再需要额外淡入帧），12 帧。

- 部署（备份 `20261003-234503`）：26.3 `cd759a9514a6edb83d371c3b580331ef`、
  26.2 `c178fdba8ed1ad2a21e8770fbc02fba9`、1.21.11 `225ea3e52d55cf2e88af38cd087346d2`。

> ⚠️ 1.21.11 的 `RecipeBookWidgetMixin` 用的是 `widget.render(...)`（26.x 为 `extractRenderState`），
> 移植这类"整段替换"的补丁时按名字匹配会静默漏掉 —— 本轮就先漏了一次（断言兜住、构建仍然通过）。


**补记（同日，第六轮）：回退到「静止前维持被配方书压住」**

用户实测反馈第五轮方案：*"这个方案不行，会出现很奇怪的现象（动画播放过程中被压在配方书下的元素闪现到配方书上）。
对于选中标签，我觉得可以选择'静止前维持被配方书压住'的状态，等到静止后再通过渐变动画转移到配方书之上。"*
—— 正是"放开移动标签的裁剪"的必然结果：选中形态一旦允许压书皮，位移中的**标签本体与图标**（半透明交叉渐变）
也会跟着压上去，看起来就是书皮上有东西在闪。

**落地（第五轮的三处改动全部回退，回到第四轮的状态）**：

1. `TabSelectFade` 删掉 `EXTEND_FADE_START` / `fromExtend`（起跑点方案废弃）；
2. `rbip$advanceSelectBlend` 翻页分支回到 **`blend = min(blend, extend)`**（只减不增）：退场随缩进淡出、
   进场按住不动；
3. `RecipeBookWidgetMixin#rbip$drawFlippingTab` **恢复整块 scissor**（按静止位算框）—— 翻页期间标签本体、
   图标、pin、以及正在渐变的选中形态一律裁在书体边缘；落地后 `tracked` 清零、裁剪解除，选中态这时才在
   按钮自己的绘制路径里淡入（那条路径只裁未选中形态，所以贴着书皮的那 5px 是渐显上去的）；
4. 删掉第五轮为图标/pin 加的 `rbip$beginBodyClip` 与 `rbip$beginSelectClip` 的"翻页也算静止位"分支。

最终行为：**翻页全程选中标签被配方书压住 → 静止后约 0.25s 内把选中形态（含那 5px）渐变到书皮之上**，
两个阶段各自干净、没有任何穿插闪现。第三轮那条"只裁未选中形态"的规则仍然只服务于**非翻页**的点击切换。

离线 `--mode flip` 预览同步回第四轮（11 帧翻页 + 6 帧落地淡入 = 17 帧）。

- 部署（备份 `20261003-235727`）：26.3 `a09bee38acccb6e696ae15772562a9e3`、
  26.2 `b0c9de347e6ae1e1bcc65f283d662f40`、1.21.11 `a53615947fb2125805aae6ae462be8ab`。


**补记（同日，第七轮）：落地后的专属渐变——真正的病根是「速率用错了那条」**

用户反馈：*"最后的专属渐变过渡没有实现，选中标签在停下来后是直接闪现到配方书顶上的。"*
—— 机制其实跑着，但**快得看不见**：落地后的淡入调用的是 `rbip$selectFadeRate()`，也就是
**点击切换**那条速率（`rate = 6.2 / (时长 × 0.5)`，默认 24.8/s），而本代码的换算约定是
`delta/20` 秒（与配方区动画一致），于是每帧就走掉 `1 - e^{-24.8/20} ≈ 71%` —— **2~3 帧结束**，
肉眼当然就是"啪"一下闪上去。对照：翻页动画用 12.4/s（每帧 46%），靠指数尾巴才有 ~11 帧的观感。

**落地**：新增一条**专属的落地曲线**，与点击切换分开：

- `TabSelectFade.POST_FLIP_DURATION_FACTOR = 3.5`、`postFlipRate(duration) = 6.2 / (时长 × 3.5)`
  —— 默认 0.5s 配置下 3.54/s，每帧约 16%，**约 0.22s 走完九成**（收敛 36 帧，尾部肉眼不可见）；
- `TabFlipState.postFlipFade`：组件在 `rbip$endTabFlip()` 里给**参与过本次翻页**的标签置位
  （此刻它们还被书体裁着、选中形态尚未显形）；
- `rbip$advanceSelectBlend` 的常规分支据此切换速率，渐变到端点就自行清掉标记，
  交回点击切换那条更跟手的快曲线（点击切换手感不变）。

各速率实测（换算 = `delta/20` 秒/帧）：

| 速率 | 每帧推进 | 收敛帧数 |
|---|---|---|
| 24.8/s（原用的点击切换） | 71% | 6 帧 ≈ 0.10s ← 看着就是闪现 |
| 12.4/s（翻页本体） | 46% | 11 帧 ≈ 0.18s |
| **3.54/s（新的落地专属）** | **16%** | **36 帧 ≈ 0.60s（九成在 0.22s）** |

离线 `--mode flip` 预览同步成 `postFlipRate`（31 帧：11 帧翻页 + 20 帧落地淡入）。

- 部署（备份 `20261004-000804`）：26.3 `4f619b476c6b206eba2001162375570e`、
  26.2 `89e4d43634115f8eb2cdec46c8cf7723`、1.21.11 `ef287fe70c55c061ff37cd48b1cd327f`。

> 调快/调慢只有 `TabSelectFade.POST_FLIP_DURATION_FACTOR` 一个旋钮（调大 = 更慢更明显）。


**补记（同日，第八轮）：落地渐变终于成立的真正根因 —— 被"瞬间隐藏"的标签带着旧选中态**

用户第二次反馈"落地那一下还是闪现"。这轮不再靠推理，先查实例日志确认跑的是新 jar（会话
00:11:59 启动 > 00:08 部署 ✓），再顺着数据流找到了真正的洞：

- **翻页动画分支只处理 `tracked` 的标签**（`blend = min(blend, extend)`）；
- 但标签还有一条**被瞬间隐藏**的路径：点标签触发"跟随当前标签翻页"、搜索重排、重开配方书
  —— 这些都不走翻页动画，`rbip$applyPagination` 直接把标签 `visible = false`；
- 隐藏的标签不会被绘制 → `rbip$advanceSelectBlend` 根本不跑 → 它的 `blend` **冻在 1**
  （隐藏前它正是选中的那个）；
- 之后滚轮翻回那一页：`rbip$syncFlipTargets` 只把 `extend` 重置为 0（从书体内部伸出），
  **没有重置 `blend`** → 伸出过程中 `min(1, extend) = extend`，选中态跟着一起涨满 →
  落地时 `blend` 已经是 1 → 第七轮那条"落地专属曲线"**根本轮不到播放**。

**修复**：给 `RecipeGroupButtonFlipAccess` 加 `rbip$beginFlipIn()`（把 `blend` 归零并把首帧吸附
标记置位），`rbip$syncFlipTargets` 在"标签新登场（`!state.tracked` → `extend = 0`）"那一支
一并调用 —— 登场标签的选中态也从"未显形"起算，与它从书体内部伸出的语义一致。

- 顺带把第七轮那两条临时诊断日志（`[BRBE-FADE]`）删掉，并清掉实例里的 `.bak.diag` 备份。
- 部署（备份 `20261004-001946`）：26.3 `c4f6f9cfa07d28ce855d56680864e1b7`、
  26.2 `9ac68b856115225a350da91ef601610b`、1.21.11 `e3a70e140b2b8d05298fed558d8f2082`。

> 教训记一笔：**"隐藏"是渐变状态机的盲区** —— 任何让标签不再被绘制的路径都会冻结它的
> `blend`，所以凡是"让它重新登场"的地方都必须显式重置，不能只重置 `extend`。


**补记（同日，第九轮）：落地转移做成"裁切 + 贴合带渐显"（用户已确认），并补锻造台/酿造台**

用户对第八轮的结论："有动画了，但效果不对 —— 会短暂变成未选中标签，比较破碎"；改成现在的形态后确认
"对，基本上对了"，随后又要求"过渡动画应该发生在停下来的一瞬间"。

**① 语义纠正：不是交叉淡化，是"裁切 + 局部渐显"**
- 翻页期间：选中标签**全程保持选中形态**（`beginFlipIn` 吸附到自身当前形态，不再归零；`advanceSelectBlend`
  的 tracked 分支也不再拿 `extend` 去夹 `blend`）——整块被书皮裁住，看上去就是"被配方书压住"。
- 落地后：**只让越过书皮边缘的那一条贴合带渐显**（正常朝向 = 右端 3×27：标签在选中位左移 2px，
  贴图 x30–34 里越过书体左缘的是 x32–34），本体 / 图标 / 位移一帧都不变。
- 实现：`rbip$beginBodyClip`（按 `TabFlipGeometry.clip` 的书体边裁住）+ `rbip$beginMergeStripClip`
  （同一条边到标签外缘，屏幕坐标；三朝向各一条），两块严丝合缝，落地瞬间不漏像素也不重复画。
- 曲线改为 **ease-out 0.35s**（`TabSelectFade.landingFade`）：smoothstep 起步太平
  （0.5%→2%→4%），前 0.1s 肉眼等于没动，观感是"停下来愣一下"；ease-out 第一帧就走 ~9%。

**② 真正的堵点（前两轮为什么"没变化"）**：`extractContents` HEAD 里那行
`if (animationTime > 0 || blend == target) return;` **把推进代码整段挡在门外** —— 落地瞬间 blend 已经是
端点值，于是"慢曲线/归零"一行都不会执行。修复：落地窗口（`postFlipFadeT ≥ 0`）强制放行。
配套两处：`RBIP_TAB_FLIP_LAND = 0.5 / RETRACT_DISTANCE`（半像素内位移取整后与静止位逐像素一致，
就在这一刻收尾 —— 否则画面停住后指数尾巴还要再跑 3~4 帧才算结束，用户感知为"停下来愣一下"）。

**③ 锻造台 / 酿造台配方书（同一轮用户要求）**：子代理只读调查结论 —— 这两本书用
**BRBE 自己的 `BRBGroupButtonWidget`**（`extends StateSwitchingButton`，35×27，原版 `recipe_book/tab*` 贴图，
选中 `x -= 2`、图标 `-2`），与 vanilla `RecipeBookComponent` **无继承关系** ⇒ RBIP 挂在
`RecipeBookComponent` / `RecipeBookTabButton` 上的 mixin 对它们完全不生效；且标签栏是左列单列竖排
（`refreshTabButtons`，`x = 书体左缘 - 30`，与书体重叠 5px，几何与 RBIP NORMAL 逐字一致）、
**没有标签栏分页**（锻造 2 个 / 酿造 3 个标签，一屏放得下）⇒ "缩进/伸出"那半截没有触发源。
因此按"选中态过渡"落地（改 2 处源码、0 mixin）：
- `generic/BRBGroupButtonWidget.java`：新增 `selectBlend` / `selectBlendInit` / `selectClip`，
  `extractWidgetRenderState`（1.21.11 为 `renderWidget`）改为**交叉渐变自绘** —— 同一插值位置画两张贴图
  （未选中形态在渐变中按书体边裁住、选中形态压在书皮上）、图标按 `TabSelectFade.shift(blend)` 插值偏移；
  曲线 / 开关 / 时长直接复用 `TabSelectFade` 与 `BetterRecipeBook.config.pageAnimation*`（**无新增配置项**）。
- `setStateTriggered` 仍是唯一真相源，`GenericRecipeBookComponent` 一行未动 ⇒ 选中逻辑、集合更新零风险。
- ⚠️ 依赖方向：`com.alonie.brbe.*` 首次 import `com.alonie.recipebookispain_extended.animation.*`
  （同 jar、纯数学类，无配置耦合）。

**部署**（备份 `20261004-004113` / `20261004-004243`）：26.3 `83cdee15c2a4baa543151102c2006a07`、
26.2 `8993d881d87f39c0f62e9b962257f0dd`、1.21.11 `9aa9b80b735ebef14092f6b057bf8fa7`。
离线预览 `tools/tab-flip-anim/preview.py` 已同步（`--mode flip` 的落地段改为贴合带 ease-out 渐显，
收尾判据同为半像素）。

> 教训（两轮空转的根因）：**改一段"推进/插值"代码时，先确认它所在的分支真的会被执行** ——
> 前两轮都改在了同一处 early-return 之后，代码正确但永不运行。


**补记（同日，第十轮）：把过渡提前到"停靠过程里" —— 固定停靠窗口 + 位移驱动的渐显（用户已确认）**

用户提议：*"在标签停靠前一段时间就开始播放渐变过渡动画，不过是截取选中标签的右边和配方书重合的
部分来播放渐变过渡动画，就可以做到缓慢停靠和渐变过渡同时进行了"*。

**落地**（三分支同步）：
- `TabSelectFade.DOCK_REVEAL_DISTANCE = 12`（像素）+ `dockReveal(remainingPx)`：渐显量**直接跟着
  剩余位移走**（还剩 12px 时为 0，走到静止位正好为 1）。位移本身是指数减速的，所以时间上天然是
  ease-out，而且**不需要任何计时器** —— 也就不可能出现"停下来之后再淡一下"的割裂。第九轮那套
  `postFlipFadeT` / `LANDING_FADE_DURATION` / `landingFade` 随之退役。
- `RecipeGroupButtonFlipAccess#rbip$revealsDock()`（新增）：**登场 + 选中 + 离静止位 ≤ 12px**。
  组件据此决定要不要跳过"整块裁到书皮边缘"的外层 scissor —— 跳过之后由按钮自己拆成
  「本体（含图标、pin，裁在书皮边缘）+ 固定停靠窗口（按剩余位移渐显）」，两块拼满整块标签。
- 裁剪基准改为**静止位**（`rbip$clipBaseX/Y`：翻页中取 `state.baseX/baseY`，其余取 `getX()/getY()`）——
  翻页时标签被临时挪到动画位置，而书皮边缘固定在静止位，不这样改窗口会跟着标签漂。
- 停靠窗口里画的是**静止位贴图本身**（`clipBaseX() - shift` 位置），窗口与内容**都不动**：

| 阶段 | 画面 |
|---|---|
| 伸出（> 12px） | 整块被书皮裁住（原样） |
| 停靠窗口（≤ 12px） | 本体照旧裁在书皮边缘；窗口里逐渐显出"标签最终停靠的那一小段"（位置与内容都固定），标签从它下面滑过去 |
| 到位 | 窗口 alpha 正好到 1，与本体拼成完整的选中形态 —— 无缝衔接，且此后按原版路径整块绘制 |

**用户实测抓到的两处偏差（都已修）**：
1. **只有选中标签该做** —— 第一版由组件按"登场 + 进窗口"判定并跳过外层裁剪，把未选中标签也算进去了
   （它们的图标/pin 因此露到书皮上）。改为标签自己判定（`rbip$revealsDock`），组件只问结果。
2. **窗口不能动，窗口里的内容也不能动** —— 第一版窗口跟着标签走（越界区）；第二版窗口固定了但内容
   还在"窗口里滚动"；最终版连内容也固定在静止位的贴图上。

**部署**（备份 `20261004-014219`）：26.3 `a207840e1d18b28ddb7f1f273b49db9c`、
26.2 `1383c588324ee0d8feae329b57d5439d`、1.21.11 `e25a61e70896217952aa9ff6ea88441c`。
离线预览 `tools/tab-flip-anim/preview.py`（`--mode flip`）同步：逐帧按剩余位移给停靠系数，
落地后不再追加淡入帧。

> 计时器 vs 位移驱动：这类"和运动耦合的渐变"用**剩余位移**驱动比用时间驱动更好 ——
> 天然同步、帧率无关、不会被指数尾巴拖出"停一下再淡"的观感。


**补记（同日，第十一轮）：上/下侧标签"取消选中时纹理消失" —— enableScissor 会按 pose 变换裁剪框**

用户报了一个此前没测到的严重问题：*"上侧和下侧的标签在取消选中的过程中标签纹理会消失"*。

**根因（反编译 26.3 `GuiGraphicsExtractor` 实锤）**：
```java
public void enableScissor(int x1, int y1, int x2, int y2) {
    ScreenRectangle rect = new ScreenRectangle(x1, y1, x2 - x1, y2 - y1);
    ScreenRectangle transformed = rect.transformAxisAligned(this.pose);   // ← 按当前 pose 变换
    this.scissorStack.push(transformed);
}
```
裁剪框**只在 `enableScissor` 那一刻**按当前 pose 变换（绘制时只用存好的屏幕矩形，不再变换）。
而旋转条带（上/下侧）的整块绘制都在 90° 旋转矩阵里进行，`rbip$beginSelectClip`（以及本轮新加的
停靠渐显裁剪）就在矩阵**之内**设置裁剪 → 裁剪框被旋转 + 平移到了完全不相干的位置 → 标签整块被裁掉。
只有「取消选中」渐变期间才设裁剪，所以静止时看不出来；正常朝向没有矩阵（pose 为单位阵，变换是恒等），
所以只有上/下侧标签中招。

**修复**：旋转条带改为**每次绘制都先在单位矩阵下设好裁剪 → 再 push 旋转矩阵绘制 → pop → 关裁剪**；
停靠渐显的两块（本体 / 固定窗口）各自如此处理。并在 `rbip$beginBodyClip` / `rbip$beginSelectClip`
的 javadoc 里写明「必须在单位矩阵下调用」。

**顺带审计**：全仓库 23 处 `enableScissor` 调用点扫了一遍，只有这一处处在变换 pose 内（其余都在单位
矩阵下）。另外 `rbip$drawTabContents` 的"新配方解锁弹跳"会 push 一个缩放矩阵再调自绘 —— 该弹跳对
RBIP 标签已被 `rbip$skipCreativeTabUnlockBounce` 取消（`animationTime` 恒为 0），已在注释中标注
"将来若放开弹跳，必须同时保证裁剪在单位矩阵下设置"。

**部署**（备份 `20261004-020239`）：26.3 先单独验证通过（`845d5b4ff1eaf800045a0b858c11a60f`），
随后三分支同步重建部署 —— 26.3 `c98f0d0bcbbabf3c01a95b34ceb8ac64`、
26.2 `4a2562a6908a1308785ed72cbd2b7cda`、1.21.11 `aadb092bd0a986582441381dbff266cd`。
