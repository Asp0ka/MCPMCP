package dev.mcpmcp.forge;

import java.util.*;

/** Resumable candidate iterator: consumes only this page, never skips an unbounded prefix. */
public final class ObservCursor<T> {
    private final Iterator<T> iterator;
    private long examined;
    public ObservCursor(Iterator<T> iterator) { this.iterator=Objects.requireNonNull(iterator); }
    public List<T> take(int count) {
        int n=ObservBounds.pageSize(count,4096);ArrayList<T> page=new ArrayList<>(Math.min(n,256));
        while(page.size()<n&&iterator.hasNext()){page.add(iterator.next());examined++;}
        return page;
    }
    public boolean hasNext(){return iterator.hasNext();}
    public long examined(){return examined;}
}
