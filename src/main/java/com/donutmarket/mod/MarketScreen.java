package com.donutmarket.mod;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MarketScreen extends Dashboard {
    private static final int ROW = 26;
    static final String[] RANGE_LABELS = {"1H", "6H", "24H", "7D", "30D"};
    static final int[] RANGE_HOURS = {1, 6, 24, 168, 720};

    private enum Sort {
        FLIP("Flip"), GAIN("7d %"), VOLUME("Volume"), PRICE("Price"), NAME("A-Z");

        final String label;

        Sort(String label) {
            this.label = label;
        }
    }

    record Row(MarketItem item, String name, ItemStack icon) {
    }

    private record Loaded(long at, MarketData.History history) {
    }

    private static final Map<String, Loaded> HISTORY = new HashMap<>();
    private static Sort sort = Sort.FLIP;
    private static String query = "";
    private static String selected = "";

    static String selected() {
        return selected;
    }

    private final List<Row> rows = new ArrayList<>();
    private final PriceChart chart = new PriceChart();
    private EditBox search;
    private double scroll;
    private long builtFor = -1;
    private String chartFor = "";
    private boolean loading;
    private int request;

    private int bodyTop;
    private int searchW;
    private int statusX;
    private boolean twoRows;
    private int listTop;
    private int mid;
    private int panelX1;
    private int panelY1;
    private int panelX2;
    private int panelY2;
    private int chartTop;
    private int chartBottom;
    private int statsY;
    private int statRows;
    private int filterLabelX;
    private long swingsSeen;
    private long[] lastT = new long[0];
    private double[] lastO = new double[0];
    private double[] lastA = new double[0];
    private double[] lastM = new double[0];
    private double[] lastWo = new double[0];
    private double[] lastWa = new double[0];
    private boolean linesOpen;
    private int starX = -100;
    private int starY = -100;
    private int linesX;
    private int linesY;
    private int linesW;
    private static final int MEDIAN = 0xFFAD78FF;
    private static final int ORDERS_LINE = PriceGraph.ORDERS;
    private static final int LOW_LINE = PriceGraph.AH;
    private static final String[] LINE_LABELS = {"/orders", "AH median", "AH low", "Orders range", "AH median range", "AH low range", "Bulk orders", "Past bulk (24h)", "Candles"};

    public MarketScreen() {
        super("Donut Market");
    }

    public static void select(String key) {
        selected = key == null ? "" : key;
    }

    @Override
    protected Page page() {
        return Page.MARKET;
    }

    @Override
    protected void build() {
        int avail = cr - cl;
        int[] widths = {78, 54, 80};
        int full = 2 * 4 + 78 + 54 + 80;
        twoRows = avail < 90 + full;
        int bx;
        int by;
        if (twoRows) {
            searchW = avail - 46;
            int gap = 4;
            int each = (avail - gap * 2) / 3;
            for (int i = 0; i < 3; i++) {
                widths[i] = each;
            }
            widths[2] = avail - (each + gap) * 2;
            bx = cl;
            by = ct + 19;
        } else {
            searchW = Math.max(90, Math.min(170, avail - full - 44));
            bx = cl + searchW + 4;
            by = ct - 1;
        }
        statusX = twoRows ? cr : bx + full - 4;
        search = new EditBox(font, cl + 5, ct + 4, searchW - 10, 10, Component.literal("Search"));
        search.setBordered(false);
        search.setMaxLength(40);
        search.setTextColor(Ui.TEXT);
        search.setValue(query);
        search.setHint(Component.literal("Search " + CATEGORIES.get(category).label().toLowerCase(Locale.ROOT) + "...").withColor(Ui.MUTED));
        search.setResponder(t -> {
            query = t;
            scroll = 0;
            rebuild();
        });
        addRenderableWidget(search);
        int x = bx;
        addRenderableWidget(Button.builder(sortLabel(), b -> {
            sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length];
            b.setMessage(sortLabel());
            rebuild();
        }).bounds(x, by, widths[0], 18).build());
        x += widths[0] + 4;
        Button stack = stackToggle(x, by, () -> {
            rebuild();
            chart.clear();
            load(true);
            rebuildWidgets();
        });
        stack.setWidth(widths[1]);
        x += widths[1] + 4;
        volButton = addRenderableWidget(Button.builder(volLabel(), b -> pickOpen = pickOpen == 1 ? 0 : 1).bounds(x, by, widths[2], 18).build());
        pickOpen = 0;

        bodyTop = ct + (twoRows ? 44 : 24);
        listTop = bodyTop + 14;
        int listW = Math.max(140, Math.min(218, (int) ((cr - cl) * 0.42)));
        mid = cl + listW;
        panelX1 = mid + 6;
        panelY1 = bodyTop;
        panelX2 = cr;
        panelY2 = cb;

        int buttonsY = panelY2 - 24;
        statRows = panelY2 - panelY1 >= 210 ? 2 : 1;
        statsY = buttonsY - 4 - statRows * 22;
        chartTop = panelY1 + 30;
        chartBottom = statsY - 26;
        if (chartBottom - chartTop < 50 && statRows == 2) {
            statRows = 1;
            statsY = buttonsY - 4 - 22;
            chartBottom = statsY - 26;
        }
        int bw = Math.min(90, (panelX2 - panelX1 - 20) / 2);
        addRenderableWidget(Button.builder(Component.literal("Set alert"), b -> {
            if (!selected.isEmpty()) {
                AlertsScreen screen = new AlertsScreen(this);
                minecraft.gui.setScreen(screen);
                screen.preset(selected);
            }
        }).bounds(panelX1 + 7, buttonsY, bw, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Add investment"), b -> {
            if (!selected.isEmpty()) {
                minecraft.gui.setScreen(InvestmentsScreen.adding(selected));
            }
        }).bounds(panelX1 + 11 + bw, buttonsY, bw + 14, 18).build());

        filterFactor = factor(MarketData.item(selected));
        applyFilter();
        buildFilter(selected, panelX1 + 6, chartBottom + 13, panelX2 - 6, this::applyFilter);
        setInitialFocus(search);
        rebuild();
    }

    private Button volButton;
    private int pickOpen;

    private static String picked(int mask, String[] names) {
        if (mask == 0) {
            return "All";
        }
        List<String> on = new ArrayList<>();
        for (int i = 1; i < names.length; i++) {
            if ((mask & (1 << (i - 1))) != 0) {
                on.add(names[i]);
            }
        }
        return on.size() == 1 ? on.get(0) : on.size() + " picked";
    }

    private static Component volLabel() {
        return Component.literal("Volat: ").append(Component.literal(picked(volFilter, VOL_LABELS)).withColor(volFilter == 0 ? Ui.MUTED : Ui.GOLD));
    }

    private Button pickButton() {
        return pickOpen == 1 ? volButton : null;
    }

    private String[] pickNames() {
        return VOL_LABELS;
    }

    private int pickRow(double mx, double my) {
        Button b = pickButton();
        if (b == null) {
            return -1;
        }
        int w = Math.max(b.getWidth(), 80);
        int top = b.getY() + 20;
        if (mx < b.getX() || mx >= b.getX() + w || my < top || my >= top + pickNames().length * 12 + 4) {
            return -1;
        }
        return Math.max(0, Math.min(pickNames().length - 1, (int) ((my - top - 2) / 12)));
    }

    private void drawPick(GuiGraphicsExtractor g, int mx, int my) {
        Button b = pickButton();
        if (b == null) {
            return;
        }
        String[] names = pickNames();
        int mask = volFilter;
        int w = Math.max(b.getWidth(), 80);
        int top = b.getY() + 20;
        Ui.card(g, b.getX(), top, b.getX() + w, top + names.length * 12 + 4, 0xF2171819, Ui.SELECT_BORDER);
        int hover = pickRow(mx, my);
        for (int i = 0; i < names.length; i++) {
            int y = top + 2 + i * 12;
            if (i == hover) {
                g.fill(b.getX() + 1, y, b.getX() + w - 1, y + 12, Ui.HOVER);
            }
            boolean on = i == 0 ? mask == 0 : (mask & (1 << (i - 1))) != 0;
            g.fill(b.getX() + 5, y + 2, b.getX() + 13, y + 10, on ? Ui.CHIP_ON : Ui.BORDER);
            if (!on) {
                g.fill(b.getX() + 6, y + 3, b.getX() + 12, y + 9, Ui.CHIP);
            }
            Ui.text(g, font, names[i], b.getX() + 18, y + 2, on ? Ui.TEXT : Ui.SOFT);
        }
    }

    private void togglePick(int row) {
        int mask = volFilter;
        if (row == 0) {
            mask = 0;
        } else {
            mask ^= 1 << (row - 1);
            if (mask == (1 << (pickNames().length - 1)) - 1) {
                mask = 0;
            }
        }
        volFilter = mask;
        volButton.setMessage(volLabel());
        scroll = 0;
        rebuild();
    }

    private static double speed(MarketItem it) {
        double wanted = MarketData.demand(it.key());
        return wanted > 0 ? it.volume24() / wanted : 0;
    }

    private static long tiersFor = -1;
    private static final Map<String, Integer> TIERS = new HashMap<>();

    static int volumeTier(String key) {
        long at = MarketData.marketLoadedAt();
        if (at != tiersFor) {
            tiersFor = at;
            TIERS.clear();
            List<MarketItem> active = new ArrayList<>();
            for (MarketItem it : MarketData.all()) {
                TIERS.put(it.key(), 3);
                if (it.volume24() > 0 && !Swings.SHOP_ONLY.contains(it.key())) {
                    active.add(it);
                }
            }
            int n = active.size();
            Map<String, Double> score = new HashMap<>();
            List<MarketItem> byAmount = new ArrayList<>(active);
            byAmount.sort((a, b) -> Double.compare(a.volume24() / Math.max(1, a.basis()), b.volume24() / Math.max(1, b.basis())));
            for (int i = 0; i < n; i++) {
                score.put(byAmount.get(i).key(), n > 1 ? (double) i / (n - 1) : 1);
            }
            List<MarketItem> bySpeed = new ArrayList<>(active);
            bySpeed.sort((a, b) -> Double.compare(speed(a), speed(b)));
            for (int i = 0; i < n; i++) {
                score.merge(bySpeed.get(i).key(), n > 1 ? (double) i / (n - 1) : 1, Double::sum);
            }
            active.sort((a, b) -> Double.compare(score.get(b.key()), score.get(a.key())));
            for (int i = 0; i < n; i++) {
                TIERS.put(active.get(i).key(), i < n / 3.0 ? 1 : i < n * 2 / 3.0 ? 2 : 3);
            }
        }
        if (Swings.SHOP_ONLY.contains(key)) {
            return key.endsWith("_concrete") || key.endsWith("_concrete_powder") || key.endsWith("_wool") || key.endsWith("terracotta") ? 3 : 2;
        }
        return TIERS.getOrDefault(key, 3);
    }

    private Component sortLabel() {
        return Component.literal("Sort: " + sort.label);
    }

    @Override
    protected void categoryChanged() {
        scroll = 0;
        search.setHint(Component.literal("Search " + CATEGORIES.get(category).label().toLowerCase(Locale.ROOT) + "...").withColor(Ui.MUTED));
        rebuild();
        if (!rows.isEmpty()) {
            choose(rows.get(0).item().key());
        }
    }

    static List<Row> matching() {
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<Row> out = new ArrayList<>();
        for (MarketItem item : MarketData.all()) {
            if (!catPass(item.key())) {
                continue;
            }
            String name = ItemKeys.name(item.key());
            if (!q.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(q) && !item.key().replace('_', ' ').contains(q)) {
                continue;
            }
            int volLevel = Swings.volatilityLevel(item.key());
            if (volFilter != 0 && (volLevel < 0 || (volFilter & (1 << volLevel)) == 0)) {
                continue;
            }
            out.add(new Row(item, name, ItemKeys.stackFor(item.key())));
        }
        Comparator<Row> order = switch (sort) {
            case FLIP -> Comparator.comparingDouble((Row r) -> -flipRank(r.item())).thenComparing(Row::name);
            case GAIN -> Comparator.comparingDouble((Row r) -> {
                double c = r.item().change7d();
                return Double.isNaN(c) ? Double.MAX_VALUE : -c;
            });
            case VOLUME -> Comparator.comparingDouble((Row r) -> -r.item().volume24());
            case PRICE -> Comparator.comparingDouble((Row r) -> -Math.max(r.item().ordersEach(), r.item().ahEach()) * r.item().basis() / divisor(r.item()));
            case NAME -> Comparator.comparing((Row r) -> r.name().toLowerCase(Locale.ROOT));
        };
        out.sort(order);
        return out;
    }

    static String sortHead() {
        return switch (sort) {
            case FLIP, NAME -> "Flip";
            case GAIN -> "7d";
            case VOLUME -> "24h sold";
            case PRICE -> "Price";
        };
    }

    static String agoFor(MarketItem it) {
        long at = it != null ? it.scannedAt() : 0;
        if (at <= 0) {
            at = MarketData.newestScanAt();
        }
        if (at <= 0) {
            return MarketData.marketLoadedAt() == 0 ? "Connecting..." : "Waiting...";
        }
        return ago(at);
    }

    private void rebuild() {
        builtFor = MarketData.marketLoadedAt();
        rows.clear();
        rows.addAll(matching());
        clampScroll();
        if (selected.isEmpty() || MarketData.item(selected) == null) {
            if (!rows.isEmpty()) {
                choose(rows.get(0).item().key());
            }
        } else if (!selected.equals(chartFor)) {
            choose(selected);
        }
    }

    private static final double NO_AH = 999_999_999;
    private static final String[] VOL_LABELS = {"All", "Low", "Normal", "High", "Extreme"};
    private static int volFilter = 0;

    private static boolean shop(MarketItem it) {
        return Swings.SHOP_ONLY.contains(it.key());
    }

    private static double shopDelta(MarketItem it) {
        double avg = Swings.shopAverage(it.key());
        if (avg <= 0) {
            avg = it.usualAh();
        }
        return it.ahLow() > 0 && avg > 0 ? it.ahLow() - avg : Double.NaN;
    }

    record Metric(String text, int color, String note) {
    }

    static Metric metric(MarketItem it) {
        double dv = divisor(it);
        double ch = it.change7d();
        return switch (sort) {
            case GAIN -> new Metric(Double.isNaN(ch) ? "-" : Money.percent(ch), Ui.trend(ch), "");
            case VOLUME -> new Metric(it.volume24() > 0 ? count(it.volume24()) : "-", Ui.TEXT, "");
            case PRICE -> {
                double each = Math.max(it.orders(), it.ahLow()) / dv;
                yield new Metric(each > 0 ? Money.format(each) : "-", Ui.TEXT, "");
            }
            default -> {
                if (shop(it)) {
                    double d = shopDelta(it);
                    yield new Metric(Double.isNaN(d) ? "-" : Money.signed(d / dv),
                            Double.isNaN(d) ? Ui.MUTED : d > 0 ? Ui.GREEN : d < 0 ? Ui.RED : Ui.MUTED, "vs avg");
                }
                if (it.orders() > 0 && it.ahLow() <= 0) {
                    yield new Metric("+999,999,999", Ui.GOLD, "No AH");
                }
                if (it.ahLow() > 0 && it.orders() <= 0) {
                    yield new Metric(Money.signed(it.ahLow() / dv), Ui.GOLD, "No orders");
                }
                double flipEach = it.flip() / dv;
                yield new Metric(flipEach == 0 ? "-" : Money.signed(flipEach),
                        flipEach > 0 ? Ui.BLUE : flipEach < 0 ? Ui.RED : Ui.MUTED, "");
            }
        };
    }

    private static double flipRank(MarketItem it) {
        if (shop(it)) {
            double d = shopDelta(it);
            return -1e18 + (Double.isNaN(d) ? -1e15 : d);
        }
        if (it.orders() > 0 && it.ahLow() <= 0) {
            return 1e18;
        }
        if (it.ahLow() > 0 && it.orders() <= 0) {
            return 1e17 + it.ahLow();
        }
        return it.flip() / divisor(it);
    }

    private void choose(String key) {
        boolean same = key.equals(chartFor);
        selected = key;
        chartFor = key;
        if (!same) {
            chart.clear();
            lastT = new long[0];
            linesOpen = false;
            filterFactor = factor(MarketData.item(key));
            applyFilter();
            if (filterOpen) {
                filterOpen = false;
                rebuildWidgets();
                return;
            }
        }
        load(!same);
    }

    private static double factor(MarketItem it) {
        return it == null ? 1 : Math.max(1, it.basis()) / divisor(it);
    }

    private void applyFilter() {
        double[] f = filterFor(selected);
        double k = factor(MarketData.item(selected));
        chart.filter(f[0] * k, f[1] * k);
        if (lastT.length > 0) {
            overlays();
        }
    }

    private void load(boolean fresh) {
        int hours = RANGE_HOURS[range()];
        String cacheKey = selected + "|" + hours;
        Loaded hit = HISTORY.get(cacheKey);
        long maxAge = hours <= 24 ? 15_000L : 300_000L;
        if (hit != null) {
            apply(hit.history(), !fresh);
            if (System.currentTimeMillis() - hit.at() < maxAge) {
                return;
            }
        }
        loading = true;
        int id = ++request;
        String key = selected;
        MarketData.history(key, hours).thenCompose(h -> hours < 6 && inWindow(h, hours) < 2 ? MarketData.history(key, 6).thenApply(w -> w != null ? w : h) : java.util.concurrent.CompletableFuture.completedFuture(h)).thenAccept(h -> minecraft.execute(() -> {
            if (h != null) {
                HISTORY.put(key + "|" + hours, new Loaded(System.currentTimeMillis(), h));
            }
            if (id == request) {
                loading = false;
                if (h != null) {
                    apply(h, hit != null || !fresh);
                }
            }
        }));
    }

    private static int range() {
        return Math.max(0, Math.min(RANGE_HOURS.length - 1, Settings.get().chartRange));
    }

    private static int inWindow(MarketData.History h, int hours) {
        if (h == null) {
            return 0;
        }
        long since = System.currentTimeMillis() - hours * 3_600_000L;
        int n = 0;
        for (long t : h.times()) {
            if (t >= since) {
                n++;
            }
        }
        return n;
    }

    private void apply(MarketData.History h, boolean keepView) {
        long since = System.currentTimeMillis() - RANGE_HOURS[range()] * 3_600_000L;
        int start = 0;
        while (start < h.times().length && h.times()[start] < since) {
            start++;
        }
        if (start > 0 && h.times().length - start < 2) {
            start = Math.max(0, h.times().length - 2);
        }
        int n = h.times().length - start;
        MarketItem mi = MarketData.item(selected);
        double basis = mi != null ? divisor(mi) : Settings.get().perStack ? 1 : Math.max(1, h.basis());
        lastT = Arrays.copyOfRange(h.times(), start, start + n);
        lastO = scaled(h.orders(), start, n, basis);
        lastA = scaled(h.ah(), start, n, basis);
        lastM = scaled(h.median(), start, n, basis);
        lastWo = Arrays.copyOfRange(h.ordersWeight(), start, start + n);
        lastWa = Arrays.copyOfRange(h.ahWeight(), start, start + n);
        compact();
        mergeLive();
        draw(keepView);
    }

    private static final long MERGE_MS = 1500L;

    private void compact() {
        int n = lastT.length;
        if (n < 2) {
            return;
        }
        int w = 0;
        for (int i = 0; i < n; i++) {
            if (w > 0 && lastT[i] - lastT[w - 1] <= MERGE_MS) {
                lastT[w - 1] = lastT[i];
                lastO[w - 1] = lastO[i];
                lastA[w - 1] = lastA[i];
                lastM[w - 1] = lastM[i];
                lastWo[w - 1] = lastWo[i];
                lastWa[w - 1] = lastWa[i];
            } else {
                if (w != i) {
                    lastT[w] = lastT[i];
                    lastO[w] = lastO[i];
                    lastA[w] = lastA[i];
                    lastM[w] = lastM[i];
                    lastWo[w] = lastWo[i];
                    lastWa[w] = lastWa[i];
                }
                w++;
            }
        }
        if (w == n) {
            return;
        }
        lastT = Arrays.copyOf(lastT, w);
        lastO = Arrays.copyOf(lastO, w);
        lastA = Arrays.copyOf(lastA, w);
        lastM = Arrays.copyOf(lastM, w);
        lastWo = Arrays.copyOf(lastWo, w);
        lastWa = Arrays.copyOf(lastWa, w);
    }

    private static double[] scaled(double[] v, int start, int n, double basis) {
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            out[i] = v[start + i] / basis;
        }
        return out;
    }

    private static boolean line(int i) {
        Settings.Values st = Settings.get();
        return switch (i) {
            case 0 -> st.lineOrders;
            case 1 -> st.lineMedian;
            case 2 -> st.lineLow;
            case 3 -> st.rangeOrders;
            case 4 -> st.rangeMedian;
            case 5 -> st.rangeLow;
            case 6 -> st.bulkNow;
            case 7 -> st.bulkPast;
            default -> st.candles;
        };
    }

    private static void toggleLine(int i) {
        Settings.Values st = Settings.get();
        boolean v = !line(i);
        switch (i) {
            case 0 -> st.lineOrders = v;
            case 1 -> st.lineMedian = v;
            case 2 -> st.lineLow = v;
            case 3 -> st.rangeOrders = v;
            case 4 -> st.rangeMedian = v;
            case 5 -> st.rangeLow = v;
            case 6 -> st.bulkNow = v;
            case 7 -> st.bulkPast = v;
            default -> st.candles = v;
        }
        if (i >= 3 && i <= 5 && v) {
            switch (i) {
                case 3 -> st.lineOrders = true;
                case 4 -> st.lineMedian = true;
                default -> st.lineLow = true;
            }
        }
        Settings.save();
    }

    private void draw(boolean keepView) {
        boolean shop = Swings.SHOP_ONLY.contains(selected);
        Settings.Values st = Settings.get();
        List<double[]> ser = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<Integer> cols = new ArrayList<>();
        List<Boolean> areas = new ArrayList<>();
        if (!shop && st.lineOrders) {
            ser.add(lastO);
            names.add("/orders");
            cols.add(ORDERS_LINE);
            areas.add(true);
        }
        if (!shop && st.lineMedian) {
            ser.add(lastM);
            names.add("AH median");
            cols.add(MEDIAN);
            areas.add(false);
        }
        if (shop || st.lineLow) {
            ser.add(lastA);
            names.add("AH low");
            cols.add(LOW_LINE);
            areas.add(false);
        }
        if (ser.isEmpty()) {
            ser.add(lastO);
            names.add("/orders");
            cols.add(ORDERS_LINE);
            areas.add(true);
        }
        int[] c = new int[cols.size()];
        boolean[] f = new boolean[areas.size()];
        for (int i = 0; i < c.length; i++) {
            c[i] = cols.get(i);
            f[i] = areas.get(i);
        }
        chart.candles(st.candles);
        chart.set(lastT, ser.toArray(new double[0][]), names.toArray(new String[0]), c, f, false, keepView);
        overlays();
        marks();
    }

    private void overlays() {
        boolean shop = Swings.SHOP_ONLY.contains(selected);
        Settings.Values st = Settings.get();
        double[] f = filterFor(selected);
        double k = factor(MarketData.item(selected));
        List<PriceChart.Overlay> list = new ArrayList<>();
        if (!shop && st.lineOrders && st.rangeOrders) {
            range(list, lastO, lastWo, f[0] * k, f[1] * k, "Orders", 0xFFFFF4CC);
        }
        if (!shop && st.lineMedian && st.rangeMedian) {
            range(list, lastM, lastWa, f[0] * k, f[1] * k, "AH median", 0xFFEAD9FF);
        }
        if ((shop || st.lineLow) && st.rangeLow) {
            range(list, lastA, lastWa, f[0] * k, f[1] * k, "AH low", 0xFFCFE7FF);
        }
        chart.overlays(list);
    }

    private void range(List<PriceChart.Overlay> list, double[] values, double[] weights, double min, double max, String name, int color) {
        if (values.length != lastT.length || lastT.length < 2) {
            return;
        }
        Ranges.Result r = Ranges.compute(lastT, PriceChart.limit(values, min, max), Ranges.weights(weights));
        list.add(new PriceChart.Overlay(r.vwap(), color, name + " " + r.label(), false));
        list.add(new PriceChart.Overlay(r.hi(), 0xFF26C59A, name + " high line", true));
        list.add(new PriceChart.Overlay(r.lo(), 0xFFFF4D6A, name + " low line", true));
    }

    private void marks() {
        String key = selected;
        if (key.isEmpty() || Swings.SHOP_ONLY.contains(key)) {
            chart.marks(null, null);
            return;
        }
        Settings.Values st = Settings.get();
        double dv = divisor(MarketData.item(key));
        Runnable again = () -> minecraft.execute(() -> {
            if (key.equals(selected)) {
                marks();
            }
        });
        List<PriceChart.Level> levels = new ArrayList<>();
        List<PriceChart.Segment> spans = new ArrayList<>();
        if (st.bulkNow) {
            for (Bulk.Wall w : Bulk.now(key, again)) {
                levels.add(new PriceChart.Level(w.price() / dv, 0xFFFF9F1C, "Bulk buy " + count(w.amount())));
            }
        }
        if (st.bulkPast) {
            for (Bulk.Past p : Bulk.past(key, again)) {
                spans.add(new PriceChart.Segment(p.from(), p.to(), p.price() / dv, 0xCCFFC46E, Money.format(p.price() / dv) + " · " + count(p.amount())));
            }
        }
        chart.marks(levels, spans);
    }

    private void clampScroll() {
        double max = Math.max(0, rows.size() * ROW - (cb - listTop));
        scroll = Math.max(0, Math.min(scroll, max));
    }

    private int ticks;
    private long liveSeen;

    private void appendLive() {
        if (mergeLive()) {
            draw(true);
        }
    }

    private boolean mergeLive() {
        if (lastT.length == 0 || selected.isEmpty()) {
            return false;
        }
        List<MarketData.Row> rows = MarketData.recentFor(selected, lastT[lastT.length - 1] - MERGE_MS);
        if (rows.isEmpty()) {
            return false;
        }
        double dv = divisor(MarketData.item(selected));
        int n = lastT.length;
        int m = rows.size();
        lastT = Arrays.copyOf(lastT, n + m);
        lastO = Arrays.copyOf(lastO, n + m);
        lastA = Arrays.copyOf(lastA, n + m);
        lastM = Arrays.copyOf(lastM, n + m);
        lastWo = Arrays.copyOf(lastWo, n + m);
        lastWa = Arrays.copyOf(lastWa, n + m);
        for (int i = 0; i < m; i++) {
            MarketData.Row r = rows.get(i);
            lastT[n + i] = r.time();
            lastO[n + i] = r.orders() / dv;
            lastA[n + i] = r.ah() / dv;
            lastM[n + i] = r.median() / dv;
        }
        compact();
        return true;
    }

    @Override
    public void tick() {
        if (MarketData.liveVersion() != liveSeen) {
            liveSeen = MarketData.liveVersion();
            appendLive();
        }
        if (++ticks % 100 == 0 && lastT.length > 0 && (Settings.get().bulkNow || Settings.get().bulkPast)) {
            marks();
        }
        long swingsAt = Swings.loadedAt();
        Swings.ensure();
        if (builtFor != MarketData.marketLoadedAt() || swingsAt != swingsSeen) {
            swingsSeen = swingsAt;
            rebuild();
        }
        if (!selected.isEmpty() && !loading) {
            Loaded hit = HISTORY.get(selected + "|" + RANGE_HOURS[range()]);
            if (hit != null && System.currentTimeMillis() - hit.at() > (RANGE_HOURS[range()] <= 24 ? 15_000L : 300_000L)) {
                load(false);
            }
        }
    }

    private int menuTop() {
        return linesY - 4 - LINE_LABELS.length * 12 - 6;
    }

    private int menuWidth() {
        int w = 0;
        for (String l : LINE_LABELS) {
            w = Math.max(w, font.width(l));
        }
        return w + 26;
    }

    private int menuRow(double mx, double my) {
        int top = menuTop() + 3;
        if (mx < linesX || mx >= linesX + menuWidth() || my < top || my >= top + LINE_LABELS.length * 12) {
            return -1;
        }
        return (int) ((my - top) / 12);
    }

    @Override
    protected void drawOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        drawPick(g, mouseX, mouseY);
        if (!linesOpen) {
            return;
        }
        boolean shop = Swings.SHOP_ONLY.contains(selected);
        int top = menuTop();
        int w = menuWidth();
        Ui.card(g, linesX, top, linesX + w, linesY - 4, 0xF2171819, Ui.SELECT_BORDER);
        int hover = menuRow(mouseX, mouseY);
        int[] colors = {ORDERS_LINE, MEDIAN, LOW_LINE, 0xFFFFF4CC, 0xFFEAD9FF, 0xFFCFE7FF, 0xFFFF9F1C, 0xFFFFC46E, Ui.GREEN};
        for (int i = 0; i < LINE_LABELS.length; i++) {
            int y = top + 3 + i * 12;
            boolean usable = !shop || i == 2 || i == 5 || i == 8;
            if (i == hover && usable) {
                g.fill(linesX + 1, y, linesX + w - 1, y + 12, Ui.HOVER);
            }
            if (i == 3 || i == 6 || i == 8) {
                g.fill(linesX + 4, y - 1, linesX + w - 4, y, Ui.BORDER);
            }
            boolean on = line(i) && usable;
            g.fill(linesX + 5, y + 2, linesX + 13, y + 10, on ? colors[i] : Ui.BORDER);
            if (!on) {
                g.fill(linesX + 6, y + 3, linesX + 12, y + 9, Ui.CHIP);
            }
            Ui.text(g, font, LINE_LABELS[i], linesX + 18, y + 2, usable ? (on ? Ui.TEXT : Ui.SOFT) : 0xFF61656E);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        if (pickOpen != 0) {
            int row = pickRow(mx, my);
            if (row >= 0) {
                togglePick(row);
                return true;
            }
            Button b = pickButton();
            if (b == null || !b.isMouseOver(mx, my)) {
                pickOpen = 0;
                if (b != null && volButton.isMouseOver(mx, my)) {
                    return super.mouseClicked(event, doubleClick);
                }
                return true;
            }
        }
        if (linesOpen) {
            int row = menuRow(mx, my);
            if (row >= 0) {
                if (!Swings.SHOP_ONLY.contains(selected) || row == 2 || row == 5 || row == 8) {
                    toggleLine(row);
                    draw(true);
                }
                return true;
            }
            if (!Ui.inside(mx, my, linesX, linesY, linesX + linesW, linesY + 12)) {
                linesOpen = false;
                return true;
            }
        }
        if (!selected.isEmpty() && Ui.inside(mx, my, starX - 2, starY - 1, starX + 10, starY + 10)) {
            if (!Settings.get().favorites.remove(selected)) {
                Settings.get().favorites.add(selected);
            }
            Settings.save();
            return true;
        }
        if (Ui.inside(mx, my, linesX, linesY, linesX + linesW, linesY + 12) && !filterOpen) {
            linesOpen = !linesOpen;
            return true;
        }
        if (Ui.inside(mx, my, cl, listTop, mid, cb)) {
            int index = (int) ((my - listTop + scroll) / ROW);
            if (index >= 0 && index < rows.size()) {
                choose(rows.get(index).item().key());
                return true;
            }
        }
        int r = rangeAt(RANGE_LABELS, panelX2 - 6, panelY1 + 7, mx, my);
        if (r >= 0) {
            if (r != range()) {
                Settings.get().chartRange = r;
                Settings.save();
                chart.clear();
                load(true);
            }
            return true;
        }
        int fc = filterClick(mx, my, filterLabelX);
        if (fc == 1 || fc == 2) {
            filterOpen = fc == 1;
            rebuildWidgets();
            return true;
        }
        if (fc == 3) {
            clearFilter();
            applyFilter();
            rebuildWidgets();
            return true;
        }
        if (chart.click(mx, my, event.button(), doubleClick)) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (chart.drag(event.x())) {
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        chart.release();
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (chart.scroll(mx, my, vertical)) {
            return true;
        }
        if (Ui.inside(mx, my, cl, listTop, mid, cb)) {
            scroll -= vertical * ROW * 2;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor g, int mx, int my, float delta) {
        Ui.card(g, cl, ct, cl + searchW, ct + 16);
        String status = agoFor(MarketData.item(selected));
        int left = twoRows ? cl + searchW + 6 : statusX + 6;
        if (left + font.width(status) <= cr) {
            Ui.right(g, font, status, cr, ct + 4, MarketData.marketLoadedAt() == 0 ? Ui.GOLD : Ui.MUTED);
        }

        drawList(g, mx, my);
        drawPanel(g, mx, my);
    }

    static String ago(long at) {
        long sec = Math.max(0, (System.currentTimeMillis() - at) / 1000);
        return sec < 60 ? sec + "s ago" : (sec / 60) + "m ago";
    }

    static String count(double n) {
        if (n >= 1_000_000) {
            return String.format(Locale.ROOT, "%.1fM", n / 1_000_000);
        }
        if (n >= 10_000) {
            return String.format(Locale.ROOT, "%.1fK", n / 1_000);
        }
        return String.format(Locale.ROOT, "%,d", Math.round(n));
    }

    private void drawList(GuiGraphicsExtractor g, int mx, int my) {
        Ui.text(g, font, "Item", cl + 24, bodyTop + 1, Ui.MUTED);
        Ui.right(g, font, sortHead(), mid - 6, bodyTop + 1, Ui.MUTED);
        g.fill(cl, bodyTop + 11, mid, bodyTop + 12, Ui.BORDER);

        g.enableScissor(cl, listTop, mid + 1, cb);
        int y = listTop - (int) scroll;
        for (int i = 0; i < rows.size(); i++, y += ROW) {
            if (y + ROW < listTop || y > cb) {
                continue;
            }
            Row r = rows.get(i);
            MarketItem it = r.item();
            boolean on = it.key().equals(selected);
            boolean hover = Ui.inside(mx, my, cl, Math.max(y, listTop), mid, Math.min(y + ROW, cb));
            if (on) {
                Ui.card(g, cl, y, mid, y + ROW - 2, Ui.SELECT, Ui.SELECT_BORDER);
                g.fill(cl, y, cl + 2, y + ROW - 2, Ui.GOLD);
            } else if (hover) {
                g.fill(cl, y, mid, y + ROW - 2, Ui.HOVER);
            } else if (i % 2 == 0) {
                g.fill(cl, y, mid, y + ROW - 2, Ui.STRIPE);
            }
            Ui.item(g, r.icon(), cl + 4, y + 4, 1F);
            Metric m = metric(it);
            String pct = m.text();
            int pctColor = m.color();
            String note = m.note();
            double dv = divisor(it);
            int pctX = mid - 6 - font.width(pct);
            Ui.text(g, font, pct, pctX, y + 3, pctColor);
            boolean fav = Settings.get().favorites.contains(it.key());
            String shownName = Ui.clip(font, r.name(), pctX - 6 - (cl + 24) - (fav ? 9 : 0));
            Ui.text(g, font, shownName, cl + 24, y + 3, Ui.TEXT);
            if (fav) {
                Ui.text(g, font, "★", cl + 24 + font.width(shownName) + 3, y + 3, Ui.GOLD);
            }
            String o = it.orders() > 0 ? Money.format(it.orders() / dv) : "-";
            String a = it.ahLow() > 0 ? Money.format(it.ahLow() / dv) : "-";
            Ui.text(g, font, o, cl + 24, y + 14, it.orders() > 0 ? Ui.GREEN : Ui.MUTED);
            int ax = cl + 24 + font.width(o) + 5;
            Ui.text(g, font, a, ax, y + 14, it.ahLow() > 0 ? Ui.GOLD : Ui.MUTED);
            int sparkW = 28;
            int sparkX = mid - 6 - sparkW;
            if (!note.isEmpty()) {
                Ui.right(g, font, note, mid - 6, y + 14, Ui.MUTED);
            } else if (sparkX > ax + font.width(a) + 6) {
                double[] sp = it.spark();
                double[] range = Chart.range(sp);
                if (range != null && sp.length > 1) {
                    Chart.line(g, sp, range[0], range[1], sparkX, y + 13, sparkW, 10, sp[sp.length - 1] >= firstPositive(sp) ? Ui.GREEN : Ui.RED);
                }
            }
        }
        g.disableScissor();
        if (rows.isEmpty()) {
            String msg = MarketData.marketLoadedAt() == 0 ? "Loading prices..." : "Nothing matches";
            Ui.text(g, font, msg, cl + 6, listTop + 8, Ui.MUTED);
        }
        int track = cb - listTop;
        if (rows.size() * ROW > track) {
            int thumb = Math.max(14, track * track / (rows.size() * ROW));
            double max = rows.size() * ROW - track;
            int ty = listTop + (int) ((track - thumb) * (scroll / max));
            g.fill(mid + 2, ty, mid + 4, ty + thumb, 0x55FFFFFF);
        }
    }

    private static double firstPositive(double[] v) {
        for (double x : v) {
            if (x > 0) {
                return x;
            }
        }
        return 0;
    }

    private void drawPanel(GuiGraphicsExtractor g, int mx, int my) {
        Ui.card(g, panelX1, panelY1, panelX2, panelY2, Ui.GLASS, Ui.BORDER);
        MarketItem it = MarketData.item(selected);
        if (it == null) {
            Ui.text(g, font, MarketData.marketLoadedAt() == 0 ? "Loading..." : "Pick an item", panelX1 + 8, panelY1 + 8, Ui.MUTED);
            return;
        }
        int x = panelX1 + 6;
        Ui.item(g, ItemKeys.stackFor(selected), x, panelY1 + 6, 1F);
        int chipsLeft = ranges(g, RANGE_LABELS, range(), panelX2 - 6, panelY1 + 7);
        boolean fav = Settings.get().favorites.contains(selected);
        String star = fav ? "★" : "☆";
        String title = Ui.clip(font, ItemKeys.name(selected), chipsLeft - 18 - (x + 20));
        Ui.text(g, font, title, x + 20, panelY1 + 6, Ui.TEXT);
        starX = x + 20 + font.width(title) + 5;
        starY = panelY1 + 5;
        boolean overStar = Ui.inside(mx, my, starX - 2, starY - 1, starX + font.width(star) + 2, starY + 10);
        Ui.text(g, font, star, starX, starY + 1, fav ? Ui.GOLD : overStar ? Ui.TEXT : Ui.MUTED);
        Ui.text(g, font, Ui.clip(font, unitText(it), chipsLeft - 4 - (x + 20)), x + 20, panelY1 + 16, Ui.MUTED);

        int cx = panelX1 + 6;
        int cw = panelX2 - 6 - cx;
        int chH = chartBottom - chartTop;
        if (chart.empty()) {
            g.fill(cx, chartTop, cx + cw, chartBottom, Ui.PLOT);
            String msg = loading ? "Loading history..." : "No history for this range yet";
            Ui.text(g, font, msg, cx + (cw - font.width(msg)) / 2, chartTop + chH / 2 - 4, Ui.MUTED);
        } else {
            chart.render(g, font, cx, chartTop, cw, chH, mx, my);
        }
        int fy = chartBottom + 13;
        if (drawFilter(g, mx, my)) {
            drawStats(g, it);
            return;
        }
        int on = 0;
        for (int i = 0; i < LINE_LABELS.length; i++) {
            if (line(i)) {
                on++;
            }
        }
        String chip = "Lines (" + on + ")";
        linesX = cx;
        linesY = fy - 2;
        linesW = font.width(chip) + 8;
        boolean overChip = Ui.inside(mx, my, linesX, linesY, linesX + linesW, linesY + 12);
        g.fill(linesX, linesY, linesX + linesW, linesY + 12, linesOpen ? Ui.CHIP_ON : overChip ? Ui.ACTIVE : Ui.CHIP);
        Ui.text(g, font, chip, linesX + 4, fy, Ui.TEXT);
        String label = filterLabel(selected);
        filterLabelX = panelX2 - 6 - font.width(label);
        boolean overFilter = Ui.inside(mx, my, filterLabelX - 2, fy - 2, panelX2 - 4, fy + 10);
        boolean active = !label.equals("Filter");
        Ui.text(g, font, label, filterLabelX, fy, overFilter ? Ui.TEXT : active ? Ui.GOLD : Ui.BLUE);
        String hint = chart.zoomed() ? "double-click to reset" : "drag · scroll to zoom";
        int hx = linesX + linesW + 8;
        if (hx + font.width(hint) < filterLabelX - 8) {
            Ui.text(g, font, hint, hx, fy, 0xFF61656E);
        }
        drawStats(g, it);
    }

    private void drawStats(GuiGraphicsExtractor g, MarketItem it) {
        int col = (panelX2 - panelX1 - 14) / 3;
        double dv = divisor(it);
        double flip = it.flip() / dv;
        double ch = it.change7d();
        double[] sp = it.spark();
        double lo = Double.MAX_VALUE;
        double hi = 0;
        for (double v : sp) {
            if (v > 0) {
                lo = Math.min(lo, v);
                hi = Math.max(hi, v);
            }
        }
        String[][] stats = {
                {"Best order", it.orders() > 0 ? Money.format(it.orders() / dv) : "-"},
                {"Cheapest AH", it.ahLow() > 0 ? Money.format(it.ahLow() / dv) : "-"},
                {"Flip", flip == 0 ? "-" : Money.signed(flip)},
                {"24h volume", it.volume24() > 0 ? count(it.volume24()) : "-"},
                {"7d change", Double.isNaN(ch) ? "-" : Money.percent(ch)},
                {"24h range", hi > 0 ? Money.format(lo / dv) + " - " + Money.format(hi / dv) : "-"}};
        int[] colors = {Ui.GREEN, Ui.GOLD, flip > 0 ? Ui.BLUE : flip < 0 ? Ui.RED : Ui.MUTED, Ui.TEXT, Ui.trend(ch), Ui.TEXT};
        int shown = statRows * 3;
        for (int i = 0; i < shown; i++) {
            int sx = panelX1 + 7 + (i % 3) * col;
            int sy = statsY + (i / 3) * 22;
            Ui.text(g, font, Ui.clip(font, stats[i][0], col - 4), sx, sy, Ui.MUTED);
            Ui.text(g, font, Ui.clip(font, stats[i][1], col - 4), sx, sy + 10, colors[i]);
        }
    }
}
