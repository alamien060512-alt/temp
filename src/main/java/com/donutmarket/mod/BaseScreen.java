package com.donutmarket.mod;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

public abstract class BaseScreen extends Screen {
    protected static final int BG = 0xF0080809;
    protected static final int CARD = 0xFF121214;
    protected static final int BORDER = 0xFF26282B;
    protected static final int TEXT = 0xFFEEF0F3;
    protected static final int MUTED = 0xFF8A8F97;
    protected static final int GREEN = 0xFF4CE5B1;
    protected static final int GOLD = 0xFFF2C14E;
    protected static final int RED = 0xFFFF6B81;
    protected static final int BLUE = 0xFF7FB2FF;

    protected final Screen parent;
    protected int left;
    protected int right;

    protected BaseScreen(String title, Screen parent) {
        super(Component.literal(title));
        this.parent = parent;
    }

    protected void layout(int maxWidth) {
        int w = Math.min(maxWidth, width - 20);
        left = (width - w) / 2;
        right = left + w;
    }

    protected Button button(String label, int x, int y, int w, Button.OnPress press) {
        return addRenderableWidget(Button.builder(Component.literal(label), press).bounds(x, y, w, 20).build());
    }

    protected EditBox field(int x, int y, int w, String hint, int max) {
        EditBox box = new EditBox(font, x, y, w, 18, Component.literal(hint));
        box.setHint(Component.literal(hint).withColor(MUTED));
        box.setMaxLength(max);
        return addRenderableWidget(box);
    }

    protected static void mask(EditBox box) {
        box.addFormatter((text, start) -> FormattedCharSequence.forward("*".repeat(text.length()), Style.EMPTY));
    }

    protected void backdrop(GuiGraphicsExtractor g) {
        Ui.earth(g, left - 10, 0, right + 10, height);
        g.fill(left - 10, 0, right + 10, height, Ui.BG);
    }

    protected void header(GuiGraphicsExtractor g, String title, String right) {
        g.text(font, title, left, 12, GOLD, false);
        if (right != null && !right.isEmpty()) {
            g.text(font, right, this.right - font.width(right), 12, MUTED, false);
        }
    }

    protected void alignRight(GuiGraphicsExtractor g, String text, int x, int y, int color) {
        g.text(font, text, x - font.width(text), y, color, false);
    }

    protected String clip(String text, int max) {
        if (font.width(text) <= max) {
            return text;
        }
        String cut = text;
        while (cut.length() > 1 && font.width(cut + "...") > max) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "...";
    }

    protected static double parseAmount(String text) {
        String t = text.trim().toLowerCase(java.util.Locale.ROOT).replace("$", "").replace(",", "");
        if (t.isEmpty()) {
            return 0;
        }
        double mult = 1;
        char last = t.charAt(t.length() - 1);
        if (last == 'k' || last == 'm' || last == 'b') {
            mult = last == 'k' ? 1_000 : last == 'm' ? 1_000_000 : 1_000_000_000;
            t = t.substring(0, t.length() - 1);
        }
        try {
            return Double.parseDouble(t) * mult;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
