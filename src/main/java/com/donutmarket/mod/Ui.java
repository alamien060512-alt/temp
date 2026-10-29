package com.donutmarket.mod;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

public final class Ui {
    public static final int SHADE = 0xC8050505;
    public static final int OUTLINE = 0xFF2C2E31;
    public static final int BG = 0xCC050506;
    public static final int SIDE = 0xC40A0A0B;
    public static final int CARD = 0xE60E0E10;
    public static final int BORDER = 0xFF26282B;
    public static final int SELECT = 0xFF1C1D20;
    public static final int SELECT_BORDER = 0xFF3B6FD0;
    public static final int ACTIVE = 0xFF1E2023;
    public static final int PLOT = 0x30000000;
    public static final int GLASS = 0x40111112;
    public static final int CHIP = 0xFF161719;
    public static final int CHIP_ON = 0xFF2F7BFF;
    public static final int STRIPE = 0x0CFFFFFF;
    public static final int HOVER = 0x16FFFFFF;

    public static final int TEXT = 0xFFEEF0F3;
    public static final int SOFT = 0xFFC4C8CE;
    public static final int MUTED = 0xFF8A8F97;
    public static final int GREEN = 0xFF4CE5B1;
    public static final int GOLD = 0xFFF2C14E;
    public static final int RED = 0xFFFF6B81;
    public static final int BLUE = 0xFF7FB2FF;

    private Ui() {
    }

    public static void card(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2) {
        card(g, x1, y1, x2, y2, CARD, BORDER);
    }

    public static void card(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int bg, int border) {
        g.fill(x1, y1, x2, y2, border);
        g.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, bg);
    }

    public static void text(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color) {
        g.text(font, s, x, y, color, false);
    }

    public static void right(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color) {
        g.text(font, s, x - font.width(s), y, color, false);
    }

    public static String clip(Font font, String s, int max) {
        if (max <= 0) {
            return "";
        }
        if (font.width(s) <= max) {
            return s;
        }
        String cut = s;
        while (cut.length() > 1 && font.width(cut + "...") > max) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "...";
    }

    public static void item(GuiGraphicsExtractor g, ItemStack stack, int x, int y, float scale) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (scale == 1F) {
            g.item(stack, x, y);
            return;
        }
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.item(stack, 0, 0);
        g.pose().popMatrix();
    }

    public static int chip(GuiGraphicsExtractor g, Font font, String s, int x, int y, boolean on) {
        int w = font.width(s) + 8;
        g.fill(x, y, x + w, y + 12, on ? CHIP_ON : CHIP);
        g.text(font, s, x + 4, y + 2, TEXT, false);
        return w;
    }

    public static int tag(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color) {
        int w = font.width(s) + 6;
        g.fill(x, y, x + w, y + 11, (color & 0x00FFFFFF) | 0x30000000);
        g.text(font, s, x + 3, y + 2, color, false);
        return w;
    }

    public static boolean inside(double mx, double my, int x1, int y1, int x2, int y2) {
        return mx >= x1 && mx < x2 && my >= y1 && my < y2;
    }

    private static final net.minecraft.resources.Identifier EARTH = net.minecraft.resources.Identifier.fromNamespaceAndPath("donutmarket", "textures/gui/earth.png");

    public static void earth(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2) {
        int w = x2 - x1;
        int h = y2 - y1;
        if (w <= 0 || h <= 0) {
            return;
        }
        float scale = Math.max(w / 1280F, h / 720F);
        int rw = Math.min(1280, Math.round(w / scale));
        int rh = Math.min(720, Math.round(h / scale));
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, EARTH, x1, y1, (1280 - rw) / 2F, (720 - rh) / 2F, w, h, rw, rh, 1280, 720);
    }

    public static int trend(double v) {
        return Double.isNaN(v) ? MUTED : v >= 0 ? GREEN : RED;
    }
}
