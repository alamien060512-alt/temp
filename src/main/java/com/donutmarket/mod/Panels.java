package com.donutmarket.mod;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Panels {
    private static final int WIDTH = 148;
    private static final int BG = 0xE6161718;
    private static final int BORDER = 0xFF303134;
    private static final int TEXT = 0xFFEEF0F3;
    private static final int MUTED = 0xFF979CA3;
    private static final int GOLD = 0xFFF2C14E;
    private static final int GREEN = 0xFF4CE5B1;
    private static final int RED = 0xFFFF6B81;
    private static final int BLUE = 0xFF7FB2FF;

    private Panels() {
    }

    public static boolean isSell(ContainerScreen screen) {
        return screen.getTitle().getString().trim().startsWith("Sell");
    }

    private static String lastHovered = "";
    private static int lastCount = 1;

    public static void draw(ContainerScreen screen, GuiGraphicsExtractor g, Listings.Kind kind, int mouseX, int mouseY) {
        if (!Settings.get().panels) {
            return;
        }
        ChestMenu menu = screen.getMenu();
        int rows = menu.getRowCount();
        int chestLeft = (screen.width - 176) / 2;
        int chestTop = (screen.height - (114 + rows * 18)) / 2;
        int x = chestLeft - WIDTH - 6;
        if (x < 4) {
            x = chestLeft + 176 + 6;
            if (x + WIDTH > screen.width - 4) {
                return;
            }
        }
        int count = Math.min(rows * 9, menu.slots.size());
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            stacks.add(menu.getSlot(i).getItem());
        }
        Font font = screen.getFont();
        String hovered = "";
        for (int i = 0; i < count; i++) {
            int sx = chestLeft + menu.getSlot(i).x;
            int sy = chestTop + menu.getSlot(i).y;
            if (mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16) {
                MarketItem m = ItemKeys.lookup(stacks.get(i));
                if (m != null && Listings.listedPrice(stacks.get(i)) > 0) {
                    hovered = m.key();
                    lastCount = Math.max(1, stacks.get(i).getCount());
                }
            }
        }
        if (!hovered.isEmpty()) {
            lastHovered = hovered;
        }
        if (kind != null) {
            listingPanel(g, font, x, chestTop, kind, stacks, lastHovered);
        } else {
            sellPanel(g, font, x, chestTop, stacks);
        }
    }

    private static void listingPanel(GuiGraphicsExtractor g, Font font, int x, int y, Listings.Kind kind, List<ItemStack> stacks, String hovered) {
        Map<String, Integer> votes = new HashMap<>();
        for (ItemStack s : stacks) {
            MarketItem item = ItemKeys.lookup(s);
            if (item != null && Listings.listedPrice(s) > 0) {
                votes.merge(item.key(), 1, Integer::sum);
            }
        }
        String key = votes.containsKey(hovered) ? hovered : votes.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        MarketItem item = MarketData.item(key);
        if (item == null) {
            return;
        }
        int amount = key.equals(hovered) ? lastCount : 1;
        boolean extra = amount > 1;
        int h = 128 + (extra ? 26 : 0);
        frame(g, x, y, h);
        int ty = y + 6;
        g.item(ItemKeys.stackFor(key), x + 6, ty - 2);
        g.text(font, fit(font, ItemKeys.name(key), WIDTH - 32), x + 26, ty + 3, TEXT, false);
        ty += 20;
        ty = row(g, font, x, ty, "Best order", item.orders() > 0 ? Money.format(item.ordersEach()) + " ea" : "-", GREEN);
        ty = row(g, font, x, ty, "Cheapest AH", item.ahLow() > 0 ? Money.format(item.ahEach()) + " ea" : "-", GOLD);
        double flip = item.flipEach();
        ty = row(g, font, x, ty, "Order → AH", flip == 0 ? "-" : Money.signed(flip), flip > 0 ? BLUE : RED);
        if (item.basis() > 1 && flip != 0) {
            ty = row(g, font, x, ty, "Per x" + item.basis(), Money.signed(flip * item.basis()), flip > 0 ? BLUE : RED);
        }
        if (extra) {
            ty += 4;
            g.text(font, "This listing, x" + amount, x + 7, ty, TEXT, false);
            ty += 11;
            String both = (item.orders() > 0 ? Money.format(item.ordersEach() * amount) : "-") + " / " + (item.ahLow() > 0 ? Money.format(item.ahEach() * amount) : "-");
            ty = row(g, font, x, ty, "Orders / AH", both, GOLD);
        }
        ty += 4;
        double[] spark = item.spark();
        double[] r = Chart.range(spark);
        if (r != null && spark.length > 1) {
            g.text(font, "/orders, 24h", x + 7, ty, MUTED, false);
            Chart.line(g, spark, r[0], r[1], x + 7, ty + 11, WIDTH - 14, y + h - 6 - (ty + 11), spark[spark.length - 1] >= spark[0] ? GREEN : RED);
        }
    }

    private static boolean filler(ItemStack s) {
        String path = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
        return path.endsWith("glass_pane") || path.equals("barrier") || path.equals("arrow") || s.has(DataComponents.CUSTOM_NAME);
    }

    private static void sellPanel(GuiGraphicsExtractor g, Font font, int x, int y, List<ItemStack> stacks) {
        Map<String, Long> totals = new LinkedHashMap<>();
        long untracked = 0;
        for (ItemStack s : stacks) {
            if (s.isEmpty()) {
                continue;
            }
            MarketItem item = ItemKeys.lookup(s);
            if (item == null) {
                if (!filler(s)) {
                    untracked += s.getCount();
                }
                continue;
            }
            totals.merge(item.key(), (long) s.getCount(), Long::sum);
        }
        if (totals.isEmpty() && untracked == 0) {
            return;
        }
        List<Map.Entry<String, Long>> list = new ArrayList<>(totals.entrySet());
        list.sort((a, b) -> Double.compare(value(b), value(a)));
        int shown = Math.min(6, list.size());
        int h = 46 + shown * 21 + (list.size() > shown ? 11 : 0) + (untracked > 0 ? 11 : 0);
        frame(g, x, y, h);
        int ty = y + 6;
        g.text(font, "If sold to buy orders", x + 7, ty, GOLD, false);
        ty += 14;
        double total = 0;
        double totalAh = 0;
        for (Map.Entry<String, Long> e : list) {
            MarketItem item = MarketData.item(e.getKey());
            total += item.ordersEach() * e.getValue();
            totalAh += item.ahEach() * e.getValue();
        }
        for (int i = 0; i < shown; i++) {
            Map.Entry<String, Long> e = list.get(i);
            MarketItem item = MarketData.item(e.getKey());
            g.item(ItemKeys.stackFor(e.getKey()), x + 5, ty);
            g.text(font, fit(font, ItemKeys.name(e.getKey()), WIDTH - 32), x + 24, ty, TEXT, false);
            String value = item.orders() > 0 ? Money.format(item.ordersEach() * e.getValue()) : "no orders";
            int vw = font.width(value);
            String each = item.orders() > 0 ? "x" + count(e.getValue()) + " · " + Money.format(item.ordersEach()) + " ea" : "x" + count(e.getValue());
            if (font.width(each) > WIDTH - 35 - vw) {
                each = "x" + count(e.getValue());
            }
            g.text(font, each, x + 24, ty + 9, MUTED, false);
            g.text(font, value, x + WIDTH - 7 - vw, ty + 9, item.orders() > 0 ? GREEN : MUTED, false);
            ty += 21;
        }
        if (list.size() > shown) {
            g.text(font, "+" + (list.size() - shown) + " more", x + 7, ty, MUTED, false);
            ty += 11;
        }
        if (untracked > 0) {
            g.text(font, count(untracked) + (untracked == 1 ? " item" : " items") + " not tracked", x + 7, ty, MUTED, false);
            ty += 11;
        }
        g.fill(x + 6, ty + 1, x + WIDTH - 6, ty + 2, BORDER);
        ty += 6;
        g.text(font, "Total", x + 7, ty, TEXT, false);
        String sum = Money.format(total);
        g.text(font, sum, x + WIDTH - 7 - font.width(sum), ty, GREEN, false);
        ty += 11;
        String ah = "On /ah: " + Money.format(totalAh);
        g.text(font, ah, x + WIDTH - 7 - font.width(ah), ty, MUTED, false);
    }

    private static double value(Map.Entry<String, Long> e) {
        MarketItem item = MarketData.item(e.getKey());
        return item == null ? 0 : item.ordersEach() * e.getValue();
    }

    private static void frame(GuiGraphicsExtractor g, int x, int y, int h) {
        g.fill(x, y, x + WIDTH, y + h, BORDER);
        g.fill(x + 1, y + 1, x + WIDTH - 1, y + h - 1, BG);
    }

    private static int row(GuiGraphicsExtractor g, Font font, int x, int y, String label, String value, int color) {
        int valueWidth = font.width(value);
        g.text(font, fit(font, label, WIDTH - 20 - valueWidth), x + 7, y, MUTED, false);
        g.text(font, value, x + WIDTH - 7 - valueWidth, y, color, false);
        return y + 11;
    }

    private static String fit(Font font, String text, int max) {
        if (font.width(text) <= max) {
            return text;
        }
        String cut = text;
        while (cut.length() > 1 && font.width(cut + "...") > max) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "...";
    }

    private static String count(long n) {
        if (n >= 1_000_000) {
            return String.format(java.util.Locale.ROOT, "%.1fM", n / 1_000_000.0);
        }
        if (n >= 10_000) {
            return String.format(java.util.Locale.ROOT, "%.1fK", n / 1_000.0);
        }
        return String.format(java.util.Locale.ROOT, "%,d", n);
    }
}
