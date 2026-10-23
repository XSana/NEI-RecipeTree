package moe.takochan.neirecipetree.nei;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import codechicken.nei.recipe.GuiRecipeButton;
import codechicken.nei.recipe.GuiRecipeButton.UpdateRecipeButtonsEvent;
import codechicken.nei.recipe.RecipeHandlerRef;

class RecipeTreeButtonHandlerTest {

    @Test
    void ignoresEventsWithoutARecipe() {
        UpdateRecipeButtonsEvent.Post event = new UpdateRecipeButtonsEvent.Post(
            null,
            null,
            160,
            100,
            Collections.emptyList());

        new RecipeTreeButtonHandler().onRecipeButtons(event);

        assertTrue(event.buttonList.isEmpty());
    }

    @Test
    void usesRecipeAndDimensionsFromTheEvent() {
        // No recipe handler methods should be needed just to add the tree button.
        RecipeHandlerRef ref = RecipeHandlerRef.of(null, 7);
        UpdateRecipeButtonsEvent.Post event = new UpdateRecipeButtonsEvent.Post(
            null,
            ref,
            150,
            100,
            Collections.emptyList());

        new RecipeTreeButtonHandler().onRecipeButtons(event);

        assertEquals(1, event.buttonList.size());
        GuiRecipeButton button = event.buttonList.get(0);
        assertInstanceOf(RecipeTreeButton.class, button);
        assertSame(ref, button.handlerRef);
        assertEquals(138, button.xPosition);
        assertEquals(82, button.yPosition);
    }

    @Test
    void preservesExistingButtonsAndCapsTheHorizontalPosition() {
        RecipeHandlerRef ref = RecipeHandlerRef.of(null, 7);
        GuiRecipeButton existing = new RecipeTreeButton(ref, 156, 82);
        UpdateRecipeButtonsEvent.Post event = new UpdateRecipeButtonsEvent.Post(
            null,
            ref,
            200,
            100,
            Collections.singletonList(existing));

        new RecipeTreeButtonHandler().onRecipeButtons(event);

        assertEquals(2, event.buttonList.size());
        assertSame(existing, event.buttonList.get(0));
        GuiRecipeButton button = event.buttonList.get(1);
        assertSame(ref, button.handlerRef);
        assertEquals(156, button.xPosition);
        assertEquals(69, button.yPosition);
    }
}
