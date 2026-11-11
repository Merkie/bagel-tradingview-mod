package com.merkie.bageltv.view;

import com.merkie.bageltv.data.Api;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * A long list fetched a hundred rows at a time: the first page at once and the next whenever the table is
 * scrolled near its end. A change of sort or filter starts over, showing the old rows until the new ones arrive.
 */
final class Pager<P, R> {
    private static final int PAGE = 100;
    private final Class<P> type;
    private final Function<P, List<R>> rows;
    private final ToIntFunction<P> totalOf;
    private final List<R> list = new ArrayList<>();
    private String base = "";
    private int pages = 1;
    private long stamp = -1;
    /** How many rows the server has for this sort and filter. */
    int total;
    boolean loading;
    /** Why the first page failed, or null. */
    String error;
    Api.Query<P> first;
    /** True for the frame in which rows for a new sort or filter first show, so the table can scroll to the top. */
    boolean restarted;

    Pager(Class<P> type, Function<P, List<R>> rows, ToIntFunction<P> total) {
        this.type = type;
        this.rows = rows;
        this.totalOf = total;
    }

    /** The rows so far for a path that already has a query string, such as {@code /v1/items?sort=turnover}. */
    List<R> rows(String path, boolean wantMore) {
        boolean changed = !path.equals(base);
        if (changed) {
            base = path;
            pages = 1;
            stamp = -1;
        }
        long next = 17;
        loading = false;
        boolean complete = true;
        List<Api.Query<P>> queries = new ArrayList<>(pages);
        for (int page = 0; page < pages; page++) {
            Api.Query<P> query = Api.get(path + "&limit=" + PAGE + "&offset=" + page * PAGE, type);
            queries.add(query);
            next = next * 31 + query.version + (query.data == null ? 0 : 7);
            loading |= query.loading;
            complete &= query.data != null;
        }
        first = queries.getFirst();
        error = first.data == null ? first.error : null;
        restarted = false;
        if (next != stamp && first.data != null) {
            restarted = stamp == -1;
            stamp = next;
            list.clear();
            for (Api.Query<P> query : queries) {
                if (query.data == null) break;
                List<R> page = rows.apply(query.data);
                if (page != null) list.addAll(page);
            }
            total = totalOf.applyAsInt(first.data);
        }
        if (wantMore && complete && list.size() < total && list.size() >= pages * PAGE) pages++;
        return list;
    }
}
