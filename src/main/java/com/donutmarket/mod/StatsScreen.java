package com.donutmarket.mod;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StatsScreen extends Dashboard {
    private static final String[] MODES = {"ah_median", "orders", "ah_low"};
    private static final String[] MODE_LABELS = {"AH Median", "Orders", "AH Low"};
    private static final String[] RANGE_LABELS = {"24h", "7d", "30d"};
    private static final int[] RANGE_MIN = {1440, 10080, 43200};
    private static final String[] COLS = {"Current", "Low", "Average", "High", "Range", "Change"};
    private static final int ROW = 24;

    private record Stat(String key, String name, int basis, int count, double first, double now, double low, double high, double avg) {
        double position() {
            return high > low ? (now - low) / (high - low) * 100 : 50;
        }

        double change() {
            return first > 0 ? (now - first) / first * 100 : 0;
        }

        double value(int col) {
            return switch (col) {
                case 0 -> now;
                case 1 -> low;
                case 2 -> avg;
                case 3 -> high;
                case 4 -> position();
                default -> change();
            };
        }
    }

    private record Loaded(long at, List<Stat> stats) {
    }

    private static final Map<String, Loaded> CACHE = new HashMap<>();
    private static int mode = 0;
    private static int range = 0;
    private static int sortCol = 5;
    private static boolean sortDesc = true;
    private static String picked = "";

    private final List<Stat> rows = new ArrayList<>();
    private final List<int[]> hits = new ArrayList<>();
    private boolean loading;
    private long failedAt;
    private String error = "";
    private double scroll;
    private int listTop;
    private int headY;
    private int[] colX = new int[6];
    private boolean[] colOn = new boolean[6];
    private int listRight;
    private int panelX;
    private boolean showPanel;

    public StatsScreen() {
        super("Market stats");
    }

    @Override
    protected Page page() {
        return Page.STATS;
    }

    @Override
    protected void build() {
        stackToggle(cr - 54, ct - 1, this::rebuild);
        headY = ct + 48;
        listTop = headY + 16;
        int w = cr - cl;
        showPanel = w >= 360;
        panelX = showPanel ? cr - 168 : cr;
        listRight = showPanel ? panelX - 6 : cr;
        int listW = listRight - cl;
        colOn = new boolean[]{true, listW >= 280, true, listW >= 330, listW >= 240, true};
        int x = listRight - 6;
        int[] widths = {52, 48, 56, 48, 70, 58};
        for (int c = 5; c >= 0; c--) {
            if (!colOn[c]) {
                continue;
            }
            colX[c] = x;
            x -= widths[c];
        }
        load();
        rebuild();
    }

    private String cacheKey() {
        return MODES[mode] + "|" + minutes();
    }

    private int minutes() {
        return RANGE_MIN[range];
    }

    private void load() {
        String key = cacheKey();
        Loaded hit = CACHE.get(key);
        long maxAge = minutes() <= 1440 ? 90_000L : 600_000L;
        if (hit != null && System.currentTimeMillis() - hit.at() < maxAge) {
            return;
        }
        if (loading) {
            return;
        }
        loading = true;
        error = "";
        MarketData.api("/mod/stats?f=" + MODES[mode] + "&m=" + minutes()).thenAccept(json -> minecraft.execute(() -> {
            if (json == null || !json.isJsonArray()) {
                failedAt = System.currentTimeMillis();
                error = json != null && json.isJsonObject() && json.getAsJsonObject().has("error") ? json.getAsJsonObject().get("error").getAsString() : "Couldn't load stats";
            }
            if (json != null && json.isJsonArray()) {
                List<Stat> list = new ArrayList<>();
                for (JsonElement el : json.getAsJsonArray()) {
                    JsonObject o = el.getAsJsonObject();
                    String k = o.get("k").getAsString();
                    list.add(new Stat(k, ItemKeys.name(k), o.has("b") && !o.get("b").isJsonNull() ? Math.max(1, o.get("b").getAsInt()) : 1,
                            o.get("n").getAsInt(), num(o, "f"), num(o, "c"), num(o, "lo"), num(o, "hi"), num(o, "avg")));
                }
                CACHE.put(key, new Loaded(System.currentTimeMillis(), list));
            }
            loading = false;
            if (key.equals(cacheKey())) {
                rebuild();
            } else {
                load();
            }
        }));
    }

    private static double num(JsonObject o, String f) {
        return o.has(f) && !o.get(f).isJsonNull() ? o.get(f).getAsDouble() : 0;
    }

    @Override
    protected void categoryChanged() {
        scroll = 0;
        rebuild();
    }

    private void rebuild() {
        Loaded l = CACHE.get(cacheKey());
        if (l != null || !loading) {
            rows.clear();
        }
        if (l != null) {
            for (Stat s : l.stats()) {
                if (catPass(s.key())) {
                    rows.add(s);
                }
            }
        }
        Comparator<Stat> cmp = Comparator.comparingDouble(s -> s.value(sortCol) / (sortCol <= 3 ? div(s) : 1));
        if (sortDesc) {
            cmp = cmp.reversed();
        }
        rows.sort(cmp.thenComparing(Stat::name));
        if (picked.isEmpty() || rows.stream().noneMatch(s -> s.key().equals(picked))) {
            picked = rows.isEmpty() ? "" : rows.get(0).key();
        }
        double max = Math.max(0, rows.size() * ROW - (cb - listTop));
        scroll = Math.max(0, Math.min(scroll, max));
    }

    private static double div(Stat s) {
        return Settings.get().perStack ? 1 : Math.max(1, s.basis());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        for (int[] h : hits) {
            if (Ui.inside(mx, my, h[0], h[1], h[2], h[3])) {
                switch (h[4]) {
                    case 0 -> mode = h[5];
                    case 1 -> range = h[5];
                    case 4 -> {
                        MarketScreen.select(picked);
                        minecraft.gui.setScreen(new MarketScreen());
                        return true;
                    }
                    default -> {
                        if (sortCol == h[5]) {
                            sortDesc = !sortDesc;
                        } else {
                            sortCol = h[5];
                            sortDesc = true;
                        }
                    }
                }
                scroll = 0;
                load();
                rebuild();
                return true;
            }
        }
        if (Ui.inside(mx, my, cl, listTop, listRight, cb)) {
            int index = (int) ((my - listTop + scroll) / ROW);
            if (index >= 0 && index < rows.size()) {
                String key = rows.get(index).key();
                if (key.equals(picked) && event.button() == 0) {
                    MarketScreen.select(key);
                    minecraft.gui.setScreen(new MarketScreen());
                } else {
                    picked = key;
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (Ui.inside(mx, my, cl, listTop, listRight, cb)) {
            double max = Math.max(0, rows.size() * ROW - (cb - listTop));
            scroll = Math.max(0, Math.min(max, scroll - vertical * ROW * 2));
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    public void tick() {
        Loaded l = CACHE.get(cacheKey());
        long maxAge = minutes() <= 1440 ? 90_000L : 600_000L;
        if (!loading && System.currentTimeMillis() - failedAt > 15_000L && (l == null || System.currentTimeMillis() - l.at() > maxAge)) {
            load();
        }
    }

    private int chipRow(GuiGraphicsExtractor g, String[] labels, int selected, int x, int y, int group) {
        for (int i = 0; i < labels.length; i++) {
            int w = Ui.chip(g, font, labels[i], x, y, i == selected);
            hits.add(new int[]{x, y, x + w, y + 12, group, i});
            x += w + 3;
        }
        return x;
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor g, int mx, int my, float delta) {
        hits.clear();
        Ui.text(g, font, "DonutSMP", cl, ct, Ui.GOLD);
        Ui.text(g, font, "Market stats", cl, ct + 10, Ui.TEXT);
        String sub = switch (mode) {
            case 1 -> "Buy order prices over " + RANGE_LABELS[range];
            case 2 -> "Auction house lows over " + RANGE_LABELS[range];
            default -> "Auction house medians over " + RANGE_LABELS[range];
        };
        Ui.text(g, font, Ui.clip(font, sub, Math.max(40, cr - 60 - cl)), cl + font.width("Market stats") + 8, ct + 10, Ui.MUTED);
        int y2 = ct + 24;
        int x = chipRow(g, MODE_LABELS, mode, cl, y2, 0);
        x += 8;
        chipRow(g, RANGE_LABELS, range, x, y2, 1);
        String count = loading ? "Loading..." : rows.size() + " markets";
        Ui.right(g, font, count, cr - 58, y2 + 2, Ui.MUTED);

        Ui.text(g, font, "Item", cl + 20, headY, Ui.MUTED);
        for (int c = 0; c < 6; c++) {
            if (!colOn[c]) {
                continue;
            }
            String label = COLS[c] + (sortCol == c ? (sortDesc ? " v" : " ^") : "");
            int w = font.width(label);
            boolean over = Ui.inside(mx, my, colX[c] - w - 2, headY - 2, colX[c] + 2, headY + 10);
            Ui.right(g, font, label, colX[c], headY, sortCol == c ? Ui.TEXT : over ? Ui.SOFT : Ui.MUTED);
            hits.add(new int[]{colX[c] - w - 2, headY - 2, colX[c] + 2, headY + 10, 3, c});
        }
        g.fill(cl, headY + 12, listRight, headY + 13, Ui.BORDER);

        g.enableScissor(cl, listTop, listRight + 1, cb);
        int y = listTop - (int) scroll;
        int firstCol = listRight;
        for (int c = 0; c < 6; c++) {
            if (colOn[c]) {
                firstCol = Math.min(firstCol, colX[c] - (c == 0 ? 48 : c == 4 ? 66 : 56));
            }
        }
        for (int i = 0; i < rows.size(); i++, y += ROW) {
            if (y + ROW < listTop || y > cb) {
                continue;
            }
            Stat s = rows.get(i);
            boolean on = s.key().equals(picked);
            boolean hover = Ui.inside(mx, my, cl, Math.max(y, listTop), listRight, Math.min(y + ROW, cb));
            if (on) {
                g.fill(cl, y, listRight, y + ROW - 1, Ui.SELECT);
                g.fill(cl, y, cl + 2, y + ROW - 1, Ui.GOLD);
            } else if (hover) {
                g.fill(cl, y, listRight, y + ROW - 1, Ui.HOVER);
            } else if (i % 2 == 0) {
                g.fill(cl, y, listRight, y + ROW - 1, Ui.STRIPE);
            }
            Ui.item(g, ItemKeys.stackFor(s.key()), cl + 2, y + 4, 0.85F);
            Ui.text(g, font, Ui.clip(font, s.name(), firstCol - 6 - (cl + 20)), cl + 20, y + 2, Ui.TEXT);
            String unit = s.basis() > 1 ? (Settings.get().perStack ? " · per x" + s.basis() : " · each") : "";
            Ui.text(g, font, Ui.clip(font, s.count() + " scans" + unit, firstCol - 6 - (cl + 20)), cl + 20, y + 12, 0xFF61656E);
            double dv = div(s);
            int ty = y + 8;
            if (colOn[0]) {
                Ui.right(g, font, Money.format(s.now() / dv), colX[0], ty, Ui.TEXT);
            }
            if (colOn[1]) {
                Ui.right(g, font, Money.format(s.low() / dv), colX[1], ty, Ui.RED);
            }
            if (colOn[2]) {
                Ui.right(g, font, Money.format(s.avg() / dv), colX[2], ty, Ui.SOFT);
            }
            if (colOn[3]) {
                Ui.right(g, font, Money.format(s.high() / dv), colX[3], ty, Ui.GREEN);
            }
            if (colOn[4]) {
                int bx2 = colX[4] - font.width("100%") - 4;
                int bx1 = bx2 - 28;
                g.fill(bx1, ty + 3, bx2, ty + 5, 0x40FFFFFF);
                int px = bx1 + (int) Math.round(Math.max(0, Math.min(100, s.position())) / 100 * (bx2 - bx1 - 2));
                g.fill(px, ty + 1, px + 2, ty + 7, Ui.GOLD);
                Ui.right(g, font, Math.round(s.position()) + "%", colX[4], ty, Ui.SOFT);
            }
            if (colOn[5]) {
                double ch = s.change();
                String pct = Money.percent(ch);
                int pw = font.width(pct) + 6;
                int px = colX[5] - pw;
                int bg = ch > 0 ? 0x334CE5B1 : ch < 0 ? 0x33FF6B81 : 0x22FFFFFF;
                g.fill(px, ty - 2, colX[5], ty + 10, bg);
                Ui.right(g, font, pct, colX[5] - 3, ty, ch > 0 ? Ui.GREEN : ch < 0 ? Ui.RED : Ui.MUTED);
            }
        }
        g.disableScissor();
        if (rows.isEmpty() && !loading) {
            if (!error.isEmpty()) {
                Ui.text(g, font, Ui.clip(font, error + ". Retrying in a few seconds.", listRight - cl - 8), cl + 4, listTop + 6, Ui.RED);
            } else {
                Ui.text(g, font, "No history for this metric and time. Try a longer range.", cl + 4, listTop + 6, Ui.MUTED);
            }
        }
        int track = cb - listTop;
        if (rows.size() * ROW > track) {
            int thumb = Math.max(14, track * track / (rows.size() * ROW));
            double max = rows.size() * ROW - track;
            int ty = listTop + (int) ((track - thumb) * (scroll / max));
            g.fill(listRight + 1, ty, listRight + 3, ty + thumb, 0x55FFFFFF);
        }
        if (showPanel) {
            drawPanel(g, mx, my);
        }
    }

    private Stat pickedStat() {
        for (Stat s : rows) {
            if (s.key().equals(picked)) {
                return s;
            }
        }
        return null;
    }

    private void drawPanel(GuiGraphicsExtractor g, int mx, int my) {
        Ui.card(g, panelX, listTop - 16, cr, cb, Ui.GLASS, Ui.BORDER);
        Stat s = pickedStat();
        if (s == null) {
            Ui.text(g, font, "Pick an item", panelX + 8, listTop - 6, Ui.MUTED);
            return;
        }
        double dv = div(s);
        double ch = s.change();
        Ui.item(g, ItemKeys.stackFor(s.key()), panelX + 7, listTop - 10, 1F);
        Ui.text(g, font, Ui.clip(font, s.name(), cr - panelX - 16), panelX + 28, listTop - 10, Ui.TEXT);
        Ui.text(g, font, MODE_LABELS[mode] + " · " + RANGE_LABELS[range], panelX + 28, listTop, Ui.MUTED);
        Ui.text(g, font, "Current price", panelX + 8, listTop + 16, Ui.MUTED);
        Ui.text(g, font, Money.format(s.now() / dv), panelX + 8, listTop + 26, Ui.TEXT);
        Ui.text(g, font, Money.percent(ch), panelX + 8, listTop + 38, ch > 0 ? Ui.GREEN : ch < 0 ? Ui.RED : Ui.MUTED);
        Ui.text(g, font, "vs previous " + RANGE_LABELS[range], panelX + 8, listTop + 48, 0xFF61656E);
        int y = listTop + 64;
        String[][] cells = {
                {"Low", Money.format(s.low() / dv)},
                {"Average", Money.format(s.avg() / dv)},
                {"High", Money.format(s.high() / dv)}
        };
        int[] colors = {Ui.RED, Ui.SOFT, Ui.GREEN};
        int col = (cr - panelX - 16) / 3;
        for (int i = 0; i < 3; i++) {
            int sx = panelX + 8 + i * col;
            Ui.text(g, font, cells[i][0], sx, y, Ui.MUTED);
            Ui.text(g, font, Ui.clip(font, cells[i][1], col - 4), sx, y + 10, colors[i]);
        }
        y += 32;
        Ui.text(g, font, "Range position", panelX + 8, y, Ui.MUTED);
        Ui.right(g, font, Math.round(s.position()) + "%", cr - 8, y, Ui.SOFT);
        int bx1 = panelX + 8;
        int bx2 = cr - 8;
        g.fill(bx1, y + 12, bx2, y + 15, 0x40FFFFFF);
        int px = bx1 + (int) Math.round(Math.max(0, Math.min(100, s.position())) / 100.0 * (bx2 - bx1 - 2));
        g.fill(px, y + 10, px + 2, y + 17, Ui.GOLD);
        String open = "Open chart";
        int ow = font.width(open) + 8;
        int ox = panelX + 8;
        int oy = cb - 18;
        boolean over = Ui.inside(mx, my, ox, oy, ox + ow, oy + 12);
        g.fill(ox, oy, ox + ow, oy + 12, over ? Ui.ACTIVE : Ui.CHIP);
        Ui.text(g, font, open, ox + 4, oy + 2, Ui.TEXT);
        hits.add(new int[]{ox, oy, ox + ow, oy + 12, 4, 0});
    }
}
