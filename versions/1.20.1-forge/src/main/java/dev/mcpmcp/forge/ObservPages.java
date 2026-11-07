package dev.mcpmcp.forge;

import com.google.gson.JsonObject;
import java.util.*;
import java.util.function.Supplier;
import static dev.mcpmcp.core.Jsons.*;

/** Short-lived client-thread cursors keep iterator traversal bounded and pin the original query. */
final class ObservPages {
    private record Session(Object owner,String kind,JsonObject query,ObservCursor<?> cursor,long created) {}
    private static final LinkedHashMap<String,Session> SESSIONS=new LinkedHashMap<>();
    record Page<T>(List<T> candidates,String next,long examined,boolean more) {}
    @SuppressWarnings("unchecked")
    static <T> Page<T> take(String kind,Object owner,JsonObject args,int budget,Supplier<Iterator<T>> factory) {
        long now=System.currentTimeMillis();SESSIONS.entrySet().removeIf(e->now-e.getValue().created>30000||(e.getValue().kind.equals(kind)&&e.getValue().owner!=owner));
        JsonObject query=args.deepCopy();query.remove("cursor");query.remove("limit");
        String token=str(args,"cursor","");Session s;
        if(token.isEmpty()) {
            if(SESSIONS.size()>=16)SESSIONS.remove(SESSIONS.keySet().iterator().next());
            token=UUID.randomUUID().toString();s=new Session(owner,kind,query,new ObservCursor<T>(factory.get()),now);SESSIONS.put(token,s);
        } else {
            s=SESSIONS.get(token);
            if(s==null||s.owner!=owner||!s.kind.equals(kind)||!s.query.equals(query))throw new IllegalArgumentException("stale_or_mismatched_cursor");
        }
        try {
            ObservCursor<T> c=(ObservCursor<T>)s.cursor;List<T> page=c.take(budget);boolean more=c.hasNext();
            if(!more)SESSIONS.remove(token);return new Page<>(page,more?token:null,c.examined(),more);
        } catch(ConcurrentModificationException ex) {SESSIONS.remove(token);throw new IllegalArgumentException("stale_cursor_retry_from_start");}
    }
    private ObservPages(){}
}
