package com.donutmarket.mod;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class ItemPicker {
    private static final int ROW = 16;
    private static final int MAX = 6;

    private final EditBox box;
    private final Consumer<String> onPick;
    private final List<String> matches = new ArrayList<>();
    private String picked = "";
    private boolean open;

    public ItemPicker(Font font, int x, int y, int width, Consumer<String> onPick) {
        this.onPick = onPick;
        this.box = new EditBox(font, x, y, width, 18, Component.literal("Item"));
        box.setHint(Component.literal("Item, e.g. netherite ingot"));
        box.setMaxLength(40);
        box.setResponder(text -> {
            if (!picked.isEmpty() && !text.equals(ItemKeys.name(picked))) {
                picked = "";
                onPick.accept("");
            }
            search(text);
        });
    }

    public EditBox box() {
        return box;
    }

    public String picked() {
        return picked;
    }

    public void clear() {
        picked = "";
        box.setValue("");
        matches.clear();
        open = false;
    }

    private void search(String text) {
        matches.clear();
        String q = text.trim().toLowerCase(Locale.ROOT);
        open = !q.isEmpty() && picked.isEmpty();
        if (!open) {
            return;
        }
        List<String> found = new ArrayList<>();
        for (MarketItem item : MarketData.all()) {
            String name = ItemKeys.name(item.key()).toLowerCase(Locale.ROOT);
            if (name.contains(q) || item.key().replace('_', ' ').contains(q)) {
                found.add(item.key());
            }
        }
        found.sort(Comparator.comparing((String k) -> !ItemKeys.name(k).toLowerCase(Locale.ROOT).startsWith(q))
                .thenComparing(k -> ItemKeys.name(k).length()));
        matches.addAll(found.subList(0, Math.min(MAX, found.size())));
    }

    public void render(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
        if (!open || matches.isEmpty() || !box.isFocused()) {
            return;
        }
        int x = box.getX();
        int y = box.getY() + 20;
        int w = box.getWidth();
        g.fill(x, y, x + w, y + matches.size() * ROW + 2, 0xFF303134);
        g.fill(x + 1, y + 1, x + w - 1, y + matches.size() * ROW + 1, 0xFF161617);
        for (int i = 0; i < matches.size(); i++) {
            int ry = y + 1 + i * ROW;
            if (mouseX >= x && mouseX < x + w && mouseY >= ry && mouseY < ry + ROW) {
                g.fill(x + 1, ry, x + w - 1, ry + ROW, 0x33FFFFFF);
            }
            String key = matches.get(i);
            g.pose().pushMatrix();
            g.pose().translate(x + 3, ry);
            g.pose().scale(0.875F, 0.875F);
            g.item(ItemKeys.stackFor(key), 0, 0);
            g.pose().popMatrix();
            g.text(font, ItemKeys.name(key), x + 22, ry + 4, 0xFFEEF0F3, false);
        }
    }

    public boolean click(double mouseX, double mouseY) {
        if (!open || matches.isEmpty() || !box.isFocused()) {
            return false;
        }
        int x = box.getX();
        int y = box.getY() + 21;
        if (mouseX < x || mouseX >= x + box.getWidth() || mouseY < y || mouseY >= y + matches.size() * ROW) {
            return false;
        }
        int index = (int) ((mouseY - y) / ROW);
        if (index < 0 || index >= matches.size()) {
            return false;
        }
        pick(matches.get(index));
        return true;
    }

    public void pick(String key) {
        picked = key;
        open = false;
        matches.clear();
        box.setValue(ItemKeys.name(key));
        onPick.accept(key);
    }

    public boolean covers(double mouseX, double mouseY) {
        if (!open || matches.isEmpty() || !box.isFocused()) {
            return false;
        }
        return mouseX >= box.getX() && mouseX < box.getX() + box.getWidth()
                && mouseY >= box.getY() + 20 && mouseY < box.getY() + 22 + matches.size() * ROW;
    }
}
