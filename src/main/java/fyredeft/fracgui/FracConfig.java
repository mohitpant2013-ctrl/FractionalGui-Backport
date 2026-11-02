package fyredeft.fracgui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MainWindow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FracConfig {
    public static final Logger LOGGER = LogManager.getLogger("fracgui");
    public static final double MIN = 0.5D, MAX = 8.0D, STEP = 0.05D;
    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.DoubleValue SCALE;
    private static final ForgeConfigSpec.IntValue SMART_HEIGHT;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> EXCLUDED;

    /** True while an excluded screen is open: the vanilla integer scale is used instead. */
    public static volatile boolean forceInteger = false;
    /** Set by the mixin the first time it runs; if it never does, ClientHandler uses its fallback. */
    public static volatile boolean mixinActive = false;
    /** Safe mode: after any internal error the mod stops touching the scale (vanilla behaviour). */
    public static volatile boolean disabled = false;
    /** Framebuffer size seen at the last scale update (used to catch silent window-mode changes). */
    public static volatile int lastFbW = -1, lastFbH = -1;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        SCALE = b.comment("Fractional GUI scale. 0 = Auto. Example: 1.5")
                 .defineInRange("guiScale", 0.0D, 0.0D, MAX);
        SMART_HEIGHT = b.comment("Smart Auto: when guiScale is 0 and this is above 0, the scale is chosen as",
                                 "(window height / this value), so the GUI keeps the same relative size at any",
                                 "resolution or window mode. Example: 540 gives 2.0x at 1080p, 4.0x at 4K. 0 = off.")
                        .defineInRange("smartAutoHeight", 0, 0, 4320);
        EXCLUDED = b.comment("Screens (full class name or package prefix) that switch back to the vanilla integer",
                             "scale while open. For modded GUIs that break at fractional scales.",
                             "In-game: open the screen and press Ctrl+Alt+G to add/remove it.")
                    .defineList("excludedScreens", new ArrayList<String>(), o -> o instanceof String);
        SPEC = b.build();
    }

    // ---- safe config access (values throw before the config file is loaded) ----

    private static double rawScale() {
        try { return SCALE.get(); } catch (RuntimeException e) { return 0; }
    }

    private static int rawSmartHeight() {
        try { return SMART_HEIGHT.get(); } catch (RuntimeException e) { return 0; }
    }

    /** Manual scale: 0 when Auto, otherwise a value in MIN..MAX. */
    public static double scale() {
        double v = rawScale();
        return v <= 0 ? 0 : Math.min(MAX, Math.max(MIN, v));
    }

    public static void set(double v) {
        try {
            SCALE.set(v);
            SCALE.save();
        } catch (RuntimeException e) {
            LOGGER.warn("[fracgui] Could not save config yet: {}", e.toString());
        }
    }

    /**
     * Decides the scale that should be used. {@code original} is what vanilla (or another mod)
     * asked for; it is returned untouched whenever the mod has nothing to say.
     */
    public static double resolve(MainWindow w, double original) {
        int fw = w.getFramebufferWidth(), fh = w.getFramebufferHeight();
        lastFbW = fw;
        lastFbH = fh;
        if (disabled || forceInteger) return original;

        double want = scale();
        if (want <= 0) {
            int smart = rawSmartHeight();
            if (smart <= 0 || fh <= 0) return original;
            want = Math.min(MAX, Math.max(MIN, fh / (double) smart));
        }
        // Same limit vanilla uses: the scaled screen must stay at least 320x240.
        double cap = Math.max(1.0D, Math.min(fw / 320.0D, fh / 240.0D));
        return Math.min(want, cap);
    }

    // ---- screen exclusions ----

    public static boolean isExcluded(Screen s) {
        if (s == null) return false;
        try {
            String name = s.getClass().getName();
            for (String e : EXCLUDED.get()) {
                if (!e.isEmpty() && name.startsWith(e)) return true;
            }
        } catch (RuntimeException ignored) { }
        return false;
    }

    /** Adds the exact class name, or removes it if already listed. Returns true if now listed. */
    public static boolean toggleExcluded(String className) {
        try {
            List<String> list = new ArrayList<>(EXCLUDED.get());
            boolean added;
            if (list.remove(className)) added = false;
            else { list.add(className); added = true; }
            EXCLUDED.set(list);
            EXCLUDED.save();
            return added;
        } catch (RuntimeException e) {
            LOGGER.warn("[fracgui] Could not update exclusions: {}", e.toString());
            return false;
        }
    }

    // ---- safe mode ----

    public static void fail(String where, Throwable t) {
        if (disabled) return;
        disabled = true;
        forceInteger = false;
        LOGGER.error("[fracgui] Error in " + where + ": dropping to safe mode (vanilla GUI scale). "
                + "Edit/save the config or restart to retry.", t);
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) mc.execute(() -> {
                try { if (mc.getMainWindow() != null) mc.updateWindowSize(); } catch (Throwable ignored) { }
            });
        } catch (Throwable ignored) { }
    }
}
