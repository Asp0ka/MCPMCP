package dev.mcpmcp.magic;

import com.google.gson.*;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import static dev.mcpmcp.core.Jsons.*;

/** Bounded single-use continuation tied to exact world and RecipeManager/registry identity. */
final class Pages {
    private record Cursor(Object world,Object owner,String filter,List<?> candidates,int index,long expires) {}
    private final LinkedHashMap<String,Cursor> cursors=new LinkedHashMap<>();
    JsonObject take(Object world,Object owner,JsonObject args,List<?> initial,Predicate<Object> match,Function<Object,JsonObject> encode) {
        long now=System.currentTimeMillis();cursors.entrySet().removeIf(e->e.getValue().expires<now||e.getValue().world!=world);
        JsonObject filter=args.deepCopy();String token=str(filter,"cursor","");filter.remove("cursor");String signature=filter.toString();
        List<?> all=initial;int at=0;
        if(!token.isEmpty()) {Cursor c=cursors.remove(token);if(c==null||c.world!=world||c.owner!=owner||!c.filter.equals(signature))return error("stale_or_mismatched_cursor");all=c.candidates;at=c.index;}
        int limit=integer(args,"limit",16),budget=integer(args,"scan_budget",256),examined=0;JsonArray rows=new JsonArray();
        while(at<all.size()&&examined<budget&&rows.size()<limit){Object candidate=all.get(at++);examined++;if(match.test(candidate))rows.add(encode.apply(candidate));}
        boolean complete=at>=all.size();String next=null;
        if(!complete){next=UUID.randomUUID().toString();cursors.put(next,new Cursor(world,owner,signature,all,at,now+60000));while(cursors.size()>32)cursors.remove(cursors.keySet().iterator().next());}
        return obj("entries",rows,"examined",examined,"examined_total",at,"complete",complete,"truncated",!complete,"next_cursor",next,"consistency","client_snapshot_candidates; values may change; recipe reload invalidates recipe cursors");
    }
}
