package com.donutmarket.mod;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SwingsScreen extends Dashboard {
    private static final String[] SIGNALS = {"buy", "sell", "investment"};
    private static final String[] SIGNAL_LABELS = {"Buy", "Sell on AH", "Sell to orders"};
    private static final int CARD_H = 38;

    private Map<String, Swings.Data> data = Collections.emptyMap();
    private long loadedAt;
    private static String query = "";
    private static boolean showHidden;

    private record Card(String key, String name, Swings.State state, boolean hidden) {
    }

    private final List<Card> cards = new ArrayList<>();
    private EditBox search;
    private double scroll;
    private int listTop;
    private int cols;
    private int cardW;
    private int chipsY;
    private int[][] chipBoxes = new int[4][];
    private int activeCount;
    private long builtFor = -1;

    public SwingsScreen() {
        super("Advanced swings");
    }

    @Override
    protected Page page() {
        return Page.SWINGS;
    }

    @Override
    protected void build() {
        int searchW = Math.min(170, (cr - cl) / 2);
        search = new EditBox(font, cl + 5, ct + 4, searchW - 10, 10, Component.literal("Search"));
        search.setBordered(false);
        search.setMaxLength(40);
        search.setTextColor(Ui.TEXT);
        search.setValue(query);
        search.setHint(Component.literal("Search markets...").withColor(Ui.MUTED));
        search.setResponder(t -> {
            query = t;
            scroll = 0;
            rebuild();
        });
        addRenderableWidget(search);
        stackToggle(cr - 54, ct - 1, () -> {
        });
        chipsY = ct + 22;
        listTop = ct + 40;
        cols = cr - cl >= 380 ? 2 : 1;
        cardW = (cr - cl - 6 - (cols - 1) * 6) / cols;
        setInitialFocus(search);
        data = Swings.data();
        loadedAt = Swings.loadedAt();
        rebuild();
    }

    @Override
    protected void categoryChanged() {
        scroll = 0;
        rebuild();
    }

    private void rebuild() {
        builtFor = MarketData.marketLoadedAt() ^ loadedAt;
        cards.clear();
        String q = query.trim().toLowerCase(Locale.ROOT);
                List<String> off = Settings.get().swingSignalsOff;
        activeCount = 0;
        for (MarketItem item : MarketData.all()) {
            String key = item.key();
            if (!catPass(key)) {
                continue;
            }
            String name = ItemKeys.name(key);
            if (!q.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(q) && !key.replace('_', ' ').contains(q)) {
                continue;
            }
            boolean hidden = Settings.get().swingHidden.contains(key);
            if (hidden && !showHidden) {
                continue;
            }
            Swings.State st = Swings.state(key, data.get(key));
            if (st.active() && off.contains(st.signal())) {
                continue;
            }
            if (st.active() && !hidden) {
                activeCount++;
            }
            cards.add(new Card(key, name, st, hidden));
        }
        cards.sort((a, b) -> {
            if (a.hidden() != b.hidden()) {
                return a.hidden() ? 1 : -1;
            }
            boolean aw = "waiting".equals(a.state().signal());
            boolean bw = "waiting".equals(b.state().signal());
            if (aw != bw) {
                return aw ? 1 : -1;
            }
            if (aw) {
                return a.name().compareToIgnoreCase(b.name());
            }
            int p = b.state().priority() - a.state().priority();
            if (p != 0) {
                return p;
            }
            double ma = Math.max(Math.abs(a.state().ahChange()), Math.abs(a.state().ordersChange()));
            double mb = Math.max(Math.abs(b.state().ahChange()), Math.abs(b.state().ordersChange()));
            return Double.compare(mb, ma);
        });
        clampScroll();
    }

    private int rowsHeight() {
        int rows = (cards.size() + cols - 1) / cols;
        return rows * (CARD_H + 4);
    }

    private void clampScroll() {
        double max = Math.max(0, rowsHeight() - (cb - listTop));
        scroll = Math.max(0, Math.min(scroll, max));
    }

    @Override
    public void tick() {
        data = Swings.data();
        loadedAt = Swings.loadedAt();
        if (builtFor != (MarketData.marketLoadedAt() ^ loadedAt)) {
            rebuild();
        }
    }

    private int cardAt(double mx, double my) {
        if (!Ui.inside(mx, my, cl, listTop, cr, cb)) {
            return -1;
        }
        int col = (int) ((mx - cl) / (cardW + 6));
        int row = (int) ((my - listTop + scroll) / (CARD_H + 4));
        if (col < 0 || col >= cols) {
            return -1;
        }
        double inY = (my - listTop + scroll) - row * (CARD_H + 4);
        if (inY > CARD_H) {
            return -1;
        }
        int index = row * cols + col;
        return index < cards.size() ? index : -1;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        for (int i = 0; i < chipBoxes.length; i++) {
            int[] b = chipBoxes[i];
            if (b != null && Ui.inside(mx, my, b[0], b[1], b[2], b[3])) {
                if (i < 3) {
                    List<String> off = Settings.get().swingSignalsOff;
                    if (!off.remove(SIGNALS[i])) {
                        off.add(SIGNALS[i]);
                    }
                    Settings.save();
                } else {
                    showHidden = !showHidden;
                }
                rebuild();
                return true;
            }
        }
        int index = cardAt(mx, my);
        if (index >= 0) {
            Card c = cards.get(index);
            if (event.button() == 1) {
                if (!Settings.get().swingHidden.remove(c.key())) {
                    Settings.get().swingHidden.add(c.key());
                }
                Settings.save();
                rebuild();
            } else {
                MarketScreen.select(c.key());
                minecraft.gui.setScreen(new MarketScreen());
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (Ui.inside(mx, my, cl, listTop, cr, cb)) {
            scroll -= vertical * (CARD_H + 4);
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor g, int mx, int my, float delta) {
        int searchW = Math.min(170, (cr - cl) / 2);
        Ui.card(g, cl, ct, cl + searchW, ct + 16);
        Ui.text(g, font, "Advanced swings", cl + searchW + 8, ct + 4, Ui.TEXT);
        String hint = "right-click hides a market";
        int hx = cl + searchW + 16 + font.width("Advanced swings");
        if (hx + font.width(hint) < cr - 60) {
            Ui.text(g, font, hint, hx, ct + 4, 0xFF61656E);
        }

        int x = cl;
        List<String> off = Settings.get().swingSignalsOff;
        int[] colors = {Ui.GREEN, Ui.RED, Ui.GOLD};
        for (int i = 0; i < 3; i++) {
            boolean on = !off.contains(SIGNALS[i]);
            int w = font.width(SIGNAL_LABELS[i]) + 16;
            g.fill(x, chipsY, x + w, chipsY + 13, on ? (colors[i] & 0x00FFFFFF) | 0x40000000 : Ui.CHIP);
            g.fill(x + 4, chipsY + 4, x + 8, chipsY + 8, on ? colors[i] : Ui.MUTED);
            Ui.text(g, font, SIGNAL_LABELS[i], x + 11, chipsY + 3, on ? Ui.TEXT : Ui.MUTED);
            chipBoxes[i] = new int[]{x, chipsY, x + w, chipsY + 13};
            x += w + 4;
        }
        int hiddenCount = Settings.get().swingHidden.size();
        String hiddenLabel = (showHidden ? "Hide hidden" : "Show hidden") + " (" + hiddenCount + ")";
        int hw = font.width(hiddenLabel) + 8;
        if (hiddenCount > 0 || showHidden) {
            g.fill(x, chipsY, x + hw, chipsY + 13, showHidden ? Ui.CHIP_ON : Ui.CHIP);
            Ui.text(g, font, hiddenLabel, x + 4, chipsY + 3, Ui.TEXT);
            chipBoxes[3] = new int[]{x, chipsY, x + hw, chipsY + 13};
        } else {
            chipBoxes[3] = null;
        }
        String count = loadedAt == 0 ? "Loading..." : activeCount + " active · " + cards.size() + " markets";
        Ui.right(g, font, count, cr, chipsY + 3, Ui.MUTED);

        g.enableScissor(cl, listTop, cr + 1, cb);
        for (int i = 0; i < cards.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int cx = cl + col * (cardW + 6);
            int cy = listTop + row * (CARD_H + 4) - (int) scroll;
            if (cy + CARD_H < listTop || cy > cb) {
                continue;
            }
            drawCard(g, cards.get(i), cx, cy, mx, my);
        }
        g.disableScissor();
        if (cards.isEmpty()) {
            Ui.text(g, font, loadedAt == 0 ? "Loading swings..." : "No markets match", cl + 4, listTop + 6, Ui.MUTED);
        }
        int track = cb - listTop;
        int total = rowsHeight();
        if (total > track) {
            int thumb = Math.max(14, track * track / total);
            int ty = listTop + (int) ((track - thumb) * (scroll / (total - track)));
            g.fill(cr + 2, ty, cr + 4, ty + thumb, 0x55FFFFFF);
        }
    }

    private void drawCard(GuiGraphicsExtractor g, Card c, int x, int y, int mx, int my) {
        Swings.State st = c.state();
        boolean hover = Ui.inside(mx, my, x, Math.max(y, listTop), x + cardW, Math.min(y + CARD_H, cb));
        int border = st.active() ? Swings.color(st) : hover ? Ui.SELECT_BORDER : Ui.BORDER;
        Ui.card(g, x, y, x + cardW, y + CARD_H, hover ? Ui.SELECT : Ui.CARD, border);
        if (st.active()) {
            g.fill(x + 1, y + 1, x + 3, y + CARD_H - 1, Swings.color(st));
        }
        Ui.item(g, ItemKeys.stackFor(c.key()), x + 6, y + 4, 1F);
        String tag = c.hidden() ? "HIDDEN" : Swings.shortLabel(st);
        int tagColor = c.hidden() ? Ui.MUTED : st.active() ? Swings.color(st) : Ui.MUTED;
        int tw = font.width(tag) + 6;
        Ui.tag(g, font, tag, x + cardW - 5 - tw, y + 4, tagColor);
        Ui.text(g, font, Ui.clip(font, c.name(), cardW - 36 - tw), x + 26, y + 5, c.hidden() ? Ui.MUTED : Ui.TEXT);

        MarketItem item = MarketData.item(c.key());
        Swings.Data d = data.get(c.key());
        int basis = d != null ? d.basis() : item != null ? item.basis() : 1;
        double dv = Settings.get().perStack ? 1 : Math.max(1, basis);
        String unit = basis > 1 ? (Settings.get().perStack ? " per x" + basis : " each") : "";
        if ("waiting".equals(st.signal())) {
            double p = st.ordersNow() > 0 ? st.ordersNow() : st.ahNow();
            Ui.text(g, font, p > 0 ? Money.format(p / dv) + unit : "Waiting for data", x + 26, y + 16, Ui.SOFT);
            int need = 3;
            Ui.text(g, font, Ui.clip(font, Math.min(st.usable(), need) + "/" + need + " usable scans" + (st.shop() ? ", AH low only" : ""), cardW - 32), x + 26, y + 27, Ui.MUTED);
            return;
        }
        String price = Money.format(st.mainPrice() / dv);
        Ui.text(g, font, price, x + 26, y + 16, Ui.TEXT);
        int px = x + 26 + font.width(price);
        Ui.text(g, font, unit, px, y + 16, Ui.MUTED);
        px += font.width(unit) + 4;
        double ch = st.mainChange();
        Ui.text(g, font, Money.percent(ch), px, y + 16, ch >= 0 ? Ui.GREEN : Ui.RED);
        String what = st.shop() ? "AH low avg" : "sell".equals(st.signal()) ? "/ah median" : "/orders";
        String meta = what + " usually ~" + Money.format(st.usual() / dv);
        Ui.text(g, font, Ui.clip(font, meta, cardW - 32), x + 26, y + 27, Ui.MUTED);
    }
}
