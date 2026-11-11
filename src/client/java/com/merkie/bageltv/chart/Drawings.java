package com.merkie.bageltv.chart;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.merkie.bageltv.BagelTV;
import com.merkie.bageltv.Config;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What the player drew on each chart, kept in config/bagel-tradingview-drawings.json under the chart's key
 * (such as {@code item:water_bucket:price} or {@code market:index}). Points are a time and a value, so a drawing
 * stays on its candles however the chart is panned or zoomed.
 */
public final class Drawings {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir()
            .resolve("bagel-tradingview-drawings.json");
    private static Map<String, List<Drawing>> all;

    private Drawings() {}

    public enum Kind { PENCIL, TREND, ARROW, HLINE, RECT }

    public static final class Drawing {
        public Kind kind;
        public int color;
        /** Times in seconds and values, one pair per point: many for a pencil stroke, two for the rest, one for a line across. */
        public double[] t;
        public double[] v;
    }

    /** The drawings for a chart, a list the chart edits in place. */
    public static List<Drawing> of(String key) {
        return load().computeIfAbsent(key, ignored -> new ArrayList<>());
    }

    private static Map<String, List<Drawing>> load() {
        if (all != null) return all;
        all = new HashMap<>();
        if (Files.exists(PATH)) {
            try (var reader = Files.newBufferedReader(PATH)) {
                Map<String, List<Drawing>> loaded = GSON.fromJson(reader,
                        new TypeToken<Map<String, List<Drawing>>>() {}.getType());
                if (loaded != null) {
                    loaded.forEach((key, list) -> {
                        List<Drawing> good = new ArrayList<>();
                        if (list != null) for (Drawing drawing : list) if (valid(drawing)) good.add(drawing);
                        all.put(key, good);
                    });
                }
            } catch (IOException | RuntimeException exception) {
                LoggerFactory.getLogger(BagelTV.ID).warn("Could not read {}; starting without drawings", PATH,
                        exception);
            }
        }
        return all;
    }

    private static boolean valid(Drawing drawing) {
        return drawing != null && drawing.kind != null && drawing.t != null && drawing.v != null
                && drawing.t.length == drawing.v.length && drawing.t.length > 0;
    }

    public static void save() {
        if (all == null) return;
        Map<String, List<Drawing>> kept = new HashMap<>();
        all.forEach((key, list) -> {
            if (!list.isEmpty()) kept.put(key, list);
        });
        Config.write(PATH, GSON.toJson(kept));
    }

    /** Forgets what was read, so the next use reads the file again. For tests. */
    public static void reload() {
        all = null;
    }
}
