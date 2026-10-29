package com.donutmarket.mod;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public final class Alerts {
    private static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId(6000L);
    private static long checkedVersion;

    private Alerts() {
    }

    public static double currentPrice(Alert alert) {
        MarketItem item = MarketData.item(alert.item);
        if (item == null) {
            return 0;
        }
        return alert.auction ? item.ahEach() : item.ordersEach();
    }

    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            return;
        }
        long version = MarketData.marketVersion();
        if (version == 0 || version == checkedVersion) {
            return;
        }
        checkedVersion = version;
        boolean changed = false;
        for (Alert alert : Settings.get().alerts) {
            if (alert.hit()) {
                continue;
            }
            double now = currentPrice(alert);
            if (now <= 0) {
                continue;
            }
            boolean reached = alert.below ? now <= alert.price : now >= alert.price;
            if (reached) {
                alert.hitAt = System.currentTimeMillis();
                alert.hitPrice = now;
                changed = true;
                announce(mc, alert, now);
            }
        }
        if (changed) {
            Settings.save();
        }
    }

    private static void announce(Minecraft mc, Alert alert, double now) {
        String name = ItemKeys.name(alert.item);
        String where = alert.auction ? "/ah" : "/orders";
        SystemToast.add(mc.gui.toastManager(), TOAST,
                Component.literal("Price alert: " + name),
                Component.literal(where + " is " + Money.format(now) + " each"));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.6F, 0.9F));
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("[Donut Market] ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(name + " hit " + Money.format(now) + " on " + where).withStyle(ChatFormatting.WHITE)));
        }
    }
}
