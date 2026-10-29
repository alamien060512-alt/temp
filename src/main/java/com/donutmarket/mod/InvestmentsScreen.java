package com.donutmarket.mod;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public class InvestmentsScreen extends Dashboard {
    private static final int ROW = 26;
    private static final long DAY = 86_400_000L;

    private enum Mode { VIEW, FORM }

    private record Merged(long at, long[] times, double[] prices, double[] filled) {
    }

    private static String selectedId = "";

    private final PriceChart chart = new PriceChart();
    private Mode mode = Mode.VIEW;
    private Position editing;
    private String pendingPick = "";
    private String formPrice = "";
    private String formAmount = "";
    private boolean perItem = true;

    private ItemPicker picker;
    private EditBox price;
    private EditBox amount;
    private EditBox username;
    private EditBox key;
    private Button unitButton;
    private Button removeButton;
    private String removeArmed = "";

    private String status = "";
    private boolean statusGood;
    private boolean busy;

    private double scroll;
    private boolean cards;
    private int bodyTop;
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
    private boolean past;
    private int pastIndex = -1;
    private double[] shownProfit = new double[0];
    private double[] shownPrice = new double[0];

    private int pointsRequest;
    private String pointsFor = "";
    private boolean pointsLoading;
    private Merged merged;

    private String viewing;
    private List<Position> other = List.of();
    private String otherName = "";
    private String otherError = "";
    private long otherAt;
    private long otherAsked;
    private boolean otherBusy;

    public InvestmentsScreen() {
        super("Investments");
    }

    public static InvestmentsScreen viewing(String username) {
        InvestmentsScreen screen = new InvestmentsScreen();
        screen.viewing = username;
        screen.otherName = username;
        return screen;
    }

    public static InvestmentsScreen adding(String item) {
        InvestmentsScreen screen = new InvestmentsScreen();
        screen.mode = Mode.FORM;
        screen.pendingPick = item;
        return screen;
    }

    @Override
    protected Page page() {
        return viewing != null ? Page.TRADERS : Page.INVESTMENTS;
    }

    private boolean hasName() {
        return viewing != null || Settings.get().portfolio.trim().length() >= 2;
    }

    private boolean canEdit() {
        return viewing == null && hasName() && Settings.get().portfolioKey.length() >= 4;
    }

    @Override
    protected void build() {
        if (viewing != null) {
            addRenderableWidget(Button.builder(Component.literal("< Traders"), b -> minecraft.gui.setScreen(new TradersScreen()))
                    .bounds(cr - 78, ct - 1, 78, 18).build());
        } else {
            addRenderableWidget(Button.builder(Component.literal("+ Add item"), b -> openForm(null, ""))
                    .bounds(cr - 78, ct - 1, 78, 18).build());
        }

        cards = cb - ct >= 290 && cr - cl >= 330;
        bodyTop = cards ? ct + 62 : ct + 24;
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
        chart.format(Money::format);
        removeButton = null;
        unitButton = null;
        picker = null;
        price = null;
        amount = null;
        username = null;
        key = null;

        int fx = panelX1 + 8;
        int fw = panelX2 - 8 - fx;
        if (!hasName() || (mode == Mode.FORM && !canEdit())) {
            int y = panelY1 + 40;
            username = field(fx, y, Math.min(160, fw), "Website username", 24);
            username.setValue(Settings.get().portfolio);
            username.setResponder(t -> {
                Settings.get().portfolio = t.trim();
                Settings.save();
            });
            key = field(fx, y + 34, Math.min(160, fw), "Portfolio key", 200);
            mask(key);
            key.setValue(Settings.get().portfolioKey);
            key.setResponder(t -> {
                Settings.get().portfolioKey = t;
                Settings.save();
            });
            button(mode == Mode.FORM ? "Continue" : "Load", fx, y + 62, 70, b -> {
                MarketData.refreshPortfolio();
                if (hasName()) {
                    rebuildWidgets();
                }
            });
            if (mode == Mode.FORM) {
                button("Cancel", fx + 76, y + 62, 70, b -> closeForm());
            }
            setInitialFocus(username);
            return;
        }

        if (mode == Mode.FORM) {
            int y = panelY1 + 34;
            if (editing == null) {
                picker = new ItemPicker(font, fx, y, fw, k -> syncUnit());
                addRenderableWidget(picker.box());
                if (!pendingPick.isEmpty()) {
                    picker.pick(pendingPick);
                    pendingPick = "";
                }
                y += 32;
            }
            int half = (fw - 6) / 2;
            price = field(fx, y + 12, half, "Price paid", 16);
            price.setValue(formPrice);
            price.setResponder(t -> formPrice = t);
            amount = field(fx + half + 6, y + 12, fw - half - 6, "Amount", 16);
            amount.setValue(formAmount);
            amount.setResponder(t -> formAmount = t);
            unitButton = button(unitLabel(), fx, y + 38, Math.min(110, fw), b -> {
                perItem = !perItem;
                b.setMessage(Component.literal(unitLabel()));
            });
            button(editing == null ? "Add investment" : "Save changes", fx, panelY2 - 24, Math.min(110, half + 20), b -> save());
            button("Cancel", fx + Math.min(110, half + 20) + 4, panelY2 - 24, 70, b -> closeForm());
            syncUnit();
            setInitialFocus(editing == null ? picker.box() : price);
            return;
        }

        Position cur = current();
        if (past) {
            int bw = (panelX2 - panelX1 - 14 - 9) / 4;
            int bx = panelX1 + 7;
            button("Best", bx, buttonsY, bw, b -> jump(1));
            button("Worst", bx + bw + 3, buttonsY, bw, b -> jump(-1));
            button("Now", bx + (bw + 3) * 2, buttonsY, bw, b -> jump(0));
            button("Done", bx + (bw + 3) * 3, buttonsY, bw, b -> {
                past = false;
                pastIndex = -1;
                chart.mark(-1);
                rebuildWidgets();
            });
        } else if (viewing != null) {
            button("Past 24h", panelX1 + 7, buttonsY, 80, b -> {
                past = true;
                pastIndex = -1;
                rebuildWidgets();
            });
        } else {
            int bw = Math.min(70, (panelX2 - panelX1 - 20) / 3);
            button("Edit", panelX1 + 7, buttonsY, bw, b -> {
                Position p = current();
                if (p != null) {
                    openForm(p, "");
                }
            }).active = canEdit();
            removeButton = button("Remove", panelX1 + 10 + bw, buttonsY, bw + 20, b -> remove());
            removeButton.active = canEdit();
            button("Past 24h", panelX1 + 13 + bw * 2 + 20, buttonsY, bw + 10, b -> {
                past = true;
                pastIndex = -1;
                rebuildWidgets();
            });
        }
        buildFilter(cur == null ? "" : cur.item(), panelX1 + 6, chartBottom + 13, panelX2 - 6, () -> {
            Position p = current();
            if (p != null) {
                redraw(p, true);
            }
        });
    }

    private void jump(int which) {
        if (shownProfit.length == 0) {
            return;
        }
        int pick = -1;
        for (int i = 0; i < shownProfit.length; i++) {
            if (Double.isNaN(shownProfit[i])) {
                continue;
            }
            if (pick < 0 || (which > 0 && shownProfit[i] > shownProfit[pick]) || (which < 0 && shownProfit[i] < shownProfit[pick]) || which == 0) {
                pick = i;
            }
        }
        pastIndex = which == 0 ? -1 : pick;
        markPast();
    }

    private int pastPick() {
        if (shownProfit.length == 0) {
            return -1;
        }
        if (pastIndex >= 0 && pastIndex < shownProfit.length && !Double.isNaN(shownProfit[pastIndex])) {
            return pastIndex;
        }
        for (int i = shownProfit.length - 1; i >= 0; i--) {
            if (!Double.isNaN(shownProfit[i])) {
                return i;
            }
        }
        return -1;
    }

    private void markPast() {
        int i = past ? pastPick() : -1;
        chart.mark(i >= 0 && merged != null ? merged.times()[i] : -1);
    }

    private String unitLabel() {
        String k = editing != null ? editing.item() : picker == null ? "" : picker.picked();
        MarketItem item = MarketData.item(k);
        if (item == null || item.basis() <= 1) {
            return "Price per item";
        }
        return perItem ? "Price per item" : "Price per x" + item.basis();
    }

    private void syncUnit() {
        if (unitButton == null) {
            return;
        }
        String k = editing != null ? editing.item() : picker == null ? "" : picker.picked();
        MarketItem item = MarketData.item(k);
        boolean stacked = item != null && item.basis() > 1;
        unitButton.visible = stacked;
        if (!stacked) {
            perItem = true;
        }
        unitButton.setMessage(Component.literal(unitLabel()));
    }

    private void openForm(Position p, String item) {
        mode = Mode.FORM;
        editing = p;
        pendingPick = item;
        status = "";
        if (p != null) {
            MarketItem mi = MarketData.item(p.item());
            int basis = mi == null ? 1 : mi.basis();
            boolean x1 = basis > 1;
            perItem = true;
            formPrice = plain(x1 ? p.entry() / basis : p.entry());
            formAmount = plain(x1 ? p.qty() * basis : p.qty());
        } else {
            perItem = true;
            formPrice = "";
            formAmount = "";
        }
        rebuildWidgets();
    }

    private void closeForm() {
        mode = Mode.VIEW;
        editing = null;
        rebuildWidgets();
    }

    private static String plain(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9) {
            return String.valueOf((long) Math.rint(v));
        }
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private void save() {
        if (busy) {
            return;
        }
        if (!canEdit()) {
            show("Add your portfolio key first", false);
            return;
        }
        String k = editing != null ? editing.item() : picker.picked();
        if (k.isEmpty()) {
            show("Pick an item from the list", false);
            return;
        }
        double paid = parseAmount(price.getValue());
        double qty = parseAmount(amount.getValue());
        if (paid <= 0 || qty <= 0) {
            show("Enter a price and an amount", false);
            return;
        }
        MarketItem item = MarketData.item(k);
        int basis = item == null ? 1 : item.basis();
        boolean x1 = basis > 1 && perItem;
        double entry = x1 ? paid * basis : paid;
        double units = x1 ? qty / basis : qty;
        JsonObject change = new JsonObject();
        if (editing == null) {
            JsonObject inv = new JsonObject();
            inv.addProperty("item", k);
            inv.addProperty("entry", entry);
            inv.addProperty("qty", units);
            inv.addProperty("mode", x1 ? "x1" : "market");
            change.addProperty("op", "add");
            change.add("investment", inv);
        } else {
            change.addProperty("op", "edit");
            change.addProperty("id", editing.id());
            change.addProperty("entry", entry);
            change.addProperty("qty", units);
            pointsFor = "";
        }
        String done = (editing == null ? "Added " : "Saved ") + ItemKeys.name(k);
        send(change, done, true);
    }

    private void remove() {
        Position p = current();
        if (p == null || busy) {
            return;
        }
        if (!removeArmed.equals(p.id())) {
            removeArmed = p.id();
            removeButton.setMessage(Component.literal("Click to confirm").withColor(Ui.RED));
            return;
        }
        removeArmed = "";
        JsonObject change = new JsonObject();
        change.addProperty("op", "remove");
        change.addProperty("id", p.id());
        selectedId = "";
        send(change, "Removed " + ItemKeys.name(p.item()), false);
    }

    private void send(JsonObject change, String done, boolean closeAfter) {
        busy = true;
        show("Saving...", true);
        MarketData.savePortfolio(change).thenAccept(error -> minecraft.execute(() -> {
            busy = false;
            if (error.isEmpty()) {
                show(done, true);
                if (closeAfter) {
                    closeForm();
                } else {
                    rebuildWidgets();
                }
            } else {
                show(error, false);
            }
        }));
    }

    private void show(String text, boolean good) {
        status = text;
        statusGood = good;
    }

    private List<Position> positions() {
        return viewing != null ? other : MarketData.positions();
    }

    private void pollOther() {
        long now = System.currentTimeMillis();
        if (otherBusy || now - otherAsked < 8_000L) {
            return;
        }
        otherBusy = true;
        otherAsked = now;
        String name = viewing;
        MarketData.portfolioOf(name, true).thenAccept(o -> minecraft.execute(() -> {
            otherBusy = false;
            if (!name.equals(viewing)) {
                return;
            }
            otherError = o.error();
            if (o.error().isEmpty()) {
                other = o.positions();
                otherName = o.name();
                otherAt = System.currentTimeMillis();
            }
        }));
    }

    private Position current() {
        List<Position> list = positions();
        for (Position p : list) {
            if (p.id().equals(selectedId)) {
                return p;
            }
        }
        if (!list.isEmpty()) {
            selectedId = list.get(0).id();
            return list.get(0);
        }
        return null;
    }

    private static int range() {
        return Math.max(0, Math.min(MarketScreen.RANGE_HOURS.length - 1, Settings.get().investmentRange));
    }

    @Override
    public void tick() {
        if (viewing != null) {
            pollOther();
        } else {
            MarketData.watchPortfolio();
        }
        if (mode != Mode.VIEW) {
            return;
        }
        Position p = current();
        String want = p == null ? "" : p.id() + "|" + p.entry() + "|" + p.qty() + "|" + (past ? "past" : String.valueOf(range()));
        if (!want.equals(pointsFor)) {
            pointsFor = want;
            chart.clear();
            merged = null;
            if (p != null) {
                loadPoints(p, false);
            }
        } else if (p != null && merged != null && !pointsLoading && System.currentTimeMillis() - merged.at() > 15_000L) {
            loadPoints(p, true);
        }
    }

    private void loadPoints(Position p, boolean keepView) {
        int id = ++pointsRequest;
        pointsLoading = true;
        long now = System.currentTimeMillis();
        long since = now - (past ? 24 : MarketScreen.RANGE_HOURS[range()]) * 3_600_000L;
        if (!past && p.createdAt() > since) {
            since = p.createdAt();
        }
        long first = Math.floorDiv(since, DAY) * DAY;
        List<Long> days = new ArrayList<>();
        for (long d = first; d <= now; d += DAY) {
            days.add(d);
        }
        MarketData.Points[] parts = new MarketData.Points[days.size()];
        AtomicInteger next = new AtomicInteger(days.size() - 1);
        AtomicInteger left = new AtomicInteger(days.size());
        long from = since;
        Runnable[] worker = new Runnable[1];
        worker[0] = () -> {
            int i = next.getAndDecrement();
            if (i < 0) {
                return;
            }
            MarketData.points(p.item(), p.qty(), days.get(i)).whenComplete((pts, err) -> minecraft.execute(() -> {
                if (id != pointsRequest) {
                    return;
                }
                parts[i] = pts;
                boolean doneAll = left.decrementAndGet() == 0;
                publish(p, parts, from, keepView || merged != null, doneAll);
                if (doneAll) {
                    pointsLoading = false;
                }
                worker[0].run();
            }));
        };
        for (int k = 0; k < Math.min(2, days.size()); k++) {
            worker[0].run();
        }
        if (days.isEmpty()) {
            pointsLoading = false;
        }
    }

    private void publish(Position p, MarketData.Points[] parts, long since, boolean keepView, boolean done) {
        int total = 0;
        for (MarketData.Points pt : parts) {
            if (pt != null) {
                total += pt.times().length;
            }
        }
        long[] t = new long[total + 1];
        double[] price = new double[total + 1];
        double[] fill = new double[total + 1];
        int n = 0;
        for (MarketData.Points pt : parts) {
            if (pt == null) {
                continue;
            }
            for (int i = 0; i < pt.times().length; i++) {
                if (pt.times()[i] < since || !(pt.prices()[i] > 0) || !(pt.filled()[i] > 0)) {
                    continue;
                }
                t[n] = pt.times()[i];
                price[n] = pt.prices()[i];
                fill[n] = pt.filled()[i];
                n++;
            }
        }
        if (done && p.priced() && p.price() > 0 && p.filled() > 0 && (n == 0 || t[n - 1] < System.currentTimeMillis() - 1_000L)) {
            t[n] = System.currentTimeMillis();
            price[n] = p.price();
            fill[n] = p.filled();
            n++;
        }
        merged = new Merged(System.currentTimeMillis(), java.util.Arrays.copyOf(t, n), java.util.Arrays.copyOf(price, n), java.util.Arrays.copyOf(fill, n));
        redraw(p, keepView);
    }

    private void redraw(Position p, boolean keepView) {
        if (merged == null) {
            return;
        }
        MarketItem item = MarketData.item(p.item());
        int basis = item == null ? 1 : Math.max(1, item.basis());
        double[] f = filterFor(p.item());
        double[] prices = merged.prices();
        double[] profit = new double[prices.length];
        for (int i = 0; i < prices.length; i++) {
            double each = prices[i] / basis;
            boolean out = (f[0] > 0 && each < f[0]) || (f[1] > 0 && each > f[1]);
            profit[i] = out ? Double.NaN : (prices[i] - p.entry()) * merged.filled()[i];
        }
        shownProfit = profit;
        shownPrice = prices;
        chart.set(merged.times(), new double[][]{profit}, new String[]{"Profit"}, new int[]{Ui.GREEN}, new boolean[]{true}, true, keepView);
        markPast();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        if (picker != null && (picker.click(mx, my) || picker.covers(mx, my))) {
            return true;
        }
        if (Ui.inside(mx, my, cl, listTop, mid, cb)) {
            int index = (int) ((my - listTop + scroll) / ROW);
            List<Position> list = positions();
            if (index >= 0 && index < list.size()) {
                selectedId = list.get(index).id();
                removeArmed = "";
                pastIndex = -1;
                filterOpen = false;
                if (mode != Mode.VIEW) {
                    mode = Mode.VIEW;
                    editing = null;
                }
                rebuildWidgets();
                return true;
            }
        }
        if (mode == Mode.VIEW && hasName()) {
            int r = past ? -1 : rangeAt(MarketScreen.RANGE_LABELS, panelX2 - 6, panelY1 + 7, mx, my);
            if (r >= 0) {
                if (r != range()) {
                    Settings.get().investmentRange = r;
                    Settings.save();
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
                Position p = current();
                if (p != null) {
                    redraw(p, true);
                }
                rebuildWidgets();
                return true;
            }
            if (chart.click(mx, my, event.button(), doubleClick)) {
                return true;
            }
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
        if (chart.release() && past) {
            int i = chart.nearest(event.x());
            if (i >= 0) {
                pastIndex = i;
                markPast();
            }
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (mode == Mode.VIEW && chart.scroll(mx, my, vertical)) {
            return true;
        }
        if (Ui.inside(mx, my, cl, listTop, mid, cb)) {
            double max = Math.max(0, positions().size() * ROW - (cb - listTop));
            scroll = Math.max(0, Math.min(max, scroll - vertical * ROW * 2));
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    private static double unitScale(Position p) {
        MarketItem item = MarketData.item(p.item());
        int basis = item == null ? 1 : item.basis();
        return Math.max(1, basis);
    }

    private static double shownQty(Position p) {
        return p.qty() * unitScale(p);
    }

    private static String qtyText(Position p) {
        MarketItem item = MarketData.item(p.item());
        int basis = item == null ? 1 : item.basis();
        double q = shownQty(p);
        String n = Math.abs(q - Math.rint(q)) < 1e-9 ? String.format(Locale.ROOT, "%,d", Math.round(q)) : String.format(Locale.ROOT, "%,.2f", q);
        return "x" + n + " · per item";
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor g, int mx, int my, float delta) {
        if (viewing != null) {
            String title = otherName + "'s portfolio";
            Ui.text(g, font, Ui.clip(font, title, cr - 130 - cl), cl, ct + 4, Ui.GOLD);
            int lx = cl + Math.min(font.width(title), cr - 130 - cl) + 8;
            boolean live = otherAt > 0 && System.currentTimeMillis() - otherAt < 30_000L;
            g.fill(lx, ct + 6, lx + 4, ct + 10, live ? Ui.GREEN : Ui.MUTED);
            Ui.text(g, font, live ? "live" : otherAt == 0 ? "loading" : "reconnecting", lx + 7, ct + 4, Ui.MUTED);
        } else {
            String name = MarketData.portfolioName().isEmpty() ? Settings.get().portfolio : MarketData.portfolioName();
            Ui.text(g, font, "Your investments", cl, ct + 4, Ui.TEXT);
            if (!name.isBlank()) {
                Ui.text(g, font, Ui.clip(font, name, cr - 90 - (cl + font.width("Your investments  "))), cl + font.width("Your investments  "), ct + 4, Ui.MUTED);
            }
        }
        List<Position> list = positions();
        if (cards) {
            drawCards(g, ct + 24, list);
        }
        drawList(g, mx, my, list);
        Ui.card(g, panelX1, panelY1, panelX2, panelY2, Ui.GLASS, Ui.BORDER);
        if (!hasName() || (mode == Mode.FORM && !canEdit())) {
            drawSetup(g);
        } else if (mode == Mode.FORM) {
            drawForm(g);
        } else {
            drawView(g, mx, my);
        }
    }

    private void drawCards(GuiGraphicsExtractor g, int y, List<Position> list) {
        double value = 0;
        double profit = 0;
        double cost = 0;
        Position best = null;
        double bestChange = -Double.MAX_VALUE;
        for (Position p : list) {
            if (!p.priced()) {
                continue;
            }
            value += p.price() * p.filled();
            profit += p.profit();
            cost += p.entry() * p.filled();
            MarketItem item = MarketData.item(p.item());
            if (item != null && item.orders() > 0) {
                double start = 0;
                for (double v : item.spark()) {
                    if (v > 0) {
                        start = v;
                        break;
                    }
                }
                if (start > 0) {
                    double c = (item.orders() - start) / start * 100;
                    if (c > bestChange) {
                        bestChange = c;
                        best = p;
                    }
                }
            }
        }
        int w = (cr - cl - 8) / 3;
        boolean any = !list.isEmpty();
        statCard(g, cl, y, cl + w, y + 30, "Total value", null, any ? Money.format(value) : "-", Ui.TEXT);
        String pl = any ? Money.signed(profit) + (cost > 0 ? "  " + Money.percent(profit / cost * 100) : "") : "-";
        statCard(g, cl + w + 4, y, cl + w * 2 + 4, y + 30, "Total profit", null, pl, profit >= 0 ? Ui.GREEN : Ui.RED);
        statCard(g, cl + w * 2 + 8, y, cr, y + 30, "Best 24h", best == null ? "-" : ItemKeys.name(best.item()), best == null ? "" : Money.percent(bestChange), Ui.trend(bestChange));
    }

    private void drawList(GuiGraphicsExtractor g, int mx, int my, List<Position> list) {
        Ui.text(g, font, "Item", cl + 24, bodyTop + 1, Ui.MUTED);
        Ui.right(g, font, "Profit", mid - 6, bodyTop + 1, Ui.MUTED);
        g.fill(cl, bodyTop + 11, mid, bodyTop + 12, Ui.BORDER);
        Position sel = mode == Mode.VIEW ? current() : editing;
        g.enableScissor(cl, listTop, mid + 1, cb);
        int y = listTop - (int) scroll;
        for (int i = 0; i < list.size(); i++, y += ROW) {
            if (y + ROW < listTop || y > cb) {
                continue;
            }
            Position p = list.get(i);
            boolean on = sel != null && sel.id().equals(p.id());
            boolean hover = Ui.inside(mx, my, cl, Math.max(y, listTop), mid, Math.min(y + ROW, cb));
            if (on) {
                Ui.card(g, cl, y, mid, y + ROW - 2, Ui.SELECT, Ui.SELECT_BORDER);
                g.fill(cl, y, cl + 2, y + ROW - 2, Ui.GREEN);
            } else if (hover) {
                g.fill(cl, y, mid, y + ROW - 2, Ui.HOVER);
            } else if (i % 2 == 0) {
                g.fill(cl, y, mid, y + ROW - 2, Ui.STRIPE);
            }
            Ui.item(g, ItemKeys.stackFor(p.item()), cl + 4, y + 4, 1F);
            String pl = p.priced() ? Money.signed(p.profit()) : "no orders";
            int color = !p.priced() ? Ui.MUTED : p.profit() > 0 ? Ui.GREEN : p.profit() < 0 ? Ui.RED : Ui.MUTED;
            int plX = mid - 6 - font.width(pl);
            Ui.text(g, font, pl, plX, y + 3, color);
            if (p.priced() && p.entry() > 0) {
                Ui.right(g, font, Money.percent((p.price() - p.entry()) / p.entry() * 100), mid - 6, y + 14, color);
            }
            Ui.text(g, font, Ui.clip(font, ItemKeys.name(p.item()), plX - 6 - (cl + 24)), cl + 24, y + 3, Ui.TEXT);
            Ui.text(g, font, Ui.clip(font, qtyText(p), mid - 50 - (cl + 24)), cl + 24, y + 14, Ui.MUTED);
        }
        g.disableScissor();
        if (list.isEmpty()) {
            String msg;
            if (viewing != null) {
                msg = !otherError.isEmpty() ? otherError : otherAt == 0 ? "Loading..." : "No investments";
            } else if (!hasName()) {
                msg = "Link your website portfolio";
            } else if (!MarketData.portfolioError().isEmpty()) {
                msg = MarketData.portfolioError();
            } else if (MarketData.portfolioLoadedAt() == 0) {
                msg = "Loading...";
            } else {
                msg = "No investments yet";
            }
            Ui.text(g, font, Ui.clip(font, msg, mid - cl - 8), cl + 6, listTop + 8, MarketData.portfolioError().isEmpty() ? Ui.MUTED : Ui.RED);
        }
    }

    private void drawSetup(GuiGraphicsExtractor g) {
        int x = panelX1 + 8;
        Ui.text(g, font, mode == Mode.FORM ? "Add your key to make changes" : "Link your portfolio", x, panelY1 + 8, Ui.GOLD);
        Ui.text(g, font, Ui.clip(font, "Use the same name and key as donutmarket.us", panelX2 - x - 8), x, panelY1 + 20, Ui.MUTED);
        Ui.text(g, font, "Username", x, panelY1 + 30, Ui.SOFT);
        Ui.text(g, font, "Key (only needed to make changes)", x, panelY1 + 64, Ui.SOFT);
        drawStatus(g, x, panelY1 + 130);
    }

    private void drawForm(GuiGraphicsExtractor g) {
        int x = panelX1 + 8;
        Ui.text(g, font, editing == null ? "Add investment" : "Edit " + ItemKeys.name(editing.item()), x, panelY1 + 8, Ui.GOLD);
        int y = panelY1 + 34;
        if (editing == null) {
            Ui.text(g, font, "Item", x, panelY1 + 24, Ui.SOFT);
            y += 32;
        }
        Ui.text(g, font, "Price paid", x, y + 2, Ui.SOFT);
        int half = (panelX2 - 8 - x - 6) / 2;
        Ui.text(g, font, "How many", x + half + 6, y + 2, Ui.SOFT);
        Ui.text(g, font, Ui.clip(font, "Use 1.5m, 250k or 2b. Same as the website.", panelX2 - x - 8), x, y + 62, Ui.MUTED);
        drawStatus(g, x, y + 76);
    }

    private void drawStatus(GuiGraphicsExtractor g, int x, int y) {
        if (!status.isEmpty()) {
            Ui.text(g, font, Ui.clip(font, status, panelX2 - x - 8), x, y, statusGood ? Ui.GREEN : Ui.RED);
        }
    }

    private void drawView(GuiGraphicsExtractor g, int mx, int my) {
        Position p = current();
        int x = panelX1 + 6;
        if (p == null) {
            String empty = viewing != null ? (otherAt == 0 ? "Loading..." : "Nothing to show") : MarketData.portfolioLoadedAt() == 0 ? "Loading your investments..." : "Click + Add item to start";
            Ui.text(g, font, empty, x + 2, panelY1 + 8, Ui.MUTED);
            drawStatus(g, x + 2, panelY1 + 22);
            return;
        }
        MarketItem item = MarketData.item(p.item());
        Ui.item(g, ItemKeys.stackFor(p.item()), x, panelY1 + 6, 1F);
        int chipsLeft;
        if (past) {
            String tag = "Past 24h";
            chipsLeft = panelX2 - 6 - font.width(tag) - 8;
            g.fill(chipsLeft, panelY1 + 7, panelX2 - 6, panelY1 + 19, Ui.CHIP_ON);
            Ui.text(g, font, tag, chipsLeft + 4, panelY1 + 9, Ui.TEXT);
            chipsLeft -= 3;
        } else {
            chipsLeft = ranges(g, MarketScreen.RANGE_LABELS, range(), panelX2 - 6, panelY1 + 7);
        }
        double q = shownQty(p);
        String qty = Math.abs(q - Math.rint(q)) < 1e-9 ? String.valueOf(Math.round(q)) : String.format(Locale.ROOT, "%.2f", q);
        Ui.text(g, font, Ui.clip(font, ItemKeys.name(p.item()) + " x" + qty, chipsLeft - 4 - (x + 20)), x + 20, panelY1 + 6, Ui.TEXT);
        Ui.text(g, font, Ui.clip(font, past ? "if you sold to /orders at..." : bought(p.createdAt()), chipsLeft - 4 - (x + 20)), x + 20, panelY1 + 16, Ui.MUTED);

        int cw = panelX2 - 6 - x;
        int chH = chartBottom - chartTop;
        if (chart.empty()) {
            g.fill(x, chartTop, x + cw, chartBottom, Ui.PLOT);
            String msg = pointsLoading || merged == null ? "Loading history..." : "No history for this range yet";
            Ui.text(g, font, msg, x + (cw - font.width(msg)) / 2, chartTop + chH / 2 - 4, Ui.MUTED);
        } else {
            chart.render(g, font, x, chartTop, cw, chH, mx, my);
        }
        int fy = chartBottom + 13;
        if (!drawFilter(g, mx, my)) {
            String hint = pointsLoading && merged != null ? "loading more..." : past ? "click the chart to pick a time" : "profit over time";
            String label = filterLabel(p.item());
            filterLabelX = panelX2 - 6 - font.width(label);
            boolean over = Ui.inside(mx, my, filterLabelX - 2, fy - 2, panelX2 - 4, fy + 10);
            Ui.text(g, font, Ui.clip(font, hint, filterLabelX - 8 - x), x, fy, Ui.MUTED);
            Ui.text(g, font, label, filterLabelX, fy, over ? Ui.TEXT : label.equals("Filter") ? Ui.BLUE : Ui.GOLD);
        }
        if (past) {
            drawPastStats(g, p, item);
            return;
        }

        double scale = unitScale(p);
        int basis = item == null ? 1 : item.basis();
        String unit = scale > 1 || basis <= 1 ? " ea" : " /x" + basis;
        double change = p.priced() && p.entry() > 0 ? (p.price() - p.entry()) / p.entry() * 100 : Double.NaN;
        String[][] stats = {
                {"Bought at", Money.format(p.entry() / scale) + unit},
                {"Now worth", p.priced() ? Money.format(p.price() / scale) + unit : "-"},
                {"Profit", p.priced() ? Money.signed(p.profit()) : "-"},
                {"Value", p.priced() ? Money.format(p.price() * p.filled()) : "-"},
                {"Change", Double.isNaN(change) ? "-" : Money.percent(change)},
                {"Priced by", "/orders"}};
        int[] colors = {Ui.TEXT, Ui.trend(change), Ui.trend(p.profit()), Ui.TEXT, Ui.trend(change), Ui.MUTED};
        int col = (panelX2 - panelX1 - 14) / 3;
        for (int i = 0; i < statRows * 3; i++) {
            int sx = panelX1 + 7 + (i % 3) * col;
            int sy = statsY + (i / 3) * 22;
            Ui.text(g, font, Ui.clip(font, stats[i][0], col - 4), sx, sy, Ui.MUTED);
            Ui.text(g, font, Ui.clip(font, stats[i][1], col - 4), sx, sy + 10, colors[i]);
        }
        if (viewing != null) {
            Ui.right(g, font, "updates live", panelX2 - 7, panelY2 - 19, 0xFF61656E);
        } else if (!canEdit()) {
            Ui.right(g, font, "Key needed to edit", panelX2 - 7, panelY2 - 19, Ui.MUTED);
        } else if (!status.isEmpty()) {
            Ui.right(g, font, Ui.clip(font, status, panelX2 - panelX1 - 200), panelX2 - 7, panelY2 - 19, statusGood ? Ui.GREEN : Ui.RED);
        }
    }

    private void drawPastStats(GuiGraphicsExtractor g, Position p, MarketItem item) {
        double scale = unitScale(p);
        int basis = item == null ? 1 : item.basis();
        String unit = scale > 1 || basis <= 1 ? " ea" : " /x" + basis;
        int i = pastPick();
        int best = -1;
        int worst = -1;
        for (int k = 0; k < shownProfit.length; k++) {
            if (Double.isNaN(shownProfit[k])) {
                continue;
            }
            if (best < 0 || shownProfit[k] > shownProfit[best]) {
                best = k;
            }
            if (worst < 0 || shownProfit[k] < shownProfit[worst]) {
                worst = k;
            }
        }
        java.time.format.DateTimeFormatter clock = java.time.format.DateTimeFormatter.ofPattern("h:mm a").withZone(java.time.ZoneId.systemDefault());
        String[][] stats;
        int[] colors;
        if (i < 0 || merged == null) {
            stats = new String[][]{{"Time", "-"}, {"Sell price", "-"}, {"Profit/loss", "-"}, {"Return", "-"}, {"Best", "-"}, {"Worst", "-"}};
            colors = new int[]{Ui.MUTED, Ui.MUTED, Ui.MUTED, Ui.MUTED, Ui.MUTED, Ui.MUTED};
        } else {
            long t = merged.times()[i];
            double pl = shownProfit[i];
            double ret = p.entry() > 0 ? (shownPrice[i] - p.entry()) / p.entry() * 100 : 0;
            boolean partial = merged.filled()[i] < p.qty() - 1e-9;
            stats = new String[][]{
                    {"Time", clock.format(java.time.Instant.ofEpochMilli(t)) + " · " + ago(t)},
                    {"Sell price", Money.format(shownPrice[i] / scale) + unit},
                    {partial ? "Profit (part)" : "Profit/loss", Money.signed(pl)},
                    {"Return", Money.percent(ret)},
                    {"Best", best < 0 ? "-" : Money.signed(shownProfit[best]) + " " + clock.format(java.time.Instant.ofEpochMilli(merged.times()[best]))},
                    {"Worst", worst < 0 ? "-" : Money.signed(shownProfit[worst]) + " " + clock.format(java.time.Instant.ofEpochMilli(merged.times()[worst]))}};
            colors = new int[]{Ui.TEXT, Ui.TEXT, Ui.trend(pl), Ui.trend(ret), Ui.GREEN, Ui.RED};
        }
        int col = (panelX2 - panelX1 - 14) / 3;
        for (int k = 0; k < statRows * 3; k++) {
            int sx = panelX1 + 7 + (k % 3) * col;
            int sy = statsY + (k / 3) * 22;
            Ui.text(g, font, Ui.clip(font, stats[k][0], col - 4), sx, sy, Ui.MUTED);
            Ui.text(g, font, Ui.clip(font, stats[k][1], col - 4), sx, sy + 10, colors[k]);
        }
    }

    private static String ago(long t) {
        long m = Math.max(0, (System.currentTimeMillis() - t) / 60_000L);
        if (m < 1) {
            return "now";
        }
        if (m < 60) {
            return m + "m ago";
        }
        return (m / 60) + "h" + (m % 60 > 0 ? " " + (m % 60) + "m" : "") + " ago";
    }

    private static String bought(long at) {
        if (at <= 0) {
            return "";
        }
        long hours = Math.max(0, (System.currentTimeMillis() - at) / 3_600_000L);
        if (hours < 1) {
            return "bought just now";
        }
        if (hours < 48) {
            return "bought " + hours + (hours == 1 ? " hour ago" : " hours ago");
        }
        return "bought " + (hours / 24) + " days ago";
    }

    @Override
    protected void drawOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (picker != null) {
            picker.render(g, font, mouseX, mouseY);
        }
    }
}
