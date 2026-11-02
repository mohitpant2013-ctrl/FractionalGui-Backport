package fyredeft.fracgui;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod("fracgui")
public class FracGuiMod {
    public FracGuiMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, FracConfig.SPEC);
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ClientHandler::init);
    }
}
