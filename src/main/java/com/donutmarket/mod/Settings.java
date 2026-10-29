package com.donutmarket.mod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class Settings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Values values = new Values();

    private Settings() {
    }

    public static Values get() {
        return values;
    }

    public static void load() {
        Path file = file();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                Values read = GSON.fromJson(reader, Values.class);
                if (read != null) {
                    values = read;
                }
            } catch (Exception e) {
                values = new Values();
            }
        }
        if (values.portfolio == null) {
            values.portfolio = "";
        }
        if (values.portfolioKey == null) {
            values.portfolioKey = "";
        }
        if (values.swingSignalsOff == null) {
            values.swingSignalsOff = new java.util.ArrayList<>();
        }
        if (values.swingHidden == null) {
            values.swingHidden = new java.util.HashSet<>();
        }
        if (values.myRatings == null) {
            values.myRatings = new java.util.HashMap<>();
        }
        if (values.filters == null) {
            values.filters = new java.util.HashMap<>();
        }
        if (values.hudPos == null) {
            values.hudPos = new java.util.HashMap<>();
        }
        if (values.favorites == null) {
            values.favorites = new java.util.ArrayList<>();
        }
        if (values.alerts == null) {
            values.alerts = new ArrayList<>();
        }
        if (values.clientId == null || !values.clientId.matches("[a-z0-9-]{16,40}")) {
            values.clientId = UUID.randomUUID().toString();
        }
        if (values.settingsRev < 151) {
            values.chatShown = true;
            values.settingsRev = 151;
        }
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(file().getParent());
            try (Writer writer = Files.newBufferedWriter(file())) {
                GSON.toJson(values, writer);
            }
        } catch (Exception ignored) {
        }
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("donutmarket.json");
    }

    public static final class Values {
        public boolean tooltips = true;
        public boolean highlights = true;
        public boolean panels = true;
        public boolean hud = true;
        public boolean chatShown = true;
        public int settingsRev = 0;
        public java.util.Map<String, double[]> filters = new java.util.HashMap<>();
        public boolean perStack = true;
        public java.util.List<String> swingSignalsOff = new java.util.ArrayList<>();
        public java.util.Set<String> swingHidden = new java.util.HashSet<>();
        public java.util.Map<String, Integer> myRatings = new java.util.HashMap<>();
        public int chartRange = 2;
        public int investmentRange = 3;
        public String portfolio = "";
        public String portfolioKey = "";
        public String clientId = "";
        public List<Alert> alerts = new ArrayList<>();
        public boolean lineOrders = true;
        public boolean lineMedian = false;
        public boolean lineLow = true;
        public boolean rangeOrders = false;
        public boolean rangeMedian = false;
        public boolean rangeLow = false;
        public boolean bulkNow = false;
        public boolean bulkPast = false;
        public boolean candles = false;
        public boolean swingHud = true;
        public boolean watchHud = true;
        public boolean marketHud = true;
        public boolean miniCharts = true;
        public java.util.List<String> favorites = new java.util.ArrayList<>();
        public java.util.Map<String, double[]> hudPos = new java.util.HashMap<>();
    }
}
