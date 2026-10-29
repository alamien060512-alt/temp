package com.donutmarket.mod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Ranges {
    public record Result(double[] vwap, double[] hi, double[] lo, String label) {
    }

    private Ranges() {
    }

    private static boolean has(double v) {
        return !Double.isNaN(v) && v > 0;
    }

    public static double[] weights(double[] raw) {
        int n = raw.length;
        double[] w0 = new double[n];
        for (int i = 0; i < n; i++) {
            w0[i] = raw[i] > 0 ? raw[i] : 1;
        }
        double[] sorted = Arrays.copyOf(w0, n);
        Arrays.sort(sorted);
        double cap = (n > 0 ? sorted[(n - 1) / 2] : 1) * 3;
        double[] w = new double[n];
        for (int i = 0; i < n; i++) {
            w[i] = Math.min(w0[i], cap);
        }
        return w;
    }

    public static Result compute(long[] times, double[] values, double[] w) {
        int n = times.length;
        double[] vwap = new double[n];
        double[] hi = new double[n];
        double[] lo = new double[n];
        Arrays.fill(vwap, Double.NaN);
        Arrays.fill(hi, Double.NaN);
        Arrays.fill(lo, Double.NaN);
        int firstValid = -1;
        int lastValid = -1;
        int count = 0;
        for (int i = 0; i < n; i++) {
            if (has(values[i])) {
                if (firstValid < 0) {
                    firstValid = i;
                }
                lastValid = i;
                count++;
            }
        }
        if (count < 2) {
            return new Result(vwap, hi, lo, "VWAP");
        }
        double spanH = (times[lastValid] - times[firstValid]) / 3_600_000.0;
        long winH = Math.max(1, Math.min(24, Math.round(spanH / 6)));
        long win = winH * 3_600_000L;
        double pv = 0;
        double vol = 0;
        int from = 0;
        for (int i = 0; i < n; i++) {
            if (has(values[i])) {
                pv += values[i] * w[i];
                vol += w[i];
            }
            while (from < i && times[from] < times[i] - win) {
                if (has(values[from])) {
                    pv -= values[from] * w[from];
                    vol -= w[from];
                }
                from++;
            }
            vwap[i] = vol > 0 ? pv / vol : Double.NaN;
        }
        int step = Math.max(1, n / 200);
        int a = 0;
        double lh = Double.NaN;
        double ll = Double.NaN;
        for (int i = 0; i < n; i++) {
            while (a < i && times[a] < times[i] - win) {
                a++;
            }
            if (i % step == 0 || i == n - 1) {
                List<Double> xs = new ArrayList<>();
                for (int j = a; j <= i; j++) {
                    if (has(values[j])) {
                        xs.add(values[j]);
                    }
                }
                if (xs.size() >= 5) {
                    xs.sort(Double::compare);
                    lh = xs.get((int) Math.floor((xs.size() - 1) * 0.9));
                    ll = xs.get((int) Math.floor((xs.size() - 1) * 0.1));
                }
            }
            hi[i] = lh;
            lo[i] = ll;
        }
        return new Result(vwap, hi, lo, (winH >= 24 ? "24h" : winH + "h") + " VWAP");
    }
}
