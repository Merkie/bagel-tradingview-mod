package com.merkie.bageltv.ui;

import com.merkie.bageltv.data.Api;
import com.mojang.blaze3d.platform.cursor.CursorType;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * One frame's drawing and input surface, in terminal pixels. Views draw with it and register the regions that
 * take clicks; the screen then routes the mouse to the last region registered under it.
 */
public final class Ui {
    /** Far outside any region, where the mouse is while something covers a layer. */
    private static final int NOWHERE = -100000;

    public GuiGraphics g;
    public Font font;
    public int width;
    public int height;
    public int mx;
    public int my;
    /** Milliseconds, for blinking and spinners. */
    public long now;
    public Nav nav;
    final List<Hit> hits = new ArrayList<>();
    private final int[] clips = new int[4 * 16];
    private int clipDepth;
    private final List<String> tip = new ArrayList<>();
    private CursorType cursor;
    private int realMx;
    private int realMy;
    TextField focus;
    /** Drawn after everything else this frame, in order: popovers, menus, a maximized chart. */
    private final List<Runnable> later = new ArrayList<>();
    /** Where this frame's popovers are, which hides the mouse from what is under them next frame. */
    private final List<int[]> popups = new ArrayList<>();
    private final List<int[]> lastPopups = new ArrayList<>();
    /** The open dropdown or popover, by the object that owns it, or null. */
    public Object openMenu;
    /** The view's box inside the panel, which a maximized chart fills. */
    public int contentX, contentY, contentW, contentH;
    /** Named points drawn this frame, so tests can find controls. */
    private final java.util.Map<String, int[]> marks = new java.util.HashMap<>();

    /** Where views send the user. */
    public interface Nav {
        void item(String key);

        void player(String name);

        void go(Route route);
    }

    static final class Hit {
        int x, y, w, h;
        Runnable click;
        Interactive target;
        TextField field;

        boolean contains(int px, int py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    void begin(GuiGraphics graphics, Font font, int width, int height, int mouseX, int mouseY) {
        this.g = graphics;
        this.font = font;
        this.width = width;
        this.height = height;
        this.realMx = mouseX;
        this.realMy = mouseY;
        this.mx = mouseX;
        this.my = mouseY;
        this.now = System.currentTimeMillis();
        hits.clear();
        clipDepth = 0;
        tip.clear();
        cursor = null;
        later.clear();
        lastPopups.clear();
        lastPopups.addAll(popups);
        popups.clear();
        marks.clear();
        regions = 0;
    }

    /** The startup animation while it plays; parts of the terminal ask it how far they have appeared. */
    Intro intro;
    private int regions;
    private int regionX, regionY, regionW, regionH, regionCover;
    private float regionShown;

    /** Starts a part of the terminal that appears on its own during the startup animation. */
    public void reveal(int x, int y, int w, int h) {
        reveal(x, y, w, h, Theme.PANEL, 4);
    }

    /**
     * What is drawn until {@link #revealed} rises {@code slide} pixels into place and comes out from under a
     * cover of the color behind it. Outside the startup animation this does nothing.
     */
    public void reveal(int x, int y, int w, int h, int cover, int slide) {
        if (intro == null) return;
        regionShown = intro.region(regions++);
        regionX = x;
        regionY = y;
        regionW = w;
        regionH = h;
        regionCover = cover;
        g.pose().pushMatrix();
        g.pose().translate(0, Math.round((1 - regionShown) * slide));
    }

    public void revealed() {
        if (intro == null) return;
        if (regionShown < 1) {
            g.nextStratum();
            fill(regionX, regionY, regionW, regionH, Theme.alpha(regionCover, Math.round((1 - regionShown) * 255)));
        }
        g.pose().popMatrix();
    }

    /** Whether the mouse is over a popover drawn last frame. */
    boolean overPopup() {
        for (int[] box : lastPopups) {
            if (realMx >= box[0] && realMx < box[0] + box[2] && realMy >= box[1] && realMy < box[1] + box[3]) {
                return true;
            }
        }
        return false;
    }

    /** Draws something after the rest of the frame, on top of it. */
    public void later(Runnable draw) {
        later.add(draw);
    }

    void runLater() {
        // A deferred draw can defer more, such as a menu inside a maximized chart.
        for (int i = 0; i < later.size(); i++) {
            clipDepth = 0;
            later.get(i).run();
        }
    }

    /** A floating box with a shadow that swallows clicks. Call from {@link #later}. */
    public void popup(int x, int y, int w, int h, boolean shadow) {
        g.nextStratum();
        if (shadow) fill(x + 3, y + 3, w, h, Theme.SHADOW);
        box(x, y, w, h, Theme.RAISED, Theme.BORDER_STRONG);
        shield(x, y, w, h);
        popups.add(new int[] {x, y, w, h});
    }

    /** Covers the screen with a region that runs an action on a click, under a popover drawn next. */
    public void closer(Runnable action) {
        Hit hit = new Hit();
        hit.w = width;
        hit.h = height;
        hit.click = action;
        hits.add(hit);
    }

    public void mark(String name, int x, int y) {
        marks.put(name, new int[] {x, y});
    }

    /** Where a named control was drawn this frame, for tests. */
    public int[] marked(String name) {
        int[] at = marks.get(name);
        if (at == null) throw new IllegalStateException("Nothing named " + name + " on screen: " + marks.keySet());
        return at;
    }

    /**
     * A button showing the picked choice that opens a list of the others under it. {@code owner} tells this
     * dropdown from every other. Returns its width.
     */
    public int dropdown(Object owner, int x, int y, String[] labels, int selected, IntConsumer pick) {
        String label = labels[Math.clamp(selected, 0, labels.length - 1)];
        int w = font.width(label) + 17;
        boolean open = openMenu == owner;
        boolean over = click(x, y, w, 13, () -> openMenu = open ? null : owner);
        if (open || over) fill(x, y, w, 13, open ? Theme.SELECTED : Theme.HOVER);
        text(label, x + 4, y + 3, open ? Theme.ACCENT : over ? Theme.TEXT : Theme.TEXT);
        int ax = x + w - 10, color = open ? Theme.ACCENT : Theme.MUTED;
        fill(ax, y + 5, 5, 1, color);
        fill(ax + 1, y + 6, 3, 1, color);
        fill(ax + 2, y + 7, 1, 1, color);
        mark("menu:" + label, x + w / 2, y + 6);
        if (!open) return w;
        later(() -> {
            int widest = w - 2;
            for (String option : labels) widest = Math.max(widest, font.width(option) + 12);
            int mw = widest + 2, mh = labels.length * 12 + 2;
            int mx0 = Math.min(x, width - mw - 2), my0 = y + 14;
            closer(() -> openMenu = null);
            popup(mx0, my0, mw, mh, true);
            for (int i = 0; i < labels.length; i++) {
                int index = i, ry = my0 + 1 + i * 12;
                boolean hot = click(mx0 + 1, ry, mw - 2, 12, () -> {
                    openMenu = null;
                    pick.accept(index);
                });
                if (hot || i == selected) fill(mx0 + 1, ry, mw - 2, 12, hot ? Theme.HOVER : Theme.SELECTED);
                text(labels[i], mx0 + 6, ry + 2, i == selected ? Theme.ACCENT : hot ? Theme.TEXT : Theme.MUTED);
                mark("option:" + labels[i], mx0 + mw / 2, ry + 6);
            }
        });
        return w;
    }

    /** A player's face, or a neutral square until it has loaded. */
    public void head(String name, int x, int y, int size) {
        net.minecraft.resources.Identifier face = Heads.face(name);
        if (face == null) {
            fill(x, y, size, size, Theme.BORDER);
            fill(x + size / 4, y + size / 4, size / 2, size / 2, Theme.BORDER_STRONG);
            return;
        }
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, face, x, y, 0F, 0F, size, size, 32, 32,
                32, 32);
    }


    /** Hides the mouse from what is drawn next, or shows it again. */
    void block(boolean blocked) {
        mx = blocked ? NOWHERE : realMx;
        my = blocked ? NOWHERE : realMy;
    }

    // Drawing

    public void fill(int x, int y, int w, int h, int color) {
        if (w > 0 && h > 0) g.fill(x, y, x + w, y + h, color);
    }

    public void hline(int x, int y, int w, int color) {
        fill(x, y, w, 1, color);
    }

    public void vline(int x, int y, int h, int color) {
        fill(x, y, 1, h, color);
    }

    public void outline(int x, int y, int w, int h, int color) {
        hline(x, y, w, color);
        hline(x, y + h - 1, w, color);
        vline(x, y + 1, h - 2, color);
        vline(x + w - 1, y + 1, h - 2, color);
    }

    /** A filled box with a 1 px border. */
    public void box(int x, int y, int w, int h, int fill, int border) {
        fill(x + 1, y + 1, w - 2, h - 2, fill);
        outline(x, y, w, h, border);
    }

    public int text(String text, int x, int y, int color) {
        g.drawString(font, text, x, y, color, false);
        return font.width(text);
    }

    public void textRight(String text, int right, int y, int color) {
        g.drawString(font, text, right - font.width(text), y, color, false);
    }

    public void textCenter(String text, int center, int y, int color) {
        g.drawString(font, text, center - font.width(text) / 2, y, color, false);
    }

    /** Text drawn at a whole multiple of its size. */
    public void textScaled(String text, int x, int y, int scale, int color) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popMatrix();
    }

    public int width(String text) {
        return font.width(text);
    }

    public String fit(String text, int width) {
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width(".."))) + "..";
    }

    /** The y that centers a line of text in a row. */
    public static int textY(int y, int h) {
        return y + (h - 7) / 2;
    }

    public void icon(Icon icon, int x, int y, int color) {
        for (int row = 0; row < icon.height; row++) {
            String line = icon.rows[row];
            int start = -1;
            for (int column = 0; column <= icon.width; column++) {
                boolean on = column < icon.width && line.charAt(column) == 'X';
                if (on && start < 0) start = column;
                if (!on && start >= 0) {
                    g.fill(x + start, y + row, x + column, y + row + 1, color);
                    start = -1;
                }
            }
        }
    }

    /** An item's icon, 16 px, or a placeholder for a key this game version does not have. */
    public void item(String key, int x, int y) {
        ItemStack stack = Items.stack(key);
        if (stack.isEmpty()) {
            box(x + 2, y + 2, 12, 12, Theme.RAISED, Theme.BORDER);
            icon(Icon.UNKNOWN, x + 4, y + 4, Theme.FAINT);
            return;
        }
        g.renderFakeItem(stack, x, y);
    }

    /** An item's icon scaled from 16 px. */
    public void item(String key, int x, int y, float scale) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        item(key, 0, 0);
        g.pose().popMatrix();
    }

    // Clipping

    public void clip(int x, int y, int w, int h) {
        int x0 = x, y0 = y, x1 = x + w, y1 = y + h;
        if (clipDepth > 0) {
            int base = (clipDepth - 1) * 4;
            x0 = Math.max(x0, clips[base]);
            y0 = Math.max(y0, clips[base + 1]);
            x1 = Math.min(x1, clips[base + 2]);
            y1 = Math.min(y1, clips[base + 3]);
        }
        int base = clipDepth * 4;
        clips[base] = x0;
        clips[base + 1] = y0;
        clips[base + 2] = Math.max(x0, x1);
        clips[base + 3] = Math.max(y0, y1);
        clipDepth++;
        g.enableScissor(x0, y0, Math.max(x0, x1), Math.max(y0, y1));
    }

    public void unclip() {
        clipDepth--;
        g.disableScissor();
    }

    private boolean inClip(int px, int py) {
        if (clipDepth == 0) return true;
        int base = (clipDepth - 1) * 4;
        return px >= clips[base] && py >= clips[base + 1] && px < clips[base + 2] && py < clips[base + 3];
    }

    // Input

    public boolean hover(int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h && inClip(mx, my);
    }

    private Hit hit(int x, int y, int w, int h) {
        Hit hit = new Hit();
        if (clipDepth > 0) {
            int base = (clipDepth - 1) * 4;
            int x0 = Math.max(x, clips[base]), y0 = Math.max(y, clips[base + 1]);
            int x1 = Math.min(x + w, clips[base + 2]), y1 = Math.min(y + h, clips[base + 3]);
            hit.x = x0;
            hit.y = y0;
            hit.w = Math.max(0, x1 - x0);
            hit.h = Math.max(0, y1 - y0);
        } else {
            hit.x = x;
            hit.y = y;
            hit.w = w;
            hit.h = h;
        }
        if (mx != NOWHERE) hits.add(hit);
        return hit;
    }

    /** Makes a region clickable. Returns whether the mouse is over it, and shows the hand if so. */
    public boolean click(int x, int y, int w, int h, Runnable action) {
        hit(x, y, w, h).click = action;
        boolean over = hover(x, y, w, h);
        if (over) cursor = CursorTypes.POINTING_HAND;
        return over;
    }

    /** Hands a region's presses, drags and scrolling to a widget. */
    public void interactive(int x, int y, int w, int h, Interactive target) {
        hit(x, y, w, h).target = target;
    }

    /** Swallows clicks in a region so nothing under it gets them. */
    public void shield(int x, int y, int w, int h) {
        hit(x, y, w, h).click = () -> {};
    }

    public void cursor(CursorType type) {
        cursor = type;
    }

    // Tooltip

    /** This frame's tooltip. A line of "Label\tvalue" puts the value in a right-hand column. */
    public void tip(String... lines) {
        tip.clear();
        for (String line : lines) if (line != null) tip.add(line);
    }

    public void tip(List<String> lines) {
        tip.clear();
        tip.addAll(lines);
    }

    void end() {
        if (cursor != null) g.requestCursor(cursor);
        if (tip.isEmpty() || realMx < 0) return;
        int left = 0, right = 0;
        for (String line : tip) {
            int tab = line.indexOf('\t');
            if (tab < 0) left = Math.max(left, font.width(line));
            else {
                left = Math.max(left, font.width(line.substring(0, tab)));
                right = Math.max(right, font.width(line.substring(tab + 1)));
            }
        }
        boolean columns = right > 0;
        int inner = columns ? Math.max(left + 8 + right, widest()) : left;
        int w = inner + 10, h = tip.size() * 10 + 7;
        int x = realMx + 10, y = realMy + 12;
        if (x + w > width - 2) x = Math.max(2, realMx - w - 6);
        if (y + h > height - 2) y = Math.max(2, realMy - h - 6);
        g.nextStratum();
        fill(x + 2, y + 2, w, h, 0x60000000);
        box(x, y, w, h, Theme.RAISED, Theme.BORDER_STRONG);
        int ty = y + 5;
        for (int i = 0; i < tip.size(); i++, ty += 10) {
            String line = tip.get(i);
            int tab = line.indexOf('\t');
            if (tab < 0) {
                text(line, x + 5, ty, i == 0 ? Theme.TEXT : Theme.MUTED);
            } else {
                text(line.substring(0, tab), x + 5, ty, Theme.MUTED);
                textRight(line.substring(tab + 1), x + w - 5, ty, Theme.TEXT);
            }
        }
    }

    private int widest() {
        int widest = 0;
        for (String line : tip) if (line.indexOf('\t') < 0) widest = Math.max(widest, font.width(line));
        return widest;
    }

    // Widgets

    /** A 13 px square icon button. */
    public boolean iconButton(Icon icon, int x, int y, boolean selected, String label, Runnable action) {
        return iconButton(icon, x, y, 13, 13, selected, true, label, action);
    }

    public boolean iconButton(Icon icon, int x, int y, int w, int h, boolean selected, boolean enabled, String label,
                              Runnable action) {
        boolean over = enabled && click(x, y, w, h, action);
        if (selected || over) fill(x, y, w, h, over ? Theme.HOVER : Theme.SELECTED);
        icon(icon, x + (w - icon.width) / 2, y + (h - icon.height) / 2,
                !enabled ? Theme.FAINT : selected ? Theme.ACCENT : over ? Theme.TEXT : Theme.MUTED);
        if (over && label != null) tip(label);
        return over;
    }

    /**
     * Buttons side by side, one of them picked, 13 px tall with a 1 px line between neighbors. Framed, it has
     * its own outline, for use on the panel; unframed it sits flush in a strip that already has borders.
     * Returns the width drawn.
     */
    public int segments(int x, int y, String[] labels, int selected, IntConsumer pick) {
        return segments(x, y, labels, selected, pick, true);
    }

    public int segments(int x, int y, String[] labels, int selected, IntConsumer pick, boolean framed) {
        int cx = x;
        for (int i = 0; i < labels.length; i++) {
            int w = font.width(labels[i]) + 8;
            int index = i;
            boolean over = click(cx, y, w, 13, () -> pick.accept(index));
            boolean on = i == selected;
            if (framed || on || over) fill(cx, y, w, 13, on ? Theme.SELECTED : over ? Theme.HOVER : Theme.RAISED);
            text(labels[i], cx + 4, y + 3, on ? Theme.ACCENT : over ? Theme.TEXT : Theme.MUTED);
            mark("segment:" + labels[i], cx + w / 2, y + 6);
            cx += w;
            if (i < labels.length - 1) {
                vline(cx, y, 13, Theme.BORDER);
                cx++;
            }
        }
        if (framed) outline(x - 1, y - 1, cx - x + 2, 15, Theme.BORDER);
        return cx - x;
    }

    /** The width {@link #segments} draws for these labels. */
    public int segmentsWidth(String[] labels) {
        int w = labels.length - 1;
        for (String label : labels) w += font.width(label) + 8;
        return w;
    }

    /** A text button with a frame. Returns its width. */
    public int button(String label, int x, int y, boolean selected, Runnable action) {
        int w = font.width(label) + 10;
        boolean over = click(x, y, w, 13, action);
        box(x, y, w, 13, selected ? Theme.SELECTED : over ? Theme.HOVER : Theme.RAISED,
                selected ? Theme.ACCENT : over ? Theme.BORDER_STRONG : Theme.BORDER);
        text(label, x + 5, y + 3, selected ? Theme.ACCENT : over ? Theme.TEXT : Theme.MUTED);
        return w;
    }

    /** A text box. Clicking it takes the keyboard. */
    public void field(TextField field, int x, int y, int w, Icon icon) {
        boolean focused = focus == field;
        Hit hit = hit(x, y, w, 13);
        hit.field = field;
        boolean over = hover(x, y, w, 13);
        if (over) cursor = CursorTypes.IBEAM;
        box(x, y, w, 13, Theme.BG, focused ? Theme.ACCENT : over ? Theme.BORDER_STRONG : Theme.BORDER);
        int tx = x + 4;
        if (icon != null) {
            icon(icon, tx, y + 3, focused ? Theme.ACCENT : Theme.FAINT);
            tx += icon.width + 4;
        }
        int room = x + w - 4 - tx;
        if (field.text.isEmpty() && !focused) {
            text(fit(field.hint, room), tx, y + 3, Theme.FAINT);
            return;
        }
        String shown = field.text;
        while (font.width(shown) > room - 2 && !shown.isEmpty()) shown = shown.substring(1);
        int end = tx + text(shown, tx, y + 3, Theme.TEXT);
        if (focused && now / 500 % 2 == 0) vline(end + 1, y + 2, 9, Theme.TEXT);
    }

    /** An 8-step pixel spinner, 7 x 7. */
    public void spinner(int x, int y) {
        int[][] dots = {{3, 0}, {5, 1}, {6, 3}, {5, 5}, {3, 6}, {1, 5}, {0, 3}, {1, 1}};
        int step = (int) (now / 90 % 8);
        for (int i = 0; i < 8; i++) {
            int age = (step - i + 8) % 8;
            int color = age == 0 ? Theme.ACCENT : age < 3 ? Theme.mix(Theme.ACCENT, Theme.BORDER, age / 3F)
                    : Theme.BORDER;
            fill(x + dots[i][0], y + dots[i][1], 1, 1, color);
        }
    }

    /** Placeholder rows while the first answer is on its way. */
    public void skeleton(int x, int y, int w, int h) {
        float pulse = (float) (0.5 + 0.5 * Math.sin(now / 320.0));
        int color = Theme.mix(Theme.RAISED, Theme.HOVER, pulse * 0.6F);
        int[] widths = {72, 54, 86, 40, 64, 78, 48};
        int row = 0;
        for (int ry = y + 6; ry + 5 <= y + h - 4 && row < 12; ry += 12, row++) {
            fill(x + 6, ry, Math.min(w - 12, w * widths[row % widths.length] / 100), 5, color);
        }
        spinner(x + w - 13, y + 5);
    }

    /** A short error with a way to try again. */
    public void error(int x, int y, int w, int h, String message, Runnable retry) {
        int cy = y + h / 2;
        boolean roomy = h >= 44;
        if (roomy) icon(Icon.WARNING, x + w / 2 - 3, cy - 20, Theme.WARN);
        textCenter(fit(message, w - 8), x + w / 2, roomy ? cy - 8 : cy - 11, Theme.MUTED);
        int bw = font.width("Retry") + 10;
        button("Retry", x + w / 2 - bw / 2, roomy ? cy + 5 : cy + 1, false, retry);
    }

    /**
     * Whether a request has data to draw. If not, draws its loading or error state in the box and returns false.
     * A refresh that fails keeps showing the data it had.
     */
    public boolean ready(Api.Query<?> query, int x, int y, int w, int h) {
        if (query.data != null) return true;
        if (query.error != null) error(x, y, w, h, query.error, query::retry);
        else skeleton(x, y, w, h);
        return false;
    }
}
