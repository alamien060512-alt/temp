package com.donutmarket.mod;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PortfolioHud {
    private static final int GREEN = 0xFF4CE5B1;
    private static final int RED = 0xFFFF6B81;
    private static final int MUTED = 0xFFA6AAB1;
    private static final int WHITE = 0xFFEEF0F3;
    private static final int MAX_ROWS = 4;

    private record Line(String name, double profit) {
    }

    static int bottom;

    private PortfolioHud() {
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        bottom = 0;
        Minecraft mc = Minecraft.getInstance();
        Settings.Values settings = Settings.get();
        boolean edit = Hud.editing();
        if (!settings.hud || (settings.portfolio.isBlank() && !edit) || Hud.hidden()) {
            Hud.clear(Hud.PORTFOLIO);
            return;
        }
        List<Line> lines = new ArrayList<>();
        double profit = 0;
        double cost = 0;
        for (Position p : MarketData.positions()) {
            if (!p.priced()) {
                continue;
            }
            profit += p.profit();
            cost += p.entry() * p.filled();
            lines.add(new Line(ItemKeys.name(p.item()), p.profit()));
        }
        lines.sort(Comparator.comparingDouble(l -> -Math.abs(l.profit())));
        if (lines.size() > MAX_ROWS) {
            lines = lines.subList(0, MAX_ROWS);
        }

        String title = MarketData.portfolioName().isEmpty() ? (settings.portfolio.isBlank() ? "Portfolio" : settings.portfolio) : MarketData.portfolioName();
        String summary;
        int accent;
        if (!MarketData.portfolioError().isEmpty()) {
            summary = MarketData.portfolioError();
            accent = MUTED;
            lines.clear();
        } else if (lines.isEmpty()) {
            summary = MarketData.portfolioLoadedAt() == 0 ? "Loading..." : "Nothing to price yet";
            accent = MUTED;
        } else {
            summary = Money.signed(profit) + "  " + Money.percent(cost > 0 ? profit / cost * 100 : 0);
            accent = profit >= 0 ? GREEN : RED;
        }

        Font font = mc.font;
        int width = Math.max(font.width(title), font.width(summary)) + 14;
        for (Line l : lines) {
            width = Math.max(width, font.width(l.name()) + font.width(Money.signed(l.profit())) + 22);
        }
        width = Math.min(width, 220);

        int height = 26 + (lines.isEmpty() ? 0 : lines.size() * 10 + 3);
        float sc = Hud.scale(Hud.PORTFOLIO, 1F);
        int[] at = Hud.place(Hud.PORTFOLIO, 4, 4, Math.round(width * sc), Math.round(height * sc));
        bottom = at[1] + Math.round(height * sc);
        Hud.begin(graphics, at[0], at[1], sc);
        int x = 0;
        int y = 0;
        graphics.fill(x, y, x + width, y + height, 0xB0161718);
        graphics.fill(x, y, x + 2, y + height, accent);
        graphics.text(font, title, x + 7, y + 4, WHITE, false);
        graphics.text(font, summary, x + 7, y + 15, accent, false);

        int rowY = y + 29;
        for (Line l : lines) {
            String value = Money.signed(l.profit());
            String name = fit(font, l.name(), width - font.width(value) - 20);
            graphics.text(font, name, x + 7, rowY, MUTED, false);
            graphics.text(font, value, x + width - 5 - font.width(value), rowY, l.profit() >= 0 ? GREEN : RED, false);
            rowY += 10;
        }
        Hud.end(graphics);
    }

    private static String fit(Font font, String text, int max) {
        if (font.width(text) <= max) {
            return text;
        }
        String cut = text;
        while (cut.length() > 1 && font.width(cut + "...") > max) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "...";
    }
}
