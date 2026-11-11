package com.merkie.bageltv.ui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** The API's item keys as item stacks to draw, and each item's own color for chart lines. */
public final class Items {
    private static final Map<String, ItemStack> STACKS = new HashMap<>();
    private static final Map<String, Integer> COLORS = new HashMap<>();
    /** How bright a color's strongest channel is made, so dark items still show on the dark panel. */
    private static final double BRIGHT = 215;

    private Items() {}

    /** The stack for a key such as {@code water_bucket}, or an empty stack if this version has no such item. */
    public static ItemStack stack(String key) {
        return STACKS.computeIfAbsent(key == null ? "" : key, Items::find);
    }

    /**
     * A plain key is its item. A variant key such as {@code potion[strong_strength]} is its material, and a
     * potion, splash or lingering potion or tipped arrow also carries its potion, so it shows that potion's color.
     */
    private static ItemStack find(String key) {
        int bracket = key.indexOf('[');
        String material = bracket < 0 ? key : key.substring(0, bracket);
        Identifier id = Identifier.tryParse(material.indexOf(':') < 0 ? "minecraft:" + material : material);
        if (id == null) return ItemStack.EMPTY;
        ItemStack stack = BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
        if (stack.isEmpty() || bracket < 0 || !key.endsWith("]")) return stack;
        if (material.endsWith("potion") || material.equals("tipped_arrow")) {
            Identifier potion = Identifier.tryParse("minecraft:" + key.substring(bracket + 1, key.length() - 1));
            if (potion != null) {
                BuiltInRegistries.POTION.get(potion).ifPresent(holder -> stack.set(
                        net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                        new net.minecraft.world.item.alchemy.PotionContents(holder)));
            }
        }
        return stack;
    }

    /**
     * An item's color: the average of its texture, leaning toward its most colorful pixels (a water bucket is
     * blue, not the gray of its bucket), brightened for the dark panel. Items come by their model's own texture
     * and blocks by their particle texture. An item with no readable texture gets a palette color from its key.
     */
    public static int color(String key) {
        Integer cached = COLORS.get(key);
        if (cached != null) return cached;
        int color = read(key);
        COLORS.put(key, color);
        return color;
    }

    /** Forgets the colors, for when resource packs change. */
    public static void clearColors() {
        COLORS.clear();
    }

    private static int read(String key) {
        ItemStack stack = stack(key);
        if (!stack.isEmpty()) {
            Minecraft client = Minecraft.getInstance();
            try {
                ItemStackRenderState state = new ItemStackRenderState();
                client.getItemModelResolver().updateForTopItem(state, stack, ItemDisplayContext.GUI, null, null, 0);
                var material = state.pickParticleMaterial(RandomSource.create(42));
                if (material != null) {
                    Identifier sprite = material.sprite().contents().name();
                    try (InputStream in = client.getResourceManager()
                            .open(sprite.withPath(path -> "textures/" + path + ".png"));
                         NativeImage image = NativeImage.read(in)) {
                        int color = average(image);
                        if (color != 0) return color;
                    }
                }
            } catch (Exception ignored) {
                // No texture to read; fall through to the palette.
            }
        }
        return Theme.PALETTE[Math.floorMod(key == null ? 0 : key.hashCode() * 31, Theme.PALETTE.length)];
    }

    private static int average(NativeImage image) {
        // An animated texture's frames stack down it; the first is enough.
        int height = Math.min(image.getHeight(), image.getWidth());
        double red = 0, green = 0, blue = 0, total = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getPixel(x, y);
                if (ARGB.alpha(pixel) < 128) continue;
                int r = ARGB.red(pixel), g = ARGB.green(pixel), b = ARGB.blue(pixel);
                int max = Math.max(r, Math.max(g, b));
                double saturation = max == 0 ? 0 : (max - Math.min(r, Math.min(g, b))) / (double) max;
                double weight = 1 + 24 * saturation * saturation;
                red += r * weight;
                green += g * weight;
                blue += b * weight;
                total += weight;
            }
        }
        if (total == 0) return 0;
        red /= total;
        green /= total;
        blue /= total;
        double max = Math.max(red, Math.max(green, blue));
        if (max < 1) return 0xFFC8C8C8;
        double scale = Math.max(1, BRIGHT / max);
        red *= scale;
        green *= scale;
        blue *= scale;
        // Lift very dark channels a little so a deep blue or red still reads against navy.
        double floor = 40;
        return ARGB.color(255, channel(Math.max(red, floor)), channel(Math.max(green, floor)),
                channel(Math.max(blue, floor)));
    }

    private static int channel(double value) {
        return (int) Math.min(255, Math.round(value));
    }
}
