package com.donutmarket.mod;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.Map;

public class HudEditScreen extends Screen {
    private final Screen parent;
    private String dragging;
    private double grabX;
    private double grabY;

    public HudEditScreen(Screen parent) {
        super(Component.literal("Move HUD"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
    }

    private static String label(String id) {
        return switch (id) {
            case Hud.PORTFOLIO -> "Portfolio";
            case Hud.SWINGS -> "Swings";
            case Hud.MARKET -> "Market";
            default -> "Watchlist";
        };
    }

    private String at(double mx, double my) {
        String hit = null;
        for (Map.Entry<String, Hud.Box> e : Hud.BOXES.entrySet()) {
            Hud.Box b = e.getValue();
            if (mx >= b.x() && mx < b.x() + b.w() && my >= b.y() && my < b.y() + b.h()) {
                hit = e.getKey();
            }
        }
        return hit;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0x30000000);
        String hover = dragging != null ? dragging : at(mouseX, mouseY);
        for (Map.Entry<String, Hud.Box> e : Hud.BOXES.entrySet()) {
            Hud.Box b = e.getValue();
            boolean on = e.getKey().equals(hover);
            int c = on ? 0xFF3B8CFF : 0xAAFFFFFF;
            g.fill(b.x() - 1, b.y() - 1, b.x() + b.w() + 1, b.y(), c);
            g.fill(b.x() - 1, b.y() + b.h(), b.x() + b.w() + 1, b.y() + b.h() + 1, c);
            g.fill(b.x() - 1, b.y(), b.x(), b.y() + b.h(), c);
            g.fill(b.x() + b.w(), b.y(), b.x() + b.w() + 1, b.y() + b.h(), c);
            String tag = label(e.getKey()) + " " + Math.round(Hud.userScale(e.getKey()) * 100) + "%";
            int tw = font.width(tag) + 6;
            int ty = b.y() + b.h() + 2 + 11 > height ? b.y() - 12 : b.y() + b.h() + 2;
            g.fill(b.x(), ty, b.x() + tw, ty + 11, on ? 0xE02F6FFF : 0xC0000000);
            g.text(font, tag, b.x() + 3, ty + 2, 0xFFFFFFFF, false);
        }
        String[] help = {"Drag a box to move it", "Scroll on it to make it bigger or smaller", "R resets everything, Esc saves"};
        int y = height / 2 - 16;
        for (String h : help) {
            int w = font.width(h);
            g.fill(width / 2 - w / 2 - 4, y - 2, width / 2 + w / 2 + 4, y + 9, 0xB0000000);
            g.text(font, h, width / 2 - w / 2, y, 0xFFEEF0F3, false);
            y += 12;
        }
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        String hit = at(event.x(), event.y());
        if (hit != null) {
            Hud.Box b = Hud.BOXES.get(hit);
            dragging = hit;
            grabX = event.x() - b.x();
            grabY = event.y() - b.y();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging != null) {
            Hud.Box b = Hud.BOXES.get(dragging);
            int w = b == null ? 0 : b.w();
            int h = b == null ? 0 : b.h();
            int x = (int) Math.round(Math.max(0, Math.min(width - w, event.x() - grabX)));
            int y = (int) Math.round(Math.max(0, Math.min(height - h, event.y() - grabY)));
            Hud.move(dragging, x, y);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null) {
            dragging = null;
            Settings.save();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        String hit = at(mx, my);
        if (hit != null && vertical != 0) {
            Hud.resize(hit, vertical > 0 ? 0.1 : -0.1);
            Settings.save();
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == 82) {
            Hud.reset();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        Settings.save();
        minecraft.gui.setScreen(parent);
    }
}
