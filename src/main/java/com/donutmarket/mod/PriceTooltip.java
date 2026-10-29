package com.donutmarket.mod;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public final class PriceTooltip {
    private PriceTooltip() {
    }

    public static void append(ItemStack stack, Item.TooltipContext context, TooltipFlag flag, List<Component> lines) {
        if (!Settings.get().tooltips) {
            return;
        }
        MarketItem item = ItemKeys.lookup(stack);
        if (item == null) {
            return;
        }
        lines.add(Component.empty());
        lines.add(Component.literal("Donut Market").withStyle(ChatFormatting.GOLD));
        int count = stack.getCount();
        int basis = Math.max(1, item.basis());
        int shownFor = count > 1 || Listings.currentKind() != null ? count : (Settings.get().perStack ? basis : 1);
        String unit = shownFor > 1 ? " for x" + shownFor : " each";
        if (item.orders() > 0) {
            lines.add(row("/orders", Money.format(item.ordersEach() * shownFor) + unit, ChatFormatting.GREEN));
        } else {
            lines.add(row("/orders", "no buy orders", ChatFormatting.DARK_GRAY));
        }
        if (item.ahLow() > 0) {
            lines.add(row("/ah", Money.format(item.ahEach() * shownFor) + unit, ChatFormatting.YELLOW));
        } else {
            lines.add(row("/ah", "nothing listed", ChatFormatting.DARK_GRAY));
        }
        if (shownFor > 1 && item.orders() > 0) {
            lines.add(row("Each", Money.format(item.ordersEach()) + " /orders, " + (item.ahLow() > 0 ? Money.format(item.ahEach()) + " /ah" : "no /ah"), ChatFormatting.GRAY));
        }
        double flip = item.flipEach() * shownFor;
        if (flip != 0) {
            lines.add(row("Flip", Money.signed(flip) + unit, flip > 0 ? ChatFormatting.AQUA : ChatFormatting.RED));
        }
        Listings.Verdict verdict = Listings.judge(Listings.currentKind(), stack, item);
        if (verdict != null) {
            ChatFormatting color = verdict.color() == 0xFFF2C14E ? ChatFormatting.GOLD
                    : verdict.color() == 0xFF4CE5B1 ? ChatFormatting.GREEN
                    : verdict.color() == 0xFFFF6B81 ? ChatFormatting.RED : ChatFormatting.GRAY;
            lines.add(Component.literal(verdict.text()).withStyle(color));
        }
    }

    private static Component row(String label, String value, ChatFormatting color) {
        MutableComponent line = Component.literal(" " + label + "  ").withStyle(ChatFormatting.GRAY);
        return line.append(Component.literal(value).withStyle(color));
    }
}
