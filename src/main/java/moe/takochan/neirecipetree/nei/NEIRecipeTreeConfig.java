package moe.takochan.neirecipetree.nei;

import net.minecraftforge.common.MinecraftForge;

import codechicken.nei.api.IConfigureNEI;
import moe.takochan.neirecipetree.NEIRecipeTreeMod;
import moe.takochan.neirecipetree.Tags;

public class NEIRecipeTreeConfig implements IConfigureNEI {

    @Override
    public void loadConfig() {
        NEIRecipeTreeMod.LOG.info("NEI-RecipeTree plugin loading");
        MinecraftForge.EVENT_BUS.register(new RecipeTreeButtonHandler());
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
