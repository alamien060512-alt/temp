package com.donutmarket.mod;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

public final class MarketHud {
    private static final int BG = 0xB0161718;
    private static final int MUTED = 0xFFA6AAB1;
    private static final int WHITE = 0xFFEEF0F3;
    private static final int MAX_WATCH = 5;

    private record Signal(String key, Swings.State state) {
    }

    private static List<Signal> signals = new ArrayList<>();
    private static long builtAt;

    private MarketHud() {
    }

    private static boolean hidden(Minecraft mc) {
        return Hud.hidden();
    }

    public static void swings(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!Settings.get().swingHud || hidden(mc)) {
            Hud.clear(Hud.SWINGS);
            return;
        }
        long now = System.currentTimeMillis();
        if (now - builtAt > 1000) {
            builtAt = now;
            rebuild();
        }
        Font font = mc.font;
        float scale = Hud.scale(Hud.SWINGS, 0.75F);
        int defTop = PortfolioHud.bottom > 0 ? PortfolioHud.bottom + 4 : 4;
        double[] saved = Settings.get().hudPos.get(Hud.SWINGS);
        int startY = saved != null && saved.length > 1 && saved[1] >= 0 ? (int) Math.round(saved[1]) : defTop;
        if (signals.isEmpty()) {
            if (Hud.editing()) {
                String t = "Swings · no signals right now";
                int w = font.width(t) + 10;
                int[] at = Hud.place(Hud.SWINGS, 4, defTop, Math.round(w * scale), Math.round(14 * scale));
                Hud.begin(g, at[0], at[1], scale);
                g.fill(0, 0, w, 14, BG);
                g.text(font, t, 5, 3, MUTED, false);
                Hud.end(g);
            } else {
                Hud.clear(Hud.SWINGS);
            }
            return;
        }
        int screenH = (int) ((mc.getWindow().getGuiScaledHeight() - startY) / scale);
        int top = 0;
        int left = 0;
        int perColumn = Math.max(4, (screenH - 30) / 10);
        int columns = (signals.size() + perColumn - 1) / perColumn;
        int rows = Math.min(signals.size(), perColumn);
        int count = signals.size();
        String[] names = new String[count];
        String[] prices = new String[count];
        String[] pcts = new String[count];
        int[] colName = new int[columns];
        int[] colPrice = new int[columns];
        int[] colPct = new int[columns];
        int tagW = 0;
        for (Signal s0 : signals) {
            tagW = Math.max(tagW, font.width(Swings.hudLabel(s0.state())) + 6);
        }
        for (int i = 0; i < count; i++) {
            Signal s = signals.get(i);
            MarketItem item = MarketData.item(s.key());
            double price = s.state().mainPrice();
            names[i] = Ui.clip(font, ItemKeys.name(s.key()), 96);
            prices[i] = price > 0 ? Money.format(price / Dashboard.divisor(item)) : "-";
            pcts[i] = Money.percent(s.state().mainChange());
            int c = i / perColumn;
            colName[c] = Math.max(colName[c], font.width(names[i]));
            colPrice[c] = Math.max(colPrice[c], font.width(prices[i]));
            colPct[c] = Math.max(colPct[c], font.width(pcts[i]));
        }
        int[] colW = new int[columns];
        int total = 0;
        for (int c = 0; c < columns; c++) {
            colW[c] = tagW + 8 + colName[c] + 8 + colPrice[c] + 8 + colPct[c] + 5;
            total += colW[c];
        }
        String title = "Swings · " + count + (Settings.get().perStack ? " · x64" : " · x1");
        total = Math.max(total, font.width(title) + 10);
        int height = 14 + rows * 10 + 2;
        int[] at = Hud.place(Hud.SWINGS, 4, defTop, Math.round(total * scale), Math.round(height * scale));
        Hud.begin(g, at[0], at[1], scale);
        g.fill(left, top, left + total, top + height, BG);
        g.text(font, title, left + 5, top + 3, WHITE, false);
        int cx = left;
        for (int c = 0; c < columns; c++) {
            int ry = top + 14;
            for (int i = c * perColumn; i < Math.min(count, (c + 1) * perColumn); i++) {
                Swings.State st = signals.get(i).state();
                int color = Swings.color(st);
                String tag = Swings.hudLabel(st);
                g.fill(cx + 4, ry - 1, cx + 4 + tagW, ry + 8, (color & 0x00FFFFFF) | 0x40000000);
                g.text(font, tag, cx + 4 + (tagW - font.width(tag)) / 2, ry, color, false);
                g.text(font, names[i], cx + tagW + 8, ry, WHITE, false);
                int priceRight = cx + tagW + 8 + colName[c] + 8 + colPrice[c];
                g.text(font, prices[i], priceRight - font.width(prices[i]), ry, MUTED, false);
                int pctRight = priceRight + 8 + colPct[c];
                g.text(font, pcts[i], pctRight - font.width(pcts[i]), ry, color, false);
                ry += 10;
            }
            cx += colW[c];
        }
        Hud.end(g);
    }

    private static void rebuild() {
        java.util.Map<String, Swings.Data> data = Swings.data();
        List<String> off = Settings.get().swingSignalsOff;
        List<Signal> all = new ArrayList<>();
        for (MarketItem item : MarketData.all()) {
            String key = item.key();
            if (Settings.get().swingHidden.contains(key)) {
                continue;
            }
            Swings.State st = Swings.state(key, data.get(key));
            if (!st.active() || off.contains(st.signal())) {
                continue;
            }
            all.add(new Signal(key, st));
        }
        all.sort((a, b) -> {
            int p = b.state().priority() - a.state().priority();
            if (p != 0) {
                return p;
            }
            return Double.compare(Math.abs(b.state().mainChange()), Math.abs(a.state().mainChange()));
        });
        signals = all;
    }

    public static void watchlist(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        List<String> favorites = Settings.get().favorites;
        if (!Settings.get().watchHud || favorites.isEmpty() || hidden(mc)) {
            Hud.clear(Hud.WATCH);
            return;
        }
        Font font = mc.font;
        int screenW = mc.getWindow().getGuiScaledWidth();
        List<String[]> parts = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        int total = 0;
        for (String key : favorites) {
            if (keys.size() >= MAX_WATCH) {
                break;
            }
            MarketItem it = MarketData.item(key);
            if (it == null) {
                continue;
            }
            boolean shop = Swings.SHOP_ONLY.contains(key);
            double raw = shop || it.orders() <= 0 ? it.ahLow() : it.orders();
            String price = raw > 0 ? Money.format(raw / Dashboard.divisor(it)) : "-";
            double ch = dayChange(it);
            String change = Double.isNaN(ch) ? "" : (ch >= 0 ? "▲" : "▼") + String.format(java.util.Locale.ROOT, "%.1f%%", Math.abs(ch));
            int w = 11 + font.width(price) + (change.isEmpty() ? 0 : 3 + font.width(change));
            if (total + w + 12 > screenW * 0.7 && !keys.isEmpty()) {
                break;
            }
            total += w + (keys.isEmpty() ? 0 : 12);
            keys.add(key);
            parts.add(new String[]{price, change});
            colors.add(Double.isNaN(ch) ? MUTED : ch >= 0 ? 0xFF4CE5B1 : 0xFFFF6B81);
        }
        if (keys.isEmpty()) {
            Hud.clear(Hud.WATCH);
            return;
        }
        int width = total + 10;
        float ws = Hud.scale(Hud.WATCH, 1F);
        int[] at = Hud.place(Hud.WATCH, Math.round((screenW - width * ws) / 2), 2, Math.round(width * ws), Math.round(13 * ws));
        Hud.begin(g, at[0], at[1], ws);
        int x = 0;
        int y = 0;
        g.fill(x, y, x + width, y + 13, BG);
        int cx = x + 5;
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) {
                g.fill(cx + 5, y + 3, cx + 6, y + 10, 0x40FFFFFF);
                cx += 12;
            }
            Ui.item(g, ItemKeys.stackFor(keys.get(i)), cx, y + 2, 0.5F);
            cx += 11;
            String[] p = parts.get(i);
            g.text(font, p[0], cx, y + 3, WHITE, false);
            cx += font.width(p[0]);
            if (!p[1].isEmpty()) {
                cx += 3;
                g.text(font, p[1], cx, y + 3, colors.get(i), false);
                cx += font.width(p[1]);
            }
        }
        Hud.end(g);
    }

    public static void market(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!Settings.get().marketHud || hidden(mc)) {
            Hud.clear(Hud.MARKET);
            return;
        }
        Font font = mc.font;
        List<MarketScreen.Row> all = MarketScreen.matching();
        int max = 8;
        List<MarketScreen.Row> rows = all.size() > max ? all.subList(0, max) : all;
        float scale = Hud.scale(Hud.MARKET, 0.85F);
        int sh = mc.getWindow().getGuiScaledHeight();
        String head = MarketScreen.sortHead();
        MarketItem clock = MarketData.item(MarketScreen.selected());
        if (clock == null && !rows.isEmpty()) {
            clock = rows.get(0).item();
        }
        String title = "Market · " + MarketScreen.agoFor(clock);
        int rowH = 22;
        int innerW = 178;
        int metricW = font.width(head);
        for (MarketScreen.Row r : rows) {
            metricW = Math.max(metricW, font.width(MarketScreen.metric(r.item()).text()));
            metricW = Math.max(metricW, font.width(MarketScreen.metric(r.item()).note()));
        }
        innerW = Math.max(innerW, 28 + 90 + 8 + metricW + 10);
        int height = 15 + Math.max(1, rows.size()) * rowH + 3;
        int[] at = Hud.place(Hud.MARKET, 4, Math.max(4, sh - Math.round(height * scale) - 6), Math.round(innerW * scale), Math.round(height * scale));
        Hud.begin(g, at[0], at[1], scale);
        g.fill(0, 0, innerW, height, BG);
        g.text(font, Ui.clip(font, title, innerW - 12 - font.width(head)), 5, 3, WHITE, false);
        g.text(font, head, innerW - 6 - font.width(head), 3, MUTED, false);
        if (rows.isEmpty()) {
            g.text(font, MarketData.marketLoadedAt() == 0 ? "Loading prices..." : "Nothing matches", 6, 18, MUTED, false);
            Hud.end(g);
            return;
        }
        int y = 14;
        for (MarketScreen.Row r : rows) {
            MarketItem it = r.item();
            MarketScreen.Metric m = MarketScreen.metric(it);
            Ui.item(g, r.icon(), 4, y + 3, 0.9F);
            String pct = m.text();
            int pctX = innerW - 6 - font.width(pct);
            g.text(font, pct, pctX, y + 2, m.color(), false);
            g.text(font, Ui.clip(font, r.name(), pctX - 28), 22, y + 2, WHITE, false);
            double dv = Dashboard.divisor(it);
            String o = it.orders() > 0 ? Money.format(it.orders() / dv) : "-";
            String a = it.ahLow() > 0 ? Money.format(it.ahLow() / dv) : "-";
            g.text(font, o, 22, y + 12, it.orders() > 0 ? 0xFF4CE5B1 : MUTED, false);
            g.text(font, a, 22 + font.width(o) + 5, y + 12, it.ahLow() > 0 ? 0xFFF2C14E : MUTED, false);
            if (!m.note().isEmpty()) {
                g.text(font, m.note(), innerW - 6 - font.width(m.note()), y + 12, MUTED, false);
            }
            y += rowH;
        }
        Hud.end(g);
    }

    static double dayChange(MarketItem it) {
        double[] sp = it.spark();
        double first = 0;
        double last = 0;
        for (double v : sp) {
            if (v > 0) {
                if (first == 0) {
                    first = v;
                }
                last = v;
            }
        }
        return first > 0 && last > 0 ? (last - first) / first * 100 : Double.NaN;
    }
}
