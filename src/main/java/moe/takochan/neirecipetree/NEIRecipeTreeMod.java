package moe.takochan.neirecipetree;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

@Mod(
    modid = NEIRecipeTreeMod.MODID,
    version = Tags.VERSION,
    name = "NEI-RecipeTree",
    acceptedMinecraftVersions = "[1.7.10]",
    dependencies = "required-after:NotEnoughItems")
public class NEIRecipeTreeMod {

    public static final String MODID = "neirecipetree";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(
        clientSide = "moe.takochan.neirecipetree.ClientProxy",
        serverSide = "moe.takochan.neirecipetree.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }
}
