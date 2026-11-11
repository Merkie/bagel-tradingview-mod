package com.merkie.bageltv;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/**
 * A local stand-in for the BagelTV API, started with the test game so no test reaches the live one. It serves
 * the fixtures captured from the live API (tools/capture-fixtures.sh) in one of three ways:
 * {@link Mode#SPARSE} as captured, minutes after the collector started; {@link Mode#DENSE} with long, busy
 * histories made up around them, so charts are judged at real density; and {@link Mode#DOWN} answering 503.
 */
public final class FakeApi implements ClientModInitializer {
    public enum Mode { DENSE, SPARSE, DOWN }

    public record Request(String path, String userAgent, Map<String, List<String>> headers) {}

    private static final Gson GSON = new Gson();
    private static final List<Request> REQUESTS = new CopyOnWriteArrayList<>();
    private static final Map<String, JsonObject> FIXTURES = new java.util.concurrent.ConcurrentHashMap<>();
    public static volatile Mode mode = Mode.DENSE;
    /** Milliseconds every answer waits, to show loading states. */
    public static volatile int delay;
    /** Where the fake listens, for tests that point the mod elsewhere and back. */
    public static String address;

    @Override
    public void onInitializeClient() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", FakeApi::handle);
            server.setExecutor(Executors.newFixedThreadPool(4, task -> {
                Thread thread = new Thread(task, "Fake BagelTV API");
                thread.setDaemon(true);
                return thread;
            }));
            server.start();
            // Its dispatcher thread would otherwise keep the game from exiting.
            ClientLifecycleEvents.CLIENT_STOPPING.register(client -> server.stop(0));
            address = "http://127.0.0.1:" + server.getAddress().getPort();
            System.setProperty("bageltv.api", address);
            System.setProperty("bageltv.heads", address + "/api/skin/");
            // Answers go stale in two seconds, so a test can watch the open view refresh.
            System.setProperty("bageltv.refresh", "2000");
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public static List<Request> requests() {
        return List.copyOf(REQUESTS);
    }

    public static void clearRequests() {
        REQUESTS.clear();
    }

    private static void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
        REQUESTS.add(new Request(exchange.getRequestURI().toString(),
                exchange.getRequestHeaders().getFirst("User-Agent"), Map.copyOf(exchange.getRequestHeaders())));
        if (path.startsWith("/api/skin/")) {
            // Every player wears the one captured head.
            byte[] png;
            try (InputStream in = FakeApi.class.getResourceAsStream("/fixtures/head.png")) {
                png = in.readAllBytes();
            }
            exchange.getResponseHeaders().set("Content-Type", "image/png");
            exchange.sendResponseHeaders(mode == Mode.DOWN ? 503 : 200, mode == Mode.DOWN ? -1 : png.length);
            if (mode != Mode.DOWN) exchange.getResponseBody().write(png);
            exchange.close();
            return;
        }
        int status = 200;
        JsonElement body;
        try {
            if (delay > 0) Thread.sleep(delay);
            if (mode == Mode.DOWN) {
                status = 503;
                body = error("upstream_error", "Bagel is not answering");
            } else {
                body = route(path, query);
                if (body == null) {
                    status = 404;
                    body = error(path.startsWith("/v1/players/") ? "player_not_found" : path.startsWith("/v1/items/")
                            ? "item_not_found" : "not_found", "No such thing");
                }
            }
        } catch (Exception exception) {
            status = 500;
            body = error("fake_api", exception.toString());
            org.slf4j.LoggerFactory.getLogger("bagel-tradingview-test").error("Fake API failed on {}", path, exception);
        }
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static JsonObject error(String code, String message) {
        JsonObject object = new JsonObject();
        object.addProperty("ok", false);
        object.addProperty("error", code);
        object.addProperty("message", message);
        return object;
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> query = new HashMap<>();
        if (raw == null) return query;
        for (String pair : raw.split("&")) {
            int equals = pair.indexOf('=');
            if (equals < 0) continue;
            query.put(pair.substring(0, equals), URLDecoder.decode(pair.substring(equals + 1),
                    StandardCharsets.UTF_8));
        }
        return query;
    }

    private static JsonObject fixture(String name) {
        return FIXTURES.computeIfAbsent(name, key -> {
            try (InputStream in = FakeApi.class.getResourceAsStream("/fixtures/" + key + ".json")) {
                if (in == null) throw new IllegalStateException("No fixture " + key);
                return GSON.fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8), JsonObject.class);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }).deepCopy();
    }

    private static long now() {
        return System.currentTimeMillis() / 1000;
    }

    /** When the fixtures were captured, the moment their own times count from. */
    private static long capturedAt() {
        return fixture("status").get("now").getAsLong();
    }

    private static JsonElement route(String path, Map<String, String> query) {
        boolean dense = mode == Mode.DENSE;
        if (path.equals("/v1/status")) return status();
        if (path.equals("/v1/market/overview")) return dense ? denseOverview(query) : fixture("overview");
        if (path.equals("/v1/market/candles")) return dense ? denseMarketCandles(query) : fixture("market_candles");
        if (path.equals("/v1/market/leaders")) return dense ? denseLeaders(query) : fixture("leaders");
        if (path.equals("/v1/orders/owners")) return owners(query, dense);
        if (path.equals("/v1/economy")) return dense ? denseEconomy() : fixture("economy");
        if (path.equals("/v1/items")) return items(query, dense);
        if (path.equals("/v1/orders")) return orders(query, dense);
        if (path.equals("/v1/orders/fills")) return fixture("fills");
        if (path.equals("/v1/players")) return players(query, dense);
        if (path.equals("/v1/players/search")) return search(query.getOrDefault("q", ""));
        if (path.equals("/v1/players/history")) return dense ? densePlayersHistory() : fixture("players_history");
        if (path.startsWith("/v1/players/")) return player(path.substring("/v1/players/".length()), dense);
        if (path.startsWith("/v1/items/")) {
            String[] parts = path.substring("/v1/items/".length()).split("/");
            if (!dense) return sparseItem(parts[0], parts.length == 1 ? "" : parts[1], query);
            JsonObject row = itemRow(parts[0], true);
            if (row == null) return null;
            if (parts.length == 1) return itemDetail(row, true);
            return switch (parts[1]) {
                case "candles" -> denseItemCandles(row, query);
                case "trades" -> denseTrades(row);
                case "book" -> denseBook(row);
                default -> null;
            };
        }
        return null;
    }

    /** The items captured whole, by key; any other captured item has its row and nothing more. */
    private static final Map<String, String> CAPTURED = Map.of("diamond", "item_diamond",
            "enchanted_book[mending=1]", "item_variant");

    private static JsonElement sparseItem(String key, String part, Map<String, String> query) {
        String name = CAPTURED.get(key);
        if (name == null) {
            JsonObject row = itemRow(key, false);
            if (row == null) return null;
            return switch (part) {
                case "" -> itemDetail(row, false);
                case "candles" -> emptyCandles(key, query);
                case "trades" -> emptyTrades(key);
                case "book" -> emptyBook(key);
                default -> null;
            };
        }
        return switch (part) {
            case "" -> fixture(name);
            // The capture is five-minute candles; other sizes of so short a history are not worth faking.
            case "candles" -> query.getOrDefault("tf", "1h").equals("5m") && !query.containsKey("source")
                    ? fixture(name + "_candles")
                    : emptyCandles(key, query);
            case "trades" -> fixture(name + "_trades");
            case "book" -> fixture(name + "_book");
            default -> null;
        };
    }

    // Shared pieces

    private static JsonObject status() {
        JsonObject status = fixture("status");
        long now = now();
        long shift = now - status.get("now").getAsLong();
        status.addProperty("now", now);
        if (mode == Mode.DENSE) {
            JsonObject since = status.getAsJsonObject("since");
            for (String key : List.of("sales", "listings", "orders")) since.addProperty(key, now - 45L * 86400);
        } else {
            JsonObject since = status.getAsJsonObject("since");
            for (String key : List.of("sales", "listings", "orders")) {
                since.addProperty(key, since.get(key).getAsLong() + shift);
            }
        }
        JsonObject collector = status.getAsJsonObject("collector");
        collector.getAsJsonObject("sales").addProperty("at", now - 4);
        collector.getAsJsonObject("listings").addProperty("at", now - 9);
        collector.getAsJsonObject("orders").addProperty("at", now - 140);
        return status;
    }

    private static double seconds(String tf) {
        return switch (tf == null ? "1h" : tf) {
            case "5m" -> 300;
            case "15m" -> 900;
            case "4h" -> 4 * 3600;
            case "1d" -> 86400;
            default -> 3600;
        };
    }

    private static double windowSeconds(String window) {
        return switch (window == null ? "24h" : window) {
            case "1h" -> 3600;
            case "6h" -> 6 * 3600;
            case "7d" -> 7 * 86400;
            case "30d" -> 30 * 86400;
            default -> 86400;
        };
    }

    private static double number(JsonObject object, String key, double fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsDouble();
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }

    // Items

    /** The captured items, and in dense mode a change and a sparkline made up for each. */
    private static List<JsonObject> itemRows(boolean dense) {
        List<JsonObject> rows = new ArrayList<>();
        for (JsonElement element : fixture("items").getAsJsonArray("items")) {
            JsonObject row = element.getAsJsonObject();
            if (dense) densify(row);
            rows.add(row);
        }
        if (dense) {
            // Two variants, as the live API lists books and potions by kind.
            for (String[] variant : new String[][] {
                    {"enchanted_book[mending=1]", "enchanted_book", "Enchanted Book (Mending 1)", "BOOKS"},
                    {"potion[strong_strength]", "potion", "Potion (Strong Strength)", "POTIONS"}}) {
                JsonObject row = rows.get(5).deepCopy();
                row.addProperty("item", variant[0]);
                row.addProperty("base", variant[1]);
                row.addProperty("name", variant[2]);
                row.addProperty("category", variant[3]);
                row.addProperty("coverage", 0.34);
                densify(row);
                rows.add(row);
            }
        }
        return rows;
    }

    private static void densify(JsonObject row) {
        Random random = new Random(row.get("item").getAsString().hashCode());
        double vwap = number(row, "vwap", 1000);
        double change = (random.nextGaussian() * 0.12);
        row.addProperty("change", round(change * 10000) / 10000);
        JsonArray spark = new JsonArray();
        double price = vwap / (1 + change);
        for (int i = 0; i < 24; i++) {
            price *= 1 + change / 24 + random.nextGaussian() * 0.008;
            if (random.nextInt(12) == 0) spark.add(JsonNull.INSTANCE);
            else spark.add(round(price));
        }
        row.add("spark", spark);
        double trades = number(row, "trades", 1) * (20 + random.nextInt(60));
        double volume = number(row, "volume", 1) * (20 + random.nextInt(60));
        row.addProperty("trades", Math.round(trades));
        row.addProperty("volume", Math.round(volume));
        row.addProperty("turnover", Math.round(volume * vwap));
        row.addProperty("sellThrough", round(0.25 + random.nextDouble() * 0.7));
        row.addProperty("medianTimeToSell", 40 + random.nextInt(4000));
        row.addProperty("timedSales", 5 + random.nextInt(400));
        if (row.get("bestAsk").isJsonNull()) row.addProperty("bestAsk", round(vwap * (1.01 + random.nextDouble() * 0.1)));
        if (row.get("bestBid").isJsonNull()) row.addProperty("bestBid", round(vwap * (0.7 + random.nextDouble() * 0.25)));
        row.addProperty("spread", round(number(row, "bestAsk", 0) - number(row, "bestBid", 0)));
        // Buy orders are their own market: bulk sellers deliver into them for about half the auction price.
        // Diamonds move mostly through the auction house and cobblestone mostly through orders; the rest vary.
        String item = row.get("item").getAsString();
        double ahShare = item.equals("diamond") ? 0.8 : item.equals("cobblestone") ? 0.03 : 0.05 + random.nextDouble() * 0.9;
        double orderVwap = round(vwap * 0.55);
        double orderTurnover = Math.round(volume * vwap * (1 - ahShare) / ahShare);
        row.addProperty("ahShare", round(ahShare * 10000) / 10000);
        row.addProperty("orderVwap", orderVwap);
        row.addProperty("orderTurnover", orderTurnover);
        row.addProperty("orderVolume", Math.round(orderTurnover / Math.max(orderVwap, 0.01)));
        row.addProperty("orderFills", 20 + random.nextInt(200));
        row.addProperty("orderLastAt", now() - 60 - random.nextInt(600));
        double worth = number(row, "worth", 0);
        row.addProperty("sellScore", round(Math.max(0, vwap - worth) * volume / 24 * number(row, "sellThrough", 0.5)));
    }

    private static JsonObject itemRow(String key, boolean dense) {
        for (JsonObject row : itemRows(dense)) if (row.get("item").getAsString().equals(key)) return row;
        return null;
    }

    private static JsonObject items(Map<String, String> query, boolean dense) {
        List<JsonObject> rows = itemRows(dense);
        String q = query.getOrDefault("q", "").toLowerCase();
        String category = query.getOrDefault("category", "");
        double minTrades = query.containsKey("minTrades") && !query.get("minTrades").isEmpty()
                ? Double.parseDouble(query.get("minTrades")) : 0;
        String sort = query.getOrDefault("sort", "turnover");
        boolean descending = !"asc".equals(query.get("order"));
        rows.removeIf(row -> !row.get("item").getAsString().contains(q)
                || !category.isEmpty() && !category.equals(row.get("category").getAsString())
                || number(row, "trades", 0) < minTrades
                || !sort.equals("name") && (row.get(sort) == null || row.get(sort).isJsonNull()));
        Comparator<JsonObject> order = sort.equals("name")
                ? Comparator.comparing(row -> row.get("name").getAsString())
                : Comparator.comparingDouble(row -> number(row, sort, 0));
        rows.sort(descending ? order.reversed() : order);
        int offset = Integer.parseInt(query.getOrDefault("offset", "0"));
        int limit = Integer.parseInt(query.getOrDefault("limit", "50"));
        JsonObject answer = new JsonObject();
        answer.addProperty("window", query.getOrDefault("window", "24h"));
        answer.addProperty("sort", sort);
        answer.addProperty("total", rows.size());
        answer.addProperty("offset", offset);
        JsonArray page = new JsonArray();
        for (int i = offset; i < Math.min(rows.size(), offset + limit); i++) page.add(rows.get(i));
        answer.add("items", page);
        return answer;
    }

    private static JsonObject itemDetail(JsonObject row, boolean dense) {
        String key = row.get("item").getAsString();
        if (!dense) {
            if (key.equals("diamond")) return fixture("item_diamond");
            JsonObject windows = new JsonObject();
            for (String window : List.of("1h", "6h", "24h", "7d", "30d")) windows.add(window, windowStats(row, 1));
            row.add("windows", windows);
            row.add("variants", new JsonArray());
            return row;
        }
        JsonObject windows = new JsonObject();
        double[] scale = {0.05, 0.28, 1, 6.4, 25};
        String[] names = {"1h", "6h", "24h", "7d", "30d"};
        for (int i = 0; i < names.length; i++) windows.add(names[i], windowStats(row, scale[i]));
        row.add("windows", windows);
        row.addProperty("firstTrade", now() - 45L * 86400);
        row.add("variants", new JsonArray());
        return row;
    }

    private static JsonObject windowStats(JsonObject row, double scale) {
        Random random = new Random(row.get("item").getAsString().hashCode() + (long) (scale * 1000));
        JsonObject stats = new JsonObject();
        double vwap = number(row, "vwap", 1000) * (1 + (scale == 1 ? 0 : random.nextGaussian() * 0.03));
        stats.addProperty("complete", mode == Mode.DENSE);
        stats.addProperty("trades", Math.max(1, Math.round(number(row, "trades", 1) * scale)));
        stats.addProperty("volume", Math.max(1, Math.round(number(row, "volume", 1) * scale)));
        stats.addProperty("turnover", Math.round(number(row, "turnover", 1) * scale));
        stats.addProperty("vwap", round(vwap));
        stats.addProperty("median", round(vwap * 1.01));
        stats.addProperty("low", round(vwap * (0.9 - 0.02 * Math.log1p(scale))));
        stats.addProperty("high", round(vwap * (1.08 + 0.03 * Math.log1p(scale))));
        JsonElement change = row.get("change");
        if (change == null || change.isJsonNull()) stats.add("change", JsonNull.INSTANCE);
        else stats.addProperty("change", round(change.getAsDouble() * (0.4 + random.nextDouble()) * 10000) / 10000);
        stats.add("sellThrough", row.get("sellThrough"));
        stats.add("medianTimeToSell", row.get("medianTimeToSell"));
        return stats;
    }

    private static JsonObject emptyCandles(String item, Map<String, String> query) {
        JsonObject answer = new JsonObject();
        answer.addProperty("item", item);
        answer.addProperty("tf", query.getOrDefault("tf", "1h"));
        answer.add("candles", new JsonArray());
        return answer;
    }

    private static JsonObject emptyTrades(String item) {
        JsonObject answer = new JsonObject();
        answer.addProperty("item", item);
        answer.add("trades", new JsonArray());
        return answer;
    }

    private static JsonObject emptyBook(String item) {
        JsonObject answer = new JsonObject();
        answer.addProperty("item", item);
        answer.add("bids", new JsonArray());
        answer.add("orders", new JsonArray());
        answer.add("asks", new JsonArray());
        answer.add("listings", new JsonArray());
        answer.addProperty("asksSource", "recent");
        answer.add("bookAt", JsonNull.INSTANCE);
        answer.add("depth", new JsonArray());
        return answer;
    }

    /** A random walk that ends at the item's VWAP now: 600 candles, a few of them missing. */
    private static JsonObject denseItemCandles(JsonObject row, Map<String, String> query) {
        String tf = query.getOrDefault("tf", "1h");
        long bucket = (long) seconds(tf);
        int count = Math.min(Integer.parseInt(query.getOrDefault("limit", "300")), 2000);
        // Deliveries into buy orders have their own walk, at about half the auction price.
        boolean orders = "orders".equals(query.get("source"));
        Random random = new Random(row.get("item").getAsString().hashCode() * 31L + bucket + (orders ? 7 : 0));
        long end = now() / bucket * bucket;
        double price = number(row, "vwap", 1000) * (orders ? 0.55 : 1);
        double volatility = 0.012 * Math.sqrt(bucket / 3600.0) + 0.004;
        List<JsonObject> candles = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long t = end - (long) i * bucket;
            double close = price;
            double open = close / (1 + random.nextGaussian() * volatility + Math.sin(i / 37.0) * volatility * 0.25);
            double high = Math.max(open, close) * (1 + Math.abs(random.nextGaussian()) * volatility * 0.6);
            double low = Math.min(open, close) * (1 - Math.abs(random.nextGaussian()) * volatility * 0.6);
            double volume = Math.max(1, Math.round(Math.exp(2.2 + random.nextGaussian() * 0.9)
                    * (1 + 0.6 * Math.sin(t / 86400.0 * 2 * Math.PI))));
            price = open;
            JsonObject candle = new JsonObject();
            if (random.nextInt(14) == 0 && i > 0) {
                // A bucket with no trades: flat at the close before it, as the API fills them.
                price = close;
                candle.addProperty("t", t);
                for (String key : List.of("o", "h", "l", "c")) candle.addProperty(key, round(close));
                candle.addProperty("volume", 0);
                candle.addProperty("turnover", 0);
                candle.addProperty("trades", 0);
                candle.add("vwap", JsonNull.INSTANCE);
                candle.addProperty("empty", true);
                candles.add(candle);
                continue;
            }
            candle.addProperty("t", t);
            candle.addProperty("o", round(open));
            candle.addProperty("h", round(high));
            candle.addProperty("l", round(low));
            candle.addProperty("c", round(close));
            candle.addProperty("volume", volume);
            candle.addProperty("turnover", Math.round(volume * (open + close) / 2));
            candle.addProperty("trades", Math.max(1, Math.round(volume / 3)));
            candle.addProperty("vwap", round((open + close + high + low) / 4));
            candles.add(candle);
        }
        JsonObject answer = new JsonObject();
        answer.addProperty("item", row.get("item").getAsString());
        answer.addProperty("source", orders ? "orders" : "auction");
        answer.addProperty("tf", tf);
        answer.addProperty("from", end - (long) count * bucket);
        answer.addProperty("to", now());
        JsonArray array = new JsonArray();
        for (int i = candles.size() - 1; i >= 0; i--) array.add(candles.get(i));
        answer.add("candles", array);
        return answer;
    }

    private static final String[] NAMES = {"Simpinhoe", "Wintap", "Luca_Ponno", "Cup_Songs", "diddyishereXD",
            "___imortqlvr___", "KnoxTheFox", "MerkieMoo", "bagel_baron", "xX_Trader_Xx", "OreWhisperer", "Stonks64",
            "LavaLlama", "PixelPete", "ArbitrageAndy", "CreeperCo"};

    private static JsonObject denseTrades(JsonObject row) {
        Random random = new Random(row.get("item").getAsString().hashCode() + 7);
        double price = number(row, "vwap", 1000);
        long t = now() - 3;
        JsonArray trades = new JsonArray();
        for (int i = 0; i < 100; i++) {
            JsonObject trade = new JsonObject();
            int qty = random.nextInt(5) == 0 ? 64 : 1 + random.nextInt(16);
            double unit = round(price * (1 + random.nextGaussian() * 0.02));
            trade.addProperty("id", 2925616 - i);
            trade.addProperty("t", t);
            trade.addProperty("qty", qty);
            trade.addProperty("price", Math.round(unit * qty));
            trade.addProperty("unit", unit);
            trade.addProperty("seller", NAMES[random.nextInt(NAMES.length)]);
            trade.addProperty("buyer", NAMES[random.nextInt(NAMES.length)]);
            trade.addProperty("name", row.get("name").getAsString());
            trade.addProperty("outlier", random.nextInt(40) == 0);
            trade.addProperty("timeToSell", 20 + random.nextInt(900));
            trades.add(trade);
            t -= 5 + random.nextInt(240);
            price *= 1 + random.nextGaussian() * 0.004;
        }
        JsonObject answer = new JsonObject();
        answer.addProperty("item", row.get("item").getAsString());
        answer.add("trades", trades);
        return answer;
    }

    /** A full book: thirty bid levels under the price and thirty ask levels over it. */
    private static JsonObject denseBook(JsonObject row) {
        Random random = new Random(row.get("item").getAsString().hashCode() + 11);
        double price = number(row, "vwap", 1000);
        long now = now();
        JsonArray bids = new JsonArray(), asks = new JsonArray(), orders = new JsonArray(), listings = new JsonArray();
        double bid = price * 0.985, ask = price * 1.012;
        for (int i = 0; i < 30; i++) {
            int units = (int) Math.round(Math.exp(4 + random.nextGaussian() * 1.1) * (1 + i * 0.12));
            JsonObject level = new JsonObject();
            level.addProperty("price", round(bid));
            level.addProperty("units", units);
            level.addProperty("orders", 1 + random.nextInt(3));
            level.addProperty("value", Math.round(bid * units));
            bids.add(level);
            JsonObject order = new JsonObject();
            order.addProperty("id", 96000 + i);
            order.addProperty("owner", NAMES[random.nextInt(NAMES.length)]);
            order.addProperty("item", row.get("item").getAsString());
            order.addProperty("name", row.get("name").getAsString());
            order.addProperty("price", round(bid));
            order.addProperty("qty", units * 2);
            order.addProperty("delivered", units);
            order.addProperty("remaining", units);
            order.addProperty("value", Math.round(bid * units));
            order.addProperty("enchants", "");
            order.addProperty("potion", "");
            order.addProperty("createdAt", now - 600 - random.nextInt(86400 * 3));
            order.addProperty("expiresAt", now + 86400 * (1 + random.nextInt(6)));
            orders.add(order);
            bid *= 1 - 0.004 - random.nextDouble() * 0.012;

            int askUnits = (int) Math.round(Math.exp(2.5 + random.nextGaussian()) * (1 + i * 0.1));
            JsonObject askLevel = new JsonObject();
            askLevel.addProperty("unit", round(ask));
            askLevel.addProperty("units", askUnits);
            askLevel.addProperty("listings", 1 + random.nextInt(4));
            boolean exactPrice = ask < 100000 || random.nextInt(3) == 0;
            askLevel.addProperty("exact", exactPrice);
            asks.add(askLevel);
            JsonObject listing = new JsonObject();
            listing.addProperty("seller", NAMES[random.nextInt(NAMES.length)]);
            listing.addProperty("qty", askUnits);
            listing.addProperty("price", Math.round(ask * askUnits));
            listing.addProperty("unit", round(ask));
            listing.addProperty("name", row.get("name").getAsString());
            listing.addProperty("priceExact", exactPrice ? 1 : 0);
            listing.addProperty("enchants", "");
            listing.addProperty("createdAt", now - random.nextInt(86400));
            listing.addProperty("expiresAt", now + 86400 * 2);
            listings.add(listing);
            ask *= 1 + 0.003 + random.nextDouble() * 0.014;
        }
        JsonObject answer = new JsonObject();
        answer.addProperty("item", row.get("item").getAsString());
        answer.add("bids", bids);
        answer.add("orders", orders);
        answer.add("asks", asks);
        answer.add("listings", listings);
        answer.addProperty("asksSource", "book");
        answer.addProperty("bookAt", now - 320);
        answer.add("depth", new JsonArray());
        return answer;
    }

    // Market

    private static JsonObject denseOverview(Map<String, String> query) {
        JsonObject overview = fixture("overview");
        long now = now();
        overview.addProperty("window", query.getOrDefault("window", "24h"));
        overview.addProperty("now", now);
        overview.addProperty("since", now - 45L * 86400);
        overview.addProperty("complete", true);
        List<JsonObject> rows = itemRows(true);
        double scale = windowSeconds(query.get("window")) / 86400;
        double turnover = 0, volume = 0, trades = 0;
        for (JsonObject row : rows) {
            turnover += number(row, "turnover", 0);
            volume += number(row, "volume", 0);
            trades += number(row, "trades", 0);
        }
        JsonObject totals = overview.getAsJsonObject("totals");
        totals.addProperty("turnover", Math.round(turnover * scale));
        totals.addProperty("volume", Math.round(volume * scale));
        totals.addProperty("trades", Math.round(trades * scale));
        totals.addProperty("items", rows.size());
        totals.addProperty("turnoverChange", 0.0842);
        JsonObject index = overview.getAsJsonObject("index");
        index.addProperty("value", 1043.27);
        index.addProperty("change", 0.0127);
        JsonObject lists = overview.getAsJsonObject("lists");
        lists.add("turnover", top(rows, "turnover", true, 10));
        lists.add("volume", top(rows, "volume", true, 10));
        lists.add("trades", top(rows, "trades", true, 10));
        lists.add("gainers", top(rows, "change", true, 10));
        lists.add("losers", top(rows, "change", false, 10));
        lists.add("fastest", top(rows, "medianTimeToSell", false, 10));
        lists.add("sell", top(rows, "sellScore", true, 10));
        lists.add("wanted", top(rows, "bidValue", true, 10));
        return overview;
    }

    private static JsonArray top(List<JsonObject> rows, String key, boolean descending, int count) {
        List<JsonObject> sorted = new ArrayList<>(rows);
        sorted.removeIf(row -> row.get(key) == null || row.get(key).isJsonNull());
        Comparator<JsonObject> order = Comparator.comparingDouble(row -> number(row, key, 0));
        sorted.sort(descending ? order.reversed() : order);
        JsonArray array = new JsonArray();
        for (int i = 0; i < Math.min(count, sorted.size()); i++) array.add(sorted.get(i));
        return array;
    }

    private static JsonObject denseMarketCandles(Map<String, String> query) {
        String tf = query.getOrDefault("tf", "1h");
        long bucket = (long) seconds(tf);
        int count = Math.min(Integer.parseInt(query.getOrDefault("limit", "300")), 2000);
        Random random = new Random(42 + bucket);
        long end = now() / bucket * bucket;
        double value = 1043.27;
        double volatility = 0.0035 * Math.sqrt(bucket / 3600.0) + 0.001;
        List<JsonObject> candles = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long t = end - (long) i * bucket;
            double close = value;
            double open = close / (1 + random.nextGaussian() * volatility + Math.sin(i / 53.0) * volatility * 0.3);
            double high = Math.max(open, close) * (1 + Math.abs(random.nextGaussian()) * volatility * 0.5);
            double low = Math.min(open, close) * (1 - Math.abs(random.nextGaussian()) * volatility * 0.5);
            double activity = (1.2 + Math.sin(t / 86400.0 * 2 * Math.PI)) * Math.exp(random.nextGaussian() * 0.5);
            value = open;
            JsonObject candle = new JsonObject();
            candle.addProperty("t", t);
            candle.addProperty("o", round(open));
            candle.addProperty("h", round(high));
            candle.addProperty("l", round(low));
            candle.addProperty("c", round(close));
            candle.addProperty("turnover", Math.round(9.0e7 * activity * bucket / 300));
            candle.addProperty("volume", Math.round(800 * activity * bucket / 300));
            candle.addProperty("trades", Math.round(70 * activity * bucket / 300));
            candle.addProperty("orderTurnover", Math.round(3.0e7 * activity * bucket / 300));
            candle.addProperty("orderVolume", Math.round(4000 * activity * bucket / 300));
            candles.add(candle);
        }
        JsonObject answer = new JsonObject();
        answer.addProperty("tf", tf);
        answer.addProperty("from", end - (long) count * bucket);
        answer.addProperty("to", now());
        answer.addProperty("base", 1000);
        JsonArray array = new JsonArray();
        for (int i = candles.size() - 1; i >= 0; i--) array.add(candles.get(i));
        answer.add("candles", array);
        return answer;
    }

    /** Ten series over the whole window, with ranks that cross and a remainder. */
    private static JsonObject denseLeaders(Map<String, String> query) {
        String metric = query.getOrDefault("metric", "turnover");
        String tf = query.getOrDefault("tf", "1h");
        String window = query.getOrDefault("window", "24h");
        long bucket = (long) seconds(tf);
        int count = (int) Math.max(2, windowSeconds(window) / bucket);
        int top = Integer.parseInt(query.getOrDefault("top", "8"));
        long end = now() / bucket * bucket;
        List<JsonObject> rows = itemRows(true);
        Comparator<JsonObject> order = Comparator.comparingDouble(row -> number(row, metric, 0));
        rows.sort(order.reversed());
        rows = rows.subList(0, Math.min(top, rows.size()));
        double[][] values = new double[rows.size()][count];
        double[] totals = new double[rows.size()];
        for (int s = 0; s < rows.size(); s++) {
            Random random = new Random(rows.get(s).get("item").getAsString().hashCode() + metric.hashCode());
            double level = number(rows.get(s), metric, 1) / count;
            double drift = 1;
            double phase = random.nextDouble() * 6.28;
            for (int i = 0; i < count; i++) {
                drift *= 1 + random.nextGaussian() * 0.08;
                drift = Math.clamp(drift, 0.3, 3);
                long t = end - (long) (count - 1 - i) * bucket;
                double daily = 1 + 0.45 * Math.sin(t / 86400.0 * 2 * Math.PI + phase);
                double value = level * drift * daily * Math.exp(random.nextGaussian() * 0.25);
                if (random.nextInt(30) == 0) value = 0;
                values[s][i] = metric.equals("turnover") ? Math.round(value) : Math.round(value);
                totals[s] += values[s][i];
            }
        }
        JsonArray buckets = new JsonArray();
        JsonArray other = new JsonArray();
        Random random = new Random(99);
        for (int i = 0; i < count; i++) {
            buckets.add(end - (long) (count - 1 - i) * bucket);
            double sum = 0;
            for (double[] one : values) sum += one[i];
            other.add(Math.round(sum * (0.5 + random.nextDouble() * 0.3)));
        }
        JsonArray series = new JsonArray();
        for (int s = 0; s < rows.size(); s++) {
            JsonObject one = new JsonObject();
            one.addProperty("item", rows.get(s).get("item").getAsString());
            one.addProperty("name", rows.get(s).get("name").getAsString());
            one.addProperty("total", totals[s]);
            JsonArray v = new JsonArray(), vwap = new JsonArray(), rank = new JsonArray();
            for (int i = 0; i < count; i++) {
                v.add(values[s][i]);
                vwap.add(values[s][i] > 0 ? number(rows.get(s), "vwap", 1) : null);
                int place = 1;
                for (double[] others : values) if (others[i] > values[s][i]) place++;
                rank.add(values[s][i] > 0 ? place : 0);
            }
            one.add("values", v);
            one.add("vwap", vwap);
            one.add("rank", rank);
            series.add(one);
        }
        JsonObject answer = new JsonObject();
        answer.addProperty("metric", metric);
        answer.addProperty("window", window);
        answer.addProperty("tf", tf);
        answer.add("buckets", buckets);
        answer.add("series", series);
        answer.add("other", other);
        return answer;
    }

    private static JsonObject denseEconomy() {
        JsonObject economy = fixture("economy");
        long now = now();
        long shift = now - capturedAt();
        JsonArray daily = economy.getAsJsonArray("daily");
        for (JsonElement day : daily) {
            JsonObject object = day.getAsJsonObject();
            object.addProperty("t", object.get("t").getAsLong() + shift / 86400 * 86400);
        }
        JsonObject current = economy.getAsJsonObject("now");
        current.addProperty("t", now - 40);
        JsonArray series = new JsonArray();
        Random random = new Random(5);
        double money = number(current, "totalMoney", 8e12) * 0.94, escrow = number(current, "orderEscrow", 8e11) * 0.9;
        int points = 7 * 288;
        for (int i = 0; i < points; i++) {
            long t = now - (long) (points - 1 - i) * 300;
            money *= 1 + 0.06 / points + random.nextGaussian() * 0.00012;
            escrow *= 1 + 0.1 / points + random.nextGaussian() * 0.0015;
            JsonObject point = new JsonObject();
            point.addProperty("t", t);
            point.addProperty("totalMoney", Math.round(money));
            point.addProperty("online", Math.round(1100 + 500 * Math.sin((t % 86400) / 86400.0 * 2 * Math.PI - 1.2)
                    + random.nextGaussian() * 25));
            point.addProperty("players", 219000 + i / 3);
            point.addProperty("openOrders", 1600 + (int) (80 * Math.sin(i / 90.0)) + random.nextInt(12));
            point.addProperty("orderEscrow", Math.round(escrow));
            series.add(point);
        }
        economy.add("series", series);
        return economy;
    }

    // Orders

    private static JsonObject orders(Map<String, String> query, boolean dense) {
        JsonObject answer = fixture("orders");
        List<JsonObject> rows = new ArrayList<>();
        long shift = now() - capturedAt();
        for (JsonElement element : answer.getAsJsonArray("orders")) {
            JsonObject row = element.getAsJsonObject();
            row.addProperty("createdAt", row.get("createdAt").getAsLong() + shift);
            row.addProperty("expiresAt", row.get("expiresAt").getAsLong() + shift);
            if (dense) {
                Random random = new Random(row.get("id").getAsLong());
                double price = number(row, "price", 1);
                if (random.nextInt(3) > 0) {
                    double vwap = round(price * (0.6 + random.nextDouble() * 0.7));
                    row.addProperty("ahVwap", vwap);
                    row.addProperty("vsAh", round(price / vwap * 1000) / 1000);
                    row.addProperty("edge", round((price / vwap - 1) * 10000) / 10000);
                }
                if (random.nextInt(5) == 0) {
                    double ask = round(price * (0.7 + random.nextDouble() * 0.25));
                    row.addProperty("bestAsk", ask);
                    row.addProperty("arbitrage", round(price - ask));
                }
                if (random.nextInt(3) == 0) row.addProperty("filledLastHour", 1 + random.nextInt(400));
                double qty = number(row, "qty", 1);
                double delivered = Math.floor(qty * random.nextDouble() * 0.9);
                row.addProperty("delivered", delivered);
                row.addProperty("remaining", qty - delivered);
            }
            rows.add(row);
        }
        if (dense) {
            // Three hundred orders, so the table has more than one page to fetch.
            int captured = rows.size();
            for (int copy = 1; copy <= 2; copy++) {
                for (int i = 0; i < captured; i++) {
                    JsonObject clone = rows.get(i).deepCopy();
                    clone.addProperty("id", clone.get("id").getAsLong() + copy * 1_000_000L);
                    double shrink = 1 - copy * 0.31;
                    for (String key : List.of("pool", "value", "remaining", "qty")) {
                        clone.addProperty(key, Math.round(number(clone, key, 0) * shrink));
                    }
                    rows.add(clone);
                }
            }
        }
        String item = query.getOrDefault("item", ""), owner = query.getOrDefault("owner", "");
        String sort = query.getOrDefault("sort", "value");
        boolean descending = !"asc".equals(query.get("order"));
        rows.removeIf(row -> !item.isEmpty() && !item.equals(row.get("item").getAsString())
                || !owner.isEmpty() && !owner.equalsIgnoreCase(row.get("owner").getAsString())
                || row.get(sort) == null || row.get(sort).isJsonNull());
        Comparator<JsonObject> order = Comparator.comparingDouble(row -> number(row, sort, 0));
        rows.sort(descending ? order.reversed() : order);
        int offset = Integer.parseInt(query.getOrDefault("offset", "0"));
        int limit = Integer.parseInt(query.getOrDefault("limit", "50"));
        JsonArray page = new JsonArray();
        for (int i = offset; i < Math.min(rows.size(), offset + limit); i++) page.add(rows.get(i));
        answer.addProperty("sort", sort);
        answer.addProperty("total", rows.size());
        answer.addProperty("offset", offset);
        answer.addProperty("sweptAt", now() - 140);
        answer.add("orders", page);
        return answer;
    }

    // Players

    private static JsonObject players(Map<String, String> query, boolean dense) {
        JsonObject answer = fixture("players");
        List<JsonObject> rows = new ArrayList<>();
        for (JsonElement element : answer.getAsJsonArray("players")) {
            JsonObject row = element.getAsJsonObject();
            if (dense) {
                Random random = new Random(row.get("name").getAsString().hashCode());
                row.addProperty("change", round(random.nextGaussian() * 0.03 * 10000) / 10000);
            }
            rows.add(row);
        }
        String sort = query.getOrDefault("sort", "trueBalance");
        boolean descending = !"asc".equals(query.get("order"));
        Comparator<JsonObject> order = Comparator.comparingDouble(row -> number(row, sort, 0));
        rows.sort(descending ? order.reversed() : order);
        int offset = Integer.parseInt(query.getOrDefault("offset", "0"));
        int limit = Integer.parseInt(query.getOrDefault("limit", "100"));
        JsonArray page = new JsonArray();
        for (int i = offset; i < Math.min(rows.size(), offset + limit); i++) page.add(rows.get(i));
        answer.addProperty("sort", sort);
        answer.addProperty("total", rows.size());
        answer.add("players", page);
        return answer;
    }

    /** Eight players over thirty days: daily points, then every fifteen minutes for the last day. */
    private static JsonObject densePlayersHistory() {
        JsonArray series = new JsonArray();
        long now = now();
        int index = 0;
        for (JsonElement element : fixture("players").getAsJsonArray("players")) {
            if (index++ >= 8) break;
            JsonObject row = element.getAsJsonObject();
            Random random = new Random(row.get("name").getAsString().hashCode());
            double balance = number(row, "balance", 1e9);
            double money = balance * (0.35 + random.nextDouble() * 0.5);
            JsonArray points = new JsonArray();
            int days = 29, quarters = 96;
            for (int i = 0; i < days + quarters; i++) {
                long t = i < days ? (now - 86400L * (days + 1 - i)) / 86400 * 86400 : now - (quarters - 1 - (i - days)) * 900L;
                int left = days + quarters - i;
                money += (balance - money) / left * (i < days ? 3 : 0.2) + money * random.nextGaussian() * (i < days
                        ? 0.03 : 0.002);
                JsonObject point = new JsonObject();
                point.addProperty("t", t);
                point.addProperty("money", Math.round(i == days + quarters - 1 ? balance : money));
                points.add(point);
            }
            JsonObject one = new JsonObject();
            one.add("uuid", row.get("uuid"));
            one.add("name", row.get("name"));
            one.add("balance", row.get("balance"));
            one.add("points", points);
            series.add(one);
        }
        JsonObject answer = new JsonObject();
        answer.addProperty("window", "30d");
        answer.add("series", series);
        return answer;
    }

    /** The captured owners, sorted and paged as asked; three times as many with the busy market. */
    private static JsonObject owners(Map<String, String> query, boolean dense) {
        JsonObject answer = fixture("owners");
        List<JsonObject> rows = new ArrayList<>();
        for (JsonElement element : answer.getAsJsonArray("owners")) rows.add(element.getAsJsonObject());
        if (dense) {
            int captured = rows.size();
            for (int copy = 1; copy <= 2; copy++) {
                for (int i = 0; i < captured; i++) {
                    JsonObject clone = rows.get(i).deepCopy();
                    clone.addProperty("name", clone.get("name").getAsString() + "_" + copy);
                    for (String key : List.of("paid", "units", "locked", "lockedActive", "lockedIdle")) {
                        clone.addProperty(key, Math.round(number(clone, key, 0) * (1 - copy * 0.3)));
                    }
                    rows.add(clone);
                }
            }
        }
        String sort = query.getOrDefault("sort", "paid");
        Comparator<JsonObject> order = Comparator.comparingDouble(row -> number(row, sort, 0));
        rows.sort("asc".equals(query.get("order")) ? order : order.reversed());
        int offset = Integer.parseInt(query.getOrDefault("offset", "0"));
        int limit = Integer.parseInt(query.getOrDefault("limit", "50"));
        JsonArray page = new JsonArray();
        for (int i = offset; i < Math.min(rows.size(), offset + limit); i++) {
            rows.get(i).addProperty("rank", i + 1);
            page.add(rows.get(i));
        }
        answer.addProperty("window", query.getOrDefault("window", "24h"));
        answer.addProperty("sort", sort);
        answer.addProperty("total", rows.size());
        answer.addProperty("offset", offset);
        answer.add("owners", page);
        return answer;
    }

    private static JsonObject search(String q) {
        JsonArray names = new JsonArray();
        if (q.length() >= 2) {
            for (JsonElement element : fixture("players").getAsJsonArray("players")) {
                String name = element.getAsJsonObject().get("name").getAsString();
                if (name.toLowerCase().startsWith(q.toLowerCase()) && names.size() < 8) names.add(name);
            }
        }
        JsonObject answer = new JsonObject();
        answer.add("players", names);
        return answer;
    }

    private static JsonObject player(String name, boolean dense) {
        if (name.equalsIgnoreCase("nobody_here")) return null;
        JsonObject player = fixture("player_simpinhoe");
        long now = now();
        long shift = now - capturedAt();
        if (!name.equalsIgnoreCase("Simpinhoe")) {
            // Anyone else on the list gets the same shape with their own name and balance.
            for (JsonElement element : fixture("players").getAsJsonArray("players")) {
                JsonObject row = element.getAsJsonObject();
                if (!row.get("name").getAsString().equalsIgnoreCase(name)) continue;
                double factor = number(row, "balance", 1) / number(player, "balance", 1);
                player.addProperty("name", row.get("name").getAsString());
                player.add("balance", row.get("balance"));
                player.add("escrow", row.get("escrow"));
                player.add("trueBalance", row.get("trueBalance"));
                for (JsonElement day : player.getAsJsonArray("daily")) {
                    JsonObject object = day.getAsJsonObject();
                    object.addProperty("money", Math.round(number(object, "money", 0) * factor));
                }
            }
            if (player.get("name").getAsString().equals("Simpinhoe")) player.addProperty("name", name);
        }
        for (JsonElement day : player.getAsJsonArray("daily")) {
            JsonObject object = day.getAsJsonObject();
            object.addProperty("t", object.get("t").getAsLong() + shift / 86400 * 86400);
        }
        for (String list : List.of("intraday", "escrowHistory")) {
            for (JsonElement point : player.getAsJsonArray(list)) {
                JsonObject object = point.getAsJsonObject();
                object.addProperty("t", object.get("t").getAsLong() + shift);
            }
        }
        for (JsonElement order : player.getAsJsonArray("orders")) {
            JsonObject object = order.getAsJsonObject();
            object.addProperty("createdAt", object.get("createdAt").getAsLong() + shift);
            object.addProperty("expiresAt", object.get("expiresAt").getAsLong() + shift);
        }
        player.addProperty("lastOnline", now - 11000);
        if (!dense) return player;

        Random random = new Random(name.hashCode());
        double balance = number(player, "balance", 1e9), escrow = number(player, "escrow", 1e8);
        JsonArray intraday = new JsonArray(), escrowHistory = new JsonArray();
        int points = 7 * 144;
        double target = balance;
        double money = balance * 0.93, locked = escrow * 0.6;
        JsonArray days = player.getAsJsonArray("daily");
        for (JsonElement day : days) {
            JsonObject object = day.getAsJsonObject();
            if (object.get("t").getAsLong() < now - (long) points * 600) money = number(object, "money", money);
        }
        for (int i = 0; i < points; i++) {
            long t = now - (long) (points - 1 - i) * 600;
            money += (target - money) / (points - i) + money * random.nextGaussian() * 0.0016;
            if (random.nextInt(140) == 0) money *= 1 + random.nextGaussian() * 0.02;
            JsonObject point = new JsonObject();
            point.addProperty("t", t);
            point.addProperty("money", Math.round(i == points - 1 ? balance : money));
            intraday.add(point);
            if (i % 3 == 0) {
                locked *= 1 + 0.5 / points * 3 + random.nextGaussian() * 0.01;
                JsonObject held = new JsonObject();
                held.addProperty("t", t);
                held.addProperty("escrow", Math.round(i >= points - 3 ? escrow : locked));
                held.addProperty("orders", 8 + random.nextInt(5));
                escrowHistory.add(held);
            }
        }
        JsonArray fills = new JsonArray();
        List<JsonObject> wanted = itemRows(true);
        long seen = now - 120;
        for (int i = 0; i < 60; i++) {
            JsonObject row = wanted.get(random.nextInt(wanted.size()));
            int qty = 1 + random.nextInt(640);
            double each = round(number(row, "vwap", 100) * 0.9);
            JsonObject fill = new JsonObject();
            fill.addProperty("t", seen);
            fill.addProperty("orderId", 90000 + random.nextInt(900));
            fill.addProperty("item", row.has("base") && !row.get("base").isJsonNull() ? row.get("base").getAsString()
                    : row.get("item").getAsString());
            fill.addProperty("key", row.get("item").getAsString());
            fill.addProperty("name", row.get("name").getAsString());
            fill.addProperty("qty", qty);
            fill.addProperty("price", each);
            fill.addProperty("value", Math.round(each * qty));
            fills.add(fill);
            seen -= 300L * (1 + random.nextInt(20));
        }
        player.add("orderFills", fills);
        for (JsonElement order : player.getAsJsonArray("orders")) {
            JsonObject object = order.getAsJsonObject();
            double delivered = Math.floor(number(object, "qty", 1) * random.nextDouble() * 0.7);
            object.addProperty("delivered", delivered);
            object.addProperty("paid", Math.round(delivered * number(object, "price", 0)));
        }
        player.add("intraday", intraday);
        player.add("escrowHistory", escrowHistory);

        List<JsonObject> rows = itemRows(true);
        JsonObject auction = player.getAsJsonObject("auction");
        JsonArray topSold = new JsonArray(), topBought = new JsonArray(), recent = new JsonArray();
        double soldTurnover = 0, boughtTurnover = 0, soldVolume = 0, boughtVolume = 0;
        for (int i = 0; i < 8; i++) {
            JsonObject sold = topItem(rows.get(random.nextInt(rows.size())), random);
            JsonObject bought = topItem(rows.get(random.nextInt(rows.size())), random);
            topSold.add(sold);
            topBought.add(bought);
            soldTurnover += number(sold, "turnover", 0);
            boughtTurnover += number(bought, "turnover", 0);
            soldVolume += number(sold, "volume", 0);
            boughtVolume += number(bought, "volume", 0);
        }
        long t = now - 60;
        for (int i = 0; i < 40; i++) {
            JsonObject row = rows.get(random.nextInt(rows.size()));
            boolean sold = random.nextBoolean();
            int qty = 1 + random.nextInt(32);
            JsonObject trade = new JsonObject();
            trade.addProperty("id", 2900000 - i);
            trade.addProperty("t", t);
            trade.addProperty("item", row.get("item").getAsString());
            trade.addProperty("name", row.get("name").getAsString());
            trade.addProperty("qty", qty);
            trade.addProperty("price", Math.round(number(row, "vwap", 100) * qty));
            trade.addProperty("seller", sold ? player.get("name").getAsString() : NAMES[random.nextInt(NAMES.length)]);
            trade.addProperty("buyer", sold ? NAMES[random.nextInt(NAMES.length)] : player.get("name").getAsString());
            trade.addProperty("side", sold ? "sold" : "bought");
            recent.add(trade);
            t -= 200 + random.nextInt(9000);
        }
        auction.add("sold", side(214, soldTurnover, soldVolume));
        auction.add("bought", side(97, boughtTurnover, boughtVolume));
        auction.add("topSold", topSold);
        auction.add("topBought", topBought);
        JsonObject listed = new JsonObject();
        listed.addProperty("listings", 12);
        listed.addProperty("value", 48_250_000_00L);
        auction.add("listed", listed);
        auction.add("recent", recent);
        return player;
    }

    private static JsonObject side(int trades, double turnover, double volume) {
        JsonObject side = new JsonObject();
        side.addProperty("trades", trades);
        side.addProperty("turnover", Math.round(turnover));
        side.addProperty("volume", Math.round(volume));
        return side;
    }

    private static JsonObject topItem(JsonObject row, Random random) {
        JsonObject item = new JsonObject();
        int volume = 1 + random.nextInt(900);
        item.addProperty("item", row.get("item").getAsString());
        item.addProperty("name", row.get("name").getAsString());
        item.addProperty("trades", 1 + volume / 12);
        item.addProperty("volume", volume);
        item.addProperty("turnover", Math.round(number(row, "vwap", 100) * volume));
        return item;
    }
}
