package com.donutmarket.mod;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public class DonutMarketMod implements ClientModInitializer {
    public static final String MOD_ID = "donutmarket";

    private static KeyMapping openKey;
    private static KeyMapping alertsKey;
    private static KeyMapping investmentsKey;
    private static KeyMapping chatKey;
    private static KeyMapping chatTypeKey;
    private static boolean keysChecked;

    @Override
    public void onInitializeClient() {
        Settings.load();

        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.donutmarket.open", InputConstants.Type.KEYBOARD, InputConstants.KEY_M, category));
        alertsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.donutmarket.alerts", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), category));
        investmentsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.donutmarket.investments", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), category));
        chatKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.donutmarket.chat", InputConstants.Type.KEYBOARD, InputConstants.KEY_Y, category));
        chatTypeKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.donutmarket.chattype", InputConstants.Type.KEYBOARD, InputConstants.KEY_J, category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!keysChecked) {
                keysChecked = true;
                boolean fixed = fixKey(openKey) | fixKey(alertsKey) | fixKey(investmentsKey) | fixKey(chatKey) | fixKey(chatTypeKey);
                if (fixed) {
                    KeyMapping.resetMapping();
                    client.options.save();
                }
            }
            MarketData.tick();
            Alerts.tick(client);
            while (openKey.consumeClick()) {
                if (client.gui.screen() == null) {
                    client.gui.setScreen(new MarketScreen());
                }
            }
            while (alertsKey.consumeClick()) {
                if (client.gui.screen() == null) {
                    client.gui.setScreen(new AlertsScreen(null));
                }
            }
            while (chatKey.consumeClick()) {
                if (client.gui.screen() == null) {
                    Settings.get().chatShown = !Settings.get().chatShown;
                    Settings.save();
                }
            }
            while (chatTypeKey.consumeClick()) {
                if (client.gui.screen() == null) {
                    client.gui.setScreen(new ChatScreen());
                }
            }
            LiveChat.tick();
            while (investmentsKey.consumeClick()) {
                if (client.gui.screen() == null) {
                    client.gui.setScreen(new InvestmentsScreen());
                }
            }
        });

        ItemTooltipCallback.EVENT.register(PriceTooltip::append);
        ClientTooltipComponentCallback.EVENT.register(data -> data instanceof PriceGraph graph ? new PriceGraph.View(graph) : null);
        ScreenEvents.AFTER_INIT.register(ListingHighlights::attach);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "portfolio"), PortfolioHud::render);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "swings"), MarketHud::swings);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "watchlist"), MarketHud::watchlist);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "market"), MarketHud::market);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "chat"), LiveChat::render);
    }

    static String chatTypeKeyName() {
        return chatTypeKey == null ? "J" : chatTypeKey.getTranslatedKeyMessage().getString();
    }

    static String chatKeyName() {
        return chatKey == null ? "Y" : chatKey.getTranslatedKeyMessage().getString();
    }

    private static boolean fixKey(KeyMapping mapping) {
        InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(mapping);
        if (key.getType() == InputConstants.Type.KEYBOARD && key.getValue() < 0 && key.getValue() != InputConstants.UNKNOWN.getValue()) {
            mapping.setKey(mapping.getDefaultKey());
            return true;
        }
        return false;
    }
}
