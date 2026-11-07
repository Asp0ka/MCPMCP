package dev.mcpmcp.jei;

import com.google.gson.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.Stream;

/** Client-thread bounded iterators and ephemeral recipe handles; never rebuild a cursor by skipping. */
final class QueryStore {
    record Page(JsonArray entries, String next, int scanned, boolean complete) {}
    record RecipeRef(Object runtime, long epoch, Object category, Object recipe, long created) {}
    private record Scan(Object runtime,long epoch,String kind,JsonObject query,Stream<?> stream,Iterator<?> iterator,long created) {}
    private final LinkedHashMap<String,Scan> scans=new LinkedHashMap<>();
    private final LinkedHashMap<String,RecipeRef> recipes=new LinkedHashMap<>();
    private final LongSupplier clock;
    QueryStore(){this(System::currentTimeMillis);}
    QueryStore(LongSupplier clock){this.clock=clock;}
    Page page(Object runtime,long epoch,String kind,JsonObject query,String cursor,int limit,int budget,Supplier<? extends Stream<?>> factory,Function<Object,JsonObject> map){
        if(limit<1||limit>64||budget<1||budget>2048)throw new IllegalArgumentException("jei_page_bound");
        long now=clock.getAsLong();expire(now);Scan s;String token=cursor;
        if(cursor.isEmpty()){
            while(scans.size()>=16)drop(scans.keySet().iterator().next());
            Stream<?> stream=factory.get();s=new Scan(runtime,epoch,kind,query.deepCopy(),stream,stream.iterator(),now);token=UUID.randomUUID().toString();scans.put(token,s);
        }else{
            s=scans.get(token);if(s==null||s.runtime!=runtime||s.epoch!=epoch||!s.kind.equals(kind)||!s.query.equals(query))throw new IllegalArgumentException("stale_or_mismatched_jei_cursor");
        }
        JsonArray out=new JsonArray();int examined=0;
        try{
            while(examined<budget&&out.size()<limit&&s.iterator.hasNext()){
                Object value=s.iterator.next();examined++;JsonObject row=map.apply(value);if(row!=null)out.add(row);
            }
            boolean more=s.iterator.hasNext();if(!more)drop(token);return new Page(out,more?token:null,examined,!more);
        }catch(RuntimeException|LinkageError ex){drop(token);throw ex;}
    }
    String remember(Object runtime,long epoch,Object category,Object recipe){
        expire(clock.getAsLong());while(recipes.size()>=512)recipes.remove(recipes.keySet().iterator().next());
        String id=UUID.randomUUID().toString();recipes.put(id,new RecipeRef(runtime,epoch,category,recipe,clock.getAsLong()));return id;
    }
    RecipeRef resolve(Object runtime,long epoch,String id){
        expire(clock.getAsLong());RecipeRef ref=recipes.get(id);if(ref==null||ref.runtime!=runtime||ref.epoch!=epoch)throw new IllegalArgumentException("stale_jei_recipe_handle");return ref;
    }
    void clear(){for(Scan s:scans.values())s.stream.close();scans.clear();recipes.clear();}
    private void expire(long now){
        List<String> expired=new ArrayList<>();scans.forEach((k,s)->{if(now-s.created>30000)expired.add(k);});expired.forEach(this::drop);
        recipes.entrySet().removeIf(e->now-e.getValue().created>120000);
    }
    private void drop(String k){Scan s=scans.remove(k);if(s!=null)s.stream.close();}
}
