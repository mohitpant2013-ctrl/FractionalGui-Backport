package fyredeft.fracgui;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.AbstractSlider;
import net.minecraft.util.text.StringTextComponent;

/**
 * Slider for the fractional GUI scale. Index 0 = Auto, 1..N = MIN..MAX in STEP increments.
 * The scale is applied on release (not while dragging), because applying it re-initialises
 * the screen and would destroy the slider mid-drag.
 */
public class ScaleSlider extends AbstractSlider {
    private static final int MAX_IDX = (int) Math.round((FracConfig.MAX - FracConfig.MIN) / FracConfig.STEP) + 1;

    public ScaleSlider(int x, int y, int w, int h) {
        super(x, y, w, h, StringTextComponent.EMPTY, toIdx(FracConfig.scale()) / (double) MAX_IDX);
        updateMessage();
    }

    static int toIdx(double s) {
        if (s <= 0) return 0;
        long i = Math.round((s - FracConfig.MIN) / FracConfig.STEP) + 1;
        return (int) Math.max(1, Math.min(MAX_IDX, i));
    }

    static double fromIdx(int i) {
        if (i <= 0) return 0;
        return Math.round((FracConfig.MIN + (i - 1) * FracConfig.STEP) * 100.0D) / 100.0D;
    }

    private int idx() {
        return (int) Math.round(this.sliderValue * MAX_IDX);
    }

    @Override
    protected void updateMessage() {
        int i = idx();
        String v = i == 0 ? "Auto" : String.format(Locale.ROOT, "%.2fx", fromIdx(i));
        setMessage(new StringTextComponent("GUI Scale: " + v));
    }

    @Override
    protected void applyValue() {
        // committed on release / key press, see commit()
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        super.onRelease(mouseX, mouseY);
        commit();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        boolean handled = super.keyPressed(key, scan, mods);
        if (handled) commit();
        return handled;
    }

    private void commit() {
        try {
            double v = fromIdx(idx());
            if (Math.abs(v - FracConfig.scale()) < 1e-9) return;
            FracConfig.set(v);
            Minecraft.getInstance().updateWindowSize(); // goes through the mixin, re-inits the open screen
        } catch (Throwable t) {
            FracConfig.fail("slider", t);
        }
    }
}
