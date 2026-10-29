package com.donutmarket.mod;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ChatScreen extends Screen {
    private EditBox box;
    private boolean sending;

    public ChatScreen() {
        super(Component.literal("Live Chat"));
    }

    @Override
    protected void init() {
        int right = width - 4;
        int bottom = height - 4;
        box = new EditBox(font, right - LiveChat.WIDTH, bottom - 16, LiveChat.WIDTH, 16, Component.literal("Message"));
        box.setMaxLength(200);
        box.setHint(Component.literal("Say something, Enter to send").withColor(0xFF878C94));
        addRenderableWidget(box);
        setInitialFocus(box);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if ((event.key() == 257 || event.key() == 335) && !sending) {
            String text = box.getValue().trim();
            if (!text.isEmpty()) {
                sending = true;
                LiveChat.status("Sending...");
                LiveChat.send(text).thenAccept(error -> minecraft.execute(() -> {
                    sending = false;
                    LiveChat.status(error);
                    if (error.isEmpty() && box != null) {
                        box.setValue("");
                    }
                }));
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        int bottom = height - 24;
        String status = LiveChat.status();
        if (!status.isEmpty()) {
            g.text(font, status, width - 4 - LiveChat.WIDTH, bottom - 9, status.equals("Sending...") ? 0xFF878C94 : 0xFFFF6B81, false);
            bottom -= 12;
        }
        LiveChat.draw(g, font, width - 4, bottom, 14, true);
        String who = LiveChat.signedIn() ? "as " + Settings.get().portfolio.trim() : "Login to your portfolio in Investments (M). " + DonutMarketMod.chatKeyName() + " closes chat";
        g.text(font, who, width - 4 - font.width(who), height - 30, LiveChat.signedIn() ? 0xFF878C94 : 0xFFF2C14E, false);
    }

    @Override
    public void removed() {
        LiveChat.status("");
        super.removed();
    }
}
