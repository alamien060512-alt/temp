package com.donutmarket.mod;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class SettingsScreen extends Dashboard {
    private static final String[][] TOGGLES = {
            {"Price tooltips", "Prices when you hover an item"},
            {"/ah and /orders colors", "Good and bad deals get a color"},
            {"Side panels", "Page summary next to /ah, /orders, /sell"},
            {"Portfolio HUD", "Your profit in the corner"},
            {"Swings HUD", "Active swing signals, left side"},
            {"x64 prices", "Stack items priced per 64 instead of each"},
            {"Watchlist", "Starred items, top middle"},
            {"Market HUD", "Live list matching the Market tab, bottom left"},
            {"Tooltip charts", "24h /orders and /ah graphs when you hover an item"}};

    private int toggleTop;
    private int accountTop;
    private int rowH;

    public SettingsScreen() {
        super("Settings");
    }

    @Override
    protected Page page() {
        return Page.SETTINGS;
    }

    @Override
    protected void build() {
        rowH = Math.max(14, Math.min(24, (cb - ct - 112) / TOGGLES.length));
        toggleTop = ct + 20;
        BooleanSupplier[] get = {
                () -> Settings.get().tooltips,
                () -> Settings.get().highlights,
                () -> Settings.get().panels,
                () -> Settings.get().hud,
                () -> Settings.get().swingHud,
                () -> Settings.get().perStack,
                () -> Settings.get().watchHud,
                () -> Settings.get().marketHud,
                () -> Settings.get().miniCharts};
        @SuppressWarnings("unchecked")
        Consumer<Boolean>[] set = new Consumer[]{
                (Consumer<Boolean>) v -> Settings.get().tooltips = v,
                (Consumer<Boolean>) v -> Settings.get().highlights = v,
                (Consumer<Boolean>) v -> Settings.get().panels = v,
                (Consumer<Boolean>) v -> Settings.get().hud = v,
                (Consumer<Boolean>) v -> Settings.get().swingHud = v,
                (Consumer<Boolean>) v -> Settings.get().perStack = v,
                (Consumer<Boolean>) v -> Settings.get().watchHud = v,
                (Consumer<Boolean>) v -> Settings.get().marketHud = v,
                (Consumer<Boolean>) v -> Settings.get().miniCharts = v};
        for (int i = 0; i < TOGGLES.length; i++) {
            int index = i;
            int y = toggleTop + i * rowH;
            addRenderableWidget(Button.builder(state(get[i].getAsBoolean()), b -> {
                boolean next = !get[index].getAsBoolean();
                set[index].accept(next);
                Settings.save();
                b.setMessage(state(next));
            }).bounds(cr - 50, y, 50, Math.min(18, rowH - 2)).build());
        }
        accountTop = toggleTop + TOGGLES.length * rowH + 12;
        int fw = Math.min(150, (cr - cl - 8) / 2);
        EditBox name = field(cl, accountTop + 24, fw, "Website username", 24);
        name.setValue(Settings.get().portfolio);
        name.setResponder(t -> {
            Settings.get().portfolio = t.trim();
            Settings.save();
        });
        EditBox key = field(cl + fw + 8, accountTop + 24, fw, "Portfolio key", 200);
        mask(key);
        key.setValue(Settings.get().portfolioKey);
        key.setResponder(t -> {
            Settings.get().portfolioKey = t;
            Settings.save();
        });
        button("Refresh prices", cl, accountTop + 52, 100, b -> MarketData.refreshNow());
        button("Move HUD", cl + 108, accountTop + 52, 90, b -> minecraft.gui.setScreen(new HudEditScreen(this)));
    }

    private static Component state(boolean on) {
        return Component.literal(on ? "On" : "Off").withColor(on ? Ui.GREEN : Ui.RED);
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor g, int mx, int my, float delta) {
        Ui.text(g, font, "Settings", cl, ct + 4, Ui.TEXT);
        for (int i = 0; i < TOGGLES.length; i++) {
            int y = toggleTop + i * rowH;
            if (i % 2 == 0) {
                g.fill(cl, y - 2, cr, y + rowH - 2, Ui.STRIPE);
            }
            Ui.text(g, font, TOGGLES[i][0], cl + 6, y + (rowH < 24 ? Math.max(1, (rowH - 10) / 2) : 0), Ui.TEXT);
            if (rowH >= 24) {
                Ui.text(g, font, Ui.clip(font, TOGGLES[i][1], cr - 60 - (cl + 6)), cl + 6, y + 10, Ui.MUTED);
            } else {
                Ui.text(g, font, Ui.clip(font, TOGGLES[i][1], cr - 60 - (cl + 12 + font.width(TOGGLES[i][0]))), cl + 12 + font.width(TOGGLES[i][0]), y + Math.max(1, (rowH - 10) / 2), Ui.MUTED);
            }
        }
        g.fill(cl, accountTop - 6, cr, accountTop - 5, Ui.BORDER);
        Ui.text(g, font, "Website portfolio", cl, accountTop, Ui.GOLD);
        int fw = Math.min(150, (cr - cl - 8) / 2);
        Ui.text(g, font, "Username", cl, accountTop + 13, Ui.SOFT);
        Ui.text(g, font, "Key (to add or remove)", cl + fw + 8, accountTop + 13, Ui.SOFT);
        if (accountTop + 84 < cb - 10) {
            Ui.text(g, font, Ui.clip(font, "Press M in game to open this menu. Keys can be changed in Controls.", cr - cl), cl, cb - 10, Ui.MUTED);
        }
    }
}
