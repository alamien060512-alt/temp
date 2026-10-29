package com.donutmarket.mod;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

public final class Crash {
    public record Row(String key, int basis, double now, double to, double drop, long items, int orders, double fill, double eta, int risk) {
        boolean hasCliff() {
            return items >= 0;
        }

        String level() {
            return risk >= 70 ? "danger" : risk >= 40 ? "watch" : "ok";
        }
    }

    private static List<Row> rows = new ArrayList<>();
    private static long loadedAt;
    private static boolean loading;
    private static boolean failed;

    private Crash() {
    }

    public static List<Row> rows() {
        return rows;
    }

    public static long loadedAt() {
        return loadedAt;
    }

    public static boolean failed() {
        return failed;
    }

    public static Row of(String key) {
        for (Row r : rows) {
            if (r.key().equals(key)) {
                return r;
            }
        }
        return null;
    }

    private static double num(JsonObject o, String f, double fallback) {
        return o.has(f) && !o.get(f).isJsonNull() ? o.get(f).getAsDouble() : fallback;
    }

    public static void load(Runnable done) {
        if (loading) {
            return;
        }
        loading = true;
        MarketData.api("/mod/crash").thenAccept(json -> Minecraft.getInstance().execute(() -> {
            loading = false;
            if (json == null || !json.isJsonArray()) {
                failed = true;
            } else {
                failed = false;
                List<Row> list = new ArrayList<>();
                for (JsonElement el : json.getAsJsonArray()) {
                    try {
                        JsonObject o = el.getAsJsonObject();
                        list.add(new Row(o.get("k").getAsString(), (int) num(o, "b", 1), num(o, "now", 0), num(o, "to", 0), num(o, "d", 0),
                                (long) num(o, "q", -1), (int) num(o, "n", 0), num(o, "f", 0), num(o, "eta", -1), (int) num(o, "risk", 0)));
                    } catch (Exception ignored) {
                    }
                }
                rows = list;
                loadedAt = System.currentTimeMillis();
            }
            if (done != null) {
                done.run();
            }
        }));
    }

    static String eta(double minutes) {
        if (minutes < 0) {
            return "-";
        }
        if (minutes < 60) {
            return Math.max(1, Math.round(minutes)) + "m";
        }
        if (minutes < 1440) {
            return String.format(java.util.Locale.ROOT, minutes < 600 ? "%.1fh" : "%.0fh", minutes / 60);
        }
        return Math.round(minutes / 1440) + "d";
    }
}
