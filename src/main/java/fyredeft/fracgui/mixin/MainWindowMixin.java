package fyredeft.fracgui.mixin;

import fyredeft.fracgui.FracConfig;
import net.minecraft.client.MainWindow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// priority > 1000: applied after other mods' mixins on this method, so the chosen scale is the last word.
@Mixin(value = MainWindow.class, priority = 1500)
public class MainWindowMixin {
    /**
     * Every caller of setGuiScale (vanilla's updateWindowSize and any mod that sets the scale
     * itself) passes through here. Never throws: on any error the original value is returned
     * and the mod drops into safe mode. require = 0 + "required": false means a failed
     * injection (e.g. another mod rewrote the method) logs a warning instead of crashing;
     * ClientHandler then switches to its tick-based fallback.
     */
    @ModifyVariable(method = "setGuiScale", at = @At("HEAD"), argsOnly = true, require = 0)
    private double fracgui$override(double original) {
        try {
            FracConfig.mixinActive = true;
            return FracConfig.resolve((MainWindow) (Object) this, original);
        } catch (Throwable t) {
            FracConfig.fail("setGuiScale", t);
            return original;
        }
    }
}
