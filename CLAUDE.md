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
| `showModName` | Display source mod name in tooltip | `modname/RecipeButtonMixin` |

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
cp fabric/build/libs/BetterRecipeBookExtended-fabric-1.21.1-2.1.5.jar /media/…/1.21.1-Fabric/mods/
cp neoforge/build/libs/BetterRecipeBookExtended-neoforge-1.21.1-2.1.5.jar /media/…/1.21.1-NeoForge/mods/
```

Test instance paths follow the pattern `/home/avalonia/data/MinecraftLib/versions/<version>-<loader>/mods/`.

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
