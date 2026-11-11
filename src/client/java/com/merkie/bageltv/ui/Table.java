package com.merkie.bageltv.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A scrolling table: a header whose sortable titles click, rows that highlight under the mouse and click, and a
 * scroll bar. Columns have fixed widths except those given a share of the room left over; the least important
 * columns drop out when the table is narrow.
 */
public final class Table<R> implements Interactive {
    public interface Cell<R> {
        void draw(Ui ui, R row, int x, int y, int w, int h);
    }

    public static final class Col<R> {
        final String title;
        final int width;
        final Cell<R> cell;
        boolean right = true;
        int flex;
        String sort;
        String tip;
        /** Columns with a higher number drop out first when the table is too narrow; 0 always stays. */
        int drop;

        Col(String title, int width, Cell<R> cell) {
            this.title = title;
            this.width = width;
            this.cell = cell;
        }

        public Col<R> left() {
            right = false;
            return this;
        }

        public Col<R> flex(int share) {
            flex = share;
            return this;
        }

        public Col<R> sort(String key) {
            sort = key;
            return this;
        }

        public Col<R> tip(String text) {
            tip = text;
            return this;
        }

        public Col<R> drop(int order) {
            drop = order;
            return this;
        }
    }

    public static final int HEADER = 13;
    private final List<Col<R>> cols = new ArrayList<>();
    private final List<Col<R>> shown = new ArrayList<>();
    public final int rowHeight;
    /** Pixels scrolled. */
    public int scroll;
    private int x, y, w, h, contentHeight;
    private boolean draggingBar;
    /** Whether the last rows are in view, for loading more. */
    public boolean nearEnd;
    public String empty = "Nothing to show";

    public Table(int rowHeight) {
        this.rowHeight = rowHeight;
    }

    public Col<R> col(String title, int width, Cell<R> cell) {
        Col<R> col = new Col<>(title, width, cell);
        cols.add(col);
        return col;
    }

    /** Text against the right edge of a cell. */
    public static void right(Ui ui, String text, int x, int y, int w, int h, int color) {
        ui.textRight(text, x + w - 4, Ui.textY(y, h), color);
    }

    public static void left(Ui ui, String text, int x, int y, int w, int h, int color) {
        ui.text(ui.fit(text, w - 6), x + 4, Ui.textY(y, h), color);
    }

    /** The middle of a column's title in terminal pixels, for tests, or null if the column is not showing. */
    public int[] header(String title) {
        int cx = x + 1;
        for (int i = 0; i < shown.size(); i++) {
            if (shown.get(i).title.equals(title)) return new int[] {cx + lastWidths[i] / 2, y + 6};
            cx += lastWidths[i];
        }
        return null;
    }

    /** The middle of a row in terminal pixels, for tests. */
    public int[] row(int index) {
        return new int[] {x + 60, y + HEADER + 1 + index * rowHeight - scroll + rowHeight / 2};
    }

    /** The left edge of a cell and the middle of its row in terminal pixels, for tests. */
    public int[] cell(int index, String title) {
        int[] middle = header(title);
        int at = 0;
        for (int i = 0; i < shown.size(); i++) if (shown.get(i).title.equals(title)) at = i;
        return new int[] {middle[0] - lastWidths[at] / 2, row(index)[1]};
    }

    private int[] lastWidths = new int[0];

    private int viewHeight() {
        return h - HEADER - 2;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - viewHeight());
    }

    public void render(Ui ui, int x, int y, int w, int h, List<R> rows, String sort, boolean descending,
                       Consumer<String> onSort, Consumer<R> onClick, java.util.function.Function<R, List<String>> tip) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        contentHeight = rows.size() * rowHeight;
        scroll = Math.clamp(scroll, 0, maxScroll());
        boolean bar = maxScroll() > 0;
        int inner = w - 2 - (bar ? 6 : 0);

        // Which columns fit, dropping the least important first.
        shown.clear();
        shown.addAll(cols);
        while (true) {
            int need = 0;
            for (Col<R> col : shown) need += col.width;
            if (need <= inner) break;
            Col<R> worst = null;
            for (Col<R> col : shown) if (col.drop > 0 && (worst == null || col.drop > worst.drop)) worst = col;
            if (worst == null) break;
            shown.remove(worst);
        }
        int fixed = 0, flex = 0;
        for (Col<R> col : shown) {
            fixed += col.width;
            flex += col.flex;
        }
        int spare = Math.max(0, inner - fixed);

        ui.box(x, y, w, h, Theme.BG, Theme.BORDER);
        ui.fill(x + 1, y + 1, w - 2, HEADER - 1, Theme.RAISED);
        ui.hline(x + 1, y + HEADER, w - 2, Theme.BORDER);
        ui.interactive(x, y, w, h, this);

        int cx = x + 1;
        int given = 0, flexSeen = 0;
        int[] widths = new int[shown.size()];
        lastWidths = widths;
        for (int i = 0; i < shown.size(); i++) {
            Col<R> col = shown.get(i);
            int extra = 0;
            if (col.flex > 0 && flex > 0) {
                flexSeen += col.flex;
                extra = flexSeen == flex ? spare - given : spare * col.flex / flex;
                given += extra;
            }
            widths[i] = col.width + extra;
            boolean sorted = col.sort != null && col.sort.equals(sort);
            boolean over = col.sort != null && onSort != null
                    && ui.click(cx, y + 1, widths[i], HEADER - 1, () -> onSort.accept(col.sort));
            if (over) ui.fill(cx, y + 1, widths[i], HEADER - 1, Theme.HOVER);
            if (ui.hover(cx, y + 1, widths[i], HEADER - 1) && col.tip != null) ui.tip(col.tip);
            int color = sorted ? Theme.ACCENT : over ? Theme.TEXT : Theme.MUTED;
            int arrow = sorted ? 8 : 0;
            String title = ui.fit(col.title, widths[i] - 6 - arrow);
            if (col.right) {
                ui.textRight(title, cx + widths[i] - 4, y + 3, color);
                if (sorted) ui.icon(descending ? Icon.SORT_DESC : Icon.SORT_ASC,
                        cx + widths[i] - 4 - ui.width(title) - 8, y + 2, color);
            } else {
                int end = cx + 4 + ui.text(title, cx + 4, y + 3, color);
                if (sorted) ui.icon(descending ? Icon.SORT_DESC : Icon.SORT_ASC, end + 2, y + 2, color);
            }
            cx += widths[i];
        }

        int top = y + HEADER + 1, view = viewHeight();
        if (rows.isEmpty()) {
            ui.textCenter(empty, x + w / 2, top + Math.min(view / 2, 30) - 4, Theme.FAINT);
            nearEnd = false;
            return;
        }
        ui.clip(x + 1, top, w - 2, view);
        int first = scroll / rowHeight;
        int last = Math.min(rows.size() - 1, (scroll + view) / rowHeight);
        nearEnd = last >= rows.size() - 3;
        for (int i = first; i <= last; i++) {
            R row = rows.get(i);
            int ry = top + i * rowHeight - scroll;
            boolean over = onClick != null ? ui.click(x + 1, ry, inner, rowHeight, () -> onClick.accept(row))
                    : ui.hover(x + 1, ry, inner, rowHeight);
            if (over) {
                ui.fill(x + 1, ry, inner, rowHeight, Theme.HOVER);
                if (tip != null) ui.tip(tip.apply(row));
            }
            if (i > first || scroll % rowHeight == 0) ui.hline(x + 1, ry + rowHeight - 1, inner, Theme.GRID);
            cx = x + 1;
            for (int c = 0; c < shown.size(); c++) {
                shown.get(c).cell.draw(ui, row, cx, ry, widths[c], rowHeight - 1);
                cx += widths[c];
            }
        }
        ui.unclip();
        if (bar) {
            int trackX = x + w - 6;
            int thumb = Math.max(10, view * view / contentHeight);
            int thumbY = top + (view - thumb) * scroll / maxScroll();
            ui.fill(trackX, top, 5, view, Theme.PANEL);
            boolean hot = draggingBar || ui.hover(trackX - 1, top, 7, view);
            ui.fill(trackX + 1, thumbY, 3, thumb, hot ? Theme.ACCENT : Theme.BORDER_STRONG);
        }
    }

    @Override
    public boolean mouseDown(int px, int py, int button, boolean shift, boolean doubleClick) {
        if (maxScroll() > 0 && px >= x + w - 7 && py > y + HEADER) {
            draggingBar = true;
            barTo(py);
            return true;
        }
        return false;
    }

    @Override
    public void mouseDrag(int px, int py) {
        if (draggingBar) barTo(py);
    }

    @Override
    public void mouseUp(int px, int py) {
        draggingBar = false;
    }

    private void barTo(int py) {
        int view = viewHeight();
        int thumb = Math.max(10, view * view / Math.max(1, contentHeight));
        double share = (py - (y + HEADER + 1) - thumb / 2.0) / Math.max(1, view - thumb);
        scroll = (int) Math.round(Math.clamp(share, 0.0, 1.0) * maxScroll());
    }

    @Override
    public boolean scroll(int px, int py, double dx, double dy) {
        if (dy == 0 || maxScroll() == 0) return false;
        // A trackpad sends many small steps, so each moves at least a pixel.
        int step = Math.max(1, (int) Math.round(Math.abs(dy) * rowHeight * 2));
        scroll = Math.clamp(scroll - (int) Math.signum(dy) * step, 0, maxScroll());
        return true;
    }
}
