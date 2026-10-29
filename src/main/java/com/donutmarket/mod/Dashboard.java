package com.donutmarket.mod;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Set;

public abstract class Dashboard extends BaseScreen {
    public enum Page { MARKET, INVESTMENTS, SWINGS, STATS, CRASH, TRADERS, ALERTS, CALC, SETTINGS }

    private static final Page[] NAV = Page.values();
    private static final String[] NAV_LABELS = {"Market", "Investments", "Advanced swings", "Market stats", "Crash detector", "Active traders", "Alerts", "Calculator", "Settings"};
    private static final String[] NAV_ICONS = {"gold_ingot", "emerald", "recovery_compass", "spyglass", "tnt", "player_head", "bell", "paper", "comparator"};
    private static final int[] NAV_COLORS = {0xFFF2C14E, 0xFF4CE5B1, 0xFF7FB2FF, 0xFFC0C3C7, 0xFFFF6B81, 0xFFF2C14E, 0xFFF2C14E, 0xFF7FB2FF, 0xFF878C94};

    public record Category(String id, String label, String icon, Set<String> keys) {
        public boolean has(String key) {
            return keys.isEmpty() || keys.contains(key);
        }
    }

    public static final List<Category> CATEGORIES = List.of(
            new Category("all", "All", "nether_star", Set.of()),
            new Category("crystal_pvp", "Crystal PvP", "end_crystal", Set.of("end_crystal", "respawn_anchor", "golden_apple", "enchanted_golden_apple", "totem_of_undying", "glowstone", "experience_bottle", "ender_pearl")),
            new Category("armor_gear", "Armor & gear", "netherite_chestplate", Set.of("trident", "diamond_helmet_p4", "diamond_chestplate_p4", "diamond_leggings_bp4", "diamond_boots_bp4", "netherite_helmet_p4", "netherite_chestplate_p4", "netherite_leggings_bp4", "netherite_boots_bp4", "diamond_sword_kb2", "diamond_sword_kb1", "diamond_sword_kb1_sharp5", "netherite_sword_kb2")),
            new Category("skulls", "Skulls", "wither_skeleton_skull", Set.of("skeleton_skull", "wither_skeleton_skull", "piglin_head", "zombie_head", "creeper_head", "dragon_head")),
            new Category("vals", "Vals", "netherite_block", Set.of("diamond_block", "gold_block", "netherite_block", "netherite_ingot", "ancient_debris", "iron_block", "emerald_block")),
            new Category("ores", "Ores", "diamond", Set.of("diamond", "iron_ingot", "gold_ingot", "emerald", "deepslate_diamond_ore", "deepslate_emerald_ore", "deepslate_emerald_ore_x64")),
            new Category("blocks", "Blocks", "blue_ice", Set.of("blue_ice", "crying_obsidian", "glass", "sand", "gravel", "carved_pumpkin", "pumpkin")),
            new Category("colors", "Colors", "orange_concrete", Set.of()),
            new Category("farming", "Farming", "bone_meal", Set.of("bone_block", "bone_meal", "spruce_log", "charcoal", "coal", "moss_block", "blaze_rod", "gunpowder")),
            new Category("redstone", "Redstone", "redstone", Set.of("redstone", "redstone_block", "observer", "dispenser", "dropper", "repeater", "comparator", "hopper", "piston", "sticky_piston", "redstone_torch", "crafter", "redstone_lamp", "slime_block", "honey_block", "armor_stand")),
            new Category("misc", "Misc", "elytra", Set.of("elytra", "gilded_blackstone", "chorus_fruit", "turtle_master", "firework_rocket_fd3", "shulker_box", "tnt_minecart", "furnace", "smoker", "blast_furnace")));

    protected static int category = 0;
    protected static final java.util.Set<Integer> cats = new java.util.LinkedHashSet<>();

    public static boolean catPass(String key) {
        if (cats.isEmpty()) {
            return true;
        }
        for (int i : cats) {
            if (inCategory(CATEGORIES.get(i), key)) {
                return true;
            }
        }
        return false;
    }

    protected int px1;
    protected int py1;
    protected int px2;
    protected int py2;
    protected int side;
    protected boolean compact;
    protected int cl;
    protected int cr;
    protected int ct;
    protected int cb;
    private int catTop;
    private int catRow;
    private int catCols;
    private int navTop;

    protected Dashboard(String title) {
        super(title, null);
    }

    protected abstract Page page();

    protected abstract void build();

    public static boolean inCategory(Category c, String key) {
        if (c.id().equals("colors")) {
            return key.endsWith("_terracotta") || key.equals("terracotta") || key.endsWith("_concrete") || key.endsWith("_concrete_powder") || key.endsWith("_wool");
        }
        if (c.id().equals("misc")) {
            if (c.keys().contains(key)) {
                return true;
            }
            for (Category other : CATEGORIES) {
                if (other != c && !other.id().equals("all") && inCategory(other, key)) {
                    return false;
                }
            }
            return true;
        }
        return c.has(key);
    }

    @Override
    protected final void init() {
        int w = Math.min(640, width - 12);
        int h = Math.min(360, height - 12);
        px1 = (width - w) / 2;
        py1 = (height - h) / 2;
        px2 = px1 + w;
        py2 = py1 + h;
        compact = w < 430;
        side = compact ? 24 : w < 540 ? 92 : 108;
        left = px1;
        right = px2;
        cl = px1 + side + 10;
        cr = px2 - 9;
        ct = py1 + 8;
        cb = py2 - 8;
        navTop = py1 + (compact ? 8 : 36);
        catTop = navTop + NAV.length * 13 + 7;
        int avail = py2 - 6 - catTop;
        if (avail / CATEGORIES.size() >= 11) {
            catCols = 1;
            catRow = Math.min(15, avail / CATEGORIES.size());
        } else {
            catCols = Math.max(1, (side - 8) / 18);
            catRow = 18;
        }
        build();
    }


    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xD80B0B0B);
        g.fill(px1, py1, px2, py2, Ui.OUTLINE);
        Ui.earth(g, px1 + 1, py1 + 1, px2 - 1, py2 - 1);
        g.fill(px1 + 1, py1 + 1, px2 - 1, py2 - 1, Ui.BG);
        drawSidebar(g, mouseX, mouseY);
        drawContent(g, mouseX, mouseY, delta);
        super.extractRenderState(g, mouseX, mouseY, delta);
        drawOverlay(g, mouseX, mouseY);
    }

    protected abstract void drawContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta);

    protected void drawOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY) {
    }

    protected boolean showsCategories() {
        return page() == Page.MARKET || page() == Page.SWINGS || page() == Page.STATS;
    }

    private int[] catCell(int i) {
        if (catCols == 1) {
            return new int[]{px1 + 4, catTop + i * catRow, px1 + side - 4, catTop + (i + 1) * catRow};
        }
        int col = i % catCols;
        int row = i / catCols;
        int x = px1 + 4 + col * 18;
        int y = catTop + row * 18;
        return new int[]{x, y, x + 17, y + 17};
    }

    private void drawSidebar(GuiGraphicsExtractor g, int mx, int my) {
        int x = px1 + 1;
        g.fill(x, py1 + 1, px1 + side, py2 - 1, Ui.SIDE);
        g.fill(px1 + side, py1 + 1, px1 + side + 1, py2 - 1, Ui.BORDER);
        if (!compact) {
            Ui.text(g, font, "Donut", px1 + 9, py1 + 9, Ui.TEXT);
            Ui.text(g, font, "Market", px1 + 9 + font.width("Donut "), py1 + 9, Ui.GOLD);
            long at = MarketData.marketLoadedAt();
            Ui.text(g, font, at == 0 ? "connecting..." : MarketData.all().size() + " items · live", px1 + 9, py1 + 21, at == 0 ? Ui.GOLD : Ui.MUTED);
        }
        for (int i = 0; i < NAV.length; i++) {
            int y = navTop + i * 13;
            boolean on = page() == NAV[i];
            boolean hover = Ui.inside(mx, my, px1 + 4, y - 2, px1 + side - 4, y + 11);
            if (on) {
                g.fill(px1 + 4, y - 2, px1 + side - 4, y + 11, Ui.ACTIVE);
                g.fill(px1 + 4, y - 2, px1 + 6, y + 11, NAV_COLORS[i]);
            } else if (hover) {
                g.fill(px1 + 4, y - 2, px1 + side - 4, y + 11, Ui.HOVER);
            }
            if (compact) {
                Ui.item(g, ItemKeys.stackFor(NAV_ICONS[i]), px1 + 6, y - 1, 0.75F);
            } else {
                String label = NAV_LABELS[i];
                if (NAV[i] == Page.ALERTS && !Settings.get().alerts.isEmpty()) {
                    label += " (" + Settings.get().alerts.size() + ")";
                }
                Ui.text(g, font, Ui.clip(font, label, side - 16), px1 + 10, y, on ? Ui.TEXT : NAV_COLORS[i]);
            }
        }
        if (!showsCategories()) {
            return;
        }
        g.fill(px1 + 4, catTop - 4, px1 + side - 4, catTop - 3, Ui.BORDER);
        g.enableScissor(px1 + 1, catTop - 2, px1 + side, py2 - 2);
        Category hovered = null;
        for (int i = 0; i < CATEGORIES.size(); i++) {
            Category c = CATEGORIES.get(i);
            int[] r = catCell(i);
            boolean on = i == 0 ? cats.isEmpty() : cats.contains(i);
            boolean hover = Ui.inside(mx, my, r[0], r[1], r[2], r[3]);
            if (on) {
                g.fill(r[0], r[1], r[2], r[3], Ui.ACTIVE);
                if (catCols == 1) {
                    g.fill(r[0], r[1], r[0] + 2, r[3], Ui.GOLD);
                } else {
                    g.fill(r[0], r[3] - 1, r[2], r[3], Ui.GOLD);
                }
            } else if (hover) {
                g.fill(r[0], r[1], r[2], r[3], Ui.HOVER);
            }
            if (hover) {
                hovered = c;
            }
            if (catCols == 1) {
                Ui.item(g, ItemKeys.stackFor(c.icon()), r[0] + 5, r[1] + (catRow - 12) / 2, 0.75F);
                if (!compact) {
                    Ui.text(g, font, c.label(), r[0] + 21, r[1] + (catRow - 8) / 2, on ? Ui.TEXT : Ui.SOFT);
                }
            } else {
                Ui.item(g, ItemKeys.stackFor(c.icon()), r[0] + 2, r[1] + 2, 0.8F);
            }
        }
        g.disableScissor();
        if (hovered != null && (catCols > 1 || compact)) {
            g.setComponentTooltipForNextFrame(font, List.of(Component.literal(hovered.label())), mx, my);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        if (mx >= px1 && mx < px1 + side && my >= py1 && my < py2) {
            for (int i = 0; i < NAV.length; i++) {
                int y = navTop + i * 13;
                if (my >= y - 2 && my < y + 11) {
                    open(NAV[i]);
                    return true;
                }
            }
            if (showsCategories()) {
                for (int i = 0; i < CATEGORIES.size(); i++) {
                    int[] r = catCell(i);
                    if (Ui.inside(mx, my, r[0], r[1], r[2], r[3])) {
                        if (i == 0) {
                            cats.clear();
                        } else if (!cats.remove(i)) {
                            cats.add(i);
                        }
                        category = cats.size() == 1 ? cats.iterator().next() : 0;
                        categoryChanged();
                        return true;
                    }
                }
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    protected void open(Page target) {
        if (target == page() && target != Page.TRADERS && target != Page.INVESTMENTS) {
            return;
        }
        switch (target) {
            case MARKET -> minecraft.gui.setScreen(new MarketScreen());
            case INVESTMENTS -> minecraft.gui.setScreen(new InvestmentsScreen());
            case SWINGS -> minecraft.gui.setScreen(new SwingsScreen());
            case STATS -> minecraft.gui.setScreen(new StatsScreen());
            case CRASH -> minecraft.gui.setScreen(new CrashScreen());
            case TRADERS -> minecraft.gui.setScreen(new TradersScreen());
            case ALERTS -> minecraft.gui.setScreen(new AlertsScreen(this));
            case CALC -> minecraft.gui.setScreen(new CalcScreen());
            default -> minecraft.gui.setScreen(new SettingsScreen());
        }
    }

    protected void categoryChanged() {
    }

    protected void statCard(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, String label, String name, String value, int color) {
        Ui.card(g, x1, y1, x2, y2);
        Ui.text(g, font, Ui.clip(font, label, x2 - x1 - 12), x1 + 6, y1 + 5, Ui.MUTED);
        if (name == null) {
            Ui.text(g, font, Ui.clip(font, value, x2 - x1 - 12), x1 + 6, y1 + 16, color);
            return;
        }
        int vw = font.width(value);
        Ui.text(g, font, Ui.clip(font, name, x2 - x1 - 18 - vw), x1 + 6, y1 + 16, Ui.TEXT);
        Ui.right(g, font, value, x2 - 6, y1 + 16, color);
    }

    protected int ranges(GuiGraphicsExtractor g, String[] labels, int selected, int rightX, int y) {
        int x = rightX;
        for (int i = labels.length - 1; i >= 0; i--) {
            x -= font.width(labels[i]) + 8;
            Ui.chip(g, font, labels[i], x, y, i == selected);
            x -= 3;
        }
        return x;
    }

    protected int rangeAt(String[] labels, int rightX, int y, double mx, double my) {
        if (my < y || my >= y + 12) {
            return -1;
        }
        int x = rightX;
        for (int i = labels.length - 1; i >= 0; i--) {
            int w = font.width(labels[i]) + 8;
            x -= w;
            if (mx >= x && mx < x + w) {
                return i;
            }
            x -= 3;
        }
        return -1;
    }

    protected ItemStack icon(String key) {
        return ItemKeys.stackFor(key);
    }

    public static double divisor(MarketItem it) {
        return it == null || Settings.get().perStack ? 1 : Math.max(1, it.basis());
    }

    public static String unitText(MarketItem it) {
        if (it == null || it.basis() <= 1) {
            return "per item";
        }
        return Settings.get().perStack ? "per x" + it.basis() : "per item";
    }

    protected Button stackToggle(int x, int y, Runnable changed) {
        return addRenderableWidget(Button.builder(stackLabel(), b -> {
            Settings.get().perStack = !Settings.get().perStack;
            Settings.save();
            b.setMessage(stackLabel());
            changed.run();
        }).bounds(x, y, 54, 18).build());
    }

    private static Component stackLabel() {
        boolean on = Settings.get().perStack;
        return Component.literal("x64: ").append(Component.literal(on ? "On" : "Off").withColor(on ? Ui.GREEN : Ui.MUTED));
    }

    protected double filterFactor = 1;
    protected EditBox filterLow;
    protected EditBox filterHigh;
    protected boolean filterOpen;
    private int filterX;
    private int filterY;
    private int filterRight;
    private String filterKey = "";

    protected static double[] filterFor(String key) {
        double[] f = Settings.get().filters.get(key);
        return f == null || f.length < 2 ? new double[]{0, 0} : f;
    }

    protected void buildFilter(String key, int x, int y, int rightX, Runnable changed) {
        filterKey = key;
        filterX = x;
        filterY = y;
        filterRight = rightX;
        filterLow = null;
        filterHigh = null;
        if (!filterOpen || key.isEmpty()) {
            return;
        }
        double[] f = filterFor(key);
        int bx = x + font.width("Show ") + 2;
        filterLow = new EditBox(font, bx, y - 2, 52, 12, Component.literal("Min"));
        filterHigh = new EditBox(font, bx + 52 + font.width(" to ") + 4, y - 2, 52, 12, Component.literal("Max"));
        for (EditBox box : new EditBox[]{filterLow, filterHigh}) {
            box.setMaxLength(12);
            box.setTextColor(Ui.TEXT);
        }
        filterLow.setHint(Component.literal("min").withColor(Ui.MUTED));
        filterHigh.setHint(Component.literal("max").withColor(Ui.MUTED));
        filterLow.setValue(f[0] > 0 ? Money.format(f[0] * filterFactor).replace("$", "") : "");
        filterHigh.setValue(f[1] > 0 ? Money.format(f[1] * filterFactor).replace("$", "") : "");
        filterLow.setResponder(t -> saveFilter(changed));
        filterHigh.setResponder(t -> saveFilter(changed));
        addRenderableWidget(filterLow);
        addRenderableWidget(filterHigh);
    }

    private void saveFilter(Runnable changed) {
        double lo = parseAmount(filterLow.getValue()) / filterFactor;
        double hi = parseAmount(filterHigh.getValue()) / filterFactor;
        if (lo <= 0 && hi <= 0) {
            Settings.get().filters.remove(filterKey);
        } else {
            Settings.get().filters.put(filterKey, new double[]{Math.max(0, lo), Math.max(0, hi)});
        }
        Settings.save();
        changed.run();
    }

    protected String filterLabel(String key) {
        double[] f = filterFor(key);
        if (f[0] <= 0 && f[1] <= 0) {
            return "Filter";
        }
        if (f[0] > 0 && f[1] > 0) {
            return "Filter: " + Money.format(f[0] * filterFactor) + "-" + Money.format(f[1] * filterFactor);
        }
        return f[0] > 0 ? "Filter: over " + Money.format(f[0] * filterFactor) : "Filter: under " + Money.format(f[1] * filterFactor);
    }

    protected boolean drawFilter(GuiGraphicsExtractor g, int mx, int my) {
        if (!filterOpen || filterLow == null) {
            return false;
        }
        Ui.text(g, font, "Show", filterX, filterY, Ui.SOFT);
        Ui.card(g, filterLow.getX() - 2, filterY - 4, filterLow.getX() + 52, filterY + 10, Ui.PLOT, Ui.SELECT_BORDER);
        Ui.text(g, font, "to", filterLow.getX() + 56, filterY, Ui.SOFT);
        Ui.card(g, filterHigh.getX() - 2, filterY - 4, filterHigh.getX() + 52, filterY + 10, Ui.PLOT, Ui.SELECT_BORDER);
        int x = filterRight;
        boolean overDone = Ui.inside(mx, my, x - font.width("Done") - 2, filterY - 2, x + 2, filterY + 10);
        Ui.right(g, font, "Done", x, filterY, overDone ? Ui.TEXT : Ui.BLUE);
        x -= font.width("Done") + 8;
        boolean overClear = Ui.inside(mx, my, x - font.width("Clear") - 2, filterY - 2, x + 2, filterY + 10);
        Ui.right(g, font, "Clear", x, filterY, overClear ? Ui.TEXT : Ui.MUTED);
        return true;
    }

    protected int filterClick(double mx, double my, int labelX) {
        if (my < filterY - 2 || my >= filterY + 10) {
            return 0;
        }
        if (!filterOpen) {
            return mx >= labelX - 2 && mx <= filterRight + 2 ? 1 : 0;
        }
        int x = filterRight;
        if (mx >= x - font.width("Done") - 2 && mx <= x + 2) {
            return 2;
        }
        x -= font.width("Done") + 8;
        if (mx >= x - font.width("Clear") - 2 && mx <= x + 2) {
            return 3;
        }
        return 0;
    }

    protected void clearFilter() {
        Settings.get().filters.remove(filterKey);
        Settings.save();
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(null);
    }
}
