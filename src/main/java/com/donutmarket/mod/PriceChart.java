package com.donutmarket.mod;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.DoubleFunction;

public class PriceChart {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(ZoneId.systemDefault());

    private long[] times = new long[0];
    private double[][] raw = new double[0][];
    private double[][] shown = new double[0][];
    private String[] names = new String[0];
    private int[] colors = new int[0];
    private boolean[] fills = new boolean[0];
    private boolean signed;
    private double filterMin;
    private double filterMax;
    private long mark = -1;
    private double pressX;
    private boolean moved;
    private DoubleFunction<String> format = Money::format;
    private List<Overlay> overlays = new ArrayList<>();
    private List<Level> levels = new ArrayList<>();
    private List<Segment> segments = new ArrayList<>();

    public record Overlay(double[] values, int color, String name, boolean dashed) {
    }

    public record Level(double price, int color, String label) {
    }

    public record Segment(long from, long to, double price, int color, String label) {
    }

    private boolean candles;

    public void candles(boolean on) {
        candles = on;
    }

    private void drawCandles(GuiGraphicsExtractor g, double[] v, int a, int b, double from, double to, double lo, double hi) {
        int n = Math.max(6, Math.min(90, plotW / 6));
        double step = (to - from) / n;
        if (step <= 0) {
            return;
        }
        int bw = Math.max(1, (int) Math.floor(plotW / (double) n) - 2);
        int i = a;
        for (int k = 0; k < n; k++) {
            double s = from + k * step;
            double e = s + step;
            double open = Double.NaN;
            double close = Double.NaN;
            double high = -Double.MAX_VALUE;
            double low = Double.MAX_VALUE;
            while (i <= b && times[i] < s) {
                i++;
            }
            int j = i;
            while (j <= b && times[j] < e) {
                double x = v[j];
                if (usable(x)) {
                    if (Double.isNaN(open)) {
                        open = x;
                    }
                    close = x;
                    high = Math.max(high, x);
                    low = Math.min(low, x);
                }
                j++;
            }
            i = j;
            if (Double.isNaN(open)) {
                continue;
            }
            int cx = px(s + step / 2, from, to);
            int c = close >= open ? Ui.GREEN : Ui.RED;
            int yh = py(high, lo, hi);
            int yl = py(low, lo, hi);
            g.fill(cx, yh, cx + 1, yl + 1, c);
            int yo = py(open, lo, hi);
            int yc = py(close, lo, hi);
            int top = Math.min(yo, yc);
            int bot = Math.max(yo, yc);
            if (bot - top < 1) {
                bot = top + 1;
            }
            int left = cx - bw / 2;
            g.fill(left, top, left + Math.max(1, bw), bot + 1, c);
        }
    }

    public void overlays(List<Overlay> list) {
        overlays = list == null ? new ArrayList<>() : list;
    }

    public void marks(List<Level> lines, List<Segment> spans) {
        levels = lines == null ? new ArrayList<>() : lines;
        segments = spans == null ? new ArrayList<>() : spans;
    }

    private double viewStart = 0;
    private double viewEnd = 1;
    private boolean dragging;
    private double lastX;

    private double viewFrom;
    private double viewTo;
    private int plotX;
    private int plotY;
    private int plotW;
    private int plotH;

    public void set(long[] t, double[][] series, String[] labels, int[] lineColors, boolean[] areas, boolean allowNegative, boolean keepView) {
        times = t == null ? new long[0] : t;
        raw = series == null ? new double[0][] : series;
        names = labels;
        colors = lineColors;
        fills = areas;
        signed = allowNegative;
        if (!keepView) {
            reset();
        }
        refilter();
    }

    public void clear() {
        overlays = new ArrayList<>();
        levels = new ArrayList<>();
        segments = new ArrayList<>();
        set(new long[0], new double[0][], new String[0], new int[0], new boolean[0], false, false);
    }

    public void format(DoubleFunction<String> f) {
        format = f;
    }

    public void filter(double min, double max) {
        if (min != filterMin || max != filterMax) {
            filterMin = min;
            filterMax = max;
            refilter();
        }
    }

    public void mark(long time) {
        mark = time;
    }

    public long[] times() {
        return times;
    }

    public boolean empty() {
        return times.length < 2;
    }

    public boolean zoomed() {
        return viewStart > 0.0001 || viewEnd < 0.9999;
    }

    public void reset() {
        viewStart = 0;
        viewEnd = 1;
        dragging = false;
    }

    private void refilter() {
        shown = new double[raw.length][];
        for (int s = 0; s < raw.length; s++) {
            shown[s] = signed ? raw[s] : limit(raw[s], filterMin, filterMax);
        }
    }

    public static double[] limit(double[] v, double min, double max) {
        if (min <= 0 && max <= 0) {
            return v;
        }
        double[] out = Arrays.copyOf(v, v.length);
        for (int i = 0; i < out.length; i++) {
            if (out[i] > 0 && ((min > 0 && out[i] < min) || (max > 0 && out[i] > max))) {
                out[i] = Double.NaN;
            }
        }
        return out;
    }

    private boolean usable(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v) && (signed || v > 0);
    }

    public void render(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my) {
        if (empty()) {
            plotW = 0;
            return;
        }
        long t0 = times[0];
        long dataEnd = times[times.length - 1];
        long t1 = !zoomed() && System.currentTimeMillis() > dataEnd ? System.currentTimeMillis() : dataEnd;
        double span = Math.max(1, t1 - t0);
        double from = t0 + viewStart * span;
        double to = t0 + viewEnd * span;

        int first = times.length;
        int last = -1;
        for (int i = 0; i < times.length; i++) {
            if (times[i] >= from && times[i] <= to) {
                first = Math.min(first, i);
                last = Math.max(last, i);
            }
        }
        if (last < 0) {
            first = 0;
            last = times.length - 1;
        }
        double lo = Double.MAX_VALUE;
        double hi = -Double.MAX_VALUE;
        for (double[] s : shown) {
            for (int i = first; i <= last; i++) {
                if (usable(s[i])) {
                    lo = Math.min(lo, s[i]);
                    hi = Math.max(hi, s[i]);
                }
            }
        }
        for (Overlay o : overlays) {
            for (int i = first; i <= last && i < o.values().length; i++) {
                if (usable(o.values()[i])) {
                    lo = Math.min(lo, o.values()[i]);
                    hi = Math.max(hi, o.values()[i]);
                }
            }
        }
        if (lo == Double.MAX_VALUE) {
            plotW = 0;
            return;
        }
        if (hi - lo < Math.max(Math.abs(hi), 1) * 0.002) {
            double pad = Math.max(Math.abs(hi) * 0.01, 1);
            lo -= pad;
            hi += pad;
        }
        double pad = (hi - lo) * 0.08;
        lo -= pad;
        hi += pad;

        int gutter = 0;
        String[] labels = new String[5];
        for (int i = 0; i < 5; i++) {
            labels[i] = format.apply(hi - (hi - lo) * i / 4);
            gutter = Math.max(gutter, font.width(labels[i]));
        }
        gutter += 5;
        plotX = x + gutter;
        plotY = y;
        plotW = Math.max(10, w - gutter);
        plotH = h;
        g.fill(plotX, plotY, plotX + plotW, plotY + plotH, Ui.PLOT);
        for (int i = 0; i < 5; i++) {
            int ly = plotY + 4 + (int) ((plotH - 8) * i / 4.0);
            g.fill(plotX, ly, plotX + plotW, ly + 1, 0x14FFFFFF);
            g.text(font, labels[i], plotX - 4 - font.width(labels[i]), ly - 4, Ui.MUTED, false);
        }
        if (signed && lo < 0 && hi > 0) {
            int zy = py(0, lo, hi);
            for (int zx = plotX; zx < plotX + plotW; zx += 4) {
                g.fill(zx, zy, Math.min(zx + 2, plotX + plotW), zy + 1, 0x55FFFFFF);
            }
        }

        g.enableScissor(plotX, plotY, plotX + plotW, plotY + plotH);
        int a = Math.max(0, first - 1);
        int b = Math.min(times.length - 1, last + 1);
        for (int s = 0; s < shown.length; s++) {
            if (s == 0 && candles && !signed) {
                drawCandles(g, shown[s], a, b, from, to, lo, hi);
            } else {
                drawSeries(g, shown[s], a, b, from, to, lo, hi, colors[s], fills[s], false);
            }
        }
        for (Overlay o : overlays) {
            if (o.values().length == times.length) {
                drawSeries(g, o.values(), a, b, from, to, lo, hi, o.color(), false, o.dashed());
            }
        }
        for (Segment sg : segments) {
            if (sg.to() < from || sg.from() > to || sg.price() < lo || sg.price() > hi) {
                continue;
            }
            int x1 = Math.max(plotX, px(sg.from(), from, to));
            int x2 = Math.min(plotX + plotW - 1, px(sg.to(), from, to));
            int yy = py(sg.price(), lo, hi);
            for (int cx = x1; cx <= x2; cx++) {
                if ((cx / 3) % 2 == 0) {
                    g.fill(cx, yy, cx + 1, yy + 1, sg.color());
                }
            }
            if (sg.to() <= to) {
                g.fill(x2 - 1, yy - 1, x2 + 2, yy + 2, sg.color());
                if (!sg.label().isEmpty()) {
                    g.text(font, sg.label(), Math.min(x2 + 3, plotX + plotW - font.width(sg.label()) - 1), yy - 9, sg.color(), false);
                }
            }
        }
        for (Level lv : levels) {
            if (lv.price() < lo || lv.price() > hi) {
                continue;
            }
            int yy = py(lv.price(), lo, hi);
            for (int cx = plotX; cx < plotX + plotW; cx++) {
                if ((cx / 2) % 3 != 2) {
                    g.fill(cx, yy, cx + 1, yy + 2, lv.color());
                }
            }
            int lw = font.width(lv.label());
            int lx = plotX + plotW - lw - 4;
            g.fill(lx - 2, yy - 10, lx + lw + 2, yy - 1, 0xC01A1B1C);
            g.text(font, lv.label(), lx, yy - 9, lv.color(), false);
        }
        if (mark >= 0 && mark >= from && mark <= to) {
            int mxp = px(mark, from, to);
            for (int yy = plotY; yy < plotY + plotH; yy += 3) {
                g.fill(mxp, yy, mxp + 1, yy + 2, 0xCCFFFFFF);
            }
            for (int s = 0; s < shown.length; s++) {
                int mi = Arrays.binarySearch(times, mark);
                if (mi >= 0 && usable(shown[s][mi])) {
                    int my2 = py(shown[s][mi], lo, hi);
                    g.fill(mxp - 3, my2 - 3, mxp + 4, my2 + 4, 0xFFFFFFFF);
                    g.fill(mxp - 2, my2 - 2, mxp + 3, my2 + 2 + 1, signed ? (shown[s][mi] >= 0 ? Ui.GREEN : Ui.RED) : colors[s]);
                }
            }
        }
        viewFrom = from;
        viewTo = to;
        int hover = -1;
        if (!dragging && Ui.inside(mx, my, plotX, plotY, plotX + plotW, plotY + plotH)) {
            double at = from + (double) (mx - plotX) / plotW * (to - from);
            double best = Double.MAX_VALUE;
            for (int i = first; i <= last; i++) {
                double d = Math.abs(times[i] - at);
                if (d < best && anyValue(i)) {
                    best = d;
                    hover = i;
                }
            }
            if (hover >= 0) {
                int hx = px(times[hover], from, to);
                g.fill(hx, plotY, hx + 1, plotY + plotH, 0x40FFFFFF);
                for (int s = 0; s < shown.length; s++) {
                    if (usable(shown[s][hover])) {
                        int hy = py(shown[s][hover], lo, hi);
                        g.fill(hx - 2, hy - 2, hx + 3, hy + 3, signed ? (shown[s][hover] >= 0 ? Ui.GREEN : Ui.RED) : colors[s]);
                    }
                }
            }
        }
        g.disableScissor();
        for (Level lv : levels) {
            if (lv.price() < lo || lv.price() > hi) {
                continue;
            }
            int yy = py(lv.price(), lo, hi);
            String tag = format.apply(lv.price());
            int tw = font.width(tag) + 4;
            int tx = Math.max(x, plotX - tw - 1);
            g.fill(tx, yy - 5, plotX, yy + 6, lv.color());
            g.text(font, tag, tx + 2, yy - 3, 0xFF1A1206, false);
        }

        String left = stamp((long) from, to - from);
        String right = zoomed() ? stamp((long) to, to - from) : "now";
        g.text(font, left, plotX, plotY + plotH + 3, Ui.MUTED, false);
        g.text(font, right, plotX + plotW - font.width(right), plotY + plotH + 3, Ui.MUTED, false);

        if (hover >= 0) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(FULL.format(Instant.ofEpochMilli(times[hover]))).withColor(Ui.MUTED));
            for (int s = 0; s < shown.length; s++) {
                double v = shown[s][hover];
                String value = usable(v) ? (signed ? signedText(v) : format.apply(v)) : "-";
                int vc = signed && usable(v) ? (v >= 0 ? Ui.GREEN : Ui.RED) : colors[s];
                lines.add(Component.literal(names[s] + "  ").withColor(Ui.SOFT).append(Component.literal(value).withColor(vc)));
            }
            for (Overlay o : overlays) {
                if (hover < o.values().length && usable(o.values()[hover]) && !o.name().isEmpty()) {
                    lines.add(Component.literal(o.name() + "  ").withColor(Ui.SOFT).append(Component.literal(format.apply(o.values()[hover])).withColor(o.color())));
                }
            }
            g.setTooltipForNextFrame(font, lines, java.util.Optional.empty(), mx, my);
        }
    }

    private String signedText(double v) {
        String f = format.apply(v);
        return v > 0 && !f.startsWith("+") ? "+" + f : f;
    }

    private boolean anyValue(int i) {
        for (double[] s : shown) {
            if (usable(s[i])) {
                return true;
            }
        }
        return false;
    }

    private static String stamp(long t, double span) {
        return (span <= 36 * 3_600_000L ? TIME : DAY).format(Instant.ofEpochMilli(t));
    }

    private int px(double t, double from, double to) {
        return plotX + (int) Math.round((t - from) / Math.max(1, to - from) * (plotW - 1));
    }

    private int py(double v, double lo, double hi) {
        int y = plotY + plotH - 4 - (int) Math.round((v - lo) / (hi - lo) * (plotH - 8));
        return Math.max(plotY, Math.min(plotY + plotH - 1, y));
    }

    private void drawSeries(GuiGraphicsExtractor g, double[] v, int a, int b, double from, double to, double lo, double hi, int color, boolean area, boolean dashed) {
        int prevX = Integer.MIN_VALUE;
        int prevY = 0;
        int zero = signed ? Math.max(plotY, Math.min(plotY + plotH, py(0, lo, hi))) : plotY + plotH;
        boolean zeroInside = signed && lo < 0 && hi > 0;
        int base = signed ? zero : plotY + plotH;
        for (int i = a; i <= b; i++) {
            double value = v[i];
            if (Double.isNaN(value)) {
                continue;
            }
            if (!usable(value)) {
                prevX = Integer.MIN_VALUE;
                continue;
            }
            int x = px(times[i], from, to);
            int y = py(value, lo, hi);
            if (prevX != Integer.MIN_VALUE && x == prevX) {
                prevY = y;
                continue;
            }
            if (prevX != Integer.MIN_VALUE) {
                int x1 = Math.max(prevX, plotX);
                int x2 = Math.min(x, plotX + plotW - 1);
                for (int cx = x1; cx <= x2; cx++) {
                    double t = x == prevX ? 1 : (double) (cx - prevX) / (x - prevX);
                    double tn = x == prevX ? 1 : Math.min(1, (double) (cx + 1 - prevX) / (x - prevX));
                    int cy = (int) Math.round(prevY + (y - prevY) * t);
                    int ny = cx < x ? (int) Math.round(prevY + (y - prevY) * tn) : cy;
                    int c = color;
                    if (signed) {
                        boolean up = zeroInside ? cy <= zero : hi > 0;
                        c = up ? Ui.GREEN : Ui.RED;
                    }
                    if (area) {
                        int fillColor = (c & 0x00FFFFFF) | 0x1E000000;
                        if (cy < base) {
                            g.fill(cx, cy, cx + 1, base, fillColor);
                        } else if (cy > base) {
                            g.fill(cx, base, cx + 1, cy, fillColor);
                        }
                    }
                    if (!dashed || (cx / 3) % 2 == 0) {
                        g.fill(cx, Math.min(cy, ny), cx + 1, Math.max(cy, ny) + 1, c);
                    }
                }
            } else if (i == b || !nextUsable(v, i, b)) {
                int c = signed ? (value >= 0 ? Ui.GREEN : Ui.RED) : color;
                g.fill(x - 1, y - 1, x + 2, y + 2, c);
            }
            prevX = x;
            prevY = y;
        }
        if (!dashed && prevX != Integer.MIN_VALUE) {
            int xEnd = Math.min(plotX + plotW - 1, px(to, from, to));
            if (xEnd > prevX) {
                int c = signed ? (zeroInside ? (prevY <= zero ? Ui.GREEN : Ui.RED) : (hi > 0 ? Ui.GREEN : Ui.RED)) : color;
                for (int cx = prevX; cx <= xEnd; cx++) {
                    if (area) {
                        int fillColor = (c & 0x00FFFFFF) | 0x1E000000;
                        if (prevY < base) {
                            g.fill(cx, prevY, cx + 1, base, fillColor);
                        } else if (prevY > base) {
                            g.fill(cx, base, cx + 1, prevY, fillColor);
                        }
                    }
                    g.fill(cx, prevY, cx + 1, prevY + 1, c);
                }
            }
        }
    }

    private boolean nextUsable(double[] v, int i, int b) {
        for (int j = i + 1; j <= b; j++) {
            if (Double.isNaN(v[j])) {
                continue;
            }
            return usable(v[j]);
        }
        return false;
    }

    public boolean over(double mx, double my) {
        return plotW > 0 && Ui.inside(mx, my, plotX, plotY, plotX + plotW, plotY + plotH);
    }

    public boolean click(double mx, double my, int button, boolean doubleClick) {
        if (button != 0 || !over(mx, my)) {
            return false;
        }
        if (doubleClick) {
            reset();
            return true;
        }
        dragging = true;
        lastX = mx;
        pressX = mx;
        moved = false;
        return true;
    }

    public boolean drag(double mx) {
        if (!dragging || plotW <= 0) {
            return false;
        }
        double dx = mx - lastX;
        lastX = mx;
        if (Math.abs(mx - pressX) > 2) {
            moved = true;
        }
        double width = viewEnd - viewStart;
        double shift = -dx / plotW * width;
        shift = Math.max(-viewStart, Math.min(1 - viewEnd, shift));
        viewStart += shift;
        viewEnd += shift;
        return true;
    }

    public boolean release() {
        boolean wasClick = dragging && !moved;
        dragging = false;
        return wasClick;
    }

    public int nearest(double mx) {
        if (plotW <= 0 || times.length == 0) {
            return -1;
        }
        double at = viewFrom + (mx - plotX) / plotW * (viewTo - viewFrom);
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < times.length; i++) {
            if (times[i] < viewFrom || times[i] > viewTo || !anyValue(i)) {
                continue;
            }
            double d = Math.abs(times[i] - at);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    public boolean scroll(double mx, double my, double amount) {
        if (!over(mx, my) || amount == 0 || times.length < 3) {
            return false;
        }
        double f = (mx - plotX) / plotW;
        double width = viewEnd - viewStart;
        double anchor = viewStart + f * width;
        double minWidth = Math.min(1, Math.max(0.01, 4.0 / times.length));
        double next = Math.max(minWidth, Math.min(1, width * (amount > 0 ? 0.8 : 1.25)));
        double start = anchor - f * next;
        start = Math.max(0, Math.min(1 - next, start));
        viewStart = start;
        viewEnd = start + next;
        return true;
    }
}
