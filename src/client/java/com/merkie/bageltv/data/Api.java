package com.merkie.bageltv.data;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.merkie.bageltv.BagelTV;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The BagelTV data API. A view asks for a path every frame it draws and gets that path's cache entry back; the
 * entry fetches itself when it is new or old. So only what is on screen refreshes, nothing is asked twice at once,
 * nothing is asked while the terminal is closed, and a request nobody is drawing any more is dropped.
 * Requests carry a User-Agent and nothing about the player.
 */
public final class Api {
    /** The live API. Tests point {@code bageltv.api} at a local stand-in. */
    public static final String BASE = "https://bageltv.archers.tools";
    /** How old an answer gets before the view showing it asks again. Tests shorten it. */
    private static final long REFRESH_MS = Long.getLong("bageltv.refresh", 25_000);
    /** How long after a failure the next automatic try waits. */
    private static final long RETRY_MS = 15_000;
    /** A request whose entry no view has drawn for this long is dropped. */
    private static final long UNUSED_MS = 1_500;
    /** An entry no view has drawn for this long is forgotten. */
    private static final long EVICT_MS = 5 * 60_000;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    private static final Gson GSON = new Gson();
    private static final Map<String, Query<?>> CACHE = new HashMap<>();
    private static long lastSweep;

    private Api() {}

    /** One path's cached answer and the state of its request. Read and changed on the client thread only. */
    public static final class Query<T> {
        private final String path;
        private final Class<T> type;
        /** The latest answer, kept while a refresh runs or fails. */
        public T data;
        /** Why the latest request failed, in a few plain words, or null. */
        public String error;
        public boolean loading;
        /** Counts answers, so a view can tell when {@link #data} is new. */
        public int version;
        private long fetchedAt;
        private long failedAt;
        private long usedAt;
        private CompletableFuture<?> request;

        private Query(String path, Class<T> type) {
            this.path = path;
            this.type = type;
        }

        /** Asks again now, after an error. */
        public void retry() {
            failedAt = 0;
            error = null;
            if (!loading) fetch(this);
        }
    }

    public static String base() {
        return System.getProperty("bageltv.api", BASE);
    }

    /** The cache entry for a path such as {@code /v1/items?sort=turnover}, fetching it if it is new or old. */
    @SuppressWarnings("unchecked")
    public static <T> Query<T> get(String path, Class<T> type) {
        long now = System.currentTimeMillis();
        Query<T> query = (Query<T>) CACHE.get(path);
        if (query == null) {
            query = new Query<>(path, type);
            CACHE.put(path, query);
        }
        query.usedAt = now;
        if (!query.loading && now - query.fetchedAt > REFRESH_MS && now - query.failedAt > RETRY_MS) fetch(query);
        sweep(now);
        return query;
    }

    public static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** Drops requests nobody is drawing and forgets entries long unused. */
    private static void sweep(long now) {
        if (now - lastSweep < 500) return;
        lastSweep = now;
        CACHE.values().removeIf(query -> {
            if (query.loading && now - query.usedAt > UNUSED_MS) cancel(query);
            return !query.loading && now - query.usedAt > EVICT_MS;
        });
    }

    /** Whether any request is in flight. For tests. */
    public static boolean busy() {
        for (Query<?> query : CACHE.values()) if (query.loading) return true;
        return false;
    }

    /** Forgets every answer, so the next frame asks again. For tests. */
    public static void clear() {
        cancelAll();
        CACHE.clear();
    }

    /** Drops every request in flight, when the terminal closes. */
    public static void cancelAll() {
        for (Query<?> query : CACHE.values()) if (query.loading) cancel(query);
    }

    private static void cancel(Query<?> query) {
        CompletableFuture<?> request = query.request;
        query.request = null;
        query.loading = false;
        if (request != null) request.cancel(true);
    }

    private static <T> void fetch(Query<T> query) {
        query.loading = true;
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(base() + query.path))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", BagelTV.NAME + "/" + BagelTV.VERSION)
                    .header("Accept", "application/json")
                    .build();
        } catch (IllegalArgumentException badPath) {
            query.loading = false;
            query.failedAt = System.currentTimeMillis();
            query.error = "Bad request";
            return;
        }
        CompletableFuture<HttpResponse<String>> future = HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        query.request = future;
        future.whenComplete((response, exception) -> Minecraft.getInstance().execute(() -> {
            // A dropped request's answer is ignored; a newer request owns the entry by now.
            if (query.request != future) return;
            query.request = null;
            query.loading = false;
            long now = System.currentTimeMillis();
            String error;
            if (exception != null) {
                error = "Can't reach the market data";
            } else if (response.statusCode() / 100 != 2 || response.statusCode() == 202) {
                error = errorText(response);
            } else {
                try {
                    T parsed = GSON.fromJson(response.body(), query.type);
                    if (parsed == null) throw new IllegalStateException("empty");
                    query.data = parsed;
                    query.version++;
                    query.error = null;
                    query.fetchedAt = now;
                    return;
                } catch (RuntimeException unreadable) {
                    error = "Unexpected answer from the market data";
                }
            }
            query.error = error;
            query.failedAt = now;
        }));
    }

    private static String errorText(HttpResponse<String> response) {
        String code = null;
        try {
            JsonObject object = GSON.fromJson(response.body(), JsonObject.class);
            if (object != null && object.has("error")) code = object.get("error").getAsString();
        } catch (RuntimeException ignored) {
            // Not the API's JSON error.
        }
        if (code == null) return "Market data answered " + response.statusCode();
        return switch (code) {
            case "item_not_found" -> "No trades recorded for this item";
            case "player_not_found" -> "No player by that name";
            case "pending" -> "Still looking this player up";
            case "upstream_error" -> "Bagel isn't answering right now";
            default -> "Market data answered " + response.statusCode();
        };
    }
}
