package com.donutmarket.mod;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MarketData {
    private static final String API = "https://donut-market-upload.donutmarket.workers.dev";
    private static final long MARKET_EVERY = 60_000L;
    private static final long PORTFOLIO_EVERY = 10_000L;
    private static final long LIVE_EVERY = 5_000L;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    private static volatile Map<String, MarketItem> items = Collections.emptyMap();
    private static volatile long marketLoadedAt;
    private static volatile List<Position> positions = Collections.emptyList();
    private static volatile String portfolioName = "";
    private static volatile String portfolioError = "";
    private static volatile long portfolioLoadedAt;
    private static volatile int portfolioFails;

    private static final AtomicBoolean marketBusy = new AtomicBoolean();
    private static final AtomicBoolean portfolioBusy = new AtomicBoolean();
    private static final AtomicBoolean liveBusy = new AtomicBoolean();
    private static long nextLive;
    private static volatile long lastId;
    private static volatile long liveVersion;
    private static final java.util.Deque<Row> recent = new java.util.concurrent.ConcurrentLinkedDeque<>();

    public record Row(String item, long time, int basis, double orders, double ah, double median) {
    }
    private static long nextMarket;
    private static long nextPortfolio;
    private static String lastPortfolio = "";

    private MarketData() {
    }

    public static void tick() {
        long now = System.currentTimeMillis();
        if (now >= nextMarket && marketBusy.compareAndSet(false, true)) {
            nextMarket = now + MARKET_EVERY;
            fetchMarket();
        }
        if (marketLoadedAt > 0) {
            startStream();
        }
        if (now >= nextLive && marketLoadedAt > 0 && liveBusy.compareAndSet(false, true)) {
            nextLive = now + (liveStream ? 60_000L : liveEvery());
            fetchLive();
        }
        String wanted = Settings.get().portfolio.trim();
        if (!wanted.equalsIgnoreCase(lastPortfolio)) {
            lastPortfolio = wanted;
            positions = Collections.emptyList();
            portfolioName = "";
            portfolioError = "";
            portfolioLoadedAt = 0;
            nextPortfolio = 0;
        }
        if (wanted.length() >= 2 && now >= nextPortfolio && portfolioBusy.compareAndSet(false, true)) {
            nextPortfolio = now + (now < fastUntil ? 8_000L : PORTFOLIO_EVERY);
            boolean fresh = freshNext;
            freshNext = false;
            fetchPortfolio(wanted, fresh);
        }
    }

    public record History(int basis, long[] times, double[] orders, double[] ah, double[] median, double[] ordersWeight, double[] ahWeight) {
    }

    private static double[] series(JsonObject o, String field, int n) {
        double[] v = new double[n];
        JsonArray a = o.has(field) && o.get(field).isJsonArray() ? o.getAsJsonArray(field) : null;
        for (int i = 0; a != null && i < n && i < a.size(); i++) {
            v[i] = a.get(i).isJsonNull() ? 0 : a.get(i).getAsDouble();
        }
        return v;
    }

    public static CompletableFuture<History> history(String key, int hours) {
        String url = API + "/mod/history?item=" + URLEncoder.encode(key, StandardCharsets.UTF_8) + "&h=" + hours;
        return get(url, null).thenApply(body -> {
            if (body == null) {
                return null;
            }
            JsonObject o = JsonParser.parseString(body).getAsJsonObject();
            JsonArray t = o.getAsJsonArray("t");
            int n = t == null ? 0 : t.size();
            long[] times = new long[n];
            for (int i = 0; i < n; i++) {
                times[i] = t.get(i).getAsLong() * 1000L;
            }
            int basis = o.has("basis") && !o.get("basis").isJsonNull() ? Math.max(1, o.get("basis").getAsInt()) : 1;
            return new History(basis, times, series(o, "o", n), series(o, "a", n), series(o, "m", n), series(o, "wo", n), series(o, "wa", n));
        }).exceptionally(e -> null);
    }

    public record Points(long[] times, double[] prices, double[] filled) {
    }

    private record CachedPoints(long at, Points points) {
    }

    private static final Map<String, CachedPoints> POINTS = new java.util.concurrent.ConcurrentHashMap<>();

    public static CompletableFuture<Points> points(String item, double qty, long day) {
        String q = qty == Math.rint(qty) ? String.valueOf((long) qty) : String.valueOf(qty);
        String cacheKey = item + "|" + q + "|" + day;
        long now = System.currentTimeMillis();
        CachedPoints hit = POINTS.get(cacheKey);
        boolean finished = day + 86_400_000L <= now - 300_000L;
        if (hit != null && (finished || now - hit.at() < 15_000L)) {
            return CompletableFuture.completedFuture(hit.points());
        }
        String url = API + "/points?item=" + URLEncoder.encode(item, StandardCharsets.UTF_8) + "&qty=" + q + "&day=" + day;
        return get(url, null).thenApply(body -> {
            if (body == null) {
                return hit == null ? null : hit.points();
            }
            JsonObject o = JsonParser.parseString(body).getAsJsonObject();
            JsonArray t = o.getAsJsonArray("t");
            JsonArray p = o.getAsJsonArray("p");
            JsonArray f = o.getAsJsonArray("q");
            int n = t == null || p == null || f == null ? 0 : Math.min(t.size(), Math.min(p.size(), f.size()));
            long[] times = new long[n];
            double[] prices = new double[n];
            double[] filled = new double[n];
            for (int i = 0; i < n; i++) {
                times[i] = t.get(i).getAsLong();
                prices[i] = p.get(i).isJsonNull() ? 0 : p.get(i).getAsDouble();
                filled[i] = f.get(i).isJsonNull() ? 0 : f.get(i).getAsDouble();
            }
            Points out = new Points(times, prices, filled);
            POINTS.put(cacheKey, new CachedPoints(System.currentTimeMillis(), out));
            return out;
        }).exceptionally(e -> hit == null ? null : hit.points());
    }

    public static long marketVersion() {
        return marketLoadedAt;
    }

    private static volatile long fastUntil;
    private static volatile boolean freshNext;

    public static void watchPortfolio() {
        long now = System.currentTimeMillis();
        if (fastUntil < now) {
            freshNext = true;
            nextPortfolio = 0;
        }
        fastUntil = now + 3_000L;
    }

    public static void refreshPortfolio() {
        freshNext = true;
        nextPortfolio = 0;
    }

    public static CompletableFuture<String> savePortfolio(JsonObject change) {
        Settings.Values s = Settings.get();
        change.addProperty("u", s.portfolio.trim());
        change.addProperty("key", s.portfolioKey);
        HttpRequest request = HttpRequest.newBuilder(URI.create(API + "/mod/portfolio/save"))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "DonutMarketMod/1.4")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(change.toString()))
                .build();
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            try {
                JsonObject out = JsonParser.parseString(response.body()).getAsJsonObject();
                if (out.has("ok") && out.get("ok").getAsBoolean()) {
                    nextPortfolio = 0;
                    return "";
                }
                return out.has("error") ? out.get("error").getAsString() : "Could not save";
            } catch (Exception e) {
                return "Could not save";
            }
        }).exceptionally(e -> "Couldn't reach Donut Market");
    }

    public static void refreshNow() {
        nextMarket = 0;
        nextPortfolio = 0;
    }

    public static MarketItem item(String key) {
        return key == null ? null : items.get(key);
    }

    public static Collection<MarketItem> all() {
        return items.values();
    }

    public static long marketLoadedAt() {
        return marketLoadedAt;
    }

    public static long newestScanAt() {
        long newest = 0;
        for (MarketItem it : items.values()) {
            newest = Math.max(newest, it.scannedAt());
        }
        return newest;
    }

    public static List<Position> positions() {
        return positions;
    }

    public static String portfolioName() {
        return portfolioName;
    }

    public static String portfolioError() {
        return portfolioError;
    }

    public static long portfolioLoadedAt() {
        return portfolioLoadedAt;
    }

    private static long liveEvery() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null || !mc.isWindowActive()) {
            return 30_000L;
        }
        if (mc.gui.screen() instanceof Dashboard || mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen) {
            return LIVE_EVERY;
        }
        Settings.Values s = Settings.get();
        if (mc.gui.screen() == null && (s.swingHud || s.marketHud || (s.watchHud && !s.favorites.isEmpty()))) {
            return LIVE_EVERY;
        }
        return 30_000L;
    }

    public static long liveVersion() {
        return liveVersion;
    }

    public static List<Row> recentFor(String key, long after) {
        List<Row> out = new ArrayList<>();
        for (Row r : recent) {
            if (r.item().equals(key) && r.time() > after) {
                out.add(r);
            }
        }
        return out;
    }

    private static synchronized void applyRows(JsonArray rows) {
        if (rows.isEmpty()) {
            return;
        }
        Map<String, MarketItem> next = new HashMap<>(items);
        java.util.Set<String> touched = new java.util.HashSet<>();
        long seen = lastId;
        for (JsonElement el : rows) {
            JsonObject o = el.getAsJsonObject();
            long id = o.has("id") && !o.get("id").isJsonNull() ? o.get("id").getAsLong() : 0;
            if (id > 0 && id <= seen) {
                continue;
            }
            if (id > 0) {
                lastId = Math.max(lastId, id);
            }
            String key = text(o, "item");
            MarketItem old = next.get(key);
            long time;
            try {
                time = OffsetDateTime.parse(text(o, "created_at")).toInstant().toEpochMilli();
            } catch (Exception e) {
                continue;
            }
            int basis = Math.max(1, (int) num(o, "market_basis"));
            Row row = new Row(key, time, basis, num(o, "orders_top3_average"), num(o, "ah_low"), num(o, "ah_median"));
            recent.addLast(row);
            Swings.append(row);
            touched.add(key);
            if (old == null || time <= old.scannedAt()) {
                continue;
            }
            next.put(key, new MarketItem(key, basis, row.orders(), row.ah(), row.median(), old.usualOrders(), old.usualAh(),
                    old.volume24(), old.orders7d(), old.spark(), old.sparkAh(), time));
        }
        while (recent.size() > 3000) {
            recent.pollFirst();
        }
        items = Collections.unmodifiableMap(next);
        marketLoadedAt = System.currentTimeMillis();
        liveVersion++;
        for (Position p : positions) {
            if (touched.contains(p.item())) {
                refreshPortfolio();
                break;
            }
        }
    }

    private static final String LIVE = "https://live.donutmarket.us/live";
    private static volatile boolean liveStream;
    private static volatile boolean streamStarted;

    private static void startStream() {
        if (streamStarted) {
            return;
        }
        streamStarted = true;
        Thread t = new Thread(() -> {
            int fails = 0;
            while (true) {
                try {
                    HttpRequest request = HttpRequest.newBuilder(URI.create(LIVE))
                            .header("Accept", "text/event-stream")
                            .GET()
                            .build();
                    HttpResponse<java.util.stream.Stream<String>> response = HTTP.send(request, HttpResponse.BodyHandlers.ofLines());
                    if (response.statusCode() == 200) {
                        fails = 0;
                        liveStream = true;
                        java.util.Iterator<String> it = response.body().iterator();
                        while (it.hasNext()) {
                            String line = it.next();
                            if (line.startsWith("data: ")) {
                                try {
                                    applyRows(JsonParser.parseString(line.substring(6)).getAsJsonArray());
                                } catch (Exception ignored) {
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
                liveStream = false;
                fails++;
                try {
                    Thread.sleep(fails > 5 ? 300_000L : 5_000L);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "donut-live");
        t.setDaemon(true);
        t.start();
    }

    private static void fetchLive() {
        long newest = 0;
        for (MarketItem it : items.values()) {
            newest = Math.max(newest, it.scannedAt());
        }
        if (newest == 0) {
            liveBusy.set(false);
            return;
        }
        long from = Math.min(newest - 60_000L, System.currentTimeMillis() - 150_000L) / 30_000L * 30_000L;
        String filter = "created_at=gt." + URLEncoder.encode(java.time.Instant.ofEpochMilli(from).toString(), StandardCharsets.UTF_8);
        String url = API + "/db/market_history?select=id,item,market_basis,orders_top3_average,ah_low,ah_median,created_at&" + filter + "&order=id.asc&limit=1000";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) -> {
            try {
                if (error != null || response.statusCode() != 200) {
                    nextLive = System.currentTimeMillis() + 15_000L;
                    return;
                }
                applyRows(JsonParser.parseString(response.body()).getAsJsonArray());
            } catch (Exception ignored) {
            } finally {
                liveBusy.set(false);
            }
        });
    }

    private static Map<String, MarketItem> merge(Map<String, MarketItem> fresh) {
        Map<String, MarketItem> out = new HashMap<>(fresh);
        for (MarketItem cur : items.values()) {
            MarketItem f = out.get(cur.key());
            if (f != null && cur.scannedAt() > f.scannedAt()) {
                out.put(cur.key(), new MarketItem(cur.key(), cur.basis(), cur.orders(), cur.ahLow(), cur.ahMedian(), f.usualOrders(), f.usualAh(),
                        f.volume24(), f.orders7d(), f.spark(), f.sparkAh(), cur.scannedAt()));
            }
        }
        return Collections.unmodifiableMap(out);
    }

    private static void fetchMarket() {
        get(API + "/mod/market", Settings.get().clientId).whenComplete((body, error) -> {
            try {
                if (error == null && body != null) {
                    items = merge(parseMarket(body));
                    marketLoadedAt = System.currentTimeMillis();
                } else {
                    nextMarket = System.currentTimeMillis() + 15_000L;
                }
            } catch (Exception e) {
                nextMarket = System.currentTimeMillis() + 15_000L;
            } finally {
                marketBusy.set(false);
            }
        });
    }

    private static void fetchPortfolio(String name, boolean fresh) {
        String url = API + "/mod/portfolio?u=" + URLEncoder.encode(name, StandardCharsets.UTF_8) + (fresh ? "&fresh=1" : "");
        get(url, null).whenComplete((body, error) -> {
            try {
                if (!name.equalsIgnoreCase(Settings.get().portfolio.trim())) {
                    return;
                }
                if (error != null || body == null) {
                    portfolioFails++;
                    if (portfolioFails < 4 && portfolioLoadedAt > 0) {
                        nextPortfolio = System.currentTimeMillis() + 5_000L;
                        return;
                    }
                    portfolioError = "Couldn't reach Donut Market";
                    nextPortfolio = System.currentTimeMillis() + 10_000L;
                    return;
                }
                portfolioFails = 0;
                JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                if (!root.has("ok") || !root.get("ok").getAsBoolean()) {
                    portfolioError = root.has("error") ? root.get("error").getAsString() : "Portfolio not found";
                    positions = Collections.emptyList();
                    return;
                }
                if (root.has("exists") && !root.get("exists").getAsBoolean()) {
                    portfolioError = "No portfolio called " + name;
                    positions = Collections.emptyList();
                    return;
                }
                positions = parsePositions(root);
                portfolioName = root.has("username") ? root.get("username").getAsString() : name;
                portfolioError = "";
                portfolioLoadedAt = System.currentTimeMillis();
            } catch (Exception e) {
                portfolioError = "Couldn't read that portfolio";
            } finally {
                portfolioBusy.set(false);
            }
        });
    }

    private static List<Position> parsePositions(JsonObject root) {
        List<Position> list = new ArrayList<>();
        if (!root.has("investments") || !root.get("investments").isJsonArray()) {
            return Collections.emptyList();
        }
        for (JsonElement el : root.getAsJsonArray("investments")) {
            JsonObject o = el.getAsJsonObject();
            list.add(new Position(
                    text(o, "id"),
                    text(o, "item"),
                    num(o, "entry"),
                    num(o, "qty"),
                    text(o, "mode"),
                    (long) num(o, "createdAt"),
                    num(o, "price"),
                    num(o, "filled"),
                    num(o, "profit"),
                    o.has("priced") && o.get("priced").getAsBoolean()));
        }
        return Collections.unmodifiableList(list);
    }

    public record Other(String name, List<Position> positions, String error) {
    }

    public static CompletableFuture<Other> portfolioOf(String name, boolean fresh) {
        String url = API + "/mod/portfolio?u=" + URLEncoder.encode(name, StandardCharsets.UTF_8) + (fresh ? "&fresh=1" : "");
        return get(url, null).thenApply(body -> {
            if (body == null) {
                return new Other(name, Collections.emptyList(), "Couldn't reach Donut Market");
            }
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (!root.has("ok") || !root.get("ok").getAsBoolean() || (root.has("exists") && !root.get("exists").getAsBoolean())) {
                return new Other(name, Collections.emptyList(), "No portfolio called " + name);
            }
            return new Other(root.has("username") ? root.get("username").getAsString() : name, parsePositions(root), "");
        }).exceptionally(e -> new Other(name, Collections.emptyList(), "Couldn't read that portfolio"));
    }

    public static CompletableFuture<JsonElement> api(String pathAndQuery) {
        return get(API + pathAndQuery, null).thenApply(body -> body == null ? null : JsonParser.parseString(body)).exceptionally(e -> null);
    }

    public static CompletableFuture<JsonObject> post(String path, JsonObject payload) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(API + path))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "DonutMarketMod/1.4")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            try {
                return JsonParser.parseString(response.body()).getAsJsonObject();
            } catch (Exception e) {
                return (JsonObject) null;
            }
        }).exceptionally(e -> null);
    }

    private static Map<String, MarketItem> parseMarket(String body) {
        JsonArray array = JsonParser.parseString(body).getAsJsonObject().getAsJsonArray("items");
        Map<String, MarketItem> map = new HashMap<>();
        Map<String, Double> wanted = new HashMap<>();
        for (JsonElement el : array) {
            JsonObject o = el.getAsJsonObject();
            String key = o.get("k").getAsString();
            wanted.put(key, num(o, "dem"));
            long time = 0;
            try {
                time = OffsetDateTime.parse(o.get("t").getAsString()).toInstant().toEpochMilli();
            } catch (Exception ignored) {
            }
            double[] spark = line(o, "sp");
            double[] sparkAh = line(o, "spa");
            map.put(key, new MarketItem(
                    key,
                    Math.max(1, o.get("b").getAsInt()),
                    num(o, "o"),
                    num(o, "a"),
                    num(o, "am"),
                    num(o, "om24"),
                    num(o, "am24"),
                    num(o, "v24"),
                    num(o, "o7"),
                    spark,
                    sparkAh,
                    time));
        }
        demand = wanted;
        return Collections.unmodifiableMap(map);
    }

    private static volatile Map<String, Double> demand = Collections.emptyMap();

    public static double demand(String key) {
        return demand.getOrDefault(key, 0.0);
    }

    private static double[] line(JsonObject o, String field) {
        if (!o.has(field) || !o.get(field).isJsonArray()) {
            return new double[0];
        }
        JsonArray a = o.getAsJsonArray(field);
        double[] v = new double[a.size()];
        for (int i = 0; i < v.length; i++) {
            v[i] = a.get(i).isJsonNull() ? 0 : a.get(i).getAsDouble();
        }
        return v;
    }

    private static String text(JsonObject o, String field) {
        JsonElement el = o.get(field);
        return el == null || el.isJsonNull() ? "" : el.getAsString();
    }

    private static double num(JsonObject o, String field) {
        JsonElement el = o.get(field);
        return el == null || el.isJsonNull() ? 0 : el.getAsDouble();
    }

    private static CompletableFuture<String> get(String url, String clientId) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(12))
                .header("User-Agent", "DonutMarketMod/1.4")
                .GET();
        if (clientId != null && !clientId.isEmpty()) {
            builder.header("X-DM-Client", clientId);
        }
        HttpRequest request = builder.build();
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> response.statusCode() == 200 ? response.body() : null);
    }
}
