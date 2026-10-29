package com.donutmarket.mod;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class ListingHighlights {
    private ListingHighlights() {
    }

    public static void attach(Minecraft client, Screen screen, int width, int height) {
        if (!(screen instanceof ContainerScreen container)) {
            return;
        }
        Listings.Kind kind = Listings.kindOf(screen);
        if (kind == null && !Panels.isSell(container)) {
            return;
        }
        ScreenEvents.afterBackground(screen).register((s, graphics, mouseX, mouseY, delta) -> {
            ContainerScreen cs = (ContainerScreen) s;
            Listings.Kind k = Listings.kindOf(cs);
            if (k != null) {
                drawHighlights(cs, graphics, k);
            }
        });
        ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> {
            ContainerScreen cs = (ContainerScreen) s;
            Panels.draw(cs, graphics, Listings.kindOf(cs), mouseX, mouseY);
        });
    }

    private static void drawHighlights(ContainerScreen screen, GuiGraphicsExtractor graphics, Listings.Kind kind) {
        if (!Settings.get().highlights) {
            return;
        }
        ChestMenu menu = screen.getMenu();
        int rows = menu.getRowCount();
        int left = (screen.width - 176) / 2;
        int top = (screen.height - (114 + rows * 18)) / 2;
        int count = Math.min(rows * 9, menu.slots.size());
        for (int i = 0; i < count; i++) {
            Slot slot = menu.getSlot(i);
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            Listings.Verdict verdict = Listings.judge(kind, stack, ItemKeys.lookup(stack));
            if (verdict == null || verdict.color() == 0) {
                continue;
            }
            int x = left + slot.x;
            int y = top + slot.y;
            graphics.fill(x, y, x + 16, y + 16, (verdict.color() & 0x00FFFFFF) | 0x38000000);
            graphics.outline(x - 1, y - 1, 18, 18, verdict.color());
        }
    }
}
