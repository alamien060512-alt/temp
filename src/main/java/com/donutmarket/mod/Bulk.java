package com.donutmarket.mod;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public final class Bulk {
    public record Wall(double price, double amount) {
    }

    public record Past(double price, double amount, long from, long to) {
    }

    private record Cached<T>(long at, T value) {
    }

    private record Scan(long t, TreeMap<Double, Double> levels, double ref, double demand, double ahLow) {
    }

    private static final Map<String, Cached<List<Wall>>> NOW = new ConcurrentHashMap<>();
    private static final Map<String, Cached<List<Past>>> PAST = new ConcurrentHashMap<>();
    private static final Set<String> BUSY = ConcurrentHashMap.newKeySet();

    private Bulk() {
    }

    public static List<Wall> now(String key, Runnable loaded) {
        Cached<List<Wall>> hit = NOW.get(key);
        if ((hit == null || System.currentTimeMillis() - hit.at() > 30_000L) && BUSY.add("n" + key)) {
            MarketData.api("/mod/depth?item=" + key).whenComplete((json, err) -> {
                try {
                    if (json != null && json.isJsonArray()) {
                        NOW.put(key, new Cached<>(System.currentTimeMillis(), walls(json.getAsJsonArray())));
                        loaded.run();
                    }
                } catch (Exception ignored) {
                } finally {
                    BUSY.remove("n" + key);
                }
            });
        }
        return hit == null ? Collections.emptyList() : hit.value();
    }

    public static List<Past> past(String key, Runnable loaded) {
        Cached<List<Past>> hit = PAST.get(key);
        if ((hit == null || System.currentTimeMillis() - hit.at() > 120_000L) && BUSY.add("p" + key)) {
            MarketData.api("/mod/bulk?item=" + key).whenComplete((json, err) -> {
                try {
                    if (json != null && json.isJsonArray()) {
                        PAST.put(key, new Cached<>(System.currentTimeMillis(), pasts(json.getAsJsonArray())));
                        loaded.run();
                    }
                } catch (Exception ignored) {
                } finally {
                    BUSY.remove("p" + key);
                }
            });
        }
        return hit == null ? Collections.emptyList() : hit.value();
    }

    private static List<Past> pasts(JsonArray rows) {
        List<Past> out = new ArrayList<>();
        for (JsonElement el : rows) {
            JsonObject o = el.getAsJsonObject();
            double price = num(o, "price");
            long a = time(o, "first_seen");
            long b = time(o, "last_seen");
            if (price > 0 && a > 0 && b > 0) {
                out.add(new Past(price, num(o, "max_amount"), a, b));
            }
        }
        out.sort((x, y) -> Double.compare(y.amount(), x.amount()));
        return out.size() > 25 ? new ArrayList<>(out.subList(0, 25)) : out;
    }

    private static double niceFloor(double n) {
        if (!(n > 0)) {
            return 0;
        }
        double b = Math.pow(10, Math.floor(Math.log10(n)));
        double m = n / b;
        return (m >= 5 ? 5 : m >= 2 ? 2 : 1) * b;
    }

    private static double realDemand(double dem, double top, TreeMap<Double, Double> levels) {
        if (levels.isEmpty()) {
            return dem;
        }
        double ref = top > 0 ? top : levels.lastKey();
        double s = 0;
        for (Map.Entry<Double, Double> e : levels.entrySet()) {
            if (e.getKey() >= ref * 0.1) {
                s += e.getValue();
            }
        }
        return s > 0 ? Math.min(dem > 0 ? dem : s, s) : dem;
    }

    static List<Wall> walls(JsonArray rows) {
        List<JsonObject> all = new ArrayList<>();
        for (JsonElement el : rows) {
            all.add(el.getAsJsonObject());
        }
        if (all.isEmpty()) {
            return Collections.emptyList();
        }
        all.sort((a, b) -> Long.compare(a.get("t").getAsLong(), b.get("t").getAsLong()));
        long lastT = all.get(all.size() - 1).get("t").getAsLong();
        List<Scan> scans = new ArrayList<>();
        for (int i = all.size() - 1; i >= 0; i--) {
            JsonObject r = all.get(i);
            long t = r.get("t").getAsLong();
            if (lastT - t > 900) {
                break;
            }
            TreeMap<Double, Double> lv = new TreeMap<>();
            JsonArray levels = r.has("lv") && r.get("lv").isJsonArray() ? r.getAsJsonArray("lv") : new JsonArray();
            for (JsonElement l : levels) {
                JsonArray pq = l.getAsJsonArray();
                double p = Math.round(pq.get(0).getAsDouble() * 100) / 100.0;
                double q = pq.get(1).getAsDouble();
                if (p > 0 && q > 0) {
                    lv.merge(p, q, Double::sum);
                }
            }
            if (lv.isEmpty()) {
                continue;
            }
            double top = num(r, "top");
            double total = 0;
            for (Map.Entry<Double, Double> e : lv.entrySet()) {
                if (!(top > 0) || e.getKey() >= top * 0.1) {
                    total += e.getValue();
                }
            }
            double acc = 0;
            double ref = 0;
            for (Map.Entry<Double, Double> e : lv.entrySet()) {
                if (top > 0 && e.getKey() < top * 0.1) {
                    continue;
                }
                acc += e.getValue();
                if (acc >= total / 2) {
                    ref = e.getKey();
                    break;
                }
            }
            double dem = realDemand(num(r, "dem"), top, lv);
            scans.add(new Scan(t, lv, ref, dem > 0 ? dem : total, num(r, "ah")));
        }
        if (scans.isEmpty()) {
            return Collections.emptyList();
        }
        Collections.reverse(scans);
        List<Double> dems = new ArrayList<>();
        for (Scan s : scans) {
            dems.add(s.demand());
        }
        Collections.sort(dems);
        double minAmount = Math.max(500, niceFloor(dems.get((dems.size() - 1) / 2) * 0.05));
        Map<Double, double[]> seen = new LinkedHashMap<>();
        for (Scan sc : scans) {
            for (Map.Entry<Double, Double> e : sc.levels().entrySet()) {
                double p = e.getKey();
                double q = e.getValue();
                double[] entry = seen.computeIfAbsent(p, k -> new double[]{0, 0});
                entry[0] = q;
                if (q >= minAmount && p >= sc.ref() * 0.5 && p <= sc.ref() * 2) {
                    entry[1] = 1;
                }
            }
        }
        Set<Double> live = new HashSet<>();
        for (Scan sc : scans.subList(Math.max(0, scans.size() - 2), scans.size())) {
            live.addAll(sc.levels().keySet());
        }
        double ahLow = scans.get(scans.size() - 1).ahLow();
        double cap = ahLow > 0 ? ahLow * 1.10 : Double.MAX_VALUE;
        List<Wall> out = new ArrayList<>();
        for (Map.Entry<Double, double[]> e : seen.entrySet()) {
            double p = e.getKey();
            double[] v = e.getValue();
            if (v[1] > 0 && v[0] >= minAmount * 0.5 && live.contains(p) && p <= cap) {
                out.add(new Wall(p, v[0]));
            }
        }
        out.sort((a, b) -> Double.compare(b.price(), a.price()));
        return out.size() > 12 ? new ArrayList<>(out.subList(0, 12)) : out;
    }

    private static double num(JsonObject o, String f) {
        JsonElement el = o.get(f);
        return el == null || el.isJsonNull() ? 0 : el.getAsDouble();
    }

    private static long time(JsonObject o, String f) {
        JsonElement el = o.get(f);
        if (el == null || el.isJsonNull()) {
            return 0;
        }
        try {
            return OffsetDateTime.parse(el.getAsString().replace(' ', 'T')).toInstant().toEpochMilli();
        } catch (Exception e) {
            try {
                return java.time.LocalDateTime.parse(el.getAsString().replace(' ', 'T')).toInstant(java.time.ZoneOffset.UTC).toEpochMilli();
            } catch (Exception e2) {
                return 0;
            }
        }
    }
}
