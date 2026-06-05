# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Full build (all platforms)
./gradlew build

# Build specific platform
./gradlew :fabric:build          # Fabric only
./gradlew :neoforge:build        # NeoForge only

# Clean + build
./gradlew clean build

# Run in dev environment (IDE required)
./gradlew :fabric:runClient      # Fabric dev env
./gradlew :neoforge:runClient    # NeoForge dev env

# Generate sources jar
./gradlew sourcesJar
```

**Prerequisites:** Java 21, Fabric API / NeoForge / Architectury / Cloth Config JARs in `~/Downloads/1.21.1/` (paths are hardcoded in build.gradle files). Set `jei_fabric_jar`, `jei_neoforge_jar`, `rei_fabric_jar`, `rei_neoforge_jar` gradle properties to override.

## Project Architecture

**Multi-loader Minecraft mod** (Fabric + NeoForge) via Architectury Loom. Minecraft 1.21.1, Java 21.

### Module Layout

```
common/      — All gameplay logic, GUI, mixins, assets, translations
fabric/      — Fabric entrypoints, JEI plugin, REI handler, Fabric-specific accessors
neoforge/    — NeoForge entrypoint @Mod, JEI plugin, NeoForge-specific accessors
```

`common` is compiled into both platform jars. The root `build.gradle` orchestrates via architectury-plugin.

### Entry Points

| Platform | Main Init | Client Init |
|----------|-----------|-------------|
| Fabric | `BetterRecipeBookFabric` (calls `BetterRecipeBook.init()`) | `BetterRecipeBookClientFabric` (registers REI handler) |
| NeoForge | `BetterRecipeBookNeoForge` (`@Mod` constructor calls `BetterRecipeBook.init()`) | — |

`BetterRecipeBook.init()` is the common entry: registers config (Cloth Config AutoConfig), loads potions, registers REI compat, keybindings, and loads pinned recipes.

### The Generic Recipe Book Framework

The core design pattern is an **abstract generic recipe book framework** in `generic/` with **concrete per-block-type implementations**. This avoids duplicating the entire recipe book UI for each block.

**Base classes** (`common/…/generic/`):
- `GenericRecipeBookComponent<M, C, R>` — abstract recipe book widget; manages tabs, search, filter, ghost recipe, pinning. Extends vanilla `Renderable`, `NarratableEntry`, `GuiEventListener`.
- `GenericRecipePage<M, C, R>` — paginated display of recipe buttons, forward/back navigation, overlay display.
- `GenericRecipeButton<C, R, M>` — individual clickable recipe slot in the book.
- `GenericRecipeCollection<R, M>` — a group of related recipes (e.g. all planks-from-log variants).
- `GenericRecipe` — wrapper around a single recipe result.
- `GenericGhostRecipe<R>` — ghost/overlay showing missing ingredients.
- `GenericClientRecipeBook` — client-side recipe book state.
- `BRBGroupButtonWidget` — category tab button (search, transform, trim, etc.).

**Concrete implementations** — each inherits from the generic base:
- `brewingstand/` — `BrewingRecipeBookComponent`, `BrewableRecipeButton`, `BrewableResult`, `BrewingRecipeCollection`
- `smithingtable/` — `SmithingRecipeBookComponent`, `SmithingRecipeBookPage`, `SmithingRecipeCollection`, `SmithingGhostRecipe`, `SmithingOverlayRecipeComponent`
- `recipe/` — `BRBSmithingRecipe`, `BRBSmithingTransformRecipe`, `BRBSmithingTrimRecipe` (wraps vanilla smithing recipes)

To add a new recipe book type, extend `GenericRecipeBookComponent` and implement:
- `getRecipeFilterName()` — tooltip for filter toggle
- `getRecipeBookType()` — returns `BRBHelper.Book` identity
- `handlePlaceRecipe()` — click-to-place logic (auto-fill ingredients)
- `getCollectionsForCategory()` — returns recipes for the selected tab category

### Mixin Organization

Mixins are in `common/…/mixins/`, grouped by feature package:

| Package | Purpose |
|---------|---------|
| `pins/` | Recipe pinning (F key to pin/unpin) |
| `instantcraft/` | One-click craft + auto shift-click result |
| `unlockrecipes/` | "View locked recipes" feature |
| `alternativerecipes/` | Alternative recipe display in overlay |
| `scrollablepages/` | Mouse scroll support in recipe pages |
| `centered/` | Keep crafting screens centered |
| `settings/` | Settings button in recipe book |
| `ungroup/` | Un-group alternative recipes |
| `rei/` | REI compat integration |
| `modname/` | Show item source mod name |
| `toasts/` | Suppress unlock toasts/sounds |
| `accessors/` | Public accessors for private fields (e.g. `RecipeBookComponentAccessor`, `BrewingStandMenuAccessor`) |
| Root (`SmithingScreenMixin`, `BrewingStandScreenMixin`, etc.) | Screen-level mixins |

Mixins are registered in `mixins.brbe-common.json` (common, required), `mixins.brbe.json` (platform-specific accessors), and `mixins.brbe-common-compat.json` (optional conditional compat mixins via `CompatMixinPlugin`). Each platform jar includes all three configs.

### Compat System

**JEI:** Common `JeiCompat` defines a handler interface with `openRecipeView`/`openUsageView`. Platform-specific plugins (`fabric/…/jei/BetterRecipeBookJEIPlugin`, `neoforge/…/jei/BetterRecipeBookJEIPlugin`) call `JeiCompat.setHandler()`. NeoForge JEI plugin is conditionally excluded from compilation if only the Fabric JEI jar is found.

**REI:** Common `ReiCompat` uses reflection to open recipe/usage views (avoids compile-time dependency). Called during `BetterRecipeBook.init()`.

**ModMenu:** Fabric only — `ModMenuFabric` integrates with Cloth Config's AutoConfig screen.

**MouseWheelie:** Conditional mixin (`MixinMWClient`) gated by `CompatMixinPlugin.shouldApplyMixin()` checking for `Platform.isModLoaded("mousewheelie")`. Registered in `mixins.brbe-common-compat.json` (required: false).

## Key Features

- **Pinning** — `PinnedRecipeManager` persists pinned recipe IDs to `<gamedir>/brbe.pins` (JSON). Keybinding: F (default).
- **Instant Crafting** — `InstantCraftingManager` tracks last crafted result and auto-shift-clicks the output slot. Toggle via config/button.
- **Potion System** — `PotionLoader` loads `PotionBrewing.Mix<Potion>` entries on `CLIENT_LEVEL_LOAD` via Architectury event. `PlatformPotionUtil` bridges Fabric/NeoForge potion brewing differences (implemented per-platform as `PlatformPotionUtilImpl`).
- **Config** — Cloth Config AutoConfig with TOML serialization. Categories: General, New Recipes, Instant Craft, Alternative Recipes, Scrolling.

## Resource Locations

- Mod ID: `brbe`
- Custom sprites: `brbe:recipe_book/…` (pin, settings, instant craft buttons, overlays)
- Access widener: `common/src/main/resources/brbe.common.accesswidener` (currently opens `PotionBrewing$Mix`)
- Translations: 7 languages in `common/src/main/resources/assets/brbe/lang/`
