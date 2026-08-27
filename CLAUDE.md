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
