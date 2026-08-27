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
