package com.donutmarket.mod;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class Chart {
    private Chart() {
    }

    public static double[] range(double[]... series) {
        double lo = Double.MAX_VALUE;
        double hi = -Double.MAX_VALUE;
        for (double[] s : series) {
            if (s == null) {
                continue;
            }
            for (double v : s) {
                if (v > 0) {
                    lo = Math.min(lo, v);
                    hi = Math.max(hi, v);
                }
            }
        }
        if (lo == Double.MAX_VALUE) {
            return null;
        }
        if (hi - lo < hi * 0.002) {
            lo = lo * 0.99;
            hi = hi * 1.01;
        }
        double pad = (hi - lo) * 0.08;
        return new double[]{lo - pad, hi + pad};
    }

    public static void line(GuiGraphicsExtractor g, double[] values, double lo, double hi, int x, int y, int w, int h, int color) {
        if (values == null || values.length < 2 || hi <= lo) {
            return;
        }
        int prevX = -1;
        int prevY = 0;
        for (int i = 0; i < values.length; i++) {
            double v = values[i];
            if (v <= 0) {
                prevX = -1;
                continue;
            }
            int px = x + (int) Math.round((double) i / (values.length - 1) * (w - 1));
            int py = y + h - 1 - (int) Math.round((v - lo) / (hi - lo) * (h - 1));
            py = Math.max(y, Math.min(y + h - 1, py));
            if (prevX >= 0) {
                segment(g, prevX, prevY, px, py, color);
            }
            prevX = px;
            prevY = py;
        }
    }

    private static void segment(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        if (x2 == x1) {
            g.fill(x1, Math.min(y1, y2), x1 + 1, Math.max(y1, y2) + 1, color);
            return;
        }
        for (int x = x1; x <= x2; x++) {
            double t = (double) (x - x1) / (x2 - x1);
            int y = (int) Math.round(y1 + (y2 - y1) * t);
            int next = x < x2 ? (int) Math.round(y1 + (y2 - y1) * ((double) (x + 1 - x1) / (x2 - x1))) : y;
            g.fill(x, Math.min(y, next), x + 1, Math.max(y, next) + 1, color);
        }
    }
}
