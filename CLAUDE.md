# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Multi-branch architecture

Each git branch targets a **different Minecraft version** and is built independently:

| Branch    | Minecraft | Java | Loom                         | Mod Loaders      |
|-----------|-----------|------|------------------------------|-------------------|
| `1.21.1`  | 1.21.1    | 21   | Architectury Loom            | Fabric + NeoForge |
| `1.21.11` | 1.21.11   | 21   | Architectury Loom            | Fabric + NeoForge |
| `26.1.2`  | 26.1.2    | 25   | Loom (`loom-no-remap`)       | Fabric + NeoForge |
| `26.2`    | 26.2      | 25   | Loom (`loom-no-remap`)       | Fabric + NeoForge |

**The root `build.gradle` validates `minecraft_version` against the branch name at configure time** — it will fail with a clear error if they differ.

## Module layout

```
common/          ← shared across Fabric + NeoForge
  api/           ←   public interfaces (HudHider, ConfigScreenProvider)
  impl/hud/      ←   HudHider implementations (JeiHudHider, ReiHudHider)
  compat/        ←   cross-mod bridges (OverlayHider, JeiCompat, ReiCompat)
  compat/mixins/ ←   compat mixins with conditional loading (CompatMixinPlugin)
  mixins/        ←   grouped by feature, one directory per concern (see below)
  search/        ←   advanced search DSL (10 classes: @mod, $tag, #tooltip, regex)
  generic/       ←   recipe book implementation for all station types
    pins/        ←     Pinnable interface for pinning support
  brewingstand/  ←   brewing stand recipe book extension
  smithingtable/ ←   smithing table recipe book extension
  cache/         ←   client-side recipe caching (VanillaRecipeCache)
  config/        ←   Cloth Config categories (Config, AlternativeRecipes, etc.)
  recipe/        ←   custom recipe types (smithing transform/trim)
  recipebookispain_extended/  ← RBIP — merged source code, own mixin configs
  widget/        ←   custom widgets (StateSwitchingButton)
  interfaces/    ←   mixin target interfaces (IPinningComponent, etc.)
fabric/          ← Fabric-specific entry points + JEI plugin + Fabric-only mixins
neoforge/        ← NeoForge-specific entry points + platform init + NeoForge-only mixins
```

### Mixin organization (by feature)

Mixin directories under `common/.../mixins/` are organized by feature — every directory containing at least one Mixin class corresponds to a distinct behavior change:

| Directory | Feature |
|-----------|---------|
| `centered/` | Keep recipe book centered on screen |
| `hideoverlay/` | Hide JEI/REI bookmarks & ingredient list overlays |
| `incompatibleenvironment/` | Hide recipes that can't be crafted in the current dimension |
| `incompletecrafting/` | Show partially-craftable recipes (partial materials) |
| `instantcraft/` | Shift-click instant crafting |
| `localcache/` | Client-side recipe book caching |
| `modname/` | Display mod source name on recipe buttons |
| `pins/` | Recipe pinning system |
| `pipeline/` | Recipe collection pipeline (tagging, ordering, filtering) |
| `scrollablepages/` | Scrolling confined to recipe book area |
| `search/` | Search box integration with the search DSL |
| `settings/` | Settings button in recipe book |
| `toasts/` | Suppress unlock toasts and sounds |
| `ungroup/` | Ungroup recipe variants (alternative recipes) |
| `unlockrecipes/` | Auto-unlock all recipes / craft-one-to-unlock bypass |
| `jei/` | JEI-specific compatibility (REI package uses same name for loading) |
| `alternativerecipes/` | Alternative recipe overlay layout |
| `accessors/` | Mixin accessors — expose private fields/methods |

## Mixin configuration architecture

There are **8 mixin JSON configs** spanning two loaders, with different loading strategies:

```
COMMON (shared, loaded by both fabric.mod.json and neoforge.mods.toml):
  mixins.brbe-common.json         required:true  — 50+ mixins, the core
  mixins.brbe-common-compat.json  required:false — uses CompatMixinPlugin (conditional on mod presence)
  mixins.brbe-jei-common.json     required:false, defaultRequire:0 — JEI overlay hiding (common)
  mixins.brbe-rei-common.json     required:false, defaultRequire:0 — REI overlay hiding (common)
  recipe-book-is-pain-extended.mixins.json — RBIP for Fabric
  rbip-neoforge.mixins.json                 — RBIP for NeoForge

FABRIC-ONLY:
  mixins.brbe.json                required:true  — FabricPotionBrewingAccessor
  mixins.brbe-jei.json            required:false — JeiBookmarkOverlayMixin, JeiIngredientListOverlayMixin

NEOFORGE-ONLY:
  mixins.brbe.json                required:true  — NeoForgePotionBrewingAccessor
```

**Key rules:**
- `required:false` + `defaultRequire:0` means mixins are skipped unless the target class loads (JEI/REI soft-dep pattern)
- `CompatMixinPlugin` conditionally enables mixin classes based on which mods are loaded at runtime — check its `preApply()` before adding new compat mixins
- Fabric loads 6 configs (`fabric.mod.json` mixins array), NeoForge loads 6 (`neoforge.mods.toml` `[[mixins]]` entries)
- NeoForge RBIP uses a separate config (`rbip-neoforge.mixins.json`) because it targets different classes than the Fabric version

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

## Search DSL

The recipe book supports an advanced search syntax parsed by `SearchQuery`:

| Syntax | Meaning | Example |
|--------|---------|---------|
| `word` | Substring match on item name | `sword` |
| `"two words"` | Quoted phrase (literal spaces) | `"golden apple"` |
| `@text` | Mod namespace or display name | `@minecraft` |
| `$text` | Item tag identifier | `$logs` |
| `#text` | Tooltip text search | `#fire` |
| `r/regex/` | Regex on item hover name | `r/^Iron/` |
| `-` prefix | Negate any argument | `-@minecraft` |
| `\|` | OR separator between groups | `sword \| axe` |
| Space | AND within a group | `iron sword` |

`SearchCache` memoizes per-item matches to avoid repeated tooltip/index lookups.

## Generic recipe book

The `generic/` package provides a complete recipe book implementation that can be embedded into any screen type:

- `GenericClientRecipeBook` — owns the recipe book state per-client
- `GenericRecipeBookComponent` — the UI widget (tabs, pages, search, scroll)
- `GenericRecipeBookCollection` / `GenericRecipe` / `GenericRecipeButton` — data model
- `GenericRecipePage` / `BRBGroupButtonWidget` — pagination and grouping

This generic book powers the vanilla crafting screen **and** is extended by the brewing stand and smithing table modules.

## Brewing & Smithing extensions

**Brewing stand** (`brewingstand/`): `BrewingRecipeBookComponent` extends `GenericRecipeBookComponent` to show potion recipes (potion, splash, lingering) in the brewing stand screen. Uses `PlatformPotionUtil` (interface) with per-loader `PlatformPotionUtilImpl` to load brewing recipes — this is the platform-abstraction pattern.

**Smithing table** (`smithingtable/`): `SmithingRecipeBookComponent` + `SmithingRecipeBookPage` handle transform and trim recipes. Custom recipe types in `recipe/smithing/` (`BRBSmithingTransformRecipe`, `BRBSmithingTrimRecipe`).

Both are wired in via their respective `*ScreenMixin` classes (`BrewingStandScreenMixin`, `SmithingScreenMixin`).

## Access widener / transformer

- **common**: `brbe.common.accesswidener` — widens access for mixin targets shared across loaders
- **NeoForge**: `META-INF/accesstransformer.cfg` — NeoForge-specific access transformations

Both must be kept in sync when adding new mixin targets that need private/protected access.

## Build commands

```bash
./gradlew build                    # full build (common + fabric + neoforge)
./gradlew :common:compileJava      # compile-only check

# Cache corruption recovery (after branch switches)
./gradlew cleanLoomCache && rm -rf .gradle && ./gradlew build

# Deploy (build JAR → copy to test instance)
cp fabric/build/libs/BetterRecipeBookExtended-fabric-26.1.2-2.1.4b.jar /media/…/26.1.2-Fabric/mods/
cp neoforge/build/libs/BetterRecipeBookExtended-neoforge-26.1.2-2.1.4b.jar /media/…/26.1.2-NeoForge/mods/
```

Test instance paths follow the pattern `/media/avalonia/data/MinecraftLib/versions/<version>-<loader>/mods/`.

## Key mappings

| Key | Binding | Action |
|-----|---------|--------|
| F | `key.brbe.pin` | Pin/unpin a recipe |
| R | `key.brbe.recipeView` | Show recipe in JEI/REI |
| U | `key.brbe.usageView` | Show usages in JEI/REI |

Key mappings are registered by platform entry points (not in `BetterRecipeBook.init()`).

## Config features and their gates

Config is managed by Cloth Config AutoConfig, serialized to `brbe.toml`. Categories: `ui`, `recipeFilter`, `rbip`, `newRecipes`, `instantCraft`, `alternativeRecipes`, `scrolling`.

| Config field | Effect | Gate location |
|-------------|--------|---------------|
| `hideReiJeiOverlay` | Hides JEI/REI overlays | `OverlayHider.setOverlaysHidden()` → iterates `HudHider` registry |
| `showAllRecipesInSurvival` | When **false**, skips ALL partial-material injection (vanilla-only) | `RecipeBookComponentMixin.keepPartiallyCraftable` |
| `partialCraftingEnabled` | Show recipes with partial materials available | `incompletecrafting/` mixins |
| `partialMarkingEnabled` | Visually mark items that are partially available | `incompletecrafting/` mixins |
| `enableRecipeBookIsPain` | Enables RBIP creative-mode tabs in recipe book | Hidden from GUI (`@ConfigEntry.Gui.Excluded`), edited in `brbe.toml`, hot-reloaded via `reloadIfChanged()` |
| `enablePinning` | Pin recipes | `PinnedRecipeManager` |
| `instantCraft.enabled` | Shift-click instant craft | `InstantCraftingManager` |
| `alternativeRecipes.noGrouped` | Ungroup recipe variants | `ungroup/RecipeBookComponentMixin` |
| `keepCentered` | Keep recipe book centered | `centered/RecipeBookComponentMixin` |
| `showModName` | Display mod source on recipe buttons | `modname/RecipeButtonMixin` |
| `settingsButton` | Show settings button in recipe book | `settings/RecipeBookComponentMixin` |
| `enableBook` | Enable/disable the recipe book entirely | `DisableBook` mixin |
| `scrolling.*` | Scroll behavior configuration | `MouseScrollHandler`, `scrollablepages/` |
| `newRecipes.unlockAll` | Auto-unlock all recipes | `unlockrecipes/` mixins |

## RBIP (Recipe Book is Pain) module

- Source-merged into `common/.../recipebookispain_extended/` — **not** a jar-in-jar dependency
- Own mixin configs: `recipe-book-is-pain-extended.mixins.json` (Fabric), `rbip-neoforge.mixins.json` (NeoForge)
- Platform init: NeoForge → `BetterRecipeBookClientNeoForge.init()`, Fabric → `RBIPFabricEntrypoint`
- Config bridged through `RecipeBookIsPainExtendedConfig.enabled()` → reads `brbe.toml [rbip]`
- If `enableRecipeBookIsPain` is off, RBIP is a no-op (constructor saves `vanillaTabInfos`, all methods guard on `enabled()`)

## HudHider API (refactored from OverlayHider)

`OverlayHider` is now a thin registry. New implementations implement `api/hud/HudHider`:

```java
OverlayHider.register(new JeiHudHider());  // JEI IClientToggleState bridge
OverlayHider.register(new ReiHudHider());  // REI ConfigObject bridge
```

Each hider owns its own state (snapshot, guard flags). Adding a new HUD mod only requires implementing the interface + one registration call.

### NeoForge startup race condition

On NeoForge, JEI/REI may boot in an inconsistent visibility state. `OverlayHider.forceShowOverlays()` (called from `BetterRecipeBookClientNeoForge.init()` in `CLIENT_STARTED`) unconditionally forces both overlays visible via reflection, bypassing the `currentlyHidden` guard. This runs once at startup, then the persisted config takes over.

## TopLayerOverlayRenderer

`TopLayerOverlayRenderer` renders BRBE's custom overlay (pin indicators, instant-craft highlights, settings button borders) **on top of** JEI/REI overlays. It hooks into the screen render pipeline via `ScreenEvents.afterExtract()` (Fabric) or `ClientGuiEvent.RENDER_POST` (NeoForge), ensuring BRBE widgets are always the top-most rendered layer.

## Platform abstraction pattern

When a feature needs loader-specific behavior, the pattern is:
1. Define an interface in `common/` (e.g., `PlatformPotionUtil`)
2. Implement in `fabric/.../fabric/PlatformPotionUtilImpl` and `neoforge/.../neoforge/PlatformPotionUtilImpl`
3. Register via `init()` call from the platform entry point

## libs/ directory

Contains stub JARs (`jei-*.jar`, `RoughlyEnoughItems-*.jar`) used as **compile-only** references. These are not bundled into the mod — the `compat/` system uses reflection to bridge JEI/REI at runtime.

## No automated test suite

There is no JUnit or integration test harness. Validation is done by launching the relevant loader client (`./gradlew :fabric:runClient` or `./gradlew :neoforge:runClient`) and verifying behavior in-game. When changing mixins, test both Fabric and NeoForge.
