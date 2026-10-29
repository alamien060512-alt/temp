package com.donutmarket.mod;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ItemKeys {
    private static final Set<String> SKIP = Set.of("firework_rocket", "potion", "splash_potion", "lingering_potion");

    private static final Map<String, String> GEAR_NAMES = new LinkedHashMap<>();
    private static final Map<String, String> GEAR_ITEMS = new LinkedHashMap<>();

    static {
        gear("diamond_helmet_p4", "diamond_helmet", "Diamond Helmet P4");
        gear("diamond_chestplate_p4", "diamond_chestplate", "Diamond Chestplate P4");
        gear("diamond_leggings_bp4", "diamond_leggings", "Diamond Leggings BP4");
        gear("diamond_boots_bp4", "diamond_boots", "Diamond Boots BP4");
        gear("netherite_helmet_p4", "netherite_helmet", "Netherite Helmet P4");
        gear("netherite_chestplate_p4", "netherite_chestplate", "Netherite Chestplate P4");
        gear("netherite_leggings_bp4", "netherite_leggings", "Netherite Leggings BP4");
        gear("netherite_boots_bp4", "netherite_boots", "Netherite Boots BP4");
        gear("diamond_sword_kb2", "diamond_sword", "Diamond Sword KB2");
        gear("diamond_sword_kb1", "diamond_sword", "Diamond Sword KB1");
        gear("diamond_sword_kb1_sharp5", "diamond_sword", "Diamond Sword KB1 Sharp 5");
        gear("netherite_sword_kb2", "netherite_sword", "Netherite Sword KB2");
        gear("trident", "trident", "Trident Riptide 3 Impaling 5");
        gear("turtle_master", "potion", "Turtle Master");
        gear("firework_rocket_fd3", "firework_rocket", "Firework Rocket FD3");
        gear("tnt_minecart", "tnt_minecart", "Minecart with TNT");
    }

    private ItemKeys() {
    }

    private static void gear(String key, String item, String name) {
        GEAR_ITEMS.put(key, item);
        GEAR_NAMES.put(key, name);
    }

    public static MarketItem lookup(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!"minecraft".equals(id.getNamespace())) {
            return null;
        }
        String path = id.getPath();
        ItemEnchantments enchants = stack.get(DataComponents.ENCHANTMENTS);
        boolean enchanted = enchants != null && !enchants.isEmpty();
        if (enchanted) {
            return MarketData.item(gearKey(path, enchants));
        }
        if (SKIP.contains(path) || path.equals("trident") || path.equals("elytra")) {
            return null;
        }
        return MarketData.item(path);
    }

    private static boolean lasting(ItemEnchantments enchants) {
        return level(enchants, "unbreaking") >= 3 && level(enchants, "mending") >= 1;
    }

    private static String gearKey(String path, ItemEnchantments enchants) {
        if (path.equals("elytra")) {
            return lasting(enchants) ? "elytra" : null;
        }
        if (!path.startsWith("diamond_") && !path.startsWith("netherite_") && !path.endsWith("_sword") && !path.equals("trident")) {
            return null;
        }
        if (path.endsWith("_helmet") || path.endsWith("_chestplate")) {
            boolean ok = level(enchants, "protection") >= 4 && lasting(enchants);
            if (path.equals("netherite_helmet")) {
                ok &= level(enchants, "respiration") >= 3 && level(enchants, "aqua_affinity") >= 1;
            }
            return ok ? path + "_p4" : null;
        }
        if (path.endsWith("_leggings") || path.endsWith("_boots")) {
            return level(enchants, "blast_protection") >= 4 && lasting(enchants) ? path + "_bp4" : null;
        }
        if (path.endsWith("_sword")) {
            int kb = level(enchants, "knockback");
            if (kb >= 2) {
                return path + "_kb2";
            }
            if (kb == 1) {
                return level(enchants, "sharpness") >= 5 ? path + "_kb1_sharp5" : path + "_kb1";
            }
            return null;
        }
        if (path.equals("trident")) {
            return level(enchants, "riptide") >= 3 && level(enchants, "impaling") >= 5 && lasting(enchants) ? "trident" : null;
        }
        return null;
    }

    private static int level(ItemEnchantments enchants, String name) {
        Identifier wanted = Identifier.withDefaultNamespace(name);
        for (Holder<Enchantment> holder : enchants.keySet()) {
            if (holder.is(wanted)) {
                return enchants.getLevel(holder);
            }
        }
        return 0;
    }

    public static ItemStack stackFor(String key) {
        String path = GEAR_ITEMS.getOrDefault(key, key.replace("_x64", ""));
        Identifier id = Identifier.tryParse("minecraft:" + path);
        if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
            try {
                Item item = BuiltInRegistries.ITEM.getValue(id);
                return new ItemStack(item);
            } catch (RuntimeException e) {
                return ItemStack.EMPTY;
            }
        }
        return ItemStack.EMPTY;
    }

    public static String name(String key) {
        String gear = GEAR_NAMES.get(key);
        if (gear != null) {
            return gear;
        }
        ItemStack stack = stackFor(key);
        String base = stack.isEmpty() ? pretty(key.replace("_x64", "")) : stack.getHoverName().getString();
        return key.endsWith("_x64") ? base + " (stack)" : base;
    }

    private static String pretty(String key) {
        StringBuilder out = new StringBuilder();
        for (String part : key.split("_")) {
            if (part.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(part.substring(0, 1).toUpperCase(Locale.ROOT)).append(part.substring(1));
        }
        return out.toString();
    }
}
