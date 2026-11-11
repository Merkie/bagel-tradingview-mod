package com.merkie.bageltv.ui;

import com.merkie.bageltv.BagelTV;
import com.merkie.bageltv.Config;
import com.merkie.bageltv.Fmt;
import com.merkie.bageltv.data.Api;
import com.merkie.bageltv.data.Models;
import com.merkie.bageltv.view.EconomyView;
import com.merkie.bageltv.view.ItemView;
import com.merkie.bageltv.view.MarketView;
import com.merkie.bageltv.view.OrdersView;
import com.merkie.bageltv.view.PlayerView;
import com.merkie.bageltv.view.PlayersView;
import com.merkie.bageltv.view.ScreenerView;
import com.merkie.bageltv.view.View;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.sdl.SDLMouse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The terminal: a dark panel over the dimmed game with a title bar, a rail of views and the view itself. It draws
 * in its own pixel size (see {@link #scale}), independent of Minecraft's GUI scale, so the charts get the room a
 * trading screen needs and every pixel still lands on the screen's grid.
 */
public final class TerminalScreen extends Screen implements Ui.Nav {
    private static final int RAIL = 72;
    private static final int TITLE = Theme.TITLE_BAR;
    private static final int MAX_RECENT = 8;

    // Where the player is and has been, kept while the game runs so reopening returns to the same place.
    private static Route route = Route.of(Route.Kind.MARKET);
    private static final List<Route> BACK = new ArrayList<>();
    private static final List<Route> FORWARD = new ArrayList<>();
    private static final List<Route> RECENT = new ArrayList<>();
    /** Names the API gave for item keys, so a variant reads as "Mending 1 Book" in the rail. */
    public static final Map<String, String> NAMES = new java.util.HashMap<>();
    private static final Map<Route, View> VIEWS = new LinkedHashMap<>(16, 0.75F, true);

    /** The connection the terminal was last opened on, which tells a new login from a reopen. */
    private static java.lang.ref.WeakReference<Object> session;
    private static final Object NO_SERVER = new Object();

    private final Screen parent;
    /** The startup animation, while it plays. */
    private Intro intro;
    private boolean pointerHidden;
    private final Ui ui = new Ui();
    private final TextField search = new TextField("Search items and players", 32);
    private boolean settings;
    private boolean capturingKey;
    private String keyNote = "";
    private Interactive captured;
    private int panelX, panelY, panelW, panelH;
    private int grabX, grabY;
    private boolean draggingPanel;
    /** Search results, laid out when drawn so Enter can open the first. */
    private final List<Route> results = new ArrayList<>();
    private int dropX, dropY, dropW, dropH;

    public TerminalScreen(Screen parent) {
        super(Component.literal(BagelTV.NAME));
        this.parent = parent;
        // The first open after joining a server starts from the blank default state, behind the startup animation.
        var listener = net.minecraft.client.Minecraft.getInstance().getConnection();
        Object now = listener == null ? NO_SERVER : listener.getConnection();
        if (session == null || session.get() != now) {
            session = new java.lang.ref.WeakReference<>(now);
            reset();
            com.merkie.bageltv.chart.Chart.restore();
            if (Intro.enabled) intro = new Intro();
        }
        search.submit = () -> {
            if (!results.isEmpty()) go(results.getFirst());
            else if (search.text.trim().length() >= 3) go(Route.player(search.text.trim()));
        };
    }

    public static Route route() {
        return route;
    }

    /** The view showing, for tests. */
    public static View view() {
        return VIEWS.get(route);
    }

    /** Forgets where the player was, for tests. */
    public static void reset() {
        route = Route.of(Route.Kind.MARKET);
        BACK.clear();
        FORWARD.clear();
        RECENT.clear();
        VIEWS.clear();
    }

    /** Makes the next open count as the first after a login, for tests. */
    public static void newSession() {
        session = null;
    }

    /** How many items and players the rail remembers, for tests. */
    public static int recent() {
        return RECENT.size();
    }

    /** The startup animation if it is playing, for tests. */
    public Intro intro() {
        return intro;
    }

    /** The panel's rectangle in terminal pixels (x, y, width, height), for tests. */
    public int[] bounds() {
        return new int[] {panelX, panelY, panelW, panelH};
    }

    /** Where title bar controls are in terminal pixels, for tests: "search", "settings", "fullscreen", "close". */
    public int[] control(String which) {
        return switch (which) {
            case "search" -> new int[] {searchAt + 30, panelY + 8};
            case "settings" -> new int[] {gearX + 7, panelY + 8};
            case "fullscreen" -> new int[] {gearX + 22, panelY + 8};
            default -> new int[] {gearX + 37, panelY + 8};
        };
    }

    /** The middle of a rail entry in terminal pixels, for tests. */
    public int[] rail(Route.Kind kind) {
        return new int[] {panelX + 30, panelY + TITLE + 3 + kind.ordinal() * 15 + 7};
    }

    // Scale and geometry

    /** Screen pixels per terminal pixel: the setting, or the largest that leaves room for the full layout. */
    public int scale() {
        int width = minecraft.getWindow().getWidth(), height = minecraft.getWindow().getHeight();
        int most = Math.max(1, Math.min(width / 480, height / 270));
        int chosen = BagelTV.config().scale;
        if (chosen > 0) return Math.min(chosen, most);
        int scale = 1;
        while (scale < most && width / (scale + 1) >= 760 && height / (scale + 1) >= 420) scale++;
        return scale;
    }

    private double toTerminal() {
        return (double) minecraft.getWindow().getGuiScale() / scale();
    }

    private int termWidth() {
        return minecraft.getWindow().getWidth() / scale();
    }

    private int termHeight() {
        return minecraft.getWindow().getHeight() / scale();
    }

    private void layout() {
        Config config = BagelTV.config();
        int tw = termWidth(), th = termHeight();
        if (config.fullscreen) {
            panelX = 0;
            panelY = 0;
            panelW = tw;
            panelH = th;
            return;
        }
        int marginX = Math.max(10, tw / 24), marginY = Math.max(8, th / 24);
        panelW = tw - 2 * marginX;
        panelH = th - 2 * marginY;
        config.offsetX = Math.clamp(config.offsetX, -marginX - panelW + 80, tw - marginX - 80);
        config.offsetY = Math.clamp(config.offsetY, -marginY, th - marginY - TITLE);
        panelX = marginX + config.offsetX;
        panelY = marginY + config.offsetY;
    }

    // Navigation

    @Override
    public void item(String key) {
        go(Route.item(key));
    }

    @Override
    public void player(String name) {
        go(Route.player(name));
    }

    @Override
    public void go(Route next) {
        closeOverlays();
        if (next.equals(route)) return;
        BACK.add(route);
        if (BACK.size() > 100) BACK.removeFirst();
        FORWARD.clear();
        show(next);
    }

    private void show(Route next) {
        route = next;
        com.merkie.bageltv.chart.Chart.restore();
        if (next.kind() == Route.Kind.ITEM || next.kind() == Route.Kind.PLAYER) {
            RECENT.remove(next);
            RECENT.addFirst(next);
            while (RECENT.size() > MAX_RECENT) RECENT.removeLast();
        }
    }

    private void back() {
        if (BACK.isEmpty()) return;
        closeOverlays();
        FORWARD.add(route);
        show(BACK.removeLast());
    }

    private void forward() {
        if (FORWARD.isEmpty()) return;
        closeOverlays();
        BACK.add(route);
        show(FORWARD.removeLast());
    }

    private void closeOverlays() {
        settings = false;
        capturingKey = false;
        keyNote = "";
        ui.focus = null;
        ui.openMenu = null;
        if (!search.text.isEmpty()) search.text = "";
    }

    private View current() {
        View view = VIEWS.get(route);
        if (view == null) {
            view = create(route);
            VIEWS.put(route, view);
            if (VIEWS.size() > 16) VIEWS.remove(VIEWS.keySet().iterator().next());
        }
        return view;
    }

    private static View create(Route route) {
        return switch (route.kind()) {
            case MARKET -> new MarketView();
            case SCREENER -> new ScreenerView();
            case ORDERS -> new OrdersView();
            case PLAYERS -> new PlayersView();
            case ECONOMY -> new EconomyView();
            case ITEM -> new ItemView(route.arg());
            case PLAYER -> new PlayerView(route.arg());
        };
    }

    // Drawing

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (intro != null && intro.done()) intro = null;
        // No pointer over the startup animation; it comes back as the animation ends or the screen closes.
        if ((intro != null) != pointerHidden) {
            pointerHidden = intro != null;
            if (pointerHidden) SDLMouse.SDL_HideCursor();
            else SDLMouse.SDL_ShowCursor();
        }
        // The startup animation plays over a lighter dimming, which deepens as the window opens.
        float dim = intro == null ? 1 : intro.dim();
        graphics.fillGradient(0, 0, width, height, Theme.alpha(Theme.DIM_TOP, Math.round(0xA0 * dim)),
                Theme.alpha(Theme.DIM_BOTTOM, Math.round(0xD0 * dim)));
        layout();
        double k = toTerminal();
        float draw = (float) (1 / k);
        graphics.pose().pushMatrix();
        graphics.pose().scale(draw, draw);
        ui.nav = this;
        ui.begin(graphics, font, termWidth(), termHeight(), (int) Math.floor(mouseX * k),
                (int) Math.floor(mouseY * k));

        ui.intro = intro;
        boolean searching = dropdownOpen();
        ui.block(intro != null || settings || ui.overPopup() || searching && ui.hover(dropX, dropY, dropW, dropH));
        // During the startup animation the panel is drawn unseen, so its data loads, then opens from the logo.
        int[] window = intro == null ? null : intro.window(panelX, panelY, panelW, panelH, panelX + 37, panelY + 4);
        if (intro != null) intro.backdrop(ui, panelX + panelW / 2, panelY + panelH / 2);
        if (window != null) {
            if (window[2] > 0 && !BagelTV.config().fullscreen) {
                ui.fill(window[0] + 4, window[1] + 4, window[2], window[3], Theme.SHADOW);
            }
            ui.clip(window[0], window[1], window[2], window[3]);
        }
        panel();
        if (window != null) {
            ui.unclip();
            if (window[2] > 0) {
                ui.g.nextStratum();
                ui.outline(window[0], window[1], window[2], window[3], Theme.BORDER_STRONG);
            }
        }
        ui.block(false);
        if (intro == null) ui.runLater();
        if (intro != null) intro.draw(ui, panelX, panelY, panelW, panelH, panelX + 37, panelY + 4);
        if (searching) dropdown();
        if (settings) settingsPopover();
        ui.end();
        graphics.pose().popMatrix();
    }

    private void panel() {
        int x = panelX, y = panelY, w = panelW, h = panelH;
        boolean full = BagelTV.config().fullscreen;
        if (!full) ui.fill(x + 4, y + 4, w, h, Theme.SHADOW);
        ui.fill(x, y, w, h, Theme.PANEL);
        ui.fill(x + 1, y + 1, w - 2, TITLE - 1, Theme.RAISED);
        ui.outline(x, y, w, h, full ? Theme.BORDER : Theme.BORDER_STRONG);
        ui.hline(x + 1, y + TITLE - 1, w - 2, Theme.BORDER);
        ui.shield(x, y, w, h);
        ui.reveal(x + 1, y + 1, w - 2, TITLE - 1, Theme.PANEL, 0);
        titleBar(x, y, w);
        ui.revealed();
        ui.reveal(x + 1, y + TITLE, RAIL + 1, h - TITLE - 1, Theme.PANEL, 0);
        rail(x + 1, y + TITLE, RAIL, h - TITLE - 1);
        ui.vline(x + RAIL + 1, y + TITLE, h - TITLE - 1, Theme.BORDER);
        ui.revealed();
        int cx = x + RAIL + 2 + 5, cy = y + TITLE + 3;
        int cw = x + w - 1 - 5 - cx, ch = y + h - 1 - 5 - cy;
        ui.contentX = cx;
        ui.contentY = cy;
        ui.contentW = cw;
        ui.contentH = ch;
        ui.clip(x + RAIL + 2, y + TITLE, w - RAIL - 3, h - TITLE - 1);
        current().render(ui, cx, cy, cw, ch);
        ui.unclip();
    }

    private final Interactive titleDrag = new Interactive() {
        @Override
        public boolean mouseDown(int px, int py, int button, boolean shift, boolean doubleClick) {
            if (button != InputConstants.MOUSE_BUTTON_LEFT) return false;
            if (doubleClick) {
                toggleFullscreen();
                return true;
            }
            if (BagelTV.config().fullscreen) return true;
            draggingPanel = true;
            grabX = px - BagelTV.config().offsetX;
            grabY = py - BagelTV.config().offsetY;
            return true;
        }

        @Override
        public void mouseDrag(int px, int py) {
            if (!draggingPanel) return;
            BagelTV.config().offsetX = px - grabX;
            BagelTV.config().offsetY = py - grabY;
        }

        @Override
        public void mouseUp(int px, int py) {
            if (draggingPanel) BagelTV.config().save();
            draggingPanel = false;
        }
    };

    private void toggleFullscreen() {
        BagelTV.config().fullscreen = !BagelTV.config().fullscreen;
        BagelTV.config().save();
    }

    private void titleBar(int x, int y, int w) {
        ui.interactive(x, y, w, TITLE, titleDrag);
        int by = y + 1;
        ui.iconButton(Icon.BACK, x + 1, by, 15, 15, false, !BACK.isEmpty(), "Back", this::back);
        ui.iconButton(Icon.FORWARD, x + 16, by, 15, 15, false, !FORWARD.isEmpty(), "Forward", this::forward);
        ui.vline(x + 31, y + 1, TITLE - 2, Theme.BORDER);
        ui.icon(Icon.BAGEL, x + 37, y + 4, Theme.ACCENT);
        int tx = x + 50 + ui.text(BagelTV.NAME, x + 50, y + 5, Theme.TEXT);

        int right = x + w - 1;
        right -= 15;
        ui.iconButton(Icon.CLOSE, right, by, 15, 15, false, true, "Close", this::onClose);
        right -= 15;
        boolean full = BagelTV.config().fullscreen;
        ui.iconButton(full ? Icon.RESTORE : Icon.MAXIMIZE, right, by, 15, 15, false, true,
                full ? "Window" : "Fullscreen", this::toggleFullscreen);
        right -= 15;
        int gearX = right;
        ui.iconButton(Icon.GEAR, right, by, 15, 15, settings, true, "Settings", () -> {
            settings = !settings;
            capturingKey = false;
            keyNote = "";
        });
        ui.vline(right - 1, y + 1, TITLE - 2, Theme.BORDER);
        this.gearX = gearX;

        // The search box, then the data's freshness in whatever room is left.
        int searchW = Math.clamp(w / 4, 110, 190);
        int searchX = right - 5 - searchW;
        ui.field(search, searchX, y + 2, searchW, Icon.SEARCH);
        dropX = searchX;
        searchAt = searchX;
        dropY = y + TITLE;
        dropW = Math.max(searchW, 170);
        if (dropX + dropW > x + w - 2) dropX = x + w - 2 - dropW;
        status(tx + 10, searchX - 8, y);
    }

    private int gearX;
    private int lastPressX, lastPressY;
    private int searchAt;

    /** Whether the collector is keeping up, and since when there is history. */
    private void status(int left, int right, int y) {
        Api.Query<Models.Status> query = Api.get("/v1/status", Models.Status.class);
        Models.Status status = query.data;
        String label;
        int color;
        long now = System.currentTimeMillis() / 1000;
        Long salesAt = status != null && status.collector != null && status.collector.sales != null
                ? status.collector.sales.at : null;
        if (status == null) {
            label = query.error != null ? "Offline" : "Connecting";
            color = query.error != null ? Theme.DOWN : Theme.MUTED;
        } else if (query.error != null) {
            label = "Offline";
            color = Theme.DOWN;
        } else if (salesAt == null || now - salesAt > 180) {
            label = "Stale";
            color = Theme.WARN;
        } else {
            label = "Live";
            color = Theme.UP;
        }
        Long since = status != null && status.since != null ? status.since.sales : null;
        int width = 9 + ui.width(label);
        if (right - left < width) return;
        int sx = right - width;
        boolean blink = color == Theme.UP && ui.now / 900 % 2 == 0;
        ui.fill(sx, y + 6, 5, 5, blink ? Theme.mix(Theme.RAISED, color, 0.55F) : color);
        ui.text(label, sx + 9, y + 5, color);
        if (ui.hover(sx - 2, y + 1, width + 4, TITLE - 2)) {
            List<String> tip = new ArrayList<>();
            tip.add(status == null ? (query.error != null ? query.error : "Connecting to the market data")
                    : query.error != null ? query.error : label.equals("Live") ? "Market data is live"
                    : "Market data has stopped updating");
            if (status != null && status.collector != null) {
                Models.Collector collector = status.collector;
                if (collector.sales != null && collector.sales.at != null) {
                    tip.add("Auction sales\t" + Fmt.ago(collector.sales.at, now) + " ago");
                }
                if (collector.listings != null && collector.listings.at != null) {
                    tip.add("Listings\t" + Fmt.ago(collector.listings.at, now) + " ago");
                }
                if (collector.orders != null && collector.orders.at != null) {
                    tip.add("Buy orders\t" + Fmt.ago(collector.orders.at, now) + " ago");
                }
            }
            if (since != null) tip.add("Recording since\t" + Fmt.dayClock(since));
            ui.tip(tip);
        }
    }

    private void rail(int x, int y, int w, int h) {
        int ry = y + 3;
        Route.Kind[] kinds = {Route.Kind.MARKET, Route.Kind.SCREENER, Route.Kind.ORDERS, Route.Kind.PLAYERS,
                Route.Kind.ECONOMY};
        for (Route.Kind kind : kinds) {
            boolean on = route.kind() == kind;
            boolean over = ui.click(x, ry, w, 15, () -> go(Route.of(kind)));
            if (on || over) ui.fill(x, ry, w, 15, on ? Theme.SELECTED : Theme.HOVER);
            if (on) ui.fill(x, ry, 2, 15, Theme.ACCENT);
            int color = on ? Theme.TEXT : over ? Theme.TEXT : Theme.MUTED;
            ui.icon(kind.icon, x + 7, ry + 4, on ? Theme.ACCENT : color);
            ui.text(kind.label, x + 19, ry + 4, color);
            ry += 15;
        }
        if (RECENT.isEmpty()) return;
        ry += 4;
        ui.hline(x + 6, ry, w - 12, Theme.BORDER);
        ry += 6;
        ui.text("Recent", x + 7, ry, Theme.FAINT);
        ry += 11;
        for (int i = 0; i < RECENT.size() && ry + 13 <= y + h - 2; i++, ry += 13) {
            Route recent = RECENT.get(i);
            boolean on = route.equals(recent);
            boolean over = ui.click(x, ry, w, 13, () -> go(recent));
            if (on || over) ui.fill(x, ry, w, 13, on ? Theme.SELECTED : Theme.HOVER);
            if (on) ui.fill(x, ry, 2, 13, Theme.ACCENT);
            String label;
            if (recent.kind() == Route.Kind.ITEM) {
                ui.item(recent.arg(), x + 6, ry + 2, 0.5F);
                var stack = Items.stack(recent.arg());
                label = NAMES.getOrDefault(recent.arg(), stack.isEmpty() ? recent.arg()
                        : stack.getHoverName().getString());
            } else {
                ui.head(recent.arg(), x + 6, ry + 2, 8);
                label = recent.arg();
            }
            ui.text(ui.fit(label, w - 22), x + 18, ry + 3, on || over ? Theme.TEXT : Theme.MUTED);
            if (over && ui.width(label) > w - 22) ui.tip(label);
        }
    }

    // Search

    private boolean dropdownOpen() {
        return ui.focus == search && search.text.trim().length() >= 2;
    }

    private void dropdown() {
        String q = search.text.trim();
        Api.Query<Models.ItemList> items = Api.get("/v1/items?window=30d&sort=turnover&limit=6&q="
                + Api.encode(q.toLowerCase().replace(' ', '_')), Models.ItemList.class);
        Api.Query<Models.PlayerSearch> players = Api.get("/v1/players/search?q=" + Api.encode(q),
                Models.PlayerSearch.class);
        results.clear();
        List<String> names = new ArrayList<>();
        if (items.data != null && items.data.items != null) {
            for (Models.ItemRow row : items.data.items) {
                if (results.size() >= 6) break;
                results.add(Route.item(row.item));
                names.add(row.label());
            }
        }
        int itemCount = results.size();
        if (players.data != null && players.data.players != null) {
            for (String name : players.data.players) {
                if (results.size() >= itemCount + 5) break;
                results.add(Route.player(name));
                names.add(name);
            }
        }
        boolean waiting = items.data == null && items.error == null || players.data == null && players.error == null;
        boolean failed = items.data == null && players.data == null && items.error != null;
        // Any name can be looked up, known or not.
        boolean lookup = q.length() >= 3 && q.matches("[A-Za-z0-9_.]{3,17}")
                && names.stream().noneMatch(name -> name.equalsIgnoreCase(q));
        int rows = results.size() + (lookup ? 1 : 0);
        dropH = Math.max(1, rows) * 18 + 2 + (itemCount > 0 && results.size() > itemCount ? 1 : 0);
        int x = dropX, y = dropY, w = dropW;
        ui.g.nextStratum();
        ui.fill(x + 3, y + 3, w, dropH, Theme.SHADOW);
        ui.box(x, y, w, dropH, Theme.RAISED, Theme.BORDER_STRONG);
        ui.shield(x, y, w, dropH);
        int ry = y + 1;
        if (rows == 0) {
            if (waiting) ui.spinner(x + 6, ry + 5);
            ui.text(failed ? items.error : waiting ? "Searching" : "No items or players match", x + (waiting ? 18 : 6),
                    ry + 5, Theme.MUTED);
            return;
        }
        for (int i = 0; i < results.size(); i++, ry += 18) {
            Route result = results.get(i);
            if (i == itemCount && itemCount > 0) {
                ui.hline(x + 1, ry, w - 2, Theme.BORDER);
                ry += 1;
            }
            boolean over = ui.click(x + 1, ry, w - 2, 18, () -> go(result));
            if (over || i == 0) ui.fill(x + 1, ry, w - 2, 18, over ? Theme.HOVER : Theme.SELECTED);
            if (result.kind() == Route.Kind.ITEM) ui.item(result.arg(), x + 3, ry + 1);
            else ui.head(result.arg(), x + 5, ry + 3, 12);
            ui.text(ui.fit(names.get(i), w - 70), x + 24, ry + 5, Theme.TEXT);
            ui.textRight(result.kind() == Route.Kind.ITEM ? "Item" : "Player", x + w - 6, ry + 5, Theme.FAINT);
        }
        if (lookup) {
            boolean over = ui.click(x + 1, ry, w - 2, 18, () -> go(Route.player(q)));
            if (over || results.isEmpty()) ui.fill(x + 1, ry, w - 2, 18, over ? Theme.HOVER : Theme.SELECTED);
            ui.icon(Icon.SEARCH, x + 8, ry + 6, Theme.MUTED);
            ui.text(ui.fit("Look up player " + q, w - 30), x + 24, ry + 5, Theme.TEXT);
        }
    }

    // Settings

    private static final String[] SCALES = {"Auto", "1", "2", "3", "4", "5"};

    private void settingsPopover() {
        int w = 168, h = 62;
        int x = Math.min(gearX + 15 - w, ui.width - w - 2), y = panelY + TITLE;
        ui.g.nextStratum();
        // A click anywhere else closes it.
        ui.click(0, 0, ui.width, ui.height, () -> {
            settings = false;
            capturingKey = false;
        });
        ui.cursor(null);
        ui.fill(x + 3, y + 3, w, h, Theme.SHADOW);
        ui.box(x, y, w, h, Theme.RAISED, Theme.BORDER_STRONG);
        ui.shield(x, y, w, h);
        ui.text("Hotkey", x + 7, y + 10, Theme.MUTED);
        String key = capturingKey ? "Press a key" : keyName(BagelTV.config().hotkey);
        int kw = ui.width(key) + 10;
        ui.button(key, x + w - 7 - kw, y + 7, capturingKey, () -> {
            capturingKey = !capturingKey;
            keyNote = "";
        });
        ui.text("Size", x + 7, y + 28, Theme.MUTED);
        int sw = ui.segmentsWidth(SCALES);
        ui.segments(x + w - 7 - sw, y + 25, SCALES, Math.min(BagelTV.config().scale, SCALES.length - 1), picked -> {
            BagelTV.config().scale = picked;
            BagelTV.config().save();
        });
        ui.text(ui.fit(keyNote.isEmpty() ? BagelTV.NAME + " " + BagelTV.VERSION : keyNote, w - 14), x + 7, y + 46,
                keyNote.isEmpty() ? Theme.FAINT : Theme.WARN);
    }

    private static String keyName(int code) {
        return InputConstants.Type.KEYBOARD.getOrCreate(code).getDisplayName().getString();
    }

    /** Takes a key for opening the terminal unless Minecraft already uses it. */
    private void captureKey(int code) {
        if (code == InputConstants.KEY_ESCAPE) {
            capturingKey = false;
            keyNote = "";
            return;
        }
        if (code == InputConstants.UNKNOWN.getValue()) return;
        InputConstants.Key key = InputConstants.Type.KEYBOARD.getOrCreate(code);
        for (KeyMapping mapping : minecraft.options.keyMappings) {
            boolean plain = mapping.getCategory() != KeyMapping.Category.DEBUG
                    || mapping == minecraft.options.keyDebugModifier || mapping == minecraft.options.keyDebugOverlay;
            if (plain && mapping.matches(key)) {
                keyNote = "Minecraft already uses " + keyName(code);
                return;
            }
        }
        BagelTV.config().hotkey = code;
        BagelTV.config().save();
        capturingKey = false;
        keyNote = "";
    }

    // Input

    private int tx(double guiX) {
        return (int) Math.floor(guiX * toTerminal());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int px = tx(event.x()), py = tx(event.y());
        if (intro != null) {
            intro.skip();
            return true;
        }
        // The mouse's side buttons go back and forward.
        if (event.button() == InputConstants.MOUSE_BUTTON_4) {
            back();
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_5) {
            forward();
            return true;
        }
        // Minecraft calls any two quick presses a double click; here they also have to land in the same spot.
        boolean twice = doubleClick && Math.abs(px - lastPressX) <= 3 && Math.abs(py - lastPressY) <= 3;
        lastPressX = px;
        lastPressY = py;
        TextField before = ui.focus;
        for (int i = ui.hits.size() - 1; i >= 0; i--) {
            Ui.Hit hit = ui.hits.get(i);
            if (!hit.contains(px, py)) continue;
            if (hit.field != null) {
                ui.focus = hit.field;
                return true;
            }
            if (hit.click != null) {
                if (before != search || !dropdownOpen() || !inDropdown(px, py)) ui.focus = null;
                if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) hit.click.run();
                return true;
            }
            if (hit.target != null && hit.target.mouseDown(px, py, event.button(), event.hasShiftDown() || minecraft.hasShiftDown(),
                    twice)) {
                ui.focus = null;
                captured = hit.target;
                return true;
            }
        }
        ui.focus = null;
        return true;
    }

    private boolean inDropdown(int px, int py) {
        return px >= dropX && px < dropX + dropW && py >= dropY && py < dropY + dropH;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (captured != null) captured.mouseDrag(tx(event.x()), tx(event.y()));
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (captured != null) {
            Interactive target = captured;
            captured = null;
            target.mouseUp(tx(event.x()), tx(event.y()));
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int px = tx(mouseX), py = tx(mouseY);
        if (intro != null) return true;
        // A popover or the dropdown stops the wheel reaching what is under it.
        if (settings || dropdownOpen() && inDropdown(px, py)) return true;
        for (int i = ui.hits.size() - 1; i >= 0; i--) {
            Ui.Hit hit = ui.hits.get(i);
            if (hit.contains(px, py) && hit.target != null && hit.target.scroll(px, py, scrollX, scrollY)) return true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        // Any key skips the startup animation; Escape and the hotkey still close the terminal.
        if (intro != null && key != InputConstants.KEY_ESCAPE && key != BagelTV.config().hotkey) {
            intro.skip();
            return true;
        }
        if (capturingKey) {
            captureKey(key);
            return true;
        }
        if (ui.focus != null) {
            if (key == InputConstants.KEY_ESCAPE) {
                ui.focus = null;
                return true;
            }
            ui.focus.key(event);
            return true;
        }
        if (ui.openMenu != null && key == InputConstants.KEY_ESCAPE) {
            ui.openMenu = null;
            return true;
        }
        if (settings && key == InputConstants.KEY_ESCAPE) {
            settings = false;
            return true;
        }
        if (current().key(key)) return true;
        if (key == BagelTV.config().hotkey) {
            onClose();
            return true;
        }
        boolean alt = event.hasAltDown() || minecraft.hasAltDown();
        if (alt && key == InputConstants.KEY_LEFT) {
            back();
            return true;
        }
        if (alt && key == InputConstants.KEY_RIGHT) {
            forward();
            return true;
        }
        if (key == InputConstants.KEY_SLASH || event.hasControlDownWithQuirk() && key == InputConstants.KEY_F) {
            ui.focus = search;
            swallowChar = key == InputConstants.KEY_SLASH;
            return true;
        }
        return super.keyPressed(event);
    }

    /** The slash that focuses the search box is not typed into it. */
    private boolean swallowChar;

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (swallowChar) {
            swallowChar = false;
            if (event.codepoint() == '/') return true;
        }
        if (ui.focus == null) return false;
        ui.focus.type(event.codepointAsString());
        return true;
    }

    /** Gives a text box the keyboard, for views with their own. */
    public Ui ui() {
        return ui;
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void removed() {
        if (pointerHidden) {
            pointerHidden = false;
            SDLMouse.SDL_ShowCursor();
        }
        BagelTV.config().save();
        Api.cancelAll();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
