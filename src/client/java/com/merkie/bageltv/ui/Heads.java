package com.merkie.bageltv.ui;

import com.merkie.bageltv.BagelTV;
import com.merkie.bageltv.data.Api;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Players' flat faces from Bagel's CDN, 32 px each. A face is asked for once, off the render thread, and kept as a
 * texture while the game runs; until it arrives, and if it never does, callers draw a placeholder.
 */
public final class Heads {
    /** Bagel's skin service. Tests point {@code bageltv.heads} at a local stand-in. */
    public static final String BASE = "https://bagelsmp.b-cdn.net/api/skin/";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    /** Each name asked for so far: its texture, or empty while loading or after failing. */
    private static final Map<String, Optional<Identifier>> FACES = new HashMap<>();
    private static int next;

    private Heads() {}

    /** A player's face texture, or null while it loads or if there is none. Call on the client thread. */
    public static Identifier face(String name) {
        if (name == null || name.isEmpty()) return null;
        String key = name.toLowerCase(Locale.ROOT);
        Optional<Identifier> known = FACES.get(key);
        if (known != null) return known.orElse(null);
        FACES.put(key, Optional.empty());
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(System.getProperty("bageltv.heads", BASE) + Api.encode(name)
                            + "?size=32"))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", BagelTV.NAME + "/" + BagelTV.VERSION)
                    .build();
        } catch (IllegalArgumentException badName) {
            return null;
        }
        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).whenComplete((response, exception) -> {
            if (exception != null || response.statusCode() != 200) return;
            Minecraft.getInstance().execute(() -> {
                try {
                    NativeImage image = NativeImage.read(response.body());
                    if (image.getWidth() != 32 || image.getHeight() != 32) {
                        image.close();
                        return;
                    }
                    Identifier id = Identifier.fromNamespaceAndPath(BagelTV.ID, "head/" + next++);
                    Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "Head of "
                            + name, image));
                    FACES.put(key, Optional.of(id));
                } catch (Exception unreadable) {
                    // Not an image; the placeholder stays.
                }
            });
        });
        return null;
    }

    /** How many faces have loaded, for tests. */
    public static long loaded() {
        return FACES.values().stream().filter(Optional::isPresent).count();
    }
}
