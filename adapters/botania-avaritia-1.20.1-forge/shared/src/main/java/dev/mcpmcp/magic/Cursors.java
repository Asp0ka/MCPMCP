package dev.mcpmcp.magic;
import java.util.*;
import java.util.function.LongSupplier;
/** Server-side tokens, no caller-controlled offset; bounded lifetime and count. */
final class Cursors {
 record Page(Object world,Object source,String signature,int offset,long expires){}
 private final Map<String,Page> pages=new LinkedHashMap<>();
 private final LongSupplier clock;
 Cursors(){this(System::currentTimeMillis);} Cursors(LongSupplier clock){this.clock=clock;}
 synchronized int offset(String token,Object world,Object source,String signature){
  if(token.isEmpty())return 0;Page p=pages.get(token);
  if(p==null||p.expires<clock.getAsLong()||p.world!=world||p.source!=source||!p.signature.equals(signature))throw new IllegalArgumentException("cursor_expired_or_query_changed");return p.offset;
 }
 synchronized String next(Object world,Object source,String signature,int offset){
  pages.entrySet().removeIf(e->e.getValue().expires<clock.getAsLong());
  while(pages.size()>=128)pages.remove(pages.keySet().iterator().next());
  String t=UUID.randomUUID().toString();pages.put(t,new Page(world,source,signature,offset,clock.getAsLong()+120000));return t;
 }
 static String signature(String kind,com.google.gson.JsonObject a){var keys=new TreeMap<String,String>();a.entrySet().stream().filter(e->!Set.of("cursor","limit","scan_budget").contains(e.getKey())).forEach(e->keys.put(e.getKey(),e.getValue().toString()));return kind+keys;}
}
