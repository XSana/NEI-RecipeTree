package moe.takochan.neirecipetree;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        NEIRecipeTreeMod.LOG.info("NEI-RecipeTree v" + Tags.VERSION + " loading");
    }

    public void init(FMLInitializationEvent event) {}
}
