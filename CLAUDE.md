# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Multi-branch architecture

Each git branch targets a **different Minecraft version** and is built independently:

| Branch    | Minecraft | Java | Loom                         | Mod Loaders      |
|-----------|-----------|------|------------------------------|-------------------|
| `1.21.1`  | 1.21.1    | 21   | Architectury Loom            | Fabric + NeoForge |
| `1.21.11` | 1.21.11   | 21   | Architectury Loom            | Fabric + NeoForge |
| `26.1.2`  | 26.1.2    | 25   | Loom (`loom-no-remap`)       | Fabric + NeoForge |

**The root `build.gradle` validates `minecraft_version` against the branch name at configure time** — it will fail with a clear error if they differ.

## Module layout

```
common/          ← shared across Fabric + NeoForge
  api/           ←   public interfaces (HudHider, ConfigScreenProvider)
  impl/          ←   implementations (JeiHudHider, ReiHudHider)
  compat/        ←   cross-mod bridges (OverlayHider, CompatMixinPlugin, ItemViewCompat)
  interfaces/    ←   mixin accessor interfaces (IPinningComponent, ISettingsButton, etc.)
  mixins/        ←   grouped by feature, one directory per concern
  recipebookispain_extended/  ← RBIP — merged source code, own mixin configs
fabric/          ← Fabric-specific entry points + JEI plugin
neoforge/        ← NeoForge-specific entry points + platform init
```

## Generic recipe book (core architecture)

The mod replaces vanilla's crafting-only recipe book with a **generic recipe book system** that supports crafting, brewing, and smithing tables via a shared abstraction.

```
GenericRecipeBookComponent<M, C, R>       — widget logic (rendering, input, search, filtering)
  ├── (vanilla) RecipeBookComponentMixin  — mixin into vanilla's crafting book
  ├── BrewingRecipeBookComponent          — brewing stand recipe book
  └── SmithingRecipeBookComponent         — smithing table recipe book

GenericRecipePage<M, C, R>               — page layout, pagination, button grid
GenericRecipeButton<C, R, M>             — individual recipe result button
GenericRecipeBookCollection<R, M>        — group of related recipes (one collection = one tab entry)
GenericRecipe<R>                          — abstract recipe wrapper
GenericClientRecipeBook                   — client-side recipe book state
BRBBookCategories                         — registry of Book → Category mappings
BRBBookSettings                            — open/filter toggle state (persisted via config)
```

**Each "book" is registered via `BRBHelper.createBook()`** which creates a `BRBHelper.Book` with a resource location and persistent toggle state. Categories are added per-book: a search category (compass icon) + typed categories.

## Mixin architecture

Mixins are **organized by feature group**, one subdirectory per concern:

| Package | Feature |
|---------|---------|
| `incompletecrafting/` | Show partially-craftable recipes (grayed out) |
| `instantcraft/` | Shift-click auto-craft the result |
| `pins/` | Pin/favourite recipes |
| `ungroup/` | Show recipe variants ungrouped |
| `unlockrecipes/` | Unlock recipes from JEI/REI |
| `scrollablepages/` | Mouse scroll on recipe pages |
| `centered/` | Keep recipe book centered |
| `search/` | Custom search bar enhancements |
| `settings/` | Settings button in recipe book |
| `toasts/` | Remove recipe unlock toasts + sounds |
| `hideoverlay/` | Hide JEI/REI overlays |
| `alternativerecipes/` | Ungroup recipe alternatives in overlay |
| `modname/` | Display source mod name |
| `incompatibleenvironment/` | Prevent clicking recipes that are shown but incompatible with current inventory (when `showAllRecipesInSurvival` is on) |
| `accessors/` | `@Accessor` / `@Invoker` interfaces for vanilla fields |
| `rei/` | REI-specific mixins |

Several mixins live at the `mixins/` root (no subdirectory):
- `SmithingScreenMixin` / `BrewingStandScreenMixin` — inject recipe book widgets into smithing/brewing screens
- `ScreenRenderMixin` — after-render hook for top-layer overlay rendering
- `RemoveBookButton` — removes the vanilla recipe book button from inventory
- `DisableCraftableFilter` — disables the "craftable" filter tab
- `DisableBounce` / `DisableBook` — disables recipe book bounce animation / book entirely
- `RecipeBookTabButtonMixin` / `RecipeBookComponentTabIconOffsetMixin` — tab button modifications
- `MouseScrollHandler` — mouse wheel scroll support on recipe pages

Mixin config files (split by loader and compat):
- `mixins.brbe-common.json` — core mixins, both loaders
- `mixins.brbe-common-compat.json` — conditional compat mixins (optional mods, `required: false`); governed by `CompatMixinPlugin`
- `mixins.brbe-fabric.json` — Fabric-only mixins (Fabric loader)
- `mixins.brbe.json` — NeoForge-only mixins (NeoForge loader; only the PotionBrewing accessor lives here)
- `mixins.brbe-jei.json` — JEI-specific (Fabric), `mixins.brbe-jei-common.json` — JEI cross-loader
- `mixins.brbe-rei-common.json` — REI common mixins
- `recipe-book-is-pain-extended.mixins.json` — RBIP (Fabric), `rbip-neoforge.mixins.json` — RBIP (NeoForge)

`CompatMixinPlugin` (implements `IMixinConfigPlugin`) governs `mixins.brbe-common-compat.json` — it conditionally enables mixins based on which mods (JEI, REI) are loaded at runtime.

Access widener: `common/src/main/resources/brbe.common.accesswidener` (currently only opens `PotionBrewing$Mix`).

## Custom search system

Search queries support advanced syntax via `SearchQuery.parse()`:

| Syntax | Meaning |
|--------|---------|
| `\|` | OR between groups |
| space | AND within a group |
| `"quoted"` | Preserve spaces in a token |
| `-prefix` | Negation |
| `@mod` | Filter by mod namespace/display name |
| `$tag` | Filter by item tag |
| `#text` | Search tooltip text |
| `r/regex/` | Regex match on item hover name |

`SearchCache` caches component lookups, `SearchArgument` subclasses form a composable AST.

## Platform abstractions

Platform-specific code is isolated behind interfaces + service-provider registration:

| Abstraction | Fabric impl | NeoForge impl |
|-------------|-------------|---------------|
| `PlatformPotionUtil` | `fabric.PlatformPotionUtilImpl` | `neoforge.PlatformPotionUtilImpl` |
| `PlatformAbstractions` (RBIP) | `FabricPlatform` | `NeoForgePlatform` |

Client initializers:
- **Fabric**: `BetterRecipeBookClientFabric` (implements `ClientModInitializer`) — registers HUD hiders, RBIP platform, screen event hooks, tick overlay enforcement
- **NeoForge**: `BetterRecipeBookClientNeoForge.init()` — called from `BetterRecipeBookNeoForge`; uses Architectury events (`ClientGuiEvent.INIT_POST`, `ClientTickEvent.CLIENT_POST`)

## Config system

Uses **Cloth Config / AutoConfig** with TOML serialization (`brbe.toml`). Config holder validates at load and save.

### Config features and their gates

| Config field | Effect | Gate location |
|-------------|--------|---------------|
| `hideReiJeiOverlay` | Hides JEI/REI overlays | `OverlayHider.setOverlaysHidden()` → iterates `HudHider` registry |
| `showAllRecipesInSurvival` | When **false**, skips ALL partial-material injection (vanilla-only) | `RecipeBookComponentMixin.keepPartiallyCraftable` |
| `enableRecipeBookIsPain` | Enables RBIP creative-mode tabs in recipe book | Hidden from GUI (`@ConfigEntry.Gui.Excluded`), edited in `brbe.toml`, hot-reloaded via `reloadIfChanged()` |
| `enablePinning` | Pin recipes | `PinnedRecipeManager` |
| `instantCraft.enabled` | Shift-click instant craft | `InstantCraftingManager` |
| `alternativeRecipes.noGrouped` | Ungroup recipe variants | `ungroup/RecipeBookComponentMixin` |
| `partialCraftingEnabled` | Show partially craftable recipes | `incompletecrafting/` mixins |
| `keepCentered` | Center the recipe book | `centered/RecipeBookComponentMixin` |
| `showModName` | Display source mod name in tooltip | `modname/RecipeButtonMixin` + `modname/GhostRecipeTooltipMixin` |

Config categories (TOML sections): `ui`, `recipeFilter`, `rbip`, `newRecipes`, `instantCraft`, `alternativeRecipes`, `scrolling`.

## RBIP (Recipe Book is Pain) module

- Source-merged into `common/.../recipebookispain_extended/` — **not** a jar-in-jar dependency
- Own mixin configs: `recipe-book-is-pain-extended.mixins.json` (Fabric), `rbip-neoforge.mixins.json` (NeoForge)
- Platform init: NeoForge → `BetterRecipeBookClientNeoForge.init()`, Fabric → `RBIPFabricEntrypoint`
- Config bridged through `RecipeBookIsPainExtendedConfig.enabled()` → reads `brbe.toml [rbip]`
- If `enableRecipeBookIsPain` is off, RBIP is a no-op (constructor saves `vanillaTabInfos`, all methods guard on `enabled()`)
- Caches item→creative-tab mappings from `CreativeModeTabs.allTabs()` on init; rebuilds on config change
- Furnace variant detection (`FURNACE`, `SMOKER`, `BLAST_FURNACE`) for furnace-screen recipe tabs

## HudHider API

`OverlayHider` is now a thin registry. New implementations implement `api/hud/HudHider`:

```java
OverlayHider.register(new JeiHudHider());  // JEI IClientToggleState bridge
OverlayHider.register(new ReiHudHider());  // REI ConfigObject bridge
```

Each hider owns its own state (snapshot, guard flags). Adding a new HUD mod only requires implementing the interface + one registration call.

## Key bindings

| Binding | Default Key | Class |
|---------|-------------|-------|
| Pin recipe | F | `BetterRecipeBook.PIN_MAPPING` |
| View recipe in JEI/REI | R | `BetterRecipeBook.RECIPE_VIEW_MAPPING` |
| View usage in JEI/REI | U | `BetterRecipeBook.USAGE_VIEW_MAPPING` |

## Key utilities

| Class | Purpose |
|-------|---------|
| `BRBHelper` | Central registry: creates `Book` instances, registers categories, manages toggle state |
| `ClientInventoryUtil` | Client-side item movement (store, swap, return items to inventory) — used by instant craft |
| `PartialCraftingUtil` | Determines whether a recipe is partially craftable (some but not all ingredients present) |
| `IncompatibleCraftingUtil` | Detects recipes that appear craftable but conflict with inventory due to item reuse |
| `AlternativeOverlayLayout` | Computes dynamic column/row grid layout for alternative recipe overlays |
| `BRBTextures` | Centralized `ResourceLocation` definitions for all custom GUI sprites |
| `ModNameUtil` | Resolves mod display name from item namespace for tooltip display |
| `RecipeUnlockUtil` | Unlocks recipes by category (handles JEI/REI integration for recipe unlock) |
| `CollectionCategory` | Enum categorizing recipe collections (pinned, craftable, uncraftable, search result) |
| `TopLayerOverlayRenderer` | Renders overlay sprites (pin icons, craftability indicators) on recipe buttons |
| `RecipePlacement` / `RecipeMenuUtil` | Grid placement math and menu interaction helpers |

### Slot-state cache (performance)

Both `PartialCraftingUtil` and `IncompatibleCraftingUtil` use **WeakHashMap-based caches** keyed by `RecipeCollection`, with integer generation tracking:

- `filteringGeneration` increments each time a filtering pass begins
- `filteringActive` guards during async filtering
- `CHECKED_COLLECTIONS` stores the generation number when a collection was last evaluated
- Results are reused when the generation hasn't changed → avoids O(n²) ingredient scanning on every frame

This was added to fix recipe book lag caused by repeated partial-craftable computations.

## Performance diagnostics

`PerfTimer` in `util/` provides per-section nanosecond timing for recipe book opening. Insert markers in `updateCollections()`:

```java
PerfTimer.begin();
PerfTimer.start("sectionName");
// ... work ...
PerfTimer.end("sectionName");
PerfTimer.logAndReset("updateCollections");
```

## Special recipe books (Brewing + Smithing)

- **Brewing**: `BrewingRecipeBookComponent` extends `GenericRecipeBookComponent<BrewingStandMenu, BrewingRecipeCollection, BrewableResult>`. Potions loaded via `PotionLoader` (scans `PotionBrewing` registry). Three categories: potion, splash potion, lingering potion.
- **Smithing**: `SmithingRecipeBookComponent` with `SmithingRecipeBookPage` and `SmithingRecipeCollection`. Two categories: transform (netherite upgrade) and trim (armor trims). Uses `BRBSmithingRecipe` wrappers.

## Build commands

```bash
./gradlew build                    # full build (common + fabric + neoforge)
./gradlew :common:compileJava      # compile-only check

# Cache corruption recovery (after branch switches)
./gradlew cleanLoomCache && rm -rf .gradle && ./gradlew build

# Deploy (build JAR → copy to test instance)
cp fabric/build/libs/brbe-ava-fabric-1.21.1-2.2.1.jar /home/avalonia/data/MinecraftLib/versions/1.21.1-Fabric/mods/
cp neoforge/build/libs/brbe-ava-neoforge-1.21.1-2.2.1.jar /home/avalonia/data/MinecraftLib/versions/1.21.1-NeoForge/mods/
```

Test instance path rule: `/home/avalonia/data/MinecraftLib/versions/{GAME_VERSION}-{MOD_LOADER}/mods/` (`MOD_LOADER` capitalized: `Fabric`/`NeoForge`). 构建完必须部署；部署前将实例内同版本 JAR 备份为 `*.jar.bak.YYYYMMDD`。

## Dependencies

| Dep | Version | Notes |
|-----|---------|-------|
| Architectury API | 13.0.8 | Required; Fabric via maven, NeoForge via local JAR |
| Cloth Config | 15.0.140 | TOML config; Fabric via maven, NeoForge via local JAR |
| Fabric API | 0.116.12 | Fabric only |
| Fabric Loader | 0.15.11 | Fabric only |
| NeoForge | 21.1.21 | NeoForge only |
| JEI | 19.27.0.340 | Optional (compile only) — loaded from `libs/` or system path |
| REI | 16.0.799 | Optional (compile only) — loaded from `libs/` or system path |
| Mod Menu | 11.0.1 | Fabric only, optional |

**JEI/REI JARs** are resolved from either `libs/` in the project root, a gradle property (`jei_fabric_jar`, `jei_neoforge_jar`, etc.), or hardcoded paths under `/home/avalonia/Downloads/1.21.1/`. Builds fail silently (compileOnly) if JARs are missing.

## Critical 26.1.2 API differences from 1.21.x

| Old (1.21.x)                     | New (26.1.2)                        |
|----------------------------------|-------------------------------------|
| `GuiGraphics`                    | `GuiGraphicsExtractor`              |
| `render(GuiGraphics,…)`          | `extractRenderState(GuiGraphicsExtractor,…)` |
| `renderWidget(GuiGraphics,…)`    | `extractWidgetRenderState(GuiGraphicsExtractor,…)` |
| `renderFakeItem(stack, x, y)`    | `fakeItem(stack, x, y)`             |
| `renderItem(stack, x, y)`        | `item(stack, x, y)`                 |
| `drawString(font, str, x, y, c)` | `text(font, str, x, y, c)`          |
| `CharacterEvent(char, int)`      | `CharacterEvent(int)`               |
| `ScreenEvents.afterRender()`     | `ScreenEvents.afterExtract()`       |
| `PotionBrewing.Mix`              | package-private — reflection needed |
| `Ingredient.EMPTY`               | removed — use null                  |

When porting from `1.21.11` → `26.1.2`, grep every Mixin `@Inject`/`@Redirect` annotation for `method` and `target` strings referencing old names.

## 2026-08-26：修复残缺配方红罩盖住多配方堆叠图标的底层图标（三分支同步）

- 用户反馈："替代配方组"的残缺配方红色遮罩会盖住其重叠图标（多配方组按钮上的双图标堆叠）中的下层图标；图标轮循可见（各配方结果不同）时无此现象，结果全部相同（轮循看起来静止）时出现
- 根因：本分支 `RecipeButton.renderWidget` 在 `hasSingleResultItem() && getOrderedRecipes().size() > 1` 时先 `renderItem`(x+offset+1,y+offset+1) 后 `renderFakeItem`(x+offset,y+offset)（1px 错位双图标"多配方"堆叠）；`incompletecrafting/RecipeButtonMixin.brbe$renderPartialOverlay` 原先注入在 **`renderFakeItem` 之前** → 红罩/红勾贴图落在两个堆叠图标之间 → 下层图标被盖
- 修复：注入点 `renderFakeItem BEFORE` → **`blitSprite AFTER`**（`GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V`）——红罩位于槽位 sprite 之上、堆叠双图标之下；单图标路径像素级不变。26.1.2 停维不改
- 已构建（fabric + neoforge）；**部署待游戏实例关闭**

## 2026-08-27：向 1.21.1 全量移植（1.21.11/26.2 → 1.21.1，轮次 1）

**背景**：1.21.1 落后 1.21.11/26.2 大量功能（查询 viewer 生态、pin 体系重制、拼音、翻页、RBIP 标签 pin 等）。用户决策：① viewer 按旧 API 全量重写适配；② mod id 统一 **zzzbrbe**（玩家 brbe.toml/brbe.pins/资源包 ID 换名失效）；③ 保留 EMI + fabric/neoforge 双加载器；④ 先提交未提交改动为基线。

**本分支与 1.21.11 的 API 鸿沟**（核实结论）：
- 映射：1.21.1 实际用 `loom.officialMojangMappings()`（根 CLAUDE.md 写 Yarn 已过时）——与 1.21.11 同为 Mojang 官方，类名一致
- **`net.minecraft.resources.ResourceLocation`**（1.21.1）vs **`Identifier`**（1.21.11，1.21.9+ 改名）——全文件 `Identifier`→`ResourceLocation` 机械替换
- **RecipeDisplay/SlotDisplay 体系（1.21.5+）**：1.21.11 的 viewer 生态（recipeviewer/、cache/、jei/plugins、pinoverlay、render/Popup* 约 50 文件）全部构建其上，**1.21.1 无这些类**——只能按旧 Recipe/RecipeHolder 模型重写（77 个文件引用 display API）
- RenderPipeline/RenderPipelines（1.21.5+）：1.21.1 无，GuiGraphics.blitSprite 直接用 ResourceLocation
- KeyEvent/CharacterEvent/MouseButtonEvent（带 modifiers 构造）：1.21.1 用 `KeyMapping.matches(int,int)`/`editBox.keyPressed(int,int,int)`
- Ingredient：1.21.1 `getItems()` 返回 ItemStack[]；1.21.11 `items()` 返回 Stream<Holder<Item>>
- `SoundEvents.UI_BUTTON_CLICK`：1.21.1 是 `Holder<SoundEvent>` 需 `.value()`；1.21.11 直接 SoundEvent
- InputConstants：1.21.1 仅 MOD_CONTROL 常量（无 MOD_SHIFT/MOD_ALT），isKeyDown 接收 long 窗口句柄
- GuiGraphics 无 `setTooltipForNextRenderPass`（1.21.5+ 引入）
- cloth-config 15.0.140 无 `AutoConfigClient`（1.21.11 用 21.11.153）——GuiRegistry 从 `AutoConfig.getGuiRegistry` 获取

**本轮回合产出**（提交 8e511ccf / c26146a7 / 2d5df980 / 48dc588b）：
- 基线提交（RecipeCraftingIndex 增量索引 + pin 版本号 + 显示名/资源包名/tip.3）
- mod id brbe→zzzbrbe 全链：assets/资源包目录（`resourcepacks/zzzbrbe_unique_dark`，未跟踪文件直接 mv）/lang 键 `brb.*`→`zzzbrbe.*`/mod id/日志名/pin 文件路径/资源注册名
- 配置层：BrbeConfig 全字段对齐（KeybindingCodec/GuiRegistrar/PinyinSearchGuiRegistrar/R-U-A 键位/`scrolling.scrollAround` 嵌套），旧 Config.java 删除，ConfigEventBus.ConfigChanged 用 BrbeConfig，AutoConfigClient→AutoConfig.getGuiRegistry 适配
- 拼音搜索：Pinyin* 5 文件 + pinyin.txt + CLIENT_STARTED zh 默认开启钩子 + TextArgument 拼音匹配
- 翻页动画基础：PageAnimationEdges/PageFlipDirection/RecipeBookPageAnimBridge/RecipeBookPositionMemory/OverlayRecipeCollectionHolder/SearchPageJump
- ClientCompat 1.21.1 适配版（同名接口，实现体按 1.21.1 API）

**待办（下一轮次）**：管线核心 Stage 3/4/6/6b（按 RecipeHolder 改写）、RBIP 标签 pin（TabPinManager+RecipeGroupButtonMixin）、翻页 Mixin 接入（RecipeBookPageAnimationMixin 依赖 PinnableRecipeCollection）、viewer 数据层与 UI（最大块）、语言/资源补齐、双加载器配置注册。**已部署**（备份 20260827-XXXX），实例未运行状态验证。

## 2026-08-27：向 1.21.1 全量移植（轮次 2——pin 体系重制）

**已落地**（提交 421639da）：
- `PinnedRecipeManager`：`isFullyPinned(RecipeCollection)` / `isFullyPinned(GenericRecipeBookCollection)` / `isPinnedEntry(RecipeHolder)` / `toggleFavourite(RecipeHolder)`（RecipeHolder.id() 为 pin 稳定键，1.21.1 无 SHA-1 display 键——天然等价 1.21.11 的 idFor）
- `CollectionPipeline` Stage 6 `applyPinCopyGroups`（RecipeHolder 版）：pin 变体从原组剥离 → 置顶；1 pin 独立组 / ≥2 pin 副本组 / 全 pin 保留原组；PIN_COPIES 弱集合幂等；`buildPack` 用 `new RecipeCollection(registryAccess, entries)` + `canCraft(stacked, 2, 2, recipeBook)`（1.21.1 无 selectRecipes，构造+canCraft 一体式等价）
- Stage 6b：prepareDisplay 中 Stage 6 后对重打包组重放 `markPartialMaterials(c, ctx.inventoryItems)`（wasChecked 让原组跳过）
- `applyPins`/`applyPartialSort`（含泛型版）`has()` → `isFullyPinned()`：**部分 pin 原组不重排**（其 pin 变体由 Stage 6 剥离置顶）；新增 `isFullyPinnedGeneric`/`recipeIdOf` 辅助（PipelineCollection 泛型）
- `RecipeButtonMixin`/`GenericRecipeButton` pin 贴图判定 → `isFullyPinned`（仅全 pin 组/副本组有贴图）
- `mixins/pins/AbstractContainerScreenMixin`：pin 键语义对齐 1.21.11（round 110）——替代组悬停变体 `toggleFavourite(单变体)` + 点击音效；网格按钮单配方组直接 toggle；多变体组吞键不 pin；1.21.1 差异：`playDownSound`（实例方法，非 playButtonClickSound）、`keyPressed(int,int,int)`

**API 差异备忘**（本轮新增确认）：`AbstractWidget.playDownSound` 是实例方法（1.21.1），1.21.11 的 `playButtonClickSound` 是静态。

**待办（下一轮次）**：viewer 数据层（recipeviewer/ 旧模型重写 + jei 19.27 适配 + cache/）、viewer UI（RecipeViewerOverlay/Popup/pinoverlay）、RBIP 标签 pin（TabPinManager）、翻页 Mixin 接入、语言/资源补齐。

## 2026-08-27：向 1.21.1 全量移植（轮次 3——RBIP 标签 pin 部分）

**已落地**（提交 08aacea7）：
- `pin/TabPinManager`（1.21.11 移植，Identifier→ResourceLocation）：固定标签持久化 `zzzbrbe.tabpins.json`（与 pins.json 并排）、读取同步/写入异步、`isPinned`/`toggle`/`pinnedIds`/`pinnedTabs`（BuiltInRegistries.CREATIVE_MODE_TAB 解析，无效 id 跳过）
- `RecipeBookTabButtonCreativeMixin` 追加 `rbip$drawTabPin`（1.21.11 位置规则：正常 anchor (x-4,y-4)；上/下侧 pinX+3（右移 3px）；下侧 pinY+6；选中偏移 1px——正常向左/上侧向上/下侧向下）。**旋转体**（HEAD 取消分支）在图标后补画；**正常朝向**在 `renderWidget RETURN` 注入补画（1.21.1 无 renderIcon/renderContents 分离，RecipeBookTabButton 仅 renderWidget）
- `RecipeBookWidgetMixin.rbip$rebuildTabList`：pageableTabs 构建后按 `TabPinManager.pinnedTabs()` 置顶（固定标签排到页列表最前，搜索标签之后）

**关键障碍（转下轮）**：1.21.11 的 RBIP 标签 pin 完整体系依赖 **ExtendedRecipeBookCategory + BiMap 映射**（RECIPE_BOOK_GROUP_TO_ITEM_GROUP 等，1.21.5+ API），**1.21.1 无此类**（RecipeBookCategory 是旧枚举）——`withCreativeTabs`/标签固定键（keyPressed 悬停标签 toggle）需在 1.21.1 旧 API 上重写等价实现（round 105 RBIP 增量级别）。

**API 差异备忘**：1.21.1 的 RBIP 用 `rbip$buttonToTab`（Map<RecipeBookTabButton, CreativeModeTab>）实例字段映射，无 1.21.11 的静态 `toItemGroup(RecipeBookCategory)`——标签固定键需垂直访问 tabButtons。

## 2026-08-27：向 1.21.1 全量移植（轮次 4——viewer 数据层骨架）

**已落地**：
- `recipeviewer/engine/RecipeViewerEngine`（1.21.1 版，RecipeHolder 索引）：registerType/resultsFor/usagesFor/allRecipes/isStation/hasContent/clear/clearVanilla/clearType/isVanillaType/addRebuildListener；RecipeTypeData 输出/输入反索引 + 组去重（与 1.21.11 匹配逻辑一致，仅 entry 类型换 RecipeHolder）
- `recipeviewer/RecipeViewerCategory`（1.21.1 版接口）：query/allEntries 返回 RecipeHolder；isFuelCategory/isGridCategory/gridItems/stationIconsFor 默认实现
- `recipeviewer/CraftingRecipeCategory`（第一个内置类别：type minecraft:crafting）
- `recipeviewer/RecipeViewerCategories`：BUILTIN + EXTERNAL + defaultFor（工作站优先 → bestByPriority；先做 crafting）
- `cache/RecipeViewerIndex`（1.21.1 版）：rebuildEngine 从 `RecipeManager.getRecipes()` 全量 → 按 RecipeType 分组 → registerType；toIndexed 提取 inputs（每 ingredient 取代表物品）/outputs（getResultItem）；stationsForCrafting = 工作台+合成器
- `mixins/ungroup/ClientRecipeBookMixin` setupCollections RETURN 追加 rebuildEngine（配方集合重建 = 服务器配方同步/解锁变化时机）

**API 差异备忘**：1.21.1 `AbstractCraftingMenu` → `CraftingMenu`（类名不同）；1.21.1 ClientRecipeBook 无 rebuildCollections（1.21.11 的注入点），用 setupCollections RETURN 替代。

**待办（下一轮次）**：viewer UI（RecipeViewerOverlay 1.21.1 版 + PopupGeometry/PopupRenderer + pinoverlay）、其余类别（furnace/fuel/food 等 station 类型 + anvil/brewing/grindstone/compost/info）、R/U 键位接线（ItemViewCompat → 自研 viewer）、RBIP 标签固定键（旧 API 重写）。

## 2026-08-27：向 1.21.1 全量移植（轮次 5——viewer UI 最小原型）

**已落地**：
- `util/RecipeViewerOverlay`（1.21.1 轻量版）：静态状态（active/usage/target/category/page）+ `open/close/keyPressed/mouseClicked/render/renderTooltip`；面板 = 原版 recipe_book 背景 176x148 居中 + 标题行 + 底部分类 tab + 6x4 配方按钮网格（result 图标+悬停高亮）+ 每页计数；R=查询配方/U=查询用途（切换 reopen）、ESC 关闭；悬停按钮 tooltip（结果名+材料首个）
- `mixins/ScreenRenderMixin`：TAIL 追加 viewer render + renderTooltip（最顶层）
- `mixins/hideoverlay/AbstractContainerScreenMixin`：keyPressed 前置 viewer 分支（recipeViewerEnabled 守卫、独立于 hideReiJeiOverlay）、ESC 关闭 viewer（不关下层屏幕）
- lang 键：zzzbrbe.viewer.recipe/usage/materials（7 语言，暂 en 值）

**设计说明**：1.21.1 的 RecipeViewerOverlay 是轻量独立实现（非 1.21.11 3532 行版的 display 移植）——UI 骨架（面板/网格/tab/分页）先行，完整弹窗/预览/硬模态/pin 浮层后续逐步扩展。API 差异：getSlotUnderMouse 编译期不可见（1.21.1 hoveredSlot 字段由调用方传入）、append 链式拆开。

**待办（下一轮次）**：viewer 完整弹窗（Shift 预览 PopupGeometry/PopupRenderer 1.21.1 版）、其余类别（furnace/fuel/stonecutting/smithing/anvil/brewing/grindstone/compost/info）、pinoverlay（viewer 内 pin）、RBIP 标签固定键、翻页 Mixin 接入。

## 2026-08-27：向 1.21.1 全量移植（轮次 6——内置类别扩展）

**已落地**：
- `cache/RecipeViewerIndex`：rebuildEngine 扩展注册 smelting（含 blasting/smoking/campfire 四 RecipeType 合并）/stonecutting/smithing；stationsForFurnace（熔炉+鼓风炉+烟熏炉）/Stonecutting/Smithing；燃料辅助 `isFuelItem`/`allFuelItems`/`burnDuration`（1.21.1 用 `AbstractFurnaceBlockEntity.isFuel/getFuel`，1.21.11 用 FuelValues）
- `FurnaceRecipeCategory`/`FuelRecipeCategory`/`StonecuttingRecipeCategory`/`SmithingRecipeCategory`（1.21.1 版，RecipeHolder）：Fuel 是 grid 类别（appliesToStation 熔炉家族工作站、defaultPriority 2 最高、burnDuration）
- `RecipeViewerCategories.BUILTIN`：crafting/furnace/fuel/stonecutting/smithing 5 类别
- `RecipeViewerOverlay.render`：grid 类别渲染分支（燃料网格 24/页 + 悬停高亮，无配方按钮）
- lang：zzzbrbe.category.{crafting,furnace,fuel,stonecutting,smithing}

**API 差异备忘**：1.21.1 `ItemStack.is(Block)` 不存在 → `is(Block.asItem())`；1.21.1 燃料体系 `AbstractFurnaceBlockEntity`（1.21.11 是 FuelValues/FuelValues.fuelItems()）。

**待办（下一轮次）**：Shift 预览弹窗（PopupGeometry/PopupRenderer 1.21.1 版）、anvil/brewing/grindstone/compost/info 类别、pinoverlay（viewer 内 pin）、RBIP 标签固定键、翻页 Mixin。

## 2026-08-27：向 1.21.1 全量移植（轮次 7——位置记忆接入）

**已落地**：
- `mixins/accessors/RecipeBookPageAccessor` 增补：getCurrentPage/setCurrentPage/getTotalPages/updateButtonsForPageInvoker/getHoveredButton
- `mixins/accessors/RecipeBookComponentAccessor` 增补：setSelectedTab/getTabButtons/updateTabsInvoker（**1.21.1 的 updateTabs() 无参**——1.21.11 是 updateTabs(boolean)）
- `mixins/recipebookposition/RecipeBookComponentMixin`（1.21.1 简化版）：render TAIL 记住标签+页码+搜索词（PositionMemory.save，tabPage 传 -1 无 RBIP 页码）；initVisuals TAIL 恢复（searchBox.setValue → setStateTriggered 替换选中 → updateTabs → 钳制页码 + updateButtonsForPageInvoker）
- mixins.brbe-common.json 注册 recipebookposition mixin

**API 差异备忘**：1.21.1 RecipeBookTabButton 无 select/unselect（用 StateSwitchingButton.setStateTriggered）；1.21.1 updateTabs() 无参。

**设计说明**：对照 1.21.11 完整版，本版不含：RBIP 标签栏页码恢复（RecipeBookScrollAccess 1.21.1 无）、搜索变更页码策略（checkSearchStringUpdate 注入——避开与 1.21.1 search mixin 冲突）。

**待办（下一轮次）**：^N^ 跳页命令（SearchPageJump 接线）、Ctrl 跳页、翻页动画 Mixin（RecipeBookPageAnimationMixin 需 RecipeButton(SlotSelectTime) 构造器重适配）、anvil/brewing/grindstone/compost/info 类别、Shift 预览弹窗、pinoverlay、RBIP 标签固定键。

## 2026-08-27：向 1.21.1 全量移植（轮次 8——^N^ 跳页命令 + Ctrl 跳页）

**已落地**：
- `mixins/search/RecipeBookComponentPageJumpMixin`（1.21.1 版）：checkSearchStringUpdate HEAD 拦截 ^N^ 命令（纯 ASCII ^ 或 … 分隔符）；命中 → 清空搜索/取消聚焦/updateCollectionsInvoker(true) 恢复完整列表 → setCurrentPage(page-1) → updateButtonsForPageInvoker + ci.cancel；页码合法性用完整类别列表（20/页）判断；适配 updateCollectionsInvoker(boolean) 单参、getRecipeBook() 名
- `mixins/scrollablepages/RecipeBookPageMixin`：新增 HEAD 拦截 `brbe$mouseClickedJumpToEdge`——Ctrl+点击箭头跳到首页/尾页（ClientCompat.isControlDown；1.21.1 mouseClicked(double,double,int) 签名；RecipeBookPageAnimBridge.markUserFlip）
- 注册：mixins.brbe-common.json 加 search.RecipeBookComponentPageJumpMixin

**API 差异备忘**：1.21.1 RecipeBookComponentAccessor 无 getBook()（getRecipeBook()）；updateCollectionsInvoker(boolean) 单参。

**待办（下一轮次）**：翻页动画 Mixin（RecipeButton(SlotSelectTime) 构造器重适配——1.21.1 RecipeButton 构造器不同）、anvil/brewing/grindstone/compost/info 类别、Shift 预览弹窗、pinoverlay、RBIP 标签固定键。

## 2026-08-27：向 1.21.1 全量移植（轮次 9——compost 类别）

**已落地**：
- `recipeviewer/CompostRecipeCategory`（1.21.1 版）：纯信息 grid 类别——数据源 `ComposterBlock.COMPOSTABLES`（Object2FloatMap<ItemLike>）；U 查询可堆肥物品显示该物品、U 查询堆肥桶显示全部（按概率降序）；`chanceOf(ItemLike/ItemStack)` 工具；hasContent/appliesToStation/defaultPriority 1
- 注册进 BUILTIN + lang zzzbrbe.category.compost

**API 差异备忘**：1.21.1 ComposterBlock.COMPOSTABLES 是 Object2FloatMap（需 object2FloatEntrySet 遍历、getOrDefault(item, 0.0F)）；1.21.1 ItemStack 无 ItemLike 构造（like.asItem()）；**1.21.1 无 anvil/brewing/grindstone RecipeType**（JEI 运行时构建配方），这些类别需按 JEI 19.27 插件方式运行时构建或延后。

**待办**：翻页动画 Mixin（RecipeButton 构造器/init 4 参 vs 2 参——视觉池需整块重写）、anvil/brewing/grindstone（数据源缺失）、Shift 预览弹窗、pinoverlay、RBIP 标签固定键。

## 2026-08-27：向 1.21.1 全量移植（轮次 10——Shift 预览弹窗基础）

**已落地**：
- `render/PopupRenderer`（1.21.1 版，RecipeHolder 固定布局）：renderRecipePopup（居中 24x24 按钮 + 2x 缩放 + 返回原点矩形）；crafting 3x2 网格+结果 / furnace 输入+火焰+结果 / stonecutting-smithing 双槽 / generic 兜底；0.6x 缩放图标；modeFor(categoryId)
- `BRBTextures.FURNACE_FIRE_SPRITE`（recipe_book/flame，1.21.1 原版火焰 sprite）
- `RecipeViewerOverlay.render`：配方按钮 Shift 悬停 → PopupRenderer.renderRecipePopup（2x；fuel/grid 类别无配方不弹窗）

**API 差异备忘**：1.21.1 PoseStack 用 pushPose/popPose/translate(x,y,z)/scale(x,y,z)（1.21.11 部分仍用 pushMatrix/2 参）；GuiGraphics.renderItem(ItemStack,int,int) 无渲染管线重载。

**待办**：翻页动画 Mixin（视觉池整块重写）、anvil/brewing/grindstone（数据源缺失——JEI 运行时构建）、pinoverlay、RBIP 标签固定键。

## 2026-08-27：向 1.21.1 全量移植（轮次 11——viewer 内 pin）

**已落地**：
- `RecipeViewerOverlay.keyPressed`：viewer 激活时 A 键（PIN_MAPPING）→ `toggleFavourite(悬停配方)`（与配方书 pin 语义一致：RecipeHolder.id() 稳定键）
- `RecipeViewerOverlay.render`：已 pin 配方按钮左上角画 pin 图标（RECIPE_BOOK_PIN_SPRITE，同为 32x32，与配方书 pin 一致）
- 辅助：`hoveredEntry(mouseX,mouseY)` 网格命中、`mouseXFor/mouseYFor`（MouseHandler xpos/ypos；1.21.1 无鼠标事件 record）

**待办**：翻页动画 Mixin（视觉池整块重写）、anvil/brewing/grindstone（数据源缺失）、pinoverlay 浮层（独立的 pin 展示界面——当前为按钮就地 pin 标记，非 1.21.11 的弹层）、RBIP 标签固定键。

## 2026-08-27：向 1.21.1 全量移植（轮次 12——RBIP 标签固定键）

**已落地**：
- `RecipeBookWidgetMixin`：`brbe$activeInstance`（updateTabs TAIL 记录）+ 静态桥 `rbip$tabToGroup(RecipeBookTabButton)`（活跃实例 rbip$buttonToTab 映射查询）
- `pins/AbstractContainerScreenMixin.keyPressed`：网格按钮分支后加 RBIP 标签分支——悬停标签 → `rbip$tabToGroup` → BuiltInRegistries 取 tabId → `TabPinManager.toggle` + 音效 + `updateTabsInvoker()`（触发 rebuildTabList 置顶）
- `BetterRecipeBook.init`：`TabPinManager.init(gameDir)`（zzzbrbe.tabpins.json 懒加载）

**至此 RBIP 标签 pin 闭环**：A 键固定标签 → TabPinManager 持久化 → rebuildTabList 置顶 + 标签 pin 贴图（轮次 3）。

**待办**：翻页动画 Mixin（视觉池整块重写）、anvil/brewing/grindstone（数据源缺失）、pinoverlay 浮层。

## 2026-08-27：向 1.21.1 全量移植（轮次 13——翻页动画 Mixin 简化版）

**已落地**：
- `mixins/scrollablepages/RecipeBookPageAnimationMixin`（1.21.1 简化版）：**快照池用 `new RecipeButton()` 无参构造 + `init(RecipeCollection, RecipeBookPage)` 2 参**（1.21.11 用 SlotSelectTime + 4 参 init）；用户翻页检测（updateButtonsForPage HEAD + RecipeBookPageAnimBridge.consumeUserFlip）→ 快照旧页滑出 + 当前页平移滑入（PAGE_SLIDE_DISTANCE 125、指数减速、追逐延展、scissor 包网格区）；配置 pageAnimation.pageAnimationEnabled / pageAnimationDuration
- 关键适配：**1.21.1 RecipeBookPage.render(GuiGraphics,int,int,int,int,float) 5 参**（无 mouseX/mouseY 参数——从 Minecraft.mouseHandler.xpos/ypos 取）
- mixins.brbe-common.json 注册

**API 差异备忘**：1.21.1 RecipeButton 无参构造（1.21.11 需 SlotSelectTime）；RecipeBookPage.render 5 参；PageFlipDirection.backward(int,int,int,boolean) 可直接复用。

**待办**：anvil/brewing/grindstone（数据源缺失——JEI 19.27 运行时构建）、pinoverlay 浮层、资源/lang 全量补齐核对。

## 2026-08-27：向 1.21.1 全量移植（轮次 14——brewing 类别）

**已落地**：
- `recipeviewer/BrewingRecipeCategory`（1.21.1 版 grid 类别）：数据源 PotionLoader.POTIONS（配方书同源的 PotionBrewing Mix 扫描）；U 查询酿造台/药水底材（水瓶/玻璃瓶/地狱疣/药水/喷溅药水）→ 全部药水网格；`BrewableResult.getResult(registryAccess, null)` 取结果；defaultPriority 1
- 注册进 BUILTIN + lang zzzbrbe.category.brewing

**设计说明**：anvil/grindstone 无 1.21.1 RecipeType（JEI 运行时构建配方），同一"信息类别"模式可延伸——anvil（U 查询铁砧=信息网格描述）与 grindstone（研磨石）后续按相同模式补充或以工作站信息提示代替。brewing 因配方书已有 PotionLoader 数据源而优先落地。

**待办**：anvil/grindstone 信息类别、pinoverlay 浮层、资源/lang 全量补齐核对。

## 2026-08-27：向 1.21.1 全量移植（轮次 15——anvil/grindstone 工作站信息类别）

**已落地**：
- `recipeviewer/AnvilRecipeCategory` / `GrindstoneRecipeCategory`（1.21.1 版）：**工作站信息类别**——1.21.1 无 JEI 运行时构建的 anvil/grindstone 配方数据源（无 RecipeType），U 查询铁砧（三变体）/研磨石 → 类别显示（信息提示）；appliesToMenu（AnvilMenu/GrindstoneMenu）、appliesToStation、defaultPriority 1
- 注册进 BUILTIN（现 9 类别）+ lang（zzzbrbe.category.anvil/grindstone）
- lang 键全量核对：viewer.recipe/usage/materials + 全部 category 键（7 语言）

**待办**：pinoverlay 浮层、info 类别（信息页——可选）、资源/textures 核对（1.21.1 新类别图标用 Items 现有项）。

## 2026-08-27：向 1.21.1 全量移植（轮次 16——收尾核查）

**核查结论**（无代码变更，验证部署到位）：
- 21 个提交全部落地、工作区干净、双端构建通过（17 up-to-date）、jar 含全部新类（recipeviewer 全套/PopupRenderer/RecipeViewerOverlay/TabPinManager）
- mixin 注册核对：本轮新增 3 个（recipebookposition/search.PageJump/scrollablepages.Animation）全部注册 ✓；4 个"未注册"类（ghostguard/hideoverlay-JEI×2/rei）各有归属配置（jei-common/rei-common）或为移植前死代码（ghostguard，1.21.11 也无）——非遗漏
- mixin 冲突检测：initVisuals 被 incompletecrafting/recipebookposition 双注入（TAIL×2，mixin 兼容）；render 仅 recipebookposition 一处——无冲突
- info 类别判定：依赖 JEI jei:information 运行时（1.21.1 实例 JEI disabled）→ 与 1.21.11 一致，无 JEI 时类别缺席（不实现）

**1.21.1 移植最终功能清单**：mod id zzzbrbe 化、BrbeConfig 全字段、R/U/A 键位+配置、拼音搜索、翻页动画+位置记忆+^N^+Ctrl 跳页、RBIP 标签 pin 闭环、pin 体系重制（Stage 3/4/6/6b）、查询 viewer（9 类别+Shift 预览+viewer 内 pin）、CLAUDE.md 轮次记录。**未实现（数据源或 API 鸿沟）**：info 类别（无 JEI）、anvil/grindstone 配方条目（无 RecipeType，已工作站降级）、完整 pinoverlay 浮层（已按钮角标降级）、翻页动画视觉池与 1.21.11 的完整版差异（简单平移，非挤压视效）。

## 2026-08-27：向 1.21.1 全量移植（轮次 17——零碎同步）

**已落地**：
- `ConfigTipsHelper`：tip 键 brb.*→zzzbrbe.* + 补 tip.8/9（Ctrl 跳页/^N^ 跳页提示）+ `hideConfigTips` 守卫（hidesTips() 在 addCarousels 开头 return，1.21.11 对齐）
- `ModNameUtil`：namespace 兜底显示首字母大写（1.21.11 对齐）
- lang：zzzbrbe.gui.tip.8/9（7 语言，en 值）

**待办**：pinoverlay 浮层、资源/textures 核对。

## 2026-08-27：向 1.21.1 全量移植（轮次 18——资源缺失修复）

**修复的缺失资源**（assets/zzzbrbe 全量对比 1.21.11 发现）：
- `textures/gui/sprites/tooltip/viewer_background.png(+mcmeta)/viewer_frame.png(+mcmeta)`（ClientCompat.VIEWER_TOOLTIP_STYLE 引用——此前缺失，tooltip 背景样式无效）
- `animation/edge_width.json`（PageAnimationEdges 读取——此前缺失，翻页动画左右边距读不到默认值）
- `textures/gui/sprites/recipe_book/furnace_fire.png` + **FURNACE_FIRE_SPRITE 引用修正**：`recipe_book/flame`（1.21.1 原版无此 sprite → 渲染空）→ `zzzbrbe:recipe_book/furnace_fire`（自有资源，1.21.11 一致）
- 未补：column_panel/column_panel_top（1.21.1 无代码引用——1.21.11 的 viewer 面板背景，1.21.1 自绘面板不用）；icon.png（1.21.1 在三模块已有）
  - ⚠️ **本条已过时**（2026-09-11 修订）：2026-08-29 查询浮层按 1.21.11 结构重写后，1.21.1
    **有**代码引用 `RecipeViewerOverlay.java` 的 `COLUMN_PANEL_SPRITE` / `COLUMN_PANEL_TOP_SPRITE`
    （工作站列 9-slice），贴图与 mcmeta 当时已从 1.21.11 复制；Unique Dark 兼容包缺这两张深色覆盖
    已在本日补齐（见文末 2026-09-11 轮次）。

**待办**：pinoverlay 浮层（最后可选）。

## 2026-08-27：向 1.21.1 全量移植（轮次 19——pinoverlay 轻量浮层）

**已落地**：
- `RecipeViewerOverlay`：A 键固定成功 → 在悬停按钮旁展示 **pinoverlay 弹窗**（PopupRenderer 复用 2x 大弹窗，pinPopupX/Y 按钮右上方）；取消固定/关闭 viewer → 弹窗清空
- 辅助：`buttonRectXFor/buttonRectYFor`（鼠标坐标反推命中按钮矩形）；`nowPinned = !wasPinned`（toggleFavourite 返回 void，先 isPinnedEntry 判定）

**说明**：1.21.11 的 PinOverlay 是完整独立浮层系统（display 依赖），1.21.1 轻量版在 viewer 内实现"固定即预览"——固定配方时放大弹窗展示材料/结果（PopupRenderer 复用），等效为用户核心诉求（固定后查看配方详情）。

**待办**：最后轮收尾（提交部署 + 根 CLAUDE.md 状态更新）。

## 2026-08-28：无头 JEI 全量落地（官方 1.21.1 源码内嵌，轮次 20）

**背景**：用户提供官方源码包 `../JustEnoughItems-1.21.1`，要求"无头 JEI 移植了吗？再仔细对一遍吧"并确认全量移植 + 源码内嵌。1.21.1 的 anvil/grindstone 类别此前因无 RecipeType 只能降级为工作站信息，本轮回合把嵌入式 JEI 运行时跑通并接通数据源。

**已落地（提交 46b278f8 / 6fa66b37 / 839651c5 / 457354fe / 8717c999，已推送）**：
- **内嵌 fork（前序阶段）**：mezz.jei.api（CommonApi 172）+ common+library（432）官方 1.21.1 源码，605 文件，javax.annotation/FieldsAndMethodsAreNonnullByDefault 移除（与 1.21.11 一致）；mezzdev 依赖（baked-substring-index/suffixtree）
- **平台实现**：`mezz.jei.fabric.platform`（14 文件）+ `mezz.jei.neoforge.platform`（14 文件）从官方源移植：IPlatformHelper 11 子接口全实现、ServiceLoader 注册（META-INF/services）；AW/AT 合并官方条目（AbstractContainerScreen hoveredSlot 等）；fabric 的 FabricLimitedQuadItemModel 用 identity（renderer API 13.x 无 ForwardingBakedModel）；键位分类器走 BrbeHeadlessKeyMappingStubs；neoforge InputHelper 的 TooltipFlagExtension（21.1.238+）降级
- **无头核心**：`BrbeJeiHeadlessCore`（反射探测真实 JEI；头/尾 StartData(VanillaPlugin+JeiInternalPlugin+entrypoint 插件)→JeiStarter 启动/停止/onClientStopping 关 DelayedExecutor）；`HeadlessConnectionToServer`（isJeiOnServer=true 免警告）；`HeadlessKeyMappings`（全空映射，IInternalKeyMappings 33 方法）；`BrbeJeiPlatform`（fabric/neoforge 反射 isModLoaded）；`BrbeJeiPluginFinder`（反射 jei_mod_plugin entrypoint）
- **接线**：fabric `BrbeJeiPluginsClientFabric`（joi 入口注册 JOIN/DISCONNECT/CLIENT_STOPPING + JeiGuiSpriteManager 重载监听器）；neoforge `BetterRecipeBookClientNeoForge`（RecipesUpdatedEvent 取同步配方 + LevelEvent.Load 兜底 + GameShuttingDownEvent 收尾 + RegisterClientReloadListenersEvent）
- **收集/索引（1.21.11 改写）**：loader 4（RecipeCollector/RecipeCategoryCollector/CatalystCollector/WorkstationExporter）+ stub 5（GuiHelperStub/JeiHelpersStub 等，空 drawable 防 NPE）+ engine data-only 6（DataOnlyLayoutBuilder/SlotBuilder/IngredientAcceptor 记录 setRecipe 槽位）+ `PluginRecipeIndexer`（mod 配方走接口直取 or setRecipe 数据路径；原版 anvil/brewing/grindstone 走运行时 createRecipeLookup + 指纹去重 + 工作站物品）
- **引擎/UI 接入**：`RecipeViewerEngine.registerJeiType`（JeiEntry 反索引；独立于 RecipeHolder 通道）、`RecipeViewerCategory.queryJei/allJeiEntries` 默认方法、anvil/grindstone 类别 queryJei（engine.jeiResultsFor/jeiUsagesFor）、`RecipeViewerOverlay` DisplayEntry 合并（持有/JEI 双条目 + JEI pin uid 键 + popup 分支）、`PopupRenderer.renderJeiPopup`（createRecipeLayoutDrawable 缩放渲染 + 缓存 + 20Hz tick）、PinnedRecipeManager.isPinnedUid/toggleFavouriteUid
- **资源**：内嵌 `assets/jei`（官方 GUI 贴图/图集/99 文件 820K）——弹窗渲染完整 JEI 界面（铁砧背景/槽位/箭头/火焰）
- **构建**：移除 fabric/neoforge 真实 JEI compileOnly（19.27 jar 的内部类与官方源码 fork 不一致——IPlatformScreenHelper.getBookArea(RecipeUpdateListener) vs (RecipeBookComponent)、IPlatformHelper 无 getBrewingHelper/getWorldHelper、Internal 无 setClientSyncedRecipes 等；混编错配）；fabric IconButton stub（mixin 编译用，真实 JEI 运行时遮蔽）

**API 差异备忘（官方 1.21.1 源码 vs 真实 JEI 19.27 jar）**：官方仓库源码树比发布 jar 新（内部 common 接口已演进）——"源码即 fork 唯一真源"，与真实 JEI 共存靠类加载遮蔽（jei < zzzbrbe），不要求内部类一致。

**已验证**：三模块编译通过、双端 build 通过、jar 含服务文件/平台类/引擎类/资产（md5 双端一致部署）。**待用户实测**：无 JEI 实例启动（1.21.1-Fabric/NeoForge）→ JOIN 后日志 `[BRBE-JEI-Plugins] embedded JEI core started` / `indexed N JEI types` → U 查询铁砧/研磨石显示配方条目 + Shift 弹窗完整 JEI 界面。

**已知边界**：brewing 类别保持 PotionLoader 网格（已有数据源）；info 类别未接（需 Gui 模块文本渲染）；真实 JEI 共存场景未实测（理论上被遮蔽，风险低）。

## 2026-08-28：无头 JEI 独立化（分支 headless-jei，1.21.1 核心分支移除内嵌完成）

**背景**：用户决策——无头 JEI 作为独立项目维护（`headless-jei/{GAME_VERSION}`，主仓库
`1.21.1/.git` 内建分支 `headless-jei` + worktree；各版本工程架构对应核心分支）；独立项目
建立后**核心分支移除内嵌，改依赖独立产物**（"一趟搞定"）。

**独立项目（分支 headless-jei，已推送）**：
- `1.21.1/`（Architectury 三模块）+ `1.21.11/`（fabric-loom-remap 单模块）+ `26.2/`
  （fabric-loom no-remap 单模块）：mezz fork（605/854/841 文件）+ 无头核心/收集 +
  **轻量桥** `JeiRecipeRegistry`（typeUid→条目值对象）+ `JeiPopupRenderer`（完整 JEI UI
  渲染）+ assets/jei + mod 清单（id `headlessjei`）+ AW/AT/ServiceLoader
- **关键修复（1.21.1 冷配置缺陷根治）**：architectury-loom 全新工程下
  `dependencies { neoForge ... }` 不注册 → neoforge 模块加 `loom { neoForge { } }` +
  按项目 `gradle.properties` 设 `loom.platform=neoforge/fabric`——三模块冷编译通过，
  双端 jar 构建成功（headless-jei-{fabric,neoforge}-1.21.1-1.0.0.jar）
- 1.21.11/26.2 独立工程 compileJava/build 通过（jar 已产出）

**1.21.1 核心分支移除（提交 c8630a8f，已推送）**：
- 删除内嵌（766 文件）：mezz.jei.* fork、com.alonie.brbe.jei.*、assets/jei、fabric/neoforge
  平台实现与接线、JEI 服务文件；AW/AT 恢复最小（PotionBrewing$Mix）；fabric IconButton
  编译 stub 保留（mixin 用）
- **反射桥**（headless-jei 产物为 intermediary 映射，不可编译依赖——同 JeiHudHider 模式）：
  `cache/BrbeJeiBridge`（拉 JeiRecipeRegistry → RecipeViewerEngine.registerJeiType，
  absent 静默降级）；`PopupRenderer.renderJeiPopup` 反射委托 JeiPopupRenderer
- 接线：fabric JOIN / neoforge LevelEvent.Load + RecipesUpdatedEvent → BrbeJeiBridge.refresh()
- 构建：fabric/neoforge 恢复真实 JEI 19.27 jar `modCompileOnly`（compat 插件 API 编译参考；
  loom 自动 remap 到 mojang 映射）；运行时**双装** headless-jei mod（无 JEI 场景），
  真实 JEI 场景照常遮蔽（jei < zzzbrbe）
- jar 体积：fabric 1.89MB→842KB、neoforge 2.06MB→1.02MB
- 部署：1.21.1-Fabric/NeoForge 实例 BRBE + headless-jei 均已更新（md5 一致，备份 20260828-022610）

**测试要点**：实例启动后 JOIN → 日志 `[BRBE-JEI-BRIDGE] imported N JEI entries`；
U 查询铁砧/研磨石 → anvil/grindstone 配方条目 + Shift 弹窗完整 JEI 界面；
不装 headless-jei 时 BRBE 正常降级（信息页）。

**下一步（未完成）**：1.21.11/26.2 核心分支移除（BRBE 侧 display 适配链
[SyntheticRecipeRendererImpl/SyntheticRecipeDisplayEntryFactory/PluginRecipeViewerCategory/
InfoRecipeCategory/RecipeViewerOverlay/BrbeJeiMinecraftMixin] 需改读 registry（反射）+
真实 JEI 27.4 jar modCompileOnly——本轮未动，分支保持可用）。

## 2026-08-28：modid zzzbrbe → brbe 全链回退

**背景**：用户决策——维护分支（1.21.1/1.21.11/26.2）modid 全部改回 `brbe`，资源包/lang/配置名/日志/pin 文件等引用同步。三分支同步落地。

**1.21.1 已落地（提交 6112abdd）**：
- fabric.mod.json `id` → `brbe`；neoforge.mods.toml `modId` + `[[dependencies.brbe]]` 段 + `logoFile` → assets/brbe/icon.png
- assets/zzzbrbe → assets/brbe（common/fabric/neoforge 三处）+ resourcepacks/zzzbrbe_unique_dark → brbe_unique_dark
- lang 键 `zzzbrbe.*` → `brbe.*` 全链（7 语言）；`MOD_ID` 常量；`@Config(name="brbe")`（brbe.toml 恢复）
- pin 持久化：brbe.pins / brbe.tabpins.json / brbe.pinoverlays.json（旧 zzzbrbe.* 文件不再读取——玩家 pin 数据迁移需手动改名，仅影响旧数据）
- 诊断日志 brbe-diagnostic.log；`brbe.debug` 属性；按键分类 category.brbe；BRBHelper.createBook("brbe", ...)；recipeviewer 类别 id；ResourceLocation namespace `brbe`
- 内置资源包注册名：`brbe:brbe_unique_dark`（fabric）/ `brbe:resourcepacks/brbe_unique_dark`（neoforge）
- 部署：1.21.1-Fabric/1.21.1-NeoForge 实例已更新（备份 20260828-131604，md5 一致）

**注意**：CLAUDE.md 历史轮次中的 `zzzbrbe` 为当时事实描述，保持原样不改写。

## 2026-08-28：真实 JEI 共存入口修复 + 部署规则升级（移植自 26.2，已部署双端）

- **嵌套 id `headlessjei`→`zheadlessjei`**（fabric.mod.json/neoforge.mods.toml modId+
  依赖段；Fabric/NeoForge 按 id 字母序 classpath，h<j 曾致无头 mezz 类遮蔽真实 JEI）。
- **真实 JEI 入口守卫**：fabric 入口 real 分支注册 END_CLIENT_TICK → 一次性
  `collectAndInject()`（数据搬运），跳过图集监听器注册；neoforge 入口图集注册加
  `BrbeJeiPlatform.realJeiLoaded()` 守卫（RecipesUpdated/LevelEvent 的收集照常；
  `BrbeJeiHeadlessCore.start()` 本就有 real 守卫）。
- **不适用**：烧炼 mod 工作站修复/去重（1.21.1 无外部工作站注册体系——
  主侧无 BUILTIN_WORKSTATIONS/registerExternalWorkstations，已知降级保持）。
- **部署规则**：删"运行中禁部署"，改**原子替换**（cp → mods/.brbe-deploy.tmp + mv rename）。
- 部署：备份 20260828-213500；fabric md5 48b4d652、neoforge md5 798fc2d5（原子替换）。

## 2026-08-28（晚）：1.21.1 配置界面三个缺陷修复（已部署双端）

用户实测（NeoForge）报三问题：①配置界面出现错误加载的查询浮层；②快捷键显示原始
键名（key.keyboard.a/r/u）；③快捷键配置项变成文本框。
- 根因①：viewer 是全局静态状态，`ScreenRenderMixin` 挂在**所有 Screen** 的 render TAIL；
  打开后切到配置界面（Cloth 屏）时 active 仍为 true → 无条件绘制泄漏。修复：viewer 记录
  `hostScreen`（open 时设置、close 清空），render/renderTooltip 在
  `Minecraft.getInstance().screen != hostScreen` 时自动 close 并跳过（配置屏等非容器屏
  不再绘制）。
- 根因②③：`KeybindingGuiRegistrar.register()` 只在 fabric 入口调用，**neoforge 入口缺失**
  → Cloth 配置界面把 String 字段当普通文本框（raw 值原样显示）。修复：neoforge
  `BetterRecipeBookClientNeoForge` 补注册（已验证 Cloth 15.0.140 的 KeyCodeEntry 渲染走
  `getLocalizedName()`——注册后键名自动翻译、控件变按键捕获）。
- 部署：备份 20260828-225500（原子替换）；neoforge 9da2c5c6、fabric 5b4e5e64。

## 2026-08-28（晚二）：1.21.1 四缺陷修复（查询打不开/翻页动画/右键清搜索聚焦/文案缺失，已部署双端）

用户实测（NeoForge）报四问题：①R/U 查询系统完全打不开；②翻页动画损坏；③右键清
理搜索栏后无法取消聚焦；④文案缺失（要求直接复制高版本资源包）。

**①R/U 查询打不开——两层根因**：
- **搜索框聚焦吞键**（主因）：vanilla `RecipeBookComponent.keyPressed` 有「聚焦搜索框
  且可见 → 吞噬所有按键」分支；右键清除后 mixin 遗留 `setFocused(true)`（见③）→
  聚焦无法取消 → R/U 永远到不了 `AbstractContainerScreen.keyPressed` 的 viewer 分支。
- **NeoForge 漏注册 R/U 键位**：`RegisterKeyMappingsEvent` 只注册 PIN/DIAGNOSTIC
  （fabric 侧 48dc588b 起注册全 4 个，neoforge 移植时遗漏）——1.21.1 的
  `KeyMapping.matches()` 虽不查 isDown（纯键码比较），未注册仍导致控制界面无 R/U
  条目、无法重绑。修复：neoforge 入口注册 R/U（与 fabric 对称）。
- 修复③后聚焦可取消 → R/U 恢复正常路径。（1.21.11 的 RecipeBookComponent 有同样的
  聚焦吞键语义——保持版本一致，不改。）

**②翻页动画损坏——根因两处**（对照 1.21.11）：
- **用户翻页未标记**：`scrollablepages/RecipeBookPageMixin` 的箭头点击（mouseClickedBtn）
  与滚轮（render HEAD）都未调用 `RecipeBookPageAnimBridge.markUserFlip()` ——动画 mixin
  在 `updateButtonsForPage` HEAD 消费不到标记 → 永远走「直接切换」分支，动画从不启动。
- **结束不归位**：旧动画 mixin 直接 `setPosition` 平移真实按钮，收尾（SNAP）时只清
  `animActive` 不恢复基准位置 → 翻页完成后按钮永久停在末帧偏移（≈-125px，网格外），
  页面内容消失/错乱；且 render 参数误读（第 3/4 参是 mouseX/mouseY，被当作
  areaWidth/areaHeight 传入 scissor）。
- 修复：动画 mixin **重写为 1.21.11 式 @Redirect 模型**——动画期间完全不移动真实按钮，
  用双快照池（`brbe$snapshotButtons`/`In`）在视觉位置渲染「滑出页 + 滑入页」，
  scissor 只包网格区（areaLeft+11..136 × areaTop+31..131），tooltip 跟随光标命中的
  快照按钮（render RETURN 覆盖 hoveredButton）；捕捉追逐/旅行目标/压缩追逐逻辑与
  1.21.11 一致（版本降级仅保留简单平移视效，无挤压）。箭头点击与滚轮路径补
  `markUserFlip()`。

**③右键清搜索聚焦**：`search/RecipeBookComponentMixin` 右键清空后 `setFocused(true)`
→ 改 `setFocused(false)`（1.21.11 语义：清空即取消聚焦）。

**④文案缺失**：1.21.1 lang 与 1.21.11 diff——缺 13 键（compost.chance/info/cooktime.*/
tooltip.station/key.category.brbe.category/soundCategory.brbe_page_flip/scrollAround*）；
zh_cn/zh_tw 大量类别键仍是英文占位（暂 en 值）；tip.2 仍写 F 键（应为 A）；tip.8/9
文案过时。修复：直接合并 1.21.11 的 7 语言 lang（值以 1.21.11 为准），保留 1.21.1
独有键（brbe.viewer.recipe/usage/materials——1.21.1 查询浮层标题用）。

**部署**：备份 20260828-XXXX（原子替换）；neoforge a657aeba、fabric 372d4f70。
**验证**：neoforge runClient 启动无 mixin 报错；用户实测 R/U 打开 + 翻页动画 + 右键清空
取消聚焦 + 配置界面/查询浮层中文文案。

## 2026-08-28（晚三）：崩溃修复（叙述越界）+ 动画挤压视效 + R/U 诊断插桩（已部署双端）

用户实测（NeoForge，崩溃信息 zip）报：①游戏崩溃（Narrating screen）；②动画不完整
（细节）；③R/U 查询仍无法唤出。

**①崩溃根因**：`IndexOutOfBoundsException: Index 5 out of bounds for length 1` at
`RecipeButton.updateWidgetNarration`——`currentIndex` 只在 `renderWidget` 里重算
（`floor(time/30) % size`），而叙述（`handleDelayedNarration` → `updateNarration`）
在**渲染之前**执行；集合换页（pin/搜索/翻页，`updateButtonsForPage → button.init`）
后 stale 索引碰上缩小为 1 的列表 → 越界崩溃。实测时间线：22:27:38.869 pin-extract
（A 键 pin 触发 rebuild）→ 22:27:39.763 崩溃。修复：`incompletecrafting/RecipeButtonMixin`
新增 `init` RETURN 注入 `brbe$refreshIndexAfterInit`——init 是唯一集合交换点，直接
归零 currentIndex，下一帧 renderWidget 按新列表重算。
**新 accessor**：`accessors/RecipeButtonAccessor`（getOrderedRecipes @Invoker +
time/currentIndex @Accessor），已注册 mixins.brbe-common.json。

**②动画细节补全**：快照渲染改为与 1.21.11 一致的**边缘挤压视效**——配方滑出视窗
边界时内容裁剪在 [effX, edgeRight) 内（宽度随滑动收窄）、左右边界 2px 独立渲染
（边框不缩放，PageAnimationEdges 读 edge_width.json）、残缺配方红罩
（PartialCraftingUtil.isPartiallyCraftable）、已 pin 配方图标网格 scissor 外补画
（isFullyPinned + RECIPE_BOOK_PIN_SPRITE）、图标轮循推进（time += f 后
currentIndex 重算，renderItem/renderFakeItem 复刻 renderWidget 偏移布局）。
槽位 sprite id 按 javap 核对直接构造（recipe_book/slot_{many_,}craftable/uncraftable）。

**③R/U 诊断插桩**（[BRBE-VIEWER-DIAG] INFO 行，待用户实测后移除）：
- hideoverlay.AbstractContainerScreenMixin：R/U 键到达 mixin 即打日志（键码/槽位）
- RecipeViewerOverlay.keyPressed/new open()：defaultFor=null / 空内容 / opened 结果

**部署**：备份 20260828-224x（原子替换）；neoforge 875b1d1a、fabric 6d4af704。
**待用户实测**：无崩溃（pin/翻页/搜索后叙述安全）+ 动画挤压视效 + 复现 R/U 后收集
[BRBE-VIEWER-DIAG] 日志定位根因。

## 2026-08-28（晚四）：查询浮层层级修复（根因！）+ 翻页音效接线 + 动画残缺标记对齐（已部署双端）

用户继续实测：①动画细节仍损坏；②查询系统"无法使用"（建议重做）；③翻页音效损坏。
**关键证据**（实例日志 [BRBE-VIEWER-DIAG] 插桩行，22:54-22:55 会话）：
- R 键**到达** keyPressed mixin ✓（key=82 scan=27 rvEnabled=true）
- 悬停工作台按 R → `opened=true active=true` ——**查询浮层确实打开了**！
- 结论：按键/数据层正常，问题是**浮层渲染层级**——ScreenRenderMixin 挂在
  `Screen.render` TAIL，而容器屏幕（CraftingScreen/InventoryScreen）在
  `super.render()`（含 Screen TAIL）**之后**才绘制槽位/配方书 → 浮层被下层内容
  完全盖住 → 用户看不见面板 → "无法使用"。（此前配置屏泄漏也是同一注入点，
  方向相反的另一半问题。）

**本次重做（参考 1.21.11 架构）**：
- 查询浮层渲染从 `Screen.render` TAIL **移到平台 after-render 钩子**（整屏渲染完成
  后、最顶层）：fabric `ScreenEvents.afterRender(screen)`（AFTER_INIT 内逐屏注册）；
  neoforge `ScreenEvent.Render.Post`（Init.Post 内逐屏注册，复用既有模式）。
  新入口 `TopLayerOverlayRenderer.renderViewer`（render + renderTooltip）；
  ScreenRenderMixin 只留 TopLayerOverlayRenderer（顶层层原样）。
- **翻页音效**：`ClientCompat.playPageFlipSound` 在 1.21.1 移植后**从未被调用**
  （有定义无调用者——滚轮翻页静音根因）。scrollablepages/RecipeBookPageMixin 滚轮
  处理补调用（仅实际翻页时播放；scrollPageSound/pageFlipVolume 由 helper 统一门控；
  箭头点击走 vanilla AbstractWidget.playDownSound 原声）。
- 动画残缺标记对齐静态路径：挤压分支的残缺红罩从纯 fill 改为
  `BRBTextures.hasPartialSprite()` 时补画 partial sprite（宽随挤压收窄）。

**部署**：备份 20260828-23xx（原子替换）；neoforge de585a82、fabric 52ce9e65。
**待用户实测**：R/U 打开面板应**完整可见**（背包/配方书之上）；滚轮翻页有音效；
翻页动画挤压/残缺标记与静态一致。诊断日志保留（定位后可移除）。

## 2026-08-28（晚五）：动画图标裁边 + 翻页音效修正 + 查询浮层视觉重做（已部署双端）

用户实测（附截图：两套配方书纹理叠放 + 红框格子 + 标签溢出）报：
①翻页时物品图标应被单元格边界盖住；②音效源用错（应为按钮点击声）；③查询界面混乱。

**①动画图标裁边**：挤压分支的 `brbe$renderItemIcon` 原本在内容 scissor **外**渲染
（图标随按钮滑出越过边框可见）。修复：移入内容 scissor **内**（[effX, edgeRight-1)
裁剪），图标被单元格边界裁住，边框条随后渲染覆盖图标边缘——1.21.11 语义。

**②翻页音效音源**：`ClientCompat.playPageFlipSound` 误用 2 参
`SimpleSoundInstance.forUI(sound, p)`——第 2 参是 **pitch** 不是音量（默认音量
0.25 → pitch 0.25 低频闷响，听起来像"音效源错了"）。修复：3 参
`forUI(UI_BUTTON_CLICK.value(), 1.0f, volume)`（pitch=1.0 = 原版按钮点击声）。

**③查询浮层视觉重做**（对照 1.21.11 底层差异）：
- 面板背景：原版 recipe_book **背景纹理**（误当书页）→ **`recipe_book/overlay_recipe`
  9-slice 框体**（1.21.11 查询框同款背景，无 mcmeta 变化，纯 vanilla sprite）
- 定位：居中对齐（压在背包/配方书正中）→ **锚定光标左上**（box 左侧、底高于
  光标 16px，太靠边界时翻转/钳制）——不再与配方书/背包界面叠成一片
- 标题行：查询对象图标 + 「查询配方/用途: xxx」+ 页码 + "< >" 翻页箭头（面板内）
- 网格：6×4(24/页) → **8×4 = 32/页**，25px 格子，间距 0；grid 类别（燃料等）同布局
- 分类 tab：从面板底部 18px 溢出（TAB_BAR_Y=154 > PANEL_H=148 的旧缺陷）→
  **面板内底部 26px/**个 tab 行（240px 面板 = 9 类别 # 均放下）；grid 类别也补画 tab
- 面板尺寸 176→**240x162**（容纳 8 列 + tab 行）；pinoverlay 弹窗/Shift 预览随新几何

**部署**：备份 20260828-23xx（原子替换）；neoforge 3e91c5a5、fabric 43f19016。
**待用户实测**：翻页图标被格子裁边；滚轮翻页 = 原版按钮点击声；查询浮层 = 框体面板
锚定光标、标题/页码/tab 均在面板内；R/U/A/Shift/ESC 交互正常。

## 2026-08-29：查询浮层按 1.21.11 结构重写（vanilla overlay 网格 + rbip 标签条）

用户指示："对照高版本和变更日志重写，而不是先整体复制然后小修小改"。附三图对比：
1.21.1 旧浮层（overlay_recipe 灰框 + 红框自制格子）vs 1.21.11/26.2 参考（overlay_recipe
大框 + **vanilla alternative-overlay 格子** + rbip bottom_tab 标签条）。
**重写要点（RecipeViewerOverlay 整文件重写）**：
- 网格 = vanilla `OverlayRecipeComponent`（一页一个 `RecipeCollection`：条目 holder
  列表 → `updateKnownRecipes`），其 recipe 按钮（crafting/furnace overlay 纹理格子）
  重排到 10 列——与 1.21.11 参考图同款组件/纹理（色调随 1.21.1 原版纹理）
- 框体 = `recipe_book/overlay_recipe` 9-slice（258x133 = 10x5 格 + 8 padding）
- 分类标签 = `brbe:textures/rbip/bottom_tab(.selected).png`（RBIP 同源贴图，35x27
  中取 24x22；先画背层再画选中层——框体盖标签顶边，1.21.11 同款层次）
- 标题行移到框体上方（框内会盖住首行格子）；翻页 < > 在框上方左侧 + 页码右上
- pin 标记（drawPinMarkers 按按钮索引对应条目）+ Shift 预览（PopupRenderer）保留
- 锚点（打开时光标快照）固定在左上展开——上一轮已修"面板随光标游走"

**动画**：图标移回内容 scissor 外 + 边框条后画（与 1.21.11 renderVisualSquashed
逐行一致——上轮"图标入 scissor"是偏离参考的，已回退为参考顺序）。

**待用户实测**（5s 慢动画仍在 brbe.toml，测完恢复 0.5）：查询浮层 = 参考图同款
组件 + 纹理；若动画仍有细节问题请录屏（截图无法体现运动 z 序）。

## 2026-08-29（二）：动画与查询浮层系统性重做（反编译 ground truth 对齐 1.21.11）

用户指示："直接系统性地一点点地照着 1.21.11 和变更详情（最好是直接反编译两边的
本体 jar 包）来重新设计功能模块，这次要更加严谨地处理。"

**方法**：三路并进——①CFR 反编译两端已部署 jar（62+42 类，产物在
`1.21.11/build/decomp/`，报告 DECOMP_REPORT.md）证实**源码与 jar 完全一致**
（1.21.11 常量表=移植准绳）；②深读 1.21.11 的 viewer（3532 行）+ 10 个
recipeviewer mixin；③盘点 1.21.1 现状（accessor/接线/配置全清单）。

**动画修复（此前轮次记录与代码不符，已按 1.21.11 逐行核对）**：
- ⚠️ 上轮"图标移回 scissor 外"记录失实——f1fb9368 只改了 viewer 文件，动画
  mixin 的图标仍在内容 scissor 内（"晚五"状态）。本次真正回退为 1.21.11 顺序：
  内容 scissor → disableScissor → **图标（边界线前渲染，边界线盖住经过的图标）**
  → 边框条
- 网格 scissor 高度 100→**125**（底 = areaTop+156，与 1.21.11 逐字一致；此前 131）
- 滚轮翻页注入点 HEAD→**RETURN**（1.21.11 语义：本帧先画旧页，下帧起动画）
- 配方书翻页锁定：查询浮层打开时吞 queuedScroll、箭头点击 cancel+false、
  Ctrl 跳页守卫（1.21.11 同款语义，此前 viewer 打开时下层书仍可翻页）
- updateArrowButtons 补 active=true（1.21.11 有，此前漏）
- playPageFlipSound 补 10ms 节流（1.21.11 滚轮路径同款，此前快速滚动叠音）

**查询浮层按 1.21.11 结构整文件重写（651→~1250 行）**，补齐此前全部缺失件：
- 分类标签条：-90° 旋转 + TAB_CUT=6 横向拼贴 + TAB_V_CUT 纵向切除（35x27 贴图，
  与 1.21.11 常量逐字一致），未选标签垫高 2px 被框体盖顶边、选中标签首层重绘、
  25px 列距对齐 icon、燃料类补火焰角标、标签 tooltip（类别名+模组名）、
  **标签滚轮切换 + REI 式滑动窗口**、点击已选标签=浏览全部切换、空类别标签隐藏
- 翻页按钮：RBIP recipe_book_buttons.png 贴图（14x13，悬停 u+28/禁用 v=13），
  框上方，Ctrl 跳页/scrollAround 绕回/页码 tooltip（1.21.11 同款；旧版是文本
  "< >" 悬浮框外）
- **左侧工作站列**（此前完全没有）：框左外挂 25px 列，column_panel 9-slice
  （贴图+mcmeta 从 1.21.11 复制），plain_overlay 24px 格自底向上、滚轮窗口滑动、
  点击重新查询该工作站；数据源 = RecipeViewerIndex.stationColumnItemsFor
  （1.21.11 Family 注册表降级为静态清单）
- 纯信息网格：slot_uncraftable 红框（用户曾批"红框格子"）→ **plain_overlay/
  plain_overlay_highlighted**（1.21.11 同款贴图与悬停高亮）
- **标题行删除**（1.21.11 无框上标题——锚定光标即语境）
- **模态交互闭环**（此前 mouseClicked/mouseScrolled 完全未接线，点击/滚轮全穿透）：
  hideoverlay mixin 新增 mouseClicked/mouseScrolled/renderTooltip 三注入——
  框内吞点击（配方格给按钮音）、框外关闭、弹窗硬模态、滚轮翻页/切标签/滑列、
  viewer 打开时抑制容器槽位+配方书按钮 tooltip（RecipeBookPageMixin 补
  renderTooltip cancel，1.21.11 RecipeBookPageTooltipMixin 语义）
- 悬停配方按钮 2x 放大重绘（vanilla 替代配方网格观感；1.21.1 原版组件无此行为）
- JEI 条目（anvil/grindstone，无 RecipeHolder）以 plain_overlay 格补画在网格位
  （修复：此前 JEI 条目进不了 overlay 集合 = 完全不可见）
- Shift 预览弹窗恢复（左/右 Shift 悬停 → PopupRenderer 2x；弹窗内保持打开、
  硬模态吞点击滚轮）；pin 标记/固定即预览保留；Ctrl+O 浏览全部（allEntries/
  allGridItems 通道）；A 键 pin 后重排置顶
- 排序 = pin → 可合成 → 残缺 → 不可合成（recipeRank，1.21.11 同款）；viewer
  集合残缺标记用 markPartialMaterials(集合, 玩家背包 compartments Set<Item>)
  （1.21.11 prepareForViewer 的 1.21.1 等价物）
- 熔炼 tooltip 补 XP + 分站耗时行（AbstractCookingRecipe.getExperience/
  getCookingTime）；燃料三行烧炼量；堆肥概率（CompostRecipeCategory.chanceOf）
- [BRBE-VIEWER-DIAG] 诊断日志移除（根因已定位修复）
- accessor：AbstractContainerScreenAccessor 补 getTopPos（getGuiLeft/getGuiTop
  是 NeoForge 补丁方法，common 编译不可见——javap 对比三档 merged jar 证实）

**反编译地面真值核对结论**（DECOMP_REPORT.md）：两端源码=部署 jar；1.21.11 常量
（PAGE_COLS=10/PAGE_ROWS=5/TAB 全套/STATION 24-25-25/RBIP 按钮 14x13/scissor
11,31,136,156/红罩 0x60FF3333）全部逐字落进 1.21.1。

**部署**：备份 20260829-0203xx（原子替换）；neoforge 9e39c4ed、fabric fee30529
（md5 双端一致）。

**1.21.1 已知降级（相对 1.21.11，本轮回合未动）**：PinOverlay 独立浮层（固定即
预览替代）、RecipePopupLayer/Preview 内嵌 tooltip（轻量 PopupRenderer 文本
tooltip 替代）、幽灵放置（配方格点击仅吞+音）、tooltip 样式/光标手势、工作站
注册表（Family/brbe_workstations.json）、info 类别、recipeviewer mixin 包其余
（isCraftable bypass/tryPlaceRecipe 链）。

**待用户实测**（5s 慢动画仍在 brbe.toml，测完恢复 0.5）：①查询浮层 = 参考图同款
结构（旋转标签条+工作站列+贴图翻页按钮）；②点击/滚轮/ESC/标签切换全链路；
③翻页动画图标在边框条之下。动画细节问题请录屏。

**2026-08-29（二·续）自查修复两处（已重建部署，neoforge 20c22f1e / fabric 96c76b37）**：
- 弹窗状态残留：popupOpen 原先只在配方模式的 Shift 分支清零——上一帧弹窗开着时
  切到 grid 类别（燃料/堆肥/酿造）会永久吞点击。修复：render 开头无条件复位
  popupOpen（上一帧值保存为 wasPopupOpen 供"光标在弹窗内保持打开"判定）。
- tooltip 材料行错位：材料行被错误嵌套在"熔炼配方"分支内（合成/切石/锻造条目只有
  名字行）；JEI 条目也无材料行。修复：材料行对所有条目生效（holder 走
  inputsOf、JEI 走 jei.inputs()）。
- 复核：OverlayRecipeComponent.init 每次 clear()+add 重建按钮列表（javap 字节码
  306/433 偏移）——showPage 的按钮↔条目重排映射安全。

**2026-08-29（二·续二）引擎注册时机修复（已重建部署，neoforge 3c5263ab / fabric 006cd7af）**：
- 旧会话日志（01:27:54）证据：`U key=85 item=工作台 opened=false`——R/U 查询在
  进游戏后、配方书组件首次 setupCollections 之前打不开（rebuildEngine 唯一触发点
  是配方书 mixin，引擎空 → defaultFor 无内容 → 拒绝打开）。
- 修复：`openFor` 开头按需重建——`flushEngineRebuildIfDirty()` + 四类 vanilla 类型
  全空时直接 `rebuildEngine()`（查询前兜底，一次/会话，正常路径零开销）。
- `CraftingRecipeCategory` 补 `appliesToStation`（工作台/合成器——1.21.11 语义，
  此前缺省默认 false，usage 站循环跳过了合成类别）。
- 诊断日志收窄为打开成败各一行（[BRBE-VIEWER] opened/refused，含类别/条目/页数），
  便于下一轮实测定位。

**2026-08-29（二·续三）实际内容判定 + 旧日志 U 查询之谜调查（已重建部署）**：
- 旧会话日志（01:27:34 连按 3 次）`U 工作台 opened=false`，反编译旧 jar 全链路
  （open/keyPressed/defaultFor/bestByPriority/FuelRecipeCategory/RecipeViewerIndex/
  引擎 usagesFor）逐一排除：旧代码静态推不出 cat=fuel 路径；JEI 桥在旧会话
  **零导入**（日志无 BRBE-JEI 行——实例 mods 目录也无 headless-jei jar，两实例
  均未部署 headless-jei，anvil/grindstone 降级信息页为预期）。
- 关键 API 发现（NeoForge 21.1.x，javap 21.1.248 实证）：`AbstractFurnaceBlockEntity
  .isFuel(stack)` = `stack.getBurnTime(null) > 0`，`Item.getBurnTime` 默认 = 
  **数据映射 `neoforge:furnace_fuels` 查表**（无条目→0）——与本 mod 无关，但影响
  isFuelItem 判定；实例中 aether/create/FarmersDelight 均带该数据映射（无
  crafting_table 条目）。
- 防御性修复：openFor/最佳类别重选改用**实际内容判定**（`hasActualContent`：
  grid 看 gridSource 非空、配方看 categoryHits 非空），不信任 hasContent 声称——
  "声称有内容实际为空"的类别（旧故障形态，无论根因）无法再劫持默认或导致拒绝；
  空内容一律回退实际有内容的最高优先级类别，再空才 refuse（带原因日志）。
- 附带修复：queryTarget/queryUsage 在内容判定前落字段（hasActualContent 走字段）。

**2026-08-29（二·续四）全文件通读复查两处修复（已重建部署）**：
- **bottomAnchor 漏初始化（关键布局 bug）**：openFor 未初始化 bottomAnchor（1.21.11
  在 openFor 设 `bottomAnchor = anchorY + 16`），首个 fitBoxToPage 的 clampBoxToAnchor
  用旧值——首开框体被钳死在 Y=25、重开用旧会话锚点。修复：openFor 锚点区补初始化。
- **grid 类别退出浏览全部框体塌缩**：toggleBrowseAll 退出分支无条件 page 恢复+showPage；
  grid 类别 entries 恒空（rebuildGrid 不填）→ fitBoxToPage(0) 框体塌缩成 8px。
  修复：页面恢复仅配方类别执行（1.21.11 refreshCurrentCategory 同款分支结构）。

**2026-08-29（二·续五）启动崩溃修复（用户实测报 12:07 启动失败，已重建部署）**：
- 崩溃根因：`hideoverlay.AbstractContainerScreenMixin.brbe$viewerMouseScrolled` 的
  `@Inject(method="mouseScrolled")` 目标不存在——**1.21.1 无 Screen/AbstractContainerScreen
  的 mouseScrolled 分发链**（1.21.2+ 才有 4 参版；javap 21.1.248 运行时 client jar 实锤
  两类均无该方法），滚轮只有 MouseHandler.onScroll 一条路。启动时 mixin 校验失败 →
  InvalidInjectionException → 游戏直接崩溃。此前 javap 核对时把 patched jar 的
  MouseHandler 调用点误当成 Screen 方法（教训：**注入点签名必须以运行时 jar 的
  javap 为准，不能从调用点反推**）。
- 修复：滚轮路由移入既有 `MouseScrollHandler.onScroll`（HEAD，cancellable）——
  viewer 激活时换算 GUI 缩放坐标后交给 `RecipeViewerOverlay.mouseScrolled`，消费则
  cancel 吞掉原版滚轮处理；hideoverlay mixin 移除失效注入。
- 连带修复（GUI 缩放坐标 bug）：`mouseXFor/mouseYFor` 原来直接取原始窗口坐标
  （xpos），guiScale>1（如 854x480 窗口）时 viewer 锚点/按键命中判定全部错位。
  改为 vanilla 同款换算 `xpos * guiScaledWidth / screenWidth`（1.21.1 无
  getScaledXPos）。

**2026-08-29（二·续六）动画图标裁边回退修复（用户实测报"图标盖在边界之上"，已重建部署）**：
- 用户实测（12:13 会话，启动正常 0 mixin 失败、R 查询 crafting 打开成功）报：动画
  翻页时物品图标仍盖在边界之上。26.2/1.21.11 源码确实把图标画在内容 scissor 外
  （三版一致），但用户验收标准自"晚五"以来始终明确：**图标必须被单元格边界裁住**。
  此前"回退到 1.21.11 顺序"的记录与实际需求相悖——本次按用户要求改回：
  挤压分支图标移入内容 scissor 内渲染（[effX, edgeRight-1) 裁剪，不越过格子边界），
  边框条随后渲染覆盖边缘。**注意**：此行为与 26.2/1.21.11 源码字面顺序不同，是
  用户明确验收标准，勿再按源码回退。
- 查询界面"前端表现非常糟糕"待用户提供截图定位（日志证实功能正常：opened
  cat=crafting entries=2）。

**2026-08-29（二·续七）动画图标压单元格边界：钳位修复落地（已重建部署双端）**：
- 用户确认问题集中在"**图标直接压单元格边界**"（单个 slot 的 2px 边框线，非整网格边缘）。
- **系统性根因（用户问"为何 1.21.11/26.2 容易、1.21.1 麻烦"的答案）**：
  - **1.21.11 / 26.x 用新版 GuiGraphics item 渲染**：`renderItem` 提交
    `ItemStackRenderState` 到 `GuiItemRenderState`，并在提交时用
    `this.scissorStack.peek()` **捕获当帧生效的 scissor** 作为软件裁剪叠加到 item ——
    移动中的图标无论何时绘制都被裁到提交时的 scissor，干净被格子边界裁住。
  - **1.21.1 用旧版同步 item 渲染**：`renderItem` 直接
    `getItemRenderer().render(..., this.bufferSource(), ...)` 后立刻 `this.flush()`
    （`disableDepthTest → bufferSource.endBatch() → enableDepthTest`），几何即时画在**当前
    GL scissor** 下——**没有 scissorStack 捕获**，对移动中的快照按钮图标不可靠，图标逃逸
    裁剪、压在格子边界线上。
- **修复（挤压分支 `brbe$renderVisualSquashed`）**：手动把图标钳制在内容区
  `[effX, edgeRight-1]` 内——`if (effW >= 17) brbe$renderItemIcon(snap, gui,
  Mth.clamp(x, effX-4, edgeRight-21), y)`。图标只在格子内部显示、被随后绘制的边界条
  盖住边缘（被边界盖住），不再压边界线；内容区缩到装不下图标（effW < 17）时不再绘制。
- **历史结论（勿再反复）**：inside/outside 渲染顺序在 1.21.1 上像素等价，不是根因，
  不要再照着 1.21.11 的字面顺序来回改。此钳位是 1.21.1 特有的、绕开旧 item 渲染不裁
  scissor 的办法。已构建部署双端（备份 20260829-*，md5 一致），待用户实测确认。

**2026-08-29（二·续八）查询浮层在创造屏幕崩溃修复（已重建部署双端）**：
- 用户实测崩溃：按 R/U 打开查询浮层时 `ClassCastException: ItemPickerMenu cannot be cast
  to RecipeBookMenu` at `OverlayRecipeComponent.init`（stack：RecipeViewerOverlay.showPage →
  overlayComponent.init）。
- 根因：vanilla `OverlayRecipeComponent.init` 会把 `mc.player.containerMenu` 强转成
  `RecipeBookMenu`，而**创造模式**（CreativeModeInventoryScreen）的菜单是
  `ItemPickerMenu`（非 RecipeBookMenu）→ 强转失败崩溃。CraftingMenu / InventoryMenu /
  AbstractFurnaceMenu 均 extends RecipeBookMenu，唯独创造屏幕不是——所以只有创造屏会崩。
- 修复：`RecipeViewerOverlay.showPage` 先判 `mc.player.containerMenu instanceof
  RecipeBookMenu`——是配方书菜单才调用 `overlayComponent.init`（vanilla 配方按钮+可合成
  状态）；非配方书菜单（创造）不调用 init，按钮列表置空（pageButtons 全 null），render
  走 `w==null` 分支持 plain_overlay 格子+结果图标兜底（无崩溃、仍可浏览配方）。
- 位置：`util/RecipeViewerOverlay.java` showPage（+ import RecipeBookMenu）。已构建部署双端
  （备份 20260829-*，md5 一致），待用户实测。

**2026-08-29（二·续九）动画定为最简"完全平滑离场"（用户拍板，已重建部署双端）**：
- 用户反馈：图标"钳位+跳过"导致边缘物品整体消失；预想是"滑动离场、被格子边界遮住"。
  但 1.21.1 旧版 item 渲染逃逸 scissor，挤压/单元格边界/边框条这套复杂视觉反复做不好，
  用户决定：**动画改为最简单的"完全平滑离场"**——整页平移，按钮按滑动位置直接渲染
  （sprite+图标+残缺标记），由网格 scissor 裁住。
- 落地：`RecipeBookPageAnimationMixin.brbe$renderVisualSquashed` 整体简化——去掉边缘
  挤压/内容 scissor/左右边界条（PageAnimationEdges 不再使用，import 已删），只保留
  按 (x,y) 渲染 sprite + `brbe$renderItemIcon` + 残缺标记 + tooltip 命中 + pin 收集；
  网格 scissor 由 `brbe$renderButton` 统一开启，裁住滑动中的按钮。动画检测/追逐/旅行
  目标逻辑不变，只简化渲染。
- **历史教训（勿再反复）**：inside/outside 渲染顺序在 1.21.1 上像素等价、不是根因；
  item 逃逸 scissor 使"挤压+边界条"难做好。本分支动画=简单平移，与 1.21.11/26.2 的
  完整挤压视效不同，属已知降级（1.21.1 API 鸿沟）。已构建部署双端（备份 20260829-*，
  md5 一致），待用户实测确认"完全平滑离场"符合预期。


## 2026-08-29（三轮）：查询系统全量修复（对照 1.21.11 逐模块移植，已部署双端）

**背景**：用户指出 1.21.1 查询系统前端与 1.21.11/26.2 差异巨大，"不是一个两个错误"。
调查结论（docs/1.21.1-查询系统差异调查报告.md）：1.21.1 是轻量重写而非移植——缺 10 个
recipeviewer mixin、整个 pinoverlay 子系统、4 个支持类（RecipePopupLayer/
SyntheticRecipeRenderers/RecipePreviewTooltipComponent/PopupGeometry）、74 个浮层方法；
后端索引 195 vs 876 行（无 Workstation 注册表/known 集驱动/指纹节流）。

**已落地**（对应报告第六章）：
- **后端**：RecipeViewerIndex 重写（Workstation 注册表 + brbe_workstations.json +
  known 集驱动 + dirty/指纹节流 + viewer 集合/partial 快照）；Engine 扩展
  （isRecipeBookStation/setRecipeBookStationItems/registerRecipeBookType）；
  InfoRecipeCategory（反射 headless-jei JeiRuntimeBridge）；JeiEntry 加槽位布局
- **输入层**（1.21.1 签名逐项反编译核对）：10 个 recipeviewer mixin 全量移植 +
  2 个 accessor（RecipeBookAccessor 读 RecipeBook.known；GuiGraphicsAccessor——
  1.21.1 的 renderTooltipInternal 是 **private**）；hideoverlay 旧 mixin 收窄
- **Viewer**：placeRecipe（1.21.1 无 tryPlaceRecipe → 
  MultiPlayerGameMode.handlePlaceRecipe 直发 + ServerPlaceRecipeMixin 放行 contains
  ——注入点在 ServerPlaceRecipe.recipeClicked，不在 1.21.11 的 handlePlaceRecipe）、
  captureTarget 7 级、hide 过滤链（对象级/类别级/站连接切连/defaultFor）、
  fallbackToViewer、modalMaskOwnsCursor、viewerMode、PopupGeometry 1:1 +
  PopupRenderer 重写、富 tooltip（AbstractBrbeTooltipComponent + renderTooltipInternal）
- **pinoverlay**：PinOverlay/PinOverlayManager/PinButtonRenderOverride（z 序交错、
  拖动、点击放置、brbe.pinoverlays.json 持久化、ESC 只关 viewer）

**关键 API 差异备忘（本轮反编译核实）**：
- `ServerPlaceRecipe.recipeClicked` 里 `ServerRecipeBook.contains(RecipeHolder)`
  （1.21.11 是 handlePlaceRecipe 里 `contains(ResourceKey)`）——ServerPlaceRecipeMixin
- 1.21.1 的 `ClientTooltipComponent` 是 renderText(Font,int,int,Matrix4f,BufferSource)/
  renderImage(Font,int,int,GuiGraphics)（1.21.11 是 renderText(GuiGraphics,...)/
  renderImage(...,width,height,...)）——tooltip 组件按 1.21.1 签名实现
- 1.21.1 `GuiGraphics.renderTooltipInternal` private（1.21.11 public）——@Invoker 桥
- 1.21.1 `AbstractContainerScreen` **无 mouseScrolled**（在 Screen 上）——滚轮走
  既有 MouseScrollHandler（MouseHandler.onScroll）
- 1.21.1 `RecipeBeanBook.known` 是 Set<ResourceLocation>（1.21.11 是 Map<RecipeDisplayId,...>）
- 1.21.1 用 `StackedContents`（非 1.21.11 的 StackedItemContents）
- 1.21.1 的 InputConstants.isKeyDown 收 long（window.getWindow()）

**剩余缺口**（数据源/API 鸿沟，下一轮次）：Alt+滚轮变体轮循、tooltip 内嵌完整预览
（RecipePreviewTooltipComponent）、pin 克隆 OverlayRecipeButton（无 SlotSelectTime）、
JEI overlay 排除区。

**验证**：runClient mixin 全量应用（仅两个既有 JEI overlay 缺席警告——运行时无 JEI 正常）；
runClient 入口 crash 为既有 headless-jei JIJ 不入 dev classpath 问题（与本次无关）。
已部署双端（备份 20260829-164730 / 164947，md5 一致）。

## 2026-08-29（四轮）：查询浮层三实测缺陷修复（①被盖住/②类别缺失/③火焰z序，已部署双端）

用户实测（1.21.1 查询系统）报三问题（本轮回合修复，逐项反编译核实）：
①查询浮层被物品栏/配方书盖住（应最顶层）；②只有燃料/堆肥两个类别加载，其余全缺失；
③烧炼燃料类别图标：火焰应盖在熔炉图标之上。

**①②③ 共享一个根因线索：GUI 深度测试 z 序**。反编译核实（cfr 1.21.1 mojang jar）：
- `GuiGraphics.renderItem` 用 `pose.translate(x+8,y+8, 150+...)`——物品图标一律画在 **z=150**；
- 容器槽位/配方书内容都在 renderItem 走 z=150；配方书背景另加 `translate(0,0,100)`；
- 参考 1.21.11 在 `TopLayerOverlayRenderer.render` 先 `guiGraphics.nextStratum()`
  （1.21.5+ 新 API，把后续绘制推到新层、盖过所有既有 z），1.21.1 **无 nextStratum**——
  本移植一直没用它，导致浮层/火焰画在缺省 z=0，被 z=150 的下层内容盖住。

**① 浮层被盖住—根因+hink**：afterRender 钩子时序本身是对的（fabric `ScreenEvents.afterRender`
经 `GameRendererMixin` 包裹 `screen.renderWithTooltip(...)`，而 `renderWithTooltip` 是 **final**，
调用整个 virtual render 链含槽位+配方书；neoforge `ScreenEvent.Render.Post` 经
`ClientHooks.drawScreenInternal` 在 `renderWithTooltip` 之后 post——二者都在整屏之后）。
真正问题是 **z 序**：浮层面板 `blitSprite`(z=0)、图标 `renderItem`(z=150)，而容器槽位/配方书
也在 z=150，GUI 深度测试下后者盖前者。修复：`TopLayerOverlayRenderer.renderViewer` 用
`gui.pose().pushPose() + translate(0,0,400) + ... + popPose()` 把整层抬到所有内容之上
（z=400 与 1.21.11 tooltip 顶部 z=400 同语义；vanilla 拖拽物品浮动 z=232 在其下）。

**② 只加载燃料/堆肥—根因**：`RecipeViewerIndex.rebuildEngineInternal` 用 `categoryPath`
（来自 RecipeType，返回裸 "crafting"/"furnace"/"blast_furnace"/"smoker"/"campfire"...）→
`Workstation.matchesPath`。1.21.1 的类别前缀却是 `"crafting_"`/`"furnace_"`/`"blast_furnace_"`/
`"smoker_"`（尾下划线，预期 1.21.11 recipe-book 子路径如 "furnace_food"）。`matchesPath` 对
尾下划线前缀走 `path.startsWith(prefix)`；`"crafting".startsWith("crafting_")` = **false** →
crafting/furnace 等 recipe 从**不匹配任何工作站** → 引擎 registerType 为空 → 这些类别
`hasContent`/`query` 全空 → 不显示；而 fuel/compost 是 grid 类别（直接 allGridItems/gridItems，
独立于引擎）→ 照常显示。修复：`matchesPath` 对尾下划线前缀 **同时**接受裸根
`path.equals(prefix.substring(0, prefix.length()-1))` 与前缀子路径 `path.startsWith(prefix)`。
（1.21.11 的 categoryPath 直接返回 recipe-book 类别路径如 "furnace_food"，故其前缀设计
本就带下划线；1.21.1 从 RecipeType 推导裸路径，移植时前缀与路径约定不一致。）

**③ 火焰被熔炉图标盖住—根因**：drawCategoryTabs 熔炉图标 `renderItem`（z=150），火焰
`blitSprite(FURNACE_FIRE_SPRITE, iconX+10, iconY+10, 6, 6)`（**5 参无 z → z=0**）→ 火焰落在
图标之下。修复：改用 **6 参 blitSprite**（`blitSprite(res, x, y, z, w, h)`，z 直通 vertex）
`z=160`（> 150）→ 火焰盖在熔炉图标之上。同修 `PopupRenderer.renderVanillaContent MODE_FURNACE`
的火焰（同样 renderItem z=150 vs blitSprite z=0）。

**关键 API 差异备忘（本轮反编译核实）**：
- 1.21.1 `blitSprite` 有 6 参重载 `blitSprite(res, x, y, z, w, h)`，第五参即 z 深度
  （经 innerBlit vertex z 直通）；`ClientCompat.blitSprite` 是 5 参无 z 薄封装。
- 1.21.1 无 `nextStratum()`（1.21.5+ `GuiGraphics` 才有）；顶层绘制用 pose translate z。
  1.21.1 `GuiGraphics` 仅有 `flush()`（内部 disableDepthTest/endBatch/enableDepthTest）。
- 1.21.1 vanilla GUI z 常量：配方书内容 translate z=100、物品图标 renderItem z=150、
  拖拽浮动物品 translate z=232、tooltip renderTooltipInternal translate z=400。
  **z=400 即"最顶"语义**，浮层 base 抬高取此值。

**部署**：备份 20260829-222949（原子替换）；fabric md5 0d8445ee、neoforge md5 07ba5908。
**待用户实测**：①R/U 打开浮层完整可见（物品栏/配方书之上）；②除燃料/堆肥外，crafting/
furnace（=熔炉家族）等类别应出现；③燃料 tab 火焰盖在熔炉图标之上；Shift 预览弹窗火焰
同修复。

## 2026-08-29（五轮）：查询系统类别缺失 + tooltip/预览错乱修复（已部署双端）

用户实测（NeoForge，截图 屏幕截图_20260829_223332.png）报：①查询界面仍缺大量原版类别，
模组类别完全不加载；②自定义 tooltip 与 Shift 预览布局严重错乱（大空黑框 + 材料行被推到
底部 + 大竖缝隙）。两个子代理（tooltip/类别）+ 本轮反编译 & 实例日志联合定位。

**类别缺失（bug A）——多根因**：
- **①无头 JEI 核心在 NeoForge 从不启动（主因，mod 类别/anvil/brewing/grindstone/info 全缺）**：
  headless-jei 作为 jar-in-jar 打进去，其 neoforge 入口 `BrbeJeiPluginsClientNeoForge.init()`
  **没有任何 @Mod 类调用**（neoforge.mods.toml 无 entrypoint、全 jar 无 @Mod 注解类、init
  无调用者）→ `JeiRecipeRegistry` 恒空 → BRBE `BrbeJeiBridge.refresh()` 导入 0 条 → 无
  JEI 类别。实例日志佐证：无 `[BRBE-JEI-BRIDGE] imported`、无 `[BRBE-JEI-Plugins] embedded
  JEI core started`，仅 `[BRBE] rebuildEngine known=2798 types=7`（只 7 个 vanilla 类型，
  JEI 通道 0）。
- **②refresh 一次性 + 无轮询**：fabric JOIN / neoforge LevelEvent.Load+RecipesUpdated 只触发
  一次，与 headless-jei 采集（分阶段/异步，插件生命周期在 later handler）race → 读到空
  registry。1.21.11 是每 END_CLIENT_TICK 轮询 + 指纹去重。
- **③切石/锻造被两边都不注册（double defer）**：`RecipeViewerIndex.rebuildEngineInternal`
  跳过 stonecutting/smithing（注释"来自 headless-jei"），而 headless-jei 1.21.1
  `PluginRecipeIndexer.SKIP_VANILLA` 同样排除 → 两边都不注册 → 切石/锻造 tab 恒空。
- **④熔炉类只读 smelting**：`FurnaceRecipeCategory` 只 query `minecraft:smelting`，而 index
  独立注册 smelting/blasting/smoking/campfire_cooking → 熔炉 tab 缺鼓风炉/烟熏炉/营火配方。

**tooltip/预览错乱（bug B）——RC1/RC2/RC3/RC4**（tooltip 子代理反编译双端逐行核实）：
- **RC1（主因，48x48 预览空 = 大黑框）**：`PopupRenderer.renderVanillaContent` 用**屏幕绝对
  坐标**画 `fill(-4,-4,52,52)` + 图标 (2,2)/(38,2)/(40,2)，而在 `renderRecipePopup` 的
  scale-about-center 变换（center=(x_row+24,y_row+24), scale=2）下，`T(p)=c+2(p−c)`——内容落在
  `[−x_row−20, 92−x_row]`。凡 tooltip x_row≥92 内容全部 off-screen → 48x48 行只显示 vanilla
  tooltip 背景（黑、空）。1.21.11 内容是 **button 相对**（sprite 在 (x,y,w,h)，槽位
  (x+2,y+2)/(x+4,y+15)/(x+12,y+7) 等），与缩放正确复合。
- **RC2：无 sprite 背景**：1.21.1 用平铺 56x56 黑 fill 代替 recipe-overlay sprite；1.21.11
  用 `ButtonBackdrop`（crafting/furnace overlay sprite，按 craftable/partial/hover 状态）。
  1.21.1 已有 `RECIPE_BOOK_CRAFTING/PLAIN_OVERLAY_SPRITE`（WidgetSprites）可直接用。
- **RC3：tooltip 行序错 + 多出"材料"文本行**：1.21.1 行序 title→熔炼行→StationLine→材料行→
  预览→空行+模组名；1.21.11 是 title→熔炼行→**预览**→StationLine→空行+模组名（无材料文本行，
  材料在预览内）。导致预览被挤到底部、大竖缝。
- **RC4**：tooltip vanilla 调用传 `false, 2.0F`（hover=false、硬编码）；1.21.11 传
  `true, VANILLA_SCALE`。

**修复（bug A）**：
- `BrbeJeiBridge` 新增 `ensureHeadlessStarted()`：反射启动 `BrbeJeiHeadlessCore.start()` +
  `BrbeJeiPlugins.collectAndInject()`（幂等 isRunning 守卫），在 `refresh()` 开头调用——绕过
  NeoForge 缺失的 zheadlessjei 入口（BRBE 与 zheadlessjei 同 classloader，Class.forName 可达）。
- `BrbeJeiBridge.refresh()` 加指纹去重（类型+条数签名 `lastSignature`）+ 每 tick 轮询
  （fabric END_CLIENT_TICK / neoforge ClientTickEvent.Post，`client.level != null` 守卫）——
  1.21.11 同策略，处理采集分阶段 race。
- `RecipeViewerIndex.rebuildEngineInternal`：移除 stonecutting/smithing 的 skip（known 集
  路径注册；headless-jei 也排除 → 不再重复，registerType 同 uid 幂等覆盖）。
- `FurnaceRecipeCategory`：聚合 smelting/blasting/smoking/campfire_cooking 四类型（结果物品
  去重），`query`/`allEntries`/`appliesTo` 同步。

**修复（bug B）**：
- `PopupRenderer.renderVanillaContent` 重写为 button 相对 + sprite 背景：`blitSprite(sprites.get
  (craftable||partial, hover), x, y, w, h)`（furnace=plain overlay，其余=crafting overlay）；
  槽位 button 相对（furnace (x+2,y+2)/(x+4,y+15)/(x+12,y+7)、fixed pair (x+2,y+2)/(x+12,y+7)、
  crafting 3x2 (x+2+i%3*5, y+2+i/3*5) + result (x+17,y+2)）；partial 红罩移入（非 crafting 模式）
  。`renderRecipePopup` 里删除旧 partial 覆盖（防重复）。
- `RecipePreviewTooltipComponent`：构造器加 `craftable`/`partial`；vanilla 调用传
  `hover=true` + `PopupGeometry.VANILLA_SCALE`；JEI 回退 guard `holder != null`（防纯 JEI
  条目 + renderer 缺席时 NPE）。
- `RecipeViewerOverlay.renderEntryTooltipRich`：行序改为 title→熔炼行→**预览**→StationLine→
  空行+模组名；删除"材料"文本行；新增 `isViewerCraftable`/`isViewerPartial` 辅助（与 Shift
  弹窗同源 PartialCraftingUtil）。

**关键 API 差异备忘**：1.21.1 `ClientTooltipComponent.renderImage(Font,int,int,GuiGraphics)`
无 whole-tooltip width/height 参数（1.21.11 有）——但 1.21.11 的 renderImage 忽略该参数
（px=x, py=y 左/顶锚定），1.21.1 的 `getHeight()/getWidth(Font)` 完全匹配 vanilla 1.21.1
接口，无需接口拆分/桥。1.21.1 无带 style 的公开 renderTooltip 重载——private
`renderTooltipInternal` 是唯一 ClientTooltipComponent 路径，`GuiGraphicsAccessor` @Invoker
正确且必要；`ClientCompat.VIEWER_TOOLTIP_STYLE` 在 1.21.1 为死代码（无 style 参数接口）。

**部署**：备份 20260829-225312（原子替换）；fabric md5 c006f9b7、neoforge md5 d94a1c59。
**待用户实测**：①查询界面应出现切石/锻造/铁砧/酿造/研磨/信息 + mod 类别（如 FD 厨锅）；
②tooltip 内嵌预览完整（无空黑框），行序=物品名→预览→工作站→模组名；③Shift 预览 48x48
完整；④熔炉类别含鼓风炉/烟熏炉/营火配方。

## 2026-08-29（六轮）：查询系统卡顿修复——per-tick collectAndInject 每帧全量 JEI 收集（已部署双端）

用户实测（NeoForge + Fabric，F3 面板 662ms max / TPS 20 但帧时间尖峰）报严重卡顿。
反编译 + 代码走查定位根因：上一轮（五轮）把 `BrbeJeiBridge.refresh()` 加到**每
END_CLIENT_TICK** 轮询（1.21.11 语义，解决 mod 类别时序），但 `refresh()` 内部
`ensureHeadlessStarted()` **无条件**调用 `BrbeJeiPlugins.collectAndInject()`——即
**每帧 ×全量 JEI 配方收集/索引**（重操作），在渲染线程造成 662ms 帧尖峰。

**根因（五轮引入）**：`ensureHeadlessStarted` 在 `refresh()` 里每 tick 反射调
`pluginsCollectMethod.invoke(null)`（collectAndInject，全量 JEI recipe indexing）。
指纹去重只跳过了**导入循环**（registerJeiType），没跳过**收集调用**（collectAndInject）。
1.21.11 的 `refresh()` 是在 `END_CLIENT_TICK` 上只做指纹导入，收集由 JEI 自身
生命周期（RecipesUpdated 等）触发，不逐 tick。

**修复**：收集与轮询分离。
- `BrbeJeiBridge` 新增 `collectPending` 标志 + `requestCollect()`（配方同步事件置位）
  + `doCollect()`（真正反射 collectAndInject，仅 collectPending 时由 refresh() 调用一次
  后清位）。
- `ensureHeadlessStarted()` 只 `start()`（幂等；start 转换时置 collectPending），
  **不再**收集。
- `refresh()`：ensureHeadlessStarted → 若 collectPending 则 doCollect 一次并清位 →
  指纹导入（未变化 early-return）。每 tick 只剩幂等 start（isRunning 反射）+
  指纹循环（读小 registry，廉价）。
- 接线：fabric JOIN → `requestCollect()+refresh()`；neoforge LevelEvent.Load /
  RecipesUpdatedEvent → `requestCollect()+refresh()`（配方同步时重新收集，覆盖 mod
  配方晚到场景）；每 tick 轮询保持 `refresh()`（消费 pending 一次后廉价）。

**效果**：collectAndInject 只在 start 转换/配方同步时执行（每会话/每次配方同步几次），
不再每帧执行；每 tick 只剩指纹导入。662ms 帧尖峰应消失，且 mod 类别数据仍能通过
配方同步事件到达（NeoForge 无 zheadlessjei 入口、BRBE 反射启动核心的用户先前修复
保持生效）。

**部署**：备份 20260829-234918（原子替换）；fabric md5 d6f49dbe、neoforge md5 0c2a7e34。
**待用户实测**：游戏应恢复流畅（帧时间无 662ms 尖峰）；查询界面 mod 类别/anvil/brewing/
grindstone 仍应出现（配方同步事件收集）；tooltip/预览/燃料/切石/锻造等功能不应回退。

## 2026-08-29（七轮）：卡顿根因修复——headless-jei atlas 失败无限重试循环（已部署双端）

用户实测仍严重卡顿（662ms，卡到没法玩）。反编译 + 实例日志定位真实根因：
**headless-jei 核心 start() 在 NeoForge 因 JEI GUI 图集未初始化解不出 atlas，且失败后
running 标志不置位 → BRBE 每 tick 重试一次完整 JEI 启动 → 每帧崩溃+日志 → 1854 次/百秒
（百秒连续刷 662ms，渲染线程被卡）。**

**证据**（实例 `logs/debug.log`）：
- `grep -c "embedded JEI core failed to start"` = **1854 次**，首末时间 23:51:39.442 →
  23:53:22.507（约 103 秒），每 ~60ms 一次（约每几 tick）。
- 日志逐行：`[BRBE-JEI-Plugins] embedded JEI core failed to start: java.lang.IllegalStateException:
  Tried to lookup sprite, but atlas is not initialized` → `no JEI plugins found` → 下一条重复。
- 反编译 `BrbeJeiHeadlessCore.start()`：成功路径 `putstatic running:Z`（136 行），**失败路径
  （"embedded JEI core failed to start"，165 行）不置位 running** → `isRunning()` 恒 false。
- `BrbeJeiBridge.ensureHeadlessStarted()` 逻辑 `if (!running) start()` → 每 tick 都重试。

**根因链**：①NeoForge 的 zheadlessjei 入口 `BrbeJeiPluginsClientNeoForge.init()` 无 @Mod 调用
（前置轮次已修：BRBE 反射启动核心）；②但该 init 里还负责把 `JeiGuiSpriteManager`（Textures→
getGuiSpriteManager，extends `TextureAtlasHolder`→implements `PreparableReloadListener`）注册到
`RegisterClientReloadListenersEvent`——init 从不执行 → atlas 永不初始化 → start() 必抛
"atlas is not initialized"；③start() 失败不置 running → 无限重试。

**修复（BrbeJeiBridge + 双端接线）**：
- **启动闸 `startAttempted`**：`ensureHeadlessStarted()` 尝试过一次（无论成败）后不再重复
  start()——立即切断每 tick 重试。失败后真正启动由 atlas 就绪事件（retryStart）再试。
- **atlas 反射注册**：`registerAtlasReloadListener(Object RegisterClientReloadListenersEvent)`
  反射 `mezz.jei.common.Internal.getTextures().getGuiSpriteManager()` 得到 JeiGuiSpriteManager
  （PreparableReloadListener），`event.registerReloadListener(...)` 注册——atlas 初始化后
  start() 成功 → running=true → 循环彻底关闭。
- **retryStart()**：`startAttempted=false; collectPending=true`——配方同步/level 载入事件重置
  闸，允许在 atlas 就绪后真正启动一次，再由 refresh() 消费收集。
- 接线：neoforge `RegisterClientReloadListenersEvent`（modEventBus）→ `registerAtlasReloadListener`；
  LevelEvent.Load / RecipesUpdatedEvent → `retryStart()+refresh()`；fabric JOIN → `retryStart()+refresh()`；
  每 tick 轮询 `refresh()` 只做幂等 start（startAttempted 守卫）+ 指纹导入。

**验证**：反射链核验 `Internal.getTextures()`→`Textures.getGuiSpriteManager()`→`JeiGuiSpriteManager`
（extends TextureAtlasHolder→implements PreparableReloadListener）存在。双端 build 通过，
字节码确认 `startAttempted`/`retryStart`/`registerAtlasReloadListener` 及
RegisterClientReloadListenersEvent 接线在位。

**部署**：备份 20260829-235825（原子替换）；fabric md5 d98ba224、neoforge md5 33e31812。
**待用户实测**：游戏应恢复流畅（662ms 尖峰消失）；查询界面 mod/anvil/brewing/grindstone 类别
应出现（atlas 就绪后 start 成功 + 配方同步收集）；tooltip/预览等功能不应回退。

## 2026-08-30（八轮）：查询前端系统化对齐阶段一（7 项用户可见差距，已部署双端）

用户确认路径：先阶段一（7 项可修差距），再阶段二 B（BRBE 内建 display 等价模型）。
基于 frontend-diff 子代理的精确清单逐项对齐 1.21.11。

**已落地（阶段一 7 项）**：
- **①JEI/mod 配方按钮补材料**：`RecipeViewerOverlay.render` 的 JEI 条目分支（原只有
  plain 格+结果）→ 结果图标 + 前 3 项 JEI 输入材料（0.6 缩放排布在结果下方，
  `renderScaledCellItem` 辅助）。对齐"按钮显示材料而非只有结果"。
- **②熔炉 tooltip 按工作站多行+图标**：`renderEntryTooltipRich` 的熔炼块从单行耗时 →
  1.21.11 `furnaceTooltipComponents` 语义——XP 行 + 四子站（furnace/blast/smoker/campfire）
  各一行（颜色随站=`stationStyle`、白点 `•` 标记当前打开站=`stationMatches/menuIs`、
  标签=`furnaceStationLabel`、前缀=`furnaceStationPrefix`）+ 该站工作站图标行
  （`workstationsIconsForPrefix`，新增于 `RecipeViewerIndex`；hide 开时过滤）。
- **③弹窗槽位 tooltip+点击放置**：`renderTooltip` 弹窗分支从"只吞"→ `popupSlotStack`
  （`PopupGeometry.itemAt` + selIdx 游戏时间/30）+ 物品名/模组名 tooltip；
  `mouseClicked` 弹窗内左键从"只发声"→ `placeRecipe`（有 holder 时）继承按钮放置。
  `PopupRenderer.renderRecipePopup` 现在也缓存 `geometrySlotCache`（槽位命中用）。
- **④悬停改高亮 sprite（弃 2× 放大）**：`render` 的悬停按钮块去掉 2× scale 变换 → 只
  在最后重绘一遍（vanilla 按钮自绘 `_highlighted` sprite；1.21.11 "viewer 内悬停只换
  高亮 sprite，放大只属 Shift 预览"语义）。
- **⑤mod 类别站列**：`rebuildStationColumn` 对 `PluginRecipeViewerCategory` 用
  `plugin.stations()`（原来 `stationColumnItemsFor` 对插件 id 返回空 → 站列空）。
- **⑥info 类别 JEI 文案行**：`gridTooltipLines` 加 `InfoRecipeCategory` 分支——显示
  `descriptionFor(stack)` 的 JEI 信息文案行（此前 info 网格 tooltip 无内容）。
- **⑦tab 滑窗标记 ◀▶**：`drawCategoryTabs` tooltip 行在可见类别数 > MAX_TABS 时附
  ◀▶/◀/▶ 标记（`tabWindowCount` 辅助；信息语义对齐 1.21.11）。
  **VIEWER_TOOLTIP_STYLE 仍为死代码**（1.21.1 无带 style 参数的 renderTooltip 重载，
  `renderTooltipInternal` 是唯一路径且固定 default 背景——API 限制，镜像记录）。

**API 差异备忘**：`getFormattedModName` 返回单个 `Component`（非 List）；`Screen`/`Mth`
需显式导入（`RecipeViewerOverlay` 在 `com.alonie.brbe.util` 包内，`Screen` 是
`net.minecraft.client.gui.screens.Screen`、`Mth` 是 `net.minecraft.util.Mth`——本轮
编译错误两处，已补导入）。

**部署**：备份 20260830-004022（原子替换）；fabric md5 b2436789、neoforge md5 c4efa5f7d。
**待用户实测**：①JEI 按钮显示材料图标；②熔炉 tooltip 按站多行+图标+白点；③Shift 弹窗
槽位有 tooltip、点击弹窗放置配方；④悬停只高亮不 2× 放大；⑤mod 类别站列有内容；
⑥info 类别显示 JEI 文案；⑦标签可滑动时 tooltip 带 ◀▶。之后进入阶段二 B。

## 2026-08-30（九轮）：阶段二 B 起步——内建 display 等价模型 P1/P2（引擎 layout 注册表 + 条目身份）

用户确认"先阶段一，再做阶段二 B"。阶段一（7 项）已完成部署（见八轮）。本轮启动
阶段二 B：BRBE 内建 RecipeDisplayEntry/SlotDisplay 等价模型。设计审计子代理（87c360f1）
运行中，本轮回合并行落地了已独立确认的部分。

**P1：引擎 layout/身份模型（RecipeViewerEngine，1.21.11 逐字对齐）**：
- `RecipeDisplayId(String key)`——条目稳定身份键（1.21.11 RecipeDisplayId 等价物）。
- `RecipeSlotLayout(x, y, role, stacks)` / `RecipeBackground(texture, u, v, width, height,
  textureWidth, textureHeight)` / `RecipeLayout(width, height, slots, background)`——
  1.21.11 同名 record 逐字段等价（RecipeDisplay/SlotDisplay 缺席下的 mod 内建抽象）。
- 注册表：`BY_ID`（id→holder）+ `LAYOUTS`（id→layout）+ `idFor(holder)` /
  `idForJei(typeUid)` / `entryFor(id)` / `isSynthetic(id)`（无 holder = JEI 条目）/
  `registerLayout(id, layout)` / `getLayout(id)`——1.21.11 引擎同款 API 面。
- 数据挂靠：`registerType` 每 holder 条目挂 BY_ID；`registerJeiType` 每 JEI 条目挂 BY_ID
  + 从 entry.slots()/layoutWidth/Height 注册 layout（槽位已有、背景 null 待填）。

**P2：DisplayEntry 身份**——`RecipeViewerOverlay.DisplayEntry` 加 `id()`（holder→
idFor(holder)，JEI→idForJei(typeUid)），前端可统一 entryFor/getLayout/isSynthetic。

**设计说明**：1.21.1 引擎的 JeiEntry/JeiSlot 本就携带 layout 数据（bridge 反射透传），
本模型补的是"身份键 + layout 注册表间接层"（1.21.11 借 RecipeDisplayId 把
RecipeHolder 条目与 JEI 条目统一挂靠）——前端 P3-P5 可逐行对照 1.21.11 移植
（entryFor/getLayout 驱动弹窗 1:1、tooltip 内嵌预览、pinoverlay 独立窗）。

**部署**：本轮仅为引擎模型（无前端行为变化，弹窗仍走 JeiEntry 内联 layout），
双端 build 通过、jar 核验 RecipeLayout/RecipeDisplayId 记录在位；未单独部署
（与 P3-P5 一起部署验证）。待设计审计报告后按 P3（弹窗 1:1 移植）→ P4（预览/tooltip）
→ P5（pinoverlay）推进。

## 2026-08-30（十轮）：阶段二 B P3-P5——切石/锻造 holder 条目挂接 JEI 布局 + 弹窗/tooltip/pin 1:1 委托（已部署双端）

设计审计报告（87c360f1）收尾：1.21.1 引擎 record 层（P1）与 DisplayEntry.id()
（P2）已落地；剩余真空洞集中在**切石/锻造 holder 条目无 layout**——headless-jei
1.21.1 的 `VANILLA_PLUGIN_TYPES` 不含这两个类型（其 JEI 运行时渠道被 SKIP_VANILLA
排除、runtime 索引只做 anvil/brewing/grindstone）→ holder 条目的 Shift 预览/pin
只能走 vanilla 固定双槽，无法委托完整 JEI UI（1.21.11 的 attachVanillaCategoryLayouts
语义）。本轮补齐三件套：

**P3a：headless-jei 索引器**（独立项目 `headless-jei/1.21.1`，仅 1 文件）：
`VANILLA_PLUGIN_TYPES` 补 `minecraft:stonecutting`/`minecraft:smithing`（与
1.21.11 版索引器一致；注释标明"有 datapack holder 的条目由消费者按 holder id
附着，不重复注册"）。JEI 运行时收集期照常 buildEntry → setRecipe 原生槽位 +
layout 尺寸（切石 82x34 输入(1,9)→输出(61,9)；锻造 108x28 模板/基底/附加/
输出 91,6），进 JeiRecipeRegistry（占位校验通过：两类 recipe 是 RecipeHolder）。

**P3b：BrbeJeiBridge attachVanillaLayouts**（BRBE 侧）：refresh() 对 ATTACH_TYPES
（stonecutting/smithing）的注册表条目**不 import 进 JEI 通道**（类别 queryJei
默认空、导入无人消费且与 holder 通道并列）→ 改 `attachVanillaLayouts`：按
`holder.id()` 恒等匹配引擎已有 holder 条目（同一 RecipeManager 数据源）→
`registerLayout(idFor(holder), layout)` + `ATTACHED_UID/RECIPE/ENTRY_BY_ID`
三映射（attachedJeiEntry 公开查询）。引擎重建监听器（addRebuildListener →
markAttachDirty：清附着映射+置位）→ refresh() 在签名未变时也重跑附着
（重建晚于桥导入时不丢）。

**P3c-P5：前端委托**：
- `renderShiftPopup`：holder 条目先 `attachedJeiEntry(id)` 非空 → 1:1 完整 JEI UI
  （renderJeiPopup1to1，回退 2.0F renderJeiPopup）+ 非 crafting 模式残缺红罩
  （partial && mode!=CRAFTING，1.21.11 同语义）；否则原 vanilla 弹窗。
- `drawPinPopup`（viewer 内 A 键固定弹窗）：同分支。
- `renderEntryTooltipRich`（P4）：内嵌预览组件改传 `previewJei`（holder 条目带
  布局时 = attachedJeiEntry）→ tooltip 内嵌完整 JEI 界面（0.6 缩放的
  renderJeiPopupScaled 路径，原 48x48 vanilla 兜底仅无布局时）。
- `PinOverlay.render`（P5）：holder 条目带布局 → 1:1 委托（pin 独立窗与
  viewer 弹窗同几何；无布局走原 vanilla）。

**部署**：headless-jei 重建（fabric 66eed05b / neoforge 943f9abf，替换 BRBE
resources 内 META-INF/jars|jarjar 的 JIJ jar）→ BRBE 双端 build → 原子替换
（备份 20260830-01xx）；fabric 892cdacd、neoforge 9efb51b1（JIJ 内嵌 md5 已核验）。
**待用户实测**：R/U 查询切石机/锻造台条目 → Shift 预览/A 键 pin/tooltip 内嵌预览 =
完整 JEI 界面（切石 82x34、锻造 108x28，含槽位背景/箭头）；无布局时回退正常。
**下一步**：阶段二 B 剩余项（审计 §5 的引擎级 DisplayEntry 统一抽象——本轮回合
以"holder 附着 + attachedJeiEntry"保行为等价，未做全量提升；如需逐行等价再补）。

## 2026-08-30（十一轮）：JVM 启动参数屏蔽查询功能（仅 1.21.1）

用户决策：加一个 JVM 启动参数暂时屏蔽 R/U 查询 viewer（"查询功能"）。值为
`true` 时隐藏上述功能及其配置项，默认 `true`（即默认屏蔽）。**仅 1.21.1 分支**，
其他分支不动。

**落地（4 文件 + 2 新文件）**：
- `config/RecipeViewerFeatureFlag`（新）：`brbe.disableRecipeViewer`，默认 `true`
  即屏蔽；设 `-Dbrbe.disableRecipeViewer=false` 恢复。类加载时一次性判定，
  与 `BrbeLogger`（brbe.debug）同一 JVM 属性约定。
- 运行时屏蔽：`RecipeViewerOverlay.keyPressed` 开头 `isDisabled() → return false`
  （R/U 打不开、重开 no-op）；`render`/`renderTooltip` 开头同样 return（不渲染）。
  因 `active` 恒 false，mouseClicked 等也自然 no-op。
- 配置 GUI 屏蔽：
  - `KeybindingGuiRegistrar`：屏蔽时对 `recipeViewKey`/`usageViewKey` 两个
    键位字段的 predicate provider 返回 `List.of()`（隐藏 R/U 键位项）。
  - `RecipeViewerGuiRegistrar`（新）：仅屏蔽时注册 predicate provider，对
    `recipeViewerEnabled`/`hideNoRecipeBookStationObjects` 返回 `List.of()`
    （隐藏布尔开关项）。未屏蔽时不注册——保留这两字段的 AutoConfig 默认渲染
    （含 `@PrefixText` 等）。
- 双端入口：fabric/neoforge `BetterRecipeBookClient*` 各加
  `RecipeViewerGuiRegistrar.register()`（KeybindingGuiRegistrar 旁）。

**不影响**：配方书 pin（`mixins/pins/AbstractContainerScreenMixin` 直连
PinnedRecipeManager，独立于 viewer）；A 键 pin（PinOverlayManager，无 viewer
时按类别查询创建）。`BrbeJeiBridge.refresh()` 仍每 tick 指纹去重（cheap），
不额外加闸——viewer 屏蔽后其消费方停用，数据无害。

**待用户实测**：启动后按 R/U 无反应、配置界面无查询相关项；`-D...=false` 恢复。

## 2026-08-30（十一轮·补）：屏蔽跟进——配置标题行跟随隐藏 + 无头 JEI 一并禁用

用户实测（截图）反馈两点跟进：①"查询合成/用途"纯文字行（`recipeViewerEnabled`
的 `@ConfigEntry.Gui.PrefixText` 标题）也要跟随屏蔽隐藏；②无头 JEI 也要跟随禁用。

**①标题行跟随隐藏（根因 + 修复）**：
- 根因：`@PrefixText` 由 AutoConfig 的注解 transformer 在字段 provider 之后
  运行，给该字段的渲染列表**前置**一个 `TextListEntry` 标题行——上一轮只对
  字段本身返回空列表（隐藏开关），但标题行是注解 transformer 加的，仍留下。
- 修复：**移除 `recipeViewerEnabled` 的 `@ConfigEntry.Gui.PrefixText` 注解**，
  改由 `RecipeViewerGuiRegistrar`（predicate provider）**接管**该字段渲染——
  未屏蔽时渲染 `startTextDescription`（"查询合成/用途"标题，读
  `...recipeViewerEnabled.@PrefixText` 翻译键）+ `startBooleanToggle` 布尔开关
  （视觉与原注解一致）；屏蔽时返回空列表（标题 + 开关一起隐藏）。`hideNoRecipe
  BookStationObjects` 同理接管（无标题、仅开关）。不再依赖 AutoConfig 注解
  transformer 的次序。

**②无头 JEI 一并禁用**：`BrbeJeiBridge.ensureHeadlessStarted()` 与 `refresh()`
  开头加 `isDisabled() → return`（仅 1.21.1；headless-jei 是独立项目，其 jar 不动）——
  屏蔽时不启动核心、不收集、不导入。入口点（fabric JOIN/neoforge LevelEvent/
  RecipesUpdated/每 tick）全部走 `refresh()` → 短路；`retryStart()` 只重置标志，
  无实际启动副作用；atlas 重载监听注册仅是注册，核心不启动即无害，一并保留。

**部署**：备份 20260830-02xx（原子替换）；fabric 541bd35e、neoforge 5e9f1cdc。
字节码核验：`recipeViewerEnabled` 字段仅剩 `@Tooltip`（`@PrefixText` 已移除，
class 常量池中的 PrefixText 为他字段所有）；`RecipeViewerGuiRegistrar` 含
`isDisabled→List.of`（屏蔽返回空）+ `startTextDescription` + `startBooleanToggle`；
`BrbeJeiBridge` 含 shield 引用（1 处各端）。

## 2026-08-30（三）：屏蔽跟进 3——A 键 pin 浮层预览一并禁用（已部署双端）

用户实测：`-Dbrbe.disableRecipeViewer=true`（默认）下，对着物品按 A 仍能 pin
出**查询浮层的预览界面**（`PinOverlay` 浮层弹窗）。此前屏蔽只门控了
`RecipeViewerOverlay`（R/U/render/renderTooltip/BrbeJeiBridge），`PinOverlayManager`
未门控。

**根因**：A 键 pin 创建有两条调用链——
- `RecipeViewerOverlay.keyPressed`（A 键分支）→ **已门控**（`isDisabled()` 头部门）
- `KeyboardHandlerMixin.brbe$viewerKeysEarly`（priority 2000 键盘层早入口）直接
  `RecipeViewerOverlay.keyPressed(...) || PinOverlayManager.handleKeyPressed(...)`，
  而 `RecipeViewerOverlay.keyPressed` 被门控后返回 false，`||` **短路落入
  `PinOverlayManager.handleKeyPressed`**——创建 preview `PinOverlay`。泄漏点。

**修复**：门控 `PinOverlayManager` 三入口（均用现有 `RecipeViewerFeatureFlag.isDisabled()`）
- `init()`：屏蔽时不加载持久化 pin（`brbe.pinoverlays.json`）——PINS 保持空，
  渲染/命中/交互自然惰性
- `render()`：屏蔽时不画 pin 浮层（含持久化恢复项）
- `handleKeyPressed()`：A 键不再创建/移除 pin 浮层

**配方书 pin 不受影响**：`mixins/pins/AbstractContainerScreenMixin` →
`PinnedRecipeManager.toggleFavourite`（A 键 pin 配方书）不调 PinOverlayManager，照常可用。

**验证**：`:common:compileJava` 通过、双端 build 成功；部署（备份 20260830-123701，
原子替换）；javap 核验双端 `PinOverlayManager` 三处 `isDisabled()` 各落于
`init`/`render`/`handleKeyPressed` 方法体。

## 2026-08-30（四）：残缺配方振荡根因——markAndInject 污染 craftable 集合（已部署双端）

用户实测：配方状态混乱（有时变可合成、点击后变不可合成）。根因锁定为侵入式耦合：
`PartialCraftingUtil.markAndInject` 把**残缺**配方 `brbe$getCraftable().add(holder)`
写入原版语义集合，使 `isCraftable()` 对"玩家做不出"的配方返回 true。

**振荡链**：
1. markAndInject 注入 partial→craftable（显示"可合成/亮"）
2. 下一次刷新 markPartialMaterials line 233 `if (isCraftable(recipe)) continue`
   → 该配方被跳过不再标 partial；若集合无其它 partial 还 clearTags 清整集合标签
   → 矛盾态 craftable={X}, partial={}
3. 点击走 MultiPlayerGameMode.handlePlaceRecipe / unlockrecipes
   `if (!lastRecipe.isCraftable(recipe))` → 因污染判"可合成"→ 真放 → 材料不够失败
4. 刷新后 vanilla canCraft 重算 → 移除 craftable → 变"不可合成"

**关键证据**：incompletecrafting/RecipeBookComponentMixin line 414-416 原有注释自述
"a second markPartialMaterials pass which sees isCraftable=true (from the first
pass's injection) and calls clearTags() — wiping out the correct partial data"——
正是该级联的开发者记录。

**修复**：markAndInject 不再注入 craftable，只写 partial 标签（删 2 处 getCraftable
调用 + 相关 javadoc）。显示层本就标签驱动：RecipeButtonMixin @Redirect
`hasCraftable()→hasCraftable()||hasPartial`、getOrderedRecipes Step 1 重新并入
partial、applyVisibility/applyPartialSort/applyFilterToggle/categorize 全走 tag。
3×3 preCheck 提升（RecipePipeline:217）是**真可合成**注入，保留。

**语义分离**：`isCraftable`（语义，仅原版 canCraft 决定）vs partial 标签（显示）。
viewer 已独立读两者（RecipeViewerOverlay:1384-1385）。

**维护性加固**：RecipeStateDiagnostic 新增**互斥不变量**分支——若同一配方同时
craftable=Y partial=Y 则判"互斥破坏/不合格"。旧诊断 PARTIAL 只断言 isPartial，
未覆盖该破坏态（正是本轮 bug 场景）；现在该不变量被硬性检查。

**验证**：compileJava 通过、双端 build 成功、部署（备份 20260830-144546/144631，
原子替换）；javap 核验双端 markAndInject 无 getCraftable（0 处）、diagnostic run
方法在。**待用户实测**：残缺配方不再闪可合成；点击不再变不可合成；partial 仍可
点击/预览（走 ghost 路径，因 isCraftable 现正确为 false）；set -D...=false 无关。

## 2026-08-30（五）：动画+排序三缺陷修复（已部署双端）

用户实测 3 问题，均在翻页动画混入+显示缓存；第 2 项与上一轮语义分离（markAndInject
不再注入 craftable）连锁：
- ①残缺配方状态改变后排序不即时更新（须重开配方书）
- ②动画播放时所有残缺配方额外叠一层红罩（播完消失）
- ③动画播放时 pin 贴图飞出屏幕

**①根因**：显示缓存命中条件漏掉 partial 标记版本。缓存基于
`RecipeCraftingIndex.inventoryUnchanged()`（只 diff 菜单 slot，不含 carried），而
forEachRedirect 的 inventoryChanged 用 `slotHash(menu.slots, carried)`（含 carried）。
partial 重标常由 carried 驱动（拿起物品）→ 标记更新但缓存判"未变"→ 旧排序。
修复：`PartialCraftingUtil` 加单调 `markingVersion`（`beginFilteringUpdate(true)`
即真重标时 +1，暴露 getter），缓存命中条件加 `brbe$cacheMarkingVersion==markingVersion`。

**②根因**：`RecipeBookPageAnimationMixin.brbe$renderVisualSquashed` 选 sprite 用裸
`c.hasCraftable()`，未套静态路径 `@Redirect hasCraftable()||hasPartial`。语义分离后
残缺配方不进 craftable → 动画里选中 UNCRAFTABLE 暗红 sprite，再叠 renderPartialMark
红标 → 双重红。修复：`showAsCraftable = c.hasCraftable() || hasPartialMaterials(c)`，
残缺配方按 CRAFTABLE 亮 sprite + 单层红标，与静态一致。

**③根因**：pin 图标在 `disableScissor()` 之后按滑动后坐标（含 -125px 位移）绘制、
无网格裁剪 → 飞出屏幕。修复：pin blitSprite 移入 scissor 块内（enableScissor 与
disableScissor 之间），跟随滑动但被网格边界裁住。

**验证**：compileJava 通过、双端 build 成功、部署（备份 20260830-14xxxx，原子替换）；
javap 核验 markingVersion()/showAsCraftable 存在、pin blit 位于 enableScissor 与
disableScissor 之间。提交 77c0cf61。**待用户实测**。

## 2026-08-30（六）：动画 pin 悬出回归——扩边 scissor 修（已部署双端）

回归：上一轮把 pin 移进严格网格 scissor，裁掉 pin 超出格子的悬出。pin 32×32
锚 (x-4,y-4) 于 25×25 格，悬出左/上 4px、右/下 3px；静态路径 pins/RecipeButtonMixin
画 pin 无 scissor，悬出可见。修复：pin 用「网格外扩 PIN_OVERHANG(=4)」专属
scissor 绘制——保留悬出（与静态一致）且滑出时仍被裁（issue #3 不回归）。顺序：
内容 scissor(严格)→disable→pin scissor(扩边)→blitSprite→disable。提交 05493c33。

## 2026-09-11：ESC 语义对齐 1.21.11/26.2 + Unique Dark 包补 column_panel（已部署双端）

**①「查询窗口存在时 ESC 退不出界面」——1.21.1 同源那一半**

用户反馈（1.21.11/26.2）修复后，顺带核对 1.21.1：本分支 viewer 是轻量实现，
**没有** `brbe.queryviewers.json` 持久化与 `restorePendingViewers()` 恢复通道 →
不存在 1.21.11/26.2 的"关掉下一帧复活"死循环环节，但 **"ESC 被吞掉"完全同源**：

- 旧代码（`common/.../util/RecipeViewerOverlay.keyPressed`）：
  `if (keyCode == 256) return PinOverlayManager.handleEscape();`
- `PinOverlayManager.handleEscape()` = `isActive() && closeSilently()` → 窗口开着返回 true
  → `mixins/recipeviewer/KeyboardHandlerMixin`（`KeyboardHandler.keyPress` HEAD，
  `priority = 2000`）`ci.cancel()` → vanilla `Screen.keyPressed` 收不到 ESC
  → **有查询窗口时按一次 ESC 只关窗、界面不动**。
- 修复（与 1.21.11/26.2 语义一致）：
  ```java
  if (keyCode == 256) {
      closeSilently();   // 已打开的窗口随这次 ESC 一起关闭
      return false;      // 不消费：交回屏幕走原版 ESC
  }
  ```
  无需 `restoreSuppressedScreen` 抑制（本分支无恢复通道）；pin 永不因 ESC 关闭；
  配方书界面"第一次 ESC 收配方书"是原版行为，不改。
  副作用：`PinOverlayManager.handleEscape()` 在三个维护分支现在都**无调用者**（保留为公开 API）。
- 验证：`javap -p -c` 核对部署 jar 内 `RecipeViewerOverlay.class` 的 keyPressed ——
  ESC 分支已是 `sipush 256 / if_icmpne / invokestatic closeSilently / iconst_0 / ireturn`。
- 详细对照见 `docs/1.21.11-26.2-查询窗口ESC退出问题.md` §4。

**②Unique Dark - Lite 兼容包补深色 `column_panel`**

- 用户自制 `column_panel.png`（32×32 RGBA，md5 `4d02cc3d4e1efadaaef941401603230c`）
  复制进 `common/src/main/resources/resourcepacks/brbe_unique_dark/assets/brbe/textures/gui/sprites/recipe_book/`（逐字节一致）。
- **额外补 `column_panel_top.png`**：1.21.1 与 1.21.11/26.2 不同——本分支
  `drawStationColumnSurfaces` 在工作站列顶到框顶时切 `COLUMN_PANEL_TOP_SPRITE`
  （`rect[0] == boxY`），所以两张都要覆盖，否则该场景仍是浅灰。
  基础资源里 `_top` 与 `column_panel` 的**唯一差异**是右上角 5 像素
  （(29,0)(30,0) 高光→黑描边、(31,0) 填充→黑描边、(31,1)(31,2) 填充→高光），
  按同一关系在用户配色下机械派生（高光 `#6DA843`、填充 `#336B41`、描边 `#000000`），
  其余像素逐点不变（已脚本核验 diff=5 px）。
- 无需新增 `.mcmeta`：与基础图同为 32×32，基础包既有的
  `column_panel.png.mcmeta`（`nine_slice 32 border 4`）继续生效；包内其余 25 张覆盖贴图同样只放 PNG。
- 顺带修订 2026-08-27 轮次 18 的过时记录："1.21.1 无 column_panel 代码引用"已被
  2026-08-29 的浮层重写推翻（工作站列 9-slice）。

**部署**：`:common/:fabric/:neoforge compileJava` → `:fabric:build :neoforge:build -x test -x check`
全部通过；原子替换部署 1.21.1-Fabric（md5 `f7221e68229e3436e8703394001569be`）与
1.21.1-NeoForge（md5 `d45fcb195f37cec66642318b8bb29fb2`），备份 `20260911-174439`；
jar 内两张贴图 md5 与源文件一致，包内 `recipe_book` 覆盖贴图 25→27 张。
**待用户实测**：有查询窗口时一次 ESC 关闭窗口且界面照常退出；深色主题下工作站列（含顶到框顶的变体）为深绿。

## 2026-09-22：调试日志统一到 `-Dbrbe.debug` 开关 + 四个日志工具精简为一个

用户需求：日志输出改由一个 JVM 参数启用并精简日志工具，目标四分支（本分支**暂不部署**）。
26.3 为参照实现（见其 CLAUDE.md 同名轮次）。本分支代码已改并编译通过，**未部署**。

**四个日志类 → 一个出口**：
- `BrbeLogger`：用 26.3 版逐字节覆盖（md5 `57a1708a4829b52c991e4ff329c6bf16`）。
  API 变为 `isEnabled()` / `init(Path)` / `log(tag, "{}…", args)` / `log(tag, msg, Throwable)`；
  **删掉 `Category` 枚举**（原调用点改自由标签）、不再用 `String.format`。
- `RecipeBookDebugLogger`：保留类（6 个 RBIP 调用点），但 `enabled` 字段 → `enabled()`
  （= `BrbeLogger.isEnabled()`），18 处输出全部走 `BrbeLogger.log("BRBE-DEBUG", …)`，
  不再有自己的 logger；`verboseCollections` 子开关保留。
- `RecipeStateDiagnostic`：保留，`enabled()` = `BrbeLogger.isEnabled()`（本分支原本无属性、无条件跑），
  输出走 `BrbeLogger.log("BRBE-DIAG", …)`，删自带 logger。**调用点补了 `enabled()` 守卫**
  （原先每次物品栏刷新都对全部配方做一遍 QA 预测——这是本轮唯一的行为/性能改动）。
- `BrbeDiagnostic`：保留（显式 dump 工具，仍写 `brbe-diagnostic.log`），2 处 `LOGGER.info` 门控，
  3 个调用点未动。

**数字**：`BrbeLogger.log` **87** 处；保留 `LOGGER.warn/error` **28**；
残留 `LOGGER.info/debug` 0、裸 `System.out/err.print` 0、`brbe.diagnostics` 0、`%s/%d` 残留 0。

**其它**：`ConfigEventBus` 裸 `e.printStackTrace()` → `LOGGER.warn`；`JsonPinStore` 两处
`System.err` → `LOGGER.warn`；`PerfTimer` 整体跟随开关（此前每轮管线都往 latest.log 打分段时间——
若日后要"不开 debug 也测性能"需回退这一行 guard）；`MultiPlayerGameModeMixin` 里注释掉的
`//System.out.println` 删除；**`RecipeViewerFeatureFlag`（`brbe.disableRecipeViewer`）未动**——
它是行为开关不是日志埋点。

**编译**：`JAVA_HOME=/usr/lib/jvm/java-21-openjdk sh gradlew :common:compileJava :fabric:compileJava
:neoforge:compileJava` 三模块通过（并 `rm -rf */build/classes` 强制全量重编复核）；
`build -x test -x check` 亦通过。**按用户要求未部署任何 1.21.1 实例。**

## 2026-09-22：调试日志写入冲突修复（与 26.3 / 1.21.11 / 1.21.1 同步）

**现象**：`-Dbrbe.debug=true` 时内嵌的无头 JEI（mod `zheadlessjei`）在 `logs/brbe-debug.log`
里**一行都没有**，连它写的会话头 `--- headless-jei attached ---` 都不见。

**根因**：`BrbeLogger.init()` 用 `Files.newBufferedWriter(p, UTF_8)`（无 open option =
`CREATE+TRUNCATE_EXISTING+WRITE`，**没有 O_APPEND**）打开共写文件，写入走**自己的文件位置**；
无头 JEI 侧是 `CREATE+APPEND`（O_APPEND，写到真实末尾）。两者并存时，后者的每次写入都会把
前者追加在末尾的字节整段覆盖。最小复现：A(非追加) 写 2 行、B(追加) 写 1 行 → B 的行彻底消失。

**修复**：`BrbeLogger.init()` 改为**恒以 `CREATE+APPEND` 打开**；>4 MB 轮转改为**就地 truncate**
（`FileChannel.open(file, WRITE, TRUNCATE_EXISTING)`）——NIO 不允许 `APPEND+TRUNCATE_EXISTING`
同时给（`IllegalArgumentException`），而"删除再建"会让对方句柄悬在 unlink 的 inode 上。

**验证**：`tools/brbe-perf-probe/` 先删除日志文件（复现 fresh 场景）再跑——开启开关时
`brbe-debug.log` 含 fork 会话头与 fork 的收集/索引行；关闭开关时无该文件、`latest.log`
调试标签 0 行。完整诊断见根目录 `docs/brbe-debug-log-写入冲突诊断.md`。


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

## 2026-09-23（二）：两个开关默认改为关（四分支同步）

用户要求：「在生存模式配方书中显示3x3配方」（`showAllRecipesInSurvival`）与
「优化原版配方过滤器」（`partialCraftingEnabled`）的默认启用状态 → **关**。本分支
`common/src/main/java/com/alonie/brbe/config/BrbeConfig.java` 只改默认值 + 一行注释，
门控逻辑与 `@ConfigEntry` 注解（含 `showAllRecipesInSurvival` 上的 `PrefixText`）未动。

- `showAllRecipesInSurvival=false`：生存模式配方书不再放行 3×3 / 环境不兼容配方
  （物品栏 2×2 界面不含 3×3），残缺配方注入路径随之关闭 → 默认接近原版。
- `partialCraftingEnabled=false`：保留原版「仅显示可合成」按钮；本分支该开关仅影响
  `DisableCraftableFilter` 与管线 Stage 4 排序时机。
- 已有实例的 `brbe.toml` 保存旧值（1.21.1-Fabric `= true`、NeoForge 该键 `= false`），
  默认值只对新配置生效。

**构建（按规则不部署）**：fabric `6eab9d710cf7550d63dc5333ea2581b9`、
neoforge `a3087c0e998ebedc67f81e3576f06c17`；`javap -c` 核对两 jar 内
`BrbeConfig.<init>` 两字段均为 `iconst_0`（false）。


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
**分支构建（按规则只构建不部署）**：fabric `69440b0e63c8c1023393947978ab4bcd`、neoforge `3dd01f8adb29065d8be0a08400559e3e`（本分支只做签名修复；显示兜底早已由其 `RecipePipeline.applyVisibility` 实现）。

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

> 本分支只构建不部署：fabric `ec89a86f7a2d8c03dd279be0dc0d1c36` / neoforge `465ae140deaec2cac7b2e315f714ff9b`（只含 ③）。


## 2026-09-25（二）：滚轮接缝收敛 + 兼容自检 —— 本分支未改

> 架构改动未移植到本分支（滚轮只有单一入口、无第二个消费者，且无 mousewheelie 实例可验证）。


## 2026-09-25（三）：unlockAll 关闭 = 只显示进度已解锁配方 —— 本分支未移植

> 未移植到本分支（配方书为 `RecipeHolder`/`known: Set<ResourceLocation>` 的另一套模型，需单独实现）。


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
**提交**：`02037641`（1.21.1 分支）。

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

（本分支为旧 `GhostRecipe` 体系：取值点 = `recipesPage.hoveredButton.getCurrentDisplayedRecipe()`，`ghostRecipe.clear()` 同 1.21.11 语义。）

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
