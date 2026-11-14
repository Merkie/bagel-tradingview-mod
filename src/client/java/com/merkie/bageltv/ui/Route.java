package com.merkie.bageltv.ui;

/** A place in the terminal: a view and, for an item or a player, which one. */
public record Route(Kind kind, String arg) {
    public enum Kind {
        MARKET("Market", Icon.MARKET), SCREENER("Screener", Icon.SCREENER), ORDERS("Orders", Icon.ORDERS),
        PLAYERS("Players", Icon.PLAYERS), ECONOMY("Economy", Icon.ECONOMY), BILLFORD("Billford", Icon.BILLFORD),
        ITEM("Item", Icon.ITEM),
        PLAYER("Player", Icon.PLAYER);

        public final String label;
        public final Icon icon;

        Kind(String label, Icon icon) {
            this.label = label;
            this.icon = icon;
        }
    }

    public static Route of(Kind kind) {
        return new Route(kind, "");
    }

    public static Route item(String key) {
        return new Route(Kind.ITEM, key);
    }

    public static Route player(String name) {
        return new Route(Kind.PLAYER, name);
    }
}
