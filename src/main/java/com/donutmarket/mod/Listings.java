package com.donutmarket.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Listings {
    private static final Pattern PRICE = Pattern.compile("\\$\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([KMB]?)", Pattern.CASE_INSENSITIVE);

    public enum Kind { AUCTION, ORDERS }

    public record Verdict(int color, String text, double unitPrice) {
    }

    private Listings() {
    }

    public static Kind kindOf(Screen screen) {
        if (!(screen instanceof ContainerScreen)) {
            return null;
        }
        String title = screen.getTitle().getString().trim();
        if (title.startsWith("Auction")) {
            return Kind.AUCTION;
        }
        if (title.startsWith("Orders")) {
            return Kind.ORDERS;
        }
        return null;
    }

    public static Kind currentKind() {
        return kindOf(Minecraft.getInstance().gui.screen());
    }

    public static double listedPrice(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) {
            return 0;
        }
        for (Component line : lore.lines()) {
            Matcher m = PRICE.matcher(line.getString());
            if (m.find()) {
                return parse(m.group(1), m.group(2));
            }
        }
        return 0;
    }

    public static Verdict judge(Kind kind, ItemStack stack, MarketItem item) {
        if (kind == null || item == null) {
            return null;
        }
        double listed = listedPrice(stack);
        if (listed <= 0) {
            return null;
        }
        int count = Math.max(1, stack.getCount());
        String forWhat = count > 1 ? " for x" + count : "";
        if (kind == Kind.AUCTION) {
            double unit = listed / count;
            double ordersAll = item.ordersEach() * count;
            double usualAll = item.usualAhEach() * count;
            if (ordersAll > 0 && listed < ordersAll * 0.98) {
                return new Verdict(0xFFF2C14E, "Instant flip: /orders pays " + Money.format(ordersAll) + forWhat + " (" + Money.signed(ordersAll - listed) + ")", unit);
            }
            if (usualAll > 0) {
                double diff = (listed - usualAll) / usualAll * 100;
                String text = Money.percent(diff) + " vs usual /ah (" + Money.format(usualAll) + forWhat + ")";
                if (diff <= -10) {
                    return new Verdict(0xFF4CE5B1, text, unit);
                }
                if (diff >= 25) {
                    return new Verdict(0xFFFF6B81, text, unit);
                }
                return new Verdict(0, text, unit);
            }
            return null;
        }
        double unit = listed;
        int basis = Math.max(1, item.basis());
        double ahEach = item.ahEach();
        double usual = item.usualOrdersEach();
        String per = basis > 1 ? " (" + Money.format(unit * basis) + " per x" + basis + ")" : "";
        if (ahEach > 0 && unit > ahEach * 1.02) {
            return new Verdict(0xFFF2C14E, "Pays more than the cheapest /ah" + per, unit);
        }
        if (usual > 0) {
            double diff = (unit - usual) / usual * 100;
            String text = Money.percent(diff) + " vs usual /orders" + per;
            if (diff >= 10) {
                return new Verdict(0xFF4CE5B1, text, unit);
            }
            if (diff <= -20) {
                return new Verdict(0xFFFF6B81, text, unit);
            }
            return new Verdict(0, text, unit);
        }
        return null;
    }

    private static double parse(String number, String suffix) {
        double value;
        try {
            value = Double.parseDouble(number.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
        switch (suffix.toUpperCase(Locale.ROOT)) {
            case "K" -> value *= 1_000;
            case "M" -> value *= 1_000_000;
            case "B" -> value *= 1_000_000_000;
            default -> {
            }
        }
        return value;
    }
}
