package fyredeft.fracgui;

import java.util.Locale;
import net.minecraft.client.MainWindow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.VideoSettingsScreen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.lwjgl.glfw.GLFW;

public class ClientHandler {
    private static final String CAT = "key.categories.fracgui";
    private static final KeyBinding UP = new KeyBinding("key.fracgui.up", GLFW.GLFW_KEY_EQUAL, CAT);
    private static final KeyBinding DOWN = new KeyBinding("key.fracgui.down", GLFW.GLFW_KEY_MINUS, CAT);
    private static final KeyBinding OFF = new KeyBinding("key.fracgui.off", GLFW.GLFW_KEY_BACKSLASH, CAT);

    private int ticks = 0;
    private boolean fallbackLogged = false;

    public static void init() {
        ClientRegistry.registerKeyBinding(UP);
        ClientRegistry.registerKeyBinding(DOWN);
        ClientRegistry.registerKeyBinding(OFF);
        MinecraftForge.EVENT_BUS.register(new ClientHandler());
        FMLJavaModLoadingContext.get().getModEventBus().addListener(ClientHandler::onConfigEvent);
    }

    // ---- live config reload: edit config/fracgui-client.toml and it applies instantly ----

    private static void onConfigEvent(ModConfig.ModConfigEvent e) {
        if (e.getConfig().getSpec() != FracConfig.SPEC) return;
        FracConfig.disabled = false; // a config reload is also a "retry" after safe mode
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) mc.execute(ClientHandler::refresh);
    }

    private static void refresh() {
        try {
            Minecraft mc = Minecraft.getInstance();
            MainWindow w = mc.getMainWindow();
            if (w == null) return;
            double vanilla = w.calcGuiScale(mc.gameSettings.guiScale, mc.getForceUnicodeFont());
            double target = FracConfig.resolve(w, vanilla);
            if (Math.abs(target - w.getGuiScaleFactor()) > 1e-6) mc.updateWindowSize();
        } catch (Throwable t) {
            FracConfig.fail("config refresh", t);
        }
    }

    // ---- Video Settings slider ----

    /** Adds the slider to the top-right corner of Video Settings (free space, no overlap with the list). */
    @SubscribeEvent
    public void onInit(GuiScreenEvent.InitGuiEvent.Post e) {
        try {
            if (e.getGui() instanceof VideoSettingsScreen) {
                e.addWidget(new ScaleSlider(e.getGui().width - 114, 6, 110, 20));
            }
        } catch (Throwable t) {
            FracConfig.fail("init gui", t);
        }
    }

    // ---- per-screen exclusions ----

    /** Switches to the vanilla integer scale while an excluded (broken) modded screen is open. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onOpen(GuiOpenEvent e) {
        try {
            boolean excluded = FracConfig.isExcluded(e.getGui());
            if (excluded != FracConfig.forceInteger) {
                FracConfig.forceInteger = excluded;
                Minecraft.getInstance().updateWindowSize();
            }
        } catch (Throwable t) {
            FracConfig.fail("open gui", t);
        }
    }

    /** Ctrl+Alt+G on any screen: add/remove that screen from the exclusion list. */
    @SubscribeEvent
    public void onKey(GuiScreenEvent.KeyboardKeyPressedEvent.Pre e) {
        try {
            if (e.getKeyCode() != GLFW.GLFW_KEY_G || !Screen.hasControlDown() || !Screen.hasAltDown()) return;
            e.setCanceled(true);
            String name = e.getGui().getClass().getName();
            boolean added = FracConfig.toggleExcluded(name);
            FracConfig.forceInteger = FracConfig.isExcluded(e.getGui());
            Minecraft mc = Minecraft.getInstance();
            mc.updateWindowSize();
            String msg = "[fracgui] " + (added ? "Excluded " : "Removed exclusion for ") + name;
            FracConfig.LOGGER.info(msg);
            mc.ingameGUI.getChatGUI().printChatMessage(new StringTextComponent(msg));
        } catch (Throwable t) {
            FracConfig.fail("key", t);
        }
    }

    // ---- keybinds, window-mode watchdog, fallback ----

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            MainWindow w = mc.getMainWindow();
            if (w == null) return;

            while (UP.isPressed()) step(mc, +1);
            while (DOWN.isPressed()) step(mc, -1);
            while (OFF.isPressed()) set(mc, 0);

            if (FracConfig.disabled) return;

            if (FracConfig.mixinActive) {
                // Borderless / fullscreen / window-mode switches normally fire vanilla's resize callback.
                // If a mod changes the window without it, the framebuffer size still changes: catch that here.
                if (w.getFramebufferWidth() != FracConfig.lastFbW || w.getFramebufferHeight() != FracConfig.lastFbH) {
                    mc.updateWindowSize();
                }
            } else if (++ticks > 40) {
                // Mixin never ran (another mod rewrote setGuiScale): enforce the scale directly instead.
                if (!fallbackLogged) {
                    fallbackLogged = true;
                    FracConfig.LOGGER.warn("[fracgui] Mixin did not apply; using tick-based fallback.");
                }
                double cur = w.getGuiScaleFactor();
                double want = FracConfig.resolve(w, cur);
                if (Math.abs(want - cur) > 1e-6) {
                    w.setGuiScale(want);
                    if (mc.currentScreen != null) {
                        mc.currentScreen.resize(mc, w.getScaledWidth(), w.getScaledHeight());
                    }
                }
            }
        } catch (Throwable t) {
            FracConfig.fail("tick", t);
        }
    }

    private static void step(Minecraft mc, int dir) {
        double cur = FracConfig.scale();
        if (cur <= 0) cur = mc.getMainWindow().getGuiScaleFactor(); // start from the current scale
        double next = Math.round(cur / 0.25D) * 0.25D + dir * 0.25D;
        set(mc, Math.min(FracConfig.MAX, Math.max(1.0D, next)));
    }

    private static void set(Minecraft mc, double v) {
        if (Math.abs(v - FracConfig.scale()) < 1e-9) return;
        FracConfig.set(v);
        mc.updateWindowSize();
        String label = v <= 0 ? "Auto" : String.format(Locale.ROOT, "%.2fx", v);
        mc.ingameGUI.setOverlayMessage(new StringTextComponent("GUI Scale: " + label), false);
    }
}
