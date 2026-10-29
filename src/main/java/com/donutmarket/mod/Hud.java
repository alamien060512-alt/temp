package com.donutmarket.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Hud {
    public static final String PORTFOLIO = "portfolio";
    public static final String SWINGS = "swings";
    public static final String WATCH = "watchlist";
    public static final String MARKET = "market";

    record Box(int x, int y, int w, int h) {
    }

    static final Map<String, Box> BOXES = new LinkedHashMap<>();

    private Hud() {
    }

    static boolean editing() {
        return Minecraft.getInstance().gui.screen() instanceof HudEditScreen;
    }

    static boolean hidden() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return true;
        }
        return mc.gui.screen() != null && !editing();
    }

    static float scale(String id, float base) {
        double[] p = Settings.get().hudPos.get(id);
        double s = p != null && p.length > 2 && p[2] > 0 ? p[2] : 1;
        return (float) (base * s);
    }

    static double userScale(String id) {
        double[] p = Settings.get().hudPos.get(id);
        return p != null && p.length > 2 && p[2] > 0 ? p[2] : 1;
    }

    static int[] place(String id, int defX, int defY, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        double[] p = Settings.get().hudPos.get(id);
        int x = p != null && p.length > 1 && p[0] >= 0 ? (int) Math.round(p[0]) : defX;
        int y = p != null && p.length > 1 && p[1] >= 0 ? (int) Math.round(p[1]) : defY;
        x = Math.max(0, Math.min(x, sw - w));
        y = Math.max(0, Math.min(y, sh - h));
        BOXES.put(id, new Box(x, y, w, h));
        return new int[]{x, y};
    }

    static void clear(String id) {
        BOXES.remove(id);
    }

    static void begin(GuiGraphicsExtractor g, int x, int y, float s) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(s, s);
    }

    static void end(GuiGraphicsExtractor g) {
        g.pose().popMatrix();
    }

    static void move(String id, int x, int y) {
        double[] p = Settings.get().hudPos.get(id);
        double s = p != null && p.length > 2 && p[2] > 0 ? p[2] : 1;
        Settings.get().hudPos.put(id, new double[]{Math.max(0, x), Math.max(0, y), s});
    }

    static void resize(String id, double step) {
        double[] p = Settings.get().hudPos.get(id);
        Box b = BOXES.get(id);
        double x = p != null && p.length > 1 ? p[0] : -1;
        double y = p != null && p.length > 1 ? p[1] : -1;
        if (x < 0 && b != null) {
            x = b.x();
        }
        if (y < 0 && b != null) {
            y = b.y();
        }
        double s = p != null && p.length > 2 && p[2] > 0 ? p[2] : 1;
        s = Math.max(0.5, Math.min(2.5, Math.round((s + step) * 10) / 10.0));
        Settings.get().hudPos.put(id, new double[]{x, y, s});
    }

    static void reset() {
        Settings.get().hudPos.clear();
        Settings.save();
    }
}
