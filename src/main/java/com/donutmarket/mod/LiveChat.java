package com.donutmarket.mod;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class LiveChat {
    private static final String BASE = "https://live.donutmarket.us";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final int[] COLORS = {0xFF7FB2FF, 0xFF4CE5B1, 0xFFF2C14E, 0xFFFF9F6B, 0xFFC39BFF, 0xFF6BE0FF, 0xFFFF7EB6, 0xFFB5E36B};
    static final int WIDTH = 210;

    record Msg(long id, long time, String name, String text, boolean game, boolean admin) {
    }

    private static final List<Msg> MSGS = new ArrayList<>();
    private static volatile boolean connected;
    private static volatile boolean running;
    private static volatile long lastNew;
    private static volatile long lastData;
    private static volatile java.util.stream.Stream<String> live;
    private static volatile boolean watching;
    private static String status = "";

    private LiveChat() {
    }

    static synchronized List<Msg> recent(int n) {
        return new ArrayList<>(MSGS.subList(Math.max(0, MSGS.size() - n), MSGS.size()));
    }

    static boolean connected() {
        return connected;
    }

    static String status() {
        return status;
    }

    static void status(String s) {
        status = s;
    }

    static void tick() {
        boolean want = Settings.get().chatShown || Minecraft.getInstance().gui.screen() instanceof ChatScreen;
        if (want && !running) {
            start();
        }
    }

    private static synchronized void add(JsonObject o) {
        long id = o.get("i").getAsLong();
        for (Msg m : MSGS) {
            if (m.id() == id) {
                return;
            }
        }
        MSGS.add(new Msg(id, o.get("t").getAsLong(), o.get("n").getAsString(), o.get("m").getAsString(),
                o.has("g") && o.get("g").getAsBoolean(), o.has("a") && o.get("a").getAsBoolean()));
        while (MSGS.size() > 60) {
            MSGS.remove(0);
        }
        lastNew = System.currentTimeMillis();
    }

    private static synchronized void remove(long id) {
        MSGS.removeIf(m -> m.id() == id);
    }

    private static synchronized void reset(JsonArray list) {
        MSGS.clear();
        for (JsonElement el : list) {
            try {
                add(el.getAsJsonObject());
            } catch (Exception ignored) {
            }
        }
    }

    private static void watch() {
        if (watching) {
            return;
        }
        watching = true;
        Thread w = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(5_000L);
                } catch (InterruptedException e) {
                    break;
                }
                java.util.stream.Stream<String> s = live;
                if (s != null && System.currentTimeMillis() - lastData > 40_000L) {
                    live = null;
                    try {
                        s.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }, "donut-chat-watch");
        w.setDaemon(true);
        w.start();
    }

    private static void start() {
        running = true;
        watch();
        Thread t = new Thread(() -> {
            int fails = 0;
            while (Settings.get().chatShown || Minecraft.getInstance().gui.screen() instanceof ChatScreen) {
                try {
                    HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + "/chat")).header("Accept", "text/event-stream").GET().build();
                    HttpResponse<java.util.stream.Stream<String>> response = HTTP.send(request, HttpResponse.BodyHandlers.ofLines());
                    if (response.statusCode() == 200) {
                        fails = 0;
                        connected = true;
                        lastData = System.currentTimeMillis();
                        live = response.body();
                        java.util.Iterator<String> it = response.body().iterator();
                        while (it.hasNext()) {
                            String line = it.next();
                            lastData = System.currentTimeMillis();
                            if (!Settings.get().chatShown && !(Minecraft.getInstance().gui.screen() instanceof ChatScreen)) {
                                break;
                            }
                            if (!line.startsWith("data: ")) {
                                continue;
                            }
                            try {
                                JsonObject o = JsonParser.parseString(line.substring(6)).getAsJsonObject();
                                String type = o.get("type").getAsString();
                                if (type.equals("hello")) {
                                    reset(o.getAsJsonArray("msgs"));
                                } else if (type.equals("msg")) {
                                    add(o);
                                } else if (type.equals("del")) {
                                    remove(o.get("i").getAsLong());
                                }
                            } catch (Exception ignored) {
                            }
                        }
                        live = null;
                        response.body().close();
                    }
                } catch (Exception ignored) {
                }
                live = null;
                connected = false;
                fails++;
                try {
                    Thread.sleep(Math.min(30_000L, 1_500L * fails));
                } catch (InterruptedException e) {
                    break;
                }
            }
            running = false;
        }, "donut-chat");
        t.setDaemon(true);
        t.start();
    }

    static boolean signedIn() {
        return Settings.get().portfolio.trim().length() >= 3 && Settings.get().portfolioKey.length() >= 4;
    }

    static CompletableFuture<String> send(String text) {
        if (!signedIn()) {
            return CompletableFuture.completedFuture("Set your portfolio name and key in Settings first");
        }
        JsonObject body = new JsonObject();
        body.addProperty("u", "mod-" + Settings.get().clientId);
        body.addProperty("n", Settings.get().portfolio.trim());
        body.addProperty("p", Settings.get().portfolioKey);
        body.addProperty("m", text);
        body.addProperty("g", true);
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + "/chat/send"))
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(r -> {
            JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
            boolean ok = o.has("ok") && o.get("ok").getAsBoolean();
            if (ok && o.has("msg") && o.get("msg").isJsonObject()) {
                try {
                    add(o.getAsJsonObject("msg"));
                } catch (Exception ignored) {
                }
            }
            return ok ? "" : o.has("error") ? o.get("error").getAsString() : "Could not send";
        }).exceptionally(e -> "Chat is offline right now");
    }

    static int color(String name) {
        int h = 0;
        for (char c : name.toLowerCase(java.util.Locale.ROOT).toCharArray()) {
            h = h * 31 + c;
        }
        return COLORS[Math.abs(h % COLORS.length)];
    }

    static int bottomFor(int screenW, int screenH) {
        boolean overHotbar = screenW - WIDTH - 4 < screenW / 2 + 100;
        return screenH - (overHotbar ? 44 : 4);
    }

    static int draw(GuiGraphicsExtractor g, Font font, int right, int bottom, int maxLines, boolean typing) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        for (Msg m : recent(20)) {
            net.minecraft.network.chat.MutableComponent line = Component.empty();
            if (m.admin()) {
                line.append(Component.literal("[Admin] ").withColor(0xFFF2C14E));
            }
            line.append(Component.literal(m.name()).withColor(color(m.name()))).append(Component.literal(" " + m.text()).withColor(0xFFE2E4E7));
            List<FormattedCharSequence> split = font.split(line, WIDTH - 10);
            starts.add(lines.size());
            lines.addAll(split);
        }
        if (lines.size() > maxLines) {
            lines = lines.subList(lines.size() - maxLines, lines.size());
        }
        String yKey = DonutMarketMod.chatKeyName();
        String jKey = DonutMarketMod.chatTypeKeyName();
        boolean needLogin = !signedIn();
        int extra = needLogin ? 22 : 0;
        int header = 12;
        int height = header + extra + Math.max(1, lines.size()) * 10 + 4;
        int left = right - WIDTH;
        int top = bottom - height;
        g.fill(left, top, right, bottom, typing ? 0xD0111213 : 0x90151516);
        g.fill(left, top, right, top + header, 0x60000000);
        String title = connected ? "Live Chat" : "Live Chat · connecting...";
        g.text(font, title, left + 5, top + 2, 0xFFF2C14E, false);
        if (!typing) {
            String hint = yKey + " close · " + jKey + " type";
            g.text(font, hint, right - 5 - font.width(hint), top + 2, 0xFF878C94, false);
        }
        int y = top + header + 2;
        if (needLogin) {
            g.text(font, "Login to your portfolio", left + 5, y, 0xFFF2C14E, false);
            y += 11;
            g.text(font, "Press M, then Investments. " + yKey + " closes chat.", left + 5, y, 0xFF878C94, false);
            y += 11;
        }
        if (lines.isEmpty() && !needLogin) {
            g.text(font, "No messages yet. " + jKey + " to type, " + yKey + " to close", left + 5, y, 0xFF878C94, false);
        }
        for (FormattedCharSequence line : lines) {
            g.text(font, line, left + 5, y, 0xFFE2E4E7, false);
            y += 10;
        }
        return top;
    }

    public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!Settings.get().chatShown || mc.gui.screen() != null || mc.player == null) {
            return;
        }
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        draw(g, mc.font, w - 4, bottomFor(w, h), 8, false);
    }
}
