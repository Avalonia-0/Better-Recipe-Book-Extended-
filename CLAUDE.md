# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Multi-branch architecture

Each git branch targets a **different Minecraft version** and is built independently:

| Branch    | Minecraft | Java | Loom                              | Mod Loaders      |
|-----------|-----------|------|-----------------------------------|------------------|
| `1.21.1`  | 1.21.1    | 21   | Architectury Loom                 | Fabric + NeoForge |
| `1.21.11` | 1.21.11   | 21   | Architectury Loom                 | Fabric + NeoForge |
| `26.2`    | 26.2      | 25   | Architectury Loom (`loom-no-remap`) | Fabric + NeoForge |

**The root `build.gradle` validates `minecraft_version` against the branch name at configure time** — it will fail with a clear error if they differ. After switching branches, always run `git checkout -- gradle.properties` to restore the correct version.

## Module layout

```
common/                               ← shared across Fabric + NeoForge
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
  interfaces/                         ←   cross-cutting interfaces (IPinningComponent, ISettingsButton, TopLayerOverlayProvider, RecipeBookTabButtonIconOffset)
  recipe/                             ←   custom recipe wrappers (BRBSmithingRecipe, etc.)
    smithing/                         ←     smithing transform/trim recipes
  util/                               ←   utility classes (BRBHelper, BRBTextures, ModNameUtil, PartialCraftingUtil, RecipeUnlockUtil, TopLayerOverlayRenderer, etc.)
  widget/                             ←   custom widgets (StateSwitchingButton)
  loaders/                            ←   PotionLoader (registers potion recipes from data packs)
  recipebookispain_extended/          ←   RBIP module — source-merged into common (not a jar-in-jar)
    mixin/                            ←     RBIP mixins (widget/, screen/, groups/, ItemMixin, MouseMixin)
    compat/polymer/                   ←     Polymer integration
    fabric/                           ←     RBIP Fabric entrypoint
    neoforge/                         ←     RBIP NeoForge platform impl (in neoforge module)

fabric/                               ← Fabric entrypoints + platform init
  main/java/.../fabric/               ←   BetterRecipeBookFabric, BetterRecipeBookClientFabric
    ModMenuReflectiveBridge           ←   reflection-based ModMenu integration
    Mixins/Accessors/                 ←   Fabric-specific accessor mixins

neoforge/                             ← NeoForge entrypoints + platform init
  main/java/.../neoforge/             ←   BetterRecipeBookNeoForge, BetterRecipeBookClientNeoForge
    Mixins/Accessors/                 ←   NeoForge-specific accessor mixins
```

## Core architecture patterns

### No Architectury API dependency
Both the common and platform modules avoid depending on Architectury API at compile time. The `architectury-plugin` is used only for its `loom-no-remap` Loom fork (which provides Mojmap with no remapping needed). Platform-specific code uses native Fabric API or NeoForge event bus directly.

### Mixin configuration split
There are **five** mixin config files, loaded differently per platform:
- `mixins.brbe.json` — platform-mixin configs (Fabric's `FabricPotionBrewingAccessor`, NeoForge's `NeoForgePotionBrewingAccessor`). Each platform module has its own copy.
- `mixins.brbe-common.json` — all cross-cutting BRBE mixins (required: true), loaded by both platforms.
- `mixins.brbe-common-compat.json` — conditional compat mixins (required: false, with `CompatMixinPlugin` that checks FabricLoader.isModLoaded). Current compat: mousewheelie.
- `recipe-book-is-pain-extended.mixins.json` — RBIP mixins used by Fabric.
- `rbip-neoforge.mixins.json` — RBIP mixins used by NeoForge (identical content, different filename for distinct config).

### NeoForge shadow jar packaging
The NeoForge build uses `com.gradleup.shadow` to bundle the `:common` module classes into the NeoForge JAR. The `jar` task depends on `shadowJar` and extracts from it. This is because `loom-no-remap` does not provide `namedElements` / `transformProduction*` that normally merge multi-project outputs.

### Config system
Uses **Cloth Config** (`me.shedaniel.autoconfig`) with TOML serialization. Config is gated by runtime availability — the `AutoConfig.register()` call is wrapped in try-catch. The config POJO lives at `com.alonie.brbe.config.Config` with nested sub-configs for feature groups (AlternativeRecipes, InstantCraft, Scrolling, NewRecipes, RecipeBookIsPain).

### Search query system (`com.alonie.brbe.search`)
Implements a mini query language with `|` (OR), space (AND), `-` (negation), `@mod` (mod search), `$tag` (tag search), `#tooltip` (tooltip search), `r/regex/` (regex), and quoted strings. `SearchQuery.parse()` builds an `AlternativeArgument` tree of `SearchArgument` nodes.

### Brewing & Smithing recipe books
The mod adds **non-vanilla** recipe book screens for brewing stands and smithing tables. Each has its own component/collection/recipe classes under `brewingstand/` and `smithingtable/`. These are separate from the generic recipe book base classes.

### Platform potion utilities
Potion brewing is platform-dependent (`PotionBrewing.Mix` is package-private). Each platform implements `PlatformPotionUtil` via reflection-based accessors in `PlatformPotionUtilImpl`.

## Build commands

```bash
./gradlew build                       # full build (common + fabric + neoforge)
./gradlew :common:compileJava         # compile-only check
./gradlew :fabric:runClient           # launch Fabric dev client
./gradlew :neoforge:runClient         # launch NeoForge dev client
./gradlew clean build                 # full clean rebuild

# Cache corruption recovery (after branch switches)
./gradlew cleanLoomCache && rm -rf .gradle && ./gradlew build

# Deploy (build JAR → copy to test instance)
# Fabric:
cp fabric/build/libs/brbe-ava-fabric-26.2-2.2.1.jar /home/avalonia/data/MinecraftLib/versions/26.2-Fabric/mods/
# NeoForge:
cp neoforge/build/libs/brbe-ava-neoforge-26.2-2.2.1.jar /home/avalonia/data/MinecraftLib/versions/26.2-NeoForge/mods/
```

Test instance path rule: `/home/avalonia/data/MinecraftLib/versions/{GAME_VERSION}-{MOD_LOADER}/mods/` (`MOD_LOADER` capitalized: `Fabric`/`NeoForge`). 构建完必须部署；部署前将实例内同版本 JAR 备份为 `*.jar.bak.YYYYMMDD`。

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

## RBIP (Recipe Book is Pain) module

- Source-merged into `common/.../recipebookispain_extended/` — **not** a jar-in-jar dependency. Uses its own package (`com.alonie.recipebookispain_extended`).
- Own mixin configs: `recipe-book-is-pain-extended.mixins.json` (Fabric), `rbip-neoforge.mixins.json` (NeoForge)
- Platform init: NeoForge → `BetterRecipeBookClientNeoForge.init()`, Fabric → `RBIPFabricEntrypoint`
- Config bridged through `RecipeBookIsPainExtendedConfig.enabled()` → reads `brbe.toml [rbip]`
- KeyMappings and key events: Fabric → `RBIPFabricEntrypoint`, NeoForge → `NeoForgePlatform` (registered on NeoForge event bus)
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

- **`loom-no-remap` means Mojmap-only.** Intermediary-based mods (like ModMenu) cannot be directly included as compile dependencies. ModMenuFabric integration is done reflectively via `ModMenuReflectiveBridge`.
- **JEI is not yet available for 26.2** — JEI-related mixins still compile but the jar dependency is commented out.
- **Cloth Config for 26.2 is bundled as a separate mod** (not jar-in-jar). The config registration is wrapped in try-catch; if Cloth Config is absent, the mod still runs with default values.
- **No test suite.** Validation is manual via `runClient` tasks or deploying to a test instance.
- **Pinned recipes are stored in a JSON file** (`brbe.pins` in the game directory), not in NBT or config.
- **BrewingRecipeBookComponent and SmithingRecipeBookComponent** are concrete implementations that sit alongside (not as subclasses of) GenericRecipeBookComponent — they share some interfaces but have their own rendering and event handling.
