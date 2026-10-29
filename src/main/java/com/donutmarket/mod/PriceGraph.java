package com.donutmarket.mod;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

public record PriceGraph(String key) implements TooltipComponent {
    static final int ORDERS = 0xFFF0C54D;
    static final int AH = 0xFF49A5FF;
    private static final int W = 74;
    private static final int GAP = 6;
    private static final int CHART = 22;

    public static TooltipComponent of(ItemStack stack) {
        Settings.Values s = Settings.get();
        if (!s.tooltips || !s.miniCharts || stack.isEmpty()) {
            return null;
        }
        MarketItem item = ItemKeys.lookup(stack);
        if (item == null || (count(item.spark()) < 2 && count(item.sparkAh()) < 2)) {
            return null;
        }
        return new PriceGraph(item.key());
    }

    private static int count(double[] v) {
        int n = 0;
        for (double x : v) {
            if (x > 0) {
                n++;
            }
        }
        return n;
    }

    public static final class View implements ClientTooltipComponent {
        private final String key;

        public View(PriceGraph graph) {
            this.key = graph.key();
        }

        @Override
        public int getHeight(Font font) {
            return CHART + 14;
        }

        @Override
        public int getWidth(Font font) {
            return W * 2 + GAP;
        }

        @Override
        public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor g) {
            MarketItem it = MarketData.item(key);
            if (it == null) {
                return;
            }
            boolean orders = count(it.spark()) >= 2;
            boolean ah = count(it.sparkAh()) >= 2;
            if (orders && ah) {
                draw(g, font, x, y + 2, W, "/orders", it.spark(), ORDERS);
                draw(g, font, x + W + GAP, y + 2, W, "/ah", it.sparkAh(), AH);
            } else if (orders) {
                draw(g, font, x, y + 2, W * 2 + GAP, "/orders", it.spark(), ORDERS);
            } else if (ah) {
                draw(g, font, x, y + 2, W * 2 + GAP, "/ah", it.sparkAh(), AH);
            }
        }

        private static void draw(GuiGraphicsExtractor g, Font font, int x, int y, int w, String label, double[] values, int color) {
            double first = 0;
            double last = 0;
            for (double v : values) {
                if (v > 0) {
                    if (first == 0) {
                        first = v;
                    }
                    last = v;
                }
            }
            double change = first > 0 ? (last - first) / first * 100 : 0;
            String pct = (change >= 0 ? "+" : "") + String.format(Locale.ROOT, "%.1f%%", change);
            g.text(font, label, x, y, color, false);
            g.text(font, pct, x + w - font.width(pct), y, change >= 0 ? Ui.GREEN : Ui.RED, false);
            int top = y + 11;
            g.fill(x, top, x + w, top + CHART, 0x50000000);
            g.fill(x, top + CHART - 1, x + w, top + CHART, 0x30FFFFFF);
            double[] r = Chart.range(values);
            if (r != null) {
                Chart.line(g, values, r[0], r[1], x + 1, top + 1, w - 2, CHART - 2, color);
            }
        }
    }
}
