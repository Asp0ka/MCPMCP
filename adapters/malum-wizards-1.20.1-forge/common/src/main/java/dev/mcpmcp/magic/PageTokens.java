package dev.mcpmcp.magic;

import java.util.*;
import java.util.function.LongSupplier;

/** Bounded in-memory continuations. No file I/O, no client-controlled raw offsets. */
public final class PageTokens {
    private record Entry(Object owner,String scope,int offset,long expires){}
    private final LinkedHashMap<String,Entry> entries=new LinkedHashMap<>();
    private final LongSupplier clock;
    public PageTokens(){this(System::currentTimeMillis);}
    public PageTokens(LongSupplier clock){this.clock=clock;}
    public int resolve(String token,Object owner,String scope){
        if(token.isEmpty())return 0;
        Entry e=entries.get(token);
        if(e==null||e.owner!=owner||!e.scope.equals(scope)||e.expires<=clock.getAsLong())throw new IllegalArgumentException("cursor_expired_or_query_changed");
        return e.offset;
    }
    public String next(Object owner,String scope,int offset){
        long now=clock.getAsLong();entries.values().removeIf(e->e.expires<=now);
        while(entries.size()>=64)entries.remove(entries.keySet().iterator().next());
        String token=UUID.randomUUID().toString();entries.put(token,new Entry(owner,scope,offset,now+120000));return token;
    }
}
