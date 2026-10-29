package com.donutmarket.mod;

import java.util.Locale;

public final class Money {
    private Money() {
    }

    public static String format(double value) {
        double abs = Math.abs(value);
        String sign = value < 0 ? "-" : "";
        if (abs >= 1_000_000_000) {
            return sign + "$" + trim(abs / 1_000_000_000) + "B";
        }
        if (abs >= 1_000_000) {
            return sign + "$" + trim(abs / 1_000_000) + "M";
        }
        if (abs >= 10_000) {
            return sign + "$" + trim(abs / 1_000) + "K";
        }
        if (abs >= 100) {
            return sign + "$" + String.format(Locale.ROOT, "%,.0f", abs);
        }
        return sign + "$" + String.format(Locale.ROOT, "%,.2f", abs).replaceAll("\\.00$", "");
    }

    public static String signed(double value) {
        return (value > 0 ? "+" : "") + format(value);
    }

    public static String percent(double value) {
        return (value > 0 ? "+" : "") + String.format(Locale.ROOT, "%.1f%%", value);
    }

    private static String trim(double value) {
        String text = String.format(Locale.ROOT, value >= 100 ? "%.1f" : "%.2f", value);
        if (text.contains(".")) {
            text = text.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return text;
    }
}
