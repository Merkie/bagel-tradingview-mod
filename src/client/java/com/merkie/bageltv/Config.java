package com.merkie.bageltv;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Local settings in config/bagel-tradingview.json, written atomically. */
public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("bagel-tradingview.json");

    /** GLFW key code that opens and closes the terminal. */
    public int hotkey = GLFW.GLFW_KEY_B;
    public boolean fullscreen;
    /** Screen pixels per terminal pixel; 0 picks one from the window size. */
    public int scale;
    /** Where the windowed panel was dragged to, as an offset from the middle of the screen. */
    public int offsetX;
    public int offsetY;
    /** The stats window every view shares: 1h, 6h, 24h, 7d or 30d. */
    public String window = "24h";
    /** What each of the dashboard's two chart slots shows. */
    public String marketLeft = "index";
    public String marketRight = "leaders";
    /** Dragged panel sizes in terminal pixels; 0 means the default share of the view. */
    public int itemSide, itemLower, playerSide, playerLower;
    public String itemChart = "candles";
    public boolean itemVwap = true;
    public boolean itemLog;
    public String leadersMetric = "turnover";
    public String leadersMode = "value";

    public static Config load() {
        if (Files.exists(PATH)) {
            try (var reader = Files.newBufferedReader(PATH)) {
                Config loaded = GSON.fromJson(reader, Config.class);
                if (loaded != null) return loaded.checked();
            } catch (IOException | RuntimeException exception) {
                LoggerFactory.getLogger(BagelTV.ID).warn("Could not read {}; using defaults", PATH, exception);
            }
        }
        return new Config();
    }

    private Config checked() {
        if (hotkey <= 0) hotkey = GLFW.GLFW_KEY_B;
        if (scale < 0 || scale > 8) scale = 0;
        if (window == null) window = "24h";
        if (marketLeft == null) marketLeft = "index";
        if (marketRight == null) marketRight = "leaders";
        if (itemChart == null) itemChart = "candles";
        if (itemChart.equals("area")) itemChart = "line"; // a type that was removed
        if (leadersMetric == null) leadersMetric = "turnover";
        if (leadersMode == null) leadersMode = "value";
        return this;
    }

    public void save() {
        write(PATH, GSON.toJson(this));
    }

    /** Writes a file through a temporary one, so a crash never leaves half of it. */
    public static void write(Path path, String text) {
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(temporary, text + System.lineSeparator());
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException notAtomic) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            LoggerFactory.getLogger(BagelTV.ID).warn("Could not save {}", path, exception);
        }
    }
}
