package moe.takochan.neirecipetree.nei;

import net.minecraftforge.common.MinecraftForge;

import codechicken.nei.NEIClientConfig;
import codechicken.nei.api.API;
import codechicken.nei.api.IConfigureNEI;
import codechicken.nei.config.OptionToggleButton;
import moe.takochan.neirecipetree.NEIRecipeTreeMod;
import moe.takochan.neirecipetree.Tags;

public class NEIRecipeTreeConfig implements IConfigureNEI {

    private static final String TAG_BOOKMARK_EXPANDED = "inventory.recipetree.bookmarkExpanded";

    @Override
    public void loadConfig() {
        NEIRecipeTreeMod.LOG.info("NEI-RecipeTree plugin loading");
        MinecraftForge.EVENT_BUS.register(new RecipeTreeButtonHandler());

        NEIClientConfig.global.config.getTag(TAG_BOOKMARK_EXPANDED).getBooleanValue(true);
        API.addOption(new OptionToggleButton(TAG_BOOKMARK_EXPANDED, true));
    }

    public static boolean isBookmarkExpanded() {
        return NEIClientConfig.global.config.getTag(TAG_BOOKMARK_EXPANDED).getBooleanValue(true);
    }

    @Override
    public String getName() {
        return "NEI-RecipeTree";
    }

    @Override
    public String getVersion() {
        return Tags.VERSION;
    }
}
