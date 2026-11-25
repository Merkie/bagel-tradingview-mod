package com.merkie.bageltv;

import com.merkie.bageltv.ui.Items;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Not a test: draws every item the terminal can show, at every size it can show it, for anything outside the
 * game that wants the game's own icons. It does nothing unless the environment names a folder in
 * {@code BAGELTV_ICONS}; then it puts screenshots named {@code icons-SIZE-PAGE} there, with {@code icons.json},
 * which says which item is where, what it is called and what {@link Items#color} makes of it.
 *
 * <p>Each screenshot has the same items twice, on black on the left and on white on the right, so whoever cuts
 * them out can tell how see-through each pixel is.
 */
public final class IconSheetGameTest implements FabricClientGameTest {
    private static final int WIDTH = 1920, HEIGHT = 1080;
    private static final int[] SIZES = {8, 16, 24, 32, 40, 48, 64, 80, 96, 128, 160, 192};

    @Override
    public void runTest(ClientGameTestContext context) {
        String folder = System.getenv("BAGELTV_ICONS");
        if (folder == null || folder.isEmpty()) return;
        Path out = Path.of(folder);
        // Items only know what they are once a world has loaded.
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            Files.createDirectories(out);
            context.getInput().resizeWindow(WIDTH, HEIGHT);
            context.waitTicks(5);
            List<String> keys = new ArrayList<>();
            for (var id : BuiltInRegistries.ITEM.keySet()) {
                if (!id.getPath().equals("air")) keys.add(id.getPath());
            }
            keys.sort(null);
            List<String> potions = new ArrayList<>();
            for (var id : BuiltInRegistries.POTION.keySet()) potions.add(id.getPath());
            potions.sort(null);
            for (String material : new String[] {"potion", "splash_potion", "lingering_potion", "tipped_arrow"}) {
                for (String potion : potions) keys.add(material + "[" + potion + "]");
            }
            List<ItemStack> stacks = new ArrayList<>();
            StringBuilder json = new StringBuilder("{\"width\":" + WIDTH + ",\"height\":" + HEIGHT + ",\"items\":[");
            context.runOnClient(client -> {
                for (int i = 0; i < keys.size(); i++) {
                    ItemStack stack = Items.stack(keys.get(i));
                    stacks.add(stack);
                    json.append(i == 0 ? "" : ",").append("\n{\"key\":\"").append(keys.get(i)).append("\",\"name\":\"")
                            .append(stack.getHoverName().getString().replace("\\", "\\\\").replace("\"", "\\\""))
                            .append("\",\"color\":").append(Items.color(keys.get(i)) & 0xFFFFFF).append('}');
                }
            });
            json.append("],\n\"sizes\":[");
            for (int s = 0; s < SIZES.length; s++) {
                int size = SIZES[s], pad = Math.max(2, size / 8), pitch = size + 2 * pad;
                int columns = WIDTH / 2 / pitch, rows = HEIGHT / pitch, each = columns * rows;
                int pages = (stacks.size() + each - 1) / each;
                json.append(s == 0 ? "" : ",").append("\n{\"size\":").append(size).append(",\"pad\":").append(pad)
                        .append(",\"pitch\":").append(pitch).append(",\"columns\":").append(columns)
                        .append(",\"rows\":").append(rows).append(",\"pages\":").append(pages).append('}');
                for (int page = 0; page < pages; page++) {
                    int from = page * each;
                    List<ItemStack> shown = stacks.subList(from, Math.min(stacks.size(), from + each));
                    context.runOnClient(client -> client.gui.setScreen(new Sheet(shown, size, pad, pitch, columns)));
                    context.waitTicks(3);
                    context.takeScreenshot(TestScreenshotOptions.of(String.format("icons-%03d-%03d", size, page))
                            .disableCounterPrefix().withDestinationDir(out));
                }
            }
            json.append("]}\n");
            context.runOnClient(client -> client.gui.setScreen(null));
            context.getInput().resizeWindow(854, 480);
            context.waitTicks(5);
            Files.writeString(out.resolve("icons.json"), json);
            Harness.LOG.info("Wrote {} icons to {}", stacks.size(), out);
        } catch (Exception exception) {
            throw new AssertionError("IconSheetGameTest failed", exception);
        }
    }

    /** A page of items in rows, once over black and once over white, in window pixels whatever the GUI scale. */
    private static final class Sheet extends Screen {
        private final List<ItemStack> stacks;
        private final int size, pad, pitch, columns;

        Sheet(List<ItemStack> stacks, int size, int pad, int pitch, int columns) {
            super(Component.literal("Icons"));
            this.stacks = stacks;
            this.size = size;
            this.pad = pad;
            this.pitch = pitch;
            this.columns = columns;
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            float pixel = 1F / minecraft.getWindow().getGuiScale();
            graphics.pose().pushMatrix();
            graphics.pose().scale(pixel, pixel);
            graphics.fill(0, 0, WIDTH / 2, HEIGHT, 0xFF000000);
            graphics.fill(WIDTH / 2, 0, WIDTH, HEIGHT, 0xFFFFFFFF);
            for (int half = 0; half < 2; half++) {
                for (int i = 0; i < stacks.size(); i++) {
                    graphics.pose().pushMatrix();
                    graphics.pose().translate(half * WIDTH / 2 + i % columns * pitch + pad, i / columns * pitch + pad);
                    graphics.pose().scale(size / 16F, size / 16F);
                    graphics.fakeItem(stacks.get(i), 0, 0);
                    graphics.pose().popMatrix();
                }
            }
            graphics.pose().popMatrix();
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}
