package com.donutmarket.mod;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Swings {
    public static final Set<String> SHOP_ONLY = shopOnly();

    private static Set<String> shopOnly() {
        java.util.Set<String> keys = new java.util.HashSet<>(java.util.List.of(
                "diamond_helmet_p4", "diamond_chestplate_p4", "diamond_leggings_bp4", "diamond_boots_bp4",
                "netherite_helmet_p4", "netherite_chestplate_p4", "netherite_leggings_bp4", "netherite_boots_bp4",
                "diamond_sword_kb2", "diamond_sword_kb1", "diamond_sword_kb1_sharp5", "netherite_sword_kb2", "turtle_master",
                "terracotta"));
        String[] colors = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
                "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
        for (String c : colors) {
            for (String kind : new String[]{"concrete", "concrete_powder", "wool", "terracotta", "glazed_terracotta"}) {
                keys.add(c + "_" + kind);
            }
        }
        return Set.copyOf(keys);
    }
    private static final double NEAR = 0.02;

    public record Data(int basis, long[] times, double[] ah, double[] orders, double[] low, double[] bands, State sig) {
    }

    public record State(String signal, boolean shop, double ahBase, double ordersBase, double ahNow, double ordersNow, double ahChange, double ordersChange, int usable) {
        public boolean active() {
            return !"normal".equals(signal) && !"waiting".equals(signal);
        }

        public double mainPrice() {
            return shop ? ahNow : "sell".equals(signal) ? ahNow : ordersNow;
        }

        public double mainChange() {
            return shop ? ahChange : "sell".equals(signal) ? ahChange : ordersChange;
        }

        public double usual() {
            return shop ? ahBase : "sell".equals(signal) ? ahBase : ordersBase;
        }

        public int priority() {
            return switch (signal) {
                case "buy" -> 3;
                case "sell" -> 2;
                case "investment" -> 1;
                default -> 0;
            };
        }
    }

    private static volatile Map<String, Data> loaded = Collections.emptyMap();
    private static volatile long loadedAt;
    private static volatile long fetchedAt;
    private static volatile long failedAt;
    private static final java.util.concurrent.atomic.AtomicBoolean BUSY = new java.util.concurrent.atomic.AtomicBoolean();

    private Swings() {
    }

    public static Map<String, Data> data() {
        ensure();
        return loaded;
    }

    public static long loadedAt() {
        return loadedAt;
    }

    public static void ensure() {
        long now = System.currentTimeMillis();
        if (now - fetchedAt < 15_000L || now - failedAt < 20_000L || !BUSY.compareAndSet(false, true)) {
            return;
        }
        MarketData.api("/mod/swings").whenComplete((json, err) -> {
            try {
                if (json != null && json.isJsonArray()) {
                    loaded = parse(json);
                    loadedAt = System.currentTimeMillis();
                    fetchedAt = loadedAt;
                } else {
                    failedAt = System.currentTimeMillis();
                }
            } catch (Exception e) {
                failedAt = System.currentTimeMillis();
            } finally {
                BUSY.set(false);
            }
        });
    }

    public static void append(MarketData.Row row) {
        Data d = loaded.get(row.item());
        if (d == null || (d.times().length > 0 && row.time() <= d.times()[d.times().length - 1])) {
            return;
        }
        int n = d.times().length;
        int keep = Math.min(n, 59);
        int from = n - keep;
        long[] t = new long[keep + 1];
        double[] ah = new double[keep + 1];
        double[] or = new double[keep + 1];
        double[] lo = new double[keep + 1];
        System.arraycopy(d.times(), from, t, 0, keep);
        System.arraycopy(d.ah(), from, ah, 0, keep);
        System.arraycopy(d.orders(), from, or, 0, keep);
        System.arraycopy(d.low(), from, lo, 0, keep);
        t[keep] = row.time();
        ah[keep] = row.median();
        or[keep] = row.orders();
        lo[keep] = row.ah();
        Map<String, Data> next = new HashMap<>(loaded);
        next.put(row.item(), new Data(row.basis(), t, ah, or, lo, d.bands(), d.sig()));
        loaded = next;
        loadedAt = System.currentTimeMillis();
    }

    public static double volatility(String key) {
        Data d = loaded.get(key);
        if (d == null) {
            return Double.NaN;
        }
        boolean shop = SHOP_ONLY.contains(key);
        List<long[]> idx = new ArrayList<>();
        List<Double> vals = new ArrayList<>();
        for (int i = 0; i < d.times().length; i++) {
            double v = shop ? (d.low()[i] > 0 && d.orders()[i] == 0 && d.ah()[i] == 0 ? d.low()[i] : 0) : d.orders()[i];
            if (v > 0) {
                idx.add(new long[]{d.times()[i]});
                vals.add(v);
            }
        }
        if (vals.size() < 3) {
            return Double.NaN;
        }
        long end = idx.get(idx.size() - 1)[0];
        List<Double> window = new ArrayList<>();
        for (int i = 0; i < vals.size(); i++) {
            if (idx.get(i)[0] >= end - 3_600_000L) {
                window.add(vals.get(i));
            }
        }
        if (window.size() < 3) {
            return Double.NaN;
        }
        List<Double> returns = new ArrayList<>();
        for (int i = 1; i < window.size(); i++) {
            returns.add((window.get(i) / window.get(i - 1) - 1) * 100);
        }
        double mean = 0;
        for (double r : returns) {
            mean += r;
        }
        mean /= returns.size();
        double var = 0;
        for (double r : returns) {
            var += (r - mean) * (r - mean);
        }
        return Math.sqrt(var / returns.size());
    }

    public static int volatilityLevel(String key) {
        double v = volatility(key);
        if (Double.isNaN(v)) {
            return -1;
        }
        return v < 1 ? 0 : v < 2.5 ? 1 : v < 5 ? 2 : 3;
    }

    public static double shopAverage(String key) {
        Data d = loaded.get(key);
        if (d == null) {
            return 0;
        }
        List<Double> vals = new ArrayList<>();
        for (int i = 0; i < d.times().length; i++) {
            if (d.low()[i] > 0 && d.orders()[i] == 0 && d.ah()[i] == 0) {
                vals.add(d.low()[i]);
            }
        }
        if (vals.isEmpty()) {
            return 0;
        }
        List<Double> last = vals.subList(Math.max(0, vals.size() - 18), vals.size());
        double sum = 0;
        for (double v : last) {
            sum += v;
        }
        return sum / last.size();
    }

    public static Map<String, Data> parse(JsonElement root) {
        if (root == null || !root.isJsonArray()) {
            return Collections.emptyMap();
        }
        Map<String, Data> out = new HashMap<>();
        for (JsonElement el : root.getAsJsonArray()) {
            JsonObject o = el.getAsJsonObject();
            JsonArray t = o.getAsJsonArray("t");
            if (t == null) {
                continue;
            }
            int n = t.size();
            long[] times = new long[n];
            for (int i = 0; i < n; i++) {
                times[i] = t.get(i).getAsLong() * 1000L;
            }
            double[] bands = new double[6];
            JsonArray b = o.getAsJsonArray("bands");
            for (int i = 0; i < 6 && b != null && i < b.size(); i++) {
                bands[i] = b.get(i).isJsonNull() ? 0 : b.get(i).getAsDouble();
            }
            out.put(o.get("k").getAsString(), new Data(
                    o.has("b") && !o.get("b").isJsonNull() ? Math.max(1, o.get("b").getAsInt()) : 1,
                    times, nums(o.getAsJsonArray("a"), n), nums(o.getAsJsonArray("o"), n), nums(o.getAsJsonArray("l"), n), bands,
                    sig(o.get("sig"))));
        }
        return out;
    }

    private static State sig(JsonElement el) {
        if (el == null || !el.isJsonObject()) {
            return null;
        }
        JsonObject s = el.getAsJsonObject();
        String g = s.has("g") && !s.get("g").isJsonNull() ? s.get("g").getAsString() : "waiting";
        boolean shop = s.has("s") && !s.get("s").isJsonNull() && s.get("s").getAsBoolean();
        return new State(g, shop, val(s, "ab"), val(s, "ob"), val(s, "an"), val(s, "on"), val(s, "ac"), val(s, "oc"), (int) val(s, "n"));
    }

    private static double val(JsonObject o, String f) {
        JsonElement el = o.get(f);
        return el == null || el.isJsonNull() ? 0 : el.getAsDouble();
    }

    private static double[] nums(JsonArray a, int n) {
        double[] v = new double[n];
        for (int i = 0; a != null && i < n && i < a.size(); i++) {
            v[i] = a.get(i).isJsonNull() ? 0 : a.get(i).getAsDouble();
        }
        return v;
    }

    private static double median(List<Double> values) {
        List<Double> a = new ArrayList<>();
        for (double v : values) {
            if (v > 0) {
                a.add(v);
            }
        }
        if (a.isEmpty()) {
            return 0;
        }
        Collections.sort(a);
        int m = a.size() / 2;
        return a.size() % 2 == 1 ? a.get(m) : (a.get(m - 1) + a.get(m)) / 2;
    }

    public static State state(String key, Data d) {
        if (d == null) {
            return new State("waiting", SHOP_ONLY.contains(key), 0, 0, 0, 0, 0, 0, 0);
        }
        if (d.sig() != null) {
            State s = d.sig();
            boolean shop = s.shop() || SHOP_ONLY.contains(key);
            return shop == s.shop() ? s : new State(s.signal(), shop, s.ahBase(), s.ordersBase(), s.ahNow(), s.ordersNow(), s.ahChange(), s.ordersChange(), s.usable());
        }
        if (SHOP_ONLY.contains(key)) {
            List<Double> rows = new ArrayList<>();
            for (int i = 0; i < d.times().length; i++) {
                if (d.low()[i] > 0 && d.orders()[i] == 0 && d.ah()[i] == 0) {
                    rows.add(d.low()[i]);
                }
            }
            if (rows.size() < 21) {
                double last = rows.isEmpty() ? 0 : rows.get(rows.size() - 1);
                return new State("waiting", true, 0, 0, last, last, 0, 0, Math.min(rows.size(), 21));
            }
            double[] base = new double[3];
            double[] now = new double[3];
            double[] change = new double[3];
            for (int idx = 0; idx < 3; idx++) {
                int abs = rows.size() - 3 + idx;
                double sum = 0;
                int count = 0;
                for (int j = Math.max(0, abs - 18); j < abs; j++) {
                    sum += rows.get(j);
                    count++;
                }
                base[idx] = count > 0 ? sum / count : 0;
                now[idx] = rows.get(abs);
                change[idx] = base[idx] > 0 ? (now[idx] - base[idx]) / base[idx] * 100 : 0;
            }
            double hi = d.bands()[5];
            boolean nearHi = hi > 0 && now[2] >= hi * (1 - NEAR);
            boolean high = nearHi;
            for (int i = 0; i < 3; i++) {
                high &= base[i] > 0 && change[i] >= 8;
            }
            return new State(high ? "sell" : "normal", true, base[2], base[2], now[2], now[2], change[2], change[2], 21);
        }
        List<double[]> rows = new ArrayList<>();
        for (int i = 0; i < d.times().length; i++) {
            if (d.ah()[i] > 0 && d.orders()[i] > 0) {
                rows.add(new double[]{d.ah()[i], d.orders()[i]});
            }
        }
        if (rows.size() < 9) {
            double[] last = rows.isEmpty() ? new double[]{0, 0} : rows.get(rows.size() - 1);
            return new State("waiting", false, 0, 0, last[0], last[1], 0, 0, rows.size());
        }
        List<double[]> recent = rows.subList(rows.size() - 3, rows.size());
        List<double[]> prior = rows.subList(0, rows.size() - 3);
        List<double[]> baseline = prior.subList(Math.max(0, prior.size() - 18), prior.size());
        List<Double> a = new ArrayList<>();
        List<Double> o = new ArrayList<>();
        for (double[] r : baseline) {
            a.add(r[0]);
            o.add(r[1]);
        }
        double ahBase = median(a);
        double ordersBase = median(o);
        if (!(ahBase > 0 && ordersBase > 0)) {
            return new State("waiting", false, 0, 0, 0, 0, 0, 0, rows.size());
        }
        double[] last = recent.get(2);
        double ahNow = last[0];
        double ordersNow = last[1];
        double ahChange = (ahNow - ahBase) / ahBase * 100;
        double ordersChange = (ordersNow - ordersBase) / ordersBase * 100;
        boolean ahHigh = true;
        boolean ordersLow = true;
        boolean ordersHigh = true;
        for (double[] r : recent) {
            ahHigh &= (r[0] - ahBase) / ahBase * 100 >= 8;
            ordersLow &= (r[1] - ordersBase) / ordersBase * 100 <= -8;
            ordersHigh &= (r[1] - ordersBase) / ordersBase * 100 >= 8;
        }
        double[] bands = d.bands();
        boolean nearLo = bands[0] > 0 && ordersNow <= bands[0] * (1 + NEAR);
        boolean nearHiO = bands[1] > 0 && ordersNow >= bands[1] * (1 - NEAR);
        boolean nearHiA = bands[3] > 0 && ahNow >= bands[3] * (1 - NEAR);
        String signal = "normal";
        if (ordersLow && nearLo) {
            signal = "buy";
        } else if (ahHigh && nearHiA) {
            signal = "sell";
        } else if (ordersHigh && nearHiO) {
            signal = "investment";
        }
        return new State(signal, false, ahBase, ordersBase, ahNow, ordersNow, ahChange, ordersChange, rows.size());
    }

    public static String label(State s) {
        return switch (s.signal()) {
            case "buy" -> "Buy: /orders below the red line";
            case "sell" -> "Sell on AH: /ah above the green line";
            case "investment" -> "Sell to orders: /orders above the green line";
            case "waiting" -> "Building baseline";
            default -> "Normal range";
        };
    }

    public static String shortLabel(State s) {
        return switch (s.signal()) {
            case "buy" -> "BUY";
            case "sell" -> "SELL AH";
            case "investment" -> "SELL ORDERS";
            case "waiting" -> "WAIT";
            default -> "NORMAL";
        };
    }

    public static String hudLabel(State s) {
        return switch (s.signal()) {
            case "buy" -> "BUY";
            case "sell", "investment" -> "SELL";
            case "waiting" -> "WAIT";
            default -> "-";
        };
    }

    public static int color(State s) {
        return switch (s.signal()) {
            case "buy" -> Ui.GREEN;
            case "sell" -> Ui.RED;
            case "investment" -> Ui.GOLD;
            default -> Ui.MUTED;
        };
    }
}
