package dev.mcpmcp.forge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.client.ConfigScreenHandler;
@Mod("mcpmcp")
public final class Mcpmcp {
 public Mcpmcp(){DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->Runtime::initialize);DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->net.minecraftforge.fml.ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,()->new ConfigScreenHandler.ConfigScreenFactory((mc,parent)->new SettingsScreen(parent))));}
}
