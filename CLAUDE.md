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
