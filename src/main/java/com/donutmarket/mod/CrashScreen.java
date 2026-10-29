package com.donutmarket.mod;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CrashScreen extends Dashboard {
    private static final String[] FILTERS = {"At risk", "Danger", "All"};
    private static final int CARD_H = 78;
    private static final int GAP = 6;
    private static int filter = 0;

    private final List<Crash.Row> rows = new ArrayList<>();
    private final List<int[]> hits = new ArrayList<>();
    private double scroll;
    private int listTop;
    private int cols = 1;
    private int cardW;

    public CrashScreen() {
        super("Crash detector");
    }

    @Override
    protected Page page() {
        return Page.CRASH;
    }

    @Override
    protected void build() {
        listTop = ct + 36;
        stackToggle(cr - 54, ct - 1, this::rebuild);
        int w = cr - cl;
        cols = w >= 420 ? 3 : w >= 260 ? 2 : 1;
        cardW = (w - GAP * (cols - 1)) / cols;
        if (System.currentTimeMillis() - Crash.loadedAt() > 30_000L) {
            Crash.load(this::rebuild);
        }
        rebuild();
    }

    private int contentHeight() {
        int lines = (rows.size() + cols - 1) / cols;
        return lines * (CARD_H + GAP);
    }

    @Override
    public void tick() {
        if (System.currentTimeMillis() - Crash.loadedAt() > 60_000L) {
            Crash.load(this::rebuild);
        }
    }

    private void rebuild() {
        rows.clear();
        int min = filter == 0 ? 40 : filter == 1 ? 70 : -1;
        for (Crash.Row r : Crash.rows()) {
            if (r.risk() >= min && MarketData.item(r.key()) != null) {
                rows.add(r);
            }
        }
        rows.sort(Comparator.comparingInt(Crash.Row::risk).reversed().thenComparing(r -> -r.drop()));
        double max = Math.max(0, contentHeight() - (cb - listTop));
        scroll = Math.max(0, Math.min(scroll, max));
    }

    private static int riskColor(int risk) {
        return risk >= 70 ? Ui.RED : risk >= 40 ? Ui.GOLD : Ui.GREEN;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        for (int[] h : hits) {
            if (Ui.inside(mx, my, h[0], h[1], h[2], h[3])) {
                filter = h[5];
                scroll = 0;
                rebuild();
                return true;
            }
        }
        if (Ui.inside(mx, my, cl, listTop, cr, cb)) {
            int line = (int) ((my - listTop + scroll) / (CARD_H + GAP));
            int col = (int) ((mx - cl) / (cardW + GAP));
            int index = line * cols + col;
            if (col < cols && index >= 0 && index < rows.size()) {
                MarketScreen.select(rows.get(index).key());
                minecraft.gui.setScreen(new MarketScreen());
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (Ui.inside(mx, my, cl, listTop, cr, cb)) {
            double max = Math.max(0, contentHeight() - (cb - listTop));
            scroll = Math.max(0, Math.min(max, scroll - vertical * 30));
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor g, int mx, int my, float delta) {
        hits.clear();
        Ui.text(g, font, "Crash detector", cl, ct + 4, Ui.TEXT);
        int x = cl + font.width("Crash detector") + 10;
        for (int i = 0; i < FILTERS.length; i++) {
            int w = Ui.chip(g, font, FILTERS[i], x, ct + 2, i == filter);
            hits.add(new int[]{x, ct + 2, x + w, ct + 14, 0, i});
            x += w + 3;
        }
        Ui.text(g, font, Ui.clip(font, "Items held up by only a few buy orders. If those fill, /orders drops.", cr - cl), cl, ct + 22, Ui.MUTED);

        g.enableScissor(cl, listTop, cr + 1, cb);
        for (int i = 0; i < rows.size(); i++) {
            int x1 = cl + (i % cols) * (cardW + GAP);
            int y1 = listTop + (i / cols) * (CARD_H + GAP) - (int) scroll;
            if (y1 + CARD_H < listTop || y1 > cb) {
                continue;
            }
            drawCard(g, rows.get(i), x1, y1, x1 + cardW, y1 + CARD_H, Ui.inside(mx, my, x1, Math.max(y1, listTop), x1 + cardW, Math.min(y1 + CARD_H, cb)));
        }
        g.disableScissor();
        if (rows.isEmpty()) {
            String msg = Crash.loadedAt() == 0 ? (Crash.failed() ? "Couldn't load crash data, retrying..." : "Loading...") : filter == 2 ? "No order data yet." : "Nothing at risk right now.";
            Ui.text(g, font, msg, cl + 4, listTop + 6, Crash.failed() ? Ui.RED : Ui.MUTED);
        }
        int track = cb - listTop;
        int total = contentHeight();
        if (total > track) {
            int thumb = Math.max(14, track * track / total);
            double max = total - track;
            int ty = listTop + (int) ((track - thumb) * (scroll / max));
            g.fill(cr + 2, ty, cr + 4, ty + thumb, 0x55FFFFFF);
        }
    }

    private void drawCard(GuiGraphicsExtractor g, Crash.Row r, int x1, int y1, int x2, int y2, boolean hover) {
        int col = riskColor(r.risk());
        g.fill(x1, y1, x2, y2, hover ? 0xF0222324 : 0xF0191A1B);
        g.fill(x1, y1, x2, y1 + 2, col);
        g.fill(x1, y2 - 1, x2, y2, Ui.BORDER);
        String pill = r.risk() >= 70 ? "DANGER" : r.risk() >= 40 ? "WATCH" : "SAFE";
        int pw = font.width(pill) + 8;
        g.fill(x2 - 5 - pw, y1 + 6, x2 - 5, y1 + 17, (col & 0x00FFFFFF) | 0x33000000);
        g.text(font, pill, x2 - 1 - pw, y1 + 8, col, false);
        Ui.item(g, ItemKeys.stackFor(r.key()), x1 + 5, y1 + 4, 1F);
        Ui.text(g, font, Ui.clip(font, ItemKeys.name(r.key()), x2 - x1 - pw - 36), x1 + 25, y1 + 8, Ui.TEXT);
        g.pose().pushMatrix();
        g.pose().translate(x1 + 6, y1 + 21);
        g.pose().scale(2F, 2F);
        g.text(font, r.risk() + "%", 0, 0, col, false);
        g.pose().popMatrix();
        int bx1 = x1 + 6;
        int bx2 = x2 - 6;
        int by = y1 + 41;
        g.fill(bx1, by, bx2, by + 3, 0xFF262729);
        int fillTo = bx1 + (int) Math.round((bx2 - bx1) * Math.min(100, Math.max(2, r.risk())) / 100.0);
        for (int x = bx1; x < fillTo; x++) {
            double t = (double) (x - bx1) / Math.max(1, bx2 - bx1);
            g.fill(x, by, x + 1, by + 3, blend(t));
        }
        String a = r.hasCliff() ? r.orders() + " / " + Money.format(r.items()).replace("$", "") + " items" : "Solid orders";
        double dv = Settings.get().perStack ? 1 : Math.max(1, r.basis());
        String b = r.hasCliff() ? "drop " + Money.format((r.now() - r.to()) / dv) : "";
        String c = r.hasCliff() ? "to " + Money.format(r.to() / dv) + (r.basis() > 1 ? (dv > 1 ? " each" : " x" + r.basis()) : "") : "";
        String d = r.hasCliff() ? Crash.eta(r.eta()) + " left" : "";
        int half = (x2 - x1 - 12) / 2;
        Ui.text(g, font, Ui.clip(font, a, half - 4), x1 + 6, y1 + 50, Ui.SOFT);
        Ui.text(g, font, Ui.clip(font, b, half - 4), x1 + 6 + half, y1 + 50, Ui.RED);
        Ui.text(g, font, Ui.clip(font, c, half - 4), x1 + 6, y1 + 62, Ui.SOFT);
        Ui.text(g, font, Ui.clip(font, d, half - 4), x1 + 6 + half, y1 + 62, Ui.MUTED);
    }

    private static int blend(double t) {
        int[] a = {0x9B, 0xE3, 0x7A};
        int[] m = {0xF2, 0xC1, 0x4E};
        int[] z = {0xFF, 0x6B, 0x81};
        int[] from = t < 0.5 ? a : m;
        int[] to = t < 0.5 ? m : z;
        double k = t < 0.5 ? t * 2 : (t - 0.5) * 2;
        int rr = (int) (from[0] + (to[0] - from[0]) * k);
        int gg = (int) (from[1] + (to[1] - from[1]) * k);
        int bb = (int) (from[2] + (to[2] - from[2]) * k);
        return 0xFF000000 | rr << 16 | gg << 8 | bb;
    }
}
