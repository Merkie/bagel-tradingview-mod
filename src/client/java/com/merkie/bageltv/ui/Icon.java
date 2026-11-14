package com.merkie.bageltv.ui;

/** Pixel icons drawn from string bitmaps, one fill per run of X. Most are 7 x 7. */
public enum Icon {
    BACK("...X...", "..X....", ".X.....", "XXXXXXX", ".X.....", "..X....", "...X..."),
    FORWARD("...X...", "....X..", ".....X.", "XXXXXXX", ".....X.", "....X..", "...X..."),
    CLOSE("X.....X", ".X...X.", "..X.X..", "...X...", "..X.X..", ".X...X.", "X.....X"),
    MAXIMIZE("XXXXXXX", "XXXXXXX", "X.....X", "X.....X", "X.....X", "X.....X", "XXXXXXX"),
    RESTORE("..XXXXX", "..X...X", "XXXXX.X", "X...X.X", "X...XXX", "X...X..", "XXXXX.."),
    GEAR("...X...", ".XXXXX.", ".XX.XX.", "XX...XX", ".XX.XX.", ".XXXXX.", "...X..."),
    SEARCH(".XXX...", "X...X..", "X...X..", "X...X..", ".XXX...", "....XX.", ".....XX"),
    MARKET("......X", "...X.XX", "..XXXX.", ".XX.X..", "XX.....", "X......", "XXXXXXX"),
    SCREENER("XXXXXXX", ".......", "XXXXXXX", ".......", "XXXXXXX", ".......", "XXXXXXX"),
    ORDERS("XXXXXXX", "X.....X", "X.XXX.X", "X.....X", "X.XXX.X", "X.....X", "XXXXXXX"),
    PLAYERS("..XXX..", "..XXX..", "..XXX..", ".......", ".XXXXX.", "XXXXXXX", "XXXXXXX"),
    ECONOMY("..XXX..", ".X.X.X.", ".X.X...", "..XXX..", "...X.X.", ".X.X.X.", "..XXX.."),
    BILLFORD("..X....", ".XXXXXX", "..X....", ".......", "....X..", "XXXXXX.", "....X.."),
    ITEM("...X...", "..XXX..", ".XXXXX.", "XXXXXXX", ".XXXXX.", "..XXX..", "...X..."),
    PLAYER("..XXX..", ".XXXXX.", ".XXXXX.", "..XXX..", ".......", ".XXXXX.", "XXXXXXX"),
    CURSOR("X......", "XX.....", "XXX....", "XXXX...", "XXXXX..", "XX.X...", "....X.."),
    ZOOM(".XXX...", "X.X.X..", "XXXXX..", "X.X.X..", ".XXX...", "....XX.", ".....XX"),
    PENCIL(".....XX", "....XXX", "...XXX.", "..XXX..", ".XXX...", "XXX....", "XX....."),
    TREND("......X", ".....X.", "....X..", "...X...", "..X....", ".X.....", "X......"),
    ARROW("...XXXX", ".....XX", "....X.X", "...X..X", "..X....", ".X.....", "X......"),
    HLINE(".......", ".......", ".......", "XXXXXXX", ".......", ".......", "......."),
    RECT("XXXXXXX", "X.....X", "X.....X", "X.....X", "X.....X", "X.....X", "XXXXXXX"),
    ERASER("...XX..", "..XXXX.", ".XXXXXX", "X.XXXX.", "XX.XX..", ".XXX...", "XXXXXXX"),
    TRASH(".XXXXX.", "XXXXXXX", ".......", ".X.X.X.", ".X.X.X.", ".X.X.X.", ".XXXXX."),
    RESET("X.XXX..", "XX...X.", "XXX...X", "......X", "X.....X", ".X...X.", "..XXX.."),
    LOG("X......", "X......", "X..XXX.", "X.X....", "XX.....", "X......", "XXXXXXX"),
    CANDLES("..X....", ".XXX.X.", ".XXX.X.", ".XXXXXX", "..X.XXX", "..X.XXX", ".....X."),
    LINE(".......", "......X", ".X...X.", "X.X.X..", "...X...", ".......", "......."),
    AREA("......X", ".X...XX", "XXX.XXX", "XXXXXXX", "XXXXXXX", "XXXXXXX", "XXXXXXX"),
    UP("...X...", "..XXX..", ".XXXXX.", "XXXXXXX", ".......", ".......", "......."),
    DOWN(".......", ".......", ".......", "XXXXXXX", ".XXXXX.", "..XXX..", "...X..."),
    SORT_DESC(".......", ".......", "XXXXX..", ".XXX...", "..X....", ".......", "......."),
    SORT_ASC(".......", ".......", "..X....", ".XXX...", "XXXXX..", ".......", "......."),
    WARNING("...X...", "..XXX..", "..X.X..", ".XX.XX.", ".XXXXX.", "XXX.XXX", "XXXXXXX"),
    UNKNOWN("XXXXXXX", "X.XXX.X", "X...X.X", "X..X..X", "X.....X", "X..X..X", "XXXXXXX"),
    /** The logo: a bagel. */
    BAGEL("..XXXXX..", ".XXXXXXX.", "XXXXXXXXX", "XXX...XXX", "XXX...XXX", "XXX...XXX", "XXXXXXXXX", ".XXXXXXX.",
            "..XXXXX..");

    final String[] rows;
    public final int width;
    public final int height;

    Icon(String... rows) {
        this.rows = rows;
        this.width = rows[0].length();
        this.height = rows.length;
    }
}
