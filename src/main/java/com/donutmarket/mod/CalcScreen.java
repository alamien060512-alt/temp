package com.donutmarket.mod;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CalcScreen extends Dashboard {
    private static final String[] MODES = {"Math", "Flip"};
    private static int mode = 0;
    private static final List<String[]> HISTORY = new ArrayList<>();
    private static String lastExpr = "";

    private EditBox expr;
    private ItemPicker picker;
    private EditBox qty;
    private EditBox buy;
    private EditBox sell;
    private int formTop;
    private final List<int[]> hits = new ArrayList<>();

    public CalcScreen() {
        super("Calculator");
    }

    @Override
    protected Page page() {
        return Page.CALC;
    }

    @Override
    protected void build() {
        if (mode >= MODES.length) {
            mode = 0;
        }
        formTop = ct + 22;
        int w = cr - cl;
        if (mode == 0) {
            expr = field(cl + 6, formTop + 16, w - 12, "3.2m*64 or (250k+1.5m)/2", 80);
            expr.setValue(lastExpr);
            expr.setResponder(v -> lastExpr = v);
            setInitialFocus(expr);
        } else {
            int half = (w - 18) / 2;
            picker = new ItemPicker(font, cl + 6, formTop + 16, half, key -> fillPrices());
            addRenderableWidget(picker.box());
            qty = field(cl + 12 + half, formTop + 16, half, "Amount, e.g. 64", 12);
            buy = field(cl + 6, formTop + 52, half, "Buy price each", 16);
            sell = field(cl + 12 + half, formTop + 52, half, "Sell price each", 16);
            setInitialFocus(picker.box());
        }
    }

    private void fillPrices() {
        MarketItem it = MarketData.item(picker.picked());
        if (it == null) {
            return;
        }
        if (buy.getValue().isEmpty() && it.orders() > 0) {
            buy.setValue(plain(it.ordersEach()));
        }
        if (sell.getValue().isEmpty() && it.ahLow() > 0) {
            sell.setValue(plain(it.ahEach()));
        }
        if (qty.getValue().isEmpty()) {
            qty.setValue(String.valueOf(Math.max(1, it.basis())));
        }
    }

    private static String plain(double v) {
        if (v >= 1_000_000) {
            return trim(v / 1_000_000) + "m";
        }
        if (v >= 10_000) {
            return trim(v / 1_000) + "k";
        }
        return trim(v);
    }

    private static String trim(double v) {
        String s = String.format(Locale.ROOT, "%.3f", v);
        s = s.replaceAll("0+$", "");
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }

    private static String full(double v) {
        if (Math.abs(v) >= 1000 || v == Math.rint(v)) {
            return "$" + String.format(Locale.ROOT, "%,.0f", v);
        }
        return "$" + String.format(Locale.ROOT, "%,.2f", v);
    }

    static double eval(String text) {
        Parser p = new Parser(text.toLowerCase(Locale.ROOT).replace(",", "").replace("$", "").replace(" ", "").replace("×", "*").replace("x", "*"));
        double v = p.sum();
        if (p.pos != p.s.length()) {
            throw new IllegalArgumentException();
        }
        return v;
    }

    private static final class Parser {
        final String s;
        int pos;

        Parser(String s) {
            this.s = s;
        }

        char peek() {
            return pos < s.length() ? s.charAt(pos) : 0;
        }

        double sum() {
            double v = product();
            while (peek() == '+' || peek() == '-') {
                char op = s.charAt(pos++);
                double r = product();
                v = op == '+' ? v + r : v - r;
            }
            return v;
        }

        double product() {
            double v = unary();
            while (peek() == '*' || peek() == '/') {
                char op = s.charAt(pos++);
                double r = unary();
                v = op == '*' ? v * r : v / r;
            }
            return v;
        }

        double unary() {
            if (peek() == '-') {
                pos++;
                return -unary();
            }
            if (peek() == '+') {
                pos++;
                return unary();
            }
            return atom();
        }

        double atom() {
            if (peek() == '(') {
                pos++;
                double v = sum();
                if (peek() != ')') {
                    throw new IllegalArgumentException();
                }
                pos++;
                return v;
            }
            int start = pos;
            while (Character.isDigit(peek()) || peek() == '.') {
                pos++;
            }
            if (start == pos) {
                throw new IllegalArgumentException();
            }
            double v = Double.parseDouble(s.substring(start, pos));
            char c = peek();
            if (c == 'k') {
                pos++;
                v *= 1_000;
            } else if (c == 'm') {
                pos++;
                v *= 1_000_000;
            } else if (c == 'b') {
                pos++;
                v *= 1_000_000_000;
            }
            return v;
        }
    }

    private double num(EditBox box) {
        try {
            return box == null || box.getValue().isBlank() ? 0 : eval(box.getValue());
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        if (picker != null && (picker.click(mx, my) || picker.covers(mx, my))) {
            if (mode == 1) {
                fillPrices();
            }
            return true;
        }
        int m = rangeAt(MODES, cr, ct + 2, mx, my);
        if (m >= 0 && m != mode) {
            mode = m;
            minecraft.gui.setScreen(new CalcScreen());
            return true;
        }
        for (int[] h : hits) {
            if (Ui.inside(mx, my, h[0], h[1], h[2], h[3])) {
                if (h[4] == 0 && h[5] < HISTORY.size()) {
                    expr.setValue(HISTORY.get(h[5])[0]);
                } else if (h[4] == 2 && mode == 0) {
                    remember();
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (mode == 0 && expr != null && expr.isFocused() && (event.key() == 257 || event.key() == 335)) {
            remember();
            return true;
        }
        return super.keyPressed(event);
    }

    private void remember() {
        String e = expr.getValue().trim();
        if (e.isEmpty()) {
            return;
        }
        try {
            double v = eval(e);
            HISTORY.removeIf(h -> h[0].equals(e));
            HISTORY.add(0, new String[]{e, full(v)});
            while (HISTORY.size() > 8) {
                HISTORY.remove(HISTORY.size() - 1);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor g, int mx, int my, float delta) {
        hits.clear();
        Ui.text(g, font, "Calculator", cl, ct + 4, Ui.TEXT);
        ranges(g, MODES, mode, cr, ct + 2);
        if (mode == 0) {
            drawMath(g, mx, my);
        } else {
            drawFlip(g);
        }
    }

    private void drawMath(GuiGraphicsExtractor g, int mx, int my) {
        Ui.card(g, cl, formTop, cr, formTop + 70, Ui.GLASS, Ui.BORDER);
        Ui.text(g, font, "Type a sum. k, m and b work, like 3.2m*64", cl + 6, formTop + 5, Ui.SOFT);
        String value = expr.getValue().trim();
        String out;
        int color = Ui.GOLD;
        if (value.isEmpty()) {
            out = "";
        } else {
            try {
                double v = eval(value);
                out = Double.isFinite(v) ? full(v) + "   (" + Money.format(v) + ")" : "Can't divide by 0";
                if (!Double.isFinite(v)) {
                    color = Ui.RED;
                }
            } catch (Exception e) {
                out = "Keep typing...";
                color = Ui.MUTED;
            }
        }
        g.pose().pushMatrix();
        g.pose().translate(cl + 6, formTop + 42);
        g.pose().scale(1.5F, 1.5F);
        g.text(font, Ui.clip(font, out, (int) ((cr - cl - 70) / 1.5F)), 0, 0, color, false);
        g.pose().popMatrix();
        String save = "Save";
        int sw = font.width(save) + 10;
        int bx = cr - 6 - sw;
        boolean over = Ui.inside(mx, my, bx, formTop + 40, bx + sw, formTop + 54);
        g.fill(bx, formTop + 40, bx + sw, formTop + 54, over ? Ui.CHIP_ON : Ui.CHIP);
        Ui.text(g, font, save, bx + 5, formTop + 43, Ui.TEXT);
        hits.add(new int[]{bx, formTop + 40, bx + sw, formTop + 54, 2, 0});

        int y = formTop + 80;
        Ui.text(g, font, HISTORY.isEmpty() ? "Press Enter to save a result here." : "Saved (click to reuse)", cl, y, Ui.MUTED);
        y += 12;
        for (int i = 0; i < HISTORY.size() && y + 14 < cb; i++, y += 14) {
            String[] h = HISTORY.get(i);
            boolean over2 = Ui.inside(mx, my, cl, y - 2, cr, y + 11);
            g.fill(cl, y - 2, cr, y + 11, over2 ? Ui.HOVER : i % 2 == 0 ? Ui.STRIPE : 0);
            Ui.text(g, font, Ui.clip(font, h[0], (cr - cl) / 2), cl + 4, y, Ui.SOFT);
            Ui.right(g, font, h[1], cr - 4, y, Ui.GOLD);
            hits.add(new int[]{cl, y - 2, cr, y + 11, 0, i});
        }
    }

    private void drawFlip(GuiGraphicsExtractor g) {
        int half = (cr - cl - 18) / 2;
        Ui.text(g, font, "Item", cl + 6, formTop + 5, Ui.SOFT);
        Ui.text(g, font, "Amount", cl + 12 + half, formTop + 5, Ui.SOFT);
        Ui.text(g, font, "Buy price (each)", cl + 6, formTop + 41, Ui.SOFT);
        Ui.text(g, font, "Sell price (each)", cl + 12 + half, formTop + 41, Ui.SOFT);
        Ui.text(g, font, "Picking an item fills in /orders and the cheapest /ah.", cl + 6, formTop + 76, 0xFF61656E);
        double q = num(qty);
        double b = num(buy);
        double s = num(sell);
        int y = formTop + 92;
        if (q <= 0 || b < 0 || s < 0) {
            Ui.text(g, font, "Fill in amount, buy and sell price.", cl + 6, y, Ui.MUTED);
            return;
        }
        double cost = b * q;
        double gross = s * q;
        double profit = gross - cost;
        double pct = cost > 0 ? profit / cost * 100 : 0;
        int third = (cr - cl - 8) / 3;
        statCard(g, cl, y, cl + third, y + 30, "You pay", null, full(cost), Ui.TEXT);
        statCard(g, cl + third + 4, y, cl + third * 2 + 4, y + 30, "You get", null, full(gross), Ui.TEXT);
        statCard(g, cl + third * 2 + 8, y, cr, y + 30, "Profit", null, Money.signed(profit) + "  " + Money.percent(pct), profit >= 0 ? Ui.GREEN : Ui.RED);
        Ui.text(g, font, "Profit each: " + Money.signed(s - b), cl + 6, y + 38, Ui.SOFT);
        MarketItem it = MarketData.item(picker.picked());
        if (it != null && it.basis() > 1) {
            Ui.text(g, font, "Per x" + it.basis() + ": buy " + full(b * it.basis()) + ", sell " + full(s * it.basis()), cl + 6, y + 50, Ui.MUTED);
        }
    }

    @Override
    protected void drawOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (picker != null) {
            picker.render(g, font, mouseX, mouseY);
        }
    }
}
