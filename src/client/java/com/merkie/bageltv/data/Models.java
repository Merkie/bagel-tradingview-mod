package com.merkie.bageltv.data;

import java.util.List;
import java.util.Map;

/** The API's JSON shapes. Money is cents, times are Unix seconds, and most numbers can be null. */
public final class Models {
    private Models() {}

    public static final class ItemRow {
        public String item, base, name, category;
        public Double worth, shopBuy, last, vwap, median, low, high, change;
        public Double trades, volume, turnover, outliers;
        public Double listed, sold, sellThrough, medianTimeToSell, timedSales;
        public Double bestBid, openOrders, bidUnits, bidValue, orderVolume, orderTurnover;
        /** What deliveries into buy orders were paid per item in the window, how many, and when the last was. */
        public Double orderVwap, orderFills, orderLastAt;
        /** The auction house's share of the item's money in the window; the rest went through buy orders. */
        public Double ahShare;
        public Double bestAsk, askListings, askUnits, spread, premium, sellScore;
        public Double[] spark;
        /** On a variant and on its bare material: the share of the material's sales whose kind is known. */
        public Double coverage;
        /** True on the bare material row that holds the sales of unknown kind. */
        public boolean mixed;
        // Only on /v1/items/{item}.
        public Map<String, WindowStats> windows;
        public Long firstTrade;
        public List<Variant> variants;

        public String label() {
            return compact(name != null ? name : item);
        }

        /** A book or potion of a known kind. Its prices are real; its trades, volume and turnover are a floor. */
        public boolean variant() {
            return item != null && item.indexOf('[') >= 0;
        }

        /** The mark before a count or sum that is only a minimum. */
        public String floor() {
            return variant() ? "\u2265" : "";
        }
    }

    /**
     * A variant's name the short way round, so the kind leads in a narrow column: "Enchanted Book (Mending 1)"
     * is "Mending 1 Book", "Splash Potion (Oozing)" is "Oozing Splash Potion".
     */
    public static String compact(String name) {
        if (name == null || !name.endsWith(")")) return name;
        int open = name.indexOf(" (");
        if (open < 0) return name;
        String material = name.substring(0, open), kind = name.substring(open + 2, name.length() - 1);
        if (kind.equals("kind unknown")) return name;
        return kind + " " + (material.equals("Enchanted Book") ? "Book" : material);
    }

    public static final class WindowStats {
        public Double trades, volume, turnover, vwap, median, low, high, change, sellThrough, medianTimeToSell;
        /** False when our history is shorter than the window, so the numbers cover only what we have. */
        public Boolean complete;
    }

    public static final class Variant {
        public String key;
        public Double worth;
    }

    public static final class OrderRow {
        public long id;
        public String owner, item, key, name, enchants, potion;
        public Double price, qty, delivered, remaining, value;
        public Long createdAt, expiresAt;
        public Double worth, ahVwap, bestAsk, vsWorth, vsAh, pool, edge, arbitrage, filledLastHour;
        /** What the order has paid out so far: delivered times price. */
        public Double paid;

        public String label() {
            return compact(name != null ? name : item);
        }

        /** The item to open: the variant the order is for when it has one. */
        public String target() {
            return key != null ? key : item;
        }
    }

    public static final class Overview {
        public String window;
        public long now, since;
        public boolean complete;
        public Totals totals;
        public Index index;
        public Economy economy;
        public Lists lists;
        public List<OrderRow> opportunities;
    }

    public static final class Totals {
        public Double turnover, volume, trades, items, orderTurnover, orderVolume, turnoverChange;
    }

    public static final class Index {
        public Double value, change, base;
    }

    public static final class Economy {
        public long t;
        public Double totalMoney, totalMoneyChange, players, online, openOrders, orderEscrow;
    }

    public static final class Lists {
        public List<ItemRow> turnover, volume, trades, gainers, losers, fastest, sell, wanted;
    }

    public static final class Candle {
        public long t;
        public double o, h, l, c;
        public Double vwap, volume, turnover, trades, orderTurnover, orderVolume;
        /** A bucket with no trades, flat at the last close. */
        public boolean empty;
    }

    public static final class Candles {
        public String item, tf;
        public long from, to;
        public List<Candle> candles;
    }

    public static final class Leaders {
        public String metric, window, tf;
        public long[] buckets;
        public List<LeaderSeries> series;
        public double[] other;
    }

    public static final class LeaderSeries {
        public String item, name;
        public double total;
        public double[] values;
        public Double[] vwap;
        public int[] rank;
    }

    public static final class EconomyHistory {
        public Economy now;
        public List<Economy> series;
        public List<EconomyDay> daily;
    }

    public static final class EconomyDay {
        public long t;
        public Double totalMoney, players;
    }

    public static final class ItemList {
        public String window, sort;
        public int total, offset;
        public List<ItemRow> items;
    }

    public static final class Trade {
        public long id, t;
        public double qty, price, unit;
        public String seller, buyer, name, item, side;
        public boolean outlier;
        public Double timeToSell;
    }

    public static final class Trades {
        public List<Trade> trades;
    }

    public static final class Book {
        public String item, asksSource;
        public List<Bid> bids;
        public List<OrderRow> orders;
        public List<Ask> asks;
        public List<Listing> listings;
        public Long bookAt;
    }

    public static final class Bid {
        public double price, units, orders, value;
    }

    public static final class Ask {
        public double unit, units, listings;
        /** False when the price is the game menu's rounded-down figure. */
        public Boolean exact;
    }

    public static final class Listing {
        public String seller, name, variant, enchants, potion;
        public double qty, price, unit;
        public Long createdAt, expiresAt;
        /** 0 when the price is the game menu's rounded-down figure, up to 10% under the real one. */
        public Integer priceExact;

        public boolean exact() {
            return priceExact == null || priceExact != 0;
        }
    }

    public static final class Orders {
        public String sort;
        public int total, offset;
        public Long sweptAt;
        public List<OrderRow> orders;
    }

    public static final class PlayerRow {
        public int rank;
        public String uuid, name, rankId, type;
        public Double balance, escrow, openOrders, trueBalance, change;
        public boolean exact;
        public Long updatedAt;
    }

    public static final class Players {
        public String sort, source;
        public int total, offset;
        public List<PlayerRow> players;
    }

    public static final class PlayersHistory {
        public String window;
        public List<PlayerSeries> series;
    }

    public static final class PlayerSeries {
        public String uuid, name;
        public Double balance;
        public List<MoneyPoint> points;
    }

    public static final class PlayerSearch {
        public List<String> players;
    }

    public static final class Player {
        public String uuid, name, rankId, type, team;
        public Long firstJoin, lastOnline, updatedAt;
        public boolean online;
        public List<Stat> stats;
        public Double balance, escrow, trueBalance, openOrders;
        public List<MoneyPoint> daily, intraday;
        public List<EscrowPoint> escrowHistory;
        public List<OrderRow> orders;
        public Auction auction;
        public OrderSpend orderSpend;
        public List<Fill> orderFills;
    }

    /** A delivery into one of a player's buy orders. Bagel does not say who delivered. */
    public static final class Fill {
        public long t, orderId;
        public String item, key, name;
        public double qty, price, value;

        public String target() {
            return key != null ? key : item;
        }
    }

    public static final class Owners {
        public String window, sort;
        public int total, offset;
        public List<Owner> owners;
    }

    /** What a player's buy orders paid out over the window, and what they have locked in open orders now. */
    public static final class Owner {
        public int rank;
        public String name;
        public double paid, units, fills, ordersFilled, locked, lockedActive, lockedIdle, openOrders;
        public TopPaid topItem;
    }

    public static final class TopPaid {
        public String item, name;
        public double paid;
    }

    public static final class Stat {
        public String key;
        public Double value, rank;
    }

    public static final class MoneyPoint {
        public long t;
        public Double money;
    }

    public static final class EscrowPoint {
        public long t;
        public Double escrow, orders;
    }

    public static final class Auction {
        public String window;
        public Side sold, bought;
        public List<TopItem> topSold, topBought;
        public Listed listed;
        public List<Trade> recent;
    }

    public static final class Side {
        public Double trades, turnover, volume;
    }

    public static final class TopItem {
        public String item, name;
        public Double trades, volume, turnover;
    }

    public static final class Listed {
        public Double listings, value;
    }

    public static final class OrderSpend {
        public String window;
        public Double paid, units;
    }

    public static final class Status {
        public boolean ok;
        public long now;
        public Since since;
        public Collector collector;
        public Counts counts;
    }

    public static final class Since {
        public Long sales, listings, orders;
    }

    public static final class Counts {
        public Double sales, listings, openOrders, orderFills, players, book;
    }

    public static final class Collector {
        public Long started;
        public Poll sales, listings, orders, economy;
    }

    public static final class Poll {
        public Long at;
    }
}
