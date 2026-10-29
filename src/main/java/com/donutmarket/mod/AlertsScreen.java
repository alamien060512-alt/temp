package com.donutmarket.mod;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class AlertsScreen extends Dashboard {
    private static final int ROW = 24;

    private ItemPicker picker;
    private EditBox price;
    private boolean auction = true;
    private boolean below = true;
    private String status = "";
    private boolean statusGood;
    private int formTop;
    private int listTop;
    private boolean stacked;
    private double scroll;
    private final List<int[]> hits = new ArrayList<>();

    public AlertsScreen(Screen parent) {
        super("Alerts");
    }

    @Override
    protected Page page() {
        return Page.ALERTS;
    }

    @Override
    protected void build() {
        formTop = ct + 18;
        int w = cr - cl;
        stacked = w < 380;
        int pickW = stacked ? w - 12 : Math.max(120, w - 300);
        picker = new ItemPicker(font, cl + 6, formTop + 16, pickW, key -> status = "");
        addRenderableWidget(picker.box());
        int x = stacked ? cl + 6 : cl + 6 + pickW + 4;
        int row = stacked ? formTop + 39 : formTop + 15;
        addRenderableWidget(Button.builder(whereLabel(), b -> {
            auction = !auction;
            b.setMessage(whereLabel());
        }).bounds(x, row, 62, 20).build());
        x += 66;
        addRenderableWidget(Button.builder(dirLabel(), b -> {
            below = !below;
            b.setMessage(dirLabel());
        }).bounds(x, row, 76, 20).build());
        x += 80;
        int priceW = Math.max(40, cr - 6 - 54 - x);
        price = field(x, row + 1, priceW, "4.5m", 16);
        button("Add", cr - 6 - 50, row, 50, b -> add());
        listTop = formTop + (stacked ? 86 : 62);
        setInitialFocus(picker.box());
    }

    public void preset(String key) {
        if (picker != null) {
            picker.pick(key);
        }
    }

    private Component whereLabel() {
        return Component.literal(auction ? "/ah" : "/orders").withColor(auction ? Ui.GOLD : Ui.GREEN);
    }

    private Component dirLabel() {
        return Component.literal(below ? "At or below" : "At or above");
    }

    private void add() {
        String key = picker.picked();
        double value = parseAmount(price.getValue());
        if (key.isEmpty()) {
            show("Pick an item from the list first", false);
            return;
        }
        if (value <= 0) {
            show("Enter a price like 4.5k or 5.4m", false);
            return;
        }
        Settings.get().alerts.add(new Alert(key, auction, below, value));
        Settings.save();
        show("Alert added for " + ItemKeys.name(key), true);
        picker.clear();
        price.setValue("");
    }

    private void show(String text, boolean good) {
        status = text;
        statusGood = good;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (picker.click(event.x(), event.y()) || picker.covers(event.x(), event.y())) {
            return true;
        }
        for (int[] h : hits) {
            if (Ui.inside(event.x(), event.y(), h[0], h[1], h[2], h[3])) {
                List<Alert> alerts = Settings.get().alerts;
                if (h[5] < alerts.size()) {
                    Alert a = alerts.get(h[5]);
                    if (h[4] == 0) {
                        alerts.remove(a);
                    } else {
                        a.hitAt = 0;
                        a.hitPrice = 0;
                    }
                    Settings.save();
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (Ui.inside(mx, my, cl, listTop, cr, cb)) {
            double max = Math.max(0, Settings.get().alerts.size() * ROW - (cb - listTop));
            scroll = Math.max(0, Math.min(max, scroll - vertical * ROW * 2));
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor g, int mx, int my, float delta) {
        hits.clear();
        List<Alert> alerts = Settings.get().alerts;
        long active = alerts.stream().filter(a -> !a.hit()).count();
        Ui.text(g, font, "Price alerts", cl, ct + 4, Ui.TEXT);
        Ui.right(g, font, active + " active", cr, ct + 4, Ui.MUTED);

        Ui.card(g, cl, formTop, cr, formTop + (stacked ? 72 : 48), Ui.GLASS, Ui.BORDER);
        Ui.text(g, font, "New alert: item, where, when, price", cl + 6, formTop + 5, Ui.SOFT);
        if (!status.isEmpty()) {
            Ui.text(g, font, Ui.clip(font, status, cr - cl - 12), cl + 6, formTop + (stacked ? 62 : 38), statusGood ? Ui.GREEN : Ui.RED);
        } else {
            Ui.text(g, font, "You get a popup and a sound when an alert hits.", cl + 6, formTop + (stacked ? 62 : 38), 0xFF61656E);
        }

        Ui.text(g, font, "Your alerts (" + alerts.size() + ")", cl, listTop - 12, Ui.MUTED);
        g.fill(cl, listTop - 2, cr, listTop - 1, Ui.BORDER);
        if (alerts.isEmpty()) {
            Ui.text(g, font, "No alerts yet. Add one above, or use Set alert on any chart.", cl + 4, listTop + 6, Ui.MUTED);
        }
        g.enableScissor(cl, listTop, cr + 1, cb);
        int y = listTop - (int) scroll;
        for (int i = 0; i < alerts.size(); i++, y += ROW) {
            if (y + ROW < listTop || y > cb) {
                continue;
            }
            Alert a = alerts.get(i);
            boolean hover = Ui.inside(mx, my, cl, Math.max(y, listTop), cr, Math.min(y + ROW, cb));
            g.fill(cl, y, cr, y + ROW - 2, hover ? Ui.HOVER : i % 2 == 0 ? Ui.STRIPE : 0);
            Ui.item(g, ItemKeys.stackFor(a.item), cl + 4, y + 3, 1F);
            int color = a.hit() ? Ui.MUTED : Ui.TEXT;
            int x = cl + 24;
            int ty = y + 7;
            Ui.text(g, font, ItemKeys.name(a.item), x, ty, color);
            x += font.width(ItemKeys.name(a.item)) + 8;
            Ui.text(g, font, a.describe(), x, ty, a.hit() ? Ui.MUTED : a.auction ? Ui.GOLD : Ui.GREEN);
            x += font.width(a.describe()) + 8;

            int bx = cr - 6;
            String del = "Delete";
            int dw = font.width(del) + 10;
            bx -= dw;
            boolean overDel = Ui.inside(mx, my, bx, y + 4, bx + dw, y + 18);
            g.fill(bx, y + 4, bx + dw, y + 18, overDel ? 0xFF7A2A36 : Ui.CHIP);
            Ui.text(g, font, del, bx + 5, y + 7, overDel ? Ui.TEXT : Ui.RED);
            if (y + 4 >= listTop && y + 18 <= cb) {
                hits.add(new int[]{bx, y + 4, bx + dw, y + 18, 0, i});
            }
            if (a.hit()) {
                String rearm = "Re-arm";
                int rw = font.width(rearm) + 10;
                bx -= rw + 4;
                boolean overR = Ui.inside(mx, my, bx, y + 4, bx + rw, y + 18);
                g.fill(bx, y + 4, bx + rw, y + 18, overR ? Ui.CHIP_ON : Ui.CHIP);
                Ui.text(g, font, rearm, bx + 5, y + 7, Ui.TEXT);
                if (y + 4 >= listTop && y + 18 <= cb) {
                    hits.add(new int[]{bx, y + 4, bx + rw, y + 18, 1, i});
                }
            }
            double now = Alerts.currentPrice(a);
            String extra = a.hit() ? "hit at " + Money.format(a.hitPrice) : now > 0 ? "now " + Money.format(now) : "";
            Ui.text(g, font, Ui.clip(font, extra, bx - 6 - x), x, ty, a.hit() ? Ui.GREEN : Ui.MUTED);
        }
        g.disableScissor();
    }

    @Override
    protected void drawOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        picker.render(g, font, mouseX, mouseY);
    }
}
