# NEI 2.8.155 compatibility

This branch targets Minecraft 1.7.10 and NotEnoughItems 2.8.155-GTNH,
the NEI version reported by the affected GTNH 2.9.0 RC2 client.
It does not retain compatibility with NEI 2.8.77-GTNH.

## Cause and fix

Opening any NEI recipe screen invokes `RecipeTreeButtonHandler.onRecipeButtons`.
The old implementation reads `UpdateRecipeButtonsEvent.recipeWidget`, which no
longer exists in NEI 2.8.155. This raises `NoSuchFieldError` before a user can
click the recipe-tree button.

The handler now uses the event's `handlerRef`, `width`, and `height` directly.
The compile dependency is pinned to the affected NEI version so the compiler
checks the integration against that API.

## Build and automated checks

```sh
./gradlew spotlessApply
./gradlew build
```

`RecipeTreeButtonHandlerTest` constructs real NEI 2.8.155 events and checks that
the handler ignores missing recipes, preserves the recipe reference, positions
buttons using the event dimensions, and leaves existing buttons intact.

## Installation and in-game verification

1. Close Minecraft and move the existing `neirecipetree-0.0.5.jar` (including
   copies with a leading underscore) out of the instance's `mods` directory.
2. Copy the rebuilt runtime JAR from `build/libs` into `mods`. Do not install
   the `-dev` or `-sources` JAR, or keep two RecipeTree JARs in `mods`.
3. Keep the pack's `NotEnoughItems-2.8.155-GTNH.jar` installed.
4. Open crafting and machine recipes from NEI. Verify that the recipe page
   stays open and the tree button appears beside the existing recipe buttons.
5. Open a tree, expand a material, select an alternative recipe, and return to
   the tree. Check recipe previews and bookmark export as well.

Compilation and unit tests do not replace this in-game check; the full GTNH
client and its rendering context are not launched by the regression tests.
