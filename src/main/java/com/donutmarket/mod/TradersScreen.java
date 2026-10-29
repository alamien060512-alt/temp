package com.donutmarket.mod;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class TradersScreen extends Dashboard {
    private static final String[] SORTS = {"rating", "recent", "active"};
    private static final String[] SORT_LABELS = {"Top rated", "Recent", "Most active"};
    private static final String[] ACTIVITY = {"", "Quiet", "Occasional", "Regular", "Active", "Very active"};
    private static final int ROW = 46;
    private static final String STAR = "★";

    private static final class Trader {
        String username;
        double ratingAvg;
        int ratingCount;
        int days3;
        int changes3;
        int days30;
        boolean has3;
        long lastChange;
        List<String> items = new ArrayList<>();
        int positions;
        String status = "";
    }

    private static int sortIndex = 0;
    private static String query = "";

    private final List<Trader> traders = new ArrayList<>();
    private final List<int[]> hits = new ArrayList<>();
    private EditBox search;
    private int total;
    private boolean loading;
    private int request;
    private double scroll;
    private int listTop;
    private long lastSearchEdit;
    private boolean searchDirty;
    private long loadedAt;

    public TradersScreen() {
        super("Active traders");
    }

    @Override
    protected Page page() {
        return Page.TRADERS;
    }

    @Override
    protected void build() {
        int searchW = Math.min(150, (cr - cl) / 3);
        search = new EditBox(font, cl + 5, ct + 4, searchW - 10, 10, Component.literal("Search"));
        search.setBordered(false);
        search.setMaxLength(24);
        search.setTextColor(Ui.TEXT);
        search.setValue(query);
        search.setHint(Component.literal("Search traders...").withColor(Ui.MUTED));
        search.setResponder(t -> {
            query = t.trim();
            searchDirty = true;
            lastSearchEdit = System.currentTimeMillis();
        });
        addRenderableWidget(search);
        listTop = ct + 24;
        setInitialFocus(search);
        if (traders.isEmpty()) {
            load(false);
        }
    }

    private void load(boolean more) {
        int id = ++request;
        loading = true;
        int offset = more ? traders.size() : 0;
        String q = query.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
        MarketData.api("/mod/traders?sort=" + SORTS[sortIndex] + "&o=" + offset + "&q=" + q).thenAccept(json -> minecraft.execute(() -> {
            if (id != request) {
                return;
            }
            loading = false;
            if (json == null || !json.isJsonObject()) {
                return;
            }
            JsonObject root = json.getAsJsonObject();
            total = root.has("total") ? root.get("total").getAsInt() : 0;
            if (!more) {
                traders.clear();
                scroll = 0;
            }
            JsonArray rows = root.has("rows") && root.get("rows").isJsonArray() ? root.getAsJsonArray("rows") : new JsonArray();
            for (JsonElement el : rows) {
                traders.add(parse(el.getAsJsonObject()));
            }
            loadedAt = System.currentTimeMillis();
        }));
    }

    private static Trader parse(JsonObject o) {
        Trader t = new Trader();
        t.username = str(o, "username");
        t.ratingAvg = num(o, "rating_avg");
        t.ratingCount = (int) num(o, "rating_count");
        t.has3 = o.has("active_days_3d") && !o.get("active_days_3d").isJsonNull();
        t.days3 = (int) num(o, "active_days_3d");
        t.changes3 = (int) num(o, "changes_3d");
        t.days30 = (int) num(o, "active_days_30d");
        String last = str(o, "last_change");
        if (last.isEmpty()) {
            last = str(o, "updated_at");
        }
        try {
            t.lastChange = last.isEmpty() ? 0 : OffsetDateTime.parse(last.replace(" ", "T")).toInstant().toEpochMilli();
        } catch (Exception e) {
            t.lastChange = 0;
        }
        Set<String> items = new LinkedHashSet<>();
        if (o.has("investments") && o.get("investments").isJsonArray()) {
            JsonArray inv = o.getAsJsonArray("investments");
            t.positions = inv.size();
            for (JsonElement el : inv) {
                if (el.isJsonObject() && el.getAsJsonObject().has("item")) {
                    items.add(el.getAsJsonObject().get("item").getAsString());
                }
            }
        }
        t.items.addAll(items);
        return t;
    }

    private static String str(JsonObject o, String f) {
        return o.has(f) && !o.get(f).isJsonNull() ? o.get(f).getAsString() : "";
    }

    private static double num(JsonObject o, String f) {
        try {
            return o.has(f) && !o.get(f).isJsonNull() ? o.get(f).getAsDouble() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int[] activity(Trader t) {
        if (t.has3) {
            int d = t.days3;
            int stars = d >= 3 && t.changes3 >= 6 ? 5 : d >= 3 ? 4 : d == 2 ? 3 : d == 1 ? 2 : 1;
            return new int[]{stars, d, 1};
        }
        int d = t.days30;
        int stars = d >= 15 ? 5 : d >= 8 ? 4 : d >= 4 ? 3 : d >= 1 ? 2 : 1;
        return new int[]{stars, d, 0};
    }

    private static String ago(long t) {
        if (t <= 0) {
            return "";
        }
        long m = Math.max(0, (System.currentTimeMillis() - t) / 60_000L);
        if (m <= 1) {
            return "just now";
        }
        if (m < 60) {
            return m + "m ago";
        }
        long h = Math.round(m / 60.0);
        return h < 48 ? h + "h ago" : Math.round(h / 24.0) + "d ago";
    }

    private boolean own(Trader t) {
        return t.username.equalsIgnoreCase(Settings.get().portfolio.trim()) && Settings.get().portfolioKey.length() >= 4;
    }

    private void rate(Trader t, int stars) {
        if (own(t)) {
            return;
        }
        t.status = "Saving...";
        JsonObject body = new JsonObject();
        body.addProperty("u", t.username);
        body.addProperty("rater", Settings.get().clientId);
        body.addProperty("stars", stars);
        MarketData.post("/mod/rate", body).thenAccept(out -> minecraft.execute(() -> {
            if (out == null || !out.has("ok") || !out.get("ok").getAsBoolean()) {
                t.status = out != null && out.has("error") ? out.get("error").getAsString() : "Could not save";
                return;
            }
            t.ratingAvg = num(out, "rating_avg");
            t.ratingCount = (int) num(out, "rating_count");
            t.status = "";
            Settings.get().myRatings.put(t.username.toLowerCase(Locale.ROOT), stars);
            Settings.save();
        }));
    }

    @Override
    public void tick() {
        if (searchDirty && System.currentTimeMillis() - lastSearchEdit > 300) {
            searchDirty = false;
            load(false);
        }
        if (!loading && traders.size() <= 10 && System.currentTimeMillis() - loadedAt > 120_000L) {
            load(false);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        for (int[] h : hits) {
            if (!Ui.inside(mx, my, h[0], h[1], h[2], h[3])) {
                continue;
            }
            switch (h[4]) {
                case 0 -> {
                    if (sortIndex != h[5]) {
                        sortIndex = h[5];
                        load(false);
                    }
                }
                case 1 -> load(false);
                case 2 -> rate(traders.get(h[5]), h[6]);
                case 3 -> {
                    Trader t = traders.get(h[5]);
                    minecraft.gui.setScreen(own(t) ? new InvestmentsScreen() : InvestmentsScreen.viewing(t.username));
                }
                case 4 -> load(true);
                default -> {
                }
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (Ui.inside(mx, my, cl, listTop, cr, cb)) {
            double max = Math.max(0, contentHeight() - (cb - listTop));
            scroll = Math.max(0, Math.min(max, scroll - vertical * ROW));
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    private int contentHeight() {
        return traders.size() * ROW + (total > traders.size() ? 22 : 0);
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor g, int mx, int my, float delta) {
        hits.clear();
        int searchW = Math.min(150, (cr - cl) / 3);
        Ui.card(g, cl, ct, cl + searchW, ct + 16);
        int x = cl + searchW + 6;
        for (int i = 0; i < SORTS.length; i++) {
            int w = Ui.chip(g, font, SORT_LABELS[i], x, ct + 2, i == sortIndex);
            hits.add(new int[]{x, ct + 2, x + w, ct + 14, 0, i});
            x += w + 3;
        }
        String refresh = loading ? "Loading..." : "Refresh";
        boolean overR = Ui.inside(mx, my, cr - font.width(refresh) - 2, ct + 2, cr + 2, ct + 14);
        Ui.right(g, font, refresh, cr, ct + 4, overR ? Ui.TEXT : Ui.BLUE);
        hits.add(new int[]{cr - font.width(refresh) - 2, ct + 2, cr + 2, ct + 14, 1, 0});
        if (total > 0 && x + 10 + font.width(total + " traders") < cr - font.width(refresh) - 8) {
            Ui.right(g, font, total + " traders", cr - font.width(refresh) - 8, ct + 4, Ui.MUTED);
        }

        g.enableScissor(cl, listTop, cr + 1, cb);
        int y = listTop - (int) scroll;
        for (int i = 0; i < traders.size(); i++, y += ROW) {
            if (y + ROW < listTop || y > cb) {
                continue;
            }
            drawTrader(g, traders.get(i), i, y, mx, my);
        }
        if (total > traders.size()) {
            int left = total - traders.size();
            String more = loading ? "Loading..." : "Show " + Math.min(10, left) + " more (" + left + " left)";
            boolean over = Ui.inside(mx, my, cl, Math.max(y, listTop), cr, Math.min(y + 18, cb));
            Ui.card(g, cl, y + 2, cr, y + 18, over ? Ui.SELECT : Ui.CARD, Ui.BORDER);
            Ui.text(g, font, more, cl + (cr - cl - font.width(more)) / 2, y + 6, Ui.BLUE);
            if (y + 2 < cb && y + 18 > listTop) {
                hits.add(new int[]{cl, Math.max(y + 2, listTop), cr, Math.min(y + 18, cb), 4, 0});
            }
        }
        g.disableScissor();
        if (traders.isEmpty()) {
            String msg = loading ? "Loading traders..." : !query.isBlank() ? "No public trader matches \"" + query + "\"" : "No public portfolios yet";
            Ui.text(g, font, msg, cl + 4, listTop + 6, Ui.MUTED);
        }
        int track = cb - listTop;
        int totalH = contentHeight();
        if (totalH > track) {
            int thumb = Math.max(14, track * track / totalH);
            int ty = listTop + (int) ((track - thumb) * (scroll / (totalH - track)));
            g.fill(cr + 2, ty, cr + 4, ty + thumb, 0x55FFFFFF);
        }
    }

    private void drawTrader(GuiGraphicsExtractor g, Trader t, int index, int y, int mx, int my) {
        boolean hover = Ui.inside(mx, my, cl, Math.max(y, listTop), cr, Math.min(y + ROW - 3, cb));
        Ui.card(g, cl, y, cr, y + ROW - 3, hover ? Ui.SELECT : Ui.CARD, hover ? Ui.SELECT_BORDER : Ui.BORDER);
        boolean own = own(t);
        Ui.text(g, font, "#" + (index + 1), cl + 6, y + 5, Ui.MUTED);
        int x = cl + 26;
        Ui.text(g, font, t.username, x, y + 5, own ? Ui.GOLD : Ui.TEXT);
        x += font.width(t.username) + 8;
        int mine = Settings.get().myRatings.getOrDefault(t.username.toLowerCase(Locale.ROOT), 0);
        int shown = mine > 0 ? mine : (int) Math.round(t.ratingAvg);
        boolean visible = y + 5 >= listTop && y + 14 <= cb;
        int hoverStar = 0;
        for (int s = 1; s <= 5; s++) {
            int sw = font.width(STAR) + 1;
            if (!own && Ui.inside(mx, my, x + (s - 1) * sw, y + 3, x + s * sw, y + 14)) {
                hoverStar = s;
            }
        }
        for (int s = 1; s <= 5; s++) {
            int sw = font.width(STAR) + 1;
            int sx = x + (s - 1) * sw;
            boolean lit = hoverStar > 0 ? s <= hoverStar : s <= shown;
            Ui.text(g, font, STAR, sx, y + 5, lit ? Ui.GOLD : 0xFF44464A);
            if (!own && visible) {
                hits.add(new int[]{sx, y + 3, sx + sw, y + 14, 2, index, s});
            }
        }
        x += 5 * (font.width(STAR) + 1) + 6;
        String rating = t.ratingCount > 0 ? String.format(Locale.ROOT, "%.1f (%d)", t.ratingAvg, t.ratingCount) : own ? "No ratings yet" : "Click to rate";
        Ui.text(g, font, rating, x, y + 5, t.ratingCount > 0 ? Ui.SOFT : Ui.MUTED);
        x += font.width(rating) + 6;
        if (!t.status.isEmpty()) {
            Ui.text(g, font, t.status, x, y + 5, t.status.startsWith("Saving") ? Ui.MUTED : Ui.RED);
        } else if (mine > 0 && !own) {
            Ui.text(g, font, "you gave " + mine, x, y + 5, 0xFF61656E);
        }

        int[] act = activity(t);
        int bx = cl + 26;
        for (int b = 1; b <= 5; b++) {
            int h = 2 + b * 1;
            g.fill(bx, y + 24 - h, bx + 2, y + 24, b <= act[0] ? Ui.GREEN : 0xFF36373A);
            bx += 3;
        }
        String meta = ACTIVITY[act[0]] + " · " + t.positions + (t.positions == 1 ? " position" : " positions") + (t.lastChange > 0 ? " · traded " + ago(t.lastChange) : "");
        Ui.text(g, font, Ui.clip(font, meta, cr - 60 - (bx + 4)), bx + 4, y + 17, Ui.SOFT);

        int ix = cl + 26;
        int maxIcons = Math.max(1, (cr - 70 - ix) / 13);
        for (int i = 0; i < Math.min(maxIcons, t.items.size()); i++) {
            Ui.item(g, ItemKeys.stackFor(t.items.get(i)), ix, y + 29, 0.75F);
            ix += 13;
        }
        if (t.items.size() > maxIcons) {
            Ui.text(g, font, "+" + (t.items.size() - maxIcons), ix + 2, y + 31, Ui.MUTED);
        }

        String view = own ? "Yours" : "View";
        int vw = font.width(view) + 14;
        int vx = cr - 8 - vw;
        int vy = y + (ROW - 3) / 2 - 7;
        boolean overV = Ui.inside(mx, my, vx, vy, vx + vw, vy + 14);
        g.fill(vx, vy, vx + vw, vy + 14, overV ? Ui.CHIP_ON : Ui.CHIP);
        Ui.text(g, font, view, vx + 7, vy + 3, Ui.TEXT);
        if (vy >= listTop && vy + 14 <= cb) {
            hits.add(new int[]{vx, vy, vx + vw, vy + 14, 3, index});
        }
    }
}
